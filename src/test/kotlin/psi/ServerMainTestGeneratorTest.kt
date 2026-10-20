/** Unit and integration tests in [ServerMainTestGeneratorTest]. */
package psi

import domain.EndpointDefinition
import domain.EndpointParameter
import domain.HttpMethod
import domain.ParameterLocation
import domain.RequestBody
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerMainTestGeneratorTest {
  @Test
  fun `generateServerMainTestModule produces expected tests`() {
    val generator = ServerMainTestGenerator()
    val endpoints =
        listOf(
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.GET,
                operationId = "getUser",
                security = listOf(mapOf("bearer" to emptyList())),
                parameters =
                    listOf(
                        EndpointParameter(
                            "X-Header", "String", ParameterLocation.HEADER, isRequired = true),
                        EndpointParameter(
                            "q", "String", ParameterLocation.QUERY, isRequired = true),
                        EndpointParameter(
                            "opt", "String", ParameterLocation.HEADER, isRequired = false))),
            EndpointDefinition(
                path = "/users",
                method = HttpMethod.POST,
                operationId = "createUser",
                requestBody = RequestBody(required = true)),
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.PUT,
                operationId = "updateUser",
                requestBody = RequestBody(required = false)),
            EndpointDefinition(
                path = "/users/{id}", method = HttpMethod.DELETE, operationId = "deleteUser"),
            EndpointDefinition(
                path = "/users/{id}", method = HttpMethod.PATCH, operationId = "patchUser"))

    val result =
        generator
            .generateServerMainTests(
                "com.example",
                listOf(
                    domain.SchemaDefinition(name = "User"), domain.SchemaDefinition(name = "Post")),
                endpoints)
            .values
            .joinToString("\n")

    assertTrue(result.contains("class MockServerCliTest"), "Missing MockServerCliTest")
    assertTrue(
        result.contains("fun `cli parser handles ephemeral and seed flags`()"), "Missing cli test")
    assertTrue(
        result.contains("class OpenApiEndpointRoutesTest"), "Missing OpenApiEndpointRoutesTest")
    assertTrue(result.contains("endpoint getUser responds as configured"), "Missing getUser test")
    assertTrue(
        result.contains("endpoint createUser responds as configured"), "Missing createUser test")
    assertTrue(
        generator
            .generateServerMainTests("com.example", listOf(domain.SchemaDefinition(name = "User")))
            .isNotEmpty())
  }
}
