package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage27Test {
    @Test
    fun `validate media type object ref and example errors again`() {
        val validator = OpenApiValidator()
        
        val c = Components(
            mediaTypes = mapOf(
                "application/json" to MediaTypeObject(
                    reference = ReferenceObject(ref = "#/components/mediaTypes/other"),
                    schema = SchemaProperty(types=setOf("string"))
                ),
                "other" to MediaTypeObject()
            )
        )
        
        val m2 = MediaTypeObject(example = ExampleObject(value = "a"), examples = mapOf("ex1" to ExampleObject(value = "b")))
        val m3 = MediaTypeObject(encoding = mapOf("e" to EncodingObject()), prefixEncoding = listOf(EncodingObject()))
        
        val def = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = c)
        
        val i1 = validator.validate(def)
        assertTrue(i1.any { it.message.contains("Media type with \$ref should not define other fields") })

        val path = PathItem(get = EndpointDefinition(path="/", method=HttpMethod.GET, operationId="o", responses = mapOf("200" to EndpointResponse(statusCode="200", description="d", content=mapOf("text/plain" to m2, "image/png" to m3)))))
        val def2 = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = mapOf("/p" to path))
        val i2 = validator.validate(def2)
        assertTrue(i2.any { it.message.contains("Media type must not define both example and examples") })
        assertTrue(i2.any { it.message.contains("Media type must not define encoding with prefixEncoding/itemEncoding") })
    }
}
