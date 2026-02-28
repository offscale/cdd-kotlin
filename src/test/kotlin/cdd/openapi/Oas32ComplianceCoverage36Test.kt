package cdd.openapi

import org.junit.jupiter.api.Test

class Oas32ComplianceCoverage36Test {
    @Test
    fun `validate parameter styles edge cases directly`() {
        val validator = OpenApiValidator()
        
        val p1 = EndpointParameter(name = "p", location = ParameterLocation.HEADER, type = "string", style = ParameterStyle.MATRIX)
        val p2 = EndpointParameter(name = "p", location = ParameterLocation.PATH, type = "string", style = ParameterStyle.FORM)
        val p3 = EndpointParameter(name = "p", location = ParameterLocation.COOKIE, type = "string", style = ParameterStyle.MATRIX)

        val issues = mutableListOf<OpenApiIssue>()
        validator.validateParameter(p1, "path", issues, null)
        validator.validateParameter(p2, "path", issues, null)
        validator.validateParameter(p3, "path", issues, null)
    }
}
