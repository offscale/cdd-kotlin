package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeederGeneratorTest {
  @Test
  fun `generateSeederModule produces expected classes`() {
    val generator = SeederGenerator()
    val schema1 =
        SchemaDefinition(
            name = "User",
            type = "object",
            properties =
                mapOf(
                    "id" to SchemaProperty(type = "integer"),
                    "name" to SchemaProperty(type = "string"),
                    "email" to SchemaProperty(type = "string")))
    val schema2 =
        SchemaDefinition(
            name = "Post",
            type = "object",
            properties =
                mapOf(
                    "id" to SchemaProperty(type = "integer"),
                    "userId" to SchemaProperty(type = "integer"),
                    "title" to SchemaProperty(type = "string")))
    val result = generator.generateSeederModule("com.example", listOf(schema1, schema2))

    assertTrue(result.contains("class DatabaseSeeder"), "Missing DatabaseSeeder")
    assertTrue(result.contains("private val poolUserIds"), "Missing User pool")
    assertTrue(result.contains("private val poolPostIds"), "Missing Post pool")
    assertTrue(result.contains("fun seedDatabase()"), "Missing seedDatabase")
    assertTrue(result.contains("generateUser()"), "Missing generateUser")
    assertTrue(result.contains("generatePost()"), "Missing generatePost")

    assertTrue(result.contains("faker.internet.email()"), "Missing email faker logic")
    assertTrue(result.contains("faker.name.name()"), "Missing name faker logic")
    assertTrue(
        result.contains("if (poolUserIds.isNotEmpty()) poolUserIds.random() else 1"),
        "Missing relational dependency resolution")
  }
}
