package psi

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeederTestGeneratorTest {
  @Test
  fun `generateSeederTestModule produces expected tests`() {
    val generator = SeederTestGenerator()
    val result = generator.generateSeederTestModule("com.example", emptyList())

    assertTrue(result.contains("class DatabaseSeederTest"), "Missing DatabaseSeederTest")
    assertTrue(
        result.contains(
            "fun `seedDatabase populates tables while maintaining referential integrity`()"),
        "Missing seedDatabase test")
  }
}
