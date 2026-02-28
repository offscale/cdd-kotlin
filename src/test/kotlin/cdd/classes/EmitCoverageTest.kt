package cdd.classes

import cdd.openapi.*
import org.junit.jupiter.api.Test

class EmitCoverageTest {
    @Test
    fun `schemaPropertyToDocValue hits properties`() {
        val gen = DtoGenerator()
        gen.schemaPropertyToDocValue(SchemaProperty(
            schemaId = "id", schemaDialect = "schema", anchor = "a", dynamicAnchor = "da", comment = "c", format = "f",
            contentMediaType = "cmt", contentEncoding = "ce", minLength = 1, maxLength = 2, pattern = "p",
            minimum = 1.0, maximum = 2.0, multipleOf = 1.0, exclusiveMinimum = 1.0, exclusiveMaximum = 2.0,
            minItems = 1, maxItems = 2, uniqueItems = true, minProperties = 1, maxProperties = 2,
            items = SchemaProperty(type = "string"),
            prefixItems = listOf(SchemaProperty(type = "string")),
            contains = SchemaProperty(type = "string"), minContains = 1, maxContains = 2,
            properties = mapOf("p" to SchemaProperty(type = "string")),
            patternProperties = mapOf("pp" to SchemaProperty(type = "string")),
            propertyNames = SchemaProperty(type = "string"),
            additionalProperties = SchemaProperty(type = "string"),
            required = listOf("p"),
            defs = mapOf("d" to SchemaProperty(type = "string")),
            dependentRequired = mapOf("p" to listOf("q")),
            dependentSchemas = mapOf("p" to SchemaProperty(type = "string")),
            enumValues = listOf("e"), description = "d", title = "t", defaultValue = "dv", constValue = "cv",
            deprecated = true, readOnly = true, writeOnly = true,
            externalDocs = ExternalDocumentation("url", "desc"),
            discriminator = Discriminator("prop", mapOf("k" to "v"), defaultMapping = "k"),
            xml = Xml(name = "n", namespace = "ns", prefix = "p", attribute = true, wrapped = true),
            unevaluatedProperties = SchemaProperty(type = "string"),
            unevaluatedItems = SchemaProperty(type = "string"),
            contentSchema = SchemaProperty(type = "string"),
            oneOf = listOf(SchemaProperty(type = "string")),
            anyOf = listOf(SchemaProperty(type = "string")),
            allOf = listOf(SchemaProperty(type = "string")),
            not = SchemaProperty(type = "string"),
            ifSchema = SchemaProperty(type = "string"), thenSchema = SchemaProperty(type = "string"), elseSchema = SchemaProperty(type = "string"),
            customKeywords = mapOf("x-k" to "v")
        ))
        
        gen.schemaPropertyToDocValue(SchemaProperty(booleanSchema = true))
        gen.schemaPropertyToDocValue(SchemaProperty(ref = "r"))
        gen.schemaPropertyToDocValue(SchemaProperty(dynamicRef = "r"))
        gen.schemaPropertyToDocValue(SchemaProperty(types = setOf("string", "null")))
    }
}
