package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage30Test {
    @Test
    fun `validate media type object reference handling`() {
        val validator = OpenApiValidator()
        
        val m = MediaTypeObject(
            reference = ReferenceObject(ref = "#/components/mediaTypes/m1"),
            schema = SchemaProperty(type="string")
        )
        val m2 = MediaTypeObject(
            ref = "#/components/mediaTypes/m1"
        )
        
        val c = Components(
            mediaTypes = mapOf(
                "m1" to MediaTypeObject()
            )
        )
        
        val def = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("t", "1"),
            paths = emptyMap(),
            components = Components(
                responses = mapOf(
                    "r1" to EndpointResponse(
                        statusCode = "200", description = "d",
                        content = mapOf("text/plain" to m, "application/json" to m2)
                    )
                ),
                mediaTypes = mapOf("m1" to MediaTypeObject())
            )
        )
        
        val i = validator.validate(def)
        assertTrue(i.any { it.message.contains("Media type with \$ref should not define other fields") })
    }
}
