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
import domain.ServerVariable
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
        val resolvedWebhooks = if (resolvedMetadata.webhooks.isEmpty() && webhooks.isEmpty()) {
            emptyMap()
        } else {
            val merged = LinkedHashMap<String, PathItem>()
            merged.putAll(resolvedMetadata.webhooks)
            merged.putAll(webhooks)
            merged
        }

        val needsQueryStringEncoding = endpoints.any { ep ->
            ep.parameters.any { it.location == ParameterLocation.QUERYSTRING && it.content.isNotEmpty() }
        }
        val needsParameterContentSerialization = endpoints.any { ep ->
            ep.parameters.any { it.location != ParameterLocation.QUERYSTRING && it.content.isNotEmpty() }
        }
        val needsFormBodyEncoding = endpoints.any { ep ->
            resolveRequestContentType(ep)?.let { isFormUrlEncodedMediaType(it) } == true
        }
        val needsMultipartBodyEncoding = endpoints.any { ep ->
            resolveRequestContentType(ep)?.let { isMultipartFormDataMediaType(it) } == true
        }
        val needsMultipartPositionalEncoding = endpoints.any { ep -> requiresMultipartPositionalEncoding(ep) }
        val needsMultipartEncoding = needsMultipartBodyEncoding || needsMultipartPositionalEncoding
        val needsSequentialJsonEncoding = endpoints.any { ep -> requiresSequentialJsonRequest(ep) }
        val needsSequentialJsonDecoding = endpoints.any { ep -> requiresSequentialJsonResponse(ep) }
        val needsSequentialJsonSupport = needsSequentialJsonEncoding || needsSequentialJsonDecoding
        val needsOAuthHelpers = resolvedSecuritySchemes.values.any { scheme ->
            scheme.type == "oauth2" || scheme.type == "openIdConnect"
        }
        val needsJsonEncoding =
            needsQueryStringEncoding ||
                needsParameterContentSerialization ||
                needsFormBodyEncoding ||
                needsMultipartEncoding ||
                needsSequentialJsonSupport ||
                needsOAuthHelpers

        // imports
        val baseImports = mutableSetOf(
            "io.ktor.client.*",
            "io.ktor.client.call.*",
            "io.ktor.client.request.*",
            "io.ktor.http.*"
        )

        if (needsFormBodyEncoding || needsMultipartEncoding || needsOAuthHelpers) {
            baseImports.add("io.ktor.client.request.forms.*")
        }
        if (needsFormBodyEncoding) {
            baseImports.add("io.ktor.http.content.*")
        }
        if (needsSequentialJsonDecoding) {
            baseImports.add("io.ktor.client.statement.*")
        }
        if (needsOAuthHelpers) {
            baseImports.add("io.ktor.client.statement.*")
            baseImports.add("java.security.MessageDigest")
            baseImports.add("java.util.Base64")
            baseImports.add("kotlin.random.Random")
            baseImports.add("kotlinx.coroutines.delay")
        }

        if (needsJsonEncoding) {
            baseImports.add("kotlinx.serialization.encodeToJsonElement")
            baseImports.add("kotlinx.serialization.encodeToString")
            if (needsSequentialJsonDecoding) {
                baseImports.add("kotlinx.serialization.decodeFromString")
            }
            baseImports.add("kotlinx.serialization.json.*")
        }

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
        val defaultUrl = if (resolvedServers.isNotEmpty()) resolvedServers.first().url else "/"
        val serverSupport = buildServerSupport(resolvedServers)

        // Companion Object (Servers + Auth Factory)
        val companionObject = buildCompanionObject(resolvedServers, resolvedSecuritySchemes, serverSupport)

        val baseUrlParam = when {
            serverSupport != null -> {
                val parts = mutableListOf(
                    "serverIndex: Int = 0",
                    "serverName: String? = null"
                )
                if (serverSupport.hasVariables) {
                    parts.add("serverVariables: ServerVariables = ServerVariables()")
                }
                val defaultCall = if (serverSupport.hasVariables) {
                    "defaultBaseUrl(serverIndex, serverName, serverVariables)"
                } else {
                    "defaultBaseUrl(serverIndex, serverName)"
                }
                parts.add("private val baseUrl: String = $defaultCall")
                parts.joinToString(",\n                ")
            }
            defaultUrl.isNotEmpty() -> "private val baseUrl: String = \"${escapeKotlinString(defaultUrl)}\""
            else -> "private val baseUrl: String = \"\""
        }

        val interfaceDoc = generateInterfaceKDoc(resolvedMetadata, resolvedWebhooks, resolvedServers, resolvedSecuritySchemes)

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
        val hasPathsExplicitEmpty = metadata.pathsExplicitEmpty
        val hasPathItems = metadata.pathItems.isNotEmpty()
        val hasWebhooksExtensions = metadata.webhooksExtensions.isNotEmpty()
        val hasWebhooksExplicitEmpty = metadata.webhooksExplicitEmpty
        val hasSecuritySchemes = securitySchemes.isNotEmpty()
        val hasComponentSchemas = metadata.componentSchemas.isNotEmpty()
        val hasComponentExamples = metadata.componentExamples.isNotEmpty()
        val hasComponentLinks = metadata.componentLinks.isNotEmpty()
        val hasComponentCallbacks = metadata.componentCallbacks.isNotEmpty()
        val hasComponentParameters = metadata.componentParameters.isNotEmpty()
        val hasComponentResponses = metadata.componentResponses.isNotEmpty()
        val hasComponentRequestBodies = metadata.componentRequestBodies.isNotEmpty()
        val hasComponentHeaders = metadata.componentHeaders.isNotEmpty()
        val hasComponentPathItems = metadata.componentPathItems.isNotEmpty()
        val hasComponentMediaTypes = metadata.componentMediaTypes.isNotEmpty()
        val hasComponentsExtensions = metadata.componentsExtensions.isNotEmpty()
        val hasWebhooks = webhooks.isNotEmpty()

        if (!hasOpenapi && !hasInfo && !hasServers && !hasSecurity && !hasSecurityEmpty &&
            !hasTags && !hasExternalDocs && !hasExtensions && !hasSecuritySchemes && !hasWebhooks &&
            !hasPathsExtensions && !hasPathsExplicitEmpty && !hasPathItems && !hasWebhooksExtensions &&
            !hasWebhooksExplicitEmpty &&
            !hasComponentExamples && !hasComponentLinks && !hasComponentCallbacks &&
            !hasComponentParameters && !hasComponentResponses && !hasComponentRequestBodies &&
            !hasComponentHeaders && !hasComponentPathItems && !hasComponentMediaTypes &&
            !hasComponentsExtensions
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
        if (hasPathsExplicitEmpty) {
            sb.append(" * @pathsEmpty\n")
        }
        if (hasPathItems) {
            sb.append(" * @pathItems ${renderPathItems(metadata.pathItems)}\n")
        }
        if (hasWebhooksExtensions) {
            sb.append(" * @webhooksExtensions ${renderExtensions(metadata.webhooksExtensions)}\n")
        }
        if (hasWebhooksExplicitEmpty) {
            sb.append(" * @webhooksEmpty\n")
        }
        if (hasSecuritySchemes) {
            sb.append(" * @securitySchemes ${renderSecuritySchemes(securitySchemes)}\n")
        }
        if (hasComponentSchemas) {
            sb.append(" * @componentSchemas ${renderComponentSchemas(metadata.componentSchemas)}\n")
        }
        if (hasComponentExamples) {
            sb.append(" * @componentExamples ${renderComponentExamples(metadata.componentExamples)}\n")
        }
        if (hasComponentLinks) {
            sb.append(" * @componentLinks ${renderComponentLinks(metadata.componentLinks)}\n")
        }
        if (hasComponentCallbacks) {
            sb.append(" * @componentCallbacks ${renderCallbacks(metadata.componentCallbacks)}\n")
        }
        if (hasComponentParameters) {
            sb.append(" * @componentParameters ${renderComponentParameters(metadata.componentParameters)}\n")
        }
        if (hasComponentResponses) {
            sb.append(" * @componentResponses ${renderComponentResponses(metadata.componentResponses)}\n")
        }
        if (hasComponentRequestBodies) {
            sb.append(" * @componentRequestBodies ${renderComponentRequestBodies(metadata.componentRequestBodies)}\n")
        }
        if (hasComponentHeaders) {
            sb.append(" * @componentHeaders ${renderComponentHeaders(metadata.componentHeaders)}\n")
        }
        if (hasComponentPathItems) {
            sb.append(" * @componentPathItems ${renderComponentPathItems(metadata.componentPathItems)}\n")
        }
        if (hasComponentMediaTypes) {
            sb.append(" * @componentMediaTypes ${renderComponentMediaTypes(metadata.componentMediaTypes)}\n")
        }
        if (hasComponentsExtensions) {
            sb.append(" * @componentsExtensions ${renderExtensions(metadata.componentsExtensions)}\n")
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

    private fun renderPathItems(pathItems: Map<String, PathItem>): String {
        if (pathItems.isEmpty()) return "{}"
        val tempDefinition = OpenApiDefinition(
            info = Info(title = "PathItems", version = "0.0.0"),
            paths = pathItems
        )
        val json = openApiWriter.writeJson(tempDefinition)
        val node = jsonMapper.readTree(json)
        val pathsNode = node.path("paths")
        return jsonMapper.writeValueAsString(pathsNode)
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

    private fun renderComponentSchemas(schemas: Map<String, domain.SchemaDefinition>): String {
        if (schemas.isEmpty()) return "{}"
        val tempDefinition = OpenApiDefinition(
            info = Info(title = "Component Schemas", version = "0.0.0"),
            components = domain.Components(schemas = schemas)
        )
        val json = openApiWriter.writeJson(tempDefinition)
        val node = jsonMapper.readTree(json)
        val schemasNode = node.path("components").path("schemas")
        return jsonMapper.writeValueAsString(schemasNode)
    }

    private fun renderComponentExamples(examples: Map<String, ExampleObject>): String {
        if (examples.isEmpty()) return "{}"
        val mapped = examples.mapValues { exampleObjectToDocValue(it.value) }
        return jsonMapper.writeValueAsString(mapped)
    }

    private fun renderComponentLinks(links: Map<String, Link>): String {
        if (links.isEmpty()) return "{}"
        val mapped = links.mapValues { linkToDocValue(it.value) }
        return jsonMapper.writeValueAsString(mapped)
    }

    private fun renderComponentParameters(parameters: Map<String, EndpointParameter>): String {
        if (parameters.isEmpty()) return "{}"
        val tempDefinition = OpenApiDefinition(
            info = Info(title = "Component Parameters", version = "0.0.0"),
            components = domain.Components(parameters = parameters)
        )
        val json = openApiWriter.writeJson(tempDefinition)
        val node = jsonMapper.readTree(json)
        val paramsNode = node.path("components").path("parameters")
        return jsonMapper.writeValueAsString(paramsNode)
    }

    private fun renderComponentResponses(responses: Map<String, EndpointResponse>): String {
        if (responses.isEmpty()) return "{}"
        val tempDefinition = OpenApiDefinition(
            info = Info(title = "Component Responses", version = "0.0.0"),
            components = domain.Components(responses = responses)
        )
        val json = openApiWriter.writeJson(tempDefinition)
        val node = jsonMapper.readTree(json)
        val responsesNode = node.path("components").path("responses")
        return jsonMapper.writeValueAsString(responsesNode)
    }

    private fun renderComponentRequestBodies(bodies: Map<String, RequestBody>): String {
        if (bodies.isEmpty()) return "{}"
        val tempDefinition = OpenApiDefinition(
            info = Info(title = "Component Request Bodies", version = "0.0.0"),
            components = domain.Components(requestBodies = bodies)
        )
        val json = openApiWriter.writeJson(tempDefinition)
        val node = jsonMapper.readTree(json)
        val bodiesNode = node.path("components").path("requestBodies")
        return jsonMapper.writeValueAsString(bodiesNode)
    }

    private fun renderComponentHeaders(headers: Map<String, Header>): String {
        if (headers.isEmpty()) return "{}"
        val tempDefinition = OpenApiDefinition(
            info = Info(title = "Component Headers", version = "0.0.0"),
            components = domain.Components(headers = headers)
        )
        val json = openApiWriter.writeJson(tempDefinition)
        val node = jsonMapper.readTree(json)
        val headersNode = node.path("components").path("headers")
        return jsonMapper.writeValueAsString(headersNode)
    }

    private fun renderComponentPathItems(pathItems: Map<String, PathItem>): String {
        if (pathItems.isEmpty()) return "{}"
        val tempDefinition = OpenApiDefinition(
            info = Info(title = "Component Path Items", version = "0.0.0"),
            components = domain.Components(pathItems = pathItems)
        )
        val json = openApiWriter.writeJson(tempDefinition)
        val node = jsonMapper.readTree(json)
        val pathItemsNode = node.path("components").path("pathItems")
        return jsonMapper.writeValueAsString(pathItemsNode)
    }

    private fun renderComponentMediaTypes(mediaTypes: Map<String, MediaTypeObject>): String {
        if (mediaTypes.isEmpty()) return "{}"
        val tempDefinition = OpenApiDefinition(
            info = Info(title = "Component Media Types", version = "0.0.0"),
            components = domain.Components(mediaTypes = mediaTypes)
        )
        val json = openApiWriter.writeJson(tempDefinition)
        val node = jsonMapper.readTree(json)
        val mediaTypesNode = node.path("components").path("mediaTypes")
        return jsonMapper.writeValueAsString(mediaTypesNode)
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
        serverSupport: ServerSupport?
    ): String {
        val blocks = mutableListOf<String>()

        if (serverSupport != null) {
            serverSupport.companionSnippet.takeIf { it.isNotBlank() }?.let { blocks.add(it) }
        } else if (servers.isNotEmpty()) {
            val listStr = servers.joinToString(", ") { "\"${escapeKotlinString(it.url)}\"" }
            blocks.add("val SERVERS = listOf($listStr)")
        }

        if (securitySchemes.isNotEmpty()) {
            blocks.add(generateAuthFactory(securitySchemes))
        }

        if (blocks.isEmpty()) return ""

        return blocks.joinToString("\n\n").prependIndent("    ")
    }

    private fun buildHelperFunctions(endpoints: List<EndpointDefinition>): String {
        val needsQueryStringEncoding = endpoints.any { ep ->
            ep.parameters.any { it.location == ParameterLocation.QUERYSTRING && it.content.isNotEmpty() }
        }
        val needsParameterContentSerialization = endpoints.any { ep ->
            ep.parameters.any { it.location != ParameterLocation.QUERYSTRING && it.content.isNotEmpty() }
        }
        val needsFormBodyEncoding = endpoints.any { ep ->
            resolveRequestContentType(ep)?.let { isFormUrlEncodedMediaType(it) } == true
        }
        val needsMultipartBodyEncoding = endpoints.any { ep ->
            resolveRequestContentType(ep)?.let { isMultipartFormDataMediaType(it) } == true
        }
        val needsMultipartPositionalEncoding = endpoints.any { ep -> requiresMultipartPositionalEncoding(ep) }
        val needsMultipartEncoding = needsMultipartBodyEncoding || needsMultipartPositionalEncoding
        val needsSequentialJsonEncoding = endpoints.any { ep -> requiresSequentialJsonRequest(ep) }
        val needsSequentialJsonDecoding = endpoints.any { ep -> requiresSequentialJsonResponse(ep) }
        val needsPathEncoding = endpoints.any { ep ->
            ep.parameters.any { it.location == ParameterLocation.PATH }
        }
        val needsAllowReserved = endpoints.any { ep ->
            ep.parameters.any { it.location == ParameterLocation.QUERY && it.allowReserved == true }
        } || needsFormBodyEncoding
        val needsEncodingPrimitives = needsAllowReserved || needsPathEncoding
        val needsContentEncodingHelpers = needsQueryStringEncoding || needsParameterContentSerialization
        val needsJsonElementHelpers =
            needsContentEncodingHelpers || needsFormBodyEncoding || needsMultipartEncoding
        val needsJsonContentHelper = needsFormBodyEncoding || needsMultipartEncoding

        val blocks = mutableListOf<String>()

        if (needsEncodingPrimitives) {
            blocks += """
                private fun isUnreserved(ch: Char): Boolean {
                    return ch.isLetterOrDigit() || ch == '-' || ch == '.' || ch == '_' || ch == '~'
                }

                private fun isHexDigit(ch: Char): Boolean {
                    return ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'
                }

                private fun byteToHex(b: Byte): String {
                    val value = b.toInt() and 0xFF
                    val digits = "0123456789ABCDEF"
                    return "${'$'}{digits[value ushr 4]}${'$'}{digits[value and 0x0F]}"
                }
            """.trimIndent()
        }

        if (needsAllowReserved) {
            blocks += """
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

                private fun isReserved(ch: Char): Boolean {
                    return ":/?#[]@!${'$'}&'()*+,;=".indexOf(ch) >= 0
                }
            """.trimIndent()
        }

        if (needsPathEncoding) {
            blocks += """
                private fun encodePathComponent(value: String, allowReserved: Boolean): String {
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
                        val allowed = isUnreserved(ch) || (allowReserved && isPathReservedAllowed(ch))
                        if (allowed) {
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

                private fun isPathReservedAllowed(ch: Char): Boolean {
                    return ":@!${'$'}&'()*+,;=".indexOf(ch) >= 0
                }
            """.trimIndent()
        }

        if (needsQueryStringEncoding) {
            blocks += """
                private inline fun <reified T> encodeQueryStringContent(value: T, contentType: String): String {
                    val normalized = contentType.substringBefore(";").trim().lowercase()
                    return when {
                        normalized == "application/x-www-form-urlencoded" -> encodeFormUrlEncoded(value)
                        normalized == "application/json" || normalized.endsWith("+json") -> encodeJsonQuery(value)
                        else -> encodeURLQueryComponent(value.toString())
                    }
                }

                private inline fun <reified T> encodeJsonQuery(value: T): String {
                    val json = Json.encodeToString(value)
                    return encodeURLQueryComponent(json)
                }
            """.trimIndent()
        }

        if (needsContentEncodingHelpers) {
            blocks += """
                private inline fun <reified T> encodeJsonString(value: T): String {
                    return Json.encodeToString(value)
                }

                private inline fun <reified T> encodeFormUrlEncoded(value: T): String {
                    val element = Json.encodeToJsonElement(value)
                    if (element is JsonObject) {
                        val params = Parameters.build {
                            element.forEach { (key, elementValue) ->
                                when (elementValue) {
                                    is JsonArray -> elementValue.forEach { item -> append(key, jsonElementToString(item)) }
                                    else -> append(key, jsonElementToString(elementValue))
                                }
                            }
                        }
                        return params.formUrlEncode()
                    }
                    return encodeURLQueryComponent(jsonElementToString(element))
                }
            """.trimIndent()
        }

        if (needsSequentialJsonEncoding) {
            blocks += """
                private inline fun <reified T> encodeSequentialJson(items: Iterable<T>, contentType: String): String {
                    val normalized = contentType.substringBefore(";").trim().lowercase()
                    if (!items.iterator().hasNext()) return ""
                    return if (normalized.endsWith("json-seq")) {
                        buildString {
                            items.forEach { item ->
                                append('\u001E')
                                append(Json.encodeToString(item))
                                append('\n')
                            }
                        }
                    } else {
                        items.joinToString("\n") { Json.encodeToString(it) }
                    }
                }
            """.trimIndent()
        }

        if (needsSequentialJsonDecoding) {
            blocks += """
                private inline fun <reified T> decodeSequentialJsonList(payload: String, contentType: String): List<T> {
                    val normalized = contentType.substringBefore(";").trim().lowercase()
                    return if (normalized.endsWith("json-seq")) {
                        payload.split('\u001E')
                            .asSequence()
                            .map { it.trimStart('\n', '\r') }
                            .filter { it.isNotBlank() }
                            .map { Json.decodeFromString<T>(it) }
                            .toList()
                    } else {
                        payload.lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .map { Json.decodeFromString<T>(it) }
                            .toList()
                    }
                }
            """.trimIndent()
        }

        if (needsParameterContentSerialization) {
            blocks += """
                private inline fun <reified T> serializeContentValue(value: T, contentType: String): String {
                    val normalized = contentType.substringBefore(";").trim().lowercase()
                    return when {
                        normalized == "application/x-www-form-urlencoded" -> encodeFormUrlEncoded(value)
                        normalized == "application/json" || normalized.endsWith("+json") -> encodeJsonString(value)
                        else -> value.toString()
                    }
                }
            """.trimIndent()
        }

        if (needsFormBodyEncoding) {
            blocks += """
                private data class FormPair(val key: String, val value: String, val allowReserved: Boolean)

                private inline fun <reified T> encodeFormBody(
                    value: T,
                    encoding: Map<String, String> = emptyMap(),
                    styles: Map<String, String> = emptyMap(),
                    explode: Map<String, Boolean> = emptyMap(),
                    allowReserved: Map<String, Boolean> = emptyMap()
                ): OutgoingContent {
                    val element = Json.encodeToJsonElement(value)
                    val pairs = mutableListOf<FormPair>()
                    when (element) {
                        is JsonObject -> {
                            element.forEach { (key, elementValue) ->
                                appendFormPairs(
                                    pairs,
                                    key,
                                    elementValue,
                                    encoding[key],
                                    styles[key],
                                    explode[key],
                                    allowReserved[key] == true
                                )
                            }
                        }
                        else -> appendFormPairs(
                            pairs,
                            "value",
                            element,
                            encoding["value"],
                            styles["value"],
                            explode["value"],
                            allowReserved["value"] == true
                        )
                    }
                    val hasOverrides = styles.isNotEmpty() || explode.isNotEmpty() || allowReserved.isNotEmpty()
                    return if (!hasOverrides) {
                        val params = Parameters.build {
                            pairs.forEach { pair -> append(pair.key, pair.value) }
                        }
                        FormDataContent(params)
                    } else {
                        val encoded = pairs.joinToString("&") { pair ->
                            encodeFormComponent(pair.key, pair.allowReserved) + "=" +
                                encodeFormComponent(pair.value, pair.allowReserved)
                        }
                        TextContent(encoded, ContentType.Application.FormUrlEncoded)
                    }
                }

                private fun appendFormPairs(
                    pairs: MutableList<FormPair>,
                    key: String,
                    element: JsonElement,
                    contentType: String?,
                    styleOverride: String?,
                    explodeOverride: Boolean?,
                    allowReserved: Boolean
                ) {
                    val style = normalizeFormStyle(styleOverride)
                    val explode = explodeOverride ?: (style == "form")
                    when (element) {
                        is JsonArray -> {
                            when (style) {
                                "form" -> {
                                    if (explode) {
                                        element.forEach { item ->
                                            pairs.add(FormPair(key, serializeElement(item, contentType), allowReserved))
                                        }
                                    } else {
                                        val joined = element.joinToString(",") { item ->
                                            serializeElement(item, contentType)
                                        }
                                        pairs.add(FormPair(key, joined, allowReserved))
                                    }
                                }
                                "spacedelimited" -> {
                                    if (explode) {
                                        throw IllegalArgumentException("OAS 3.2: spaceDelimited does not support explode=true for ${'$'}key")
                                    }
                                    val joined = element.joinToString(" ") { item ->
                                        serializeElement(item, contentType)
                                    }
                                    pairs.add(FormPair(key, joined, allowReserved))
                                }
                                "pipedelimited" -> {
                                    if (explode) {
                                        throw IllegalArgumentException("OAS 3.2: pipeDelimited does not support explode=true for ${'$'}key")
                                    }
                                    val joined = element.joinToString("|") { item ->
                                        serializeElement(item, contentType)
                                    }
                                    pairs.add(FormPair(key, joined, allowReserved))
                                }
                                "deepobject" -> {
                                    throw IllegalArgumentException("OAS 3.2: deepObject only applies to objects for ${'$'}key")
                                }
                                else -> pairs.add(FormPair(key, serializeElement(element, contentType), allowReserved))
                            }
                        }
                        is JsonObject -> {
                            when (style) {
                                "form" -> {
                                    if (explode) {
                                        element.forEach { (propName, propValue) ->
                                            pairs.add(
                                                FormPair(
                                                    propName,
                                                    serializeElement(propValue, contentType),
                                                    allowReserved
                                                )
                                            )
                                        }
                                    } else {
                                        val joined = element.entries.joinToString(",") { entry ->
                                            entry.key + "," + serializeElement(entry.value, contentType)
                                        }
                                        pairs.add(FormPair(key, joined, allowReserved))
                                    }
                                }
                                "deepobject" -> {
                                    element.forEach { (propName, propValue) ->
                                        pairs.add(
                                            FormPair(
                                                "${'$'}key[${'$'}propName]",
                                                serializeElement(propValue, contentType),
                                                allowReserved
                                            )
                                        )
                                    }
                                }
                                "spacedelimited" -> {
                                    if (explode) {
                                        throw IllegalArgumentException("OAS 3.2: spaceDelimited does not support explode=true for ${'$'}key")
                                    }
                                    val joined = element.entries
                                        .flatMap { entry -> listOf(entry.key, serializeElement(entry.value, contentType)) }
                                        .joinToString(" ")
                                    pairs.add(FormPair(key, joined, allowReserved))
                                }
                                "pipedelimited" -> {
                                    if (explode) {
                                        throw IllegalArgumentException("OAS 3.2: pipeDelimited does not support explode=true for ${'$'}key")
                                    }
                                    val joined = element.entries
                                        .flatMap { entry -> listOf(entry.key, serializeElement(entry.value, contentType)) }
                                        .joinToString("|")
                                    pairs.add(FormPair(key, joined, allowReserved))
                                }
                                else -> pairs.add(FormPair(key, serializeElement(element, contentType), allowReserved))
                            }
                        }
                        else -> pairs.add(FormPair(key, serializeElement(element, contentType), allowReserved))
                    }
                }

                private fun normalizeFormStyle(style: String?): String {
                    return style?.trim()?.lowercase() ?: "form"
                }

                private fun encodeFormComponent(value: String, allowReserved: Boolean): String {
                    val encoded = if (allowReserved) encodeAllowReserved(value) else encodeURLQueryComponent(value)
                    return encoded.replace("%20", "+")
                }
            """.trimIndent()
        }

        if (needsMultipartEncoding) {
            blocks += """
                private inline fun <reified T> encodeMultipartBody(
                    value: T,
                    encoding: Map<String, String> = emptyMap(),
                    headers: Map<String, Map<String, String>> = emptyMap()
                ): MultiPartFormDataContent {
                    val element = Json.encodeToJsonElement(value)
                    val data = formData {
                        when (element) {
                            is JsonObject -> {
                                element.forEach { (key, elementValue) ->
                                    appendMultipartValue(key, elementValue, encoding[key], headers[key])
                                }
                            }
                            else -> appendMultipartValue("value", element, encoding["value"], headers["value"])
                        }
                    }
                    return MultiPartFormDataContent(data)
                }

                private fun FormBuilder.appendMultipartValue(
                    key: String,
                    element: JsonElement,
                    contentType: String?,
                    headerOverrides: Map<String, String>?
                ) {
                    when (element) {
                        is JsonArray -> element.forEach { appendMultipartValue(key, it, contentType, headerOverrides) }
                        else -> {
                            val value = serializeElement(element, contentType)
                            val headers = buildMultipartHeaders(contentType, headerOverrides)
                            if (headers != null) {
                                append(key, value, headers)
                            } else {
                                append(key, value)
                            }
                        }
                    }
                }
            """.trimIndent()
        }

        if (needsMultipartPositionalEncoding) {
            blocks += """
                private inline fun <reified T> encodeMultipartPositional(
                    value: T,
                    prefixContentTypes: List<String?> = emptyList(),
                    prefixHeaders: List<Map<String, String>> = emptyList(),
                    itemContentType: String? = null,
                    itemHeaders: Map<String, String> = emptyMap()
                ): MultiPartFormDataContent {
                    val element = Json.encodeToJsonElement(value)
                    val items = when (element) {
                        is JsonArray -> element
                        else -> JsonArray(listOf(element))
                    }
                    val data = formData {
                        items.forEachIndexed { index, item ->
                            val isPrefix = index < prefixContentTypes.size
                            val contentType = if (isPrefix) prefixContentTypes[index] else itemContentType
                            val headerOverrides = if (isPrefix) {
                                prefixHeaders.getOrNull(index) ?: emptyMap()
                            } else {
                                itemHeaders
                            }
                            val headers = if (headerOverrides.isEmpty()) null else headerOverrides
                            appendMultipartValue("part${'$'}index", item, contentType, headers)
                        }
                    }
                    return MultiPartFormDataContent(data)
                }
            """.trimIndent()
        }

        if (needsMultipartEncoding) {
            blocks += """
                private fun buildMultipartHeaders(
                    contentType: String?,
                    overrides: Map<String, String>?
                ): Headers? {
                    if (contentType == null && overrides.isNullOrEmpty()) return null
                    return Headers.build {
                        if (!contentType.isNullOrBlank()) {
                            append(HttpHeaders.ContentType, contentType)
                        }
                        overrides?.forEach { (name, value) ->
                            if (!name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                                append(name, value)
                            }
                        }
                    }
                }
            """.trimIndent()
        }

        if (needsJsonContentHelper) {
            blocks += """
                private fun serializeElement(element: JsonElement, contentType: String?): String {
                    return if (isJsonMediaType(contentType)) element.toString() else jsonElementToString(element)
                }

                private fun isJsonMediaType(contentType: String?): Boolean {
                    if (contentType == null) return false
                    val normalized = contentType.substringBefore(";").trim().lowercase()
                    return normalized == "application/json" || normalized.endsWith("+json")
                }
            """.trimIndent()
        }

        if (needsJsonElementHelpers) {
            blocks += """
                private fun jsonElementToString(element: JsonElement): String {
                    return when (element) {
                        is JsonPrimitive -> element.content
                        else -> element.toString()
                    }
                }
            """.trimIndent()
        }

        if (blocks.isEmpty()) return ""

        val body = blocks.joinToString("\n\n").prependIndent("        ")
        return "    companion object {\n$body\n    }"
    }

    private data class ServerSupport(
        val hasVariables: Boolean,
        val companionSnippet: String
    )

    private data class QueryParamLines(
        val lines: List<String>,
        val emptyValueLine: String?
    )

    private fun buildServerSupport(servers: List<Server>): ServerSupport? {
        if (servers.isEmpty()) return null
        val hasVariables = servers.any { !it.variables.isNullOrEmpty() }
        val hasNames = servers.any { !it.name.isNullOrBlank() }
        val needsSelection = servers.size > 1 || hasVariables || hasNames
        if (!needsSelection) return null

        val serverSpecBlock = buildServerSpecBlock(servers)
        val variablesBlock = if (hasVariables) buildServerVariablesBlock(servers) else ""
        val helpersBlock = buildServerHelperFunctions(hasVariables)

        val snippet = listOf(serverSpecBlock, variablesBlock, helpersBlock)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

        return ServerSupport(
            hasVariables = hasVariables,
            companionSnippet = snippet
        )
    }

    private fun buildServerSpecBlock(servers: List<Server>): String {
        val entries = servers.map { server ->
            val args = mutableListOf("url = \"${escapeKotlinString(server.url)}\"")
            server.name?.let { args.add("name = \"${escapeKotlinString(it)}\"") }
            val variables = server.variables.orEmpty()
            if (variables.isNotEmpty()) {
                val vars = variables.entries.joinToString(", ") { (name, variable) ->
                    "\"${escapeKotlinString(name)}\" to \"${escapeKotlinString(variable.default)}\""
                }
                args.add("variables = mapOf($vars)")
            }
            "ServerSpec(${args.joinToString(", ")})"
        }

        val listBody = entries.joinToString(",\n            ")
        return """
            data class ServerSpec(
                val url: String,
                val name: String? = null,
                val variables: Map<String, String> = emptyMap()
            )

            val SERVERS = listOf(
                $listBody
            )
        """.trimIndent()
    }

    private fun buildServerVariablesBlock(servers: List<Server>): String {
        val allVariables = LinkedHashMap<String, ServerVariable>()
        servers.forEach { server ->
            server.variables?.forEach { (rawName, variable) ->
                if (!allVariables.containsKey(rawName)) {
                    allVariables[rawName] = variable
                }
            }
        }
        if (allVariables.isEmpty()) return ""

        val usedNames = mutableSetOf<String>()
        val usedEnumNames = mutableSetOf<String>()
        val propertyLines = mutableListOf<String>()
        val mapEntries = mutableListOf<String>()
        val enumBlocks = mutableListOf<String>()

        allVariables.entries.forEachIndexed { index, (rawName, variable) ->
            var safeName = sanitizeVariableName(rawName)
            if (safeName.isBlank()) {
                safeName = "var${index + 1}"
            }
            var uniqueName = safeName
            var suffix = 2
            while (!usedNames.add(uniqueName)) {
                uniqueName = "${safeName}_${suffix++}"
            }
            val enumValues = variable.enum.orEmpty().filterNotNull()
            if (enumValues.isNotEmpty()) {
                val baseEnumName = toPascalCase(uniqueName)
                val enumName = uniqueEnumName(baseEnumName, usedEnumNames)
                val constants = buildEnumConstants(enumValues)
                val defaultConst = constants.firstOrNull { it.second == variable.default }?.first ?: constants.first().first
                val enumBody = constants.joinToString(",\n                ") { (constName, rawValue) ->
                    "$constName(\"${escapeKotlinString(rawValue)}\")"
                }
                enumBlocks.add(
                    """
                        enum class $enumName(val value: String) {
                            $enumBody
                        }
                    """.trimIndent()
                )
                propertyLines.add("val $uniqueName: $enumName = $enumName.$defaultConst")
                mapEntries.add("\"${escapeKotlinString(rawName)}\" to $uniqueName.value")
            } else {
                val defaultValue = escapeKotlinString(variable.default)
                propertyLines.add("val $uniqueName: String = \"$defaultValue\"")
                mapEntries.add("\"${escapeKotlinString(rawName)}\" to $uniqueName")
            }
        }

        val enumsBlock = enumBlocks.joinToString("\n\n")

        return """
            ${enumsBlock.takeIf { it.isNotBlank() } ?: ""}

            data class ServerVariables(
                ${propertyLines.joinToString(",\n                ")}
            ) {
                fun toMap(): Map<String, String> = mapOf(
                    ${mapEntries.joinToString(",\n                    ")}
                )
            }
        """.trimIndent()
    }

    private fun buildServerHelperFunctions(hasVariables: Boolean): String {
        val variablesParam = if (hasVariables) ", variables: ServerVariables = ServerVariables()" else ""
        val variablesMap = if (hasVariables) "variables.toMap()" else "emptyMap()"
        val serverSpecFallback = if (hasVariables) {
            "ServerSpec(url = \"/\", name = null, variables = emptyMap())"
        } else {
            "ServerSpec(url = \"/\", name = null)"
        }
        val defaultBaseUrlSignature = "fun defaultBaseUrl(serverIndex: Int = 0, serverName: String? = null$variablesParam): String"

        return """
            fun resolveServerUrl(template: String, variables: Map<String, String>): String {
                var resolved = template
                variables.forEach { (key, value) ->
                    resolved = resolved.replace("{${'$'}key}", value)
                }
                return resolved
            }

            fun selectServer(serverIndex: Int = 0, serverName: String? = null): ServerSpec {
                if (SERVERS.isEmpty()) return $serverSpecFallback
                val byName = serverName?.let { name -> SERVERS.firstOrNull { it.name == name } }
                return byName ?: SERVERS.getOrNull(serverIndex) ?: SERVERS.first()
            }

            $defaultBaseUrlSignature {
                if (SERVERS.isEmpty()) return "/"
                val server = selectServer(serverIndex, serverName)
                val vars = $variablesMap
                val merged = if (server.variables.isNotEmpty()) server.variables + vars else vars
                return if (merged.isEmpty()) server.url else resolveServerUrl(server.url, merged)
            }
        """.trimIndent()
    }

    private fun sanitizeVariableName(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_')
        val base = if (cleaned.isEmpty()) "var" else cleaned
        val normalized = if (base.first().isDigit()) "_$base" else base
        return normalized.replaceFirstChar { it.lowercase() }
    }

    private fun toPascalCase(raw: String): String {
        val parts = raw.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "Var"
        val joined = parts.joinToString("") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
        return if (joined.firstOrNull()?.isDigit() == true) "Var$joined" else joined
    }

    private fun sanitizeEnumConstant(value: String): String {
        val trimmed = value.trim()
        val cleaned = trimmed.replace(Regex("[^a-zA-Z0-9]+"), "_").trim('_')
        val base = if (cleaned.isBlank()) "VALUE" else cleaned
        val normalized = if (base.first().isDigit()) "VALUE_$base" else base
        return normalized.uppercase()
    }

    private fun uniqueEnumName(base: String, used: MutableSet<String>): String {
        var name = if (base.isBlank()) "Var" else base
        var suffix = 2
        while (!used.add(name)) {
            name = "${base}_${suffix++}"
        }
        return name
    }

    private fun buildEnumConstants(values: List<String>): List<Pair<String, String>> {
        val used = mutableSetOf<String>()
        return values.map { raw ->
            val base = sanitizeEnumConstant(raw)
            var name = base
            var suffix = 2
            while (!used.add(name)) {
                name = "${base}_${suffix++}"
            }
            name to raw
        }
    }

    private fun escapeKotlinString(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun resolveServerUrlTemplate(server: Server): String {
        val variables = server.variables.orEmpty()
        if (variables.isEmpty()) return server.url
        return resolveServerUrlTemplate(server.url, variables.mapValues { it.value.default })
    }

    private fun resolveServerUrlTemplate(
        template: String,
        variables: Map<String, String>
    ): String {
        if (variables.isEmpty()) return template
        var resolved = template
        variables.forEach { (key, value) ->
            resolved = resolved.replace("{${key}}", value)
        }
        return resolved
    }

    private fun generateAuthFactory(schemes: Map<String, SecurityScheme>): String {
        val hasHttpAuth = schemes.values.any { it.type == "http" }
        val hasOAuthAuth = schemes.values.any { it.type == "oauth2" || it.type == "openIdConnect" }
        val hasMutualTls = schemes.values.any { it.type == "mutualTLS" }

        val usedNames = mutableSetOf<String>()
        fun uniqueName(base: String): String {
            val normalized = base.ifBlank { "auth" }
            var name = normalized
            var suffix = 2
            while (!usedNames.add(name)) {
                name = "${normalized}${suffix++}"
            }
            return name
        }

        val schemeParamNames = mutableMapOf<String, String>()
        val basicParamNames = mutableMapOf<String, Pair<String, String>>()
        val args = mutableListOf<String>()

        schemes.forEach { (key, scheme) ->
            val base = sanitizeIdentifier(key).ifBlank { "auth" }
            when {
                scheme.type == "http" && scheme.scheme?.lowercase() == "basic" -> {
                    val user = uniqueName("${base}User")
                    val pass = uniqueName("${base}Pass")
                    basicParamNames[key] = user to pass
                    args += "$user: String? = null"
                    args += "$pass: String? = null"
                }
                scheme.type == "oauth2" || scheme.type == "openIdConnect" -> {
                    val name = uniqueName(base)
                    schemeParamNames[key] = name
                    args += "$name: OAuthTokens? = null"
                }
                scheme.type == "mutualTLS" -> {
                    val name = uniqueName(base)
                    schemeParamNames[key] = name
                    args += "$name: MutualTlsConfig? = null"
                }
                else -> {
                    val name = uniqueName(base)
                    schemeParamNames[key] = name
                    args += "$name: String? = null"
                }
            }
        }

        val mutualTlsConfigurerName = if (hasMutualTls) uniqueName("mutualTlsConfigurer") else null
        if (mutualTlsConfigurerName != null) {
            args += "$mutualTlsConfigurerName: MutualTlsConfigurer? = null"
        }

        // Build Install blocks
        val installBlocks = StringBuilder()
        val apiKeys = schemes.entries.filter { it.value.type == "apiKey" }

        // 1. HTTP Auth (Bearer / Basic) + OAuth2/OpenID Connect
        if (hasHttpAuth || hasOAuthAuth) {
            installBlocks.append("install(Auth) {\n")
            schemes.forEach { (key, scheme) ->
                when (scheme.type) {
                    "http" -> {
                        when (scheme.scheme?.lowercase()) {
                            "basic" -> {
                                val creds = basicParamNames[key]
                                if (creds != null) {
                                    val (userParam, passParam) = creds
                                    installBlocks.append("""
                                        basic {
                                            credentials {
                                                if ($userParam != null && $passParam != null) {
                                                    BasicAuthCredentials(username = $userParam, password = $passParam)
                                                } else null
                                            }
                                        }
                                    """.trimIndent().prependIndent("                ") + "\n")
                                }
                            }
                            "bearer" -> {
                                val tokenParam = schemeParamNames[key] ?: sanitizeIdentifier(key)
                                installBlocks.append("""
                                    bearer {
                                        loadTokens {
                                            if ($tokenParam != null) {
                                                BearerTokens(accessToken = $tokenParam, refreshToken = null)
                                            } else null
                                        }
                                    }
                                """.trimIndent().prependIndent("                ") + "\n")
                            }
                        }
                    }
                    "oauth2", "openIdConnect" -> {
                        val tokenParam = schemeParamNames[key] ?: sanitizeIdentifier(key)
                        installBlocks.append("""
                            bearer {
                                loadTokens {
                                    if ($tokenParam != null) {
                                        BearerTokens(accessToken = $tokenParam.accessToken, refreshToken = $tokenParam.refreshToken)
                                    } else null
                                }
                            }
                        """.trimIndent().prependIndent("                ") + "\n")
                    }
                }
            }
            installBlocks.append("            }\n")
        }

        // 2. Api Keys (DefaultRequest)
        if (apiKeys.isNotEmpty()) {
            installBlocks.append("            install(DefaultRequest) {\n")
            apiKeys.forEach { (key, scheme) ->
                val paramName = schemeParamNames[key] ?: sanitizeIdentifier(key)
                val headerName = scheme.name ?: key
                val location = scheme.`in`

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

        // 3. mutualTLS configuration hook
        if (hasMutualTls && mutualTlsConfigurerName != null) {
            installBlocks.append("            $mutualTlsConfigurerName?.let { configurer ->\n")
            schemes.filter { it.value.type == "mutualTLS" }.forEach { (key, _) ->
                val paramName = schemeParamNames[key] ?: sanitizeIdentifier(key)
                installBlocks.append("""
                    if ($paramName != null) {
                        configurer(this, $paramName)
                    }
                """.trimIndent().prependIndent("                ") + "\n")
            }
            installBlocks.append("            }\n")
        }

        val helperBlocks = mutableListOf<String>()
        if (hasMutualTls) {
            helperBlocks.add(generateMutualTlsHelperBlock())
        }
        if (hasOAuthAuth) {
            helperBlocks.add(generateOAuthHelperBlock())
        }

        val argsBlock = args.joinToString(",\n            ")
        val createClientBlock = """
            /**
             * Creates a Ktor HttpClient configured with the defined Security Schemes.
             */
            fun createHttpClient(
                $argsBlock
            ): HttpClient {
                return HttpClient {
        $installBlocks                }
            }
        """.trimIndent()

        helperBlocks.add(createClientBlock)

        return helperBlocks.joinToString("\n\n").trimIndent().replace("\n", "\n    ")
    }

    private fun generateMutualTlsHelperBlock(): String {
        return """
            /**
             * Configuration for mutual TLS (mTLS) client credentials.
             */
            data class MutualTlsConfig(
                val keyStorePath: String? = null,
                val keyStorePassword: String? = null,
                val keyStoreType: String = "PKCS12",
                val trustStorePath: String? = null,
                val trustStorePassword: String? = null,
                val trustStoreType: String = "PKCS12"
            )

            /**
             * Hook to apply mutual TLS settings to the Ktor client engine.
             */
            typealias MutualTlsConfigurer = HttpClientConfig<*>.(MutualTlsConfig) -> Unit
        """.trimIndent()
    }

    private fun generateOAuthHelperBlock(): String {
        return """
            data class OAuthTokens(
                val accessToken: String,
                val tokenType: String? = null,
                val refreshToken: String? = null,
                val expiresIn: Long? = null,
                val scope: String? = null,
                val idToken: String? = null,
                val raw: JsonObject? = null
            )

            data class OAuthError(
                val error: String? = null,
                val errorDescription: String? = null,
                val errorUri: String? = null,
                val raw: JsonObject? = null
            )

            data class OAuthDeviceCodeResponse(
                val deviceCode: String,
                val userCode: String,
                val verificationUri: String,
                val verificationUriComplete: String? = null,
                val expiresIn: Long? = null,
                val interval: Long? = null,
                val raw: JsonObject? = null
            )

            data class Pkce(
                val codeVerifier: String,
                val codeChallenge: String,
                val method: String = "S256"
            )

            fun createPkceVerifier(length: Int = 64): String {
                require(length in 43..128) { "PKCE verifier length must be between 43 and 128." }
                val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
                return buildString(length) {
                    repeat(length) {
                        append(chars[Random.nextInt(chars.length)])
                    }
                }
            }

            fun createPkce(length: Int = 64): Pkce {
                val verifier = createPkceVerifier(length)
                val challenge = pkceS256Challenge(verifier)
                return Pkce(codeVerifier = verifier, codeChallenge = challenge)
            }

            private fun pkceS256Challenge(verifier: String): String {
                val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
                return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
            }

            fun buildAuthorizationUrl(
                authorizationUrl: String,
                clientId: String,
                redirectUri: String,
                scopes: List<String> = emptyList(),
                state: String? = null,
                responseType: String = "code",
                codeChallenge: String? = null,
                codeChallengeMethod: String? = null,
                additionalParameters: Map<String, String> = emptyMap()
            ): String {
                val builder = URLBuilder(authorizationUrl)
                builder.parameters.append("response_type", responseType)
                builder.parameters.append("client_id", clientId)
                builder.parameters.append("redirect_uri", redirectUri)
                if (scopes.isNotEmpty()) {
                    builder.parameters.append("scope", scopes.joinToString(" "))
                }
                if (state != null) {
                    builder.parameters.append("state", state)
                }
                if (codeChallenge != null) {
                    builder.parameters.append("code_challenge", codeChallenge)
                    builder.parameters.append("code_challenge_method", codeChallengeMethod ?: "S256")
                }
                additionalParameters.forEach { (key, value) ->
                    builder.parameters.append(key, value)
                }
                return builder.buildString()
            }

            suspend fun exchangeAuthorizationCode(
                client: HttpClient,
                tokenUrl: String,
                clientId: String,
                code: String,
                redirectUri: String,
                clientSecret: String? = null,
                codeVerifier: String? = null,
                additionalParameters: Map<String, String> = emptyMap()
            ): OAuthTokens {
                val params = buildOAuthParameters(
                    mapOf(
                        "grant_type" to "authorization_code",
                        "code" to code,
                        "redirect_uri" to redirectUri,
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "code_verifier" to codeVerifier
                    ),
                    additionalParameters
                )
                return requestTokenOrThrow(client, tokenUrl, params)
            }

            suspend fun refreshToken(
                client: HttpClient,
                tokenUrl: String,
                refreshToken: String,
                clientId: String,
                clientSecret: String? = null,
                scopes: List<String> = emptyList(),
                additionalParameters: Map<String, String> = emptyMap()
            ): OAuthTokens {
                val params = buildOAuthParameters(
                    mapOf(
                        "grant_type" to "refresh_token",
                        "refresh_token" to refreshToken,
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "scope" to scopes.takeIf { it.isNotEmpty() }?.joinToString(" ")
                    ),
                    additionalParameters
                )
                return requestTokenOrThrow(client, tokenUrl, params)
            }

            suspend fun clientCredentialsToken(
                client: HttpClient,
                tokenUrl: String,
                clientId: String,
                clientSecret: String? = null,
                scopes: List<String> = emptyList(),
                additionalParameters: Map<String, String> = emptyMap()
            ): OAuthTokens {
                val params = buildOAuthParameters(
                    mapOf(
                        "grant_type" to "client_credentials",
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "scope" to scopes.takeIf { it.isNotEmpty() }?.joinToString(" ")
                    ),
                    additionalParameters
                )
                return requestTokenOrThrow(client, tokenUrl, params)
            }

            suspend fun passwordToken(
                client: HttpClient,
                tokenUrl: String,
                clientId: String,
                username: String,
                password: String,
                clientSecret: String? = null,
                scopes: List<String> = emptyList(),
                additionalParameters: Map<String, String> = emptyMap()
            ): OAuthTokens {
                val params = buildOAuthParameters(
                    mapOf(
                        "grant_type" to "password",
                        "username" to username,
                        "password" to password,
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "scope" to scopes.takeIf { it.isNotEmpty() }?.joinToString(" ")
                    ),
                    additionalParameters
                )
                return requestTokenOrThrow(client, tokenUrl, params)
            }

            suspend fun requestDeviceCode(
                client: HttpClient,
                deviceAuthorizationUrl: String,
                clientId: String,
                scopes: List<String> = emptyList(),
                additionalParameters: Map<String, String> = emptyMap()
            ): OAuthDeviceCodeResponse {
                val params = buildOAuthParameters(
                    mapOf(
                        "client_id" to clientId,
                        "scope" to scopes.takeIf { it.isNotEmpty() }?.joinToString(" ")
                    ),
                    additionalParameters
                )
                val response = client.submitForm(url = deviceAuthorizationUrl, formParameters = params)
                val payload = response.bodyAsText()
                val json = parseJsonObject(payload)
                if (response.status.value !in 200..299) {
                    val error = json?.let { parseOAuthError(it) }
                        ?: OAuthError(error = "http_" + response.status.value, errorDescription = response.status.description)
                    throw ApiException(tokenErrorMessage(error))
                }
                if (json == null) {
                    throw ApiException("Device authorization response was not valid JSON.")
                }
                val deviceCode = json["device_code"]?.jsonPrimitive?.contentOrNull
                    ?: throw ApiException("Device authorization response missing device_code.")
                val userCode = json["user_code"]?.jsonPrimitive?.contentOrNull
                    ?: throw ApiException("Device authorization response missing user_code.")
                val verificationUri = json["verification_uri"]?.jsonPrimitive?.contentOrNull
                    ?: throw ApiException("Device authorization response missing verification_uri.")
                val verificationUriComplete = json["verification_uri_complete"]?.jsonPrimitive?.contentOrNull
                val expiresIn = json["expires_in"]?.jsonPrimitive?.longOrNull
                val interval = json["interval"]?.jsonPrimitive?.longOrNull
                return OAuthDeviceCodeResponse(
                    deviceCode = deviceCode,
                    userCode = userCode,
                    verificationUri = verificationUri,
                    verificationUriComplete = verificationUriComplete,
                    expiresIn = expiresIn,
                    interval = interval,
                    raw = json
                )
            }

            suspend fun exchangeDeviceToken(
                client: HttpClient,
                tokenUrl: String,
                deviceCode: String,
                clientId: String,
                clientSecret: String? = null,
                additionalParameters: Map<String, String> = emptyMap()
            ): OAuthTokens {
                val params = buildOAuthParameters(
                    mapOf(
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        "device_code" to deviceCode,
                        "client_id" to clientId,
                        "client_secret" to clientSecret
                    ),
                    additionalParameters
                )
                return requestTokenOrThrow(client, tokenUrl, params)
            }

            suspend fun pollDeviceToken(
                client: HttpClient,
                tokenUrl: String,
                deviceCode: String,
                clientId: String,
                clientSecret: String? = null,
                intervalSeconds: Long = 5,
                timeoutSeconds: Long = 600,
                additionalParameters: Map<String, String> = emptyMap()
            ): OAuthTokens {
                var intervalMs = intervalSeconds.coerceAtLeast(1L) * 1000L
                val deadline = System.currentTimeMillis() + (timeoutSeconds.coerceAtLeast(1L) * 1000L)
                val params = buildOAuthParameters(
                    mapOf(
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        "device_code" to deviceCode,
                        "client_id" to clientId,
                        "client_secret" to clientSecret
                    ),
                    additionalParameters
                )
                while (System.currentTimeMillis() < deadline) {
                    when (val result = requestToken(client, tokenUrl, params)) {
                        is OAuthTokenResult.Success -> return result.tokens
                        is OAuthTokenResult.Error -> {
                            when (result.error.error) {
                                "authorization_pending" -> delay(intervalMs)
                                "slow_down" -> {
                                    intervalMs += 5000L
                                    delay(intervalMs)
                                }
                                "expired_token" -> throw ApiException(tokenErrorMessage(result.error))
                                else -> throw ApiException(tokenErrorMessage(result.error))
                            }
                        }
                    }
                }
                throw ApiException("Device authorization timed out.")
            }

            private sealed class OAuthTokenResult {
                data class Success(val tokens: OAuthTokens) : OAuthTokenResult()
                data class Error(val error: OAuthError) : OAuthTokenResult()
            }

            private fun buildOAuthParameters(
                base: Map<String, String?>,
                additional: Map<String, String>
            ): Parameters {
                return Parameters.build {
                    base.forEach { (key, value) ->
                        if (value != null) {
                            append(key, value)
                        }
                    }
                    additional.forEach { (key, value) ->
                        append(key, value)
                    }
                }
            }

            private suspend fun requestToken(
                client: HttpClient,
                tokenUrl: String,
                parameters: Parameters
            ): OAuthTokenResult {
                val response = client.submitForm(url = tokenUrl, formParameters = parameters)
                val payload = response.bodyAsText()
                val json = parseJsonObject(payload)
                return if (response.status.value in 200..299) {
                    val tokens = json?.let { parseOAuthTokens(it) }
                    if (tokens != null) {
                        OAuthTokenResult.Success(tokens)
                    } else {
                        OAuthTokenResult.Error(
                            OAuthError(
                                error = "invalid_token_response",
                                errorDescription = "Missing access_token in response.",
                                raw = json
                            )
                        )
                    }
                } else {
                    val error = json?.let { parseOAuthError(it) }
                        ?: OAuthError(
                            error = "http_" + response.status.value,
                            errorDescription = payload.ifBlank { response.status.description },
                            raw = json
                        )
                    OAuthTokenResult.Error(error)
                }
            }

            private suspend fun requestTokenOrThrow(
                client: HttpClient,
                tokenUrl: String,
                parameters: Parameters
            ): OAuthTokens {
                return when (val result = requestToken(client, tokenUrl, parameters)) {
                    is OAuthTokenResult.Success -> result.tokens
                    is OAuthTokenResult.Error -> throw ApiException(tokenErrorMessage(result.error))
                }
            }

            private fun parseOAuthTokens(json: JsonObject): OAuthTokens? {
                val accessToken = json["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
                val tokenType = json["token_type"]?.jsonPrimitive?.contentOrNull
                val refreshToken = json["refresh_token"]?.jsonPrimitive?.contentOrNull
                val expiresIn = json["expires_in"]?.jsonPrimitive?.longOrNull
                val scope = json["scope"]?.jsonPrimitive?.contentOrNull
                val idToken = json["id_token"]?.jsonPrimitive?.contentOrNull
                return OAuthTokens(
                    accessToken = accessToken,
                    tokenType = tokenType,
                    refreshToken = refreshToken,
                    expiresIn = expiresIn,
                    scope = scope,
                    idToken = idToken,
                    raw = json
                )
            }

            private fun parseOAuthError(json: JsonObject): OAuthError {
                return OAuthError(
                    error = json["error"]?.jsonPrimitive?.contentOrNull,
                    errorDescription = json["error_description"]?.jsonPrimitive?.contentOrNull,
                    errorUri = json["error_uri"]?.jsonPrimitive?.contentOrNull,
                    raw = json
                )
            }

            private fun parseJsonObject(payload: String): JsonObject? {
                return runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull()
            }

            private fun tokenErrorMessage(error: OAuthError): String {
                val parts = listOfNotNull(error.error, error.errorDescription, error.errorUri)
                return if (parts.isEmpty()) "OAuth error." else "OAuth error: " + parts.joinToString(" - ")
            }
        """.trimIndent()
    }

    private fun generateKDoc(ep: EndpointDefinition): String {
        val hasSummary = !ep.summary.isNullOrBlank()
        val hasDescription = !ep.description.isNullOrBlank()
        val hasExtDocs = ep.externalDocs != null
        val hasTags = ep.tags.isNotEmpty()
        val hasResponses = ep.responses.isNotEmpty()
        val hasOperationIdOmitted = !ep.operationIdExplicit
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
                requestBody.contentPresent ||
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
            !hasSecurityEmpty && !hasCallbacks && !hasExtensions &&
            !hasOperationIdOmitted
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
        if (hasOperationIdOmitted) {
            sb.append("     * @operationIdOmitted\n")
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
        val filtered = headers.filterKeys { !it.equals("Content-Type", ignoreCase = true) }
        val mapped = filtered.mapValues { headerToDocValue(it.value) }
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
        if (body.content.isNotEmpty() || body.contentPresent) {
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

        val baseUrlExpr = if (ep.servers.isNotEmpty()) {
            val resolved = resolveServerUrlTemplate(ep.servers.first())
            escapeKotlinString(resolved)
        } else {
            "\$baseUrl"
        }
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

        val queryStringContentType = queryStringParam?.let { resolveQueryStringContentType(it) }
        if (queryStringParam != null && queryStringContentType == null) {
            val cleanType = resolveParameterType(queryStringParam, isOptionalParam(queryStringParam)).replace(" ", "")
            val isString = cleanType == "String" || cleanType == "String?"
            if (!isString) {
                throw IllegalArgumentException("OAS 3.2: querystring parameter must be String when content is not defined for ${ep.operationId}")
            }
        }

        val queryStringConfig = if (queryStringParam != null) {
            val assignment = if (queryStringContentType != null) {
                "url.encodedQuery = encodeQueryStringContent(${queryStringParam.name}, \"$queryStringContentType\")"
            } else {
                "url.encodedQuery = ${queryStringParam.name}"
            }
            if (isOptionalParam(queryStringParam)) {
                "\n            if (${queryStringParam.name} != null) {\n                $assignment\n            }"
            } else {
                "\n            $assignment"
            }
        } else {
            ""
        }

        val bodySignature = resolveRequestBodySignature(ep)
        val requestMediaType = resolveRequestMediaTypeEntry(ep)
        val contentType = requestMediaType?.key
        val isFormUrlEncoded = contentType?.let { isFormUrlEncodedMediaType(it) } == true
        val isMultipartFormData = contentType?.let { isMultipartFormDataMediaType(it) } == true
        val isMultipart = contentType?.let { isMultipartMediaType(it) } == true
        val usesPositionalMultipart = isMultipart && hasPositionalEncoding(requestMediaType?.value)
        val sequentialRequestElementType = if (contentType != null && bodySignature != null && isSequentialJsonMediaType(contentType)) {
            extractListElementType(bodySignature.kotlinType)
        } else {
            null
        }
        val encodingLiteral = renderEncodingContentTypeLiteral(requestMediaType?.value)
        val encodingStyleLiteral = renderEncodingStyleLiteral(requestMediaType?.value)
        val encodingExplodeLiteral = renderEncodingExplodeLiteral(requestMediaType?.value)
        val encodingAllowReservedLiteral = renderEncodingAllowReservedLiteral(requestMediaType?.value)
        val encodingHeadersLiteral = renderEncodingHeadersLiteral(requestMediaType?.value)
        val prefixEncodingContentTypesLiteral = renderPrefixEncodingContentTypesLiteral(requestMediaType?.value)
        val prefixEncodingHeadersLiteral = renderPrefixEncodingHeadersLiteral(requestMediaType?.value)
        val itemEncodingContentTypeLiteral = renderItemEncodingContentTypeLiteral(requestMediaType?.value)
        val itemEncodingHeadersLiteral = renderItemEncodingHeadersLiteral(requestMediaType?.value)
        val bodyConfig = if (bodySignature != null) {
            val lines = mutableListOf<String>()
            if (contentType != null && !isFormUrlEncoded && !isMultipartFormData && isConcreteMediaType(contentType)) {
                lines.add("contentType(ContentType.parse(\"$contentType\"))")
            }
            val encodingArg = encodingLiteral?.let { ", encoding = $it" } ?: ""
            val encodingStyleArg = encodingStyleLiteral?.let { ", styles = $it" } ?: ""
            val encodingExplodeArg = encodingExplodeLiteral?.let { ", explode = $it" } ?: ""
            val encodingAllowReservedArg = encodingAllowReservedLiteral?.let { ", allowReserved = $it" } ?: ""
            val encodingHeadersArg = encodingHeadersLiteral?.let { ", headers = $it" } ?: ""
            val positionalArgsList = mutableListOf<String>()
            prefixEncodingContentTypesLiteral?.let { positionalArgsList.add("prefixContentTypes = $it") }
            prefixEncodingHeadersLiteral?.let { positionalArgsList.add("prefixHeaders = $it") }
            itemEncodingContentTypeLiteral?.let { positionalArgsList.add("itemContentType = $it") }
            itemEncodingHeadersLiteral?.let { positionalArgsList.add("itemHeaders = $it") }
            val positionalArgs = if (positionalArgsList.isNotEmpty()) {
                ", " + positionalArgsList.joinToString(", ")
            } else {
                ""
            }
            val bodyLine = when {
                sequentialRequestElementType != null ->
                    "setBody(encodeSequentialJson(body, \"${escapeKotlinString(contentType ?: "")}\"))"
                usesPositionalMultipart ->
                    "setBody(encodeMultipartPositional(body$positionalArgs))"
                isFormUrlEncoded -> "setBody(encodeFormBody(body$encodingArg$encodingStyleArg$encodingExplodeArg$encodingAllowReservedArg))"
                isMultipartFormData -> "setBody(encodeMultipartBody(body$encodingArg$encodingHeadersArg))"
                else -> "setBody(body)"
            }
            lines.add(bodyLine)

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

        val responseMediaType = resolveResponseMediaTypeEntry(ep)
        val responseContentType = responseMediaType?.key
        val sequentialResponseElementType = if (responseContentType != null && isSequentialJsonMediaType(responseContentType)) {
            extractListElementType(returnType)
        } else {
            null
        }
        val successBodyExpr = if (sequentialResponseElementType != null) {
            "decodeSequentialJsonList<$sequentialResponseElementType>(response.bodyAsText(), \"${escapeKotlinString(responseContentType ?: "")}\")"
        } else {
            "response.body<$returnType>()"
        }

        return """
    override $signature {
        return try {
            val response = client.request("$fullUrl") {
                method = $methodStr$paramConfig$queryStringConfig$bodyConfig
            }
            if (response.status.isSuccess()) {
                Result.success($successBodyExpr)
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

    private fun resolveQueryStringContentType(param: EndpointParameter): String? {
        return resolveContentType(param.content)
    }

    private fun resolveParameterContentType(param: EndpointParameter): String? {
        return resolveContentType(param.content)
    }

    private fun resolveContentType(content: Map<String, MediaTypeObject>): String? {
        if (content.isEmpty()) return null
        val keys = content.keys
        val form = keys.firstOrNull { it.trim().startsWith("application/x-www-form-urlencoded") }
        if (form != null) return form
        val json = keys.firstOrNull {
            val trimmed = it.trim()
            trimmed.startsWith("application/json") || trimmed.substringBefore(";").endsWith("+json")
        }
        if (json != null) return json
        return keys.first()
    }

    private fun isOptionalParam(param: EndpointParameter): Boolean {
        if (param.location == ParameterLocation.PATH) return false
        return !param.isRequired
    }

    private fun resolveRequestContentType(ep: EndpointDefinition): String? {
        val content = ep.requestBody?.content ?: return null
        if (content.isEmpty()) return null
        return selectPreferredMediaTypeEntry(content).key
    }

    private fun resolveRequestMediaTypeEntry(
        ep: EndpointDefinition
    ): Map.Entry<String, MediaTypeObject>? {
        val content = ep.requestBody?.content ?: return null
        if (content.isEmpty()) return null
        return selectPreferredMediaTypeEntry(content)
    }

    private fun resolveResponseMediaTypeEntry(
        ep: EndpointDefinition
    ): Map.Entry<String, MediaTypeObject>? {
        val success = ep.responses.keys
            .filter { it.startsWith("2") }
            .minOrNull()
            ?: return null
        val response = ep.responses[success] ?: return null
        if (response.content.isEmpty()) return null
        return selectPreferredMediaTypeEntry(response.content)
    }

    private fun requiresSequentialJsonRequest(ep: EndpointDefinition): Boolean {
        val contentType = resolveRequestContentType(ep) ?: return false
        if (!isSequentialJsonMediaType(contentType)) return false
        val bodySignature = resolveRequestBodySignature(ep) ?: return false
        return extractListElementType(bodySignature.kotlinType) != null
    }

    private fun requiresMultipartPositionalEncoding(ep: EndpointDefinition): Boolean {
        val entry = resolveRequestMediaTypeEntry(ep) ?: return false
        if (!isMultipartMediaType(entry.key)) return false
        return hasPositionalEncoding(entry.value)
    }

    private fun hasPositionalEncoding(mediaType: MediaTypeObject?): Boolean {
        if (mediaType == null) return false
        return mediaType.prefixEncoding.isNotEmpty() || mediaType.itemEncoding != null
    }

    private fun requiresSequentialJsonResponse(ep: EndpointDefinition): Boolean {
        val responseEntry = resolveResponseMediaTypeEntry(ep) ?: return false
        if (!isSequentialJsonMediaType(responseEntry.key)) return false
        val returnType = resolveResponseType(ep)
        return extractListElementType(returnType) != null
    }

    private fun extractListElementType(type: String): String? {
        val trimmed = type.trim().removeSuffix("?")
        return when {
            trimmed.startsWith("List<") && trimmed.endsWith(">") ->
                trimmed.substringAfter("List<").substringBeforeLast(">")
            trimmed.startsWith("MutableList<") && trimmed.endsWith(">") ->
                trimmed.substringAfter("MutableList<").substringBeforeLast(">")
            else -> null
        }
    }

    private fun isSequentialJsonMediaType(contentType: String): Boolean {
        val normalized = contentType.substringBefore(";").trim().lowercase()
        if (normalized == "application/jsonl") return true
        if (normalized == "application/x-ndjson") return true
        if (normalized == "application/ndjson") return true
        if (normalized.endsWith("json-seq")) return true
        return false
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
        val entry = selectPreferredMediaTypeEntry(content)
        val preferred = entry.value
        preferred.schema?.let { return it }
        preferred.itemSchema?.let { return wrapItemSchemaAsArray(it) }
        return inferSchemaFromMediaType(entry.key)
    }

    private fun wrapItemSchemaAsArray(itemSchema: SchemaProperty): SchemaProperty {
        return SchemaProperty(types = setOf("array"), items = itemSchema)
    }

    private fun inferSchemaFromMediaType(mediaTypeKey: String): SchemaProperty? {
        if (!isConcreteMediaType(mediaTypeKey)) return null
        val normalized = normalizeMediaTypeKey(mediaTypeKey)
        return when {
            isJsonMediaTypeKey(normalized) -> SchemaProperty(booleanSchema = true)
            isTextMediaTypeKey(normalized) -> SchemaProperty(types = setOf("string"))
            else -> SchemaProperty(types = setOf("string"), contentMediaType = normalized)
        }
    }

    private fun isJsonMediaTypeKey(value: String): Boolean {
        val normalized = normalizeMediaTypeKey(value)
        return normalized == "application/json" || normalized.endsWith("+json")
    }

    private fun isTextMediaTypeKey(value: String): Boolean {
        val normalized = normalizeMediaTypeKey(value)
        return normalized.startsWith("text/") ||
            normalized == "application/xml" ||
            normalized == "text/xml" ||
            normalized.endsWith("+xml") ||
            normalized == "application/x-www-form-urlencoded" ||
            normalized == "multipart/form-data"
    }

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

    private fun renderEncodingContentTypeLiteral(mediaType: MediaTypeObject?): String? {
        if (mediaType == null || mediaType.encoding.isEmpty()) return null
        val entries = mediaType.encoding.mapNotNull { (name, encoding) ->
            encoding.contentType?.let { name to it }
        }
        if (entries.isEmpty()) return null
        val joined = entries.joinToString(", ") { (name, type) ->
            "\"${escapeKotlinString(name)}\" to \"${escapeKotlinString(type)}\""
        }
        return "mapOf($joined)"
    }

    private fun renderEncodingStyleLiteral(mediaType: MediaTypeObject?): String? {
        if (mediaType == null || mediaType.encoding.isEmpty()) return null
        val entries = mediaType.encoding.mapNotNull { (name, encoding) ->
            encoding.style?.let { name to parameterStyleValue(it) }
        }
        if (entries.isEmpty()) return null
        val joined = entries.joinToString(", ") { (name, style) ->
            "\"${escapeKotlinString(name)}\" to \"${escapeKotlinString(style)}\""
        }
        return "mapOf($joined)"
    }

    private fun renderEncodingExplodeLiteral(mediaType: MediaTypeObject?): String? {
        if (mediaType == null || mediaType.encoding.isEmpty()) return null
        val entries = mediaType.encoding.mapNotNull { (name, encoding) ->
            encoding.explode?.let { name to it }
        }
        if (entries.isEmpty()) return null
        val joined = entries.joinToString(", ") { (name, explode) ->
            "\"${escapeKotlinString(name)}\" to $explode"
        }
        return "mapOf($joined)"
    }

    private fun renderEncodingAllowReservedLiteral(mediaType: MediaTypeObject?): String? {
        if (mediaType == null || mediaType.encoding.isEmpty()) return null
        val entries = mediaType.encoding.mapNotNull { (name, encoding) ->
            encoding.allowReserved?.let { name to it }
        }
        if (entries.isEmpty()) return null
        val joined = entries.joinToString(", ") { (name, allowReserved) ->
            "\"${escapeKotlinString(name)}\" to $allowReserved"
        }
        return "mapOf($joined)"
    }

    private fun renderEncodingHeadersLiteral(mediaType: MediaTypeObject?): String? {
        if (mediaType == null || mediaType.encoding.isEmpty()) return null
        val entries = mediaType.encoding.mapNotNull { (name, encoding) ->
            val headerValues = extractEncodingHeaderValues(encoding)
            if (headerValues.isEmpty()) null else name to headerValues
        }
        if (entries.isEmpty()) return null
        val joined = entries.joinToString(", ") { (name, headers) ->
            val headerLiteral = headers.entries.joinToString(", ") { (headerName, value) ->
                "\"${escapeKotlinString(headerName)}\" to \"${escapeKotlinString(value)}\""
            }
            "\"${escapeKotlinString(name)}\" to mapOf($headerLiteral)"
        }
        return "mapOf($joined)"
    }

    private fun renderPrefixEncodingContentTypesLiteral(mediaType: MediaTypeObject?): String? {
        if (mediaType == null || mediaType.prefixEncoding.isEmpty()) return null
        val joined = mediaType.prefixEncoding.joinToString(", ") { encoding ->
            encoding.contentType?.let { "\"${escapeKotlinString(it)}\"" } ?: "null"
        }
        return "listOf($joined)"
    }

    private fun renderPrefixEncodingHeadersLiteral(mediaType: MediaTypeObject?): String? {
        if (mediaType == null || mediaType.prefixEncoding.isEmpty()) return null
        val entries = mediaType.prefixEncoding.map { encoding ->
            val headerValues = extractEncodingHeaderValues(encoding)
            if (headerValues.isEmpty()) {
                "emptyMap()"
            } else {
                val headerLiteral = headerValues.entries.joinToString(", ") { (name, value) ->
                    "\"${escapeKotlinString(name)}\" to \"${escapeKotlinString(value)}\""
                }
                "mapOf($headerLiteral)"
            }
        }
        val hasAnyHeaders = entries.any { it != "emptyMap()" }
        if (!hasAnyHeaders) return null
        return "listOf(${entries.joinToString(", ")})"
    }

    private fun renderItemEncodingContentTypeLiteral(mediaType: MediaTypeObject?): String? {
        val contentType = mediaType?.itemEncoding?.contentType ?: return null
        return "\"${escapeKotlinString(contentType)}\""
    }

    private fun renderItemEncodingHeadersLiteral(mediaType: MediaTypeObject?): String? {
        val encoding = mediaType?.itemEncoding ?: return null
        val headerValues = extractEncodingHeaderValues(encoding)
        if (headerValues.isEmpty()) return null
        val headerLiteral = headerValues.entries.joinToString(", ") { (name, value) ->
            "\"${escapeKotlinString(name)}\" to \"${escapeKotlinString(value)}\""
        }
        return "mapOf($headerLiteral)"
    }

    private fun extractEncodingHeaderValues(encoding: EncodingObject): Map<String, String> {
        if (encoding.headers.isEmpty()) return emptyMap()
        val values = LinkedHashMap<String, String>()
        encoding.headers.forEach { (headerName, header) ->
            if (headerName.equals("Content-Type", ignoreCase = true)) return@forEach
            val value = extractHeaderExampleValue(header) ?: extractHeaderDefaultValue(header) ?: return@forEach
            values[headerName] = value
        }
        return values
    }

    private fun extractHeaderExampleValue(header: Header): String? {
        header.example?.let { example ->
            extractExampleString(example)?.let { return it }
        }
        header.examples.values.forEach { example ->
            extractExampleString(example)?.let { return it }
        }
        return null
    }

    private fun extractExampleString(example: ExampleObject): String? {
        return when {
            example.serializedValue != null -> example.serializedValue
            example.dataValue is String -> example.dataValue
            example.value is String -> example.value
            else -> null
        }
    }

    private fun extractHeaderDefaultValue(header: Header): String? {
        val schema = header.schema
        val default = schema?.defaultValue
        if (default is String) return default
        val enumValue = schema?.enumValues?.firstOrNull { it is String } as? String
        return enumValue
    }

    private fun normalizeMediaTypeKey(value: String): String {
        return value.substringBefore(";").trim().lowercase()
    }

    private fun isFormUrlEncodedMediaType(value: String): Boolean {
        return normalizeMediaTypeKey(value) == "application/x-www-form-urlencoded"
    }

    private fun isMultipartFormDataMediaType(value: String): Boolean {
        return normalizeMediaTypeKey(value) == "multipart/form-data"
    }

    private fun isMultipartMediaType(value: String): Boolean {
        return normalizeMediaTypeKey(value).startsWith("multipart/")
    }

    private fun isConcreteMediaType(value: String): Boolean {
        val main = value.trim().substringBefore(";").trim()
        val parts = main.split("/")
        if (parts.size != 2) return false
        val type = parts[0]
        val subtype = parts[1]
        if (type == "*") return false
        if (subtype.contains("*")) return false
        return true
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

    private data class MediaTypeScore(
        val specificity: Int,
        val jsonPreference: Int,
        val length: Int,
        val key: String
    )

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
        if (param.content.isNotEmpty()) {
            if (param.style != null || param.explode != null || param.allowReserved != null) {
                throw IllegalArgumentException("OAS 3.2: query parameters with content must not set style/explode/allowReserved for ${param.name}")
            }
            val contentType = resolveParameterContentType(param)
            val serialized = if (contentType != null) {
                "serializeContentValue(${param.name}, \"$contentType\")"
            } else {
                "${param.name}.toString()"
            }
            val emptyValueLine = if (param.allowEmptyValue == true) {
                "parameter(\"${param.name}\", \"\")"
            } else {
                null
            }
            return QueryParamLines(listOf("parameter(\"${param.name}\", $serialized)"), emptyValueLine)
        }

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
        if (param.content.isNotEmpty()) {
            if (param.style != null || param.explode != null || param.allowReserved != null) {
                throw IllegalArgumentException("OAS 3.2: header parameters with content must not set style/explode/allowReserved for ${param.name}")
            }
            val contentType = resolveParameterContentType(param)
            val serialized = if (contentType != null) {
                "serializeContentValue(${param.name}, \"$contentType\")"
            } else {
                "${param.name}.toString()"
            }
            return listOf("header(\"${param.name}\", $serialized)")
        }

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
        if (param.content.isNotEmpty()) {
            if (param.style != null || param.explode != null || param.allowReserved != null) {
                throw IllegalArgumentException("OAS 3.2: cookie parameters with content must not set style/explode/allowReserved for ${param.name}")
            }
            val contentType = resolveParameterContentType(param)
            val serialized = if (contentType != null) {
                "serializeContentValue(${param.name}, \"$contentType\")"
            } else {
                "${param.name}.toString()"
            }
            return listOf("cookie(\"${param.name}\", $serialized)")
        }

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
        if (param.content.isNotEmpty()) {
            if (param.style != null || param.explode != null || param.allowReserved != null) {
                throw IllegalArgumentException(
                    "OAS 3.2: path parameters with content must not set style/explode/allowReserved for ${param.name}"
                )
            }
            val contentType = resolveParameterContentType(param)
            val serialized = if (contentType != null) {
                "serializeContentValue(${param.name}, \"$contentType\")"
            } else {
                "${param.name}.toString()"
            }
            return template("encodePathComponent($serialized, false)")
        }

        val style = param.style ?: ParameterStyle.SIMPLE
        val explodeDefault = style == ParameterStyle.FORM || style == ParameterStyle.COOKIE
        val explode = param.explode ?: explodeDefault
        val allowReserved = param.allowReserved == true
        val cleanType = paramType.replace(" ", "")
        val isList = isListType(cleanType)
        val isMap = isMapType(cleanType)

        if (!isList && !isMap) {
            val encoded = "encodePathComponent(${param.name}.toString(), $allowReserved)"
            return when (style) {
                ParameterStyle.MATRIX -> ";${param.name}=${template(encoded)}"
                ParameterStyle.LABEL -> ".${template(encoded)}"
                else -> template(encoded)
            }
        }

        return when (style) {
            ParameterStyle.MATRIX -> buildMatrixPathReplacement(param.name, isList, isMap, explode, allowReserved)
            ParameterStyle.LABEL -> buildLabelPathReplacement(param.name, isList, isMap, explode, allowReserved)
            else -> buildSimplePathReplacement(param.name, isList, isMap, explode, allowReserved)
        }
    }

    private fun buildMatrixPathReplacement(
        name: String,
        isList: Boolean,
        isMap: Boolean,
        explode: Boolean,
        allowReserved: Boolean
    ): String {
        return when {
            isList && explode -> ";$name=${template(buildPathArrayJoinExpr(name, ";$name=", allowReserved))}"
            isList -> ";$name=${template(buildPathArrayJoinExpr(name, ",", allowReserved))}"
            isMap && explode -> ";${template(buildPathObjectJoinExpr(name, ";", "=", allowReserved))}"
            isMap -> ";$name=${template(buildPathObjectJoinExpr(name, ",", ",", allowReserved))}"
            else -> ";$name=${template("encodePathComponent($name.toString(), $allowReserved)")}"
        }
    }

    private fun buildLabelPathReplacement(
        name: String,
        isList: Boolean,
        isMap: Boolean,
        explode: Boolean,
        allowReserved: Boolean
    ): String {
        return when {
            isList -> {
                val delimiter = if (explode) "." else ","
                ".${template(buildPathArrayJoinExpr(name, delimiter, allowReserved))}"
            }
            isMap -> {
                val entryDelimiter = if (explode) "." else ","
                val keyValueDelimiter = if (explode) "=" else ","
                ".${template(buildPathObjectJoinExpr(name, entryDelimiter, keyValueDelimiter, allowReserved))}"
            }
            else -> ".${template("encodePathComponent($name.toString(), $allowReserved)")}"
        }
    }

    private fun buildSimplePathReplacement(
        name: String,
        isList: Boolean,
        isMap: Boolean,
        explode: Boolean,
        allowReserved: Boolean
    ): String {
        return when {
            isList -> template(buildPathArrayJoinExpr(name, ",", allowReserved))
            isMap -> {
                val keyValueDelimiter = if (explode) "=" else ","
                template(buildPathObjectJoinExpr(name, ",", keyValueDelimiter, allowReserved))
            }
            else -> template("encodePathComponent($name.toString(), $allowReserved)")
        }
    }

    private fun template(expr: String): String = "\${$expr}"

    private fun buildArrayJoinExpr(name: String, delimiter: String): String {
        return "$name.joinToString(\"$delimiter\")"
    }

    private fun buildPathArrayJoinExpr(name: String, delimiter: String, allowReserved: Boolean): String {
        val encodeExpr = "encodePathComponent(it.toString(), $allowReserved)"
        return "$name.joinToString(\"$delimiter\") { $encodeExpr }"
    }

    private fun buildObjectJoinExpr(name: String, entryDelimiter: String, keyValueDelimiter: String): String {
        val pairExpr = "\"${'$'}{it.key}$keyValueDelimiter${'$'}{it.value}\""
        return "$name.entries.joinToString(\"$entryDelimiter\") { $pairExpr }"
    }

    private fun buildPathObjectJoinExpr(
        name: String,
        entryDelimiter: String,
        keyValueDelimiter: String,
        allowReserved: Boolean
    ): String {
        val keyExpr = "encodePathComponent(it.key.toString(), $allowReserved)"
        val valueExpr = "encodePathComponent(it.value.toString(), $allowReserved)"
        val pairExpr = "\"${'$'}{$keyExpr}$keyValueDelimiter${'$'}{$valueExpr}\""
        return "$name.entries.joinToString(\"$entryDelimiter\") { $pairExpr }"
    }

    private fun sanitizeIdentifier(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9]"), "").replaceFirstChar { it.lowercase() }
    }
}
