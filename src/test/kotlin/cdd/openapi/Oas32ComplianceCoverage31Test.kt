package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage31Test {
    @Test
    fun `validate path templating thoroughly`() {
        val validator = OpenApiValidator()
        
        val p1 = PathItem(
            parameters = listOf(EndpointParameter(name="missing", location=ParameterLocation.PATH, type="string", schema=SchemaProperty(type="string")))
        )
        val p2 = PathItem(
            get = EndpointDefinition(path="/p/{id}", method=HttpMethod.GET, operationId="o", parameters = listOf(EndpointParameter(name="missing2", location=ParameterLocation.PATH, type="string", schema=SchemaProperty(type="string"))))
        )
        
        val def = OpenApiDefinition(
            openapi = "3.2.0", info = Info("t", "1"), components = Components(),
            paths = mapOf(
                "/p/{id}/{id}" to p1, // dup template
                "/p/{id}" to p1, // missing template param from list
                "/p/{id}" to p2  // operation missing path param & extra operation param
            )
        )
        
        val i = validator.validate(def)
        assertTrue(i.any { it.message.contains("Path template parameter 'id' must not appear more than once") })
        assertTrue(i.any { it.message.contains("Path parameter 'missing' is not present in the path template") })
        assertTrue(i.any { it.message.contains("Missing path parameter 'id' for operation o") })
        assertTrue(i.any { it.message.contains("Path parameter 'missing2' is not present in the path template") })
    }
}
