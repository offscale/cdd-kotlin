package psi

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import domain.Callback
import domain.Discriminator
import domain.EncodingObject
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.ExampleObject
import domain.ExternalDocumentation
import domain.Header
import domain.HttpMethod
import domain.Info
import domain.Link
import domain.MediaTypeObject
import domain.OpenApiDefinition
import domain.ParameterLocation
import domain.ParameterStyle
import domain.PathItem
import domain.RequestBody
import domain.ReferenceObject
import domain.SchemaProperty
import domain.SecurityScheme
import domain.Server
import domain.Tag
import domain.Xml
import openapi.OpenApiWriter
import org.jetbrains.kotlin.psi.KtFile

/**
 * Generates Ktor Network Interface and Implementation code from Endpoint Definitions.
 * Supports:
 * - Result<T> return types
 * - Path, Query, Querystring, Header, Cookie parameters
 * - Parameter Serialization Styles (Matrix, Label, Form, Pipe/Space Delimited, DeepObject) and Explode logic
 * - Query parameter allowReserved/allowEmptyValue behaviors for compliant URL serialization
 * - Server Base URL configuration
 * - KDoc generation (including callbacks, response summaries, and operation extensions)
 * - Interface-level KDoc for root OpenAPI metadata (info, servers, security, tags, externalDocs, extensions, securitySchemes)
 * - Ktor Auth Plugin configuration (Basic, Bearer, ApiKey)
 * - Header/Cookie/Path array & object serialization (simple, matrix, label, cookie/form)
 */
class NetworkGenerator {

    private val psiFactory = PsiInfrastructure.createPsiFactory()
    private val jsonMapper = ObjectMapper(JsonFactory())
    private val openApiWriter = OpenApiWriter()

    /**
     * Generates a complete API file with Interface, Implementation, and Exception classes.
     *
     * @param packageName The target package.
     * @param apiName The class name for the API.
     * @param endpoints List of endpoints to generate.
     * @param servers List of Server definitions for configuration.
     * @param securitySchemes Map of Security Schemes defined in Components.
     * @param webhooks Optional webhook definitions to preserve via KDoc metadata.
     * @param metadata Optional root-level OpenAPI metadata to preserve via interface KDoc.
     */
    fun generateApi(
        packageName: String,
        apiName: String,
        endpoints: List<EndpointDefinition>,
        servers: List<Server> = emptyList(),
        securitySchemes: Map<String, SecurityScheme> = emptyMap(),
        webhooks: Map<String, PathItem> = emptyMap(),
        metadata: OpenApiMetadata? = null
    ): KtFile {
        val resolvedMetadata = metadata ?: OpenApiMetadata()
        val resolvedServers = if (resolvedMetadata.servers.isNotEmpty()) resolvedMetadata.servers else servers
        val resolvedSecuritySchemes = if (resolvedMetadata.securitySchemes.isNotEmpty()) {
            resolvedMetadata.securitySchemes
        } else {
            securitySchemes
        }

        // imports
        val baseImports = mutableSetOf(
            "io.ktor.client.*",
            "io.ktor.client.call.*",
            "io.ktor.client.request.*",
            "io.ktor.http.*"
        )

        // Add Auth imports if needed
        if (resolvedSecuritySchemes.isNotEmpty()) {
            baseImports.add("io.ktor.client.plugins.*")
            baseImports.add("io.ktor.client.plugins.auth.*")
            baseImports.add("io.ktor.client.plugins.auth.providers.*")
        }

        val importsBlock = """
            package $packageName
            
            ${baseImports.sorted().joinToString("\n") { "import $it" }}
            
            // Result is part of kotlin standard library since 1.3
        """.trimIndent()

        val interfaceName = "I$apiName"
        val implName = apiName

        // Generate Interface Methods
        val interfaceMethods = endpoints.joinToString("\n\n") { ep ->
            val signature = generateMethodSignature(ep)
            val doc = generateKDoc(ep)
            "$doc    $signature"
        }

        // Generate Implementation Methods
        val implMethods = endpoints.joinToString("\n\n") { ep ->
            val doc = generateKDoc(ep)
            val impl = generateMethodImpl(ep)
            "$doc    $impl"
        }

        val helperFunctions = buildHelperFunctions(endpoints)

        // Server Logic
        val defaultUrl = if (resolvedServers.isNotEmpty()) resolvedServers.first().url else ""
        val serverVariableSupport = buildServerVariableSupport(resolvedServers)

        // Companion Object (Servers + Auth Factory)
        val companionObject = buildCompanionObject(resolvedServers, resolvedSecuritySchemes, serverVariableSupport)

        val baseUrlParam = when {
            serverVariableSupport != null -> "private val baseUrl: String = ${serverVariableSupport.baseUrlDefault}"
            defaultUrl.isNotEmpty() -> "private val baseUrl: String = \"$defaultUrl\""
            else -> "private val baseUrl: String = \"\""
        }

        val interfaceDoc = generateInterfaceKDoc(resolvedMetadata, webhooks, resolvedServers, resolvedSecuritySchemes)

        val content = """
            $importsBlock
            
            $interfaceDoc
            interface $interfaceName {
            $interfaceMethods
            }
            
            class $implName(
                private val client: HttpClient,
                $baseUrlParam
            ) : $interfaceName {
            $implMethods
            $helperFunctions
            $companionObject
            }
            
            class ApiException(message: String) : Exception(message)
        """.trimIndent()

        return psiFactory.createFile("$apiName.kt", content)
    }

    private fun generateInterfaceKDoc(
        metadata: OpenApiMetadata,
        webhooks: Map<String, PathItem>,
        servers: List<Server>,
        securitySchemes: Map<String, SecurityScheme>
    ): String {
        val hasOpenapi = metadata.openapi != null || metadata.jsonSchemaDialect != null || metadata.self != null
        val hasInfo = metadata.info != null
        val hasServers = servers.isNotEmpty()
        val hasSecurity = metadata.security.isNotEmpty()
        val hasSecurityEmpty = metadata.securityExplicitEmpty && metadata.security.isEmpty()
        val hasTags = metadata.tags.isNotEmpty()
        val hasExternalDocs = metadata.externalDocs != null
        val hasExtensions = metadata.extensions.isNotEmpty()
        val hasPathsExtensions = metadata.pathsExtensions.isNotEmpty()
        val hasWebhooksExtensions = metadata.webhooksExtensions.isNotEmpty()
        val hasSecuritySchemes = securitySchemes.isNotEmpty()
        val hasWebhooks = webhooks.isNotEmpty()

        if (!hasOpenapi && !hasInfo && !hasServers && !hasSecurity && !hasSecurityEmpty &&
            !hasTags && !hasExternalDocs && !hasExtensions && !hasSecuritySchemes && !hasWebhooks &&
            !hasPathsExtensions && !hasWebhooksExtensions
        ) {
            return ""
        }

        val sb = StringBuilder("/**\n")
        if (hasOpenapi) {
            sb.append(" * @openapi ${renderOpenApiMeta(metadata)}\n")
        }
        if (hasInfo) {
            sb.append(" * @info ${renderInfo(metadata.info!!)}\n")
        }
        if (hasServers) {
            val serversJson = jsonMapper.writeValueAsString(servers.map { serverToDocValue(it) })
            sb.append(" * @servers $serversJson\n")
        }
        if (hasSecurityEmpty) {
            sb.append(" * @securityEmpty\n")
        }
        if (hasSecurity) {
            metadata.security.forEach { requirement ->
                sb.append(" * @security ${renderSecurityRequirement(requirement)}\n")
            }
        }
        if (hasTags) {
            sb.append(" * @tags ${renderTags(metadata.tags)}\n")
        }
        if (hasExternalDocs) {
            sb.append(" * @externalDocs ${renderExternalDocs(metadata.externalDocs!!)}\n")
        }
        if (hasExtensions) {
            sb.append(" * @extensions ${jsonMapper.writeValueAsString(metadata.extensions)}\n")
        }
        if (hasPathsExtensions) {
            sb.append(" * @pathsExtensions ${renderExtensions(metadata.pathsExtensions)}\n")
        }
        if (hasWebhooksExtensions) {
            sb.append(" * @webhooksExtensions ${renderExtensions(metadata.webhooksExtensions)}\n")
        }
        if (hasSecuritySchemes) {
            sb.append(" * @securitySchemes ${renderSecuritySchemes(securitySchemes)}\n")
        }
        if (hasWebhooks) {
            sb.append(" * @webhooks ${renderWebhooks(webhooks)}\n")
        }
        sb.append(" */")
        return sb.toString()
    }

    private fun renderOpenApiMeta(metadata: OpenApiMetadata): String {
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("openapi", metadata.openapi)
        map.putIfNotNull("jsonSchemaDialect", metadata.jsonSchemaDialect)
        map.putIfNotNull("\$self", metadata.self)
        return jsonMapper.writeValueAsString(map)
    }

    private fun renderExtensions(extensions: Map<String, Any?>): String {
        val filtered = extensions.filterKeys { it.startsWith("x-") }
        return jsonMapper.writeValueAsString(filtered)
    }

    private fun renderInfo(info: Info): String {
        return jsonMapper.writeValueAsString(infoToDocValue(info))
    }

    private fun renderTags(tags: List<Tag>): String {
        val tagValues = tags.map { tagToDocValue(it) }
        return jsonMapper.writeValueAsString(tagValues)
    }

    private fun renderExternalDocs(docs: ExternalDocumentation): String {
        return jsonMapper.writeValueAsString(externalDocsToDocValue(docs))
    }

    private fun renderSecuritySchemes(securitySchemes: Map<String, SecurityScheme>): String {
        if (securitySchemes.isEmpty()) return "{}"
        val tempDefinition = OpenApiDefinition(
            info = Info(title = "Security Schemes", version = "0.0.0"),
            components = domain.Components(securitySchemes = securitySchemes)
        )
        val json = openApiWriter.writeJson(tempDefinition)
        val node = jsonMapper.readTree(json)
        val schemesNode = node.path("components").path("securitySchemes")
        return jsonMapper.writeValueAsString(schemesNode)
    }

    private fun renderWebhooks(webhooks: Map<String, PathItem>): String {
        if (webhooks.isEmpty()) return "{}"
        val tempDefinition = OpenApiDefinition(
            info = Info(title = "Webhooks", version = "0.0.0"),
            webhooks = webhooks
        )
        val json = openApiWriter.writeJson(tempDefinition)
        val node = jsonMapper.readTree(json)
        val webhooksNode = node.path("webhooks")
        return jsonMapper.writeValueAsString(webhooksNode)
    }

    private fun buildCompanionObject(
        servers: List<Server>,
        securitySchemes: Map<String, SecurityScheme>,
        serverVariableSupport: ServerVariableSupport?
    ): String {
        val blocks = mutableListOf<String>()

        if (servers.isNotEmpty()) {
            val listStr = servers.joinToString(", ") { "\"${it.url}\"" }
            blocks.add("val SERVERS = listOf($listStr)")
        }

        serverVariableSupport?.companionSnippet?.takeIf { it.isNotBlank() }?.let { blocks.add(it) }

        if (securitySchemes.isNotEmpty()) {
            blocks.add(generateAuthFactory(securitySchemes))
        }

        if (blocks.isEmpty()) return ""

        val body = blocks.joinToString("\n\n").prependIndent("        ")
        return "    companion object {\n$body\n    }"
    }

    private fun buildHelperFunctions(endpoints: List<EndpointDefinition>): String {
        val needsAllowReserved = endpoints.any { ep ->
            ep.parameters.any { it.location == ParameterLocation.QUERY && it.allowReserved == true }
        }
        if (!needsAllowReserved) return ""

        return """
            private fun encodeAllowReserved(value: String): String {
                if (value.isEmpty()) return value
                val sb = StringBuilder()
                var i = 0
                while (i < value.length) {
                    val ch = value[i]
                    if (ch == '%' && i + 2 < value.length && isHexDigit(value[i + 1]) && isHexDigit(value[i + 2])) {
                        sb.append(ch).append(value[i + 1]).append(value[i + 2])
                        i += 3
                        continue
                    }
                    if (isUnreserved(ch) || isReserved(ch)) {
                        sb.append(ch)
                        i += 1
                        continue
                    }
                    val bytes = ch.toString().toByteArray(Charsets.UTF_8)
                    for (b in bytes) {
                        sb.append('%')
                        sb.append(byteToHex(b))
                    }
                    i += 1
                }
                return sb.toString()
            }

            private fun isUnreserved(ch: Char): Boolean {
                return ch.isLetterOrDigit() || ch == '-' || ch == '.' || ch == '_' || ch == '~'
            }

            private fun isReserved(ch: Char): Boolean {
                return ":/?#[]@!${'$'}&'()*+,;=".indexOf(ch) >= 0
            }

            private fun isHexDigit(ch: Char): Boolean {
                return ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'
            }

            private fun byteToHex(b: Byte): String {
                val value = b.toInt() and 0xFF
                val digits = "0123456789ABCDEF"
                return "${'$'}{digits[value ushr 4]}${'$'}{digits[value and 0x0F]}"
            }
        """.trimIndent().prependIndent("    ")
    }

    private data class ServerVariableSupport(
        val baseUrlDefault: String,
        val companionSnippet: String
    )

    private data class QueryParamLines(
        val lines: List<String>,
        val emptyValueLine: String?
    )

    private fun buildServerVariableSupport(servers: List<Server>): ServerVariableSupport? {
        val server = servers.firstOrNull() ?: return null
        val variables = server.variables?.takeIf { it.isNotEmpty() } ?: return null

        val usedNames = mutableSetOf<String>()
        val propertyLines = mutableListOf<String>()
        val mapEntries = mutableListOf<String>()

        variables.entries.forEachIndexed { index, (rawName, variable) ->
            var safeName = sanitizeVariableName(rawName)
            if (safeName.isBlank()) {
                safeName = "var${index + 1}"
            }
            var uniqueName = safeName
            var suffix = 2
            while (!usedNames.add(uniqueName)) {
                uniqueName = "${safeName}_${suffix++}"
            }
            val defaultValue = variable.default.replace("\"", "\\\"")
            propertyLines.add("val $uniqueName: String = \"$defaultValue\"")
            mapEntries.add("\"$rawName\" to $uniqueName")
        }

        val dataClassBlock = """
            data class ServerVariables(
                ${propertyLines.joinToString(",\n                ")}
            ) {
                fun toMap(): Map<String, String> = mapOf(
                    ${mapEntries.joinToString(",\n                    ")}
                )
            }
        """.trimIndent()

        val resolverBlock = """
            fun resolveServerUrl(template: String, variables: Map<String, String>): String {
                var resolved = template
                variables.forEach { (key, value) ->
                    resolved = resolved.replace("{${'$'}key}", value)
                }
                return resolved
            }

            fun defaultBaseUrl(variables: ServerVariables = ServerVariables()): String {
                return resolveServerUrl(SERVERS.first(), variables.toMap())
            }
        """.trimIndent()

        return ServerVariableSupport(
            baseUrlDefault = "defaultBaseUrl()",
            companionSnippet = "$dataClassBlock\n\n$resolverBlock"
        )
    }

    private fun sanitizeVariableName(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_')
        val base = if (cleaned.isEmpty()) "var" else cleaned
        val normalized = if (base.first().isDigit()) "_$base" else base
        return normalized.replaceFirstChar { it.lowercase() }
    }

    private fun generateAuthFactory(schemes: Map<String, SecurityScheme>): String {
        // Collect arguments for the factory function
        // e.g. "myApiKey: String? = null, bearerToken: String? = null"
        val args = schemes.entries.map { (key, scheme) ->
            val paramName = sanitizeIdentifier(key)
            // For basic auth we might usually need user/pass, simplifying to a config object or pair is better,
            // but for this generation we'll keep it flat: "keyUsername: String?, keyPassword: String?" logic is complex.
            // Simplified: Treat Basic as a single "Credentials" object or separate args.
            // To maintain single-token simplicity for this generator step:
            if (scheme.scheme == "basic") {
                "${paramName}User: String? = null, ${paramName}Pass: String? = null"
            } else {
                "$paramName: String? = null"
            }
        }.joinToString(",\n            ")

        // Build Install blocks
        val installBlocks = StringBuilder()
        val hasHttpAuth = schemes.values.any { it.type == "http" }
        val apiKeys = schemes.entries.filter { it.value.type == "apiKey" }

        // 1. HTTP Auth (Bearer / Basic)
        if (hasHttpAuth) {
            installBlocks.append("install(Auth) {\n")
            schemes.forEach { (key, scheme) ->
                val paramName = sanitizeIdentifier(key)
                if (scheme.type == "http") {
                    when (scheme.scheme?.lowercase()) {
                        "basic" -> {
                            installBlocks.append("""
                                basic {
                                    credentials {
                                        if (${paramName}User != null && ${paramName}Pass != null) {
                                            BasicAuthCredentials(username = ${paramName}User, password = ${paramName}Pass)
                                        } else null
                                    }
                                }
                            """.trimIndent().prependIndent("                ") + "\n")
                        }
                        "bearer" -> {
                            installBlocks.append("""
                                bearer {
                                    loadTokens {
                                        if ($paramName != null) {
                                            BearerTokens(accessToken = $paramName, refreshToken = null)
                                        } else null
                                    }
                                }
                            """.trimIndent().prependIndent("                ") + "\n")
                        }
                    }
                }
            }
            installBlocks.append("            }\n")
        }

        // 2. Api Keys (DefaultRequest)
        if (apiKeys.isNotEmpty()) {
            installBlocks.append("            install(DefaultRequest) {\n")
            apiKeys.forEach { (key, scheme) ->
                val paramName = sanitizeIdentifier(key)
                val headerName = scheme.name ?: key
                // `in` is a reserved keyword in Kotlin, accessed via backticks in domain model but field val is string
                val location = scheme.`in`

                // Only generate if param passed is not null
                val block = when (location?.lowercase()) {
                    "header" -> "header(\"$headerName\", $paramName)"
                    "query" -> "url.parameters.append(\"$headerName\", $paramName)"
                    "cookie" -> "cookie(\"$headerName\", $paramName)"
                    else -> "// Unsupported apiKey location: $location"
                }

                installBlocks.append("""
                    if ($paramName != null) {
                        $block
                    }
                """.trimIndent().prependIndent("                ") + "\n")
            }
            installBlocks.append("            }\n")
        }

        return """
        
        /**
         * Creates a Ktor HttpClient configured with the defined Security Schemes.
         */
        fun createHttpClient(
            $args
        ): HttpClient {
            return HttpClient {
    $installBlocks        }
        }
        """.trimIndent().replace("\n", "\n    ") // Indent companion body
    }

    private fun generateKDoc(ep: EndpointDefinition): String {
        val hasSummary = !ep.summary.isNullOrBlank()
        val hasDescription = !ep.description.isNullOrBlank()
        val hasExtDocs = ep.externalDocs != null
        val hasTags = ep.tags.isNotEmpty()
        val hasResponses = ep.responses.isNotEmpty()
        val responsesWithHeaders = ep.responses.filterValues { it.headers.isNotEmpty() }
        val responsesWithLinks = ep.responses.filterValues { it.links?.isNotEmpty() == true }
        val responsesWithContent = ep.responses.filterValues { it.content.isNotEmpty() }
        val responsesWithSummary = ep.responses.filterValues { !it.summary.isNullOrBlank() }
        val responsesWithRef = ep.responses.filterValues { it.reference != null }
        val responsesWithExtensions = ep.responses.filterValues { it.extensions.isNotEmpty() }
        val hasResponseHeaders = responsesWithHeaders.isNotEmpty()
        val hasResponseLinks = responsesWithLinks.isNotEmpty()
        val hasResponseContent = responsesWithContent.isNotEmpty()
        val hasResponseSummary = responsesWithSummary.isNotEmpty()
        val hasResponseRef = responsesWithRef.isNotEmpty()
        val hasResponseExtensions = responsesWithExtensions.isNotEmpty()
        val hasServers = ep.servers.isNotEmpty()
        val paramsWithDocs = ep.parameters.filter { !it.description.isNullOrBlank() }
        val paramsWithExamples = ep.parameters.filter { it.example != null || it.examples.isNotEmpty() }
        val paramsWithMeta = ep.parameters.filter {
            it.style != null || it.explode != null || it.allowReserved != null || it.allowEmptyValue != null
        }
        val paramsWithRef = ep.parameters.filter { it.reference != null }
        val paramsWithSchema = ep.parameters.filter { it.schema != null && it.content.isEmpty() }
        val paramsWithContent = ep.parameters.filter { it.content.isNotEmpty() }
        val paramsWithExtensions = ep.parameters.filter { it.extensions.isNotEmpty() }
        val hasParams = paramsWithDocs.isNotEmpty()
        val hasParamMeta = paramsWithMeta.isNotEmpty()
        val hasParamRef = paramsWithRef.isNotEmpty()
        val hasParamSchema = paramsWithSchema.isNotEmpty()
        val hasParamContent = paramsWithContent.isNotEmpty()
        val hasParamExtensions = paramsWithExtensions.isNotEmpty()
        val requestBody = ep.requestBody
        val hasRequestBody = requestBody != null && (
            requestBody.reference != null ||
                requestBody.description != null ||
                requestBody.required ||
                requestBody.content.isNotEmpty() ||
            requestBody.extensions.isNotEmpty()
            )
        val hasDeprecated = ep.deprecated
        val hasSecurity = ep.security.isNotEmpty()
        val hasSecurityEmpty = ep.securityExplicitEmpty && ep.security.isEmpty()
        val hasCallbacks = ep.callbacks.isNotEmpty()
        val hasExtensions = ep.extensions.isNotEmpty()

        if (!hasSummary && !hasDescription && !hasExtDocs && !hasTags && !hasResponses && !hasServers &&
            !hasResponseHeaders && !hasResponseLinks && !hasResponseContent && !hasResponseSummary &&
            !hasParams && paramsWithExamples.isEmpty() && !hasParamMeta && !hasParamRef && !hasParamSchema &&
            !hasParamContent && !hasParamExtensions && !hasResponseRef && !hasResponseExtensions &&
            !hasRequestBody && !hasDeprecated && !hasSecurity &&
            !hasSecurityEmpty && !hasCallbacks && !hasExtensions
        ) {
            return ""
        }

        val sb = StringBuilder("    /**\n")
        if (hasSummary) {
            sb.append("     * ${ep.summary}\n")
        }
        if (hasDescription) {
            if (hasSummary) sb.append("     *\n")
            sb.append("     * ${ep.description}\n")
        }
        if (hasExtDocs) {
            val docs = ep.externalDocs!!
            if (docs.extensions.isNotEmpty()) {
                sb.append("     * @externalDocs ${renderExternalDocs(docs)}\n")
            } else {
                sb.append("     * @see ${docs.url}")
                if (docs.description != null) {
                    sb.append(" ${docs.description}")
                }
                sb.append("\n")
            }
        }
        if (hasTags) {
            val tagStr = ep.tags.joinToString(", ")
            sb.append("     * @tag $tagStr\n")
        }
        if (hasServers) {
            val serversJson = jsonMapper.writeValueAsString(ep.servers.map { serverToDocValue(it) })
            sb.append("     * @servers $serversJson\n")
        }
        if (hasSecurityEmpty) {
            sb.append("     * @securityEmpty\n")
        }
        if (hasSecurity) {
            ep.security.forEach { requirement ->
                sb.append("     * @security ${renderSecurityRequirement(requirement)}\n")
            }
        }
        if (hasDeprecated) {
            sb.append("     * @deprecated\n")
        }
        if (hasParams) {
            paramsWithDocs.forEach { param ->
                sb.append("     * @param ${param.name} ${param.description}\n")
            }
        }
        if (hasParamMeta) {
            paramsWithMeta.forEach { param ->
                param.style?.let { sb.append("     * @paramStyle ${param.name} ${parameterStyleValue(it)}\n") }
                param.explode?.let { sb.append("     * @paramExplode ${param.name} $it\n") }
                param.allowReserved?.let { sb.append("     * @paramAllowReserved ${param.name} $it\n") }
                param.allowEmptyValue?.let { sb.append("     * @paramAllowEmptyValue ${param.name} $it\n") }
            }
        }
        if (hasParamRef) {
            paramsWithRef.forEach { param ->
                val refJson = jsonMapper.writeValueAsString(referenceToDocValue(param.reference!!))
                sb.append("     * @paramRef ${param.name} $refJson\n")
            }
        }
        if (hasParamSchema) {
            paramsWithSchema.forEach { param ->
                val schemaJson = renderParamSchema(param.schema!!)
                sb.append("     * @paramSchema ${param.name} $schemaJson\n")
            }
        }
        if (hasParamContent) {
            paramsWithContent.forEach { param ->
                val contentJson = renderParamContent(param.content)
                sb.append("     * @paramContent ${param.name} $contentJson\n")
            }
        }
        if (hasParamExtensions) {
            paramsWithExtensions.forEach { param ->
                val json = renderExtensions(param.extensions)
                sb.append("     * @paramExtensions ${param.name} $json\n")
            }
        }
        if (paramsWithExamples.isNotEmpty()) {
            paramsWithExamples.forEach { param ->
                param.example?.let { example ->
                    val value = formatExampleValue(example)
                    if (value != null) {
                        sb.append("     * @paramExample ${param.name} $value\n")
                    }
                }
                param.examples.forEach { (key, example) ->
                    val value = formatExampleValue(example)
                    if (value != null) {
                        sb.append("     * @paramExample ${param.name} $key: $value\n")
                    }
                }
            }
        }
        if (hasRequestBody) {
            val requestBodyJson = renderRequestBody(requestBody!!)
            sb.append("     * @requestBody $requestBodyJson\n")
        }
        // Responses as @response Code Type Description
        ep.responses.forEach { (code, resp) ->
            val typeStr = resp.type ?: "Unit"
            val descStr = resp.description ?: ""
            sb.append("     * @response $code $typeStr $descStr\n")
        }
        if (hasResponseRef) {
            responsesWithRef.forEach { (code, resp) ->
                val refJson = jsonMapper.writeValueAsString(referenceToDocValue(resp.reference!!))
                sb.append("     * @responseRef $code $refJson\n")
            }
        }
        if (hasResponseExtensions) {
            responsesWithExtensions.forEach { (code, resp) ->
                val json = renderExtensions(resp.extensions)
                sb.append("     * @responseExtensions $code $json\n")
            }
        }
        responsesWithHeaders.forEach { (code, resp) ->
            sb.append("     * @responseHeaders $code ${renderResponseHeaders(resp.headers)}\n")
        }
        responsesWithLinks.forEach { (code, resp) ->
            sb.append("     * @responseLinks $code ${renderResponseLinks(resp.links ?: emptyMap())}\n")
        }
        responsesWithContent.forEach { (code, resp) ->
            sb.append("     * @responseContent $code ${renderResponseContent(resp.content)}\n")
        }
        responsesWithSummary.forEach { (code, resp) ->
            sb.append("     * @responseSummary $code ${resp.summary}\n")
        }
        if (hasCallbacks) {
            sb.append("     * @callbacks ${renderCallbacks(ep.callbacks)}\n")
        }
        if (hasExtensions) {
            val extensionsJson = jsonMapper.writeValueAsString(ep.extensions)
            sb.append("     * @extensions $extensionsJson\n")
        }

        sb.append("     */\n")
        return sb.toString()
    }

    private fun renderSecurityRequirement(requirement: Map<String, List<String>>): String {
        return jsonMapper.writeValueAsString(requirement)
    }

    private fun parameterStyleValue(style: ParameterStyle): String {
        return when (style) {
            ParameterStyle.MATRIX -> "matrix"
            ParameterStyle.LABEL -> "label"
            ParameterStyle.SIMPLE -> "simple"
            ParameterStyle.FORM -> "form"
            ParameterStyle.SPACE_DELIMITED -> "spaceDelimited"
            ParameterStyle.PIPE_DELIMITED -> "pipeDelimited"
            ParameterStyle.DEEP_OBJECT -> "deepObject"
            ParameterStyle.COOKIE -> "cookie"
        }
    }

    private fun formatExampleValue(example: domain.ExampleObject): String? {
        val hasMeta = example.summary != null || example.description != null || example.ref != null || example.extensions.isNotEmpty()
        val hasOnlyExternal = example.externalValue != null &&
            example.serializedValue == null &&
            example.dataValue == null &&
            example.value == null &&
            !hasMeta
        if (hasOnlyExternal) {
            return "external:${example.externalValue}"
        }
        if (!hasMeta && example.serializedValue != null &&
            example.dataValue == null &&
            example.value == null &&
            example.externalValue == null
        ) {
            return example.serializedValue
        }
        if (!hasMeta && example.value != null &&
            example.dataValue == null &&
            example.serializedValue == null &&
            example.externalValue == null
        ) {
            return formatExamplePayload(example.value)
        }
        if (!hasMeta && example.dataValue != null &&
            example.serializedValue == null &&
            example.value == null &&
            example.externalValue == null
        ) {
            return formatExamplePayload(example.dataValue)
        }
        return jsonMapper.writeValueAsString(exampleObjectToDocValue(example))
    }

    private fun formatExamplePayload(payload: Any?): String? {
        return when (payload) {
            null -> "null"
            is String -> payload
            is Number, is Boolean -> payload.toString()
            else -> jsonMapper.writeValueAsString(payload)
        }
    }

    private fun renderResponseHeaders(headers: Map<String, Header>): String {
        val mapped = headers.mapValues { headerToDocValue(it.value) }
        return jsonMapper.writeValueAsString(mapped)
    }

    private fun renderResponseLinks(links: Map<String, Link>): String {
        val mapped = links.mapValues { linkToDocValue(it.value) }
        return jsonMapper.writeValueAsString(mapped)
    }

    private fun renderResponseContent(content: Map<String, MediaTypeObject>): String {
        val mapped = content.mapValues { mediaTypeToDocValue(it.value) }
        return jsonMapper.writeValueAsString(mapped)
    }

    private fun renderCallbacks(callbacks: Map<String, Callback>): String {
        if (callbacks.isEmpty()) return "{}"
        val tempOperation = EndpointDefinition(
            path = "/_callbacks",
            method = HttpMethod.POST,
            operationId = "callbacks",
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")),
            callbacks = callbacks
        )
        val tempDefinition = OpenApiDefinition(
            info = Info(title = "Callbacks", version = "0.0.0"),
            paths = mapOf("/_callbacks" to PathItem(post = tempOperation))
        )
        val json = openApiWriter.writeJson(tempDefinition)
        val node = jsonMapper.readTree(json)
        val callbacksNode = node.path("paths").path("/_callbacks").path("post").path("callbacks")
        return jsonMapper.writeValueAsString(callbacksNode)
    }

    private fun renderParamSchema(schema: SchemaProperty): String {
        val mapped = schemaPropertyToDocValue(schema)
        return jsonMapper.writeValueAsString(mapped)
    }

    private fun renderParamContent(content: Map<String, MediaTypeObject>): String {
        val mapped = content.mapValues { mediaTypeToDocValue(it.value) }
        return jsonMapper.writeValueAsString(mapped)
    }

    private fun renderRequestBody(body: RequestBody): String {
        val mapped = requestBodyToDocValue(body)
        return jsonMapper.writeValueAsString(mapped)
    }

    private fun requestBodyToDocValue(body: RequestBody): Any {
        body.reference?.let { return referenceToDocValue(it) }
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("description", body.description)
        map.putIfTrue("required", body.required)
        if (body.content.isNotEmpty()) {
            map["content"] = body.content.mapValues { mediaTypeToDocValue(it.value) }
        }
        map.putExtensions(body.extensions)
        return map
    }

    private fun headerToDocValue(header: Header): Any {
        header.reference?.let { return referenceToDocValue(it) }
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("description", header.description)
        map.putIfTrue("required", header.required)
        map.putIfTrue("deprecated", header.deprecated)
        map.putIfNotNull("style", header.style?.let { parameterStyleValue(it) })
        map.putIfNotNull("explode", header.explode)
        if (header.content.isNotEmpty()) {
            map["content"] = header.content.mapValues { mediaTypeToDocValue(it.value) }
        } else if (header.schema != null) {
            map["schema"] = schemaPropertyToDocValue(header.schema)
        }
        header.example?.let { map["example"] = exampleObjectToDocValue(it) }
        if (header.examples.isNotEmpty()) {
            map["examples"] = header.examples.mapValues { exampleObjectToDocValue(it.value) }
        }
        map.putExtensions(header.extensions)
        return map
    }

    private fun linkToDocValue(link: Link): Any {
        link.reference?.let { return referenceToDocValue(it) }
        link.ref?.let {
            val refMap = linkedMapOf<String, Any?>("\$ref" to it)
            refMap.putIfNotNull("description", link.description)
            return refMap
        }
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("operationId", link.operationId)
        map.putIfNotNull("operationRef", link.operationRef)
        map.putIfNotEmpty("parameters", link.parameters)
        map.putIfNotNull("requestBody", link.requestBody)
        map.putIfNotNull("description", link.description)
        link.server?.let { map["server"] = serverToDocValue(it) }
        map.putExtensions(link.extensions)
        return map
    }

    private fun mediaTypeToDocValue(mediaType: MediaTypeObject): Any {
        mediaType.reference?.let { return referenceToDocValue(it) }
        mediaType.ref?.let { return mapOf("\$ref" to it) }
        val map = linkedMapOf<String, Any?>()
        mediaType.schema?.let { map["schema"] = schemaPropertyToDocValue(it) }
        mediaType.itemSchema?.let { map["itemSchema"] = schemaPropertyToDocValue(it) }
        mediaType.example?.let { map["example"] = exampleObjectToDocValue(it) }
        if (mediaType.examples.isNotEmpty()) {
            map["examples"] = mediaType.examples.mapValues { exampleObjectToDocValue(it.value) }
        }
        map.putIfNotEmpty("encoding", mediaType.encoding.mapValues { encodingToDocValue(it.value) })
        map.putIfNotEmpty("prefixEncoding", mediaType.prefixEncoding.map { encodingToDocValue(it) })
        mediaType.itemEncoding?.let { map["itemEncoding"] = encodingToDocValue(it) }
        map.putExtensions(mediaType.extensions)
        return map
    }

    private fun encodingToDocValue(encoding: EncodingObject): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("contentType", encoding.contentType)
        map.putIfNotEmpty("headers", encoding.headers.mapValues { headerToDocValue(it.value) })
        encoding.style?.let { map["style"] = parameterStyleValue(it) }
        map.putIfNotNull("explode", encoding.explode)
        map.putIfNotNull("allowReserved", encoding.allowReserved)
        map.putIfNotEmpty("encoding", encoding.encoding.mapValues { encodingToDocValue(it.value) })
        map.putIfNotEmpty("prefixEncoding", encoding.prefixEncoding.map { encodingToDocValue(it) })
        encoding.itemEncoding?.let { map["itemEncoding"] = encodingToDocValue(it) }
        map.putExtensions(encoding.extensions)
        return map
    }

    private fun schemaPropertyToDocValue(schema: SchemaProperty): Any {
        schema.booleanSchema?.let { return it }
        val map = linkedMapOf<String, Any?>()
        schema.ref?.let { map["\$ref"] = it }
        schema.dynamicRef?.let { map["\$dynamicRef"] = it }
        val typeValue = typeValue(schema.types)
        typeValue?.let { map["type"] = it }
        map.putIfNotNull("\$id", schema.schemaId)
        map.putIfNotNull("\$schema", schema.schemaDialect)
        map.putIfNotNull("\$anchor", schema.anchor)
        map.putIfNotNull("\$dynamicAnchor", schema.dynamicAnchor)
        map.putIfNotNull("format", schema.format)
        map.putIfNotNull("contentMediaType", schema.contentMediaType)
        map.putIfNotNull("contentEncoding", schema.contentEncoding)
        map.putIfNotNull("minLength", schema.minLength)
        map.putIfNotNull("maxLength", schema.maxLength)
        map.putIfNotNull("pattern", schema.pattern)
        map.putIfNotNull("minimum", schema.minimum)
        map.putIfNotNull("maximum", schema.maximum)
        map.putIfNotNull("multipleOf", schema.multipleOf)
        map.putIfNotNull("exclusiveMinimum", schema.exclusiveMinimum)
        map.putIfNotNull("exclusiveMaximum", schema.exclusiveMaximum)
        map.putIfNotNull("minItems", schema.minItems)
        map.putIfNotNull("maxItems", schema.maxItems)
        map.putIfNotNull("uniqueItems", schema.uniqueItems)
        map.putIfNotNull("minProperties", schema.minProperties)
        map.putIfNotNull("maxProperties", schema.maxProperties)
        schema.items?.let { map["items"] = schemaPropertyToDocValue(it) }
        map.putIfNotEmpty("prefixItems", schema.prefixItems.map { schemaPropertyToDocValue(it) })
        schema.contains?.let { map["contains"] = schemaPropertyToDocValue(it) }
        map.putIfNotNull("minContains", schema.minContains)
        map.putIfNotNull("maxContains", schema.maxContains)
        if (schema.properties.isNotEmpty()) {
            map["properties"] = schema.properties.mapValues { schemaPropertyToDocValue(it.value) }
        }
        map.putIfNotEmpty("required", schema.required)
        schema.additionalProperties?.let { map["additionalProperties"] = schemaPropertyToDocValue(it) }
        map.putIfNotEmpty("\$defs", schema.defs.mapValues { schemaPropertyToDocValue(it.value) })
        if (schema.patternProperties.isNotEmpty()) {
            map["patternProperties"] = schema.patternProperties.mapValues { schemaPropertyToDocValue(it.value) }
        }
        schema.propertyNames?.let { map["propertyNames"] = schemaPropertyToDocValue(it) }
        map.putIfNotEmpty("dependentRequired", schema.dependentRequired)
        if (schema.dependentSchemas.isNotEmpty()) {
            map["dependentSchemas"] = schema.dependentSchemas.mapValues { schemaPropertyToDocValue(it.value) }
        }
        map.putIfNotNull("description", schema.description)
        map.putIfNotNull("title", schema.title)
        map.putIfNotNull("default", schema.defaultValue)
        map.putIfNotNull("const", schema.constValue)
        map.putIfTrue("deprecated", schema.deprecated)
        map.putIfTrue("readOnly", schema.readOnly)
        map.putIfTrue("writeOnly", schema.writeOnly)
        schema.externalDocs?.let { map["externalDocs"] = externalDocsToDocValue(it) }
        schema.discriminator?.let { map["discriminator"] = discriminatorToDocValue(it) }
        map.putIfNotNull("\$comment", schema.comment)
        schema.enumValues?.let { map["enum"] = it }
        map.putIfNotEmpty("oneOf", schema.oneOf.map { schemaPropertyToDocValue(it) })
        map.putIfNotEmpty("anyOf", schema.anyOf.map { schemaPropertyToDocValue(it) })
        map.putIfNotEmpty("allOf", schema.allOf.map { schemaPropertyToDocValue(it) })
        schema.not?.let { map["not"] = schemaPropertyToDocValue(it) }
        schema.ifSchema?.let { map["if"] = schemaPropertyToDocValue(it) }
        schema.thenSchema?.let { map["then"] = schemaPropertyToDocValue(it) }
        schema.elseSchema?.let { map["else"] = schemaPropertyToDocValue(it) }
        map.putIfNotNull("example", schema.example)
        schema.examples?.let { map["examples"] = it }
        schema.xml?.let { map["xml"] = xmlToDocValue(it) }
        schema.unevaluatedProperties?.let { map["unevaluatedProperties"] = schemaPropertyToDocValue(it) }
        schema.unevaluatedItems?.let { map["unevaluatedItems"] = schemaPropertyToDocValue(it) }
        schema.contentSchema?.let { map["contentSchema"] = schemaPropertyToDocValue(it) }
        map.putCustomKeywords(schema.customKeywords)
        map.putExtensions(schema.extensions)
        return map
    }

    private fun exampleObjectToDocValue(example: ExampleObject): Any {
        example.ref?.let {
            val refMap = linkedMapOf<String, Any?>("\$ref" to it)
            refMap.putIfNotNull("summary", example.summary)
            refMap.putIfNotNull("description", example.description)
            return refMap
        }
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("summary", example.summary)
        map.putIfNotNull("description", example.description)
        map.putIfNotNull("dataValue", example.dataValue)
        map.putIfNotNull("serializedValue", example.serializedValue)
        map.putIfNotNull("externalValue", example.externalValue)
        map.putIfNotNull("value", example.value)
        map.putExtensions(example.extensions)
        return map
    }

    private fun referenceToDocValue(reference: ReferenceObject): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>("\$ref" to reference.ref)
        map.putIfNotNull("summary", reference.summary)
        map.putIfNotNull("description", reference.description)
        return map
    }

    private fun discriminatorToDocValue(discriminator: Discriminator): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["propertyName"] = discriminator.propertyName
        if (discriminator.mapping.isNotEmpty()) map["mapping"] = discriminator.mapping
        map.putIfNotNull("defaultMapping", discriminator.defaultMapping)
        map.putExtensions(discriminator.extensions)
        return map
    }

    private fun infoToDocValue(info: Info): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["title"] = info.title
        map["version"] = info.version
        map.putIfNotNull("summary", info.summary)
        map.putIfNotNull("description", info.description)
        map.putIfNotNull("termsOfService", info.termsOfService)
        info.contact?.let { map["contact"] = contactToDocValue(it) }
        info.license?.let { map["license"] = licenseToDocValue(it) }
        map.putExtensions(info.extensions)
        return map
    }

    private fun contactToDocValue(contact: domain.Contact): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("name", contact.name)
        map.putIfNotNull("url", contact.url)
        map.putIfNotNull("email", contact.email)
        map.putExtensions(contact.extensions)
        return map
    }

    private fun licenseToDocValue(license: domain.License): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["name"] = license.name
        map.putIfNotNull("identifier", license.identifier)
        map.putIfNotNull("url", license.url)
        map.putExtensions(license.extensions)
        return map
    }

    private fun tagToDocValue(tag: Tag): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["name"] = tag.name
        map.putIfNotNull("summary", tag.summary)
        map.putIfNotNull("description", tag.description)
        tag.externalDocs?.let { map["externalDocs"] = externalDocsToDocValue(it) }
        map.putIfNotNull("parent", tag.parent)
        map.putIfNotNull("kind", tag.kind)
        map.putExtensions(tag.extensions)
        return map
    }

    private fun externalDocsToDocValue(docs: ExternalDocumentation): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["url"] = docs.url
        map.putIfNotNull("description", docs.description)
        map.putExtensions(docs.extensions)
        return map
    }

    private fun xmlToDocValue(xml: Xml): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("name", xml.name)
        map.putIfNotNull("namespace", xml.namespace)
        map.putIfNotNull("prefix", xml.prefix)
        map.putIfNotNull("nodeType", xml.nodeType)
        map.putIfTrue("attribute", xml.attribute)
        map.putIfTrue("wrapped", xml.wrapped)
        map.putExtensions(xml.extensions)
        return map
    }

    private fun serverToDocValue(server: Server): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["url"] = server.url
        map.putIfNotNull("description", server.description)
        map.putIfNotNull("name", server.name)
        server.variables?.let { vars ->
            if (vars.isNotEmpty()) {
                map["variables"] = vars.mapValues { variable ->
                    val variableMap = linkedMapOf<String, Any?>()
                    variableMap["default"] = variable.value.default
                    variableMap.putIfNotEmpty("enum", variable.value.enum)
                    variableMap.putIfNotNull("description", variable.value.description)
                    variableMap.putExtensions(variable.value.extensions)
                    variableMap
                }
            }
        }
        map.putExtensions(server.extensions)
        return map
    }

    private fun typeValue(types: Set<String>): Any? {
        if (types.isEmpty()) return null
        val list = types.toList().sorted()
        return if (list.size == 1) list.first() else list
    }

    private fun <T> MutableMap<String, Any?>.putIfNotNull(key: String, value: T?) {
        if (value != null) this[key] = value
    }

    private fun <T> MutableMap<String, Any?>.putIfNotEmpty(key: String, value: Collection<T>?) {
        if (!value.isNullOrEmpty()) this[key] = value
    }

    private fun <K, V> MutableMap<String, Any?>.putIfNotEmpty(key: String, value: Map<K, V>?) {
        if (!value.isNullOrEmpty()) this[key] = value
    }

    private fun MutableMap<String, Any?>.putIfTrue(key: String, value: Boolean) {
        if (value) this[key] = true
    }

    private fun MutableMap<String, Any?>.putExtensions(extensions: Map<String, Any?>) {
        if (extensions.isEmpty()) return
        extensions.forEach { (key, value) ->
            if (key.startsWith("x-")) {
                this[key] = value
            }
        }
    }

    private fun MutableMap<String, Any?>.putCustomKeywords(customKeywords: Map<String, Any?>) {
        if (customKeywords.isEmpty()) return
        customKeywords.forEach { (key, value) ->
            if (key.startsWith("x-")) return@forEach
            if (this.containsKey(key)) return@forEach
            this[key] = value
        }
    }

    /**
     * Generates the Kotlin function signature for an endpoint.
     * Returns Result<T>.
     */
    fun generateMethodSignature(ep: EndpointDefinition): String {
        val params = ep.parameters.map { param ->
            val optional = isOptionalParam(param)
            val type = resolveParameterType(param, optional)
            val deprecatedAnnotation = if (param.deprecated) "@Deprecated(\"Deprecated parameter\") " else ""
            val defaultValue = if (optional) " = null" else ""
            "$deprecatedAnnotation${param.name}: $type$defaultValue"
        }.toMutableList()

        val bodySignature = resolveRequestBodySignature(ep)
        if (bodySignature != null) {
            val defaultValue = if (bodySignature.isOptional) " = null" else ""
            params.add("body: ${bodySignature.kotlinType}$defaultValue")
        }

        val paramString = params.joinToString(", ")
        val returnType = resolveResponseType(ep)

        return "suspend fun ${ep.operationId}($paramString): Result<$returnType>"
    }

    /**
     * Generates the Ktor implementation block for an endpoint.
     */
    fun generateMethodImpl(ep: EndpointDefinition): String {
        val returnType = resolveResponseType(ep)
        val signature = generateMethodSignature(ep)

        // 0. Querystring constraint (OAS 3.2): cannot mix query and querystring
        val queryParams = ep.parameters.filter { it.location == ParameterLocation.QUERY }
        val queryStringParam = ep.parameters.firstOrNull { it.location == ParameterLocation.QUERYSTRING }
        if (queryStringParam != null && queryParams.isNotEmpty()) {
            throw IllegalArgumentException("OAS 3.2: querystring and query parameters cannot be used together for ${ep.operationId}")
        }

        // 1. Build Path String based on Style (Matrix, Label, Simple) and array/object serialization
        val pathTemplate = ep.parameters
            .filter { it.location == ParameterLocation.PATH }
            .fold(ep.path) { currentPath, param ->
                val placeholder = "{${param.name}}"
                val isMatrixOrLabel = param.style == ParameterStyle.MATRIX || param.style == ParameterStyle.LABEL

                // If Matrix or Label style, we absorb the preceding slash if present
                val target = if (isMatrixOrLabel && currentPath.contains("/$placeholder")) {
                    "/$placeholder"
                } else {
                    placeholder
                }

                val paramType = resolveParameterType(param, forceNullable = false)
                val replacement = buildPathParamReplacement(param, paramType)
                currentPath.replace(target, replacement)
            }

        val baseUrlExpr = if (ep.servers.isNotEmpty()) ep.servers.first().url else "\$baseUrl"
        val fullUrl = if (pathTemplate.startsWith("/")) "$baseUrlExpr$pathTemplate" else "$baseUrlExpr/$pathTemplate"

        // 2. Build Query/Header/Cookie Config
        val paramLines = ep.parameters.flatMap { param ->
            val optional = isOptionalParam(param)
            val paramType = resolveParameterType(param, optional)
            when (param.location) {
                ParameterLocation.QUERY -> {
                    val queryLines = buildQueryParamLines(param, paramType)
                    wrapOptional(optional, param.name, queryLines.lines, queryLines.emptyValueLine)
                }
                ParameterLocation.HEADER -> {
                    val baseLines = buildHeaderParamLines(param, paramType)
                    wrapOptional(optional, param.name, baseLines)
                }
                ParameterLocation.COOKIE -> {
                    val baseLines = buildCookieParamLines(param, paramType)
                    wrapOptional(optional, param.name, baseLines)
                }
                ParameterLocation.PATH, ParameterLocation.QUERYSTRING -> emptyList()
            }
        }

        val paramConfig = if (paramLines.isNotEmpty()) {
            "\n            " + paramLines.joinToString("\n            ")
        } else {
            ""
        }

        if (queryStringParam != null) {
            val cleanType = resolveParameterType(queryStringParam, isOptionalParam(queryStringParam)).replace(" ", "")
            val isString = cleanType == "String" || cleanType == "String?"
            if (!isString) {
                throw IllegalArgumentException("OAS 3.2: querystring parameter must be String for ${ep.operationId}")
            }
        }

        val queryStringConfig = if (queryStringParam != null) {
            val assignment = "url.encodedQuery = ${queryStringParam.name}"
            if (isOptionalParam(queryStringParam)) {
                "\n            if (${queryStringParam.name} != null) {\n                $assignment\n            }"
            } else {
                "\n            $assignment"
            }
        } else {
            ""
        }

        val bodySignature = resolveRequestBodySignature(ep)
        val contentType = resolveRequestContentType(ep)
        val bodyConfig = if (bodySignature != null) {
            val lines = mutableListOf<String>()
            if (contentType != null) {
                lines.add("contentType(ContentType.parse(\"$contentType\"))")
            }
            lines.add("setBody(body)")

            if (bodySignature.isOptional) {
                val joined = lines.joinToString("\n                ")
                "\n            if (body != null) {\n                $joined\n            }"
            } else {
                "\n            " + lines.joinToString("\n            ")
            }
        } else {
            ""
        }

        val methodStr = when (ep.method) {
            domain.HttpMethod.CUSTOM -> {
                val rawMethod = ep.customMethod ?: "CUSTOM"
                val safeMethod = rawMethod.replace("\"", "\\\"")
                "HttpMethod(\"$safeMethod\")"
            }
            domain.HttpMethod.QUERY -> "HttpMethod(\"QUERY\")"
            else -> {
                val methodEnumName = ep.method.name.lowercase().replaceFirstChar { it.uppercase() }
                "HttpMethod.$methodEnumName"
            }
        }

        return """
    override $signature {
        return try {
            val response = client.request("$fullUrl") {
                method = $methodStr$paramConfig$queryStringConfig$bodyConfig
            }
            if (response.status.isSuccess()) {
                Result.success(response.body<$returnType>())
            } else {
                Result.failure(ApiException("Error: " + response.status))
            }
        } catch (e: Exception) {
            Result.failure(ApiException(e.message ?: "Unknown Error"))
        }
    }
        """.trimIndent()
    }

    private fun resolveParameterType(param: EndpointParameter, forceNullable: Boolean = false): String {
        val schema = param.schema ?: selectSchema(param.content)
        val rawType = if (schema != null) {
            TypeMappers.mapType(schema)
        } else {
            param.type
        }

        val requiresNullable = forceNullable || schema?.types?.contains("null") == true
        val alreadyNullable = rawType.trim().endsWith("?")
        return if (requiresNullable && !alreadyNullable) "$rawType?" else rawType
    }

    private fun isOptionalParam(param: EndpointParameter): Boolean {
        if (param.location == ParameterLocation.PATH) return false
        return !param.isRequired
    }

    private fun resolveRequestContentType(ep: EndpointDefinition): String? {
        val content = ep.requestBody?.content ?: return null
        if (content.isEmpty()) return null
        return if (content.containsKey("application/json")) {
            "application/json"
        } else {
            content.keys.first()
        }
    }

    private fun resolveRequestBodyType(ep: EndpointDefinition): String? {
        ep.requestBodyType?.let { raw ->
            return if (ep.requestBody?.required == false && !raw.trim().endsWith("?")) {
                "$raw?"
            } else {
                raw
            }
        }
        val schema = selectSchema(ep.requestBody?.content ?: emptyMap()) ?: return null
        val baseType = TypeMappers.mapType(schema)
        return if (ep.requestBody?.required == false) "$baseType?" else baseType
    }

    private data class RequestBodySignature(val kotlinType: String, val isOptional: Boolean)

    private fun resolveRequestBodySignature(ep: EndpointDefinition): RequestBodySignature? {
        val kotlinType = resolveRequestBodyType(ep) ?: return null
        val isOptional = kotlinType.trim().endsWith("?")
        return RequestBodySignature(kotlinType, isOptional)
    }

    private fun resolveResponseType(ep: EndpointDefinition): String {
        ep.responseType?.let { return it }
        val success = ep.responses.keys
            .filter { it.startsWith("2") }
            .minOrNull()
            ?: return "Unit"
        val response = ep.responses[success] ?: return "Unit"
        response.type?.let { return it }
        val schema = selectSchema(response.content) ?: return "Unit"
        return TypeMappers.mapType(schema)
    }

    private fun selectSchema(content: Map<String, MediaTypeObject>): SchemaProperty? {
        if (content.isEmpty()) return null
        val preferred = content["application/json"] ?: content.values.first()
        return preferred.schema ?: preferred.itemSchema
    }

    // Helper to detect Lists
    private fun isListType(type: String): Boolean {
        val clean = type.replace("?", "")
        return clean.startsWith("List<") || clean.contains("Array")
    }

    private fun isMapType(type: String): Boolean {
        val clean = type.replace("?", "")
        return clean.startsWith("Map<") || clean.startsWith("MutableMap<")
    }

    private fun buildQueryParamLines(param: EndpointParameter, paramType: String): QueryParamLines {
        val style = param.style ?: ParameterStyle.FORM
        val explodeDefault = style == ParameterStyle.FORM
        val explode = param.explode ?: explodeDefault
        val allowReserved = param.allowReserved == true

        val isList = isListType(paramType)
        val isMap = isMapType(paramType)

        val emptyValueLine = if (param.allowEmptyValue == true) {
            if (allowReserved) {
                "url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(\"\"))"
            } else {
                "parameter(\"${param.name}\", \"\")"
            }
        } else {
            null
        }

        val lines = when {
            !isList && !isMap -> {
                if (allowReserved) {
                    listOf("url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(${param.name}.toString()))")
                } else {
                    listOf("parameter(\"${param.name}\", ${param.name})")
                }
            }
            isList -> buildQueryArrayLines(param, style, explode, allowReserved)
            else -> buildQueryObjectLines(param, style, explode, allowReserved)
        }

        return QueryParamLines(lines, emptyValueLine)
    }

    private fun buildQueryArrayLines(
        param: EndpointParameter,
        style: ParameterStyle,
        explode: Boolean,
        allowReserved: Boolean
    ): List<String> {
        if (allowReserved) {
            return when (style) {
                ParameterStyle.FORM -> {
                    if (explode) {
                        listOf("${param.name}.forEach { value -> url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(value.toString())) }")
                    } else {
                        listOf("url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(${param.name}.joinToString(\",\")))")
                    }
                }
                ParameterStyle.SPACE_DELIMITED -> {
                    if (explode) {
                        throw IllegalArgumentException("OAS 3.2: spaceDelimited does not support explode=true for ${param.name}")
                    }
                    listOf("url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(${param.name}.joinToString(\" \")))")
                }
                ParameterStyle.PIPE_DELIMITED -> {
                    if (explode) {
                        throw IllegalArgumentException("OAS 3.2: pipeDelimited does not support explode=true for ${param.name}")
                    }
                    listOf("url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(${param.name}.joinToString(\"|\")))")
                }
                ParameterStyle.DEEP_OBJECT -> {
                    throw IllegalArgumentException("OAS 3.2: deepObject only applies to objects for ${param.name}")
                }
                else -> listOf("url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(${param.name}.toString()))")
            }
        }

        return when (style) {
            ParameterStyle.FORM -> {
                if (explode) {
                    listOf("${param.name}.forEach { value -> parameter(\"${param.name}\", value) }")
                } else {
                    listOf("parameter(\"${param.name}\", ${param.name}.joinToString(\",\"))")
                }
            }
            ParameterStyle.SPACE_DELIMITED -> {
                if (explode) {
                    throw IllegalArgumentException("OAS 3.2: spaceDelimited does not support explode=true for ${param.name}")
                }
                listOf("parameter(\"${param.name}\", ${param.name}.joinToString(\" \"))")
            }
            ParameterStyle.PIPE_DELIMITED -> {
                if (explode) {
                    throw IllegalArgumentException("OAS 3.2: pipeDelimited does not support explode=true for ${param.name}")
                }
                listOf("parameter(\"${param.name}\", ${param.name}.joinToString(\"|\"))")
            }
            ParameterStyle.DEEP_OBJECT -> {
                throw IllegalArgumentException("OAS 3.2: deepObject only applies to objects for ${param.name}")
            }
            else -> listOf("parameter(\"${param.name}\", ${param.name})")
        }
    }

    private fun buildQueryObjectLines(
        param: EndpointParameter,
        style: ParameterStyle,
        explode: Boolean,
        allowReserved: Boolean
    ): List<String> {
        if (allowReserved) {
            return when (style) {
                ParameterStyle.FORM -> {
                    if (explode) {
                        listOf("${param.name}.forEach { (key, value) -> url.encodedParameters.append(encodeAllowReserved(key.toString()), encodeAllowReserved(value.toString())) }")
                    } else {
                        val joinExpr =
                            "${param.name}.entries.joinToString(\",\") { \"${'$'}{it.key},${'$'}{it.value}\" }"
                        listOf("url.encodedParameters.append(\"${param.name}\", encodeAllowReserved($joinExpr))")
                    }
                }
                ParameterStyle.DEEP_OBJECT -> {
                    listOf("${param.name}.forEach { (key, value) -> url.encodedParameters.append(encodeAllowReserved(\"${param.name}[${'$'}key]\"), encodeAllowReserved(value.toString())) }")
                }
                ParameterStyle.SPACE_DELIMITED -> {
                    if (explode) {
                        throw IllegalArgumentException("OAS 3.2: spaceDelimited does not support explode=true for ${param.name}")
                    }
                    val joinExpr =
                        "${param.name}.entries.flatMap { listOf(it.key, it.value) }.joinToString(\" \")"
                    listOf("url.encodedParameters.append(\"${param.name}\", encodeAllowReserved($joinExpr))")
                }
                ParameterStyle.PIPE_DELIMITED -> {
                    if (explode) {
                        throw IllegalArgumentException("OAS 3.2: pipeDelimited does not support explode=true for ${param.name}")
                    }
                    val joinExpr =
                        "${param.name}.entries.flatMap { listOf(it.key, it.value) }.joinToString(\"|\")"
                    listOf("url.encodedParameters.append(\"${param.name}\", encodeAllowReserved($joinExpr))")
                }
                else -> listOf("url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(${param.name}.toString()))")
            }
        }

        return when (style) {
            ParameterStyle.FORM -> {
                if (explode) {
                    listOf("${param.name}.forEach { (key, value) -> parameter(key, value) }")
                } else {
                    val joinExpr =
                        "${param.name}.entries.joinToString(\",\") { \"${'$'}{it.key},${'$'}{it.value}\" }"
                    listOf("parameter(\"${param.name}\", $joinExpr)")
                }
            }
            ParameterStyle.DEEP_OBJECT -> {
                listOf("${param.name}.forEach { (key, value) -> parameter(\"${param.name}[${'$'}key]\", value) }")
            }
            ParameterStyle.SPACE_DELIMITED -> {
                if (explode) {
                    throw IllegalArgumentException("OAS 3.2: spaceDelimited does not support explode=true for ${param.name}")
                }
                val joinExpr =
                    "${param.name}.entries.flatMap { listOf(it.key, it.value) }.joinToString(\" \")"
                listOf("parameter(\"${param.name}\", $joinExpr)")
            }
            ParameterStyle.PIPE_DELIMITED -> {
                if (explode) {
                    throw IllegalArgumentException("OAS 3.2: pipeDelimited does not support explode=true for ${param.name}")
                }
                val joinExpr =
                    "${param.name}.entries.flatMap { listOf(it.key, it.value) }.joinToString(\"|\")"
                listOf("parameter(\"${param.name}\", $joinExpr)")
            }
            else -> listOf("parameter(\"${param.name}\", ${param.name})")
        }
    }

    private fun wrapOptional(
        optional: Boolean,
        paramName: String,
        lines: List<String>,
        emptyValueLine: String? = null
    ): List<String> {
        if (lines.isEmpty()) return emptyList()
        if (!optional) return lines
        val inner = lines.joinToString("\n                    ")
        return if (emptyValueLine != null) {
            listOf("if ($paramName != null) {\n                    $inner\n            } else {\n                    $emptyValueLine\n            }")
        } else {
            listOf("if ($paramName != null) {\n                    $inner\n            }")
        }
    }

    private fun buildHeaderParamLines(param: EndpointParameter, paramType: String): List<String> {
        val style = param.style ?: ParameterStyle.SIMPLE
        val explode = param.explode ?: false
        val cleanType = paramType.replace(" ", "")
        val isList = isListType(cleanType)
        val isMap = isMapType(cleanType)

        val valueExpr = when {
            !isList && !isMap -> param.name
            isList -> buildArrayJoinExpr(param.name, ",")
            isMap -> {
                val keyValueDelimiter = if (explode) "=" else ","
                buildObjectJoinExpr(param.name, ",", keyValueDelimiter)
            }
            else -> param.name
        }

        // Headers only allow "simple" style in OAS, treat all as simple.
        return listOf("header(\"${param.name}\", $valueExpr)")
    }

    private fun buildCookieParamLines(param: EndpointParameter, paramType: String): List<String> {
        val style = param.style ?: ParameterStyle.FORM
        val explodeDefault = style == ParameterStyle.FORM || style == ParameterStyle.COOKIE
        val explode = param.explode ?: explodeDefault
        val cleanType = paramType.replace(" ", "")
        val isList = isListType(cleanType)
        val isMap = isMapType(cleanType)

        if (!isList && !isMap) {
            return listOf("cookie(\"${param.name}\", ${param.name})")
        }

        return when (style) {
            ParameterStyle.COOKIE -> buildCookieStyleLines(param, isList, isMap, explode)
            ParameterStyle.FORM -> buildFormStyleCookieLines(param, isList, isMap, explode)
            else -> buildCookieStyleLines(param, isList, isMap, explode)
        }
    }

    private fun buildCookieStyleLines(
        param: EndpointParameter,
        isList: Boolean,
        isMap: Boolean,
        explode: Boolean
    ): List<String> {
        return when {
            isList && explode -> listOf("${param.name}.forEach { value -> cookie(\"${param.name}\", value) }")
            isList -> listOf("cookie(\"${param.name}\", ${buildArrayJoinExpr(param.name, ",")})")
            isMap && explode -> listOf("${param.name}.forEach { (key, value) -> cookie(key, value) }")
            isMap -> listOf("cookie(\"${param.name}\", ${buildObjectJoinExpr(param.name, ",", ",")})")
            else -> listOf("cookie(\"${param.name}\", ${param.name})")
        }
    }

    private fun buildFormStyleCookieLines(
        param: EndpointParameter,
        isList: Boolean,
        isMap: Boolean,
        explode: Boolean
    ): List<String> {
        if (explode) {
            return when {
                isList -> {
                    val expr = "${param.name}.joinToString(\"&\") { \"${param.name}=${'$'}it\" }"
                    listOf("header(\"Cookie\", $expr)")
                }
                isMap -> {
                    val expr = buildObjectJoinExpr(param.name, "&", "=")
                    listOf("header(\"Cookie\", $expr)")
                }
                else -> listOf("cookie(\"${param.name}\", ${param.name})")
            }
        }

        return when {
            isList -> listOf("cookie(\"${param.name}\", ${buildArrayJoinExpr(param.name, ",")})")
            isMap -> listOf("cookie(\"${param.name}\", ${buildObjectJoinExpr(param.name, ",", ",")})")
            else -> listOf("cookie(\"${param.name}\", ${param.name})")
        }
    }

    private fun buildPathParamReplacement(param: EndpointParameter, paramType: String): String {
        val style = param.style ?: ParameterStyle.SIMPLE
        val explodeDefault = style == ParameterStyle.FORM || style == ParameterStyle.COOKIE
        val explode = param.explode ?: explodeDefault
        val cleanType = paramType.replace(" ", "")
        val isList = isListType(cleanType)
        val isMap = isMapType(cleanType)

        if (!isList && !isMap) {
            return when (style) {
                ParameterStyle.MATRIX -> ";${param.name}=\$${param.name}"
                ParameterStyle.LABEL -> ".\$${param.name}"
                else -> "\$${param.name}"
            }
        }

        return when (style) {
            ParameterStyle.MATRIX -> buildMatrixPathReplacement(param.name, isList, isMap, explode)
            ParameterStyle.LABEL -> buildLabelPathReplacement(param.name, isList, isMap, explode)
            else -> buildSimplePathReplacement(param.name, isList, isMap, explode)
        }
    }

    private fun buildMatrixPathReplacement(
        name: String,
        isList: Boolean,
        isMap: Boolean,
        explode: Boolean
    ): String {
        return when {
            isList && explode -> ";$name=${template(buildArrayJoinExpr(name, ";$name="))}"
            isList -> ";$name=${template(buildArrayJoinExpr(name, ","))}"
            isMap && explode -> ";${template(buildObjectJoinExpr(name, ";", "="))}"
            isMap -> ";$name=${template(buildObjectJoinExpr(name, ",", ","))}"
            else -> ";$name=\$$name"
        }
    }

    private fun buildLabelPathReplacement(
        name: String,
        isList: Boolean,
        isMap: Boolean,
        explode: Boolean
    ): String {
        return when {
            isList -> {
                val delimiter = if (explode) "." else ","
                ".${template(buildArrayJoinExpr(name, delimiter))}"
            }
            isMap -> {
                val entryDelimiter = if (explode) "." else ","
                val keyValueDelimiter = if (explode) "=" else ","
                ".${template(buildObjectJoinExpr(name, entryDelimiter, keyValueDelimiter))}"
            }
            else -> ".\$$name"
        }
    }

    private fun buildSimplePathReplacement(
        name: String,
        isList: Boolean,
        isMap: Boolean,
        explode: Boolean
    ): String {
        return when {
            isList -> template(buildArrayJoinExpr(name, ","))
            isMap -> {
                val keyValueDelimiter = if (explode) "=" else ","
                template(buildObjectJoinExpr(name, ",", keyValueDelimiter))
            }
            else -> "\$$name"
        }
    }

    private fun template(expr: String): String = "\${$expr}"

    private fun buildArrayJoinExpr(name: String, delimiter: String): String {
        return "$name.joinToString(\"$delimiter\")"
    }

    private fun buildObjectJoinExpr(name: String, entryDelimiter: String, keyValueDelimiter: String): String {
        val pairExpr = "\"${'$'}{it.key}$keyValueDelimiter${'$'}{it.value}\""
        return "$name.entries.joinToString(\"$entryDelimiter\") { $pairExpr }"
    }

    private fun sanitizeIdentifier(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9]"), "").replaceFirstChar { it.lowercase() }
    }
}
