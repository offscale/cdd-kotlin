package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage23Test {
    @Test
    fun `validate headers extra rules`() {
        val validator = OpenApiValidator()
        
        val h1 = Header(type = "string") // neither schema nor content
        val h2 = Header(type = "string", schema = SchemaProperty(type = "string"), content = mapOf("a" to MediaTypeObject(schema = SchemaProperty(type = "string")))) // both
        val h3 = Header(type = "string", content = mapOf("a" to MediaTypeObject(schema = SchemaProperty(type = "string")), "b" to MediaTypeObject(schema = SchemaProperty(type = "string")))) // mult content
        val h4 = Header(type = "string", content = mapOf("a" to MediaTypeObject(schema = SchemaProperty(type = "string"))), style = ParameterStyle.FORM) // style with content
        val h5 = Header(type = "string", content = mapOf("a" to MediaTypeObject(schema = SchemaProperty(type = "string"))), explode = true) // explode with content
        val h6 = Header(type = "string", schema = SchemaProperty(type = "string"), example = ExampleObject(value = "a"), examples = mapOf("e" to ExampleObject(value = "b"))) // ex + exs
        
        val c = Components(headers = mapOf("h1" to h1, "h2" to h2, "h3" to h3, "h4" to h4, "h5" to h5, "h6" to h6))
        val def = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = c)
        
        val i = validator.validate(def)
        i.forEach { println("${it.severity}: ${it.path} -> ${it.message}") }

        assertTrue(i.any { it.message.contains("must define either schema or content") })
        assertTrue(i.any { it.message.contains("must not define both schema and content") })
        assertTrue(i.any { it.message.contains("contain exactly one media type") })
        assertTrue(i.any { it.message.contains("style must not be set when content is used") })
        assertTrue(i.any { it.message.contains("explode must not be set when content is used") })
        assertTrue(i.any { it.message.contains("must not define both example and examples") })
    }
}
