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
  }
}
