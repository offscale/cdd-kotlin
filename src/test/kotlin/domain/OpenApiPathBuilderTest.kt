package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiPathBuilderTest {

    @Test
    fun `buildPaths groups operations by path and method`() {
        val endpoints = listOf(
            EndpointDefinition(path = "/pets", method = HttpMethod.GET, operationId = "listPets"),
            EndpointDefinition(path = "/pets", method = HttpMethod.PUT, operationId = "replacePet"),
            EndpointDefinition(path = "/pets", method = HttpMethod.POST, operationId = "createPet"),
            EndpointDefinition(path = "/pets", method = HttpMethod.DELETE, operationId = "deletePets"),
            EndpointDefinition(path = "/pets", method = HttpMethod.OPTIONS, operationId = "optionsPets"),
            EndpointDefinition(path = "/pets", method = HttpMethod.HEAD, operationId = "headPets"),
            EndpointDefinition(path = "/pets", method = HttpMethod.PATCH, operationId = "patchPets"),
            EndpointDefinition(path = "/pets", method = HttpMethod.TRACE, operationId = "tracePets"),
            EndpointDefinition(path = "/pets", method = HttpMethod.QUERY, operationId = "queryPets"),
            EndpointDefinition(path = "/pets", method = HttpMethod.CUSTOM, customMethod = "COPY", operationId = "copyPets")
        )

        val paths = OpenApiPathBuilder.buildPaths(endpoints)
        assertTrue(paths.containsKey("/pets"))

        val item = paths["/pets"]
        assertNotNull(item)
        assertEquals("listPets", item?.get?.operationId)
        assertEquals("createPet", item?.post?.operationId)
        assertEquals("copyPets", item?.additionalOperations?.get("COPY")?.operationId)
    }

    @Test
    fun `buildPaths returns empty map when no endpoints provided`() {
        val paths = OpenApiPathBuilder.buildPaths(emptyList())
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `buildPaths can lift shared path metadata when enabled`() {
        val sharedParam = EndpointParameter(
            name = "id",
            type = "String",
            location = ParameterLocation.PATH,
            description = "shared"
        )
        val sharedServers = listOf(Server(url = "https://api.example.com"))

        val endpoints = listOf(
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.GET,
                operationId = "getUser",
                summary = "User ops",
                description = "Shared description",
                parameters = listOf(sharedParam),
                servers = sharedServers
            ),
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.PUT,
                operationId = "putUser",
                summary = "User ops",
                description = "Shared description",
                parameters = listOf(sharedParam),
                servers = sharedServers
            )
        )

        val paths = OpenApiPathBuilder.buildPaths(endpoints, liftCommonPathMetadata = true)
        val item = paths["/users/{id}"]!!

        assertEquals("User ops", item.summary)
        assertEquals("Shared description", item.description)
        assertEquals(1, item.parameters.size)
        assertEquals("id", item.parameters.first().name)
        assertEquals(1, item.servers.size)
        assertEquals("https://api.example.com", item.servers.first().url)

        assertTrue(item.get?.parameters?.isEmpty() == true)
        assertTrue(item.put?.parameters?.isEmpty() == true)
        assertEquals(null, item.get?.summary)
        assertEquals(null, item.get?.description)
        assertTrue(item.get?.servers?.isEmpty() == true)
    }
}
