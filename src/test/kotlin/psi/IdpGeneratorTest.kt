/** Unit and integration tests in [IdpGeneratorTest]. */
package psi

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdpGeneratorTest {
  @Test
  fun `generateIdpModule produces auth routes and production auth validator`() {
    val generator = IdpGenerator()
    val result = generator.generateIdpModule("com.example", emptyList())

    assertTrue(
        result.contains("fun Route.authRoutes(daoConfig: DaoConfiguration)"), "Missing auth routes")
    assertTrue(result.contains("post(\"/login\")"), "Missing login route")
    assertTrue(result.contains("post(\"/register\")"), "Missing register route")
    assertTrue(result.contains("object ProductionAuth"), "Missing ProductionAuth")
    assertTrue(
        result.contains("fun validateToken(token: String, daoConfig: DaoConfiguration)"),
        "Missing token validator")

    val withSchemas =
        generator.generateIdpModule(
            "com.example",
            listOf(domain.SchemaDefinition("User"), domain.SchemaDefinition("Account")),
            mapOf(
                "oauth" to
                    domain.SecurityScheme(
                        type = "oauth2",
                        scopes = mapOf("legacy:scope" to "legacy"),
                        flows =
                            domain.OAuthFlows(
                                implicit =
                                    domain.OAuthFlow(
                                        authorizationUrl = "https://auth.example.com",
                                        scopes = mapOf("read:users" to "read")),
                                password =
                                    domain.OAuthFlow(
                                        tokenUrl = "https://auth.example.com/token",
                                        scopes = mapOf("write:users" to "write")))),
                "basic" to domain.SecurityScheme(type = "http", scheme = "basic")))
    assertTrue(withSchemas.contains("val principalModelName: String = \"User\""))
    assertTrue(withSchemas.contains("ConcurrentHashMap.newKeySet"))
    assertTrue(withSchemas.contains("read:users"))
    assertTrue(withSchemas.contains("write:users"))
    assertTrue(withSchemas.contains("legacy:scope"))
  }
}
