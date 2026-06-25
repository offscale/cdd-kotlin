package psi

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerMainTestGeneratorTest {
  @Test
  fun `generateServerMainTestModule produces expected tests`() {
    val generator = ServerMainTestGenerator()
    val result =
        generator
            .generateServerMainTests(
                "com.example",
                listOf(
                    domain.SchemaDefinition(name = "User"), domain.SchemaDefinition(name = "Post")))
            .values
            .joinToString("\n")

    assertTrue(result.contains("class MockServerCliTest"), "Missing MockServerCliTest")
    assertTrue(
        result.contains("fun `cli parser handles ephemeral and seed flags`()"), "Missing cli test")
  }
}
