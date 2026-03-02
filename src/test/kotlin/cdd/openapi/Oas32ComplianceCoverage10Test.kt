package cdd.openapi

import org.junit.jupiter.api.Test
import java.util.IdentityHashMap

class Oas32ComplianceCoverage10Test {
    @Test
    fun `validate mega schema properties internally with lists`() {
        println("OAS 3.2.0 test run!!!"); val validator = OpenApiValidator()
        
        val p = SchemaProperty(
            types = setOf("object"),
            propertyNames = SchemaProperty(types = setOf("string")),
            additionalProperties = SchemaProperty(types = setOf("string")),
            dependentSchemas = mapOf("a" to SchemaProperty(types = setOf("string"))),
            unevaluatedProperties = SchemaProperty(types = setOf("string")),
            unevaluatedItems = SchemaProperty(types = setOf("string")),
            contentSchema = SchemaProperty(types = setOf("string")),
            not = SchemaProperty(types = setOf("string")),
            ifSchema = SchemaProperty(types = setOf("string")),
            thenSchema = SchemaProperty(types = setOf("string")),
            elseSchema = SchemaProperty(types = setOf("string"))
        )

        val p2 = SchemaProperty(
            types = setOf("object"),
            allOf = listOf(SchemaProperty(types = setOf("string"))),
            anyOf = listOf(SchemaProperty(types = setOf("string"))),
            oneOf = listOf(SchemaProperty(types = setOf("string")))
        )

        val issues = mutableListOf<OpenApiIssue>()
        val constructor = Class.forName("cdd.openapi.OpenApiValidator\$SchemaValidationState").declaredConstructors.first { it.parameterCount == 4 }
        constructor.isAccessible = true
        val state = constructor.newInstance(mutableSetOf<SchemaDefinition>(), mutableSetOf<SchemaProperty>(), "dialect", emptySet<String>())
        
        val scope = DynamicAnchorScope(IdentityHashMap())
        
        val m = OpenApiValidator::class.java.declaredMethods.first { it.name == "validateSchemaPropertyInternal\$cdd_kotlin" && it.parameterCount == 7 }
        m.isAccessible = true
        
        m.invoke(validator, p, "path", issues, state, scope, "dialect", emptySet<String>())
        m.invoke(validator, SchemaProperty(booleanSchema = true), "path", issues, state, scope, "dialect", emptySet<String>())
        m.invoke(validator, p2, "path", issues, state, scope, "dialect", emptySet<String>())
    }
}
