package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage28Test {
    @Test
    fun `validate encoding object internal directly`() {
        val validator = OpenApiValidator()
        
        val enc = EncodingObject(
            headers = mapOf(
                "bad header" to Header(type = "string"),
                "content-type" to Header(type = "string")
            ),
            encoding = mapOf("nested" to EncodingObject()),
            prefixEncoding = listOf(EncodingObject()),
            itemEncoding = EncodingObject()
        )
        
        val c = Components(
            requestBodies = mapOf(
                "r" to RequestBody(content = mapOf("application/x-www-form-urlencoded" to MediaTypeObject(schema = SchemaProperty(types = setOf("object"), properties = mapOf("p" to SchemaProperty(types = setOf("string")))), encoding = mapOf("p" to enc))))
            )
        )
        val def = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = c)
        val i = validator.validate(def)
        
        assertTrue(i.any { it.message.contains("Encoding object must not define encoding with prefixEncoding") })
        assertTrue(i.any { it.message.contains("must be a valid HTTP header token") })
        assertTrue(i.any { it.message.contains("Encoding headers must not include Content-Type") })
    }
}
