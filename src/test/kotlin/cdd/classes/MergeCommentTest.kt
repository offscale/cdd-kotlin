package cdd.classes

import cdd.openapi.*
import cdd.classes.*
import cdd.routes.*
import cdd.docstrings.*
import cdd.shared.*
import cdd.scaffold.*


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DtoMergerCommentTest {
    @Test
    fun `preserves trailing comments and whitespace`() {
        val existing = """
            data class User(
                val id: Int // The user ID
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

        val result = DtoMerger().mergeDto(existing, schema)
        println("RESULT:\n$result")
    }
}
