package cdd.openapi

import org.junit.jupiter.api.Test

class Oas32ComplianceCoverage16Test {
    @Test
    fun `validate mega schema definition full`() {
        val validator = OpenApiValidator()
        
        val schemaDef = SchemaDefinition(
            name = "Mega",
            type = "object",
            properties = mapOf("a" to SchemaProperty(type = "string")),
            propertyNames = SchemaProperty(type = "string"),
            additionalProperties = SchemaProperty(type = "string"),
            dependentSchemas = mapOf("a" to SchemaProperty(type = "string")),
            unevaluatedProperties = SchemaProperty(type = "string"),
            unevaluatedItems = SchemaProperty(type = "string"),
            contentSchema = SchemaProperty(type = "string"),
            not = SchemaProperty(type = "string"),
            ifSchema = SchemaProperty(type = "string"),
            thenSchema = SchemaProperty(type = "string"),
            elseSchema = SchemaProperty(type = "string"),
            defs = mapOf("d" to SchemaProperty(type = "string")),
            items = SchemaProperty(type = "string"),
            prefixItems = listOf(SchemaProperty(type = "string")),
            contains = SchemaProperty(type = "string"),
            oneOfSchemas = listOf(SchemaProperty(type = "string")),
            anyOfSchemas = listOf(SchemaProperty(type = "string")),
            allOfSchemas = listOf(SchemaProperty(type = "string")),
            patternProperties = mapOf("p" to SchemaProperty(type = "string"))
        )

        val c = Components(schemas = mapOf("Mega" to schemaDef))
        val def = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = c)
        
        validator.validate(def)
    }
}
