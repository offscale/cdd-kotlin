package openapi

import domain.Info
import domain.OpenApiDefinition
import domain.PathItem
import domain.EndpointDefinition
import domain.HttpMethod
import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class OpenApiDocumentRegistryTest {

    @Test
    fun `registerOpenApi indexes base uri and self`() {
        val definition = OpenApiDefinition(
            info = Info(title = "Registry", version = "1.0"),
            self = "https://example.com/api/openapi.json"
        )
        val registry = OpenApiDocumentRegistry()

        registry.registerOpenApi(definition, baseUri = "file:///tmp/openapi.json#fragment")

        val resolvedBySelf = registry.resolveOpenApi("https://example.com/api/openapi.json")
        val resolvedByBase = registry.resolveOpenApi("file:///tmp/openapi.json")

        assertNotNull(resolvedBySelf)
        assertNotNull(resolvedByBase)
        assertEquals("Registry", resolvedBySelf?.info?.title)
        assertEquals("Registry", resolvedByBase?.info?.title)
    }

    @Test
    fun `registerOpenApi resolves relative self against base uri`() {
        val definition = OpenApiDefinition(
            info = Info(title = "Registry", version = "1.0"),
            self = "/api/openapi.json"
        )
        val registry = OpenApiDocumentRegistry()

        registry.registerOpenApi(definition, baseUri = "https://example.com/root/openapi.json")

        val resolvedBySelf = registry.resolveOpenApi("https://example.com/api/openapi.json")
        assertNotNull(resolvedBySelf)
        assertEquals("Registry", resolvedBySelf?.info?.title)
    }

    @Test
    fun `registerSchema indexes base uri and schema id`() {
        val schema = SchemaProperty(schemaId = "https://example.com/schema.json")
        val registry = OpenApiDocumentRegistry()

        registry.registerSchema(schema, baseUri = "file:///tmp/schema.json#section")

        val resolvedById = registry.resolveSchema("https://example.com/schema.json")
        val resolvedByBase = registry.resolveSchema("file:///tmp/schema.json")

        assertNotNull(resolvedById)
        assertNotNull(resolvedByBase)
    }

    @Test
    fun `registerSchema resolves relative id against base uri`() {
        val schema = SchemaProperty(schemaId = "/schemas/pet.json")
        val registry = OpenApiDocumentRegistry()

        registry.registerSchema(schema, baseUri = "https://example.com/root/openapi.json")

        val resolvedById = registry.resolveSchema("https://example.com/schemas/pet.json")
        assertNotNull(resolvedById)
    }

    @Test
    fun `pathItemResolver resolves component pathItems`() {
        val pathItem = PathItem(
            get = EndpointDefinition(
                path = "/ignored",
                method = HttpMethod.GET,
                operationId = "getPets"
            )
        )
        val definition = OpenApiDefinition(
            info = Info(title = "Registry", version = "1.0"),
            self = "https://example.com/api/openapi.json",
            components = domain.Components(pathItems = mapOf("Pets" to pathItem))
        )
        val registry = OpenApiDocumentRegistry()
        registry.registerOpenApi(definition, baseUri = "https://example.com/api/openapi.json")

        val resolver = registry.pathItemResolver()
        val resolved = resolver.resolve("https://example.com/api/openapi.json", "Pets")

        assertNotNull(resolved)
        assertEquals("getPets", resolved?.item?.get?.operationId)
    }
}
