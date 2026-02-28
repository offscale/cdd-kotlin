package cdd.openapi

import org.junit.jupiter.api.Test

class Oas32ComplianceCoverage9Test {
    @Test
    fun `validate mega schema properties internally`() {
        val validator = OpenApiValidator()
        
        val schemaDef = SchemaDefinition(
            name = "Mega",
            type = "object",
            properties = mapOf(
                "a" to SchemaProperty(
                    type = "string"
                )
            ),
            propertyNames = SchemaProperty(type = "string"),
            additionalProperties = SchemaProperty(type = "string"),
            dependentSchemas = mapOf("a" to SchemaProperty(type = "string")),
            patternProperties = mapOf("^b" to SchemaProperty(type = "string")),
            unevaluatedProperties = SchemaProperty(type = "string"),
            unevaluatedItems = SchemaProperty(type = "string"),
            contentSchema = SchemaProperty(type = "string"),
            oneOfSchemas = listOf(SchemaProperty(type = "object")),
            anyOfSchemas = listOf(SchemaProperty(type = "object")),
            allOfSchemas = listOf(SchemaProperty(type = "object")),
            not = SchemaProperty(type = "string"),
            ifSchema = SchemaProperty(type = "string"),
            thenSchema = SchemaProperty(type = "string"),
            elseSchema = SchemaProperty(type = "string")
        )

        val c = Components(schemas = mapOf("Mega" to schemaDef))
        val def = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = c)
        
        validator.validate(def)
    }
}
