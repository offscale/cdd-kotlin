package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage6Test {
    @Test
    fun `validate paths object siblings`() {
        val validator = OpenApiValidator()
        
        val p = PathItem(
            ref = "#/components/pathItems/p1",
            summary = "sum",
            description = "desc",
            servers = listOf(Server(url = "http://bad {a}", variables = mapOf("a" to ServerVariable(default = "x")))),
            parameters = listOf(EndpointParameter(type = "string", name = "p", location = ParameterLocation.QUERY, schema = SchemaProperty(type = "string")))
        )
        val def = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("t", "1"),
            paths = mapOf("/p" to p)
        )
        val i = validator.validate(def)
        assertTrue(i.any { it.message.contains("should not define other fields") })
    }
}
