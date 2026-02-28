package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage13Test {
    @Test
    fun `validate parameters coverage`() {
        val validator = OpenApiValidator()

        val p1 = EndpointParameter(
            name = "p1", location = ParameterLocation.QUERYSTRING, type = "string",
            schema = SchemaProperty(type = "string"),
            style = ParameterStyle.FORM, explode = true, allowReserved = true
        )
        val p2 = EndpointParameter(
            name = "p2", location = ParameterLocation.QUERYSTRING, type = "string"
        )
        val p3 = EndpointParameter(
            name = "p3", location = ParameterLocation.QUERY, type = "string",
            style = ParameterStyle.SPACE_DELIMITED, explode = true
        )
        val p4 = EndpointParameter(
            name = "p4", location = ParameterLocation.QUERY, type = "string",
            style = ParameterStyle.PIPE_DELIMITED, explode = true
        )
        val p5 = EndpointParameter(
            name = "p5", location = ParameterLocation.QUERY, type = "object",
            style = ParameterStyle.DEEP_OBJECT, schema = SchemaProperty(types = setOf("string"))
        )
        val p6 = EndpointParameter(
            name = "p6", location = ParameterLocation.PATH, type = "string",
            style = ParameterStyle.FORM
        )
        val p7 = EndpointParameter(
            name = "p7", location = ParameterLocation.QUERY, type = "string",
            content = mapOf(
                "a" to MediaTypeObject(schema = SchemaProperty(types = setOf("string"))),
                "b" to MediaTypeObject(schema = SchemaProperty(types = setOf("string")))
            ),
            style = ParameterStyle.FORM, explode = true, allowReserved = true
        )

        val def = OpenApiDefinition(
            openapi = "3.2.0", info = Info("t", "1"),
            paths = mapOf(
                "/p" to PathItem(
                    get = EndpointDefinition(
                        path = "/p", method = HttpMethod.GET, operationId = "op1",
                        parameters = listOf(p1, p2, p3, p4, p5, p6, p7)
                    )
                )
            )
        )

        val i = validator.validate(def)
        assertTrue(i.any { it.message.contains("querystring parameters must use content instead of schema") })
        assertTrue(i.any { it.message.contains("querystring parameters must define content") })
        assertTrue(i.any { it.message.contains("querystring parameters must not use style/explode/allowReserved") })
        assertTrue(i.any { it.message.contains("spaceDelimited style does not support explode=true") })
        assertTrue(i.any { it.message.contains("pipeDelimited style does not support explode=true") })
        assertTrue(i.any { it.message.contains("deepObject style only applies to object parameters") })
        assertTrue(i.any { it.message.contains("is not allowed for PATH") })
        assertTrue(i.any { it.message.contains("Parameter content must contain exactly one media type") })
        assertTrue(i.any { it.message.contains("Parameters using content must not define style/explode/allowReserved") })
    }
}
