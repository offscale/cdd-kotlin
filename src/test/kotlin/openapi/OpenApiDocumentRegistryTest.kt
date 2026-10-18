/** Unit and integration tests in [OpenApiDocumentRegistryTest]. */
package openapi

import domain.EndpointDefinition
import domain.HttpMethod
import domain.Info
import domain.OpenApiDefinition
import domain.PathItem
import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Unit tests for [OpenApiDocumentRegistry] and [pathItemResolver]. */
class OpenApiDocumentRegistryTest {

  @Test
  fun `registerOpenApi indexes base uri and self`() {
    val definition =
        OpenApiDefinition(
            info = Info(title = "Registry", version = "1.0"),
            self = "https://example.com/api/openapi.json")
    val registry = OpenApiDocumentRegistry()

    registry.registerOpenApi(definition, baseUri = "file:///tmp/openapi.json#fragment")
    registry.registerOpenApi(definition)

    val resolvedBySelf = registry.resolveOpenApi("https://example.com/api/openapi.json")
    val resolvedByBase = registry.resolveOpenApi("file:///tmp/openapi.json")

    assertNotNull(resolvedBySelf)
    assertNotNull(resolvedByBase)
    assertNull(registry.resolveSchema("https://example.com/api/openapi.json"))
    assertNull(registry.resolve(null))
    assertNull(registry.resolve("   "))
    assertNull(registry.resolveOpenApi("https://example.com/unknown.json"))
    assertEquals("Registry", resolvedBySelf?.info?.title)
    assertEquals("Registry", resolvedByBase?.info?.title)
  }

  @Test
  fun `registerOpenApi resolves relative self against base uri`() {
    val definition =
        OpenApiDefinition(
            info = Info(title = "Registry", version = "1.0"), self = "/api/openapi.json")
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
    registry.registerSchema(schema)
    registry.registerDocument(OpenApiDocument.Schema(schema))

    val resolvedById = registry.resolveSchema("https://example.com/schema.json")
    val resolvedByBase = registry.resolveSchema("file:///tmp/schema.json")

    assertNotNull(resolvedById)
    assertNotNull(resolvedByBase)
    assertNull(registry.resolveOpenApi("https://example.com/schema.json"))
    assertNull(registry.resolveSchema("https://example.com/unknown.json"))
  }

  @Test
  fun `registerSchema resolves relative id against base uri and handles whitespace`() {
    val schema = SchemaProperty(schemaId = "/schemas/pet.json")
    val registry = OpenApiDocumentRegistry()

    registry.registerSchema(schema, baseUri = "https://example.com/root/openapi.json")
    val resolvedById = registry.resolveSchema("https://example.com/schemas/pet.json")
    assertNotNull(resolvedById)

    val schemaWithSpace = SchemaProperty(schemaId = "relative path.json")
    registry.registerSchema(schemaWithSpace, baseUri = "https://example.com/root/openapi.json")
    assertNotNull(registry.resolveSchema("relative path.json"))
  }

  @Test
  fun `pathItemResolver resolves component pathItems across self configurations`() {
    val pathItem =
        PathItem(
            get =
                EndpointDefinition(
                    path = "/ignored", method = HttpMethod.GET, operationId = "getPets"))
    val definition =
        OpenApiDefinition(
            info = Info(title = "Registry", version = "1.0"),
            self = "https://example.com/api/openapi.json",
            components = domain.Components(pathItems = mapOf("Pets" to pathItem)))
    val registry = OpenApiDocumentRegistry()
    registry.registerOpenApi(definition, baseUri = "https://example.com/api/openapi.json")

    val relativeDef =
        OpenApiDefinition(
            info = Info(title = "Relative", version = "1.0"),
            self = "./sub/spec.json",
            components = domain.Components(pathItems = mapOf("RelPets" to pathItem)))
    registry.registerOpenApi(relativeDef, baseUri = "https://example.com/api/base.json")

    val spaceDef =
        OpenApiDefinition(
            info = Info(title = "Space", version = "1.0"),
            self = "space spec.json",
            components = domain.Components(pathItems = mapOf("SpacePets" to pathItem)))
    registry.registerOpenApi(spaceDef, baseUri = "https://example.com/api/space.json")

    val noSelfDef =
        OpenApiDefinition(
            info = Info(title = "NoSelf", version = "1.0"),
            self = null,
            components = domain.Components(pathItems = mapOf("NoSelfPets" to pathItem)))
    registry.registerOpenApi(noSelfDef, baseUri = "https://example.com/api/noself.json")

    val noCompDef =
        OpenApiDefinition(info = Info(title = "NoComp", version = "1.0"), components = null)
    registry.registerOpenApi(noCompDef, baseUri = "https://example.com/api/nocomp.json")

    val resolver = registry.pathItemResolver()
    val resolved = resolver.resolve("https://example.com/api/openapi.json", "Pets")
    assertNotNull(resolved)
    assertEquals("getPets", resolved?.item?.get?.operationId)

    val resolvedRel = resolver.resolve("https://example.com/api/base.json", "RelPets")
    assertEquals("https://example.com/api/sub/spec.json", resolvedRel?.selfBase)

    val resolvedSpace = resolver.resolve("https://example.com/api/space.json", "SpacePets")
    assertEquals("space spec.json", resolvedSpace?.selfBase)

    val resolvedNoSelf = resolver.resolve("https://example.com/api/noself.json", "NoSelfPets")
    assertNull(resolvedNoSelf?.selfBase)

    assertNull(resolver.resolve("https://example.com/api/unknown.json", "Pets"))
    assertNull(resolver.resolve("   ", "Pets"))
    assertNull(resolver.resolve("https://example.com/api/openapi.json", "Missing"))
    assertNull(resolver.resolve("https://example.com/api/nocomp.json", "Pets"))
  }
}
