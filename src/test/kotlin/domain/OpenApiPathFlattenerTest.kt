package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiPathFlattenerTest {

    @Test
    fun `flattenPathItem merges parameters and applies fallbacks`() {
        val pathItem = PathItem(
            summary = "Users",
            description = "User operations",
            parameters = listOf(
                EndpointParameter(
                    name = "id",
                    type = "String",
                    location = ParameterLocation.PATH,
                    description = "path id"
                )
            ),
            servers = listOf(Server(url = "https://path.example.com")),
            get = EndpointDefinition(
                path = "/ignored",
                method = HttpMethod.GET,
                operationId = "getUser",
                parameters = listOf(
                    EndpointParameter(
                        name = "id",
                        type = "String",
                        location = ParameterLocation.PATH,
                        description = "override id"
                    ),
                    EndpointParameter(
                        name = "q",
                        type = "String",
                        location = ParameterLocation.QUERY
                    )
                )
            ),
            post = EndpointDefinition(
                path = "/ignored",
                method = HttpMethod.POST,
                operationId = "createUser",
                servers = listOf(Server(url = "https://op.example.com"))
            ),
            additionalOperations = mapOf(
                "COPY" to EndpointDefinition(
                    path = "/ignored",
                    method = HttpMethod.CUSTOM,
                    customMethod = "COPY",
                    operationId = "copyUser"
                )
            )
        )

        val endpoints = OpenApiPathFlattener.flattenPaths(mapOf("/users/{id}" to pathItem))
        assertEquals(3, endpoints.size)

        val get = endpoints.first { it.operationId == "getUser" }
        assertEquals("/users/{id}", get.path)
        assertEquals(HttpMethod.GET, get.method)
        assertEquals("Users", get.summary)
        assertEquals("User operations", get.description)
        assertEquals("https://path.example.com", get.servers.first().url)
        assertEquals(2, get.parameters.size)
        assertTrue(get.parameters.any { it.name == "q" && it.location == ParameterLocation.QUERY })
        assertEquals("override id", get.parameters.first { it.name == "id" && it.location == ParameterLocation.PATH }.description)

        val post = endpoints.first { it.operationId == "createUser" }
        assertEquals("https://op.example.com", post.servers.first().url)

        val copy = endpoints.first { it.operationId == "copyUser" }
        assertEquals(HttpMethod.CUSTOM, copy.method)
        assertEquals("COPY", copy.customMethod)
    }
}
