package psi

import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.HttpMethod
import domain.ParameterLocation
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Parsers Kotlin source code to extract API Endpoint definitions implementation details.
 * Analyzes Ktor `client.request` calls to reverse-engineer the API Spec.
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
        val parameters = mutableListOf<EndpointParameter>()
        var requestBodyVarName: String? = null

        val pathParams = extractPathParams(path)
        pathParams.forEach { name ->
            parameters.add(EndpointParameter(name, "String", ParameterLocation.PATH))
        }

        val lambdaArg = requestCall.lambdaArguments.firstOrNull()
        val block = lambdaArg?.getLambdaExpression()?.bodyExpression

        block?.statements?.forEach { statement ->
            if (statement is KtBinaryExpression) {
                if (statement.left?.text == "method") {
                    val rightText = statement.right?.text
                    method = parseMethod(rightText)
                }
            } else if (statement is KtCallExpression) {
                val callee = statement.calleeExpression?.text
                when (callee) {
                    "parameter" -> extractParam(statement, ParameterLocation.QUERY)?.let { parameters.add(it) }
                    "header" -> extractParam(statement, ParameterLocation.HEADER)?.let { parameters.add(it) }
                    "cookie" -> extractParam(statement, ParameterLocation.COOKIE)?.let { parameters.add(it) }
                    "setBody" -> {
                        requestBodyVarName = statement.valueArguments.firstOrNull()?.getArgumentExpression()?.text
                    }
                }
            }
        }

        // 3. Types from Signature
        val finalParams = parameters.map { param ->
            val funcArg = func.valueParameters.find { it.name == param.name || "`" + it.name + "`" == param.name }
            val type = funcArg?.typeReference?.text ?: "String"
            param.copy(type = type)
        }

        var requestBodyType: String? = null
        if (requestBodyVarName != null) {
            val cleanVarName = requestBodyVarName.trim()
            val funcArg = func.valueParameters.find { it.name == cleanVarName }
            requestBodyType = funcArg?.typeReference?.text
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
            val type = if(successType == "Unit") null else successType
            responses["200"] = EndpointResponse("200", "Success", type)
        }

        return EndpointDefinition(
            path = path,
            method = method,
            operationId = operationId,
            parameters = finalParams,
            requestBodyType = requestBodyType,
            responses = responses,
            summary = summary,
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

    private fun parseMethod(text: String?): HttpMethod {
        if (text == null) return HttpMethod.GET
        val methodStr = text.substringAfter("HttpMethod.")
        return try {
            HttpMethod.valueOf(methodStr.uppercase())
        } catch (_: Exception) {
            HttpMethod.GET
        }
    }

    private fun extractParam(expression: KtCallExpression, location: ParameterLocation): EndpointParameter? {
        val args = expression.valueArguments
        if (args.size < 2) return null
        val keyExpr = args[0].getArgumentExpression()
        val key = keyExpr?.text?.replace("\"", "") ?: "unknown"
        return EndpointParameter(name = key, type = "String", location = location)
    }

    private fun extractDoc(element: org.jetbrains.kotlin.psi.KtDeclaration): String? {
        val docComment = element.docComment ?: return null
        return docComment.text.split("\n")
            .map { cleanKDocLine(it) }
            .filter { it.isNotEmpty() && !it.startsWith("@") }
            .joinToString(" ")
            .ifEmpty { null }
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
                responses[code] = EndpointResponse(code, desc, type)
            }
        }
        return responses
    }

    private fun cleanKDocLine(line: String): String {
        return line.trim()
            .replace("/**", "")
            .replace("*/", "")
            .replaceFirst("*", "")
            .trim()
    }
}
