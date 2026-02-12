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
    fun `parse extracts custom keywords from KDoc`() {
        val code = """
            /**
             * @keywords {"vendorKeyword":"alpha"}
             */
            data class Item(
                /**
                 * @keywords {"custom":[1,2]}
                 */
                val name: String
            )
        """.trimIndent()

        val schema = parser.parse(code).first()
        assertEquals("alpha", schema.customKeywords["vendorKeyword"])
        val nameProp = schema.properties["name"]
        assertEquals(listOf(1, 2), nameProp?.customKeywords?.get("custom"))
    }

    @Test
    fun `parse preserves custom keywords inside schema tags`() {
        val code = """
            /**
             * @contains {"type":"string","extra":42}
             */
            data class Sample(
                val items: List<String>
            )
        """.trimIndent()

        val schema = parser.parse(code).first()
        assertEquals(42, (schema.contains?.customKeywords?.get("extra") as? Number)?.toInt())
    }

    @Test
    fun `parse extracts enum values from KDoc tags`() {
        val code = """
            /**
             * @enum 1
             * @enum 2
             * @enum {"k":"v"}
             */
            typealias Level = Int
        """.trimIndent()

        val schema = parser.parse(code).first()
        assertEquals(listOf(1, 2, mapOf("k" to "v")), schema.enumValues)
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
                 * @see https://example.com/props Property docs
                 * @discriminator kind
                 * @discriminatorMapping {"user":"#/components/schemas/User"}
                 * @discriminatorDefault #/components/schemas/User
                 */
                val id: String
            )
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals("Example entity.", schema.description)
        assertEquals("simpleExample", schema.example)
        val keyedExample = schema.examples?.get("keyed") as? Map<*, *>
        assertEquals(1, keyedExample?.get("id"))
        assertEquals("https://example.com/docs", schema.externalDocs?.url)
        assertEquals("Entity docs", schema.externalDocs?.description)
        assertEquals("propExample", schema.properties["id"]?.example)
        assertEquals("https://example.com/props", schema.properties["id"]?.externalDocs?.url)
        assertEquals("Property docs", schema.properties["id"]?.externalDocs?.description)
        assertEquals("kind", schema.properties["id"]?.discriminator?.propertyName)
        assertEquals(
            "#/components/schemas/User",
            schema.properties["id"]?.discriminator?.mapping?.get("user")
        )
        assertEquals(
            "#/components/schemas/User",
            schema.properties["id"]?.discriminator?.defaultMapping
        )
    }

    @Test
    fun `parse extracts schema-level examples list`() {
        val code = """
            /**
             * @examples ["alpha", "beta"]
             */
            data class ExampleList(
                val name: String
            )
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals(listOf("alpha", "beta"), schema.examplesList)
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
             * @schemaId https://example.com/schemas/Annotated
             * @schemaDialect https://json-schema.org/draft/2020-12/schema
             * @anchor annotated
             * @dynamicAnchor annotatedDyn
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
                 * @schemaId https://example.com/schemas/Identifier
                 * @schemaDialect https://json-schema.org/draft/2020-12/schema
                 * @anchor id
                 * @dynamicAnchor idDyn
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
        val defaultValue = schema.defaultValue as? Map<*, *>
        val constValue = schema.constValue as? Map<*, *>
        assertEquals(1, defaultValue?.get("id"))
        assertEquals(1, constValue?.get("id"))
        assertEquals("https://example.com/schemas/Annotated", schema.schemaId)
        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.schemaDialect)
        assertEquals("annotated", schema.anchor)
        assertEquals("annotatedDyn", schema.dynamicAnchor)
        assertTrue(schema.deprecated)
        assertTrue(schema.readOnly)
        assertTrue(schema.writeOnly)

        val idProp = schema.properties["id"]
        assertEquals("Identifier", idProp?.title)
        assertEquals(1, idProp?.defaultValue)
        assertEquals(1, idProp?.constValue)
        assertEquals("https://example.com/schemas/Identifier", idProp?.schemaId)
        assertEquals("https://json-schema.org/draft/2020-12/schema", idProp?.schemaDialect)
        assertEquals("id", idProp?.anchor)
        assertEquals("idDyn", idProp?.dynamicAnchor)
        assertTrue(idProp?.deprecated == true)
        assertTrue(idProp?.readOnly == true)
        assertTrue(idProp?.writeOnly == true)
    }

    @Test
    fun `parse extracts schema constraint tags`() {
        val code = """
            /**
             * @minLength 2
             * @maxLength 10
             * @pattern ^[a-z]+$
             * @minimum 1
             * @maximum 9
             * @minItems 1
             * @maxItems 5
             * @uniqueItems
             * @minProperties 1
             * @maxProperties 4
             */
            data class Constrained(
                /**
                 * @minLength 1
                 * @maxLength 20
                 * @pattern ^[A-Z]+$
                 * @minimum 0
                 * @maximum 100
                 * @minItems 2
                 * @maxItems 6
                 * @uniqueItems false
                 * @minProperties 0
                 * @maxProperties 8
                 */
                val tags: List<String>
            )
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals(2, schema.minLength)
        assertEquals(10, schema.maxLength)
        assertEquals("^[a-z]+$", schema.pattern)
        assertEquals(1.0, schema.minimum)
        assertEquals(9.0, schema.maximum)
        assertEquals(1, schema.minItems)
        assertEquals(5, schema.maxItems)
        assertEquals(true, schema.uniqueItems)
        assertEquals(1, schema.minProperties)
        assertEquals(4, schema.maxProperties)

        val tagsProp = schema.properties["tags"]
        assertEquals(1, tagsProp?.minLength)
        assertEquals(20, tagsProp?.maxLength)
        assertEquals("^[A-Z]+$", tagsProp?.pattern)
        assertEquals(0.0, tagsProp?.minimum)
        assertEquals(100.0, tagsProp?.maximum)
        assertEquals(2, tagsProp?.minItems)
        assertEquals(6, tagsProp?.maxItems)
        assertEquals(false, tagsProp?.uniqueItems)
        assertEquals(0, tagsProp?.minProperties)
        assertEquals(8, tagsProp?.maxProperties)
    }

    @Test
    fun `parse extracts content metadata tags`() {
        val code = """
            /**
             * @contentMediaType application/pdf
             * @contentEncoding base64
             */
            typealias PdfBlob = String

            data class Payload(
                /**
                 * @contentMediaType image/png
                 * @contentEncoding base64url
                 */
                val data: ByteArray
            )
        """.trimIndent()

        val schemas = parser.parse(code)
        val pdf = schemas.first { it.name == "PdfBlob" }
        val payload = schemas.first { it.name == "Payload" }

        assertEquals("application/pdf", pdf.contentMediaType)
        assertEquals("base64", pdf.contentEncoding)

        val dataProp = payload.properties["data"]
        assertEquals("image/png", dataProp?.contentMediaType)
        assertEquals("base64url", dataProp?.contentEncoding)
    }

    @Test
    fun `parse extracts property examples list`() {
        val code = """
            data class ExampleHost(
                /**
                 * @example example1: 1
                 * @example example2: 2
                 */
                val count: Int
            )
        """.trimIndent()

        val schema = parser.parse(code).first()
        val countProp = schema.properties["count"]

        assertEquals(listOf(1, 2), countProp?.examples)
        assertEquals(null, countProp?.example)
    }

    @Test
    fun `parse extracts discriminator and xml metadata from KDoc`() {
        val code = """
            /**
             * @discriminator petType
             * @discriminatorMapping {"dog":"#/components/schemas/Dog"}
             * @discriminatorDefault OtherPet
             * @xmlName pet
             * @xmlNamespace https://example.com/schema
             * @xmlPrefix ex
             * @xmlNodeType element
             */
            data class Pet(
                /**
                 * @xmlAttribute
                 * @xmlName pid
                 */
                val id: String,
                /**
                 * @xmlWrapped
                 * @xmlName tags
                 */
                val tags: List<String>
            )
        """.trimIndent()

        val schema = parser.parse(code).first()

        val discriminator = schema.discriminator
        assertEquals("petType", discriminator?.propertyName)
        assertEquals("#/components/schemas/Dog", discriminator?.mapping?.get("dog"))
        assertEquals("OtherPet", discriminator?.defaultMapping)

        val xml = schema.xml
        assertEquals("pet", xml?.name)
        assertEquals("https://example.com/schema", xml?.namespace)
        assertEquals("ex", xml?.prefix)
        assertEquals("element", xml?.nodeType)

        val idProp = schema.properties["id"]
        assertEquals(true, idProp?.xml?.attribute)
        assertEquals("pid", idProp?.xml?.name)

        val tagsProp = schema.properties["tags"]
        assertEquals(true, tagsProp?.xml?.wrapped)
        assertEquals("tags", tagsProp?.xml?.name)
    }

    @Test
    fun `parse extracts advanced schema tags`() {
        val code = """
            /**
             * @comment root comment
             * @patternProperties {"^x-":{"type":"string"}}
             * @propertyNames {"type":"string","pattern":"^[a-z]+$"}
             * @dependentRequired {"credit_card":["billing_address"]}
             * @dependentSchemas {"shipping_address":{"type":"object","required":["country"],"properties":{"country":{"type":"string"}}}}
             * @unevaluatedProperties false
             * @unevaluatedItems {"type":"string"}
             * @contentSchema {"type":"object","properties":{"id":{"type":"integer"}}}
             */
            data class Advanced(
                /**
                 * @comment payload comment
                 * @patternProperties {"^data-":{"type":"string"}}
                 * @propertyNames {"type":"string","pattern":"^[a-z]+$"}
                 * @dependentRequired {"a":["b"]}
                 * @dependentSchemas {"meta":{"type":"object","required":["id"],"properties":{"id":{"type":"integer"}}}}
                 * @unevaluatedProperties false
                 * @unevaluatedItems {"type":"string"}
                 * @contentSchema {"type":"object","properties":{"id":{"type":"integer"}}}
                 */
                val payload: Map<String, String>
            )
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals("root comment", schema.comment)
        assertEquals("string", schema.patternProperties["^x-"]?.type)
        assertEquals("^[a-z]+$", schema.propertyNames?.pattern)
        assertEquals(listOf("billing_address"), schema.dependentRequired["credit_card"])
        assertEquals("string", schema.dependentSchemas["shipping_address"]?.properties?.get("country")?.type)
        assertEquals(false, schema.unevaluatedProperties?.booleanSchema)
        assertEquals("string", schema.unevaluatedItems?.type)
        assertEquals("integer", schema.contentSchema?.properties?.get("id")?.type)

        val payload = schema.properties["payload"]
        assertEquals("payload comment", payload?.comment)
        assertEquals("string", payload?.patternProperties?.get("^data-")?.type)
        assertEquals(listOf("b"), payload?.dependentRequired?.get("a"))
        assertEquals("integer", payload?.dependentSchemas?.get("meta")?.properties?.get("id")?.type)
        assertEquals(false, payload?.unevaluatedProperties?.booleanSchema)
        assertEquals("string", payload?.unevaluatedItems?.type)
    }

    @Test
    fun `parse extracts minContains and maxContains tags`() {
        val code = """
            /**
             * @minContains 1
             * @maxContains 3
             */
            typealias Bag = List<String>

            data class Container(
                /**
                 * @minContains 2
                 * @maxContains 4
                 */
                val items: List<String>
            )
        """.trimIndent()

        val results = parser.parse(code)
        val bag = results.first { it.name == "Bag" }
        val container = results.first { it.name == "Container" }

        assertEquals(1, bag.minContains)
        assertEquals(3, bag.maxContains)

        val itemsProp = container.properties["items"]
        assertEquals(2, itemsProp?.minContains)
        assertEquals(4, itemsProp?.maxContains)
    }

    @Test
    fun `parse extracts contains, prefixItems, defs, and dynamicRef tags`() {
        val code = """
            /**
             * @dynamicRef #/components/schemas/DynamicRoot
             * @defs {"RootDef":{"type":"string"}}
             * @contains {"type":"integer"}
             * @prefixItems [{"type":"string"},{"type":"integer"}]
             */
            data class Composite(
                /**
                 * @dynamicRef #/components/schemas/DynamicProp
                 * @defs {"PropDef":{"type":"boolean"}}
                 * @contains {"type":"string"}
                 * @prefixItems [{"type":"string"}]
                 */
                val items: List<String>
            )
        """.trimIndent()

        val schema = parser.parse(code).first()

        assertEquals("#/components/schemas/DynamicRoot", schema.dynamicRef)
        assertEquals("string", schema.defs["RootDef"]?.type)
        assertEquals("integer", schema.contains?.type)
        assertEquals(2, schema.prefixItems.size)
        assertEquals("string", schema.prefixItems[0].type)
        assertEquals("integer", schema.prefixItems[1].type)

        val prop = schema.properties["items"]
        assertNotNull(prop)
        assertEquals("#/components/schemas/DynamicProp", prop?.dynamicRef)
        assertEquals("boolean", prop?.defs?.get("PropDef")?.type)
        assertEquals("string", prop?.contains?.type)
        assertEquals(1, prop?.prefixItems?.size)
        assertEquals("string", prop?.prefixItems?.first()?.type)
    }
}
