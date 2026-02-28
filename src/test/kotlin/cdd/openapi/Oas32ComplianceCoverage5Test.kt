package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage5Test {
    @Test
    fun `validate operation parameter examples`() {
        val validator = OpenApiValidator()
        
        val op = EndpointDefinition(
            path = "/p",
            method = HttpMethod.GET,
            operationId = "op1",
            responses = mapOf(
                "200" to EndpointResponse(
                    statusCode = "200",
                    description = "ok",
                    content = mapOf(
                        "application/json" to MediaTypeObject(
                            schema = SchemaProperty(type = "string"),
                            examples = mapOf("ex1" to ExampleObject(value = "bad", externalValue = "http://bad")),
                            encoding = mapOf("enc1" to EncodingObject(contentType = "text/plain", headers = mapOf("h" to Header(type = "string", schema = SchemaProperty(type = "string")))))
                        )
                    ),
                    headers = mapOf("h1" to Header(type = "string", schema = SchemaProperty(type = "string"))),
                    links = mapOf("l1" to Link(operationRef = "http://bad", operationId = "op2"))
                )
            ),
            parameters = listOf(
                EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "string", schema = SchemaProperty(type = "string")),
                EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "string", schema = SchemaProperty(type = "string")) // duplicate
            )
        )
        val def = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("t", "1"),
            paths = mapOf("/p" to PathItem(get = op))
        )
        val i = validator.validate(def)
        assertTrue(i.any { it.message.contains("Example must not define externalValue with value") })
        assertTrue(i.any { it.message.contains("Link must not define both operationId and operationRef") })
        assertTrue(i.any { it.message.contains("Duplicate parameter 'p'") })
    }
}
