/** Unit and integration tests in [OpenApiPathBuilderTest]. */
package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiPathBuilderTest {

  @Test
  fun `buildPaths groups operations by path and method`() {
    val endpoints =
        listOf(
            EndpointDefinition(path = "/pets", method = HttpMethod.GET, operationId = "listPets"),
            EndpointDefinition(path = "/pets", method = HttpMethod.PUT, operationId = "replacePet"),
            EndpointDefinition(path = "/pets", method = HttpMethod.POST, operationId = "createPet"),
            EndpointDefinition(
                path = "/pets", method = HttpMethod.DELETE, operationId = "deletePets"),
            EndpointDefinition(
                path = "/pets", method = HttpMethod.OPTIONS, operationId = "optionsPets"),
            EndpointDefinition(path = "/pets", method = HttpMethod.HEAD, operationId = "headPets"),
            EndpointDefinition(
                path = "/pets", method = HttpMethod.PATCH, operationId = "patchPets"),
            EndpointDefinition(
                path = "/pets", method = HttpMethod.TRACE, operationId = "tracePets"),
            EndpointDefinition(
                path = "/pets", method = HttpMethod.QUERY, operationId = "queryPets"),
            EndpointDefinition(
                path = "/pets",
                method = HttpMethod.CUSTOM,
                customMethod = "COPY",
                operationId = "copyPets"))

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
    val sharedParam =
        EndpointParameter(
            name = "id", type = "String", location = ParameterLocation.PATH, description = "shared")
    val sharedServers = listOf(Server(url = "https://api.example.com"))

    val endpoints =
        listOf(
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.GET,
                operationId = "getUser",
                summary = "User ops",
                description = "Shared description",
                parameters = listOf(sharedParam),
                servers = sharedServers),
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.PUT,
                operationId = "putUser",
                summary = "User ops",
                description = "Shared description",
                parameters = listOf(sharedParam),
                servers = sharedServers))

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

    // Test when parameters, summary, description, and servers are NOT shared across operations
    val p1 = EndpointParameter(name = "p1", type = "String", location = ParameterLocation.QUERY)
    val p2 = EndpointParameter(name = "p2", type = "String", location = ParameterLocation.QUERY)
    val nonSharedEndpoints =
        listOf(
            EndpointDefinition(
                path = "/mixed",
                method = HttpMethod.GET,
                operationId = "op1",
                summary = "Sum 1",
                description = null,
                parameters = listOf(p1),
                servers = emptyList()),
            EndpointDefinition(
                path = "/mixed",
                method = HttpMethod.CUSTOM,
                customMethod = null,
                operationId = "op2",
                summary = "Sum 2",
                description = null,
                parameters = listOf(p2),
                servers = emptyList()))
    val mixedPaths =
        OpenApiPathBuilder.buildPaths(nonSharedEndpoints, liftCommonPathMetadata = true)
    val mixedItem = mixedPaths["/mixed"]!!
    assertEquals(null, mixedItem.summary)
    assertEquals(null, mixedItem.description)
    assertTrue(mixedItem.parameters.isEmpty())
    assertTrue(mixedItem.servers.isEmpty())
    assertEquals("Sum 1", mixedItem.get?.summary)
    assertEquals(1, mixedItem.get?.parameters?.size)
    assertTrue(mixedItem.additionalOperations.containsKey("CUSTOM"))

    // Test when first operation has empty parameters
    val emptyFirstParams =
        listOf(
            EndpointDefinition(
                path = "/emptyFirst",
                method = HttpMethod.GET,
                operationId = "op1",
                parameters = emptyList(),
                servers = listOf(Server("https://s1.example.com"))),
            EndpointDefinition(
                path = "/emptyFirst",
                method = HttpMethod.POST,
                operationId = "op2",
                parameters = listOf(p1),
                servers = listOf(Server("https://s2.example.com"))))
    val emptyFirstPaths =
        OpenApiPathBuilder.buildPaths(emptyFirstParams, liftCommonPathMetadata = true)
    val emptyFirstItem = emptyFirstPaths["/emptyFirst"]!!
    assertTrue(emptyFirstItem.parameters.isEmpty())
    assertTrue(emptyFirstItem.servers.isEmpty())
    assertEquals(1, emptyFirstItem.post?.parameters?.size)
    assertEquals(1, emptyFirstItem.get?.servers?.size)
  }
}
