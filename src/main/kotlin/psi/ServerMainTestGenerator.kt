/** Unit test generator for the generated Ktor and Clikt mock server CLI. */
package psi

import domain.EndpointDefinition
import domain.HttpMethod
import domain.SchemaDefinition

/** Generates tests for the Mock Server CLI. */
class ServerMainTestGenerator {

  /**
   * Generates a test class for MockServerCli and route suites.
   *
   * @param packageName The package namespace.
   * @param schemas The schemas.
   * @param endpoints Optional list of OpenAPI endpoint definitions to generate route tests for.
   * @return Map of relative test file paths to generated Kotlin test source code.
   */
  fun generateServerMainTests(
      packageName: String,
      schemas: List<SchemaDefinition>,
      endpoints: List<EndpointDefinition> = emptyList()
  ): Map<String, String> {
    val results = mutableMapOf<String, String>()

    results["MainTest.kt"] =
        buildKotlinFile(packageName) {
          import("kotlin.test.*", "kotlin.test.Test", "com.github.ajalt.clikt.testing.test")
          kdoc("Tests for [MockServerCli].")
          block("class MockServerCliTest") {
            line("@Test")
            block("fun `cli parser handles ephemeral and seed flags`()") {
              line("val cli = MockServerCli()")
              line("""val result = cli.test("--ephemeral --seed --dry-run")""")
              line("assertNotNull(result)")
            }
            line()
            line("@Test")
            block("fun `cli parser handles strict-validation flag`()") {
              line("val cli = MockServerCli()")
              line("""val result = cli.test("--strict-validation --dry-run")""")
              line("assertNotNull(result)")
            }
            line()
            line("@Test")
            block("fun `cli parser handles enforce-auth flag`()") {
              line("val cli = MockServerCli()")
              line("""val result = cli.test("--enforce-auth --dry-run")""")
              line("assertNotNull(result)")
            }
            line()
            line("@Test")
            block("fun `cli parser handles start-auth-server flag`()") {
              line("val cli = MockServerCli()")
              line("""val result = cli.test("--start-auth-server --dry-run")""")
              line("assertNotNull(result)")
            }
          }
        }

    for (schema in filterModelSchemas(schemas)) {
      val name = capitalizeName(schema.safeName)
      val routeName = decapitalizeName(schema.safeName)
      results["routes/${name}RoutesTest.kt"] =
          buildKotlinFile("$packageName.routes") {
            import(
                "kotlin.test.Test",
                "kotlin.test.*",
                "io.ktor.client.request.get",
                "io.ktor.http.HttpStatusCode",
                "io.ktor.server.testing.testApplication",
                "io.ktor.server.routing.routing",
                "$packageName.dao.DaoFactory")
            block("class ${name}RoutesTest") {
              line("@Test")
              block(
                  "fun `route $routeName handles stub DAO returning NotImplementedError`() = testApplication") {
                    line("val daoConfig = DaoFactory.create(useConcrete = false)")
                    block("application") {
                      block("routing") { line("${routeName}Routes(daoConfig)") }
                    }
                    line("""val response = client.get("/$routeName")""")
                    line("assertEquals(HttpStatusCode.NotImplemented, response.status)")
                  }
            }
          }
    }

    if (endpoints.isNotEmpty()) {
      results["routes/OpenApiEndpointRoutesTest.kt"] =
          buildKotlinFile("$packageName.routes") {
            import(
                "kotlin.test.Test",
                "kotlin.test.*",
                "io.ktor.client.request.*",
                "io.ktor.http.HttpHeaders",
                "io.ktor.http.HttpStatusCode",
                "io.ktor.server.testing.testApplication",
                "io.ktor.server.routing.routing",
                "$packageName.dao.DaoFactory")
            block("class OpenApiEndpointRoutesTest") {
              for (ep in endpoints) {
                val samplePath = ep.path.replace(Regex("""\{[^}]+\}"""), "1")
                val ktorCall =
                    when (ep.method) {
                      HttpMethod.POST -> "post"
                      HttpMethod.PUT -> "put"
                      HttpMethod.DELETE -> "delete"
                      HttpMethod.PATCH -> "patch"
                      else -> "get"
                    }
                line("@Test")
                block(
                    "fun `endpoint ${ep.operationId} responds as configured`() = testApplication") {
                      line("val daoConfig = DaoFactory.create(useConcrete = false)")
                      block("application") {
                        block("routing") { line("openApiEndpointRoutes(daoConfig)") }
                      }
                      if (ep.security.isNotEmpty()) {
                        line("""val unauth = client.$ktorCall("$samplePath")""")
                        line("assertEquals(HttpStatusCode.Unauthorized, unauth.status)")
                      }
                      block("""val response = client.$ktorCall("$samplePath")""") {
                        if (ep.security.isNotEmpty()) {
                          line("""header(HttpHeaders.Authorization, "Bearer test-token")""")
                        }
                        for (param in ep.parameters.filter { it.isRequired }) {
                          if (param.location == domain.ParameterLocation.HEADER) {
                            line("""header("${param.name}", "test")""")
                          }
                        }
                        if (ep.requestBody?.required == true) {
                          line("""setBody("{}")""")
                        }
                      }
                      line("assertNotNull(response.status)")
                    }
                line()
              }
            }
          }
    }

    return results
  }
}
