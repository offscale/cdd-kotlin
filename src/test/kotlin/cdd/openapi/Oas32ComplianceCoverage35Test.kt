package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage35Test {
    @Test
    fun `validate parameter styles directly`() {
        val validator = OpenApiValidator()

        val p1 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "string", style = ParameterStyle.MATRIX, explode = true)
        val p2 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "string", style = ParameterStyle.LABEL)
        val p3 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "string", style = ParameterStyle.FORM)
        val p4 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "string", style = ParameterStyle.SIMPLE)
        val p5 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "string", style = ParameterStyle.SPACE_DELIMITED)
        val p6 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "string", style = ParameterStyle.PIPE_DELIMITED)
        val p7 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "string", style = ParameterStyle.DEEP_OBJECT)

        val issues = mutableListOf<OpenApiIssue>()
        validator.validateParameter(p1, "path", issues, null)
        validator.validateParameter(p2, "path", issues, null)
        validator.validateParameter(p3, "path", issues, null)
        validator.validateParameter(p4, "path", issues, null)
        validator.validateParameter(p5, "path", issues, null)
        validator.validateParameter(p6, "path", issues, null)
        validator.validateParameter(p7, "path", issues, null)
    }
}
