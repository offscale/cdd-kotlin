package psi

import domain.EndpointDefinition
import domain.EndpointParameter
import domain.ParameterLocation
import domain.ParameterStyle
import domain.SecurityScheme
import domain.Server
import org.jetbrains.kotlin.psi.KtFile

/**
 * Generates Ktor Network Interface and Implementation code from Endpoint Definitions.
 * Supports:
 * - Result<T> return types
 * - Path, Query, Header, Cookie parameters
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
        val hasExtDocs = ep.externalDocs != null
        val hasTags = ep.tags.isNotEmpty()
        val hasResponses = ep.responses.isNotEmpty()

        if (!hasSummary && !hasExtDocs && !hasTags && !hasResponses) return ""

        val sb = StringBuilder("    /**\n")
        if (hasSummary) {
            sb.append("     * ${ep.summary}\n")
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
        // Responses as @response Code Type Description
        ep.responses.forEach { (code, resp) ->
            val typeStr = resp.type ?: "Unit"
            val descStr = resp.description ?: ""
            sb.append("     * @response $code $typeStr $descStr\n")
        }

        sb.append("     */\n")
        return sb.toString()
    }

    /**
     * Generates the Kotlin function signature for an endpoint.
     * Returns Result<T>.
     */
    fun generateMethodSignature(ep: EndpointDefinition): String {
        val params = ep.parameters.map { param ->
            val type = param.type
            "${param.name}: $type"
        }.toMutableList()

        if (ep.requestBodyType != null) {
            params.add("body: ${ep.requestBodyType}")
        }

        val paramString = params.joinToString(", ")
        val returnType = ep.responseType ?: "Unit"

        return "suspend fun ${ep.operationId}($paramString): Result<$returnType>"
    }

    /**
     * Generates the Ktor implementation block for an endpoint.
     */
    fun generateMethodImpl(ep: EndpointDefinition): String {
        val returnType = ep.responseType ?: "Unit"
        val signature = generateMethodSignature(ep)

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

        val fullUrl = if (pathTemplate.startsWith("/")) "\$baseUrl$pathTemplate" else "\$baseUrl/$pathTemplate"

        // 2. Build Query/Header/Cookie Config
        val paramLines = ep.parameters.mapNotNull { param ->
            val valueExpr = if (isListType(param.type) && param.location == ParameterLocation.QUERY) {
                formatQueryList(param)
            } else {
                param.name
            }

            when (param.location) {
                ParameterLocation.QUERY -> "parameter(\"${param.name}\", $valueExpr)"
                ParameterLocation.HEADER -> "header(\"${param.name}\", ${param.name})"
                ParameterLocation.COOKIE -> "cookie(\"${param.name}\", ${param.name})"
                ParameterLocation.PATH -> null
            }
        }

        val paramConfig = if (paramLines.isNotEmpty()) {
            "\n            " + paramLines.joinToString("\n            ")
        } else {
            ""
        }

        val bodyConfig = if (ep.requestBodyType != null) {
            "\n            setBody(body)"
        } else {
            ""
        }

        val methodEnumName = ep.method.name.lowercase().replaceFirstChar { it.uppercase() }
        val methodStr = "HttpMethod.$methodEnumName"

        return """
    override $signature {
        return try {
            val response = client.request("$fullUrl") {
                method = $methodStr$paramConfig$bodyConfig
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

    // Helper to detect Lists
    private fun isListType(type: String): Boolean = type.startsWith("List<") || type.contains("Array")

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
