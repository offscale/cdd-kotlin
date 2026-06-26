package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DaoGeneratorTest {
  @Test
  fun `generateDaoModule produces expected DAOs`() {
    val generator = DaoGenerator()
    val schema =
        SchemaDefinition(
            name = "User",
            type = "object",
            properties =
                mapOf(
                    "id" to SchemaProperty(type = "integer"),
                    "name" to SchemaProperty(type = "string")))
    val result = generator.generateDaos("com.example", listOf(schema)).values.joinToString("\n")

    // Check for Interfaces
    assertTrue(result.contains("interface UserDao"), "Missing UserDao interface")

    // Check for Stubs
    assertTrue(result.contains("class StubUserDao : UserDao"), "Missing StubUserDao")
    assertTrue(
        result.contains("override suspend fun getAll(): List<User>"), "Stub is missing methods")

    // Check for Concrete DAOs
    assertTrue(result.contains("object UserTable : Table(\"users\")"), "Missing UserTable")
    assertTrue(result.contains("class ConcreteUserDao : UserDao"), "Missing ConcreteUserDao")

    // Check for Factory
    assertTrue(result.contains("class DaoConfiguration("), "Missing DaoConfiguration")
    assertTrue(result.contains("object DaoFactory"), "Missing DaoFactory")
  }

  @Test
  fun `generateDaoModule skips non-object schemas`() {
    val generator = DaoGenerator()
    val schema = SchemaDefinition(name = "UserId", type = "string")
    val result = generator.generateDaos("com.example", listOf(schema)).values.joinToString("\n")
    assertTrue(
        !result.contains("interface UserIdDao"), "Should not generate DAOs for primitive types")
  }

  @Test
  fun `generateDaoModule handles schemas without id`() {
    val generator = DaoGenerator()
    val schema =
        SchemaDefinition(
            name = "LogEvent",
            type = "object",
            properties = mapOf("message" to SchemaProperty(type = "string")))
    val result = generator.generateDaos("com.example", listOf(schema)).values.joinToString("\n")
    assertTrue(result.contains("interface LogEventDao"), "Missing LogEventDao")
    assertTrue(!result.contains("getById("), "Should not have getById without id property")
  }
}
