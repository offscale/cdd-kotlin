package openapi

import domain.Components
import domain.EndpointDefinition
import domain.HttpMethod
import domain.Info
import domain.OpenApiDefinition
import domain.SchemaDefinition
import domain.SecurityScheme
import domain.Server
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiAssemblerTest {

    @Test
    fun `assemble merges schemas into components and builds paths`() {
        val schemas = listOf(
            SchemaDefinition(name = "Pet", type = "object"),
            SchemaDefinition(name = "Error", type = "object")
        )

        val endpoints = listOf(
            EndpointDefinition(
                path = "/pets",
                method = HttpMethod.GET,
                operationId = "listPets"
            )
        )

        val baseComponents = Components(
            securitySchemes = mapOf(
                "ApiKey" to SecurityScheme(
                    type = "apiKey",
                    name = "X-API-KEY",
                    `in` = "header"
                )
            ),
            schemas = mapOf(
                "Pet" to SchemaDefinition(name = "Pet", type = "object", description = "override")
            )
        )

        val definition: OpenApiDefinition = OpenApiAssembler().assemble(
            info = Info("Pets API", "1.0"),
            schemas = schemas,
            endpoints = endpoints,
            servers = listOf(Server(url = "https://example.com")),
            components = baseComponents
        )

        assertNotNull(definition.components)
        assertTrue(definition.components!!.schemas.containsKey("Pet"))
        assertEquals("override", definition.components!!.schemas["Pet"]?.description)
        assertTrue(definition.components!!.schemas.containsKey("Error"))
        assertTrue(definition.components!!.securitySchemes.containsKey("ApiKey"))
        assertTrue(definition.paths.containsKey("/pets"))
        assertEquals("listPets", definition.paths["/pets"]?.get?.operationId)
    }

    @Test
    fun `assemble creates components when schemas provided and no base components`() {
        val schemas = listOf(SchemaDefinition(name = "Pet", type = "object"))
        val definition = OpenApiAssembler().assemble(
            info = Info("Pets API", "1.0"),
            schemas = schemas
        )

        assertNotNull(definition.components)
        assertTrue(definition.components!!.schemas.containsKey("Pet"))
    }

    @Test
    fun `assemble omits components when nothing to include`() {
        val definition = OpenApiAssembler().assemble(info = Info("Empty API", "1.0"))
        assertEquals(null, definition.components)
    }
}
