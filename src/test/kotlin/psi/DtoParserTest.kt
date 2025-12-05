package psi

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.BeforeAll

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DtoParserTest {

    private val parser = DtoParser()

    @Test
    fun `parse extracts Data Class info correctly`() {
        val code = """
            package com.example
            
            /**
             * Represents a system user.
             */
            data class User(
                /** The primary key */
                val id: Long,
                val username: String?
            )
        """.trimIndent()

        val results = parser.parse(code)
        assertEquals(1, results.size)

        val schema = results[0]
        assertEquals("User", schema.name)
        assertEquals("object", schema.type)
        assertEquals("Represents a system user.", schema.description)

        // Properties
        assertEquals(2, schema.properties.size)

        // ID: Required, Long
        val idProp = schema.properties["id"]
        assertNotNull(idProp)
        assertEquals("integer", idProp?.type)
        assertEquals("int64", idProp?.format)
        assertEquals("The primary key", idProp?.description)
        assertTrue(schema.required.contains("id"))

        // Username: Nullable, String
        val userProp = schema.properties["username"]
        assertNotNull(userProp)
        assertEquals("string", userProp?.type)
        assertFalse(schema.required.contains("username"), "Nullable property should not be required")
    }

    @Test
    fun `parse handles SerialName annotation overrides`() {
        val code = """
            import kotlinx.serialization.SerialName
            
            data class ApiConfig(
                @SerialName("api_key")
                val key: String
            )
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertFalse(schema.properties.containsKey("key"), "Should use serial name")
        assertTrue(schema.properties.containsKey("api_key"), "Should find api_key")

        assertEquals("string", schema.properties["api_key"]?.type)
    }

    @Test
    fun `parse extracts Enum definitions`() {
        val code = """
            enum class Status {
                ACTIVE,
                INACTIVE
            }
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals("Status", schema.name)
        assertEquals("string", schema.type) // Enums simplified to string sets

        assertNotNull(schema.enumValues)
        assertTrue(schema.enumValues!!.contains("ACTIVE"))
        assertTrue(schema.enumValues!!.contains("INACTIVE"))
    }

    @Test
    fun `parse handles complex types and lists`() {
        val code = """
            data class Group(
                val members: List<User>,
                val meta: List<String>?
            )
        """.trimIndent()

        val schema = parser.parse(code).first()

        // members: Required, Array of Ref(User)
        val memberProp = schema.properties["members"]
        assertEquals("array", memberProp?.type)
        assertEquals("object", memberProp?.items?.type)
        assertEquals("User", memberProp?.items?.ref)
        assertTrue(schema.required.contains("members"))

        // meta: Optional, Array of String
        val metaProp = schema.properties["meta"]
        assertEquals("array", metaProp?.type)
        assertEquals("string", metaProp?.items?.type)
        assertFalse(schema.required.contains("meta"))
    }

    @Test
    fun `parse ignores non-data classes`() {
        val code = """
            class Logic {
                fun execute() {}
            }
            interface API {}
        """.trimIndent()

        val results = parser.parse(code)
        assertTrue(results.isEmpty())
    }
}
