package cdd.docstrings

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import cdd.openapi.*

class ParseCoverageTest {
    @Test
    fun `toComponents covers all metadata fields`() {
        val meta = OpenApiMetadata(
            securitySchemes = mapOf("s" to SecurityScheme(type="http", scheme="basic")),
            componentSchemas = mapOf("s" to SchemaDefinition(name="a")),
            componentExamples = mapOf("e" to ExampleObject()),
            componentLinks = mapOf("l" to Link()),
            componentCallbacks = mapOf("c" to Callback.Inline(emptyMap())),
            componentParameters = mapOf("p" to EndpointParameter(name="p", location=ParameterLocation.QUERY, type="string")),
            componentResponses = mapOf("r" to EndpointResponse(statusCode="200", description="d")),
            componentRequestBodies = mapOf("rb" to RequestBody(content=emptyMap())),
            componentHeaders = mapOf("h" to Header(type="string")),
            componentPathItems = mapOf("pi" to PathItem()),
            componentMediaTypes = mapOf("m" to MediaTypeObject()),
            componentsExtensions = mapOf("x-ext" to "v")
        )
        val result = meta.toComponents(Components())
        assertNotNull(result)
        
        val emptyMeta = OpenApiMetadata()
        val emptyResult = emptyMeta.toComponents()
        org.junit.jupiter.api.Assertions.assertTrue(emptyResult == null)
    }
}
