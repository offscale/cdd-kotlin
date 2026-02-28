package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage34Test {
    @Test
    fun `validate parameter size directly`() {
        val validator = OpenApiValidator()

        val p1 = EndpointParameter(
            name = "p1", location = ParameterLocation.QUERY, type = "string",
            schema = SchemaProperty(type = "string"),
            example = ExampleObject(value = "1"),
            examples = mapOf("ex1" to ExampleObject(value = "2")),
            content = mapOf(
                "application/json" to MediaTypeObject(),
                "text/plain" to MediaTypeObject()
            )
        )
        val p2 = EndpointParameter(
            name = "p2", location = ParameterLocation.QUERY, type = "string"
        )
        val p3 = EndpointParameter(
            location = ParameterLocation.HEADER, type = "string",
            schema = SchemaProperty(type = "string"), allowEmptyValue = true, name = "Accept"
        )
        val p4 = EndpointParameter(
            name = "p4", location = ParameterLocation.HEADER, type = "string",
            schema = SchemaProperty(type = "string"), allowEmptyValue = true
        )

        val issues = mutableListOf<OpenApiIssue>()
        validator.validateParameter(p1, "path", issues, null)
        validator.validateParameter(p2, "path", issues, null)
        validator.validateParameter(p3, "path", issues, null)
        validator.validateParameter(p4, "path", issues, null)
    }
}
