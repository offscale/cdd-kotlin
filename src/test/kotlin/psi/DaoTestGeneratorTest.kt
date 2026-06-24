package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DaoTestGeneratorTest {
  @Test
  fun `generateDaoTestModule produces expected test classes`() {
    val generator = DaoTestGenerator()
    val schema =
        SchemaDefinition(
            name = "User",
            type = "object",
            properties =
                mapOf(
                    "id" to SchemaProperty(type = "integer"),
                    "name" to SchemaProperty(type = "string")))
    val result = generator.generateDaoTests("com.example", listOf(schema)).values.joinToString("\n")

    // Check for Factory Test
    assertTrue(result.contains("class DaoFactoryTest"), "Missing DaoFactoryTest")
    assertTrue(
        result.contains("fun `create with true returns concrete DAOs`()"),
        "Missing concrete factory test")
    assertTrue(
        result.contains("fun `create with false returns stub DAOs`()"), "Missing stub factory test")

    // Check for Concrete DAO test
    assertTrue(result.contains("class ConcreteUserDaoTest"), "Missing ConcreteUserDaoTest")
    assertTrue(
        result.contains("fun `getAll returns empty list initially`()"), "Missing getAll test")
    assertTrue(
        result.contains("fun `create inserts record and getAll retrieves it`()"),
        "Missing create test")
    assertTrue(result.contains("fun `getById retrieves correct record`()"), "Missing getById test")
    assertTrue(result.contains("fun `delete removes record`()"), "Missing delete test")
  }

  @Test
  fun `generateDaoTestModule handles schemas without id`() {
    val generator = DaoTestGenerator()
    val schema =
        SchemaDefinition(
            name = "LogEvent",
            type = "object",
            properties = mapOf("message" to SchemaProperty(type = "string")))
    val result = generator.generateDaoTests("com.example", listOf(schema)).values.joinToString("\n")

    assertTrue(result.contains("class ConcreteLogEventDaoTest"), "Missing ConcreteLogEventDaoTest")
    assertTrue(
        !result.contains("fun `getById retrieves correct record`()"),
        "Should not have getById test")
  }
}
