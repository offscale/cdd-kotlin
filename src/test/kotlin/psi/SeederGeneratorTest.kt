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

    val complexSchema =
        SchemaDefinition(
            name = "ComplexEntity",
            type = "object",
            required = listOf("id", "tags", "attrs", "anyProp", "customRef", "parentId"),
            properties =
                mapOf(
                    "id" to SchemaProperty(ref = "#/components/schemas/CustomId"),
                    "tags" to
                        SchemaProperty(
                            types = setOf("array"),
                            items = SchemaProperty(types = setOf("string"))),
                    "attrs" to
                        SchemaProperty(
                            types = setOf("object"),
                            additionalProperties = SchemaProperty(types = setOf("string"))),
                    "anyProp" to SchemaProperty(ref = "#/components/schemas/AnyType"),
                    "customRef" to SchemaProperty(ref = "#/components/schemas/Other"),
                    "parentId" to SchemaProperty(ref = "#/components/schemas/CustomId"),
                    "longParentId" to SchemaProperty(type = "integer", format = "int64"),
                    "phone" to SchemaProperty(type = "string")))
    val schemaLongId =
        SchemaDefinition(
            name = "LongItem",
            type = "object",
            properties = mapOf("id" to SchemaProperty(type = "integer", format = "int64")))
    val complexResult =
        generator.generateSeederModule("com.example", listOf(complexSchema, schemaLongId))
    assertTrue(complexResult.contains("id = 0L"))
    assertTrue(complexResult.contains("emptyList()"))
    assertTrue(complexResult.contains("emptyMap()"))
    assertTrue(complexResult.contains("generateOther()"))
    assertTrue(complexResult.contains("faker.phoneNumber.phoneNumber()"))
  }
}
