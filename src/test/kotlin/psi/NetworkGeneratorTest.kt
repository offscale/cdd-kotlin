package psi

import domain.Callback
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
import domain.ParameterLocation
import domain.ParameterStyle
import domain.PathItem
import domain.ReferenceObject
import domain.RequestBody
import domain.SchemaDefinition
import domain.SchemaProperty
import domain.SecurityScheme
import domain.Server
import domain.ServerVariable
import domain.Tag
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.AfterAll

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetworkGeneratorTest {

    private val generator = NetworkGenerator()

    @AfterAll
    fun tearDown() {
        PsiInfrastructure.dispose()
    }

    @Test
    fun `generateApi creates Interface, Class, and Exception`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/health",
                method = HttpMethod.GET,
                operationId = "checkHealth",
                responses = mapOf("200" to EndpointResponse("200", type = "String"))
            )
        )

        val file = generator.generateApi("com.test", "HealthApi", endpoints)
        val text = file.text

        assertTrue(text.contains("interface IHealthApi"), "Missing Interface")
        assertTrue(text.contains(": IHealthApi"), "Missing Implementation")
        assertTrue(text.contains("class ApiException(message: String) : Exception(message)"), "Missing Exception class")
        assertTrue(text.contains("import io.ktor.client.*"), "Missing imports")
        assertTrue(text.contains("Result<String>"), "Return type should be Result<String>")
    }

    @Test
    fun `generateApi wraps calls in try-catch and returns Result`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/safe",
                method = HttpMethod.GET,
                operationId = "safeCall",
                responses = mapOf("200" to EndpointResponse("200", type = null)) // Unit
            )
        )

        val text = generator.generateApi("com.test", "SafeApi", endpoints).text

        assertTrue(text.contains("try {"))
        assertTrue(text.contains("catch (e: Exception) {"))
        assertTrue(text.contains("Result.success"), "Missing success path")
        assertTrue(text.contains("Result.failure"), "Missing failure path")
    }

    @Test
    fun `generateApi handles POST body`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/submit",
                method = HttpMethod.POST,
                operationId = "submitData",
                responses = mapOf("200" to EndpointResponse("200", type = "String")),
                requestBodyType = "MyData"
            )
        )

        val text = generator.generateApi("com.test", "PostApi", endpoints).text

        assertTrue(text.contains("suspend fun submitData(body: MyData): Result<String>"))
        assertTrue(text.contains("setBody(body)"))
        assertTrue(text.contains("method = HttpMethod.Post"))
    }

    @Test
    fun `generateApi emits requestBody KDoc with custom schema keywords`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/upload",
                method = HttpMethod.POST,
                operationId = "uploadData",
                requestBody = RequestBody(
                    description = "payload",
                    required = true,
                    content = mapOf(
                        "application/json" to MediaTypeObject(
                            schema = SchemaProperty(
                                types = setOf("object"),
                                customKeywords = mapOf("customKeyword" to 123),
                                extensions = mapOf("x-extra" to "yes")
                            )
                        )
                    )
                ),
                responses = mapOf("200" to EndpointResponse("200", type = "String"))
            )
        )

        val text = generator.generateApi("com.test", "UploadApi", endpoints).text

        assertTrue(text.contains("@requestBody"), "Missing @requestBody KDoc tag")
        assertTrue(text.contains("\"customKeyword\":123"), "Missing custom keyword in KDoc")
        assertTrue(text.contains("\"x-extra\":\"yes\""), "Missing schema extension in KDoc")
    }

    @Test
    fun `generateApi encodes path parameters`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/pets/{petId}",
                method = HttpMethod.GET,
                operationId = "getPet",
                parameters = listOf(
                    EndpointParameter(
                        name = "petId",
                        type = "String",
                        location = ParameterLocation.PATH,
                        isRequired = true
                    )
                ),
                responses = mapOf("200" to EndpointResponse("200", type = "String"))
            )
        )

        val text = generator.generateApi("com.test", "PetApi", endpoints).text

        assertTrue(text.contains("encodePathComponent(petId.toString(), false)"))
        assertTrue(text.contains("/pets/"))
    }

    @Test
    fun `generateApi allows reserved path parameters`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/records/{recordId}",
                method = HttpMethod.GET,
                operationId = "getRecord",
                parameters = listOf(
                    EndpointParameter(
                        name = "recordId",
                        type = "String",
                        location = ParameterLocation.PATH,
                        isRequired = true,
                        allowReserved = true
                    )
                ),
                responses = mapOf("200" to EndpointResponse("200", type = "String"))
            )
        )

        val text = generator.generateApi("com.test", "RecordApi", endpoints).text

        assertTrue(text.contains("encodePathComponent(recordId.toString(), true)"))
    }

    @Test
    fun `generateApi serializes path parameters with content`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/filters/{filter}",
                method = HttpMethod.GET,
                operationId = "getFiltered",
                parameters = listOf(
                    EndpointParameter(
                        name = "filter",
                        type = "String",
                        location = ParameterLocation.PATH,
                        isRequired = true,
                        content = mapOf(
                            "application/json" to MediaTypeObject(
                                schema = SchemaProperty(types = setOf("string"))
                            )
                        )
                    )
                ),
                responses = mapOf("200" to EndpointResponse("200", type = "String"))
            )
        )

        val text = generator.generateApi("com.test", "FilterApi", endpoints).text

        assertTrue(text.contains("serializeContentValue(filter, \"application/json\")"))
        assertTrue(text.contains("encodePathComponent(serializeContentValue(filter, \"application/json\"), false)"))
    }

    @Test
    fun `generateApi rejects style with path content`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/filters/{filter}",
                method = HttpMethod.GET,
                operationId = "getFiltered",
                parameters = listOf(
                    EndpointParameter(
                        name = "filter",
                        type = "String",
                        location = ParameterLocation.PATH,
                        isRequired = true,
                        style = ParameterStyle.MATRIX,
                        content = mapOf(
                            "application/json" to MediaTypeObject(
                                schema = SchemaProperty(types = setOf("string"))
                            )
                        )
                    )
                ),
                responses = mapOf("200" to EndpointResponse("200", type = "String"))
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            generator.generateApi("com.test", "FilterApi", endpoints)
        }
    }

    @Test
    fun `generateApi preserves empty requestBody content when flagged`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/raw",
                method = HttpMethod.POST,
                operationId = "rawPayload",
                requestBody = RequestBody(
                    description = "raw payload",
                    contentPresent = true
                ),
                responses = mapOf("200" to EndpointResponse("200", type = "String"))
            )
        )

        val text = generator.generateApi("com.test", "RawApi", endpoints).text

        assertTrue(text.contains("@requestBody"), "Missing @requestBody KDoc tag")
        assertTrue(text.contains("\"content\":{}"), "Expected explicit empty content in KDoc")
    }

    @Test
    fun `generateApi emits paramRef and responseRef tags`() {
        val paramRef = ReferenceObject(
            ref = "#/components/parameters/Limit",
            summary = "Ref param",
            description = "Ref param desc"
        )
        val responseRef = ReferenceObject(
            ref = "#/components/responses/Ok",
            summary = "Ref summary",
            description = "Ref desc"
        )
        val endpoints = listOf(
            EndpointDefinition(
                path = "/items",
                method = HttpMethod.GET,
                operationId = "listItems",
                parameters = listOf(
                    EndpointParameter(
                        name = "limit",
                        type = "String",
                        location = ParameterLocation.QUERY,
                        reference = paramRef
                    )
                ),
                responses = mapOf(
                    "200" to EndpointResponse(
                        statusCode = "200",
                        description = "ok",
                        type = "String",
                        reference = responseRef
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "RefApi", endpoints).text

        assertTrue(
            text.contains("@paramRef limit {\"${'$'}ref\":\"#/components/parameters/Limit\",\"summary\":\"Ref param\",\"description\":\"Ref param desc\"}")
        )
        assertTrue(
            text.contains("@responseRef 200 {\"${'$'}ref\":\"#/components/responses/Ok\",\"summary\":\"Ref summary\",\"description\":\"Ref desc\"}")
        )
    }

    @Test
    fun `generateApi emits response summaries`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/summary",
                method = HttpMethod.GET,
                operationId = "getSummary",
                responses = mapOf(
                    "200" to EndpointResponse(
                        statusCode = "200",
                        summary = "Short summary",
                        description = "OK",
                        type = "String"
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "SummaryApi", endpoints).text

        assertTrue(text.contains("@responseSummary 200 Short summary"), "Missing @responseSummary KDoc tag")
    }

    @Test
    fun `generateApi emits component metadata tags`() {
        val callbackOperation = EndpointDefinition(
            path = "/callbacks",
            method = HttpMethod.POST,
            operationId = "onEvent",
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", description = "ok")
            )
        )
        val metadata = OpenApiMetadata(
            componentExamples = mapOf(
                "Example1" to ExampleObject(summary = "example", value = mapOf("id" to 1))
            ),
            componentLinks = mapOf(
                "GetUser" to Link(operationId = "getUser")
            ),
            componentCallbacks = mapOf(
                "OnEvent" to Callback.Inline(
                    expressions = mapOf("{\$request.body#/url}" to PathItem(post = callbackOperation))
                )
            ),
            componentParameters = mapOf(
                "Limit" to EndpointParameter(
                    name = "limit",
                    type = "Int",
                    location = ParameterLocation.QUERY,
                    schema = SchemaProperty(types = setOf("integer"))
                )
            ),
            componentResponses = mapOf(
                "NotFound" to EndpointResponse(statusCode = "NotFound", description = "missing")
            ),
            componentRequestBodies = mapOf(
                "UserBody" to RequestBody(
                    description = "user payload",
                    content = mapOf(
                        "application/json" to MediaTypeObject(schema = SchemaProperty(types = setOf("object")))
                    )
                )
            ),
            componentHeaders = mapOf(
                "X-Rate-Limit" to Header(
                    type = "Int",
                    schema = SchemaProperty(types = setOf("integer"))
                )
            ),
            componentPathItems = mapOf(
                "UserPath" to PathItem(
                    get = EndpointDefinition(
                        path = "/users/{id}",
                        method = HttpMethod.GET,
                        operationId = "getUser",
                        responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
                    )
                )
            ),
            componentMediaTypes = mapOf(
                "application/json" to MediaTypeObject(schema = SchemaProperty(types = setOf("string")))
            ),
            componentSchemas = mapOf(
                "Extra" to SchemaDefinition(name = "Extra", type = "object")
            ),
            componentsExtensions = mapOf(
                "x-component-flag" to true
            )
        )
        val endpoints = listOf(
            EndpointDefinition(
                path = "/users",
                method = HttpMethod.GET,
                operationId = "listUsers",
                responses = mapOf("200" to EndpointResponse("200", type = "String"))
            )
        )

        val text = generator.generateApi(
            packageName = "com.test",
            apiName = "MetadataApi",
            endpoints = endpoints,
            metadata = metadata
        ).text

        assertTrue(text.contains("@componentExamples"), "Missing @componentExamples KDoc tag")
        assertTrue(text.contains("\"Example1\""), "Missing component example name")
        assertTrue(text.contains("@componentLinks"), "Missing @componentLinks KDoc tag")
        assertTrue(text.contains("\"GetUser\""), "Missing component link name")
        assertTrue(text.contains("@componentCallbacks"), "Missing @componentCallbacks KDoc tag")
        assertTrue(text.contains("\"OnEvent\""), "Missing component callback name")
        assertTrue(text.contains("@componentParameters"), "Missing @componentParameters KDoc tag")
        assertTrue(text.contains("\"Limit\""), "Missing component parameter name")
        assertTrue(text.contains("@componentResponses"), "Missing @componentResponses KDoc tag")
        assertTrue(text.contains("\"NotFound\""), "Missing component response name")
        assertTrue(text.contains("@componentRequestBodies"), "Missing @componentRequestBodies KDoc tag")
        assertTrue(text.contains("\"UserBody\""), "Missing component request body name")
        assertTrue(text.contains("@componentHeaders"), "Missing @componentHeaders KDoc tag")
        assertTrue(text.contains("\"X-Rate-Limit\""), "Missing component header name")
        assertTrue(text.contains("@componentPathItems"), "Missing @componentPathItems KDoc tag")
        assertTrue(text.contains("\"UserPath\""), "Missing component path item name")
        assertTrue(text.contains("@componentMediaTypes"), "Missing @componentMediaTypes KDoc tag")
        assertTrue(text.contains("\"application/json\""), "Missing component media type name")
        assertTrue(text.contains("@componentSchemas"), "Missing @componentSchemas KDoc tag")
        assertTrue(text.contains("\"Extra\""), "Missing component schema name")
        assertTrue(text.contains("@componentsExtensions"), "Missing @componentsExtensions KDoc tag")
        assertTrue(text.contains("\"x-component-flag\""), "Missing component extension key")
    }

    @Test
    fun `generateApi emits callbacks KDoc`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/callbacks",
                method = HttpMethod.POST,
                operationId = "withCallbacks",
                responses = mapOf("200" to EndpointResponse("200", type = "String")),
                callbacks = mapOf(
                    "onEvent" to Callback.Reference(
                        ReferenceObject(ref = "#/components/callbacks/EventCb")
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "CallbackApi", endpoints).text

        assertTrue(text.contains("@callbacks"), "Missing @callbacks KDoc tag")
        assertTrue(text.contains("\"onEvent\""), "Missing callback name in KDoc")
        assertTrue(text.contains("#/components/callbacks/EventCb"), "Missing callback ref in KDoc")
    }

    @Test
    fun `generateApi emits webhooks KDoc`() {
        val webhooks = mapOf(
            "onPing" to PathItem(
                post = EndpointDefinition(
                    path = "/",
                    method = HttpMethod.POST,
                    operationId = "onPing",
                    responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
                )
            )
        )

        val text = generator.generateApi(
            packageName = "com.webhooks",
            apiName = "WebhookApi",
            endpoints = emptyList(),
            webhooks = webhooks
        ).text

        assertTrue(text.contains("@webhooks"), "Missing @webhooks KDoc tag")
        assertTrue(text.contains("\"onPing\""), "Missing webhook name in KDoc")
    }

    @Test
    fun `generateApi emits root metadata KDoc`() {
        val metadata = OpenApiMetadata(
            openapi = "3.2.0",
            jsonSchemaDialect = "https://spec.openapis.org/oas/3.1/dialect/base",
            self = "https://example.com/openapi",
            info = Info(
                title = "Meta API",
                version = "1.2.3",
                summary = "Summary"
            ),
            servers = listOf(Server("https://api.example.com", "Prod")),
            security = listOf(mapOf("api_key" to emptyList())),
            tags = listOf(Tag(name = "alpha", summary = "Alpha")),
            externalDocs = ExternalDocumentation("Docs", "https://docs.example.com"),
            extensions = mapOf("x-root" to true),
            pathsExtensions = mapOf("x-paths" to "paths-ext"),
            pathsExplicitEmpty = true,
            pathItems = mapOf(
                "/pets" to PathItem(
                    summary = "Pets",
                    description = "All pets"
                )
            ),
            webhooksExtensions = mapOf("x-webhooks" to mapOf("flag" to true)),
            webhooksExplicitEmpty = true,
            securitySchemes = mapOf(
                "api_key" to SecurityScheme(
                    type = "apiKey",
                    name = "X-API-KEY",
                    `in` = "header"
                )
            )
        )

        val text = generator.generateApi(
            packageName = "com.meta",
            apiName = "MetaApi",
            endpoints = emptyList(),
            metadata = metadata
        ).text

        assertTrue(text.contains("@openapi"), "Missing @openapi KDoc tag")
        assertTrue(text.contains("\"jsonSchemaDialect\""), "Missing jsonSchemaDialect in @openapi")
        assertTrue(
            text.contains("\"${'$'}self\":\"https://example.com/openapi\""),
            "Missing \$self in @openapi"
        )
        assertTrue(text.contains("@info"), "Missing @info KDoc tag")
        assertTrue(text.contains("\"Meta API\""), "Missing info title in @info")
        assertTrue(text.contains("@servers"), "Missing @servers KDoc tag")
        assertTrue(text.contains("https://api.example.com"), "Missing server url in @servers")
        assertTrue(text.contains("@security"), "Missing @security KDoc tag")
        assertTrue(text.contains("\"api_key\""), "Missing security scheme name in @security")
        assertTrue(text.contains("@tags"), "Missing @tags KDoc tag")
        assertTrue(text.contains("\"alpha\""), "Missing tag name in @tags")
        assertTrue(text.contains("@externalDocs"), "Missing @externalDocs KDoc tag")
        assertTrue(text.contains("https://docs.example.com"), "Missing externalDocs url in @externalDocs")
        assertTrue(text.contains("@extensions"), "Missing @extensions KDoc tag")
        assertTrue(text.contains("\"x-root\":true"), "Missing root extension in @extensions")
        assertTrue(text.contains("@pathsExtensions"), "Missing @pathsExtensions KDoc tag")
        assertTrue(text.contains("\"x-paths\":\"paths-ext\""), "Missing paths extension in @pathsExtensions")
        assertTrue(text.contains("@pathsEmpty"), "Missing @pathsEmpty KDoc tag")
        assertTrue(text.contains("@pathItems"), "Missing @pathItems KDoc tag")
        assertTrue(text.contains("\"/pets\""), "Missing path key in @pathItems")
        assertTrue(text.contains("@webhooksExtensions"), "Missing @webhooksExtensions KDoc tag")
        assertTrue(text.contains("\"x-webhooks\""), "Missing webhooks extension in @webhooksExtensions")
        assertTrue(text.contains("@webhooksEmpty"), "Missing @webhooksEmpty KDoc tag")
        assertTrue(text.contains("@securitySchemes"), "Missing @securitySchemes KDoc tag")
        assertTrue(text.contains("\"apiKey\""), "Missing security scheme type in @securitySchemes")
    }

    @Test
    fun `generateApi emits operation extensions`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/extensions",
                method = HttpMethod.GET,
                operationId = "getExtensions",
                responses = mapOf("200" to EndpointResponse("200", type = "String")),
                extensions = mapOf(
                    "x-rate-limit" to 10,
                    "x-flag" to true
                )
            )
        )

        val text = generator.generateApi("com.test", "ExtensionApi", endpoints).text

        assertTrue(text.contains("@extensions"), "Missing @extensions KDoc tag")
        assertTrue(text.contains("\"x-rate-limit\":10"), "Missing x-rate-limit extension in KDoc")
        assertTrue(text.contains("\"x-flag\":true"), "Missing x-flag extension in KDoc")
    }

    @Test
    fun `generateApi emits parameter and response extensions`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/items",
                method = HttpMethod.GET,
                operationId = "listItems",
                parameters = listOf(
                    EndpointParameter(
                        name = "limit",
                        type = "Int",
                        location = ParameterLocation.QUERY,
                        extensions = mapOf("x-param" to true)
                    )
                ),
                responses = mapOf(
                    "200" to EndpointResponse(
                        statusCode = "200",
                        description = "ok",
                        type = "String",
                        extensions = mapOf("x-response" to "yes")
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "ExtensionsApi", endpoints).text

        assertTrue(text.contains("@paramExtensions limit"), "Missing @paramExtensions for parameter")
        assertTrue(text.contains("\"x-param\":true"), "Missing parameter extension payload")
        assertTrue(text.contains("@responseExtensions 200"), "Missing @responseExtensions for response")
        assertTrue(text.contains("\"x-response\":\"yes\""), "Missing response extension payload")
    }

    @Test
    fun `generateApi emits externalDocs tag for operation extensions`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/docs",
                method = HttpMethod.GET,
                operationId = "getDocs",
                externalDocs = ExternalDocumentation(
                    description = "Docs",
                    url = "https://docs.example.com",
                    extensions = mapOf("x-docs" to 1)
                ),
                responses = mapOf(
                    "200" to EndpointResponse(statusCode = "200", description = "ok", type = "String")
                )
            )
        )

        val text = generator.generateApi("com.test", "DocsApi", endpoints).text

        assertTrue(text.contains("@externalDocs"), "Missing @externalDocs tag")
        assertTrue(text.contains("https://docs.example.com"), "Missing externalDocs url")
        assertTrue(text.contains("\"x-docs\":1"), "Missing externalDocs extensions")
    }

    @Test
    fun `generateApi interpolates Path parameters (Default Style)`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.GET,
                operationId = "getUser",
                responses = mapOf("200" to EndpointResponse("200", type = "String")),
                parameters = listOf(
                    EndpointParameter("id", "String", ParameterLocation.PATH)
                )
            )
        )

        val text = generator.generateApi("com.test", "UserApi", endpoints).text

        assertTrue(text.contains("client.request(\"\$baseUrl/users/${'$'}{encodePathComponent(id.toString(), false)}\")"))
    }

    @Test
    fun `generateApi uses operation level server override`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.GET,
                operationId = "getUserOverride",
                servers = listOf(Server(url = "https://override.example.com")),
                responses = mapOf("200" to EndpointResponse("200", type = "String")),
                parameters = listOf(
                    EndpointParameter("id", "String", ParameterLocation.PATH)
                )
            )
        )

        val text = generator.generateApi("com.test", "UserApi", endpoints).text

        assertTrue(
            text.contains(
                "client.request(\"https://override.example.com/users/${'$'}{encodePathComponent(id.toString(), false)}\")"
            )
        )
        assertTrue(text.contains("@servers"), "Missing @servers KDoc tag")
    }

    @Test
    fun `generateApi resolves operation server variables with defaults`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/status",
                method = HttpMethod.GET,
                operationId = "getStatus",
                servers = listOf(
                    Server(
                        url = "https://{region}.example.com/{version}",
                        variables = mapOf(
                            "region" to ServerVariable(default = "us"),
                            "version" to ServerVariable(default = "v1")
                        )
                    )
                ),
                responses = mapOf("200" to EndpointResponse("200", type = "String"))
            )
        )

        val text = generator.generateApi("com.test", "ServerVarApi", endpoints).text

        assertTrue(
            text.contains("client.request(\"https://us.example.com/v1/status\")"),
            "Server variables should resolve to defaults in operation-level override URLs"
        )
        assertTrue(text.contains("@servers"), "Missing @servers KDoc tag")
    }

    @Test
    fun `generateApi emits server variable helpers`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/status",
                method = HttpMethod.GET,
                operationId = "status",
                responses = mapOf("200" to EndpointResponse("200", type = "String"))
            )
        )

        val servers = listOf(
            Server(
                url = "https://{version}.example.com",
                variables = mapOf(
                    "version" to ServerVariable(default = "v1")
                )
            )
        )

        val text = generator.generateApi("com.test", "VarsApi", endpoints, servers = servers).text

        assertTrue(text.contains("data class ServerSpec"), "Missing ServerSpec data class")
        assertTrue(text.contains("data class ServerVariables"), "Missing ServerVariables data class")
        assertTrue(text.contains("val version: String = \"v1\""), "Missing default variable value")
        assertTrue(text.contains("fun resolveServerUrl"), "Missing resolveServerUrl helper")
        assertTrue(text.contains("fun defaultBaseUrl"), "Missing defaultBaseUrl helper")
        assertTrue(text.contains("serverIndex: Int = 0"), "Missing serverIndex parameter")
        assertTrue(text.contains("serverName: String? = null"), "Missing serverName parameter")
        assertTrue(
            text.contains("private val baseUrl: String = defaultBaseUrl(serverIndex, serverName, serverVariables)"),
            "Base URL should use defaultBaseUrl with server selection"
        )
    }

    @Test
    fun `generateApi emits enum typed server variables when enum is defined`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/status",
                method = HttpMethod.GET,
                operationId = "status",
                responses = mapOf("200" to EndpointResponse("200", type = "String"))
            )
        )

        val servers = listOf(
            Server(
                url = "https://{env}.example.com",
                variables = mapOf(
                    "env" to ServerVariable(
                        default = "prod",
                        enum = listOf("prod", "staging")
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "EnvApi", endpoints, servers = servers).text

        assertTrue(text.contains("enum class Env"), "Missing enum class for server variable")
        assertTrue(text.contains("val env: Env = Env.PROD"), "Missing enum-typed server variable")
        assertTrue(text.contains("\"env\" to env.value"), "ServerVariables.toMap should use enum values")
    }

    @Test
    fun `generateApi handles Query, Header and Cookie parameters`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/search",
                method = HttpMethod.GET,
                operationId = "search",
                responses = mapOf("200" to EndpointResponse("200", type = "List<String>")),
                parameters = listOf(
                    EndpointParameter("q", "String", ParameterLocation.QUERY),
                    EndpointParameter("auth", "String", ParameterLocation.HEADER),
                    EndpointParameter("session", "String", ParameterLocation.COOKIE)
                )
            )
        )

        val text = generator.generateApi("com.test", "SearchApi", endpoints).text

        assertTrue(text.contains("parameter(\"q\", q)"))
        assertTrue(text.contains("header(\"auth\", auth)"))
        assertTrue(text.contains("cookie(\"session\", session)"))
    }

    @Test
    fun `generateApi serializes content parameters for query header and cookie`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/content",
                method = HttpMethod.GET,
                operationId = "contentParams",
                parameters = listOf(
                    EndpointParameter(
                        name = "filters",
                        type = "Filters",
                        location = ParameterLocation.QUERY,
                        content = mapOf(
                            "application/json" to MediaTypeObject(
                                schema = SchemaProperty(types = setOf("object"))
                            )
                        )
                    ),
                    EndpointParameter(
                        name = "meta",
                        type = "Meta",
                        location = ParameterLocation.HEADER,
                        content = mapOf(
                            "application/json" to MediaTypeObject(
                                schema = SchemaProperty(types = setOf("object"))
                            )
                        )
                    ),
                    EndpointParameter(
                        name = "session",
                        type = "String",
                        location = ParameterLocation.COOKIE,
                        content = mapOf(
                            "text/plain" to MediaTypeObject(
                                schema = SchemaProperty(types = setOf("string"))
                            )
                        )
                    )
                ),
                responses = mapOf("200" to EndpointResponse("200", type = "Unit"))
            )
        )

        val text = generator.generateApi("com.test", "ContentApi", endpoints).text

        assertTrue(text.contains("parameter(\"filters\", serializeContentValue(filters, \"application/json\"))"))
        assertTrue(text.contains("header(\"meta\", serializeContentValue(meta, \"application/json\"))"))
        assertTrue(text.contains("cookie(\"session\", serializeContentValue(session, \"text/plain\"))"))
    }

    @Test
    fun `generateApi rejects content parameters with style or explode`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/content",
                method = HttpMethod.GET,
                operationId = "invalidContentParams",
                parameters = listOf(
                    EndpointParameter(
                        name = "filters",
                        type = "Filters",
                        location = ParameterLocation.QUERY,
                        style = ParameterStyle.FORM,
                        content = mapOf(
                            "application/json" to MediaTypeObject(
                                schema = SchemaProperty(types = setOf("object"))
                            )
                        )
                    )
                ),
                responses = mapOf("200" to EndpointResponse("200", type = "Unit"))
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            generator.generateApi("com.test", "ContentApi", endpoints)
        }
    }

    @Test
    fun `generateApi handles querystring parameter`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/search",
                method = HttpMethod.GET,
                operationId = "searchRaw",
                responses = mapOf("200" to EndpointResponse("200", type = "List<String>")),
                parameters = listOf(
                    EndpointParameter("rawQuery", "String", ParameterLocation.QUERYSTRING)
                )
            )
        )

        val text = generator.generateApi("com.test", "QueryStringApi", endpoints).text

        assertTrue(text.contains("url.encodedQuery = rawQuery"))
    }

    @Test
    fun `generateApi uses raw QUERY method token`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/query",
                method = HttpMethod.QUERY,
                operationId = "queryOperation",
                responses = mapOf("200" to EndpointResponse("200", type = "Unit"))
            )
        )

        val text = generator.generateApi("com.test", "QueryApi", endpoints).text

        assertTrue(text.contains("method = HttpMethod(\"QUERY\")"))
    }

    @Test
    fun `generateApi serializes query array styles`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/items",
                method = HttpMethod.GET,
                operationId = "listItems",
                responses = mapOf("200" to EndpointResponse("200", type = "Unit")),
                parameters = listOf(
                    EndpointParameter(
                        name = "tags",
                        type = "List<String>",
                        location = ParameterLocation.QUERY,
                        style = ParameterStyle.SPACE_DELIMITED,
                        explode = false
                    ),
                    EndpointParameter(
                        name = "ids",
                        type = "List<String>",
                        location = ParameterLocation.QUERY,
                        style = ParameterStyle.PIPE_DELIMITED,
                        explode = false
                    ),
                    EndpointParameter(
                        name = "labels",
                        type = "List<String>",
                        location = ParameterLocation.QUERY,
                        style = ParameterStyle.FORM,
                        explode = true
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "ListApi", endpoints).text

        assertTrue(text.contains("parameter(\"tags\", tags.joinToString(\" \"))"))
        assertTrue(text.contains("parameter(\"ids\", ids.joinToString(\"|\"))"))
        assertTrue(text.contains("labels.forEach { value -> parameter(\"labels\", value) }"))
    }

    @Test
    fun `generateApi serializes query object styles`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/filters",
                method = HttpMethod.GET,
                operationId = "filterItems",
                responses = mapOf("200" to EndpointResponse("200", type = "Unit")),
                parameters = listOf(
                    EndpointParameter(
                        name = "filters",
                        type = "Map<String, String>",
                        location = ParameterLocation.QUERY,
                        style = ParameterStyle.DEEP_OBJECT,
                        explode = true
                    ),
                    EndpointParameter(
                        name = "color",
                        type = "Map<String, String>",
                        location = ParameterLocation.QUERY,
                        style = ParameterStyle.FORM,
                        explode = false
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "FilterApi", endpoints).text

        assertTrue(text.contains("filters.forEach { (key, value) -> parameter(\"filters[\$key]\", value) }"))
        assertTrue(text.contains("parameter(\"color\", color.entries.joinToString(\",\") { \"\${it.key},\${it.value}\" })"))
    }

    @Test
    fun `generateApi emits parameter metadata tags and deprecated tag`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/filters",
                method = HttpMethod.GET,
                operationId = "filter",
                deprecated = true,
                responses = mapOf("200" to EndpointResponse("200", type = "Unit")),
                parameters = listOf(
                    EndpointParameter(
                        name = "ids",
                        type = "String",
                        location = ParameterLocation.QUERY,
                        style = ParameterStyle.FORM,
                        explode = false,
                        allowReserved = true,
                        allowEmptyValue = true
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "FilterApi", endpoints).text

        assertTrue(text.contains("@deprecated"))
        assertTrue(text.contains("@paramStyle ids form"))
        assertTrue(text.contains("@paramExplode ids false"))
        assertTrue(text.contains("@paramAllowReserved ids true"))
        assertTrue(text.contains("@paramAllowEmptyValue ids true"))
    }

    @Test
    fun `generateApi uses encoded parameters when allowReserved is true`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/search",
                method = HttpMethod.GET,
                operationId = "searchEncoded",
                responses = mapOf("200" to EndpointResponse("200", type = "Unit")),
                parameters = listOf(
                    EndpointParameter(
                        name = "q",
                        type = "String",
                        location = ParameterLocation.QUERY,
                        allowReserved = true
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "EncodedQueryApi", endpoints).text

        assertTrue(text.contains("url.encodedParameters.append(\"q\", encodeAllowReserved(q.toString()))"))
        assertTrue(text.contains("private fun encodeAllowReserved"), "Missing allowReserved encoder helper")
    }

    @Test
    fun `generateApi emits empty value when allowEmptyValue is true`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/search",
                method = HttpMethod.GET,
                operationId = "searchEmpty",
                responses = mapOf("200" to EndpointResponse("200", type = "Unit")),
                parameters = listOf(
                    EndpointParameter(
                        name = "q",
                        type = "String",
                        location = ParameterLocation.QUERY,
                        isRequired = false,
                        allowEmptyValue = true
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "EmptyValueApi", endpoints).text

        assertTrue(text.contains("if (q != null)"))
        assertTrue(text.contains("parameter(\"q\", q)"))
        assertTrue(text.contains("else {\n                parameter(\"q\", \"\")"), "Missing allowEmptyValue fallback")
    }

    @Test
    fun `generateApi emits response headers links and content tags`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/users",
                method = HttpMethod.GET,
                operationId = "listUsers",
                responses = mapOf(
                    "200" to EndpointResponse(
                        statusCode = "200",
                        description = "ok",
                        type = "String",
                        headers = mapOf(
                            "X-Trace" to Header(
                                type = "String",
                                schema = SchemaProperty(types = setOf("string")),
                                description = "Trace id"
                            )
                        ),
                        links = mapOf(
                            "next" to Link(operationId = "listUsers")
                        ),
                        content = mapOf(
                            "application/json" to MediaTypeObject(
                                schema = SchemaProperty(types = setOf("string"))
                            )
                        )
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "ResponseMetaApi", endpoints).text

        assertTrue(text.contains("@responseHeaders 200"))
        assertTrue(text.contains("\"X-Trace\""))
        assertTrue(text.contains("@responseLinks 200"))
        assertTrue(text.contains("\"operationId\":\"listUsers\""))
        assertTrue(text.contains("@responseContent 200"))
        assertTrue(text.contains("\"application/json\""))
    }

    @Test
    fun `generateApi omits Content-Type from response headers`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/headers",
                method = HttpMethod.GET,
                operationId = "headers",
                responses = mapOf(
                    "200" to EndpointResponse(
                        statusCode = "200",
                        description = "ok",
                        type = "String",
                        headers = mapOf(
                            "Content-Type" to Header(
                                type = "String",
                                schema = SchemaProperty(types = setOf("string"))
                            ),
                            "X-Trace" to Header(
                                type = "String",
                                schema = SchemaProperty(types = setOf("string"))
                            )
                        )
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "ResponseHeaderApi", endpoints).text

        assertTrue(text.contains("\"X-Trace\""))
        assertTrue(!text.contains("\"Content-Type\""))
    }

    @Test
    fun `generateApi rejects query and querystring together`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/search",
                method = HttpMethod.GET,
                operationId = "badQueryMix",
                responses = mapOf("200" to EndpointResponse("200", type = "List<String>")),
                parameters = listOf(
                    EndpointParameter("q", "String", ParameterLocation.QUERY),
                    EndpointParameter("rawQuery", "String", ParameterLocation.QUERYSTRING)
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            generator.generateApi("com.test", "BadQueryApi", endpoints)
        }
    }

    @Test
    fun `generateApi rejects non-string querystring type`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/search",
                method = HttpMethod.GET,
                operationId = "badQueryType",
                responses = mapOf("200" to EndpointResponse("200", type = "List<String>")),
                parameters = listOf(
                    EndpointParameter("rawQuery", "Int", ParameterLocation.QUERYSTRING)
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            generator.generateApi("com.test", "BadQueryTypeApi", endpoints)
        }
    }

    @Test
    fun `generateApi handles style and explode for parameters`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/users/{id}", // Matrix style: /users;id=$id
                method = HttpMethod.GET,
                operationId = "getUser",
                responses = mapOf("200" to EndpointResponse("200", type = "String")),
                parameters = listOf(
                    EndpointParameter("id", "String", ParameterLocation.PATH, style = ParameterStyle.MATRIX),
                    EndpointParameter("tags", "List<String>", ParameterLocation.QUERY, explode = false), // Comma sep
                    EndpointParameter("filter", "List<String>", ParameterLocation.QUERY, style = ParameterStyle.PIPE_DELIMITED, explode = false)
                )
            )
        )

        val text = generator.generateApi("com.style", "StyleApi", endpoints).text

        // Matrix Check
        assertTrue(
            text.contains("client.request(\"\$baseUrl/users;id=${'$'}{encodePathComponent(id.toString(), false)}\")"),
            "Matrix path generation failed"
        )

        // Query param lists
        assertTrue(text.contains("parameter(\"tags\", tags.joinToString(\",\"))"), "Tags joinToString comma failed")
        assertTrue(text.contains("parameter(\"filter\", filter.joinToString(\"|\"))"), "Filter joinToString pipe failed")
    }

    @Test
    fun `generateApi handles Label style path params`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/files/{ext}",
                method = HttpMethod.GET,
                operationId = "getFile",
                responses = mapOf("200" to EndpointResponse("200", type = "Unit")),
                parameters = listOf(
                    EndpointParameter("ext", "String", ParameterLocation.PATH, style = ParameterStyle.LABEL)
                )
            )
        )
        val text = generator.generateApi("com.lbl", "LabelApi", endpoints).text
        // Expect /files.$ext
        assertTrue(text.contains("/files.${'$'}{encodePathComponent(ext.toString(), false)}"))
    }

    @Test
    fun `generateApi serializes path arrays and objects`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/items/{ids}",
                method = HttpMethod.GET,
                operationId = "getItems",
                parameters = listOf(
                    EndpointParameter(
                        "ids",
                        "List<String>",
                        ParameterLocation.PATH,
                        style = ParameterStyle.MATRIX,
                        explode = true
                    )
                )
            ),
            EndpointDefinition(
                path = "/items/{filter}",
                method = HttpMethod.GET,
                operationId = "filterItemsPath",
                parameters = listOf(
                    EndpointParameter(
                        "filter",
                        "Map<String, String>",
                        ParameterLocation.PATH,
                        style = ParameterStyle.SIMPLE,
                        explode = true
                    )
                )
            )
        )

        val text = generator.generateApi("com.path", "PathApi", endpoints).text

        val expectedMatrix =
            "client.request(\"\$baseUrl/items;ids=${'$'}{ids.joinToString(\";ids=\") { encodePathComponent(it.toString(), false) }}\")"
        val expectedObject =
            "client.request(\"\$baseUrl/items/${'$'}{filter.entries.joinToString(\",\") { \"${'$'}{encodePathComponent(it.key.toString(), false)}=${'$'}{encodePathComponent(it.value.toString(), false)}\" }}\")"

        assertTrue(text.contains(expectedMatrix))
        assertTrue(text.contains(expectedObject))
    }

    @Test
    fun `generateApi serializes header and cookie complex params`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/cookies",
                method = HttpMethod.GET,
                operationId = "cookies",
                parameters = listOf(
                    EndpointParameter("ids", "List<String>", ParameterLocation.HEADER),
                    EndpointParameter(
                        "filter",
                        "Map<String, String>",
                        ParameterLocation.HEADER,
                        explode = true
                    ),
                    EndpointParameter(
                        "session",
                        "List<String>",
                        ParameterLocation.COOKIE,
                        style = ParameterStyle.COOKIE,
                        explode = true
                    ),
                    EndpointParameter(
                        "prefs",
                        "Map<String, String>",
                        ParameterLocation.COOKIE,
                        style = ParameterStyle.COOKIE,
                        explode = true
                    ),
                    EndpointParameter(
                        "rawCookie",
                        "List<String>",
                        ParameterLocation.COOKIE,
                        style = ParameterStyle.FORM,
                        explode = true
                    )
                )
            )
        )

        val text = generator.generateApi("com.cookies", "CookieApi", endpoints).text

        assertTrue(text.contains("header(\"ids\", ids.joinToString(\",\"))"))
        assertTrue(
            text.contains(
                "header(\"filter\", filter.entries.joinToString(\",\") { \"${'$'}{it.key}=${'$'}{it.value}\" })"
            )
        )
        assertTrue(text.contains("session.forEach { value -> cookie(\"session\", value) }"))
        assertTrue(text.contains("prefs.forEach { (key, value) -> cookie(key, value) }"))
        assertTrue(text.contains("header(\"Cookie\", rawCookie.joinToString(\"&\") { \"rawCookie=${'$'}it\" })"))
    }

    @Test
    fun `generateApi handles servers and base URL`() {
        val servers = listOf(
            Server("https://api.v1.com", "Prod"),
            Server("https://staging.v1.com", "Staging")
        )
        val endpoints = listOf(
            EndpointDefinition(
                path = "/info",
                method = HttpMethod.GET,
                operationId = "getInfo"
            )
        )
        val text = generator.generateApi("com.srv", "ServerApi", endpoints, servers).text
        assertTrue(text.contains("data class ServerSpec"), "Missing ServerSpec data class")
        assertTrue(text.contains("serverIndex: Int = 0"), "Missing serverIndex parameter")
        assertTrue(text.contains("serverName: String? = null"), "Missing serverName parameter")
        assertTrue(
            text.contains("private val baseUrl: String = defaultBaseUrl(serverIndex, serverName)"),
            "Base URL should use defaultBaseUrl when multiple servers exist"
        )
        assertTrue(text.contains("val SERVERS = listOf("), "Missing SERVERS list")
        assertTrue(text.contains("ServerSpec(url = \"https://api.v1.com\""), "Missing prod server entry")
        assertTrue(text.contains("ServerSpec(url = \"https://staging.v1.com\""), "Missing staging server entry")
    }

    @Test
    fun `generateApi defaults baseUrl to root when servers missing`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/ping",
                method = HttpMethod.GET,
                operationId = "ping"
            )
        )

        val text = generator.generateApi("com.defaultsrv", "DefaultServerApi", endpoints).text

        assertTrue(text.contains("private val baseUrl: String = \"/\""), "Base URL should default to '/' when servers are absent")
        assertTrue(text.contains("client.request(\"${'$'}baseUrl/ping\")"), "Request should use default baseUrl")
    }

    @Test
    fun `generateApi includes ExternalDocumentation and Tags and Responses as KDoc`() {
        val endpoint = EndpointDefinition(
            path = "/docs",
            method = HttpMethod.GET,
            operationId = "getDocs",
            summary = "Fetch docs",
            description = "Returns full documentation payload.",
            externalDocs = ExternalDocumentation("Reference", "http://docs"),
            tags = listOf("system", "public"),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", description = "Success", type = "String"),
                "404" to EndpointResponse(statusCode = "404", description = "Not Found", type = null)
            )
        )

        val text = generator.generateApi("com.test", "DocApi", listOf(endpoint)).text

        assertTrue(text.contains("Fetch docs"))
        assertTrue(text.contains("Returns full documentation payload."))
        assertTrue(text.contains("@see http://docs Reference"))
        assertTrue(text.contains("@tag system, public"))
        assertTrue(text.contains("@response 200 String Success"))
        assertTrue(text.contains("@response 404 Unit Not Found"))
    }

    @Test
    fun `generateApi skips ContentType header for wildcard media types`() {
        val endpoint = EndpointDefinition(
            path = "/upload",
            method = HttpMethod.POST,
            operationId = "upload",
            requestBody = RequestBody(
                required = true,
                content = mapOf(
                    "text/*" to MediaTypeObject(schema = SchemaProperty("string"))
                )
            )
        )

        val text = generator.generateApi("com.wild", "WildcardApi", listOf(endpoint)).text

        assertTrue(text.contains("setBody(body)"), "Body should still be set for wildcard content types")
        assertTrue(
            !text.contains("ContentType.parse(\"text/*\")"),
            "Wildcard media types should not be passed to ContentType.parse"
        )
    }

    @Test
    fun `generateApi includes parameter examples in KDoc`() {
        val endpoint = EndpointDefinition(
            path = "/search",
            method = HttpMethod.GET,
            operationId = "search",
            parameters = listOf(
                EndpointParameter(
                    name = "q",
                    type = "String",
                    location = ParameterLocation.QUERY,
                    description = "query",
                    example = ExampleObject(serializedValue = "cats"),
                    examples = mapOf("dogs" to ExampleObject(serializedValue = "dogs"))
                )
            )
        )

        val text = generator.generateApi("com.test", "ExampleApi", listOf(endpoint)).text
        assertTrue(text.contains("@paramExample q cats"))
        assertTrue(text.contains("@paramExample q dogs: dogs"))
    }

    @Test
    fun `generateApi renders structured parameter examples as JSON`() {
        val endpoint = EndpointDefinition(
            path = "/items",
            method = HttpMethod.GET,
            operationId = "listItems",
            parameters = listOf(
                EndpointParameter(
                    name = "filter",
                    type = "String",
                    location = ParameterLocation.QUERY,
                    example = ExampleObject(
                        summary = "Filter",
                        dataValue = mapOf("status" to "active")
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "ExampleApi", listOf(endpoint)).text
        assertTrue(text.contains("@paramExample filter {\"summary\":\"Filter\",\"dataValue\":{\"status\":\"active\"}}"))
    }

    @Test
    fun `generateApi includes external parameter examples in KDoc`() {
        val endpoint = EndpointDefinition(
            path = "/avatars",
            method = HttpMethod.GET,
            operationId = "getAvatar",
            parameters = listOf(
                EndpointParameter(
                    name = "avatar",
                    type = "String",
                    location = ParameterLocation.QUERY,
                    example = ExampleObject(externalValue = "https://example.com/avatar.png")
                )
            )
        )

        val text = generator.generateApi("com.test", "ExternalExampleApi", listOf(endpoint)).text
        assertTrue(text.contains("@paramExample avatar external:https://example.com/avatar.png"))
    }

    @Test
    fun `generateApi includes parameter schema and content tags`() {
        val schemaParam = EndpointParameter(
            name = "limit",
            type = "Int",
            location = ParameterLocation.QUERY,
            schema = SchemaProperty(
                types = setOf("integer"),
                minimum = 1.0
            )
        )
        val contentParam = EndpointParameter(
            name = "filter",
            type = "String",
            location = ParameterLocation.QUERY,
            content = mapOf(
                "application/json" to MediaTypeObject(
                    schema = SchemaProperty(types = setOf("string"))
                )
            )
        )

        val endpoint = EndpointDefinition(
            path = "/items",
            method = HttpMethod.GET,
            operationId = "listItems",
            parameters = listOf(schemaParam, contentParam)
        )

        val text = generator.generateApi("com.test", "ParamSchemaApi", listOf(endpoint)).text
        assertTrue(text.contains("@paramSchema limit"))
        assertTrue(text.contains("\"minimum\":1.0"))
        assertTrue(text.contains("@paramContent filter"))
        assertTrue(text.contains("\"application/json\""))
    }

    @Test
    fun `generateApi generates Ktor Auth configuration`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/secure",
                method = HttpMethod.GET,
                operationId = "secureOp"
            )
        )
        val schemes = mapOf(
            "BearerAuth" to SecurityScheme(type = "http", scheme = "bearer"),
            "BasicAuth" to SecurityScheme(type = "http", scheme = "basic"),
            "ApiKeyAuth" to SecurityScheme(type = "apiKey", name = "X-API-KEY", `in` = "header")
        )

        val text = generator.generateApi("com.auth", "AuthApi", endpoints, emptyList(), schemes).text

        // Verify Imports
        assertTrue(text.contains("import io.ktor.client.plugins.auth.*"), "Missing Auth imports")
        assertTrue(text.contains("import io.ktor.client.plugins.*"), "Missing Plugin imports")

        // Verify Factory Method Signature
        assertTrue(text.contains("fun createHttpClient("), "Missing createHttpClient factory")
        assertTrue(text.contains("bearerAuth: String? = null"))
        assertTrue(text.contains("basicAuthUser: String? = null"))
        assertTrue(text.contains("basicAuthPass: String? = null"))
        assertTrue(text.contains("apiKeyAuth: String? = null"))

        // Verify Install Blocks
        assertTrue(text.contains("install(Auth) {"), "Missing Auth plugin install")
        assertTrue(text.contains("bearer {"), "Missing bearer block")
        assertTrue(text.contains("basic {"), "Missing basic block")
        assertTrue(text.contains("BasicAuthCredentials("), "Missing basic credentials object")
        assertTrue(text.contains("BearerTokens(accessToken = bearerAuth"), "Missing bearer token loading")

        // Verify ApiKey via DefaultRequest
        assertTrue(text.contains("install(DefaultRequest) {"), "Missing DefaultRequest plugin")
        assertTrue(text.contains("header(\"X-API-KEY\", apiKeyAuth)"), "Missing header injection")
    }

    @Test
    fun `generateApi supports oauth2 and openIdConnect security schemes`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/secure",
                method = HttpMethod.GET,
                operationId = "secureOp"
            )
        )
        val schemes = mapOf(
            "oauth2Auth" to SecurityScheme(type = "oauth2"),
            "oidcAuth" to SecurityScheme(type = "openIdConnect", openIdConnectUrl = "https://example.com/.well-known")
        )

        val text = generator.generateApi("com.auth", "OauthApi", endpoints, emptyList(), schemes).text

        assertTrue(text.contains("oauth2Auth: OAuthTokens? = null"), "Missing OAuth2 auth parameter")
        assertTrue(text.contains("oidcAuth: OAuthTokens? = null"), "Missing OpenID Connect auth parameter")
        assertTrue(text.contains("bearer {"), "Missing bearer block for OAuth2/OpenID Connect")
        assertTrue(text.contains("BearerTokens(accessToken = oauth2Auth.accessToken"), "Missing OAuth2 bearer token loading")
        assertTrue(text.contains("BearerTokens(accessToken = oidcAuth.accessToken"), "Missing OpenID Connect bearer token loading")
    }

    @Test
    fun `generateApi emits OAuth flow scaffolding helpers`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/secure",
                method = HttpMethod.GET,
                operationId = "secureOp"
            )
        )
        val schemes = mapOf(
            "oauth2Auth" to SecurityScheme(type = "oauth2")
        )

        val text = generator.generateApi("com.auth", "OAuthHelpersApi", endpoints, emptyList(), schemes).text

        assertTrue(text.contains("data class OAuthTokens"), "Missing OAuthTokens model")
        assertTrue(text.contains("data class OAuthDeviceCodeResponse"), "Missing device code response model")
        assertTrue(text.contains("data class Pkce"), "Missing PKCE model")
        assertTrue(text.contains("fun createPkceVerifier"), "Missing PKCE verifier generator")
        assertTrue(text.contains("fun buildAuthorizationUrl"), "Missing authorization URL builder")
        assertTrue(text.contains("suspend fun exchangeAuthorizationCode"), "Missing authorization code exchange")
        assertTrue(text.contains("suspend fun refreshToken"), "Missing token refresh")
        assertTrue(text.contains("suspend fun requestDeviceCode"), "Missing device authorization request")
        assertTrue(text.contains("suspend fun pollDeviceToken"), "Missing device token polling helper")
    }

    @Test
    fun `generateApi supports mutualTLS security schemes`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/secure",
                method = HttpMethod.GET,
                operationId = "secureOp"
            )
        )
        val schemes = mapOf(
            "mutualTlsAuth" to SecurityScheme(type = "mutualTLS")
        )

        val text = generator.generateApi("com.auth", "MutualTlsApi", endpoints, emptyList(), schemes).text

        assertTrue(text.contains("data class MutualTlsConfig"), "Missing MutualTlsConfig model")
        assertTrue(text.contains("typealias MutualTlsConfigurer"), "Missing MutualTlsConfigurer hook")
        assertTrue(text.contains("mutualTlsAuth: MutualTlsConfig? = null"), "Missing mutualTLS config parameter")
        assertTrue(text.contains("mutualTlsConfigurer: MutualTlsConfigurer? = null"), "Missing mutualTLS configurer parameter")
        assertTrue(text.contains("mutualTlsConfigurer?.let"), "Missing mutualTLS configurer invocation")
        assertTrue(text.contains("configurer(this, mutualTlsAuth)"), "Missing mutualTLS config application")
    }

    @Test
    fun `generateApi supports custom HTTP methods`() {
        val endpoint = EndpointDefinition(
            path = "/copy",
            method = HttpMethod.CUSTOM,
            customMethod = "COPY",
            operationId = "copyItem"
        )

        val text = generator.generateApi("com.test", "CustomApi", listOf(endpoint)).text
        assertTrue(text.contains("method = HttpMethod(\"COPY\")"))
    }

    @Test
    fun `generateApi supports optional request bodies`() {
        val endpoint = EndpointDefinition(
            path = "/optional",
            method = HttpMethod.POST,
            operationId = "optionalBody",
            requestBody = RequestBody(
                required = false,
                content = mapOf(
                    "application/json" to MediaTypeObject(schema = SchemaProperty("string"))
                )
            )
        )

        val text = generator.generateApi("com.test", "OptionalBodyApi", listOf(endpoint)).text
        assertTrue(text.contains("suspend fun optionalBody(body: String? = null): Result<Unit>"))
        assertTrue(text.contains("if (body != null)"))
        assertTrue(text.contains("setBody(body)"))
    }

    @Test
    fun `generateApi handles explode true and array list query params`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "status",
                method = HttpMethod.GET,
                operationId = "getStatus",
                parameters = listOf(
                    EndpointParameter("tags", "List<String>", ParameterLocation.QUERY, explode = true),
                    EndpointParameter("codes", "Array<String>", ParameterLocation.QUERY, explode = false)
                )
            )
        )

        val text = generator.generateApi("com.query", "QueryApi", endpoints).text

        assertTrue(text.contains("client.request(\"\$baseUrl/status\")"))
        assertTrue(text.contains("tags.forEach { value -> parameter(\"tags\", value) }"))
        assertTrue(text.contains("parameter(\"codes\", codes.joinToString(\",\"))"))
    }

    @Test
    fun `generateApi handles space-delimited lists`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/search",
                method = HttpMethod.GET,
                operationId = "search",
                parameters = listOf(
                    EndpointParameter("terms", "List<String>", ParameterLocation.QUERY, style = ParameterStyle.SPACE_DELIMITED, explode = false)
                )
            )
        )

        val text = generator.generateApi("com.space", "SpaceApi", endpoints).text

        assertTrue(text.contains("parameter(\"terms\", terms.joinToString(\" \"))"))
    }

    @Test
    fun `generateApi handles apiKey locations and unsupported locations`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/auth",
                method = HttpMethod.GET,
                operationId = "auth"
            )
        )
        val schemes = mapOf(
            "Query-Key" to SecurityScheme(type = "apiKey", name = "q", `in` = "query"),
            "Cookie-Key" to SecurityScheme(type = "apiKey", name = "c", `in` = "cookie"),
            "Weird-Key" to SecurityScheme(type = "apiKey", name = "w", `in` = "body"),
            "Fancy-Key" to SecurityScheme(type = "apiKey", name = "X-FANCY", `in` = "header")
        )

        val text = generator.generateApi("com.apikey", "ApiKeyApi", endpoints, emptyList(), schemes).text

        assertTrue(text.contains("queryKey: String? = null"))
        assertTrue(text.contains("cookieKey: String? = null"))
        assertTrue(text.contains("weirdKey: String? = null"))
        assertTrue(text.contains("fancyKey: String? = null"))
        assertTrue(text.contains("url.parameters.append(\"q\", queryKey)"))
        assertTrue(text.contains("cookie(\"c\", cookieKey)"))
        assertTrue(text.contains("// Unsupported apiKey location: body"))
        assertTrue(text.contains("header(\"X-FANCY\", fancyKey)"))
    }

    @Test
    fun `generateMethodSignature handles empty params`() {
        val endpoint = EndpointDefinition(
            path = "/ping",
            method = HttpMethod.GET,
            operationId = "ping"
        )

        val signature = generator.generateMethodSignature(endpoint)

        assertEquals("suspend fun ping(): Result<Unit>", signature)
    }

    @Test
    fun `generateMethodSignature derives parameter type from schema and marks deprecated`() {
        val endpoint = EndpointDefinition(
            path = "/limits",
            method = HttpMethod.GET,
            operationId = "limits",
            parameters = listOf(
                EndpointParameter(
                    name = "limit",
                    type = "String",
                    location = ParameterLocation.QUERY,
                    schema = SchemaProperty(types = setOf("integer")),
                    deprecated = true
                ),
                EndpointParameter(
                    name = "tag",
                    type = "String",
                    location = ParameterLocation.QUERY,
                    schema = SchemaProperty(types = setOf("string", "null"))
                )
            )
        )

        val signature = generator.generateMethodSignature(endpoint)

        assertTrue(signature.contains("@Deprecated(\"Deprecated parameter\") limit: Int"))
        assertTrue(signature.contains("tag: String?"))
    }

    @Test
    fun `generateApi derives request body type from content when missing`() {
        val endpoint = EndpointDefinition(
            path = "/ingest",
            method = HttpMethod.POST,
            operationId = "ingest",
            requestBody = RequestBody(
                content = mapOf(
                    "application/json" to MediaTypeObject(
                        schema = SchemaProperty(types = setOf("integer"))
                    )
                ),
                required = false
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", type = "Unit")
            )
        )

        val text = generator.generateApi("com.test", "IngestApi", listOf(endpoint)).text

        assertTrue(text.contains("suspend fun ingest(body: Int? = null): Result<Unit>"))
        assertTrue(text.contains("contentType(ContentType.parse(\"application/json\"))"))
        assertTrue(text.contains("setBody(body)"))
    }

    @Test
    fun `generateApi derives non-null request body when required`() {
        val endpoint = EndpointDefinition(
            path = "/submit",
            method = HttpMethod.POST,
            operationId = "submit",
            requestBody = RequestBody(
                content = mapOf(
                    "application/json" to MediaTypeObject(
                        schema = SchemaProperty(types = setOf("string"))
                    )
                ),
                required = true
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", type = "Unit")
            )
        )

        val text = generator.generateApi("com.test", "SubmitApi", listOf(endpoint)).text

        assertTrue(text.contains("suspend fun submit(body: String): Result<Unit>"))
    }

    @Test
    fun `generateApi encodes sequential json request bodies`() {
        val endpoint = EndpointDefinition(
            path = "/stream",
            method = HttpMethod.POST,
            operationId = "streamEvents",
            requestBody = RequestBody(
                required = true,
                content = mapOf(
                    "application/x-ndjson" to MediaTypeObject(
                        itemSchema = SchemaProperty(types = setOf("string"))
                    )
                )
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", type = "Unit")
            )
        )

        val text = generator.generateApi("com.test", "SeqRequestApi", listOf(endpoint)).text

        assertTrue(text.contains("contentType(ContentType.parse(\"application/x-ndjson\"))"))
        assertTrue(text.contains("setBody(encodeSequentialJson(body, \"application/x-ndjson\"))"))
        assertTrue(text.contains("encodeSequentialJson"))
    }

    @Test
    fun `generateApi decodes sequential json responses`() {
        val endpoint = EndpointDefinition(
            path = "/stream",
            method = HttpMethod.GET,
            operationId = "streamEvents",
            responses = mapOf(
                "200" to EndpointResponse(
                    statusCode = "200",
                    content = mapOf(
                        "application/jsonl" to MediaTypeObject(
                            itemSchema = SchemaProperty(types = setOf("integer"))
                        )
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "SeqResponseApi", listOf(endpoint)).text

        assertTrue(text.contains("suspend fun streamEvents(): Result<List<Int>>"))
        assertTrue(text.contains("decodeSequentialJsonList<Int>(response.bodyAsText(), \"application/jsonl\")"))
        assertTrue(text.contains("import io.ktor.client.statement.*"))
        assertTrue(text.contains("import kotlinx.serialization.decodeFromString"))
    }

    @Test
    fun `generateApi selects first non-json request content type`() {
        val endpoint = EndpointDefinition(
            path = "/upload",
            method = HttpMethod.POST,
            operationId = "upload",
            requestBody = RequestBody(
                content = mapOf(
                    "application/xml" to MediaTypeObject(
                        schema = SchemaProperty(types = setOf("string"))
                    )
                ),
                required = true
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", type = "Unit")
            )
        )

        val text = generator.generateApi("com.test", "UploadApi", listOf(endpoint)).text

        assertTrue(text.contains("contentType(ContentType.parse(\"application/xml\"))"))
        assertTrue(text.contains("setBody(body)"))
    }

    @Test
    fun `generateApi encodes form urlencoded request bodies`() {
        val endpoint = EndpointDefinition(
            path = "/submit-form",
            method = HttpMethod.POST,
            operationId = "submitForm",
            requestBodyType = "FormPayload",
            requestBody = RequestBody(
                content = mapOf(
                    "application/x-www-form-urlencoded" to MediaTypeObject(
                        schema = SchemaProperty(types = setOf("object"))
                    )
                ),
                required = true
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", type = "Unit")
            )
        )

        val text = generator.generateApi("com.test", "FormApi", listOf(endpoint)).text

        assertTrue(text.contains("import io.ktor.client.request.forms.*"))
        assertTrue(text.contains("setBody(encodeFormBody(body"))
        assertTrue(text.contains("FormDataContent"))
        assertTrue(!text.contains("ContentType.parse(\"application/x-www-form-urlencoded\")"))
    }

    @Test
    fun `generateApi honors form encoding style overrides`() {
        val endpoint = EndpointDefinition(
            path = "/submit-form",
            method = HttpMethod.POST,
            operationId = "submitFormEncoded",
            requestBodyType = "FormPayload",
            requestBody = RequestBody(
                content = mapOf(
                    "application/x-www-form-urlencoded" to MediaTypeObject(
                        schema = SchemaProperty(types = setOf("object")),
                        encoding = mapOf(
                            "tags" to EncodingObject(
                                style = ParameterStyle.SPACE_DELIMITED,
                                explode = false,
                                allowReserved = true
                            ),
                            "meta" to EncodingObject(
                                style = ParameterStyle.DEEP_OBJECT
                            )
                        )
                    )
                ),
                required = true
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", type = "Unit")
            )
        )

        val text = generator.generateApi("com.test", "FormEncodingApi", listOf(endpoint)).text

        assertTrue(text.contains("styles = mapOf("))
        assertTrue(text.contains("\"tags\" to \"spaceDelimited\""))
        assertTrue(text.contains("\"meta\" to \"deepObject\""))
        assertTrue(text.contains("explode = mapOf(\"tags\" to false)"))
        assertTrue(text.contains("allowReserved = mapOf(\"tags\" to true)"))
        assertTrue(text.contains("TextContent("))
    }

    @Test
    fun `generateApi encodes multipart form bodies with encoding content types`() {
        val endpoint = EndpointDefinition(
            path = "/upload",
            method = HttpMethod.POST,
            operationId = "uploadMultipart",
            requestBodyType = "UploadPayload",
            requestBody = RequestBody(
                content = mapOf(
                    "multipart/form-data" to MediaTypeObject(
                        schema = SchemaProperty(types = setOf("object")),
                        encoding = mapOf(
                            "meta" to EncodingObject(contentType = "application/json")
                        )
                    )
                ),
                required = true
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", type = "Unit")
            )
        )

        val text = generator.generateApi("com.test", "MultipartApi", listOf(endpoint)).text

        assertTrue(text.contains("import io.ktor.client.request.forms.*"))
        assertTrue(text.contains("setBody(encodeMultipartBody(body"))
        assertTrue(text.contains("MultiPartFormDataContent"))
        assertTrue(text.contains("mapOf(\"meta\" to \"application/json\")"))
        assertTrue(text.contains("HttpHeaders.ContentType"))
    }

    @Test
    fun `generateApi includes multipart encoding headers when example values are provided`() {
        val endpoint = EndpointDefinition(
            path = "/upload",
            method = HttpMethod.POST,
            operationId = "uploadMultipartWithHeaders",
            requestBodyType = "UploadPayload",
            requestBody = RequestBody(
                content = mapOf(
                    "multipart/form-data" to MediaTypeObject(
                        schema = SchemaProperty(types = setOf("object")),
                        encoding = mapOf(
                            "meta" to EncodingObject(
                                contentType = "application/json",
                                headers = mapOf(
                                    "X-Trace" to Header(
                                        type = "String",
                                        example = ExampleObject(value = "trace-123")
                                    ),
                                    "Content-Type" to Header(
                                        type = "String",
                                        example = ExampleObject(value = "ignored")
                                    )
                                )
                            )
                        )
                    )
                ),
                required = true
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", type = "Unit")
            )
        )

        val text = generator.generateApi("com.test", "MultipartHeaderApi", listOf(endpoint)).text

        assertTrue(text.contains("headers = mapOf(\"meta\" to mapOf(\"X-Trace\" to \"trace-123\"))"))
    }

    @Test
    fun `generateApi encodes positional multipart bodies`() {
        val endpoint = EndpointDefinition(
            path = "/mixed",
            method = HttpMethod.POST,
            operationId = "sendParts",
            requestBody = RequestBody(
                required = true,
                content = mapOf(
                    "multipart/mixed" to MediaTypeObject(
                        schema = SchemaProperty(
                            types = setOf("array"),
                            items = SchemaProperty(types = setOf("string"))
                        ),
                        prefixEncoding = listOf(
                            EncodingObject(contentType = "application/json"),
                            EncodingObject(
                                contentType = "image/png",
                                headers = mapOf(
                                    "X-Trace" to Header(
                                        type = "String",
                                        example = ExampleObject(dataValue = "trace")
                                    )
                                )
                            )
                        ),
                        itemEncoding = EncodingObject(
                            contentType = "text/plain",
                            headers = mapOf(
                                "X-Item" to Header(
                                    type = "String",
                                    example = ExampleObject(dataValue = "item")
                                )
                            )
                        )
                    )
                )
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", type = "Unit")
            )
        )

        val text = generator.generateApi("com.test", "MultipartPositionalApi", listOf(endpoint)).text

        assertTrue(text.contains("suspend fun sendParts(body: List<String>): Result<Unit>"))
        assertTrue(text.contains("contentType(ContentType.parse(\"multipart/mixed\"))"))
        assertTrue(text.contains("setBody(encodeMultipartPositional(body"))
        assertTrue(text.contains("prefixContentTypes = listOf(\"application/json\", \"image/png\")"))
        assertTrue(text.contains("prefixHeaders = listOf(emptyMap(), mapOf(\"X-Trace\" to \"trace\"))"))
        assertTrue(text.contains("itemContentType = \"text/plain\""))
        assertTrue(text.contains("itemHeaders = mapOf(\"X-Item\" to \"item\")"))
    }

    @Test
    fun `generateApi omits contentType when request body content is empty`() {
        val endpoint = EndpointDefinition(
            path = "/empty-body",
            method = HttpMethod.POST,
            operationId = "emptyBody",
            requestBodyType = "String",
            requestBody = RequestBody(
                content = emptyMap(),
                required = true
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", type = "Unit")
            )
        )

        val text = generator.generateApi("com.test", "EmptyBodyApi", listOf(endpoint)).text

        assertTrue(text.contains("setBody(body)"))
        assertTrue(!text.contains("contentType(ContentType.parse"))
    }

    @Test
    fun `generateApi emits optional parameters with defaults and null checks`() {
        val queryEndpoint = EndpointDefinition(
            path = "/search",
            method = HttpMethod.GET,
            operationId = "search",
            parameters = listOf(
                EndpointParameter(
                    name = "q",
                    type = "String",
                    location = ParameterLocation.QUERY,
                    isRequired = false
                )
            )
        )

        val queryStringEndpoint = EndpointDefinition(
            path = "/search/raw",
            method = HttpMethod.GET,
            operationId = "searchRawOptional",
            parameters = listOf(
                EndpointParameter(
                    name = "raw",
                    type = "String",
                    location = ParameterLocation.QUERYSTRING,
                    isRequired = false
                )
            )
        )

        val text = generator.generateApi("com.test", "SearchApi", listOf(queryEndpoint, queryStringEndpoint)).text

        assertTrue(text.contains("suspend fun search(q: String? = null): Result<Unit>"))
        assertTrue(text.contains("if (q != null) {"))
        assertTrue(text.contains("parameter(\"q\", q)"))
        assertTrue(text.contains("suspend fun searchRawOptional(raw: String? = null): Result<Unit>"))
        assertTrue(text.contains("if (raw != null) {"))
        assertTrue(text.contains("url.encodedQuery = raw"))
    }

    @Test
    fun `generateApi includes parameter descriptions in KDoc`() {
        val endpoint = EndpointDefinition(
            path = "/search",
            method = HttpMethod.GET,
            operationId = "describeSearch",
            parameters = listOf(
                EndpointParameter(
                    name = "q",
                    type = "String",
                    location = ParameterLocation.QUERY,
                    description = "Search term"
                )
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", type = "Unit")
            )
        )

        val text = generator.generateApi("com.test", "DocApi", listOf(endpoint)).text

        assertTrue(text.contains("@param q Search term"))
    }

    @Test
    fun `generateApi supports querystring content serialization`() {
        val endpoint = EndpointDefinition(
            path = "/search",
            method = HttpMethod.GET,
            operationId = "search",
            parameters = listOf(
                EndpointParameter(
                    name = "selector",
                    type = "Selector",
                    location = ParameterLocation.QUERYSTRING,
                    content = mapOf(
                        "application/json" to MediaTypeObject(
                            schema = SchemaProperty(types = setOf("object"))
                        )
                    )
                )
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", description = "ok")
            )
        )

        val file = generator.generateApi(
            packageName = "com.example",
            apiName = "QueryApi",
            endpoints = listOf(endpoint)
        )

        val text = file.text
        assertTrue(text.contains("encodeQueryStringContent(selector, \"application/json\")"))
        assertTrue(text.contains("private inline fun <reified T> encodeQueryStringContent"))
        assertTrue(text.contains("kotlinx.serialization.json"))
    }

    @Test
    fun `generateApi derives response type from content schema`() {
        val endpoint = EndpointDefinition(
            path = "/content",
            method = HttpMethod.GET,
            operationId = "content",
            responses = mapOf(
                "200" to EndpointResponse(
                    statusCode = "200",
                    content = mapOf(
                        "application/json" to MediaTypeObject(
                            schema = SchemaProperty(types = setOf("string"))
                        )
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "ContentApi", listOf(endpoint)).text

        assertTrue(text.contains("Result<String>"))
        assertTrue(text.contains("response.body<String>()"))
    }

    @Test
    fun `generateApi returns Unit when no success responses`() {
        val endpoint = EndpointDefinition(
            path = "/failure",
            method = HttpMethod.GET,
            operationId = "failure",
            responses = mapOf(
                "400" to EndpointResponse(statusCode = "400", description = "Bad Request")
            )
        )

        val signature = generator.generateMethodSignature(endpoint)

        assertEquals("suspend fun failure(): Result<Unit>", signature)
    }

    @Test
    fun `generateApi uses itemSchema when schema missing`() {
        val endpoint = EndpointDefinition(
            path = "/stream",
            method = HttpMethod.GET,
            operationId = "stream",
            responses = mapOf(
                "200" to EndpointResponse(
                    statusCode = "200",
                    content = mapOf(
                        "text/plain" to MediaTypeObject(
                            itemSchema = SchemaProperty(types = setOf("integer"))
                        )
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "StreamApi", listOf(endpoint)).text

        assertTrue(text.contains("Result<List<Int>>"))
    }

    @Test
    fun `generateApi infers ByteArray for binary media types without schema`() {
        val endpoint = EndpointDefinition(
            path = "/binary",
            method = HttpMethod.GET,
            operationId = "getBinary",
            responses = mapOf(
                "200" to EndpointResponse(
                    statusCode = "200",
                    description = "ok",
                    content = mapOf(
                        "application/octet-stream" to MediaTypeObject()
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "BinaryApi", listOf(endpoint)).text

        assertTrue(text.contains("Result<ByteArray>"), "Expected ByteArray response type")
        assertTrue(text.contains("response.body<ByteArray>()"), "Expected ByteArray response body decoding")
    }

    @Test
    fun `generateApi infers String for form media types without schema`() {
        val endpoint = EndpointDefinition(
            path = "/form",
            method = HttpMethod.POST,
            operationId = "submitForm",
            requestBody = RequestBody(
                required = true,
                content = mapOf(
                    "application/x-www-form-urlencoded" to MediaTypeObject()
                )
            ),
            responses = mapOf(
                "200" to EndpointResponse(
                    statusCode = "200",
                    description = "ok"
                )
            )
        )

        val text = generator.generateApi("com.test", "FormApi", listOf(endpoint)).text

        assertTrue(text.contains("suspend fun submitForm(body: String): Result<Unit>"))
        assertTrue(text.contains("encodeFormBody(body"), "Expected form body encoding for schema-less form content")
    }

    @Test
    fun `generateApi returns Unit when content empty`() {
        val endpoint = EndpointDefinition(
            path = "/empty",
            method = HttpMethod.GET,
            operationId = "empty",
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200")
            )
        )

        val signature = generator.generateMethodSignature(endpoint)

        assertEquals("suspend fun empty(): Result<Unit>", signature)
    }

    @Test
    fun `generateApi selects most specific media type for request and response`() {
        val endpoint = EndpointDefinition(
            path = "/media",
            method = HttpMethod.POST,
            operationId = "sendMedia",
            requestBody = RequestBody(
                required = true,
                content = mapOf(
                    "application/*" to MediaTypeObject(schema = SchemaProperty(types = setOf("string"))),
                    "application/json" to MediaTypeObject(schema = SchemaProperty(types = setOf("integer")))
                )
            ),
            responses = mapOf(
                "200" to EndpointResponse(
                    statusCode = "200",
                    description = "ok",
                    content = mapOf(
                        "text/*" to MediaTypeObject(schema = SchemaProperty(types = setOf("string"))),
                        "text/plain" to MediaTypeObject(schema = SchemaProperty(types = setOf("integer")))
                    )
                )
            )
        )

        val text = generator.generateApi("com.test", "MediaApi", listOf(endpoint)).text

        assertTrue(text.contains("suspend fun sendMedia(body: Int): Result<Int>"))
        assertTrue(text.contains("contentType(ContentType.parse(\"application/json\"))"))
    }

    @Test
    fun `generateApi emits operationIdOmitted tag`() {
        val endpoint = EndpointDefinition(
            path = "/pets",
            method = HttpMethod.GET,
            operationId = "get_pets",
            operationIdExplicit = false,
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )

        val text = generator.generateApi("com.test", "PetsApi", listOf(endpoint)).text
        assertTrue(text.contains("@operationIdOmitted"))

        val parsed = NetworkParser().parseWithMetadata(text).endpoints.first()
        assertFalse(parsed.operationIdExplicit)
    }
}
