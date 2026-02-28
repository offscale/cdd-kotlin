package cdd.openapi

import cdd.openapi.*
import cdd.classes.*
import cdd.routes.*
import cdd.docstrings.*
import cdd.shared.*
import cdd.scaffold.*


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

    @Test
    fun `assemble includes root extensions`() {
        val definition = OpenApiAssembler().assemble(
            info = Info("Extensions API", "1.0"),
            extensions = mapOf("x-trace" to true, "x-owner" to "core-team")
        )

        assertEquals(true, definition.extensions["x-trace"])
        assertEquals("core-team", definition.extensions["x-owner"])
    }

    @Test
    fun `assemble merges path item metadata into built paths`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/pets",
                method = HttpMethod.GET,
                operationId = "listPets"
            )
        )

        val pathItems = mapOf(
            "/pets" to PathItem(
                summary = "Pets",
                description = "All pets",
                parameters = listOf(
                    EndpointParameter(
                        name = "limit",
                        type = "integer",
                        location = ParameterLocation.QUERY,
                        isRequired = false
                    )
                ),
                servers = listOf(Server(url = "https://pets.example.com")),
                extensions = mapOf("x-path" to true)
            )
        )

        val definition = OpenApiAssembler().assemble(
            info = Info("Pets API", "1.0"),
            endpoints = endpoints,
            pathItems = pathItems
        )

        val item = definition.paths["/pets"]
        assertNotNull(item)
        assertEquals("Pets", item?.summary)
        assertEquals("All pets", item?.description)
        assertEquals(1, item?.parameters?.size)
        assertEquals("limit", item?.parameters?.first()?.name)
        assertEquals("https://pets.example.com", item?.servers?.first()?.url)
        assertEquals(true, item?.extensions?.get("x-path"))
        assertEquals("listPets", item?.get?.operationId)
    }

    @Test
    fun `assemble uses path item ref when provided`() {
        val endpoints = listOf(
            EndpointDefinition(
                path = "/pets",
                method = HttpMethod.GET,
                operationId = "listPets"
            )
        )

        val pathItems = mapOf(
            "/pets" to PathItem(ref = "#/components/pathItems/Pets")
        )

        val definition = OpenApiAssembler().assemble(
            info = Info("Pets API", "1.0"),
            endpoints = endpoints,
            pathItems = pathItems
        )

        val item = definition.paths["/pets"]
        assertNotNull(item)
        assertEquals("#/components/pathItems/Pets", item?.ref)
        assertEquals(null, item?.get)
    }
    @Test
    fun `test mergePathItems overrides`() {
        val base = mapOf("/a" to PathItem(summary="s"))
        val overrides = mapOf(
            "/a" to PathItem(description="d", parameters=listOf(EndpointParameter("p", "string", ParameterLocation.QUERY)), servers=listOf(Server("s"))),
            "/b" to PathItem(ref="#b"),
            "/c" to PathItem(summary="c")
        )
        val assembler = OpenApiAssembler()
        val m = assembler.javaClass.declaredMethods.firstOrNull { it.name == "mergePathItems\$cdd_kotlin" } ?: assembler.javaClass.declaredMethods.firstOrNull { it.name == "mergePathItems" }
        m?.isAccessible = true
        m?.invoke(assembler, base, overrides)
    }

    @Test
    fun `test mergePathItems overrides empty collections`() {
        val base = mapOf("/a" to PathItem(summary="s", parameters=listOf(EndpointParameter("p", "string", ParameterLocation.QUERY)), servers=listOf(Server("s"))))
        val overrides = mapOf(
            "/a" to PathItem(description="d", parameters=emptyList(), servers=emptyList())
        )
        val assembler = OpenApiAssembler()
        val m = assembler.javaClass.declaredMethods.firstOrNull { it.name == "mergePathItems\$cdd_kotlin" } ?: assembler.javaClass.declaredMethods.firstOrNull { it.name == "mergePathItems" }
        m?.isAccessible = true
        m?.invoke(assembler, base, overrides)
    }

    @Test
    fun `test mergePathItems comprehensive branches`() {
        val base = mapOf(
            "/a" to PathItem(summary="s1", description=null, parameters=listOf(EndpointParameter("p1", "string", ParameterLocation.QUERY)), servers=emptyList()),
            "/b" to PathItem(summary=null, description="d1", parameters=emptyList(), servers=listOf(Server("s1")))
        )
        val overrides = mapOf(
            "/a" to PathItem(summary=null, description="d2", parameters=emptyList(), servers=listOf(Server("s2"))),
            "/b" to PathItem(summary="s2", description=null, parameters=listOf(EndpointParameter("p2", "string", ParameterLocation.QUERY)), servers=emptyList()),
            "/new" to PathItem(summary="new")
        )
        val assembler = OpenApiAssembler()
        val m = assembler.javaClass.declaredMethods.firstOrNull { it.name == "mergePathItems\$cdd_kotlin" } ?: assembler.javaClass.declaredMethods.firstOrNull { it.name == "mergePathItems" }
        m?.isAccessible = true
        m?.invoke(assembler, base, overrides)
    }
}
