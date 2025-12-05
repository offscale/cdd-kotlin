package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DtoMergerTest {

    private val merger = DtoMerger()

    @Test
    fun `mergeDto adds missing nullable field preserving existing structure`() {
        val existing = """
            package com.test
            
            import kotlinx.serialization.Serializable
            
            /**
             * Existing comments.
             */
            @Serializable
            data class User(
                // Do not touch
                val name: String
            ) {
               fun helper() = "Test"
            }
        """.trimIndent()

        val schema = SchemaDefinition(
            name = "User",
            type = "object",
            properties = mapOf(
                "name" to SchemaProperty("string"),
                "email" to SchemaProperty("string") // Missing
            ),
            required = listOf("name")
        )

        val result = merger.mergeDto(existing, schema)

        // Check new field exists with annotation and default value
        assertTrue(result.contains("@kotlinx.serialization.SerialName(\"email\") val email: String? = null"), "Missing field text incorrect")

        // Check existing lines exact match (whitespace check)
        assertTrue(result.contains("// Do not touch"), "Comments removed")
        assertTrue(result.contains("fun helper() = \"Test\""), "Body content removed")
    }

    @Test
    fun `mergeDto ignores existing fields`() {
        val existing = """
            data class User(
                val id: Int
            )
        """.trimIndent()

        val schema = SchemaDefinition(
            name = "User",
            type = "object",
            properties = mapOf(
                "id" to SchemaProperty("integer"),
                "score" to SchemaProperty("integer")
            ),
            required = listOf("id", "score")
        )

        val result = merger.mergeDto(existing, schema)

        // Should not duplicate id
        val idCount = result.split("val id").size - 1
        assertEquals(1, idCount, "Existing field was duplicated")

        // Should add score
        assertTrue(result.contains("val score: Int"))
    }

    @Test
    fun `mergeDto handles multiple missing fields`() {
        val existing = "data class Group(val id: String)"
        val schema = SchemaDefinition(
            name = "Group",
            type = "object",
            properties = mapOf(
                "id" to SchemaProperty("string"),
                "a" to SchemaProperty("boolean"),
                "b" to SchemaProperty("boolean")
            ),
            required = listOf("id", "a", "b")
        )

        val result = merger.mergeDto(existing, schema)

        assertTrue(result.contains("val a: Boolean"))
        assertTrue(result.contains("val b: Boolean"))
    }
}
