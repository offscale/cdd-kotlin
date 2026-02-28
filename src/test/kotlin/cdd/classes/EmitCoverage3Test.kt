package cdd.classes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class EmitCoverage3Test {
    @Test
    fun `buildPropertyKDoc covers fields`() {
        val gen = DtoGenerator()
        
        val doc1 = gen.buildPropertyKDoc(SchemaProperty(
            types = setOf("string"),
            description = "d",
            schemaId = "id", schemaDialect = "dialect", anchor = "a", dynamicAnchor = "da", dynamicRef = "dr", comment = "c",
            minLength = 1, maxLength = 2, pattern = "p",
            minimum = 1.0, maximum = 2.0, multipleOf = 1.0, exclusiveMinimum = 1.0, exclusiveMaximum = 2.0,
            minItems = 1, maxItems = 2, uniqueItems = true, minProperties = 1, maxProperties = 2,
            minContains = 1, maxContains = 2, contains = SchemaProperty(types = setOf("string")),
            prefixItems = listOf(SchemaProperty(types = setOf("string"))),
            xml = Xml(name = "x"),
            patternProperties = mapOf("p" to SchemaProperty(types = setOf("string"))),
            propertyNames = SchemaProperty(types = setOf("string")),
            dependentRequired = mapOf("a" to listOf("b")),
            dependentSchemas = mapOf("a" to SchemaProperty(types = setOf("string"))),
            unevaluatedProperties = SchemaProperty(types = setOf("string")),
            unevaluatedItems = SchemaProperty(types = setOf("string")),
            contentSchema = SchemaProperty(types = setOf("string")),
            customKeywords = mapOf("x-k" to "v"),
            additionalProperties = SchemaProperty(types = setOf("string")),
            oneOf = listOf(SchemaProperty(types = setOf("string"))),
            anyOf = listOf(SchemaProperty(types = setOf("string"))),
            allOf = listOf(SchemaProperty(types = setOf("string"))),
            not = SchemaProperty(types = setOf("string")),
            ifSchema = SchemaProperty(types = setOf("string")),
            thenSchema = SchemaProperty(types = setOf("string")),
            elseSchema = SchemaProperty(types = setOf("string"))
        ))
        assertTrue(doc1.contains("d"))
    }
}
