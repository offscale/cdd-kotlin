package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage26Test {
    @Test
    fun `validate xml missing properties`() {
        val validator = OpenApiValidator()
        
        val c = Components(
            schemas = mapOf(
                "s1" to SchemaDefinition(name="a", 
                    types = setOf("array"),
                    xml = Xml(nodeType = "attribute")
                ),
                "s2" to SchemaDefinition(name="a", 
                    types = setOf("string"),
                    xml = Xml(nodeType = "text", name = "ignoredName")
                )
            )
        )
        
        val def = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = c)
        val i = validator.validate(def)
        i.forEach { println(it.message) }
        assertTrue(i.any { it.message.contains("not valid for array schemas") })
        assertTrue(i.any { it.message.contains("is ignored for nodeType") })
    }
}
