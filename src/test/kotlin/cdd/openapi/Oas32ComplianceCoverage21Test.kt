package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage21Test {
    @Test
    fun `validate hasPathItemSiblings fully`() {
        val validator = OpenApiValidator()
        
        // hit all boolean cases in hasPathItemSiblings
        val p1 = PathItem(ref = "someRef", summary = "s")
        val p2 = PathItem(ref = "someRef", description = "d")
        val p3 = PathItem(ref = "someRef", get = EndpointDefinition(path="/", method=HttpMethod.GET, operationId="op"))
        val p4 = PathItem(ref = "someRef", put = EndpointDefinition(path="/", method=HttpMethod.PUT, operationId="op"))
        val p5 = PathItem(ref = "someRef", post = EndpointDefinition(path="/", method=HttpMethod.POST, operationId="op"))
        val p6 = PathItem(ref = "someRef", delete = EndpointDefinition(path="/", method=HttpMethod.DELETE, operationId="op"))
        val p7 = PathItem(ref = "someRef", options = EndpointDefinition(path="/", method=HttpMethod.OPTIONS, operationId="op"))
        val p8 = PathItem(ref = "someRef", head = EndpointDefinition(path="/", method=HttpMethod.HEAD, operationId="op"))
        val p9 = PathItem(ref = "someRef", patch = EndpointDefinition(path="/", method=HttpMethod.PATCH, operationId="op"))
        val p10 = PathItem(ref = "someRef", trace = EndpointDefinition(path="/", method=HttpMethod.TRACE, operationId="op"))
        val p11 = PathItem(ref = "someRef", query = EndpointDefinition(path="/", method=HttpMethod.QUERY, operationId="op"))
        val p12 = PathItem(ref = "someRef", additionalOperations = mapOf("GET" to EndpointDefinition(path="/", method=HttpMethod.CUSTOM, operationId="op")))
        val p13 = PathItem(ref = "someRef", parameters = listOf(EndpointParameter(name="p", location=ParameterLocation.QUERY, type="string")))
        val p14 = PathItem(ref = "someRef", servers = listOf(Server(url="http://a")))
        val p15 = PathItem(ref = "someRef", extensions = mapOf("x-e" to "v"))
        
        val paths = mapOf(
            "/p1" to p1, "/p2" to p2, "/p3" to p3, "/p4" to p4, "/p5" to p5,
            "/p6" to p6, "/p7" to p7, "/p8" to p8, "/p9" to p9, "/p10" to p10,
            "/p11" to p11, "/p12" to p12, "/p13" to p13, "/p14" to p14, "/p15" to p15
        )
        
        val def = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = paths)
        val i = validator.validate(def)
        assertTrue(i.any { it.message.contains("Path Item with \$ref should not define other fields") })
        
        // Verify we hit all conditions by asserting many warnings exist
        assertTrue(i.count { it.message.contains("Path Item with \$ref should not define other fields") } >= 15)
    }
}
