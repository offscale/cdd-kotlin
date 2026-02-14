package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import domain.ExternalDocumentation
import domain.Discriminator
import domain.Xml
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
    fun `generateDto resolves dynamicRef to dynamicAnchor types`() {
        val schema = SchemaDefinition(
            name = "Box",
            type = "object",
            defs = mapOf(
                "Payload" to SchemaProperty(type = "string", dynamicAnchor = "payload")
            ),
            properties = mapOf(
                "value" to SchemaProperty(dynamicRef = "#payload")
            ),
            required = listOf("value")
        )

        val text = generator.generateDto("com.dynamic", schema).text

        assertTrue(text.contains("val value: String"))
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
    fun `generateDto emits composition and conditional schema tags`() {
        val schema = SchemaDefinition(
            name = "Composed",
            type = "object",
            oneOf = listOf("Cat"),
            oneOfSchemas = listOf(SchemaProperty("string")),
            anyOf = listOf("#/components/schemas/Dog"),
            allOf = listOf("Base"),
            not = SchemaProperty("null"),
            ifSchema = SchemaProperty("string"),
            thenSchema = SchemaProperty("string", minLength = 2),
            elseSchema = SchemaProperty("string", maxLength = 1),
            additionalProperties = SchemaProperty(booleanSchema = false),
            properties = mapOf(
                "value" to SchemaProperty("string").copy(
                    oneOf = listOf(
                        SchemaProperty("string"),
                        SchemaProperty(ref = "#/components/schemas/Name")
                    ),
                    not = SchemaProperty("null"),
                    additionalProperties = SchemaProperty("string")
                )
            )
        )

        val text = generator.generateDto("com.example", schema).text

        assertTrue(text.contains("@oneOf"))
        assertTrue(text.contains("@anyOf"))
        assertTrue(text.contains("@allOf"))
        assertTrue(text.contains("@not"))
        assertTrue(text.contains("@if"))
        assertTrue(text.contains("@then"))
        assertTrue(text.contains("@else"))
        assertTrue(text.contains("@additionalProperties false"))
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
    fun `generateDto emits advanced schema tags`() {
        val schema = SchemaDefinition(
            name = "AdvancedDoc",
            type = "object",
            comment = "root comment",
            patternProperties = mapOf("^x-" to SchemaProperty("string")),
            propertyNames = SchemaProperty(types = setOf("string"), pattern = "^[a-z]+$"),
            dependentRequired = mapOf("credit_card" to listOf("billing_address")),
            dependentSchemas = mapOf(
                "shipping_address" to SchemaProperty(
                    types = setOf("object"),
                    required = listOf("country"),
                    properties = mapOf("country" to SchemaProperty("string"))
                )
            ),
            unevaluatedProperties = SchemaProperty(booleanSchema = false),
            unevaluatedItems = SchemaProperty("string"),
            contentSchema = SchemaProperty(
                types = setOf("object"),
                properties = mapOf("id" to SchemaProperty("integer"))
            ),
            properties = mapOf(
                "payload" to SchemaProperty(
                    types = setOf("object"),
                    comment = "payload comment",
                    patternProperties = mapOf("^data-" to SchemaProperty("string")),
                    propertyNames = SchemaProperty(types = setOf("string"), pattern = "^[a-z]+$"),
                    dependentRequired = mapOf("a" to listOf("b")),
                    dependentSchemas = mapOf(
                        "meta" to SchemaProperty(
                            types = setOf("object"),
                            required = listOf("id"),
                            properties = mapOf("id" to SchemaProperty("integer"))
                        )
                    ),
                    unevaluatedProperties = SchemaProperty(booleanSchema = false),
                    unevaluatedItems = SchemaProperty("string"),
                    contentSchema = SchemaProperty(
                        types = setOf("object"),
                        properties = mapOf("id" to SchemaProperty("integer"))
                    )
                )
            )
        )

        val text = generator.generateDto("com.docs", schema).text

        assertTrue(text.contains("@comment root comment"))
        assertTrue(text.contains("@patternProperties {\"^x-\""))
        assertTrue(text.contains("@propertyNames {\"type\":\"string\""))
        assertTrue(text.contains("@dependentRequired {\"credit_card\""))
        assertTrue(text.contains("@dependentSchemas {\"shipping_address\""))
        assertTrue(text.contains("@unevaluatedProperties false"))
        assertTrue(text.contains("@unevaluatedItems {\"type\":\"string\""))
        assertTrue(text.contains("@contentSchema {\"type\":\"object\""))

        assertTrue(text.contains("@comment payload comment"))
        assertTrue(text.contains("@patternProperties {\"^data-\""))
        assertTrue(text.contains("@dependentRequired {\"a\""))
    }

    @Test
    fun `generateDto emits custom keywords tag`() {
        val schema = SchemaDefinition(
            name = "CustomKeywords",
            type = "object",
            customKeywords = mapOf("vendorKeyword" to "alpha"),
            properties = mapOf(
                "name" to SchemaProperty(
                    type = "string",
                    customKeywords = mapOf("custom" to listOf("a", "b"))
                )
            )
        )

        val text = generator.generateDto("com.custom", schema).text

        assertTrue(text.contains("@keywords {\"vendorKeyword\":\"alpha\"}"))
        assertTrue(text.contains("@keywords {\"custom\":[\"a\",\"b\"]}"))
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
    fun `generateDto uses typealias for non-string enums and preserves values`() {
        val schema = SchemaDefinition(
            name = "Level",
            type = "integer",
            enumValues = listOf(1, 2, 3)
        )

        val text = generator.generateDto("com.enums", schema).text

        assertTrue(text.contains("typealias Level = Int"), "Non-string enums should not generate enum class")
        assertTrue(text.contains("@enum 1"))
        assertTrue(text.contains("@enum 2"))
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
    fun `generateDto creates Sealed Interface for inline oneOf schemas`() {
        val schema = SchemaDefinition(
            name = "InlinePoly",
            type = "object",
            oneOfSchemas = listOf(
                SchemaProperty(types = setOf("string")),
                SchemaProperty(types = setOf("integer"))
            ),
            properties = mapOf(
                "id" to SchemaProperty("integer")
            )
        )

        val text = generator.generateDto("com.poly", schema).text

        assertTrue(text.contains("sealed interface InlinePoly"))
        assertTrue(text.contains("val id: Int?"))
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
            example = mapOf("name" to "John"),
            properties = mapOf(
                "name" to SchemaProperty(type = "string", example = "John")
            )
        )

        val text = generator.generateDto("com.ex", schema).text

        assertTrue(text.contains("@example {\"name\":\"John\"}"), "Class example missing")
        assertTrue(text.contains("@example John"), "Property example missing")
    }

    @Test
    fun `generateDto includes keyed examples in KDoc`() {
        val schema = SchemaDefinition(
            name = "ExampleMap",
            type = "object",
            examples = mapOf(
                "valid" to mapOf("id" to 1),
                "invalid" to emptyMap<String, Any>()
            )
        )

        val text = generator.generateDto("com.ex", schema).text

        assertTrue(text.contains("@example valid: {\"id\":1}"))
        assertTrue(text.contains("@example invalid: {}"))
    }

    @Test
    fun `generateDto includes examples list in KDoc`() {
        val schema = SchemaDefinition(
            name = "ExampleList",
            type = "object",
            examplesList = listOf("alpha", "beta")
        )

        val text = generator.generateDto("com.ex", schema).text

        assertTrue(text.contains("@examples [\"alpha\",\"beta\"]"))
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
    fun `generateDto includes property external docs and discriminator`() {
        val schema = SchemaDefinition(
            name = "Profile",
            type = "object",
            properties = mapOf(
                "profile" to SchemaProperty(
                    types = setOf("object"),
                    externalDocs = ExternalDocumentation(
                        url = "https://example.com/props",
                        description = "Property docs"
                    ),
                    discriminator = Discriminator(
                        propertyName = "kind",
                        mapping = mapOf("user" to "#/components/schemas/User"),
                        defaultMapping = "#/components/schemas/User"
                    )
                )
            )
        )

        val text = generator.generateDto("com.docs", schema).text

        assertTrue(text.contains("@see https://example.com/props Property docs"))
        assertTrue(text.contains("@discriminator kind"))
        assertTrue(text.contains("@discriminatorMapping {\"user\":\"#/components/schemas/User\"}"))
        assertTrue(text.contains("@discriminatorDefault #/components/schemas/User"))
    }

    @Test
    fun `generateDto includes property examples list`() {
        val schema = SchemaDefinition(
            name = "ExampleProp",
            type = "object",
            properties = mapOf(
                "count" to SchemaProperty(
                    types = setOf("integer"),
                    examples = listOf(1, 2)
                )
            )
        )

        val text = generator.generateDto("com.examples", schema).text

        assertTrue(text.contains("@example example1: 1"))
        assertTrue(text.contains("@example example2: 2"))
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
            defaultValue = mapOf("id" to 1),
            constValue = mapOf("id" to 1),
            schemaId = "https://example.com/schemas/Annotated",
            schemaDialect = "https://json-schema.org/draft/2020-12/schema",
            anchor = "annotated",
            dynamicAnchor = "annotatedDyn",
            deprecated = true,
            readOnly = true,
            writeOnly = true,
            properties = mapOf(
                "id" to SchemaProperty(
                    type = "integer",
                    title = "Identifier",
                    defaultValue = 1,
                    constValue = 1,
                    schemaId = "https://example.com/schemas/Identifier",
                    schemaDialect = "https://json-schema.org/draft/2020-12/schema",
                    anchor = "id",
                    dynamicAnchor = "idDyn",
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
        assertTrue(text.contains("@schemaId https://example.com/schemas/Annotated"))
        assertTrue(text.contains("@schemaDialect https://json-schema.org/draft/2020-12/schema"))
        assertTrue(text.contains("@anchor annotated"))
        assertTrue(text.contains("@dynamicAnchor annotatedDyn"))
        assertTrue(text.contains("@readOnly"))
        assertTrue(text.contains("@writeOnly"))
        assertTrue(text.contains("@title Identifier"))
        assertTrue(text.contains("@default 1"))
        assertTrue(text.contains("@const 1"))
        assertTrue(text.contains("@schemaId https://example.com/schemas/Identifier"))
        assertTrue(text.contains("@schemaDialect https://json-schema.org/draft/2020-12/schema"))
        assertTrue(text.contains("@anchor id"))
        assertTrue(text.contains("@dynamicAnchor idDyn"))
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

    @Test
    fun `generateDto includes constraint tags in KDoc`() {
        val schema = SchemaDefinition(
            name = "Constrained",
            type = "object",
            minLength = 2,
            maxLength = 10,
            pattern = "^[a-z]+$",
            minimum = 1.0,
            maximum = 9.0,
            minItems = 1,
            maxItems = 5,
            uniqueItems = true,
            minProperties = 1,
            maxProperties = 4,
            properties = mapOf(
                "tags" to SchemaProperty(
                    type = "array",
                    items = SchemaProperty(type = "string"),
                    minItems = 2,
                    maxItems = 6,
                    uniqueItems = false
                )
            )
        )

        val text = generator.generateDto("com.constraints", schema).text

        assertTrue(text.contains("@minLength 2"))
        assertTrue(text.contains("@maxLength 10"))
        assertTrue(text.contains("@pattern ^[a-z]+$"))
        assertTrue(text.contains("@minimum 1.0"))
        assertTrue(text.contains("@maximum 9.0"))
        assertTrue(text.contains("@minItems 1"))
        assertTrue(text.contains("@maxItems 5"))
        assertTrue(text.contains("@uniqueItems true"))
        assertTrue(text.contains("@minProperties 1"))
        assertTrue(text.contains("@maxProperties 4"))

        assertTrue(text.contains("@minItems 2"))
        assertTrue(text.contains("@maxItems 6"))
        assertTrue(text.contains("@uniqueItems false"))
    }

    @Test
    fun `generateDto emits discriminator mapping tags`() {
        val schema = SchemaDefinition(
            name = "Pet",
            type = "object",
            oneOf = listOf("Cat", "Dog"),
            discriminator = Discriminator(
                propertyName = "petType",
                mapping = mapOf("dog" to "#/components/schemas/Dog"),
                defaultMapping = "OtherPet"
            )
        )

        val text = generator.generateDto("com.api", schema).text

        assertTrue(text.contains("@discriminator petType"))
        assertTrue(text.contains("@discriminatorMapping"))
        assertTrue(text.contains("#/components/schemas/Dog"))
        assertTrue(text.contains("@discriminatorDefault OtherPet"))
    }

    @Test
    fun `generateDto emits xml metadata tags`() {
        val schema = SchemaDefinition(
            name = "Person",
            type = "object",
            xml = Xml(
                name = "person",
                namespace = "https://example.com/schema",
                prefix = "ex",
                nodeType = "element"
            ),
            properties = mapOf(
                "id" to SchemaProperty(type = "string", xml = Xml(attribute = true, name = "pid")),
                "tags" to SchemaProperty(type = "array", xml = Xml(wrapped = true, name = "tags"))
            )
        )

        val text = generator.generateDto("com.xml", schema).text

        assertTrue(text.contains("@xmlName person"))
        assertTrue(text.contains("@xmlNamespace https://example.com/schema"))
        assertTrue(text.contains("@xmlPrefix ex"))
        assertTrue(text.contains("@xmlNodeType element"))
        assertTrue(text.contains("@xmlAttribute"))
        assertTrue(text.contains("@xmlWrapped"))
        assertTrue(text.contains("@xmlName pid"))
    }

    @Test
    fun `generateDto emits minContains and maxContains tags`() {
        val schema = SchemaDefinition(
            name = "Bag",
            type = "array",
            minContains = 1,
            maxContains = 3,
            items = SchemaProperty("string")
        )

        val text = generator.generateDto("com.constraints", schema).text

        assertTrue(text.contains("@minContains 1"))
        assertTrue(text.contains("@maxContains 3"))
    }

    @Test
    fun `generateDto emits contains, prefixItems, defs, and dynamicRef tags`() {
        val schema = SchemaDefinition(
            name = "Composite",
            type = "array",
            dynamicRef = "#/components/schemas/DynamicRoot",
            defs = mapOf("RootDef" to SchemaProperty(types = setOf("string"))),
            contains = SchemaProperty(types = setOf("integer")),
            prefixItems = listOf(
                SchemaProperty(types = setOf("string")),
                SchemaProperty(types = setOf("integer"))
            ),
            items = SchemaProperty("string"),
            properties = mapOf(
                "items" to SchemaProperty(
                    types = setOf("array"),
                    items = SchemaProperty(types = setOf("string")),
                    dynamicRef = "#/components/schemas/DynamicProp",
                    defs = mapOf("PropDef" to SchemaProperty(types = setOf("boolean"))),
                    contains = SchemaProperty(types = setOf("string")),
                    prefixItems = listOf(SchemaProperty(types = setOf("string")))
                )
            )
        )

        val text = generator.generateDto("com.constraints", schema).text

        assertTrue(text.contains("@dynamicRef #/components/schemas/DynamicRoot"))
        assertTrue(text.contains("@defs {\"RootDef\":{\"type\":\"string\"}}"))
        assertTrue(text.contains("@contains {\"type\":\"integer\"}"))
        assertTrue(text.contains("@prefixItems [{\"type\":\"string\"},{\"type\":\"integer\"}]"))

        assertTrue(text.contains("@dynamicRef #/components/schemas/DynamicProp"))
        assertTrue(text.contains("@defs {\"PropDef\":{\"type\":\"boolean\"}}"))
        assertTrue(text.contains("@contains {\"type\":\"string\"}"))
        assertTrue(text.contains("@prefixItems [{\"type\":\"string\"}]"))
    }
}
