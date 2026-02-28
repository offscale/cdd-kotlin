package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage39Test {
    @Test
    fun `validate schema property internal directly`() {
        val validator = OpenApiValidator()
        val m = OpenApiValidator::class.java.declaredMethods.first { it.name == "validateSchemaPropertyInternal\$cdd_kotlin" && it.parameterCount == 7 }
        m.isAccessible = true
        
        val StateClass = Class.forName("cdd.openapi.OpenApiValidator\$SchemaValidationState")
        val stateConstructor = StateClass.constructors.first()
        stateConstructor.isAccessible = true
        val state = stateConstructor.newInstance(java.util.Collections.newSetFromMap(java.util.IdentityHashMap<SchemaDefinition, Boolean>()), java.util.Collections.newSetFromMap(java.util.IdentityHashMap<SchemaProperty, Boolean>()), "http://d", emptySet<String>())
        
        val scope = DynamicAnchorScope(java.util.IdentityHashMap())
        
        val issues = mutableListOf<OpenApiIssue>()
        m.invoke(validator, SchemaProperty(schemaDialect = "unknown"), "path", issues, state, scope, "http://c", emptySet<String>())
        
        assertTrue(issues.any { it.message.contains("is not a recognized schema dialect") })
    }
}
