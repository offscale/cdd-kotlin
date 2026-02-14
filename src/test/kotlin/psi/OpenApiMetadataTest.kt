package psi

import domain.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiMetadataTest {

    @Test
    fun `toMetadata copies root and component metadata`() {
        val definition = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("Meta API", "1.0"),
            jsonSchemaDialect = "https://spec.openapis.org/oas/3.1/dialect/base",
            self = "https://example.com/openapi",
            servers = listOf(Server(url = "https://api.example.com")),
            security = emptyList(),
            securityExplicitEmpty = true,
            tags = listOf(Tag(name = "alpha")),
            externalDocs = ExternalDocumentation("docs", "https://example.com/docs"),
            extensions = mapOf("x-root" to "root"),
            pathsExtensions = mapOf("x-paths" to "paths"),
            pathsExplicitEmpty = true,
            webhooks = mapOf(
                "onPing" to PathItem(
                    post = EndpointDefinition(
                        path = "/",
                        method = HttpMethod.POST,
                        operationId = "onPing",
                        responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
                    )
                )
            ),
            webhooksExtensions = mapOf("x-webhooks" to "hooks"),
            webhooksExplicitEmpty = true,
            components = Components(
                schemas = mapOf(
                    "Extra" to SchemaDefinition(name = "Extra", type = "object")
                ),
                securitySchemes = mapOf(
                    "ApiKey" to SecurityScheme(type = "apiKey", name = "X-API-KEY", `in` = "header")
                ),
                examples = mapOf("Example" to ExampleObject(summary = "example")),
                links = mapOf("Link" to Link(operationId = "getUser")),
                callbacks = mapOf("Callback" to Callback.Inline()),
                parameters = mapOf(
                    "Limit" to EndpointParameter(
                        name = "limit",
                        type = "integer",
                        location = ParameterLocation.QUERY,
                        isRequired = false
                    )
                ),
                responses = mapOf("Ok" to EndpointResponse(statusCode = "200", description = "ok")),
                requestBodies = mapOf("Body" to RequestBody(description = "body")),
                headers = mapOf("X-Header" to Header(type = "string")),
                pathItems = mapOf("PathItem" to PathItem(summary = "component")),
                mediaTypes = mapOf("application/json" to MediaTypeObject(schema = SchemaProperty("string"))),
                extensions = mapOf("x-components" to true)
            )
        )

        val metadata = definition.toMetadata(includeComponentSchemas = true)

        assertEquals("3.2.0", metadata.openapi)
        assertEquals("https://spec.openapis.org/oas/3.1/dialect/base", metadata.jsonSchemaDialect)
        assertEquals("https://example.com/openapi", metadata.self)
        assertEquals("Meta API", metadata.info?.title)
        assertEquals(1, metadata.servers.size)
        assertTrue(metadata.securityExplicitEmpty)
        assertEquals("alpha", metadata.tags.first().name)
        assertEquals("docs", metadata.externalDocs?.description)
        assertEquals("root", metadata.extensions["x-root"])
        assertEquals("paths", metadata.pathsExtensions["x-paths"])
        assertTrue(metadata.pathsExplicitEmpty)
        assertTrue(metadata.webhooks.containsKey("onPing"))
        assertEquals("hooks", metadata.webhooksExtensions["x-webhooks"])
        assertTrue(metadata.webhooksExplicitEmpty)

        assertTrue(metadata.securitySchemes.containsKey("ApiKey"))
        assertTrue(metadata.componentSchemas.containsKey("Extra"))
        assertTrue(metadata.componentExamples.containsKey("Example"))
        assertTrue(metadata.componentLinks.containsKey("Link"))
        assertTrue(metadata.componentCallbacks.containsKey("Callback"))
        assertTrue(metadata.componentParameters.containsKey("Limit"))
        assertTrue(metadata.componentResponses.containsKey("Ok"))
        assertTrue(metadata.componentRequestBodies.containsKey("Body"))
        assertTrue(metadata.componentHeaders.containsKey("X-Header"))
        assertTrue(metadata.componentPathItems.containsKey("PathItem"))
        assertTrue(metadata.componentMediaTypes.containsKey("application/json"))
        assertEquals(true, metadata.componentsExtensions["x-components"])

        val components = metadata.toComponents()
        assertNotNull(components)
        assertTrue(components!!.schemas.containsKey("Extra"))
    }

    @Test
    fun `toMetadata captures only path-level metadata`() {
        val pathItem = PathItem(
            summary = "Users",
            description = "User operations",
            parameters = listOf(EndpointParameter("id", "String", ParameterLocation.PATH)),
            servers = listOf(Server(url = "https://users.example.com")),
            extensions = mapOf("x-path" to "meta"),
            get = EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.GET,
                operationId = "getUser"
            )
        )
        val noMetadata = PathItem(
            get = EndpointDefinition(
                path = "/health",
                method = HttpMethod.GET,
                operationId = "health"
            )
        )

        val definition = OpenApiDefinition(
            info = Info("Paths", "1.0"),
            paths = mapOf(
                "/users/{id}" to pathItem,
                "/health" to noMetadata
            )
        )

        val metadata = definition.toMetadata()
        val captured = metadata.pathItems["/users/{id}"]

        assertNotNull(captured)
        assertEquals("Users", captured?.summary)
        assertEquals("User operations", captured?.description)
        assertEquals(1, captured?.parameters?.size)
        assertEquals(1, captured?.servers?.size)
        assertEquals("meta", captured?.extensions?.get("x-path"))
        assertNull(captured?.get)
        assertTrue(captured?.additionalOperations?.isEmpty() == true)

        assertFalse(metadata.pathItems.containsKey("/health"))
    }
}
