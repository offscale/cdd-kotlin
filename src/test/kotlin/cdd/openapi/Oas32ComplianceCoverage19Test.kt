package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage19Test {
    @Test
    fun `validate components iteration`() {
        val validator = OpenApiValidator()
        val c = Components(
            schemas = mapOf("invalid key !" to SchemaDefinition(name="a")),
            responses = mapOf("invalid key !" to EndpointResponse(statusCode="200", description="d")),
            parameters = mapOf("invalid key !" to EndpointParameter(name="p", location=ParameterLocation.QUERY, type="string")),
            requestBodies = mapOf("invalid key !" to RequestBody(content=emptyMap())),
            headers = mapOf("invalid key !" to Header(type="string")),
            securitySchemes = mapOf("invalid key !" to SecurityScheme(type="http", scheme="basic")),
            examples = mapOf("invalid key !" to ExampleObject()),
            links = mapOf("invalid key !" to Link()),
            callbacks = mapOf("invalid key !" to Callback.Inline(emptyMap())),
            pathItems = mapOf("invalid key !" to PathItem()),
            mediaTypes = mapOf("invalid key !" to MediaTypeObject())
        )
        val def = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = c)
        val i = validator.validate(def)
        assertTrue(i.any { it.message.contains("Component keys must match") })
        
        // Also add references to components
        val c2 = Components(
            links = mapOf("l1" to Link()),
            callbacks = mapOf("cb1" to Callback.Inline(emptyMap())),
            pathItems = mapOf("pi1" to PathItem())
        )
        val def2 = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = c2)
        validator.validate(def2)
    }
}
