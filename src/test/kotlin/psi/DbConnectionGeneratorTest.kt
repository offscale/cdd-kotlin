package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DbConnectionGeneratorTest {
  @Test
  fun `generateDbConnectionModule produces expected configuration classes`() {
    val generator = DbConnectionGenerator()
    val schema =
        SchemaDefinition(
            name = "User",
            type = "object",
            properties =
                mapOf(
                    "id" to SchemaProperty(type = "integer"),
                    "name" to SchemaProperty(type = "string")))
    val result = generator.generateDbConnectionModule("com.example", listOf(schema))

    assertTrue(result.contains("data class DbConfig("), "Missing DbConfig")
    assertTrue(result.contains("object DatabaseConnection"), "Missing DatabaseConnection object")
    assertTrue(result.contains("fun resolveConfig("), "Missing resolveConfig method")
    assertTrue(result.contains("fun initialize("), "Missing initialize method")
    assertTrue(result.contains("SchemaUtils.create("), "Missing schema creation logic")
    assertTrue(result.contains("UserTable"), "Missing UserTable in schema creation")
  }
}
