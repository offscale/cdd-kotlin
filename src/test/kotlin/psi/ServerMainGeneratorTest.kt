/** Unit and integration tests in [ServerMainGeneratorTest]. */
package psi

import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.HttpMethod
import domain.ParameterLocation
import domain.RequestBody
import domain.SchemaDefinition
import domain.SchemaProperty
import domain.SecurityScheme
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerMainGeneratorTest {
  @Test
  fun `generateServerMainModule produces expected cli and server logic`() {
    val generator = ServerMainGenerator()
    val schema =
        SchemaDefinition(
            name = "User",
            type = "object",
            properties =
                mapOf(
                    "id" to SchemaProperty(type = "integer"),
                    "name" to SchemaProperty(type = "string")))
    val schemaLongId =
        SchemaDefinition(
            name = "Account",
            type = "object",
            properties = mapOf("id" to SchemaProperty(type = "integer", format = "int64")))
    val schemaStringId =
        SchemaDefinition(
            name = "Session",
            type = "object",
            properties = mapOf("id" to SchemaProperty(type = "string")))
    val schemaNoId =
        SchemaDefinition(
            name = "AuditLog",
            type = "object",
            properties = mapOf("msg" to SchemaProperty(type = "string")))

    val endpoints =
        listOf(
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.GET,
                operationId = "getUser",
                security = listOf(mapOf("apiKeyAuth" to emptyList())),
                parameters =
                    listOf(
                        EndpointParameter("id", "Int", ParameterLocation.PATH, isRequired = true),
                        EndpointParameter(
                            "filter", "String", ParameterLocation.QUERY, isRequired = true),
                        EndpointParameter(
                            "X-Trace", "String", ParameterLocation.HEADER, isRequired = true),
                        EndpointParameter(
                            "opt", "String", ParameterLocation.QUERY, isRequired = false),
                        EndpointParameter(
                            "cookieParam", "String", ParameterLocation.COOKIE, isRequired = true)),
                responses = mapOf("200" to EndpointResponse("200", description = "ok"))),
            EndpointDefinition(
                path = "/users",
                method = HttpMethod.POST,
                operationId = "createUser",
                security = listOf(mapOf("bearerAuth" to emptyList())),
                requestBody = RequestBody(required = true),
                responses = mapOf("201" to EndpointResponse("201", description = "created"))),
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.PUT,
                operationId = "updateUser",
                requestBody = RequestBody(required = false),
                responses = mapOf("200" to EndpointResponse("200", description = "updated"))),
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.DELETE,
                operationId = "deleteUser",
                responses = mapOf("204" to EndpointResponse("204", description = "deleted"))),
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.PATCH,
                operationId = "patchUser",
                responses = mapOf("200" to EndpointResponse("200", description = "patched"))),
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.HEAD,
                operationId = "headUser",
                responses = mapOf("200" to EndpointResponse("200", description = "head"))),
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.OPTIONS,
                operationId = "optionsUser",
                responses = mapOf("200" to EndpointResponse("200", description = "options"))),
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.TRACE,
                operationId = "traceUser",
                responses = mapOf("200" to EndpointResponse("200", description = "trace"))))

    val schemes =
        mapOf(
            "apiKeyAuth" to SecurityScheme(type = "apiKey", `in` = "header", name = "X-API-Key"),
            "queryKeyAuth" to SecurityScheme(type = "apiKey", `in` = "query", name = "api_key"),
            "bearerAuth" to SecurityScheme(type = "http", scheme = "bearer"))

    val result =
        generator
            .generateServerMain(
                "com.example",
                listOf(schema, schemaLongId, schemaStringId, schemaNoId),
                endpoints,
                schemes)
            .values
            .joinToString("\n")

    assertTrue(result.contains("class MockServerCli : CliktCommand"), "Missing CLI class")
    assertTrue(result.contains("val ephemeral by option(\"--ephemeral\""), "Missing ephemeral flag")
    assertTrue(result.contains("val seed by option(\"--seed\""), "Missing seed flag")
    assertTrue(
        result.contains("val daoConfig = DaoFactory.create(useConcrete = useConcrete)"),
        "Missing DAO resolve")
    assertTrue(result.contains("DatabaseConnection.initialize(dbConfig)"), "Missing DB init")
    assertTrue(result.contains("val seeder = DatabaseSeeder(daoConfig)"), "Missing Seeder init")
    assertTrue(result.contains("embeddedServer(Netty, port = 8080)"), "Missing Ktor server setup")
    assertTrue(result.contains("get(\"/user\") {"), "Missing user route")
    assertTrue(result.contains("get(\"/user/{id}\") {"), "Missing user by id route")
    assertTrue(
        result.contains("openApiEndpointRoutes(daoConfig)"), "Missing OpenAPI endpoint routes")
    assertTrue(result.contains("X-API-Key"), "Missing apiKey header check")
    assertTrue(generator.generateServerMain("com.example", listOf(schema)).isNotEmpty())
  }
}
