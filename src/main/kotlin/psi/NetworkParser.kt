package psi

import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.HttpMethod
import domain.MediaTypeObject
import domain.ParameterLocation
import domain.RequestBody
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Parsers Kotlin source code to extract API Endpoint definitions implementation details.
 * Analyzes Ktor `client.request` calls to reverse-engineer the API Spec, including
 * querystring parameters set via `url.encodedQuery`.
 */
class NetworkParser {

    /**
     * Parses the provided source code.
     */
    fun parse(sourceCode: String): List<EndpointDefinition> {
        val psiFactory = PsiInfrastructure.createPsiFactory()
        val file = psiFactory.createFile("Analysis.kt", sourceCode)

        val functions = file.collectDescendantsOfType<KtNamedFunction>()

        return functions.mapNotNull { parseFunction(it) }
    }

    private fun parseFunction(func: KtNamedFunction): EndpointDefinition? {
        val operationId = func.name ?: return null

        val callExpressions = func.collectDescendantsOfType<KtCallExpression>()
        val requestCall = callExpressions.find { call ->
            val callee = call.calleeExpression
            callee?.text == "request"
        } ?: return null

        // 1. Path
        val urlArg = requestCall.valueArguments.firstOrNull()
        val stringTemplate = urlArg?.getArgumentExpression() as? KtStringTemplateExpression
        val rawPath = parseUrlTemplate(stringTemplate) ?: "/"
        val path = normalizeUrl(rawPath)

        // 2. Method and Params
        var method = HttpMethod.GET
        var customMethod: String? = null
        val parameters = mutableListOf<EndpointParameter>()
        var requestBodyVarName: String? = null
        var requestContentType: String? = null

        val pathParams = extractPathParams(path)
        pathParams.forEach { name ->
            parameters.add(EndpointParameter(name, "String", ParameterLocation.PATH))
        }

        val lambdaArg = requestCall.lambdaArguments.firstOrNull()
        val block = lambdaArg?.getLambdaExpression()?.bodyExpression

        block?.statements?.forEach { statement ->
            if (statement is KtBinaryExpression) {
                when (statement.left?.text) {
                    "method" -> {
                        val rightText = statement.right?.text
                        val parsed = parseMethod(rightText)
                        method = parsed.method
                        customMethod = parsed.customMethod
                    }
                    "url.encodedQuery" -> {
                        val rightText = statement.right?.text
                        if (!rightText.isNullOrBlank()) {
                            parameters.add(EndpointParameter(rightText, "String", ParameterLocation.QUERYSTRING))
                        }
                    }
                }
            } else if (statement is KtCallExpression) {
                val callee = statement.calleeExpression?.text
                when (callee) {
                    "parameter" -> extractParam(statement, ParameterLocation.QUERY)?.let { parameters.add(it) }
                    "header" -> extractParam(statement, ParameterLocation.HEADER)?.let { parameters.add(it) }
                    "cookie" -> extractParam(statement, ParameterLocation.COOKIE)?.let { parameters.add(it) }
                    "contentType" -> requestContentType = parseContentType(statement)
                    "setBody" -> {
                        requestBodyVarName = statement.valueArguments.firstOrNull()?.getArgumentExpression()?.text
                    }
                }
            }
        }

        // 3. Types from Signature
        val paramDocs = extractParamDocs(func)
        val paramExamples = extractParamExamples(func)
        val finalParams = parameters.map { param ->
            val funcArg = func.valueParameters.find { it.name == param.name || "`" + it.name + "`" == param.name }
            val type = funcArg?.typeReference?.text ?: "String"
            val schema = funcArg?.typeReference?.text?.let { TypeMappers.kotlinToSchemaProperty(it) }
            val deprecated = funcArg?.annotationEntries?.any { it.shortName?.asString() == "Deprecated" } ?: false
            val isOptional = funcArg?.let {
                (it.typeReference?.text?.contains("?") == true) || it.hasDefaultValue()
            } ?: false
            val isRequired = if (param.location == ParameterLocation.PATH) true else !isOptional
            val docKey = param.name.removeSurrounding("`")
            val description = paramDocs[docKey]
            val exampleBundle = paramExamples[docKey]
            param.copy(
                type = type,
                schema = schema,
                deprecated = deprecated,
                isRequired = isRequired,
                description = description,
                example = exampleBundle?.example,
                examples = exampleBundle?.examples ?: emptyMap()
            )
        }

        var requestBodyType: String? = null
        var requestBodyRequired = true
        if (requestBodyVarName != null) {
            val cleanVarName = requestBodyVarName.trim()
            val funcArg = func.valueParameters.find { it.name == cleanVarName }
            requestBodyType = funcArg?.typeReference?.text
            requestBodyRequired = funcArg?.let {
                val nullable = it.typeReference?.text?.contains("?") == true
                val hasDefault = it.hasDefaultValue()
                !(nullable || hasDefault)
            } ?: true
        }
        val requestBody = if (requestBodyType != null) {
            val mediaType = requestContentType ?: "application/json"
            RequestBody(
                content = mapOf(mediaType to MediaTypeObject(schema = TypeMappers.kotlinToSchemaProperty(requestBodyType))),
                required = requestBodyRequired
            )
        } else {
            null
        }

        // 4. Return Type & Responses
        val responses = mutableMapOf<String, EndpointResponse>()
        var successType = "Unit"

        val returnTypeRef = func.typeReference?.text
        if (returnTypeRef != null && returnTypeRef != "Unit") {
            val cleanRef = returnTypeRef.replace(" ", "")
            if (cleanRef.startsWith("Result<")) {
                successType = returnTypeRef.substringAfter("Result<").substringBeforeLast(">")
            } else {
                successType = returnTypeRef
            }
        }

        // 5. Extract Metadata (KDoc)
        val summary = extractDoc(func)
        val externalDocs = extractExternalDocs(func)
        val tags = extractTags(func)
        val extractedResponses = extractResponses(func)

        if (extractedResponses.isNotEmpty()) {
            responses.putAll(extractedResponses)
        } else {
            val type = if (successType == "Unit") null else successType
            responses["200"] = EndpointResponse(
                statusCode = "200",
                description = "Success",
                type = type,
                content = contentForType(type)
            )
        }

        return EndpointDefinition(
            path = path,
            method = method,
            customMethod = customMethod,
            operationId = operationId,
            parameters = finalParams,
            requestBodyType = requestBodyType,
            requestBody = requestBody,
            responses = responses,
            summary = summary,
            description = extractDescription(func),
            externalDocs = externalDocs,
            tags = tags
        )
    }

    private fun normalizeUrl(rawUrl: String): String {
        var url = rawUrl.replace("{baseUrl}", "")
        if (url.startsWith("http")) {
            val protoIdx = url.indexOf("://")
            if (protoIdx > 0) {
                val afterProto = url.substring(protoIdx + 3)
                val slashIdx = afterProto.indexOf("/")
                if (slashIdx >= 0) {
                    url = "/" + afterProto.substring(slashIdx + 1)
                } else {
                    url = "/"
                }
            }
        }
        if (url.isNotEmpty() && !url.startsWith("/")) {
            url = "/$url"
        }
        if (url.isEmpty()) url = "/"
        return url
    }

    private fun parseUrlTemplate(template: KtStringTemplateExpression?): String? {
        if (template == null) return null
        val sb = StringBuilder()
        template.entries.forEach { entry ->
            if (entry is org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry) {
                sb.append(entry.text)
            } else if (entry is org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry) {
                sb.append("{${entry.expression?.text}}")
            } else if (entry is org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry) {
                val content = entry.expression?.text
                sb.append("{$content}")
            }
        }
        return sb.toString()
    }

    private fun extractPathParams(path: String): List<String> {
        val regex = "\\{([^}]+)}".toRegex()
        return regex.findAll(path)
            .map { it.groupValues[1] }
            .filter { it != "baseUrl" }
            .toList()
    }

    private data class ParsedMethod(val method: HttpMethod, val customMethod: String? = null)

    private fun parseMethod(text: String?): ParsedMethod {
        if (text == null) return ParsedMethod(HttpMethod.GET)

        if (text.startsWith("HttpMethod.")) {
            val methodStr = text.substringAfter("HttpMethod.")
            return try {
                ParsedMethod(HttpMethod.valueOf(methodStr.uppercase()))
            } catch (_: Exception) {
                ParsedMethod(HttpMethod.GET)
            }
        }

        if (text.startsWith("HttpMethod(")) {
            val raw = text.substringAfter("HttpMethod(").substringBeforeLast(")")
            val methodToken = raw.removeSurrounding("\"").trim()
            if (methodToken.isNotBlank()) {
                return try {
                    ParsedMethod(HttpMethod.valueOf(methodToken.uppercase()))
                } catch (_: Exception) {
                    ParsedMethod(HttpMethod.CUSTOM, methodToken)
                }
            }
        }

        return ParsedMethod(HttpMethod.GET)
    }

    private fun extractParam(expression: KtCallExpression, location: ParameterLocation): EndpointParameter? {
        val args = expression.valueArguments
        if (args.size < 2) return null
        val keyExpr = args[0].getArgumentExpression()
        val key = keyExpr?.text?.replace("\"", "") ?: "unknown"
        return EndpointParameter(name = key, type = "String", location = location)
    }

    private fun parseContentType(expression: KtCallExpression): String? {
        val argExpr = expression.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
        val text = argExpr.text

        if (text.startsWith("ContentType.parse(")) {
            val raw = text.substringAfter("ContentType.parse(").substringBeforeLast(")")
            return raw.removeSurrounding("\"")
        }

        if (text.startsWith("ContentType.")) {
            val parts = text.split(".")
            if (parts.size >= 3) {
                val type = parts[1].lowercase()
                val subType = parts[2].lowercase()
                return "$type/$subType"
            }
        }

        return null
    }

    private fun extractDoc(element: org.jetbrains.kotlin.psi.KtDeclaration): String? {
        val docComment = element.docComment ?: return null
        return docComment.text.split("\n")
            .map { cleanKDocLine(it) }
            .filter { it.isNotEmpty() && !it.startsWith("@") }
            .joinToString(" ")
            .ifEmpty { null }
    }

    private fun extractParamDocs(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, String> {
        val docComment = element.docComment ?: return emptyMap()
        val docs = mutableMapOf<String, String>()

        docComment.text.split("\n")
            .map { cleanKDocLine(it) }
            .filter { it.startsWith("@param") }
            .forEach { line ->
                val content = line.removePrefix("@param").trim()
                if (content.isEmpty()) return@forEach
                val parts = content.split(" ", limit = 2)
                val name = parts[0].trim()
                val desc = parts.getOrNull(1)?.trim()
                if (name.isNotEmpty() && !desc.isNullOrBlank()) {
                    docs[name] = desc
                }
            }

        return docs
    }

    private data class ParamExampleBundle(
        var example: domain.ExampleObject? = null,
        val examples: MutableMap<String, domain.ExampleObject> = mutableMapOf()
    )

    private fun extractParamExamples(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, ParamExampleBundle> {
        val docComment = element.docComment ?: return emptyMap()
        val bundles = mutableMapOf<String, ParamExampleBundle>()

        docComment.text.split("\n")
            .map { cleanKDocLine(it) }
            .filter { it.startsWith("@paramExample") }
            .forEach { line ->
                val content = line.removePrefix("@paramExample").trim()
                if (content.isEmpty()) return@forEach
                val parts = content.split(" ", limit = 2)
                if (parts.size < 2) return@forEach
                val paramName = parts[0].trim()
                val rawExample = parts[1].trim()
                if (paramName.isEmpty() || rawExample.isEmpty()) return@forEach

                val bundle = bundles.getOrPut(paramName) { ParamExampleBundle() }
                val namedParts = rawExample.split(": ", limit = 2)
                if (namedParts.size == 2 && namedParts[0].isNotBlank() && !namedParts[0].contains(" ")) {
                    val key = namedParts[0].trim()
                    val value = namedParts[1].trim()
                    bundle.examples[key] = domain.ExampleObject(serializedValue = value)
                } else {
                    bundle.example = domain.ExampleObject(serializedValue = rawExample)
                }
            }

        return bundles
    }

    private fun extractExternalDocs(element: org.jetbrains.kotlin.psi.KtDeclaration): domain.ExternalDocumentation? {
        val docComment = element.docComment ?: return null
        val seeLine = docComment.text.split("\n").map { cleanKDocLine(it) }.find { it.startsWith("@see") } ?: return null
        val content = seeLine.removePrefix("@see").trim()
        val parts = content.split(" ", limit = 2)
        val url = parts.getOrNull(0) ?: return null
        val desc = parts.getOrNull(1)
        return domain.ExternalDocumentation(url = url, description = desc)
    }

    private fun extractTags(element: org.jetbrains.kotlin.psi.KtDeclaration): List<String> {
        val docComment = element.docComment ?: return emptyList()
        val tagLine = docComment.text.split("\n").map { cleanKDocLine(it) }.find { it.startsWith("@tag") } ?: return emptyList()
        val content = tagLine.removePrefix("@tag").trim()
        return content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun extractResponses(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, EndpointResponse> {
        val docComment = element.docComment ?: return emptyMap()
        val responses = mutableMapOf<String, EndpointResponse>()
        docComment.text.split("\n").map { cleanKDocLine(it) }.filter { it.startsWith("@response") }.forEach { line ->
            val parts = line.removePrefix("@response").trim().split(" ", limit = 3)
            if (parts.isNotEmpty()) {
                val code = parts[0]
                val type = if (parts.size > 1 && parts[1] != "Unit") parts[1] else null
                val desc = if (parts.size > 2) parts[2] else null
                responses[code] = EndpointResponse(
                    statusCode = code,
                    description = desc,
                    type = type,
                    content = contentForType(type)
                )
            }
        }
        return responses
    }

    private fun extractDescription(element: org.jetbrains.kotlin.psi.KtDeclaration): String? {
        val docComment = element.docComment ?: return null
        val descLine = docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .find { it.startsWith("@description") }
            ?: return null
        return descLine.removePrefix("@description").trim().ifEmpty { null }
    }

    private fun contentForType(type: String?): Map<String, MediaTypeObject> {
        if (type.isNullOrBlank() || type == "Unit") return emptyMap()
        val schema = TypeMappers.kotlinToSchemaProperty(type)
        return mapOf("application/json" to MediaTypeObject(schema = schema))
    }

    private fun cleanKDocLine(line: String): String {
        return line.trim()
            .replace("/**", "")
            .replace("*/", "")
            .replaceFirst("*", "")
            .trim()
    }
}
