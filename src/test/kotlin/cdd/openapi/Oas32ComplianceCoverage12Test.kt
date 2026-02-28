package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage12Test {
    @Test
    fun `validate links directly`() {
        val validator = OpenApiValidator()
        
        val p = PathItem(
            get = EndpointDefinition(
                path = "/p",
                method = HttpMethod.GET,
                operationId = "op1",
                responses = mapOf("200" to EndpointResponse(
                        statusCode = "200",
                        description = "desc",
                        links = mapOf(
                            "l1" to Link(operationId = "badOp"),
                            "l2" to Link(operationRef = "#/bad/ref"),
                            "l3" to Link(reference = ReferenceObject("#/components/links/l4")),
                            "l4" to Link(ref = "#/components/links/l4", server = Server(url = "http://a")),
                            "l5" to Link(operationId = "op1", parameters = mapOf("p" to "{\$request.query.b}"))
                        )
                    )
                )
            )
        )
        
        val c = Components(
            responses = mapOf(
                "r1" to EndpointResponse(
                    statusCode = "200",
                    description = "desc",
                    links = mapOf("cl1" to Link(operationId = "badOp2"))
                )
            ),
            links = mapOf(
                "l4" to Link(operationRef = "#/components/responses")
            )
        )
        
        val def = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("t", "1"),
            paths = mapOf("/p" to p),
            components = c
        )
        
        val i = validator.validate(def)
        assertTrue(i.any { it.message.contains("does not match any known operationId") })
        assertTrue(i.any { it.message.contains("does not resolve to a known operation") })
        assertTrue(i.any { it.message.contains("Link with \$ref should not define other fields") })
    }
}
