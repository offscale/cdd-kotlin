package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.AfterAll

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DtoGeneratorTest {

    private val generator = DtoGenerator()

    @AfterAll
    fun tearDown() {
        PsiInfrastructure.dispose()
    }

    @Test
    fun `generateDto creates valid Data Class`() {
        val schema = SchemaDefinition(
            name = "User",
            type = "object",
            properties = mapOf(
                "name" to SchemaProperty("string"),
                "age" to SchemaProperty("integer")
            ),
            required = listOf("name")
        )

        val file = generator.generateDto("com.example", schema)
        val text = file.text

        assertTrue(text.contains("package com.example"))
        assertTrue(text.contains("data class User("))
        assertTrue(text.contains("val name: String"))
        assertTrue(text.contains("@SerialName(\"name\")"))
    }

    @Test
    fun `generateDto handles Nullable properties`() {
        val schema = SchemaDefinition(
            name = "Product",
            type = "object",
            properties = mapOf(
                "id" to SchemaProperty("integer"),
                "description" to SchemaProperty("string")
            ),
            required = listOf("id") // description is optional
        )

        val text = generator.generateDto("com.shop", schema).text

        // id is required -> Int
        assertTrue(text.contains("val id: Int"))
        assertFalse(text.contains("val id: Int?"))

        // description is optional -> String? = null
        assertTrue(text.contains("val description: String? = null"))
    }

    @Test
    fun `generateDto handles KDoc descriptions`() {
        val schema = SchemaDefinition(
            name = "Item",
            type = "object",
            description = "Top level class doc",
            properties = mapOf(
                "sku" to SchemaProperty("string", description = "The stock keeping unit")
            )
        )

        val text = generator.generateDto("com.inventory", schema).text

        assertTrue(text.contains("/** Top level class doc */"))
        assertTrue(text.contains("/** The stock keeping unit */"))
    }

    @Test
    fun `generateDto maps types correctly`() {
        val schema = SchemaDefinition(
            name = "TypesCheck",
            type = "object",
            properties = mapOf(
                "count" to SchemaProperty("integer"),
                "id" to SchemaProperty("integer", format = "int64"),
                "ratio" to SchemaProperty("number"),
                "flag" to SchemaProperty("boolean"),
                "tags" to SchemaProperty("array")
            )
        )

        val text = generator.generateDto("com.types", schema).text

        assertTrue(text.contains("val count: Int?"))
        assertTrue(text.contains("val id: Long?"))
        assertTrue(text.contains("val ratio: Double?"))
        assertTrue(text.contains("val flag: Boolean?"))
        assertTrue(text.contains("val tags: List<String>?"))
    }

    @Test
    fun `generateDto includes SerialName annotation`() {
        val schema = SchemaDefinition(
            name = "SnakeCase",
            type = "object",
            properties = mapOf(
                "user_id" to SchemaProperty("integer")
            )
        )

        val text = generator.generateDto("com.api", schema).text

        assertTrue(text.contains("@SerialName(\"user_id\")"))
        assertTrue(text.contains("val user_id: Int?"))
    }
}
