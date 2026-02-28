package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage43Test {
    @Test
    fun `validate path item params`() {
        val validator = OpenApiValidator()
        
        val p1 = EndpointParameter(name = "p1", location = ParameterLocation.QUERY, type = "string")
        val p2 = EndpointParameter(name = "p1", location = ParameterLocation.QUERY, type = "string") // Duplicate
        
        val item = PathItem(
            parameters = listOf(p1, p2)
        )
        
        val issues = mutableListOf<OpenApiIssue>()
        validator.validatePathItemParameters(item, "path", issues, null)
        
        assertTrue(issues.any { it.message.contains("Duplicate parameter") })
    }
}
