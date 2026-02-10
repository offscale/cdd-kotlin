package psi

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

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
        assertTrue(schema.enumValues.contains("INACTIVE"))
    }

    @Test
    fun `parse extracts Enum definitions with SerialName`() {
        val code = """ 
            import kotlinx.serialization.SerialName
            enum class Sort { 
                @SerialName("asc") 
                Ascending, 
                @SerialName("desc") 
                Descending
            } 
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals("Sort", schema.name)
        val values = schema.enumValues!!
        assertEquals(2, values.size)
        // Should parse the SerialName values, NOT the Kotlin identifier names
        assertTrue(values.contains("asc"))
        assertTrue(values.contains("desc"))
        assertFalse(values.contains("Ascending"))
    }

    @Test
    fun `parse handles complex types and lists`() {
        val code = """ 
            data class Group( 
                val members: List<User>, 
                val meta: List<String>?,
                val attributes: Map<String, String>
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

        // attributes: Required, Map of String
        val attrProp = schema.properties["attributes"]
        assertEquals("object", attrProp?.type)
        assertEquals("string", attrProp?.additionalProperties?.type)
        assertTrue(schema.required.contains("attributes"))
    }

    @Test
    fun `parse extracts Map typealias`() {
        val code = """
            /**
             * Free-form metadata map.
             */
            typealias Metadata = Map<String, Int>
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals("Metadata", schema.name)
        assertEquals("object", schema.type)
        assertEquals("integer", schema.additionalProperties?.type)
        assertEquals("int32", schema.additionalProperties?.format)
        assertEquals("Free-form metadata map.", schema.description)
    }

    @Test
    fun `parse extracts primitive typealias`() {
        val code = """
            /**
             * Identifier as instant.
             */
            typealias UserId = Instant
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals("UserId", schema.name)
        assertEquals("string", schema.type)
        assertEquals("date-time", schema.format)
        assertEquals("Identifier as instant.", schema.description)
    }

    @Test
    fun `parse extracts array typealias`() {
        val code = """
            typealias Ids = List<Long>
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals("Ids", schema.name)
        assertEquals("array", schema.type)
        assertEquals("integer", schema.items?.type)
        assertEquals("int64", schema.items?.format)
    }

    @Test
    fun `parse extracts nullable primitive typealias`() {
        val code = """
            typealias MaybeCount = Int?
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals("MaybeCount", schema.name)
        assertTrue(schema.types.contains("null"))
        assertEquals("integer", schema.type)
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

    @Test
    fun `parse extracts Sealed Interface and Discriminator`() {
        val code = """ 
            import kotlinx.serialization.JsonClassDiscriminator
            import kotlinx.serialization.Serializable

            @Serializable
            @JsonClassDiscriminator("type") 
            sealed interface Shape { 
               val area: Double
            } 
        """.trimIndent()

        val results = parser.parse(code)
        assertEquals(1, results.size)

        val schema = results.first()
        assertEquals("Shape", schema.name)

        // UPDATED: Check for Discriminator Object properties
        assertNotNull(schema.discriminator)
        assertEquals("type", schema.discriminator?.propertyName)

        // oneOf is technically implied empty by current parser implementation but type is object
        assertEquals("object", schema.type)
        assertTrue(schema.properties.containsKey("area"))
    }

    @Test
    fun `parse extracts KDoc examples and external docs`() {
        val code = """ 
            /**
             * Example entity.
             * @see https://example.com/docs Entity docs
             * @example simpleExample
             * @example keyed: {"id": 1}
             */
            data class Example(
                /**
                 * @example propExample
                 */
                val id: String
            )
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals("Example entity.", schema.description)
        assertEquals("simpleExample", schema.example)
        assertEquals("{\"id\": 1}", schema.examples?.get("keyed"))
        assertEquals("https://example.com/docs", schema.externalDocs?.url)
        assertEquals("Entity docs", schema.externalDocs?.description)
        assertEquals("propExample", schema.properties["id"]?.example)
    }

    @Test
    fun `parse sealed interface without discriminator`() {
        val code = """ 
            sealed interface NoDisc {
                val name: String
            }
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals("NoDisc", schema.name)
        assertEquals(null, schema.discriminator)
        assertTrue(schema.properties.containsKey("name"))
    }

    @Test
    fun `parse captures supertypes for allOf`() {
        val code = """ 
            import kotlinx.serialization.Serializable

            data class Child(
                val id: String
            ) : Parent, Serializable
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertTrue(schema.allOf.contains("Parent"))
        assertFalse(schema.allOf.contains("Serializable"))
    }

    @Test
    fun `parse extracts schema annotations and deprecation`() {
        val code = """
            /**
             * @title Annotated schema
             * @default {"id":1}
             * @const {"id":1}
             * @deprecated
             * @readOnly
             * @writeOnly
             */
            @Deprecated("Use NewAnnotated")
            data class Annotated(
                /**
                 * @title Identifier
                 * @default 1
                 * @const 1
                 * @deprecated
                 * @readOnly
                 * @writeOnly
                 */
                @Deprecated("Old id")
                val id: Int
            )
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals("Annotated schema", schema.title)
        assertEquals("{\"id\":1}", schema.defaultValue)
        assertEquals("{\"id\":1}", schema.constValue)
        assertTrue(schema.deprecated)
        assertTrue(schema.readOnly)
        assertTrue(schema.writeOnly)

        val idProp = schema.properties["id"]
        assertEquals("Identifier", idProp?.title)
        assertEquals("1", idProp?.defaultValue)
        assertEquals("1", idProp?.constValue)
        assertTrue(idProp?.deprecated == true)
        assertTrue(idProp?.readOnly == true)
        assertTrue(idProp?.writeOnly == true)
    }
}
