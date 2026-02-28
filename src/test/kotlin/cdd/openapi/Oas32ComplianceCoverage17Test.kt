package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage17Test {
    @Test
    fun `validate xml errors`() {
        val validator = OpenApiValidator()
        val c = Components(
            schemas = mapOf(
                "s1" to SchemaDefinition(name = "x", 
                    type = "string",
                    xml = Xml(nodeType = "array", attribute = true, wrapped = true, name = "x", namespace = "n", prefix = "p")
                ),
                "s2" to SchemaDefinition(name = "x", 
                    type = "array",
                    xml = Xml(nodeType = "array", attribute = true, wrapped = true, name = "x", namespace = "n", prefix = "p")
                ),
                "s3" to SchemaDefinition(name = "x", 
                    type = "object",
                    xml = Xml(nodeType = "array", attribute = true, wrapped = true, name = "x", namespace = "n", prefix = "p")
                )
            )
        )
        val def = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = c)
        val i = validator.validate(def)
        i.forEach { println(it.message) }
    }
}
