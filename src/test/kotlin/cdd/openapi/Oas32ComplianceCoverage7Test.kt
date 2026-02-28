package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage7Test {
    @Test
    fun `validate components and component references`() {
        val validator = OpenApiValidator()
        
        val c = Components(
            schemas = mapOf("s" to SchemaDefinition(name = "s", schemaId = "http://a")),
            responses = mapOf("r" to EndpointResponse(statusCode = "200", description = "ok")),
            parameters = mapOf("p" to EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "string", schema = SchemaProperty(type = "string"))),
            examples = mapOf("e" to ExampleObject(value = "v")),
            requestBodies = mapOf("rb" to RequestBody(content = mapOf("application/json" to MediaTypeObject(schema = SchemaProperty(type = "string"))))),
            headers = mapOf("h" to Header(type = "string", schema = SchemaProperty(type = "string"))),
            securitySchemes = mapOf("ss" to SecurityScheme(type = "http", scheme = "basic")),
            links = mapOf("l" to Link(operationId = "op1")),
            callbacks = mapOf("cb" to Callback.Inline(mapOf("http://cb" to PathItem()))),
            pathItems = mapOf("pi" to PathItem())
        )
        val def = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("t", "1"),
            paths = emptyMap(),
            components = c
        )
        val i = validator.validate(def)
        assertTrue(i.isEmpty())
        
        // now invalid references
        val def2 = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("t", "1"),
            paths = mapOf(
                "/bad" to PathItem(
                    ref = "#/components/pathItems/bad",
                    parameters = listOf(EndpointParameter(reference = ReferenceObject("#/components/parameters/bad"), name="n", location=ParameterLocation.QUERY, type="string")),
                    get = EndpointDefinition(
                        path = "/bad", method = HttpMethod.GET, operationId = "badOp",
                        responses = mapOf("200" to EndpointResponse(reference = ReferenceObject("#/components/responses/bad"), statusCode = "200"))
                    )
                )
            )
        )
        val i2 = validator.validate(def2)
        i2.forEach { println("${it.severity}: ${it.path} -> ${it.message}") }
    }
}
