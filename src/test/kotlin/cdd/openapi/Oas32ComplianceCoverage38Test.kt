package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage38Test {
    @Test
    fun `validate empty root ignores everything`() {
        val validator = OpenApiValidator()
        
        val emptyDef = OpenApiDefinition(openapi = "2.0", info = Info("t", "1"), paths = emptyMap())
        assertTrue(validator.validate(emptyDef).isEmpty())
        
        val noRootElementsDef = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), webhooks = emptyMap(), components = null)
        val i = validator.validate(noRootElementsDef)
        assertTrue(i.any { it.message.contains("requires at least one of: paths, webhooks, or components") })
    }
}
