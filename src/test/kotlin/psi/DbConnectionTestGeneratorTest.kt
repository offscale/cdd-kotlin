package psi

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DbConnectionTestGeneratorTest {
  @Test
  fun `generateDbConnectionTestModule produces expected tests`() {
    val generator = DbConnectionTestGenerator()
    val result = generator.generateDbConnectionTestModule("com.example", emptyList())

    assertTrue(result.contains("class DatabaseConnectionTest"), "Missing DatabaseConnectionTest")
    assertTrue(
        result.contains("fun `resolveConfig with ephemeral flag true returns ephemeral SQLite`()"),
        "Missing ephemeral test")
    assertTrue(
        result.contains("fun `resolveConfig with postgres envUrl returns postgres config`()"),
        "Missing postgres test")
  }
}
