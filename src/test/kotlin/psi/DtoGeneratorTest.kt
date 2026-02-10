package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import domain.ExternalDocumentation
import domain.Discriminator
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
                "name" to SchemaProperty(type = "string"),
                "age" to SchemaProperty(type = "integer")
            ),
            required = listOf("name")
        )

        val file = generator.generateDto("com.example", schema)
        val text = file.text

        assertTrue(text.contains("package com.example"))
        assertTrue(text.contains("data class User("))
        assertTrue(text.contains("val name: String"))
    }

    @Test
    fun `generateDto handles Nullable properties`() {
        val schema = SchemaDefinition(
            name = "Product",
            type = "object",
            properties = mapOf(
                "id" to SchemaProperty(type = "integer"),
                "description" to SchemaProperty(type = "string")
            ),
            required = listOf("id")
        )

        val text = generator.generateDto("com.shop", schema).text

        assertTrue(text.contains("val id: Int"))
        assertFalse(text.contains("val id: Int?"))
        // Nullable implies default null
        assertTrue(text.contains("val description: String? = null"))
    }

    @Test
    fun `generateDto handles KDoc descriptions`() {
        val schema = SchemaDefinition(
            name = "Item",
            type = "object",
            description = "Top level class doc",
            properties = mapOf(
                "sku" to SchemaProperty(type = "string", description = "The stock keeping unit")
            )
        )

        val text = generator.generateDto("com.inventory", schema).text

        assertTrue(text.contains(" * Top level class doc\n"))
        assertTrue(text.contains("     * The stock keeping unit\n"))
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
                "tags" to SchemaProperty("array", items = SchemaProperty("string")),
                "labels" to SchemaProperty(
                    type = "object",
                    additionalProperties = SchemaProperty("string")
                )
            )
        )

        val text = generator.generateDto("com.types", schema).text

        assertTrue(text.contains("val count: Int?"))
        assertTrue(text.contains("val id: Long?"))
        assertTrue(text.contains("val ratio: Double?"))
        assertTrue(text.contains("val flag: Boolean?"))
        assertTrue(text.contains("val tags: List<String>?"))
        assertTrue(text.contains("val labels: Map<String, String>?"))
    }

    @Test
    fun `generateDto creates Map typealias for additionalProperties schema`() {
        val schema = SchemaDefinition(
            name = "Attributes",
            type = "object",
            additionalProperties = SchemaProperty("string"),
            description = "Dynamic attributes map"
        )

        val text = generator.generateDto("com.maps", schema).text

        assertTrue(text.contains("typealias Attributes = Map<String, String>"))
        assertTrue(text.contains("Dynamic attributes map"))
    }

    @Test
    fun `generateDto creates typealias for primitive schema`() {
        val schema = SchemaDefinition(
            name = "UserId",
            type = "string",
            format = "date-time",
            description = "Time-based identifier"
        )

        val text = generator.generateDto("com.alias", schema).text

        assertTrue(text.contains("typealias UserId = Instant"))
        assertTrue(text.contains("import kotlinx.datetime.Instant"))
        assertTrue(text.contains("Time-based identifier"))
    }

    @Test
    fun `generateDto creates typealias for array schema`() {
        val schema = SchemaDefinition(
            name = "Ids",
            type = "array",
            items = SchemaProperty("integer", format = "int64")
        )

        val text = generator.generateDto("com.alias", schema).text

        assertTrue(text.contains("typealias Ids = List<Long>"))
    }

    @Test
    fun `generateDto creates nullable typealias when null is allowed`() {
        val schema = SchemaDefinition(
            name = "MaybeIds",
            type = "array",
            types = setOf("array", "null"),
            items = SchemaProperty("integer", format = "int64")
        )

        val text = generator.generateDto("com.alias", schema).text

        assertTrue(text.contains("typealias MaybeIds = List<Long>?"))
    }

    @Test
    fun `generateDto supports extended types`() {
        val schema = SchemaDefinition(
            name = "Extended",
            type = "object",
            properties = mapOf(
                "created" to SchemaProperty("string", format = "date-time"),
                "birthday" to SchemaProperty("string", format = "date"),
                "blob" to SchemaProperty("string", format = "byte"),
                "raw" to SchemaProperty("string", format = "binary")
            )
        )

        val text = generator.generateDto("com.ext", schema).text

        assertTrue(text.contains("import kotlinx.datetime.Instant"))
        assertTrue(text.contains("import kotlinx.datetime.LocalDate"))

        assertTrue(text.contains("val created: Instant?"))
        assertTrue(text.contains("val birthday: LocalDate?"))
        assertTrue(text.contains("val blob: ByteArray?"))
        assertTrue(text.contains("val raw: ByteArray?"))
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

    @Test
    fun `generateDto includes External Documentation as KDoc`() {
        val schema = SchemaDefinition(
            name = "DocItem",
            type = "object",
            description = "Item with docs",
            externalDocs = ExternalDocumentation("More info", "https://example.com")
        )

        val text = generator.generateDto("com.doc", schema).text

        assertTrue(text.contains("Item with docs"))
        assertTrue(text.contains("@see https://example.com More info"))
    }

    @Test
    fun `generateDto handles Reference Object URIs`() {
        val schema = SchemaDefinition(
            name = "RefContainer",
            type = "object",
            properties = mapOf(
                "internal" to SchemaProperty("object", ref = "#/components/schemas/User"),
                "external" to SchemaProperty("object", ref = "./models/Address.json"),
                "legacy" to SchemaProperty("object", ref = "SimpleType")
            )
        )

        val text = generator.generateDto("com.ref", schema).text

        assertTrue(text.contains("val internal: User?"))
        assertTrue(text.contains("val external: Address?"))
        assertTrue(text.contains("val legacy: SimpleType?"))
    }

    @Test
    fun `generateDto creates Enum class with SerialName mappings`() {
        val schema = SchemaDefinition(
            name = "SortArgs",
            type = "string",
            enumValues = listOf("ascending", "descending")
        )

        val text = generator.generateDto("com.enums", schema).text

        assertTrue(text.contains("enum class SortArgs"))
        assertTrue(text.contains("@SerialName(\"ascending\")"))
        assertTrue(text.contains("Ascending"))
    }

    @Test
    fun `generateDto sanitizes enum identifiers`() {
        val schema = SchemaDefinition(
            name = "TrickyEnum",
            type = "string",
            enumValues = listOf("first-value", "123", "Value With Space")
        )

        val text = generator.generateDto("com.enums", schema).text

        assertTrue(text.contains("FirstValue"))
        assertTrue(text.contains("_123"))
        assertTrue(text.contains("ValueWithSpace"))
    }

    @Test
    fun `generateDto creates Sealed Interface for oneOf`() {
        val schema = SchemaDefinition(
            name = "PolymorphicPet",
            type = "object",
            // UPDATED: Using Discriminator Object
            discriminator = Discriminator(propertyName = "petType"),
            oneOf = listOf("Dog", "Cat"),
            properties = mapOf(
                "id" to SchemaProperty("integer")
            )
        )

        val text = generator.generateDto("com.poly", schema).text

        assertTrue(text.contains("sealed interface PolymorphicPet"))
        assertTrue(text.contains("@JsonClassDiscriminator(\"petType\")"))
        assertTrue(text.contains("val id: Int?"))
        assertTrue(text.contains("import kotlinx.serialization.JsonClassDiscriminator"))
    }

    @Test
    fun `generateDto creates Data Class with inheritance`() {
        val schema = SchemaDefinition(
            name = "Dog",
            type = "object",
            allOf = listOf("Pet", "Mammal"),
            properties = mapOf(
                "breed" to SchemaProperty("string")
            )
        )

        val text = generator.generateDto("com.poly", schema).text

        assertTrue(text.contains("data class Dog("))
        assertTrue(text.contains("val breed: String?"))
        assertTrue(text.contains(" : Pet, Mammal"))
    }

    @Test
    fun `generateDto includes examples in KDoc`() {
        val schema = SchemaDefinition(
            name = "ExampleUser",
            type = "object",
            example = "{ \"name\": \"John\" }",
            properties = mapOf(
                "name" to SchemaProperty(type = "string", example = "John")
            )
        )

        val text = generator.generateDto("com.ex", schema).text

        assertTrue(text.contains("@example { \"name\": \"John\" }"), "Class example missing")
        assertTrue(text.contains("@example John"), "Property example missing")
    }

    @Test
    fun `generateDto includes keyed examples in KDoc`() {
        val schema = SchemaDefinition(
            name = "ExampleMap",
            type = "object",
            examples = mapOf(
                "valid" to "{\"id\": 1}",
                "invalid" to "{}"
            )
        )

        val text = generator.generateDto("com.ex", schema).text

        assertTrue(text.contains("@example valid: {\"id\": 1}"))
        assertTrue(text.contains("@example invalid: {}"))
    }

    @Test
    fun `generateDto includes content metadata in property KDoc`() {
        val schema = SchemaDefinition(
            name = "Blob",
            type = "object",
            properties = mapOf(
                "payload" to SchemaProperty(
                    type = "string",
                    contentMediaType = "application/pdf",
                    contentEncoding = "base64"
                )
            )
        )

        val text = generator.generateDto("com.media", schema).text

        assertTrue(text.contains("@contentMediaType application/pdf"))
        assertTrue(text.contains("@contentEncoding base64"))
    }

    @Test
    fun `generateDto includes schema-level content metadata`() {
        val schema = SchemaDefinition(
            name = "InlineBlob",
            type = "string",
            contentMediaType = "application/pdf",
            contentEncoding = "base64"
        )

        val text = generator.generateDto("com.media", schema).text

        assertTrue(text.contains("@contentMediaType application/pdf"))
        assertTrue(text.contains("@contentEncoding base64"))
    }

    @Test
    fun `generateDto includes schema annotations and deprecation`() {
        val schema = SchemaDefinition(
            name = "Annotated",
            type = "object",
            title = "Annotated schema",
            defaultValue = "{\"id\":1}",
            constValue = "{\"id\":1}",
            deprecated = true,
            readOnly = true,
            writeOnly = true,
            properties = mapOf(
                "id" to SchemaProperty(
                    type = "integer",
                    title = "Identifier",
                    defaultValue = "1",
                    constValue = "1",
                    deprecated = true,
                    readOnly = true,
                    writeOnly = true
                )
            )
        )

        val text = generator.generateDto("com.annotations", schema).text

        assertTrue(text.contains("@Deprecated(\"Deprecated\")"))
        assertTrue(text.contains("@title Annotated schema"))
        assertTrue(text.contains("@default {\"id\":1}"))
        assertTrue(text.contains("@const {\"id\":1}"))
        assertTrue(text.contains("@readOnly"))
        assertTrue(text.contains("@writeOnly"))
        assertTrue(text.contains("@title Identifier"))
        assertTrue(text.contains("@default 1"))
        assertTrue(text.contains("@const 1"))
    }

    @Test
    fun `generateDto creates sealed interface without discriminator`() {
        val schema = SchemaDefinition(
            name = "Shape",
            type = "object",
            oneOf = listOf("Circle", "Square"),
            properties = mapOf(
                "kind" to SchemaProperty("string")
            )
        )

        val text = generator.generateDto("com.poly", schema).text

        assertTrue(text.contains("sealed interface Shape"))
        assertFalse(text.contains("@JsonClassDiscriminator"))
    }

    @Test
    fun `generateDto sanitizes empty enum values to Unknown`() {
        val schema = SchemaDefinition(
            name = "EdgeEnum",
            type = "string",
            enumValues = listOf("!!!")
        )

        val text = generator.generateDto("com.enums", schema).text

        assertTrue(text.contains("Unknown"))
    }

    @Test
    fun `generateDto supports OAS 3_2 Dual Types (array null)`() {
        val schema = SchemaDefinition(
            name = "ModernOas",
            type = "object",
            properties = mapOf(
                // type: ["string", "null"]
                "nullableString" to SchemaProperty(types = setOf("string", "null")),
                // type: ["integer"]
                "strictInt" to SchemaProperty(types = setOf("integer"))
            ),
            // Even if required list includes nullableString, type: ["null"] forces nullability in Kotlin
            required = listOf("nullableString", "strictInt")
        )

        val text = generator.generateDto("com.oas32", schema).text

        assertTrue(text.contains("val nullableString: String? = null"), "Should be nullable due to dual type")
        assertTrue(text.contains("val strictInt: Int"), "Should be non-null")
        assertFalse(text.contains("val strictInt: Int?"))
    }
}
