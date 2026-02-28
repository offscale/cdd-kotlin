package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class Oas32ComplianceCoverage8Test {
    @Test
    fun `validate schema resolution percentage decode and hexToInt`() {
        val validator = OpenApiValidator()
        
        val c = Components(
            schemas = mapOf(
                "AB" to SchemaDefinition(name="AB", schemaId = "http://a", type="string"),
                "C" to SchemaDefinition(name="C", ref = "#/components/schemas/A%42")
            )
        )
        
        val def = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("t", "1"),
            paths = emptyMap(),
            components = c
        )
        
        val i = validator.validate(def)
        i.forEach { println("${it.severity}: ${it.path} -> ${it.message}") }
        assertTrue(i.isEmpty())
        
        // hit hex error
        val c2 = Components(
            schemas = mapOf(
                "C" to SchemaDefinition(name="C", ref = "#/components/schemas/A%XX")
            )
        )
        
        val def2 = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("t", "1"),
            paths = emptyMap(),
            components = c2
        )
        val i2 = validator.validate(def2)
        assertTrue(i2.any { it.message.contains("does not resolve") })
    }
}
