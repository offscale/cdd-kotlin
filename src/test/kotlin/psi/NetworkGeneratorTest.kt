package psi

import domain.EndpointDefinition
import domain.HttpMethod
import domain.EndpointParameter
import domain.ParameterLocation
import org.junit.jupiter.api.Assertions.assertTrue
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
                responseType = "String",
                parameters = emptyList()
            )
        )

        val file = generator.generateApi("com.test", "HealthApi", endpoints)
        val text = file.text

        assertTrue(text.contains("interface IHealthApi"), "Missing Interface")
        assertTrue(text.contains("class HealthApi(private val client: HttpClient) : IHealthApi"), "Missing Implementation")
        assertTrue(text.contains("class ApiException(message: String) : Exception(message)"), "Missing Exception class")
        assertTrue(text.contains("import io.ktor.client.*"), "Missing imports")
    }

    @Test
    fun `generateApi wraps calls in try-catch`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/safe",
                method = HttpMethod.GET,
                operationId = "safeCall",
                responseType = "Unit",
                parameters = emptyList()
            )
        )

        val text = generator.generateApi("com.test", "SafeApi", endpoints).text

        // Check for try-catch block structure
        assertTrue(text.contains("try {"))
        assertTrue(text.contains("client.request(\"/safe\")"))
        assertTrue(text.contains("catch (e: Exception) {"))
        assertTrue(text.contains("throw ApiException"))
    }

    @Test
    fun `generateApi handles POST body`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/submit",
                method = HttpMethod.POST,
                operationId = "submitData",
                responseType = "String",
                parameters = emptyList(),
                requestBodyType = "MyData"
            )
        )

        val text = generator.generateApi("com.test", "PostApi", endpoints).text

        assertTrue(text.contains("suspend fun submitData(body: MyData): String"))
        assertTrue(text.contains("setBody(body)"))
        assertTrue(text.contains("method = HttpMethod.Post"))
    }

    @Test
    fun `generateApi interpolates Path parameters`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.GET,
                operationId = "getUser",
                responseType = "String",
                parameters = listOf(
                    EndpointParameter("id", "String", ParameterLocation.PATH)
                )
            )
        )

        val text = generator.generateApi("com.test", "UserApi", endpoints).text

        // Expected transformation: /users/{id} -> /users/$id
        assertTrue(text.contains("client.request(\"/users/\$id\")"))
    }

    @Test
    fun `generateApi handles Query and Header parameters`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/search",
                method = HttpMethod.GET,
                operationId = "search",
                responseType = "List<String>",
                parameters = listOf(
                    EndpointParameter("q", "String", ParameterLocation.QUERY),
                    EndpointParameter("auth", "String", ParameterLocation.HEADER)
                )
            )
        )

        val text = generator.generateApi("com.test", "SearchApi", endpoints).text

        assertTrue(text.contains("parameter(\"q\", q)"))
        assertTrue(text.contains("header(\"auth\", auth)"))
    }
}
