package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage45Test {
    @Test
    fun `validate path item`() {
        val validator = OpenApiValidator()
        
        val p1 = EndpointParameter(name = "p1", location = ParameterLocation.QUERY, type = "string")
        val p2 = EndpointParameter(name = "p1", location = ParameterLocation.QUERY, type = "string") // Duplicate
        
        val item = PathItem(
            parameters = listOf(p1, p2),
            ref = "#/components/pathItems/a",
            description = "has sibling"
        )
        
        val issues = mutableListOf<OpenApiIssue>()
        validator.validatePathItem("/a", item, "path", issues, null, false)
        
        assertTrue(issues.any { it.message.contains("should not define other fields") })
        
        // Also test validatePathItemParameters which handles the duplicates
        validator.validatePathItemParameters(item, "path.parameters", issues, null)
        assertTrue(issues.any { it.message.contains("Duplicate parameter") })
        
        // Test remaining let blocks in validatePathItem by providing operations
        val opItem = PathItem(
            get = EndpointDefinition(path="p", method=HttpMethod.GET, operationId="o", responses=mapOf("200" to EndpointResponse(description="ok", statusCode="200"))),
            put = EndpointDefinition(path="p", method=HttpMethod.GET, operationId="o", responses=mapOf("200" to EndpointResponse(description="ok", statusCode="200"))),
            post = EndpointDefinition(path="p", method=HttpMethod.GET, operationId="o", responses=mapOf("200" to EndpointResponse(description="ok", statusCode="200"))),
            delete = EndpointDefinition(path="p", method=HttpMethod.GET, operationId="o", responses=mapOf("200" to EndpointResponse(description="ok", statusCode="200"))),
            options = EndpointDefinition(path="p", method=HttpMethod.GET, operationId="o", responses=mapOf("200" to EndpointResponse(description="ok", statusCode="200"))),
            head = EndpointDefinition(path="p", method=HttpMethod.GET, operationId="o", responses=mapOf("200" to EndpointResponse(description="ok", statusCode="200"))),
            patch = EndpointDefinition(path="p", method=HttpMethod.GET, operationId="o", responses=mapOf("200" to EndpointResponse(description="ok", statusCode="200"))),
            trace = EndpointDefinition(path="p", method=HttpMethod.GET, operationId="o", responses=mapOf("200" to EndpointResponse(description="ok", statusCode="200"))),
            query = EndpointDefinition(path="p", method=HttpMethod.GET, operationId="o", responses=mapOf("200" to EndpointResponse(description="ok", statusCode="200"))),
            additionalOperations = mapOf("GET" to EndpointDefinition(path="p", method=HttpMethod.GET, operationId="o", responses=mapOf("200" to EndpointResponse(description="ok", statusCode="200"))), "B A D" to EndpointDefinition(path="p", method=HttpMethod.GET, operationId="o", responses=mapOf("200" to EndpointResponse(description="ok", statusCode="200"))))
        )
        validator.validatePathItem("/b", opItem, "path2", issues, null, true)
    }
}
