package psi

import domain.EndpointDefinition
import domain.EndpointParameter
import domain.MediaTypeObject
import domain.ParameterLocation
import domain.ParameterStyle
import domain.SchemaProperty
import domain.SecurityScheme
import domain.Server
import org.jetbrains.kotlin.psi.KtFile

/**
 * Generates Ktor Network Interface and Implementation code from Endpoint Definitions.
 * Supports:
 * - Result<T> return types
 * - Path, Query, Querystring, Header, Cookie parameters
 * - Parameter Serialization Styles (Matrix, Label, Form, Pipe/Space Delimited) and Explode logic
 * - Server Base URL configuration
 * - KDoc generation
 * - Ktor Auth Plugin configuration (Basic, Bearer, ApiKey)
 */
class NetworkGenerator {

    private val psiFactory = PsiInfrastructure.createPsiFactory()

    /**
     * Generates a complete API file with Interface, Implementation, and Exception classes.
     *
     * @param packageName The target package.
     * @param apiName The class name for the API.
     * @param endpoints List of endpoints to generate.
     * @param servers List of Server definitions for configuration.
     * @param securitySchemes Map of Security Schemes defined in Components.
     */
    fun generateApi(
        packageName: String,
        apiName: String,
        endpoints: List<EndpointDefinition>,
        servers: List<Server> = emptyList(),
        securitySchemes: Map<String, SecurityScheme> = emptyMap()
    ): KtFile {
        // imports
        val baseImports = mutableSetOf(
            "io.ktor.client.*",
            "io.ktor.client.call.*",
            "io.ktor.client.request.*",
            "io.ktor.http.*"
        )

        // Add Auth imports if needed
        if (securitySchemes.isNotEmpty()) {
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

        // Server Logic
        val defaultUrl = if (servers.isNotEmpty()) servers.first().url else ""

        // Companion Object (Servers + Auth Factory)
        val companionObject = buildCompanionObject(servers, securitySchemes)

        val baseUrlParam = if (defaultUrl.isNotEmpty()) {
            "private val baseUrl: String = \"$defaultUrl\""
        } else {
            "private val baseUrl: String = \"\""
        }

        val content = """
            $importsBlock
            
            interface $interfaceName {
            $interfaceMethods
            }
            
            class $implName(
                private val client: HttpClient,
                $baseUrlParam
            ) : $interfaceName {
            $implMethods
            $companionObject
            }
            
            class ApiException(message: String) : Exception(message)
        """.trimIndent()

        return psiFactory.createFile("$apiName.kt", content)
    }

    private fun buildCompanionObject(
        servers: List<Server>,
        securitySchemes: Map<String, SecurityScheme>
    ): String {
        val serverListCode = if (servers.isNotEmpty()) {
            val listStr = servers.joinToString(", ") { "\"${it.url}\"" }
            "val SERVERS = listOf($listStr)"
        } else {
            ""
        }

        val authFactoryCode = if (securitySchemes.isNotEmpty()) {
            generateAuthFactory(securitySchemes)
        } else {
            ""
        }

        if (serverListCode.isEmpty() && authFactoryCode.isEmpty()) return ""

        return """
            
            companion object {
                $serverListCode
                $authFactoryCode
            }
        """.trimIndent()
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
        val paramsWithDocs = ep.parameters.filter { !it.description.isNullOrBlank() }
        val paramsWithExamples = ep.parameters.filter { it.example != null || it.examples.isNotEmpty() }
        val hasParams = paramsWithDocs.isNotEmpty()

        if (!hasSummary && !hasDescription && !hasExtDocs && !hasTags && !hasResponses && !hasParams && paramsWithExamples.isEmpty()) {
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
            val docs = ep.externalDocs
            sb.append("     * @see ${docs.url}")
            if (docs.description != null) {
                sb.append(" ${docs.description}")
            }
            sb.append("\n")
        }
        if (hasTags) {
            val tagStr = ep.tags.joinToString(", ")
            sb.append("     * @tag $tagStr\n")
        }
        if (hasParams) {
            paramsWithDocs.forEach { param ->
                sb.append("     * @param ${param.name} ${param.description}\n")
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
        // Responses as @response Code Type Description
        ep.responses.forEach { (code, resp) ->
            val typeStr = resp.type ?: "Unit"
            val descStr = resp.description ?: ""
            sb.append("     * @response $code $typeStr $descStr\n")
        }

        sb.append("     */\n")
        return sb.toString()
    }

    private fun formatExampleValue(example: domain.ExampleObject): String? {
        example.serializedValue?.let { return it }
        example.dataValue?.let { return it.toString() }
        example.value?.let { return it.toString() }
        example.externalValue?.let { return "external:$it" }
        return null
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

        // 1. Build Path String based on Style (Matrix, Label, Simple)
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

                val replacement = when (param.style) {
                    ParameterStyle.MATRIX -> ";${param.name}=\$${param.name}"
                    ParameterStyle.LABEL -> ".\$${param.name}"
                    else -> "\$${param.name}" // SIMPLE/Default
                }
                currentPath.replace(target, replacement)
            }

        val baseUrlExpr = if (ep.servers.isNotEmpty()) ep.servers.first().url else "\$baseUrl"
        val fullUrl = if (pathTemplate.startsWith("/")) "$baseUrlExpr$pathTemplate" else "$baseUrlExpr/$pathTemplate"

        // 2. Build Query/Header/Cookie Config
        val paramLines = ep.parameters.mapNotNull { param ->
            val optional = isOptionalParam(param)
            val paramType = resolveParameterType(param, optional)
            val valueExpr = if (isListType(paramType) && param.location == ParameterLocation.QUERY) {
                formatQueryList(param)
            } else {
                param.name
            }

            val line = when (param.location) {
                ParameterLocation.QUERY -> "parameter(\"${param.name}\", $valueExpr)"
                ParameterLocation.HEADER -> "header(\"${param.name}\", ${param.name})"
                ParameterLocation.COOKIE -> "cookie(\"${param.name}\", ${param.name})"
                ParameterLocation.PATH -> null
                ParameterLocation.QUERYSTRING -> null
            }

            if (line == null) {
                null
            } else if (optional) {
                "if (${param.name} != null) {\n                $line\n            }"
            } else {
                line
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

    private fun formatQueryList(param: EndpointParameter): String {
        // Default for FORM is explode=true -> Ktor handles list (reproduction of keys)
        // If explode=false -> comma separated
        val explode = param.explode ?: true // Default explode=true for Form

        if (explode) {
            // For now, consistent with existing requirements, we handle *Explode=False* formatting explicitly:
            return param.name
        }

        // Explode = false: we must join manually
        val separator = when (param.style) {
            ParameterStyle.SPACE_DELIMITED -> " "
            ParameterStyle.PIPE_DELIMITED -> "|"
            else -> "," // FORM default
        }

        return "${param.name}.joinToString(\"$separator\")"
    }

    private fun sanitizeIdentifier(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9]"), "").replaceFirstChar { it.lowercase() }
    }
}
