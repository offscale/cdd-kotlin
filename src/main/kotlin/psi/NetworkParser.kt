package psi

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import domain.Callback
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.EncodingObject
import domain.ExampleObject
import domain.ExternalDocumentation
import domain.Header
import domain.HttpMethod
import domain.Info
import domain.Link
import domain.MediaTypeObject
import domain.ParameterLocation
import domain.ParameterStyle
import domain.RequestBody
import domain.ReferenceObject
import domain.SchemaDefinition
import domain.SchemaProperty
import domain.SecurityScheme
import domain.Server
import domain.ServerVariable
import domain.Tag
import domain.Xml
import domain.Discriminator
import domain.Contact
import domain.License
import openapi.OpenApiParser
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Parses Kotlin source code to extract API Endpoint definitions implementation details.
 * Analyzes Ktor `client.request` calls to reverse-engineer the API Spec, including
 * querystring parameters set via `url.encodedQuery`, plus KDoc metadata such as callbacks,
 * response summaries, operation-level extensions, and interface-level OpenAPI metadata.
 */
data class NetworkParseResult(
    /**
     * Extracted endpoint definitions from Ktor request calls.
     */
    val endpoints: List<EndpointDefinition>,
    /**
     * Webhooks captured from `@webhooks` KDoc metadata.
     */
    val webhooks: Map<String, domain.PathItem> = emptyMap(),
    /**
     * Root-level OpenAPI metadata captured from interface-level KDoc.
     */
    val metadata: OpenApiMetadata = OpenApiMetadata()
)

class NetworkParser {
    private val jsonMapper = ObjectMapper(JsonFactory())
    private val openApiParser = OpenApiParser()
    private val schemaKnownKeys = setOf(
        "\$ref",
        "\$dynamicRef",
        "\$id",
        "\$schema",
        "\$anchor",
        "\$dynamicAnchor",
        "\$comment",
        "\$defs",
        "type",
        "format",
        "contentMediaType",
        "contentEncoding",
        "minLength",
        "maxLength",
        "pattern",
        "minimum",
        "maximum",
        "multipleOf",
        "exclusiveMinimum",
        "exclusiveMaximum",
        "minItems",
        "maxItems",
        "uniqueItems",
        "minProperties",
        "maxProperties",
        "items",
        "prefixItems",
        "contains",
        "minContains",
        "maxContains",
        "properties",
        "patternProperties",
        "propertyNames",
        "additionalProperties",
        "required",
        "dependentRequired",
        "dependentSchemas",
        "enum",
        "description",
        "title",
        "default",
        "const",
        "deprecated",
        "readOnly",
        "writeOnly",
        "externalDocs",
        "discriminator",
        "xml",
        "oneOf",
        "anyOf",
        "allOf",
        "not",
        "if",
        "then",
        "else",
        "example",
        "examples",
        "unevaluatedProperties",
        "unevaluatedItems",
        "contentSchema"
    )
    private val exampleObjectKeys = setOf(
        "\$ref",
        "summary",
        "description",
        "dataValue",
        "serializedValue",
        "externalValue",
        "value"
    )

    /**
     * Parses the provided source code and returns endpoints plus OpenAPI metadata.
     */
    fun parseWithMetadata(sourceCode: String): NetworkParseResult {
        val psiFactory = PsiInfrastructure.createPsiFactory()
        val file = psiFactory.createFile("Analysis.kt", sourceCode)
        val endpoints = parseEndpoints(file)
        val metadata = extractRootMetadata(file)
        val webhooks = metadata.webhooks
        return NetworkParseResult(endpoints = endpoints, webhooks = webhooks, metadata = metadata)
    }

    /**
     * Parses the provided source code and returns endpoint definitions only.
     */
    fun parse(sourceCode: String): List<EndpointDefinition> {
        return parseWithMetadata(sourceCode).endpoints
    }

    private fun parseEndpoints(file: KtFile): List<EndpointDefinition> {
        val functions = file.collectDescendantsOfType<KtNamedFunction>()
        return functions.mapNotNull { parseFunction(it) }
    }

    private fun extractRootDocLines(file: KtFile): List<String> {
        val rootTags = setOf(
            "@openapi",
            "@info",
            "@servers",
            "@security",
            "@securityEmpty",
            "@tags",
            "@externalDocs",
            "@see",
            "@extensions",
            "@pathsExtensions",
            "@pathsEmpty",
            "@pathItems",
            "@webhooksExtensions",
            "@webhooksEmpty",
            "@securitySchemes",
            "@componentExamples",
            "@componentLinks",
            "@componentCallbacks",
            "@componentParameters",
            "@componentResponses",
            "@componentRequestBodies",
            "@componentHeaders",
            "@componentPathItems",
            "@componentMediaTypes",
            "@componentSchemas",
            "@componentsExtensions",
            "@webhooks"
        )

        return file.collectDescendantsOfType<KtClass>()
            .asSequence()
            .mapNotNull { it.docComment?.text }
            .map { it.split("\n").map(::cleanKDocLine) }
            .firstOrNull { lines -> lines.any { line -> rootTags.any { line.startsWith(it) } } }
            ?.toList()
            ?: emptyList()
    }

    private fun extractWebhooks(file: KtFile): Map<String, domain.PathItem> {
        return extractWebhooksLine(extractRootDocLines(file))
    }

    private fun extractRootMetadata(file: KtFile): OpenApiMetadata {
        val lines = extractRootDocLines(file)
        if (lines.isEmpty()) return OpenApiMetadata()

        val openapiMeta = extractOpenApiLine(lines)
        val info = extractInfoLine(lines)
        val servers = extractServersLine(lines)
        val securityResult = extractSecurityLines(lines)
        val tags = extractTagObjects(lines)
        val externalDocs = extractExternalDocsLine(lines)
        val extensions = extractExtensionsLine(lines)
        val pathsExtensions = extractPathsExtensionsLine(lines)
        val pathsExplicitEmpty = extractExplicitEmptyTag(lines, "@pathsEmpty")
        val pathItems = extractPathItemsLine(lines)
        val webhooks = extractWebhooksLine(lines)
        val webhooksExtensions = extractWebhooksExtensionsLine(lines)
        val webhooksExplicitEmpty = extractExplicitEmptyTag(lines, "@webhooksEmpty")
        val securitySchemes = extractSecuritySchemesLine(lines)
        val componentSchemas = extractComponentSchemasLine(lines)
        val componentExamples = extractComponentExamplesLine(lines)
        val componentLinks = extractComponentLinksLine(lines)
        val componentCallbacks = extractComponentCallbacksLine(lines)
        val componentParameters = extractComponentParametersLine(lines)
        val componentResponses = extractComponentResponsesLine(lines)
        val componentRequestBodies = extractComponentRequestBodiesLine(lines)
        val componentHeaders = extractComponentHeadersLine(lines)
        val componentPathItems = extractComponentPathItemsLine(lines)
        val componentMediaTypes = extractComponentMediaTypesLine(lines)
        val componentsExtensions = extractComponentsExtensionsLine(lines)

        return OpenApiMetadata(
            openapi = openapiMeta.openapi,
            jsonSchemaDialect = openapiMeta.jsonSchemaDialect,
            self = openapiMeta.self,
            info = info,
            servers = servers,
            security = securityResult.requirements,
            securityExplicitEmpty = securityResult.explicitEmpty,
            tags = tags,
            externalDocs = externalDocs,
            extensions = extensions,
            pathsExtensions = pathsExtensions,
            pathsExplicitEmpty = pathsExplicitEmpty,
            pathItems = pathItems,
            webhooks = webhooks,
            webhooksExtensions = webhooksExtensions,
            webhooksExplicitEmpty = webhooksExplicitEmpty,
            securitySchemes = securitySchemes,
            componentSchemas = componentSchemas,
            componentExamples = componentExamples,
            componentLinks = componentLinks,
            componentCallbacks = componentCallbacks,
            componentParameters = componentParameters,
            componentResponses = componentResponses,
            componentRequestBodies = componentRequestBodies,
            componentHeaders = componentHeaders,
            componentPathItems = componentPathItems,
            componentMediaTypes = componentMediaTypes,
            componentsExtensions = componentsExtensions
        )
    }

    private fun parseFunction(func: KtNamedFunction): EndpointDefinition? {
        val operationId = func.name ?: return null
        val operationIdExplicit = !extractOperationIdOmitted(func)

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

        block?.collectDescendantsOfType<KtBinaryExpression>()?.forEach { statement ->
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
        }

        block?.collectDescendantsOfType<KtCallExpression>()?.forEach { statement ->
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

        // 3. Types from Signature
        val paramDocs = extractParamDocs(func)
        val paramExamples = extractParamExamples(func)
        val paramMeta = extractParamMeta(func)
        val paramSchemas = extractParamSchemas(func)
        val paramContents = extractParamContents(func)
        val paramRefs = extractParamRefs(func)
        val paramExtensions = extractParamExtensions(func)
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
            val meta = paramMeta[docKey]
            val schemaOverride = paramSchemas[docKey]
            val contentOverride = paramContents[docKey]?.takeIf { it.isNotEmpty() }
            val refOverride = paramRefs[docKey]
            val extensionsOverride = paramExtensions[docKey] ?: emptyMap()
            val effectiveSchema = when {
                contentOverride != null -> null
                schemaOverride != null -> schemaOverride
                else -> schema
            }
            param.copy(
                type = type,
                schema = effectiveSchema,
                content = contentOverride ?: emptyMap(),
                deprecated = deprecated,
                isRequired = isRequired,
                description = description,
                example = exampleBundle?.example,
                examples = exampleBundle?.examples ?: emptyMap(),
                style = meta?.style,
                explode = meta?.explode,
                allowReserved = meta?.allowReserved,
                allowEmptyValue = meta?.allowEmptyValue,
                reference = refOverride,
                extensions = extensionsOverride
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
        var requestBody = if (requestBodyType != null) {
            val mediaType = requestContentType ?: "application/json"
            RequestBody(
                content = mapOf(mediaType to MediaTypeObject(schema = TypeMappers.kotlinToSchemaProperty(requestBodyType))),
                required = requestBodyRequired
            )
        } else {
            null
        }
        val requestBodyOverride = extractRequestBody(func)
        if (requestBodyOverride != null) {
            requestBody = mergeRequestBody(requestBody, requestBodyOverride)
            if (requestBodyType == null) {
                requestBodyType = resolveTypeFromContent(requestBody?.content ?: emptyMap())
            }
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
        val deprecated = extractDeprecated(func)
        val securityResult = extractSecurity(func)
        val servers = extractServers(func)
        val callbacks = extractCallbacks(func)
        val extensions = extractExtensions(func)

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
            operationIdExplicit = operationIdExplicit,
            parameters = finalParams,
            requestBodyType = requestBodyType,
            requestBody = requestBody,
            responses = responses,
            summary = summary,
            description = extractDescription(func),
            externalDocs = externalDocs,
            tags = tags,
            deprecated = deprecated,
            security = securityResult.requirements,
            securityExplicitEmpty = securityResult.explicitEmpty,
            servers = servers,
            callbacks = callbacks,
            extensions = extensions
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
                val content = entry.expression?.text.orEmpty()
                val paramName = extractPathParamNameFromExpression(content)
                sb.append("{${paramName ?: content}}")
            }
        }
        return sb.toString()
    }

    private fun extractPathParamNameFromExpression(expression: String): String? {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("encodePathComponent(")) {
            val inner = trimmed.removePrefix("encodePathComponent(").trim().removeSuffix(")")
            val firstArg = inner.substringBefore(",").trim()
            return extractPathParamNameFromExpression(firstArg)
        }

        if (trimmed.startsWith("serializeContentValue(")) {
            val inner = trimmed.removePrefix("serializeContentValue(").trim().removeSuffix(")")
            val firstArg = inner.substringBefore(",").trim()
            return extractPathParamNameFromExpression(firstArg)
        }

        Regex("^([A-Za-z_][A-Za-z0-9_]*)\\.entries\\.joinToString\\(").find(trimmed)?.let {
            return it.groupValues[1]
        }

        Regex("^([A-Za-z_][A-Za-z0-9_]*)\\.joinToString\\(").find(trimmed)?.let {
            return it.groupValues[1]
        }

        val cleaned = trimmed.removeSuffix(".toString()").removeSurrounding("`")
        return cleaned.takeIf { it.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*$")) }
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
        val key = when (keyExpr) {
            is KtStringTemplateExpression -> {
                val raw = keyExpr.text.removeSurrounding("\"")
                raw.substringBefore("[")
            }
            else -> inferParamNameFromLoop(expression) ?: "unknown"
        }
        return EndpointParameter(name = key, type = "String", location = location)
    }

    private fun inferParamNameFromLoop(expression: KtCallExpression): String? {
        val forEachCall = expression.parents.filterIsInstance<KtCallExpression>()
            .firstOrNull { it.calleeExpression?.text == "forEach" }
            ?: return null
        val dotQualified = forEachCall.parent
        val receiver = dotQualified?.let { it as? org.jetbrains.kotlin.psi.KtDotQualifiedExpression }
            ?.receiverExpression
            ?.text
        return receiver?.removeSurrounding("`")
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

    private data class ParamMeta(
        var style: domain.ParameterStyle? = null,
        var explode: Boolean? = null,
        var allowReserved: Boolean? = null,
        var allowEmptyValue: Boolean? = null
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
                    bundle.examples[key] = parseParamExampleValue(value)
                } else {
                    bundle.example = parseParamExampleValue(rawExample)
                }
            }

        return bundles
    }

    private fun parseParamExampleValue(raw: String): domain.ExampleObject {
        val trimmed = raw.trim()
        if (trimmed.startsWith("external:")) {
            return domain.ExampleObject(externalValue = trimmed.removePrefix("external:"))
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val node = runCatching { jsonMapper.readTree(trimmed) }.getOrNull()
            if (node != null) {
                if (node.isObject && looksLikeExampleObject(node)) {
                    return parseExampleObjectNode(node)
                }
                return domain.ExampleObject(dataValue = nodeToValue(node))
            }
        }
        return domain.ExampleObject(serializedValue = trimmed)
    }

    private fun looksLikeExampleObject(node: JsonNode): Boolean {
        if (!node.isObject) return false
        val fields = node.fieldNames().asSequence().toSet()
        if (fields.any { it.startsWith("x-") }) return true
        return fields.any { it in exampleObjectKeys }
    }

    private fun extractParamMeta(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, ParamMeta> {
        val docComment = element.docComment ?: return emptyMap()
        val metas = mutableMapOf<String, ParamMeta>()
        val lines = docComment.text.split("\n").map { cleanKDocLine(it) }

        fun ensureMeta(name: String): ParamMeta = metas.getOrPut(name) { ParamMeta() }

        lines.filter { it.startsWith("@paramStyle") }.forEach { line ->
            val content = line.removePrefix("@paramStyle").trim()
            if (content.isEmpty()) return@forEach
            val parts = content.split(" ", limit = 2)
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val styleToken = parts.getOrNull(1)?.trim().orEmpty()
            if (name.isEmpty() || styleToken.isEmpty()) return@forEach
            parseParameterStyle(styleToken)?.let { ensureMeta(name).style = it }
        }

        lines.filter { it.startsWith("@paramExplode") }.forEach { line ->
            val content = line.removePrefix("@paramExplode").trim()
            if (content.isEmpty()) return@forEach
            val parts = content.split(" ", limit = 2)
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val value = parts.getOrNull(1)?.trim()
            if (name.isEmpty()) return@forEach
            parseBooleanTagValue(value)?.let { ensureMeta(name).explode = it }
        }

        lines.filter { it.startsWith("@paramAllowReserved") }.forEach { line ->
            val content = line.removePrefix("@paramAllowReserved").trim()
            if (content.isEmpty()) return@forEach
            val parts = content.split(" ", limit = 2)
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val value = parts.getOrNull(1)?.trim()
            if (name.isEmpty()) return@forEach
            parseBooleanTagValue(value)?.let { ensureMeta(name).allowReserved = it }
        }

        lines.filter { it.startsWith("@paramAllowEmptyValue") }.forEach { line ->
            val content = line.removePrefix("@paramAllowEmptyValue").trim()
            if (content.isEmpty()) return@forEach
            val parts = content.split(" ", limit = 2)
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val value = parts.getOrNull(1)?.trim()
            if (name.isEmpty()) return@forEach
            parseBooleanTagValue(value)?.let { ensureMeta(name).allowEmptyValue = it }
        }

        return metas
    }

    private fun extractParamSchemas(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, SchemaProperty> {
        val docComment = element.docComment ?: return emptyMap()
        val lines = docComment.text.split("\n").map { cleanKDocLine(it) }
        val schemas = mutableMapOf<String, SchemaProperty>()
        lines.filter { it.startsWith("@paramSchema") }.forEach { line ->
            val content = line.removePrefix("@paramSchema").trim()
            if (content.isEmpty()) return@forEach
            val parts = content.split(" ", limit = 2)
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val json = parts.getOrNull(1)?.trim().orEmpty()
            if (name.isEmpty() || json.isEmpty()) return@forEach
            val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return@forEach
            schemas[name] = parseSchemaPropertyNode(node)
        }
        return schemas
    }

    private fun extractParamContents(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, Map<String, MediaTypeObject>> {
        val docComment = element.docComment ?: return emptyMap()
        val lines = docComment.text.split("\n").map { cleanKDocLine(it) }
        val contents = mutableMapOf<String, Map<String, MediaTypeObject>>()
        lines.filter { it.startsWith("@paramContent") }.forEach { line ->
            val content = line.removePrefix("@paramContent").trim()
            if (content.isEmpty()) return@forEach
            val parts = content.split(" ", limit = 2)
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val json = parts.getOrNull(1)?.trim().orEmpty()
            if (name.isEmpty() || json.isEmpty()) return@forEach
            val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return@forEach
            contents[name] = parseContentNode(node)
        }
        return contents
    }

    private fun extractParamRefs(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, ReferenceObject> {
        val docComment = element.docComment ?: return emptyMap()
        val lines = docComment.text.split("\n").map { cleanKDocLine(it) }
        val refs = mutableMapOf<String, ReferenceObject>()
        lines.filter { it.startsWith("@paramRef") }.forEach { line ->
            val content = line.removePrefix("@paramRef").trim()
            if (content.isEmpty()) return@forEach
            val parts = content.split(" ", limit = 2)
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val raw = parts.getOrNull(1)?.trim().orEmpty()
            if (name.isEmpty() || raw.isEmpty()) return@forEach
            parseReferenceObjectValue(raw)?.let { refs[name] = it }
        }
        return refs
    }

    private fun extractParamExtensions(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, Map<String, Any?>> {
        val docComment = element.docComment ?: return emptyMap()
        val lines = docComment.text.split("\n").map { cleanKDocLine(it) }
        val extensions = mutableMapOf<String, Map<String, Any?>>()
        lines.filter { it.startsWith("@paramExtensions") }.forEach { line ->
            val content = line.removePrefix("@paramExtensions").trim()
            if (content.isEmpty()) return@forEach
            val parts = content.split(" ", limit = 2)
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val json = parts.getOrNull(1)?.trim().orEmpty()
            if (name.isEmpty() || json.isEmpty()) return@forEach
            val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return@forEach
            if (!node.isObject) return@forEach
            val parsed = parseExtensions(node)
            if (parsed.isNotEmpty()) {
                extensions[name] = (extensions[name] ?: emptyMap()) + parsed
            }
        }
        return extensions
    }

    private fun extractResponseRefs(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, ReferenceObject> {
        val docComment = element.docComment ?: return emptyMap()
        val lines = docComment.text.split("\n").map { cleanKDocLine(it) }
        val refs = mutableMapOf<String, ReferenceObject>()
        lines.filter { it.startsWith("@responseRef") }.forEach { line ->
            parseResponsePayload(line, "@responseRef")?.let { (code, json) ->
                parseReferenceObjectValue(json)?.let { refs[code] = it }
            }
        }
        return refs
    }

    private fun extractResponseExtensions(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, Map<String, Any?>> {
        val docComment = element.docComment ?: return emptyMap()
        val lines = docComment.text.split("\n").map { cleanKDocLine(it) }
        val extensions = mutableMapOf<String, Map<String, Any?>>()
        lines.filter { it.startsWith("@responseExtensions") }.forEach { line ->
            parseResponsePayload(line, "@responseExtensions")?.let { (code, json) ->
                val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return@let
                if (!node.isObject) return@let
                val parsed = parseExtensions(node)
                if (parsed.isNotEmpty()) {
                    extensions[code] = (extensions[code] ?: emptyMap()) + parsed
                }
            }
        }
        return extensions
    }

    private fun extractExternalDocs(element: org.jetbrains.kotlin.psi.KtDeclaration): domain.ExternalDocumentation? {
        val docComment = element.docComment ?: return null
        val lines = docComment.text.split("\n").map { cleanKDocLine(it) }
        val explicit = lines.firstOrNull { it.startsWith("@externalDocs") }
        if (explicit != null) {
            val json = explicit.removePrefix("@externalDocs").trim()
            if (json.isEmpty()) return null
            val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return null
            if (!node.isObject) return null
            return parseExternalDocsNode(node)
        }

        val seeLine = lines.find { it.startsWith("@see") } ?: return null
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

    private fun extractServers(element: org.jetbrains.kotlin.psi.KtDeclaration): List<Server> {
        val docComment = element.docComment ?: return emptyList()
        val serverLine = docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .find { it.startsWith("@servers") }
            ?: return emptyList()
        val content = serverLine.removePrefix("@servers").trim()
        if (content.isEmpty()) return emptyList()
        val node = runCatching { jsonMapper.readTree(content) }.getOrNull() ?: return emptyList()
        return when {
            node.isArray -> node.mapNotNull { parseServerNode(it) }
            node.isObject -> listOfNotNull(parseServerNode(node))
            else -> emptyList()
        }
    }

    private data class OpenApiLine(
        val openapi: String?,
        val jsonSchemaDialect: String?,
        val self: String?
    )

    private fun extractOpenApiLine(lines: List<String>): OpenApiLine {
        val line = lines.firstOrNull { it.startsWith("@openapi") } ?: return OpenApiLine(null, null, null)
        val json = line.removePrefix("@openapi").trim()
        if (json.isEmpty()) return OpenApiLine(null, null, null)
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return OpenApiLine(null, null, null)
        if (!node.isObject) return OpenApiLine(null, null, null)
        return OpenApiLine(
            openapi = node.get("openapi")?.textValue(),
            jsonSchemaDialect = node.get("jsonSchemaDialect")?.textValue(),
            self = node.get("\$self")?.textValue()
        )
    }

    private fun extractInfoLine(lines: List<String>): Info? {
        val line = lines.firstOrNull { it.startsWith("@info") } ?: return null
        val json = line.removePrefix("@info").trim()
        if (json.isEmpty()) return null
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return null
        if (!node.isObject) return null
        return parseInfoNode(node)
    }

    private fun extractServersLine(lines: List<String>): List<Server> {
        val line = lines.firstOrNull { it.startsWith("@servers") } ?: return emptyList()
        val content = line.removePrefix("@servers").trim()
        if (content.isEmpty()) return emptyList()
        val node = runCatching { jsonMapper.readTree(content) }.getOrNull() ?: return emptyList()
        return when {
            node.isArray -> node.mapNotNull { parseServerNode(it) }
            node.isObject -> listOfNotNull(parseServerNode(node))
            else -> emptyList()
        }
    }

    private fun extractSecurityLines(lines: List<String>): SecurityParseResult {
        val securityLines = lines.filter { it.startsWith("@security ") }
        val explicitEmpty = lines.any { it == "@securityEmpty" || it.startsWith("@securityEmpty ") } &&
            securityLines.isEmpty()

        val requirements = securityLines.mapNotNull { line ->
            val raw = line.removePrefix("@security").trim()
            if (raw.isEmpty()) return@mapNotNull null
            val node = runCatching { jsonMapper.readTree(raw) }.getOrNull() ?: return@mapNotNull null
            if (!node.isObject) return@mapNotNull null
            node.fields().asSequence().associate { (key, value) ->
                val scopes = if (value.isArray) {
                    value.mapNotNull { it.textValue() }
                } else {
                    emptyList()
                }
                key to scopes
            }
        }
        return SecurityParseResult(requirements, explicitEmpty)
    }

    private fun extractTagObjects(lines: List<String>): List<Tag> {
        val line = lines.firstOrNull { it.startsWith("@tags") } ?: return emptyList()
        val json = line.removePrefix("@tags").trim()
        if (json.isEmpty()) return emptyList()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyList()
        if (!node.isArray) return emptyList()
        return node.mapNotNull { parseTagNode(it) }
    }

    private fun extractExternalDocsLine(lines: List<String>): ExternalDocumentation? {
        val explicit = lines.firstOrNull { it.startsWith("@externalDocs") }
        if (explicit != null) {
            val json = explicit.removePrefix("@externalDocs").trim()
            if (json.isEmpty()) return null
            val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return null
            if (!node.isObject) return null
            return parseExternalDocsNode(node)
        }

        val seeLine = lines.firstOrNull { it.startsWith("@see") } ?: return null
        val content = seeLine.removePrefix("@see").trim()
        val parts = content.split(" ", limit = 2)
        val url = parts.getOrNull(0) ?: return null
        val desc = parts.getOrNull(1)
        return ExternalDocumentation(url = url, description = desc)
    }

    private fun extractExtensionsLine(lines: List<String>): Map<String, Any?> {
        val extensionLines = lines.filter { it.startsWith("@extensions") }
        if (extensionLines.isEmpty()) return emptyMap()
        val merged = LinkedHashMap<String, Any?>()
        extensionLines.forEach { line ->
            val json = line.removePrefix("@extensions").trim()
            if (json.isEmpty()) return@forEach
            val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return@forEach
            if (!node.isObject) return@forEach
            node.fields().forEachRemaining { (key, value) ->
                merged[key] = nodeToValue(value)
            }
        }
        return merged
    }

    private fun extractPathsExtensionsLine(lines: List<String>): Map<String, Any?> {
        return extractExtensionsTag(lines, "@pathsExtensions")
    }

    private fun extractPathItemsLine(lines: List<String>): Map<String, domain.PathItem> {
        val line = lines.firstOrNull { it.startsWith("@pathItems") } ?: return emptyMap()
        val json = line.removePrefix("@pathItems").trim()
        if (json.isEmpty()) return emptyMap()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()

        val wrapper = jsonMapper.createObjectNode()
        wrapper.put("openapi", "3.2.0")
        val info = wrapper.putObject("info")
        info.put("title", "PathItems")
        info.put("version", "0.0.0")
        wrapper.set<JsonNode>("paths", node)

        return openApiParser.parseString(jsonMapper.writeValueAsString(wrapper), OpenApiParser.Format.JSON).paths
    }

    private fun extractWebhooksLine(lines: List<String>): Map<String, domain.PathItem> {
        val line = lines.firstOrNull { it.startsWith("@webhooks") } ?: return emptyMap()
        val json = line.removePrefix("@webhooks").trim()
        if (json.isEmpty()) return emptyMap()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()

        val wrapper = jsonMapper.createObjectNode()
        wrapper.put("openapi", "3.2.0")
        val info = wrapper.putObject("info")
        info.put("title", "Webhooks")
        info.put("version", "0.0.0")
        wrapper.set<JsonNode>("webhooks", node)

        return openApiParser.parseString(jsonMapper.writeValueAsString(wrapper), OpenApiParser.Format.JSON).webhooks
    }

    private fun extractWebhooksExtensionsLine(lines: List<String>): Map<String, Any?> {
        return extractExtensionsTag(lines, "@webhooksExtensions")
    }

    private fun extractExplicitEmptyTag(lines: List<String>, tag: String): Boolean {
        val line = lines.firstOrNull { it.startsWith(tag) } ?: return false
        val raw = line.removePrefix(tag).trim()
        val value = raw.ifBlank { null }
        return parseBooleanTagValue(value) ?: false
    }

    private fun extractExtensionsTag(lines: List<String>, tag: String): Map<String, Any?> {
        val line = lines.firstOrNull { it.startsWith(tag) } ?: return emptyMap()
        val json = line.removePrefix(tag).trim()
        if (json.isEmpty()) return emptyMap()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()
        return node.fields().asSequence().associate { (key, value) -> key to nodeToValue(value) }
    }

    private fun extractSecuritySchemesLine(lines: List<String>): Map<String, SecurityScheme> {
        val line = lines.firstOrNull { it.startsWith("@securitySchemes") } ?: return emptyMap()
        val json = line.removePrefix("@securitySchemes").trim()
        if (json.isEmpty()) return emptyMap()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()

        val root = jsonMapper.createObjectNode()
        root.put("openapi", "3.2.0")
        val info = root.putObject("info")
        info.put("title", "Security Schemes")
        info.put("version", "0.0.0")
        val components = root.putObject("components")
        components.set<JsonNode>("securitySchemes", node)

        val parsed = openApiParser.parseString(jsonMapper.writeValueAsString(root), OpenApiParser.Format.JSON)
        return parsed.components?.securitySchemes ?: emptyMap()
    }

    private fun extractComponentSchemasLine(lines: List<String>): Map<String, SchemaDefinition> {
        val line = lines.firstOrNull { it.startsWith("@componentSchemas") } ?: return emptyMap()
        val json = line.removePrefix("@componentSchemas").trim()
        if (json.isEmpty()) return emptyMap()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()

        val root = jsonMapper.createObjectNode()
        root.put("openapi", "3.2.0")
        val info = root.putObject("info")
        info.put("title", "Component Schemas")
        info.put("version", "0.0.0")
        val components = root.putObject("components")
        components.set<JsonNode>("schemas", node)

        val parsed = openApiParser.parseString(jsonMapper.writeValueAsString(root), OpenApiParser.Format.JSON)
        return parsed.components?.schemas ?: emptyMap()
    }

    private fun extractComponentExamplesLine(lines: List<String>): Map<String, ExampleObject> {
        val line = lines.firstOrNull { it.startsWith("@componentExamples") } ?: return emptyMap()
        val json = line.removePrefix("@componentExamples").trim()
        if (json.isEmpty()) return emptyMap()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()
        return node.fields().asSequence().associate { (name, raw) ->
            name to parseExampleObjectNode(raw)
        }
    }

    private fun extractComponentLinksLine(lines: List<String>): Map<String, Link> {
        val line = lines.firstOrNull { it.startsWith("@componentLinks") } ?: return emptyMap()
        val json = line.removePrefix("@componentLinks").trim()
        if (json.isEmpty()) return emptyMap()
        return parseLinksJson(json)
    }

    private fun extractComponentCallbacksLine(lines: List<String>): Map<String, Callback> {
        val line = lines.firstOrNull { it.startsWith("@componentCallbacks") } ?: return emptyMap()
        val json = line.removePrefix("@componentCallbacks").trim()
        if (json.isEmpty()) return emptyMap()
        return parseCallbacksJson(json)
    }

    private fun extractComponentParametersLine(lines: List<String>): Map<String, EndpointParameter> {
        val line = lines.firstOrNull { it.startsWith("@componentParameters") } ?: return emptyMap()
        val json = line.removePrefix("@componentParameters").trim()
        if (json.isEmpty()) return emptyMap()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()

        val root = jsonMapper.createObjectNode()
        root.put("openapi", "3.2.0")
        val info = root.putObject("info")
        info.put("title", "Component Parameters")
        info.put("version", "0.0.0")
        val components = root.putObject("components")
        components.set<JsonNode>("parameters", node)

        val parsed = openApiParser.parseString(jsonMapper.writeValueAsString(root), OpenApiParser.Format.JSON)
        return parsed.components?.parameters ?: emptyMap()
    }

    private fun extractComponentResponsesLine(lines: List<String>): Map<String, EndpointResponse> {
        val line = lines.firstOrNull { it.startsWith("@componentResponses") } ?: return emptyMap()
        val json = line.removePrefix("@componentResponses").trim()
        if (json.isEmpty()) return emptyMap()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()

        val root = jsonMapper.createObjectNode()
        root.put("openapi", "3.2.0")
        val info = root.putObject("info")
        info.put("title", "Component Responses")
        info.put("version", "0.0.0")
        val components = root.putObject("components")
        components.set<JsonNode>("responses", node)

        val parsed = openApiParser.parseString(jsonMapper.writeValueAsString(root), OpenApiParser.Format.JSON)
        return parsed.components?.responses ?: emptyMap()
    }

    private fun extractComponentRequestBodiesLine(lines: List<String>): Map<String, RequestBody> {
        val line = lines.firstOrNull { it.startsWith("@componentRequestBodies") } ?: return emptyMap()
        val json = line.removePrefix("@componentRequestBodies").trim()
        if (json.isEmpty()) return emptyMap()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()

        val root = jsonMapper.createObjectNode()
        root.put("openapi", "3.2.0")
        val info = root.putObject("info")
        info.put("title", "Component Request Bodies")
        info.put("version", "0.0.0")
        val components = root.putObject("components")
        components.set<JsonNode>("requestBodies", node)

        val parsed = openApiParser.parseString(jsonMapper.writeValueAsString(root), OpenApiParser.Format.JSON)
        return parsed.components?.requestBodies ?: emptyMap()
    }

    private fun extractComponentHeadersLine(lines: List<String>): Map<String, Header> {
        val line = lines.firstOrNull { it.startsWith("@componentHeaders") } ?: return emptyMap()
        val json = line.removePrefix("@componentHeaders").trim()
        if (json.isEmpty()) return emptyMap()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()

        val root = jsonMapper.createObjectNode()
        root.put("openapi", "3.2.0")
        val info = root.putObject("info")
        info.put("title", "Component Headers")
        info.put("version", "0.0.0")
        val components = root.putObject("components")
        components.set<JsonNode>("headers", node)

        val parsed = openApiParser.parseString(jsonMapper.writeValueAsString(root), OpenApiParser.Format.JSON)
        return parsed.components?.headers ?: emptyMap()
    }

    private fun extractComponentPathItemsLine(lines: List<String>): Map<String, domain.PathItem> {
        val line = lines.firstOrNull { it.startsWith("@componentPathItems") } ?: return emptyMap()
        val json = line.removePrefix("@componentPathItems").trim()
        if (json.isEmpty()) return emptyMap()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()

        val root = jsonMapper.createObjectNode()
        root.put("openapi", "3.2.0")
        val info = root.putObject("info")
        info.put("title", "Component Path Items")
        info.put("version", "0.0.0")
        val components = root.putObject("components")
        components.set<JsonNode>("pathItems", node)

        val parsed = openApiParser.parseString(jsonMapper.writeValueAsString(root), OpenApiParser.Format.JSON)
        return parsed.components?.pathItems ?: emptyMap()
    }

    private fun extractComponentMediaTypesLine(lines: List<String>): Map<String, MediaTypeObject> {
        val line = lines.firstOrNull { it.startsWith("@componentMediaTypes") } ?: return emptyMap()
        val json = line.removePrefix("@componentMediaTypes").trim()
        if (json.isEmpty()) return emptyMap()
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()

        val root = jsonMapper.createObjectNode()
        root.put("openapi", "3.2.0")
        val info = root.putObject("info")
        info.put("title", "Component Media Types")
        info.put("version", "0.0.0")
        val components = root.putObject("components")
        components.set<JsonNode>("mediaTypes", node)

        val parsed = openApiParser.parseString(jsonMapper.writeValueAsString(root), OpenApiParser.Format.JSON)
        return parsed.components?.mediaTypes ?: emptyMap()
    }

    private fun extractComponentsExtensionsLine(lines: List<String>): Map<String, Any?> {
        return extractExtensionsTag(lines, "@componentsExtensions")
    }

    private fun extractCallbacks(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, Callback> {
        val docComment = element.docComment ?: return emptyMap()
        val lines = docComment.text.split("\n").map { cleanKDocLine(it) }
        val callbackLines = lines.filter { it.startsWith("@callbacks") }
        if (callbackLines.isEmpty()) return emptyMap()
        val merged = LinkedHashMap<String, Callback>()
        callbackLines.forEach { line ->
            val json = line.removePrefix("@callbacks").trim()
            if (json.isEmpty()) return@forEach
            merged.putAll(parseCallbacksJson(json))
        }
        return merged
    }

    private fun parseCallbacksJson(json: String): Map<String, Callback> {
        val callbacksNode = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!callbacksNode.isObject) return emptyMap()

        val root = jsonMapper.createObjectNode()
        root.put("openapi", "3.2.0")
        val info = root.putObject("info")
        info.put("title", "Callbacks")
        info.put("version", "0.0.0")
        val paths = root.putObject("paths")
        val pathItem = paths.putObject("/_callbacks")
        val operation = pathItem.putObject("post")
        operation.set<JsonNode>("callbacks", callbacksNode)
        val responses = operation.putObject("responses")
        responses.putObject("200").put("description", "ok")

        val definition = openApiParser.parseString(jsonMapper.writeValueAsString(root), OpenApiParser.Format.JSON)
        return definition.paths["/_callbacks"]?.post?.callbacks ?: emptyMap()
    }

    private fun extractExtensions(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, Any?> {
        val docComment = element.docComment ?: return emptyMap()
        val lines = docComment.text.split("\n").map { cleanKDocLine(it) }
        val extensionLines = lines.filter { it.startsWith("@extensions") }
        if (extensionLines.isEmpty()) return emptyMap()
        val merged = LinkedHashMap<String, Any?>()
        extensionLines.forEach { line ->
            val json = line.removePrefix("@extensions").trim()
            if (json.isEmpty()) return@forEach
            val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return@forEach
            if (!node.isObject) return@forEach
            node.fields().forEachRemaining { (key, value) ->
                merged[key] = nodeToValue(value)
            }
        }
        return merged
    }

    private fun extractRequestBody(element: org.jetbrains.kotlin.psi.KtDeclaration): RequestBodyParseResult? {
        val docComment = element.docComment ?: return null
        val line = docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .find { it.startsWith("@requestBody") }
            ?: return null
        val json = line.removePrefix("@requestBody").trim()
        if (json.isEmpty()) return null
        return parseRequestBodyJson(json)
    }

    private fun mergeRequestBody(
        base: RequestBody?,
        overrideResult: RequestBodyParseResult
    ): RequestBody {
        val overrideBody = overrideResult.body
        if (overrideBody.reference != null) return overrideBody
        if (base == null) return overrideBody
        val baseBody = base
        val mergedContent = if (overrideBody.contentPresent) overrideBody.content else baseBody.content
        val mergedContentPresent = if (overrideBody.contentPresent) true else baseBody.contentPresent
        val mergedRequired = if (overrideResult.requiredPresent) overrideBody.required else baseBody.required
        val mergedDescription = overrideBody.description ?: baseBody.description
        val mergedExtensions = if (overrideBody.extensions.isNotEmpty()) overrideBody.extensions else baseBody.extensions
        return baseBody.copy(
            description = mergedDescription,
            content = mergedContent,
            contentPresent = mergedContentPresent,
            required = mergedRequired,
            reference = overrideBody.reference,
            extensions = mergedExtensions
        )
    }

    private fun extractResponses(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, EndpointResponse> {
        val docComment = element.docComment ?: return emptyMap()
        val responses = mutableMapOf<String, EndpointResponse>()
        val lines = docComment.text.split("\n").map { cleanKDocLine(it) }
        lines.filter { it.startsWith("@response ") }.forEach { line ->
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

        lines.filter { it.startsWith("@responseHeaders") }.forEach { line ->
            parseResponsePayload(line, "@responseHeaders")?.let { (code, json) ->
                val headers = parseHeadersJson(json)
                val existing = responses[code] ?: EndpointResponse(statusCode = code)
                responses[code] = existing.copy(headers = headers)
            }
        }

        lines.filter { it.startsWith("@responseLinks") }.forEach { line ->
            parseResponsePayload(line, "@responseLinks")?.let { (code, json) ->
                val links = parseLinksJson(json)
                val existing = responses[code] ?: EndpointResponse(statusCode = code)
                responses[code] = existing.copy(links = links)
            }
        }

        lines.filter { it.startsWith("@responseContent") }.forEach { line ->
            parseResponsePayload(line, "@responseContent")?.let { (code, json) ->
                val content = parseContentJson(json)
                val existing = responses[code] ?: EndpointResponse(statusCode = code)
                val derivedType = existing.type ?: resolveTypeFromContent(content)
                responses[code] = existing.copy(content = content, type = derivedType)
            }
        }
        lines.filter { it.startsWith("@responseSummary") }.forEach { line ->
            val content = line.removePrefix("@responseSummary").trim()
            if (content.isEmpty()) return@forEach
            val parts = content.split(" ", limit = 2)
            val code = parts.getOrNull(0) ?: return@forEach
            val summary = parts.getOrNull(1)
            if (summary.isNullOrBlank()) return@forEach
            val existing = responses[code] ?: EndpointResponse(statusCode = code)
            responses[code] = existing.copy(summary = summary)
        }
        extractResponseRefs(element).forEach { (code, reference) ->
            val existing = responses[code] ?: EndpointResponse(statusCode = code)
            val summary = reference.summary ?: existing.summary
            val description = reference.description ?: existing.description
            responses[code] = existing.copy(
                summary = summary,
                description = description,
                reference = reference
            )
        }
        extractResponseExtensions(element).forEach { (code, extensions) ->
            val existing = responses[code] ?: EndpointResponse(statusCode = code)
            val merged = existing.extensions + extensions
            responses[code] = existing.copy(extensions = merged)
        }
        return responses
    }

    private data class SecurityParseResult(
        val requirements: List<Map<String, List<String>>>,
        val explicitEmpty: Boolean
    )

    private data class RequestBodyParseResult(
        val body: RequestBody,
        val requiredPresent: Boolean
    )

    private fun extractSecurity(element: org.jetbrains.kotlin.psi.KtDeclaration): SecurityParseResult {
        val docComment = element.docComment ?: return SecurityParseResult(emptyList(), false)
        val lines = docComment.text.split("\n").map { cleanKDocLine(it) }
        val securityLines = lines.filter { it.startsWith("@security ") }
        val explicitEmpty = lines.any { it == "@securityEmpty" || it.startsWith("@securityEmpty ") } &&
            securityLines.isEmpty()

        val requirements = securityLines.mapNotNull { line ->
            val raw = line.removePrefix("@security").trim()
            if (raw.isEmpty()) return@mapNotNull null
            val node = runCatching { jsonMapper.readTree(raw) }.getOrNull() ?: return@mapNotNull null
            if (!node.isObject) return@mapNotNull null
            node.fields().asSequence().associate { (key, value) ->
                val scopes = if (value.isArray) {
                    value.mapNotNull { it.textValue() }
                } else {
                    emptyList()
                }
                key to scopes
            }
        }

        return SecurityParseResult(requirements, explicitEmpty)
    }

    private fun parseResponsePayload(line: String, tag: String): Pair<String, String>? {
        val content = line.removePrefix(tag).trim()
        if (content.isEmpty()) return null
        val parts = content.split(" ", limit = 2)
        if (parts.size < 2) return null
        val code = parts[0].trim()
        val json = parts[1].trim()
        if (code.isEmpty() || json.isEmpty()) return null
        return code to json
    }

    private fun parseReferenceObjectValue(raw: String): ReferenceObject? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val node = runCatching { jsonMapper.readTree(trimmed) }.getOrNull()
        if (node != null) {
            if (node.isTextual) {
                return ReferenceObject(ref = node.asText())
            }
            val obj = node.asObject()
            val ref = obj?.text("\$ref") ?: return null
            return ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
        }
        return ReferenceObject(ref = trimmed)
    }

    private fun parseHeadersJson(json: String): Map<String, Header> {
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()
        return node.fields().asSequence().associate { (name, raw) ->
            name to parseHeaderNode(raw)
        }
    }

    private fun parseLinksJson(json: String): Map<String, Link> {
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()
        return node.fields().asSequence().associate { (name, raw) ->
            name to parseLinkNode(raw)
        }
    }

    private fun parseContentJson(json: String): Map<String, MediaTypeObject> {
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return emptyMap()
        return parseContentNode(node)
    }

    private fun parseRequestBodyJson(json: String): RequestBodyParseResult? {
        val node = runCatching { jsonMapper.readTree(json) }.getOrNull() ?: return null
        return parseRequestBodyNode(node)
    }

    private fun parseRequestBodyNode(node: JsonNode): RequestBodyParseResult {
        val obj = node.asObject() ?: return RequestBodyParseResult(RequestBody(), false)
        val ref = obj.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            return RequestBodyParseResult(RequestBody(reference = reference), obj.has("required"))
        }
        val requiredPresent = obj.has("required")
        val hasContent = obj.has("content")
        return RequestBodyParseResult(
            body = RequestBody(
                description = obj.text("description"),
                content = parseContentNode(obj.get("content")),
                contentPresent = hasContent,
                required = obj.boolean("required") ?: false,
                reference = null,
                extensions = parseExtensions(obj)
            ),
            requiredPresent = requiredPresent
        )
    }

    private fun resolveTypeFromContent(content: Map<String, MediaTypeObject>): String? {
        val schema = selectSchema(content) ?: return null
        return TypeMappers.mapType(schema)
    }

    private fun selectSchema(content: Map<String, MediaTypeObject>): SchemaProperty? {
        if (content.isEmpty()) return null
        val preferred = selectPreferredMediaTypeEntry(content).value
        preferred.schema?.let { return it }
        preferred.itemSchema?.let { return wrapItemSchemaAsArray(it) }
        return preferred.reference?.ref?.let { SchemaProperty(ref = it) }
            ?: preferred.ref?.let { SchemaProperty(ref = it) }
    }

    private fun wrapItemSchemaAsArray(itemSchema: SchemaProperty): SchemaProperty {
        return SchemaProperty(types = setOf("array"), items = itemSchema)
    }

    private data class MediaTypeScore(
        val specificity: Int,
        val jsonPreference: Int,
        val length: Int,
        val key: String
    )

    private fun selectPreferredMediaTypeEntry(
        content: Map<String, MediaTypeObject>
    ): Map.Entry<String, MediaTypeObject> {
        if (content.isEmpty()) {
            throw IllegalArgumentException("content map is empty")
        }
        val entries = content.entries.toList()
        val scored = entries.map { entry ->
            entry to mediaTypeScore(entry.key)
        }
        val comparator = Comparator<Pair<Map.Entry<String, MediaTypeObject>, MediaTypeScore>> { a, b ->
            val left = a.second
            val right = b.second
            when {
                left.specificity != right.specificity -> left.specificity.compareTo(right.specificity)
                left.jsonPreference != right.jsonPreference -> left.jsonPreference.compareTo(right.jsonPreference)
                left.length != right.length -> left.length.compareTo(right.length)
                else -> right.key.compareTo(left.key)
            }
        }
        return scored.maxWithOrNull(comparator)?.first ?: entries.first()
    }

    private fun mediaTypeScore(raw: String): MediaTypeScore {
        val main = raw.trim().substringBefore(";").trim().lowercase()
        val parts = main.split("/")
        if (parts.size != 2) {
            return MediaTypeScore(specificity = -1, jsonPreference = 0, length = main.length, key = main)
        }
        val type = parts[0]
        val subtype = parts[1]
        val typeScore = if (type == "*") 0 else 1
        val subtypeScore = when {
            subtype == "*" -> 0
            subtype.startsWith("*+") -> 1
            else -> 2
        }
        val specificity = typeScore * 10 + subtypeScore
        val jsonPreference = if (subtype == "json" || subtype.endsWith("+json")) 1 else 0
        return MediaTypeScore(
            specificity = specificity,
            jsonPreference = jsonPreference,
            length = main.length,
            key = main
        )
    }

    private fun parseHeaderNode(node: JsonNode): Header {
        val obj = node.asObject()
        val ref = obj?.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            return Header(
                type = "String",
                description = reference.description,
                reference = reference
            )
        }

        val schema = obj?.get("schema")?.let { parseSchemaPropertyNode(it) }
        val content = parseContentNode(obj?.get("content"))
        val typeFromSchema = schema ?: selectSchema(content)
        val type = typeFromSchema?.let { TypeMappers.mapType(it) } ?: "String"

        return Header(
            type = type,
            schema = schema,
            content = content,
            description = obj?.text("description"),
            required = obj?.boolean("required") ?: false,
            deprecated = obj?.boolean("deprecated") ?: false,
            example = obj?.get("example")?.let { parseExampleObjectNode(it) },
            examples = parseExampleMapNode(obj?.get("examples")),
            style = parseParamStyle(obj?.text("style")) ?: ParameterStyle.SIMPLE,
            explode = obj?.boolean("explode") ?: false,
            reference = null,
            extensions = parseExtensions(obj)
        )
    }

    private fun parseLinkNode(node: JsonNode): Link {
        val obj = node.asObject() ?: return Link()
        val ref = obj.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            return Link(
                ref = ref,
                reference = reference,
                description = obj.text("description")
            )
        }
        return Link(
            operationId = obj.text("operationId"),
            operationRef = obj.text("operationRef"),
            parameters = obj.get("parameters")?.asObject()?.fields()?.asSequence()?.associate { (k, v) ->
                k to nodeToValue(v)
            } ?: emptyMap(),
            requestBody = obj.get("requestBody")?.let { nodeToValue(it) },
            description = obj.text("description"),
            server = obj.get("server")?.let { parseServerNode(it) },
            extensions = parseExtensions(obj)
        )
    }

    private fun parseContentNode(node: JsonNode?): Map<String, MediaTypeObject> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseMediaTypeNode(raw)
        }
    }

    private fun parseMediaTypeNode(node: JsonNode): MediaTypeObject {
        val obj = node.asObject() ?: return MediaTypeObject()
        val ref = obj.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            return MediaTypeObject(ref = ref, reference = reference)
        }

        return MediaTypeObject(
            schema = obj.get("schema")?.let { parseSchemaPropertyNode(it) },
            itemSchema = obj.get("itemSchema")?.let { parseSchemaPropertyNode(it) },
            example = obj.get("example")?.let { parseExampleObjectNode(it) },
            examples = parseExampleMapNode(obj.get("examples")),
            encoding = parseEncodingMapNode(obj.get("encoding")),
            prefixEncoding = parseEncodingArrayNode(obj.get("prefixEncoding")),
            itemEncoding = obj.get("itemEncoding")?.let { parseEncodingNode(it) },
            extensions = parseExtensions(obj)
        )
    }

    private fun parseEncodingMapNode(node: JsonNode?): Map<String, EncodingObject> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseEncodingNode(raw)
        }
    }

    private fun parseEncodingArrayNode(node: JsonNode?): List<EncodingObject> {
        val array = node.asArray() ?: return emptyList()
        return array.map { parseEncodingNode(it) }
    }

    private fun parseEncodingNode(node: JsonNode): EncodingObject {
        val obj = node.asObject() ?: return EncodingObject()
        return EncodingObject(
            contentType = obj.text("contentType"),
            headers = parseHeadersNode(obj.get("headers")),
            style = parseParamStyle(obj.text("style")),
            explode = obj.boolean("explode"),
            allowReserved = obj.boolean("allowReserved"),
            encoding = parseEncodingMapNode(obj.get("encoding")),
            prefixEncoding = parseEncodingArrayNode(obj.get("prefixEncoding")),
            itemEncoding = obj.get("itemEncoding")?.let { parseEncodingNode(it) },
            extensions = parseExtensions(obj)
        )
    }

    private fun parseHeadersNode(node: JsonNode?): Map<String, Header> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseHeaderNode(raw)
        }
    }

    private fun parseExampleMapNode(node: JsonNode?): Map<String, ExampleObject> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseExampleObjectNode(raw)
        }
    }

    private fun parseExampleObjectNode(node: JsonNode): ExampleObject {
        val obj = node.asObject() ?: return ExampleObject(value = nodeToValue(node))
        val ref = obj.text("\$ref")
        if (ref != null) {
            return ExampleObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
        }
        return ExampleObject(
            summary = obj.text("summary"),
            description = obj.text("description"),
            dataValue = obj.get("dataValue")?.let { nodeToValue(it) },
            serializedValue = obj.text("serializedValue"),
            externalValue = obj.text("externalValue"),
            value = obj.get("value")?.let { nodeToValue(it) },
            extensions = parseExtensions(obj)
        )
    }

    private fun parseSchemaPropertyNode(node: JsonNode): SchemaProperty {
        if (node.isBoolean) {
            return SchemaProperty(booleanSchema = node.booleanValue())
        }
        val obj = node.asObject()
        val ref = obj?.text("\$ref")
        val dynamicRef = obj?.text("\$dynamicRef")
        val customKeywords = parseCustomSchemaKeywords(obj)

        return SchemaProperty(
            booleanSchema = null,
            ref = ref,
            dynamicRef = dynamicRef,
            types = parseTypes(obj),
            schemaId = obj?.text("\$id"),
            schemaDialect = obj?.text("\$schema"),
            anchor = obj?.text("\$anchor"),
            dynamicAnchor = obj?.text("\$dynamicAnchor"),
            format = obj?.text("format"),
            contentMediaType = obj?.text("contentMediaType"),
            contentEncoding = obj?.text("contentEncoding"),
            minLength = obj?.int("minLength"),
            maxLength = obj?.int("maxLength"),
            pattern = obj?.text("pattern"),
            minimum = obj?.double("minimum"),
            maximum = obj?.double("maximum"),
            multipleOf = obj?.double("multipleOf"),
            exclusiveMinimum = obj?.double("exclusiveMinimum"),
            exclusiveMaximum = obj?.double("exclusiveMaximum"),
            minItems = obj?.int("minItems"),
            maxItems = obj?.int("maxItems"),
            uniqueItems = obj?.boolean("uniqueItems"),
            minProperties = obj?.int("minProperties"),
            maxProperties = obj?.int("maxProperties"),
            items = obj?.get("items")?.let { parseSchemaPropertyNode(it) },
            prefixItems = parseSchemaPropertyList(obj?.get("prefixItems")),
            contains = obj?.get("contains")?.let { parseSchemaPropertyNode(it) },
            minContains = obj?.int("minContains"),
            maxContains = obj?.int("maxContains"),
            properties = parseSchemaProperties(obj?.get("properties")),
            required = obj?.get("required")?.asArray()?.mapNotNull { it.textValue() } ?: emptyList(),
            additionalProperties = parseAdditionalProperties(obj?.get("additionalProperties")),
            defs = parseSchemaProperties(obj?.get("\$defs")),
            patternProperties = parseSchemaProperties(obj?.get("patternProperties")),
            propertyNames = obj?.get("propertyNames")?.let { parseSchemaPropertyNode(it) },
            dependentRequired = parseDependentRequired(obj?.get("dependentRequired")),
            dependentSchemas = parseSchemaProperties(obj?.get("dependentSchemas")),
            description = obj?.text("description"),
            title = obj?.text("title"),
            defaultValue = obj?.get("default")?.let { nodeToValue(it) },
            constValue = obj?.get("const")?.let { nodeToValue(it) },
            deprecated = obj?.boolean("deprecated") ?: false,
            readOnly = obj?.boolean("readOnly") ?: false,
            writeOnly = obj?.boolean("writeOnly") ?: false,
            externalDocs = parseExternalDocsNode(obj?.get("externalDocs")),
            discriminator = parseDiscriminatorNode(obj?.get("discriminator")),
            comment = obj?.text("\$comment"),
            enumValues = parseEnumValues(obj?.get("enum")),
            oneOf = parseSchemaPropertyList(obj?.get("oneOf")),
            anyOf = parseSchemaPropertyList(obj?.get("anyOf")),
            allOf = parseSchemaPropertyList(obj?.get("allOf")),
            not = obj?.get("not")?.let { parseSchemaPropertyNode(it) },
            ifSchema = obj?.get("if")?.let { parseSchemaPropertyNode(it) },
            thenSchema = obj?.get("then")?.let { parseSchemaPropertyNode(it) },
            elseSchema = obj?.get("else")?.let { parseSchemaPropertyNode(it) },
            example = obj?.get("example")?.let { nodeToValue(it) },
            examples = parseSchemaExamplesList(obj?.get("examples")),
            xml = parseXmlNode(obj?.get("xml")),
            unevaluatedProperties = obj?.get("unevaluatedProperties")?.let { parseSchemaPropertyNode(it) },
            unevaluatedItems = obj?.get("unevaluatedItems")?.let { parseSchemaPropertyNode(it) },
            contentSchema = obj?.get("contentSchema")?.let { parseSchemaPropertyNode(it) },
            customKeywords = customKeywords,
            extensions = parseExtensions(obj)
        )
    }

    private fun parseSchemaProperties(node: JsonNode?): Map<String, SchemaProperty> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseSchemaPropertyNode(raw)
        }
    }

    private fun parseSchemaPropertyList(node: JsonNode?): List<SchemaProperty> {
        val array = node.asArray() ?: return emptyList()
        return array.map { parseSchemaPropertyNode(it) }
    }

    private fun parseAdditionalProperties(node: JsonNode?): SchemaProperty? {
        return when {
            node == null -> null
            node.isBoolean -> SchemaProperty(booleanSchema = node.booleanValue())
            node.isObject -> parseSchemaPropertyNode(node)
            else -> null
        }
    }

    private fun parseDependentRequired(node: JsonNode?): Map<String, List<String>> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (key, raw) ->
            val list = raw.asArray()?.mapNotNull { it.textValue() } ?: emptyList()
            key to list
        }
    }

    private fun parseEnumValues(node: JsonNode?): List<Any?>? {
        val array = node.asArray() ?: return null
        return array.map { nodeToValue(it) }
    }

    private fun parseSchemaExamplesList(node: JsonNode?): List<Any?>? {
        if (node == null || !node.isArray) return null
        return node.map { nodeToValue(it) }
    }

    private fun parseDiscriminatorNode(node: JsonNode?): Discriminator? {
        val obj = node.asObject() ?: return null
        return Discriminator(
            propertyName = obj.text("propertyName") ?: "type",
            mapping = obj.get("mapping")?.asObject()?.fields()?.asSequence()?.associate { (k, v) ->
                k to (v.textValue() ?: v.toString())
            } ?: emptyMap(),
            defaultMapping = obj.text("defaultMapping"),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseInfoNode(node: JsonNode?): Info? {
        val obj = node.asObject() ?: return null
        val title = obj.text("title") ?: return null
        val version = obj.text("version") ?: return null
        return Info(
            title = title,
            version = version,
            summary = obj.text("summary"),
            description = obj.text("description"),
            termsOfService = obj.text("termsOfService"),
            contact = parseContactNode(obj.get("contact")),
            license = parseLicenseNode(obj.get("license")),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseContactNode(node: JsonNode?): Contact? {
        val obj = node.asObject() ?: return null
        return Contact(
            name = obj.text("name"),
            url = obj.text("url"),
            email = obj.text("email"),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseLicenseNode(node: JsonNode?): License? {
        val obj = node.asObject() ?: return null
        val name = obj.text("name") ?: return null
        return License(
            name = name,
            identifier = obj.text("identifier"),
            url = obj.text("url"),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseTagNode(node: JsonNode?): Tag? {
        val obj = node.asObject() ?: return null
        val name = obj.text("name") ?: return null
        return Tag(
            name = name,
            summary = obj.text("summary"),
            description = obj.text("description"),
            externalDocs = parseExternalDocsNode(obj.get("externalDocs")),
            parent = obj.text("parent"),
            kind = obj.text("kind"),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseExternalDocsNode(node: JsonNode?): ExternalDocumentation? {
        val obj = node.asObject() ?: return null
        val url = obj.text("url") ?: return null
        return ExternalDocumentation(
            description = obj.text("description"),
            url = url,
            extensions = parseExtensions(obj)
        )
    }

    private fun parseXmlNode(node: JsonNode?): Xml? {
        val obj = node.asObject() ?: return null
        return Xml(
            name = obj.text("name"),
            namespace = obj.text("namespace"),
            prefix = obj.text("prefix"),
            nodeType = obj.text("nodeType"),
            attribute = obj.boolean("attribute") ?: false,
            wrapped = obj.boolean("wrapped") ?: false,
            extensions = parseExtensions(obj)
        )
    }

    private fun parseServerNode(node: JsonNode): Server? {
        val obj = node.asObject() ?: return null
        return Server(
            url = obj.text("url") ?: "/",
            description = obj.text("description"),
            variables = parseServerVariables(obj.get("variables")),
            name = obj.text("name"),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseServerVariables(node: JsonNode?): Map<String, ServerVariable> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            val value = raw.asObject()
            val enumValues = value?.get("enum")?.asArray()?.mapNotNull { it.textValue() }
            name to ServerVariable(
                default = value?.text("default") ?: "",
                enum = enumValues,
                description = value?.text("description"),
                extensions = parseExtensions(value)
            )
        }
    }

    private fun parseTypes(obj: JsonNode?): Set<String> {
        val node = obj?.get("type") ?: return emptySet()
        return when {
            node.isTextual -> setOf(node.asText())
            node.isArray -> node.mapNotNull { it.textValue() }.toSet()
            else -> emptySet()
        }
    }

    private fun parseCustomSchemaKeywords(node: JsonNode?): Map<String, Any?> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence()
            .filterNot { (key, _) -> key.startsWith("x-") || schemaKnownKeys.contains(key) }
            .associate { (key, value) -> key to nodeToValue(value) }
    }

    private fun parseExtensions(node: JsonNode?): Map<String, Any?> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence()
            .filter { (key, _) -> key.startsWith("x-") }
            .associate { (key, value) -> key to nodeToValue(value) }
    }

    private fun nodeToValue(node: JsonNode): Any? {
        return when {
            node.isNull -> null
            node.isTextual -> node.asText()
            node.isNumber -> node.numberValue()
            node.isBoolean -> node.booleanValue()
            node.isArray -> node.map { nodeToValue(it) }
            node.isObject -> node.fields().asSequence().associate { (k, v) -> k to nodeToValue(v) }
            else -> node.toString()
        }
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

    private fun extractOperationIdOmitted(element: org.jetbrains.kotlin.psi.KtDeclaration): Boolean {
        val docComment = element.docComment ?: return false
        return docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .any { it == "@operationIdOmitted" || it.startsWith("@operationIdOmitted ") }
    }

    private fun extractDeprecated(element: org.jetbrains.kotlin.psi.KtDeclaration): Boolean {
        val docComment = element.docComment
        val hasDocTag = docComment?.text
            ?.split("\n")
            ?.map { cleanKDocLine(it) }
            ?.any { it == "@deprecated" || it.startsWith("@deprecated ") }
            ?: false
        val hasAnnotation = (element as? org.jetbrains.kotlin.psi.KtModifierListOwner)
            ?.annotationEntries
            ?.any { it.shortName?.asString() == "Deprecated" }
            ?: false
        return hasDocTag || hasAnnotation
    }

    private fun parseParameterStyle(value: String): domain.ParameterStyle? {
        return when (value.lowercase()) {
            "matrix" -> domain.ParameterStyle.MATRIX
            "label" -> domain.ParameterStyle.LABEL
            "simple" -> domain.ParameterStyle.SIMPLE
            "form" -> domain.ParameterStyle.FORM
            "cookie" -> domain.ParameterStyle.COOKIE
            "spacedelimited", "space_delimited", "spaceDelimited" -> domain.ParameterStyle.SPACE_DELIMITED
            "pipedelimited", "pipe_delimited", "pipeDelimited" -> domain.ParameterStyle.PIPE_DELIMITED
            "deepobject", "deep_object", "deepObject" -> domain.ParameterStyle.DEEP_OBJECT
            else -> null
        }
    }

    private fun parseParamStyle(value: String?): domain.ParameterStyle? {
        return value?.let { parseParameterStyle(it) }
    }

    private fun parseBooleanTagValue(value: String?): Boolean? {
        return when (value?.lowercase()) {
            null -> true
            "true" -> true
            "false" -> false
            else -> null
        }
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

private fun JsonNode?.asObject(): JsonNode? = if (this != null && this.isObject) this else null

private fun JsonNode?.asArray(): List<JsonNode>? = if (this != null && this.isArray) this.toList() else null

private fun JsonNode.text(field: String): String? = this.get(field)?.takeIf { !it.isNull }?.asText()

private fun JsonNode.boolean(field: String): Boolean? = this.get(field)?.takeIf { it.isBoolean }?.booleanValue()

private fun JsonNode.int(field: String): Int? = this.get(field)?.takeIf { it.isNumber }?.intValue()

private fun JsonNode.double(field: String): Double? = this.get(field)?.takeIf { it.isNumber }?.doubleValue()
