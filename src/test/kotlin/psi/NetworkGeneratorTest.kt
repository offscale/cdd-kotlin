package psi

import domain.EndpointDefinition
import domain.HttpMethod
import domain.EndpointParameter
import domain.ParameterLocation
import domain.ParameterStyle
import domain.Server
import domain.SecurityScheme
import domain.EndpointResponse
import domain.ExternalDocumentation
import org.junit.jupiter.api.Assertions.assertTrue
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

        assertTrue(text.contains("client.request(\"\$baseUrl/users/\$id\")"))
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
        assertTrue(text.contains("client.request(\"\$baseUrl/users;id=\$id\")"), "Matrix path generation failed")

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
        assertTrue(text.contains("/files.\$ext"))
    }

    @Test
    fun `generateApi handles servers and base URL`() {
        val servers = listOf(
            Server("https://api.v1.com", "Prod"),
            Server("https://staging.v1.com", "Staging")
        )
        val endpoints = listOf(
            EndpointDefinition("/info", HttpMethod.GET, "getInfo")
        )
        val text = generator.generateApi("com.srv", "ServerApi", endpoints, servers).text
        assertTrue(text.contains("private val baseUrl: String = \"https://api.v1.com\""))
        assertTrue(text.contains("val SERVERS = listOf(\"https://api.v1.com\", \"https://staging.v1.com\")"))
    }

    @Test
    fun `generateApi includes ExternalDocumentation and Tags and Responses as KDoc`() {
        val endpoint = EndpointDefinition(
            path = "/docs",
            method = HttpMethod.GET,
            operationId = "getDocs",
            summary = "Fetch docs",
            externalDocs = ExternalDocumentation("Reference", "http://docs"),
            tags = listOf("system", "public"),
            responses = mapOf(
                "200" to EndpointResponse("200", "Success", "String"),
                "404" to EndpointResponse("404", "Not Found", null)
            )
        )

        val text = generator.generateApi("com.test", "DocApi", listOf(endpoint)).text

        assertTrue(text.contains("Fetch docs"))
        assertTrue(text.contains("@see http://docs Reference"))
        assertTrue(text.contains("@tag system, public"))
        assertTrue(text.contains("@response 200 String Success"))
        assertTrue(text.contains("@response 404 Unit Not Found"))
    }

    @Test
    fun `generateApi generates Ktor Auth configuration`() {
        val endpoints = listOf(EndpointDefinition("/secure", HttpMethod.GET, "secureOp"))
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
}
