package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiPathFlattenerTest {

  @Test
  fun `flattenPathItem merges parameters and applies fallbacks`() {
    val pathItem =
        PathItem(
            summary = "Users",
            description = "User operations",
            parameters =
                listOf(
                    EndpointParameter(
                        name = "id",
                        type = "String",
                        location = ParameterLocation.PATH,
                        description = "path id")),
            servers = listOf(Server(url = "https://path.example.com")),
            get =
                EndpointDefinition(
                    path = "/ignored",
                    method = HttpMethod.GET,
                    operationId = "getUser",
                    parameters =
                        listOf(
                            EndpointParameter(
                                name = "id",
                                type = "String",
                                location = ParameterLocation.PATH,
                                description = "override id"),
                            EndpointParameter(
                                name = "q", type = "String", location = ParameterLocation.QUERY))),
            post =
                EndpointDefinition(
                    path = "/ignored",
                    method = HttpMethod.POST,
                    operationId = "createUser",
                    servers = listOf(Server(url = "https://op.example.com"))),
            additionalOperations =
                mapOf(
                    "COPY" to
                        EndpointDefinition(
                            path = "/ignored",
                            method = HttpMethod.CUSTOM,
                            customMethod = "COPY",
                            operationId = "copyUser")))

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
    assertEquals(
        "override id",
        get.parameters
            .first { it.name == "id" && it.location == ParameterLocation.PATH }
            .description)

    val post = endpoints.first { it.operationId == "createUser" }
    assertEquals("https://op.example.com", post.servers.first().url)

    val copy = endpoints.first { it.operationId == "copyUser" }
    assertEquals(HttpMethod.CUSTOM, copy.method)
    assertEquals("COPY", copy.customMethod)
  }

  @Test
  fun `flattenPathItem resolves component pathItem refs`() {
    val shared =
        PathItem(
            summary = "Shared summary",
            parameters =
                listOf(
                    EndpointParameter(
                        name = "tenantId",
                        type = "String",
                        location = ParameterLocation.PATH,
                        description = "tenant")),
            get =
                EndpointDefinition(
                    path = "/ignored", method = HttpMethod.GET, operationId = "listPets"))

    val components = Components(pathItems = mapOf("Pets" to shared))

    val refItem = PathItem(ref = "#/components/pathItems/Pets", summary = "Override summary")

    val endpoints =
        OpenApiPathFlattener.flattenPaths(
            paths = mapOf("/pets" to refItem), components = components)

    assertEquals(1, endpoints.size)
    val get = endpoints.first()
    assertEquals("/pets", get.path)
    assertEquals(HttpMethod.GET, get.method)
    assertEquals("listPets", get.operationId)
    assertEquals("Override summary", get.summary)
    assertEquals("tenant", get.parameters.first().description)
  }

  @Test
  fun `flattenPathItem resolves external pathItem refs via resolver`() {
    val externalItem =
        PathItem(
            summary = "External summary",
            get =
                EndpointDefinition(
                    path = "/ignored", method = HttpMethod.GET, operationId = "listPets"))

    val resolver = PathItemRefResolver { baseUri, key ->
      if (baseUri == "https://example.com/spec" && key == "Pets") {
        PathItemResolution(item = externalItem, selfBase = baseUri)
      } else {
        null
      }
    }

    val refItem = PathItem(ref = "https://example.com/spec#/components/pathItems/Pets")

    val endpoints =
        OpenApiPathFlattener.flattenPaths(paths = mapOf("/pets" to refItem), refResolver = resolver)

    assertEquals(1, endpoints.size)
    val get = endpoints.first()
    assertEquals("/pets", get.path)
    assertEquals("listPets", get.operationId)
    assertEquals("External summary", get.summary)
  }

  @Test
  fun `flattenPathItem resolves percent encoded component refs`() {
    val shared =
        PathItem(
            summary = "Shared summary",
            get =
                EndpointDefinition(
                    path = "/ignored", method = HttpMethod.GET, operationId = "listUsers"))

    val components = Components(pathItems = mapOf("User Path" to shared))

    val refItem = PathItem(ref = "#/components/pathItems/User%20Path", summary = "Override summary")

    val endpoints =
        OpenApiPathFlattener.flattenPaths(
            paths = mapOf("/users" to refItem), components = components)

    assertEquals(1, endpoints.size)
    val get = endpoints.first()
    assertEquals("/users", get.path)
    assertEquals(HttpMethod.GET, get.method)
    assertEquals("listUsers", get.operationId)
    assertEquals("Override summary", get.summary)
  }

  @Test
  fun `flattenPathItem resolves component pathItem refs with absolute uri`() {
    val shared =
        PathItem(
            summary = "Shared summary",
            get =
                EndpointDefinition(
                    path = "/ignored", method = HttpMethod.GET, operationId = "listPets"))

    val components = Components(pathItems = mapOf("Pets" to shared))

    val refItem =
        PathItem(
            ref = "https://example.com/openapi.json#/components/pathItems/Pets",
            summary = "Override summary")

    val endpoints =
        OpenApiPathFlattener.flattenPaths(
            paths = mapOf("/pets" to refItem), components = components)

    assertEquals(1, endpoints.size)
    val get = endpoints.first()
    assertEquals("/pets", get.path)
    assertEquals(HttpMethod.GET, get.method)
    assertEquals("listPets", get.operationId)
    assertEquals("Override summary", get.summary)
  }

  @Test
  fun `flattenPathItem skips component pathItem refs when self base mismatches`() {
    val shared =
        PathItem(
            get =
                EndpointDefinition(
                    path = "/ignored", method = HttpMethod.GET, operationId = "listPets"))

    val components = Components(pathItems = mapOf("Pets" to shared))

    val refItem =
        PathItem(ref = "https://other.example.com/openapi.json#/components/pathItems/Pets")

    val endpoints =
        OpenApiPathFlattener.flattenPaths(
            paths = mapOf("/pets" to refItem),
            components = components,
            self = "https://example.com/openapi.json")

    assertTrue(endpoints.isEmpty())
  }

  @Test
  fun `flattenPathItem ignores component refs with slash`() {
    val refItem = PathItem(ref = "#/components/pathItems/Nested/Path")
    val endpoints = OpenApiPathFlattener.flattenPaths(mapOf("/nested" to refItem))
    assertTrue(endpoints.isEmpty())
  }

  @Test
  fun `flattenWebhooks uses webhook keys as paths`() {
    val webhookItem =
        PathItem(
            post =
                EndpointDefinition(
                    path = "/ignored", method = HttpMethod.POST, operationId = "onPetCreated"))

    val endpoints =
        OpenApiPathFlattener.flattenWebhooks(webhooks = mapOf("pet.created" to webhookItem))

    assertEquals(1, endpoints.size)
    val post = endpoints.first()
    assertEquals("pet.created", post.path)
    assertEquals(HttpMethod.POST, post.method)
    assertEquals("onPetCreated", post.operationId)
  }

  @Test
  fun `flattenAll merges paths and webhooks`() {
    val paths =
        mapOf(
            "/pets" to
                PathItem(
                    get =
                        EndpointDefinition(
                            path = "/ignored", method = HttpMethod.GET, operationId = "listPets")))
    val webhooks =
        mapOf(
            "pet.deleted" to
                PathItem(
                    post =
                        EndpointDefinition(
                            path = "/ignored",
                            method = HttpMethod.POST,
                            operationId = "onPetDeleted")))

    val endpoints = OpenApiPathFlattener.flattenAll(paths, webhooks)

    assertEquals(2, endpoints.size)
    assertTrue(endpoints.any { it.operationId == "listPets" && it.path == "/pets" })
    assertTrue(endpoints.any { it.operationId == "onPetDeleted" && it.path == "pet.deleted" })

    // Direct flattenPathItem call with default args
    val direct =
        OpenApiPathFlattener.flattenPathItem(
            "/direct",
            PathItem(
                get =
                    EndpointDefinition(
                        path = "/direct", method = HttpMethod.GET, operationId = "directOp")))
    assertEquals(1, direct.size)

    // Component pathItem ref with extension merging and chained ref
    val baseTarget =
        PathItem(
            extensions = mapOf("x-base" to OpenApiInt(1L)),
            get =
                EndpointDefinition(path = "/x", method = HttpMethod.GET, operationId = "chainedOp"))
    val midTarget =
        PathItem(ref = "#/components/pathItems/Base", extensions = mapOf("x-mid" to OpenApiInt(2L)))
    val leafItem =
        PathItem(ref = "#/components/pathItems/Mid", extensions = mapOf("x-leaf" to OpenApiInt(3L)))
    val chainedComp =
        Components(
            pathItems =
                mapOf(
                    "Base" to baseTarget,
                    "Mid" to midTarget,
                    "NoExt" to PathItem(get = baseTarget.get)))
    val chainedEndpoints =
        OpenApiPathFlattener.flattenPaths(
            mapOf(
                "/chained" to leafItem,
                "/overOnly" to
                    PathItem(
                        ref = "#/components/pathItems/NoExt",
                        extensions = mapOf("x-over" to OpenApiInt(9L)))),
            components = chainedComp)
    assertEquals(2, chainedEndpoints.size)

    // Component pathItem ref with matching selfBase falling back to refResolver
    val fallbackEndpoints =
        OpenApiPathFlattener.flattenPaths(
            paths =
                mapOf(
                    "/ext" to
                        PathItem(ref = "https://example.com/spec#/components/pathItems/Remote")),
            components = Components(),
            self = "https://example.com/spec",
            refResolver =
                PathItemRefResolver { _, key ->
                  if (key == "Remote") PathItemResolution(baseTarget) else null
                })
    assertEquals(1, fallbackEndpoints.size)

    // Whitespace in base / ref
    val wsEndpoints =
        OpenApiPathFlattener.flattenPaths(
            paths =
                mapOf("/ws" to PathItem(ref = "http://bad base/spec#/components/pathItems/Remote")),
            components = Components(),
            self = "http://bad self/spec",
            refResolver = PathItemRefResolver { _, _ -> null })
    assertTrue(wsEndpoints.isEmpty())

    // Empty flattenAll and empty paths with non-empty webhooks
    assertTrue(OpenApiPathFlattener.flattenAll(emptyMap(), emptyMap()).isEmpty())
    assertEquals(1, OpenApiPathFlattener.flattenAll(emptyMap(), webhooks).size)
    assertEquals(1, OpenApiPathFlattener.flattenAll(paths, emptyMap()).size)

    // Cyclic pathItem ref and overriding parameters, servers, description
    val cycA =
        PathItem(
            ref = "#/components/pathItems/CycB",
            summary = "SumA",
            description = "DescA",
            parameters = listOf(EndpointParameter("p", "String", ParameterLocation.QUERY)),
            servers = listOf(Server("https://a.example.com")))
    val cycB = PathItem(ref = "#/components/pathItems/CycA")
    val cycComp = Components(pathItems = mapOf("CycA" to cycA, "CycB" to cycB))
    assertTrue(
        OpenApiPathFlattener.flattenPaths(mapOf("/cyc" to cycA), components = cycComp).isEmpty())

    // Non-component ref and blank key ref
    assertTrue(
        OpenApiPathFlattener.flattenPaths(mapOf("/nonComp" to PathItem(ref = "./other.yaml")))
            .isEmpty())
    assertTrue(
        OpenApiPathFlattener.flattenPaths(
                mapOf("/blankKey" to PathItem(ref = "#/components/pathItems/   ")))
            .isEmpty())

    // Missing local ref without base (base == null && refResolver != null)
    assertTrue(
        OpenApiPathFlattener.flattenPaths(
                paths = mapOf("/missingLocal" to PathItem(ref = "#/components/pathItems/Missing")),
                components = Components(),
                refResolver = PathItemRefResolver { _, _ -> null })
            .isEmpty())

    // Missing local ref with base but refResolver == null
    assertTrue(
        OpenApiPathFlattener.flattenPaths(
                paths =
                    mapOf(
                        "/missingLocalWithBase" to
                            PathItem(
                                ref = "https://example.com/spec#/components/pathItems/Missing")),
                components = Components(),
                self = "https://example.com/spec",
                refResolver = null)
            .isEmpty())

    // Blank self base and whitespace only in ref (valid base)
    assertTrue(
        OpenApiPathFlattener.flattenPaths(
                paths =
                    mapOf(
                        "/wsRefOnly" to
                            PathItem(ref = "http://bad ref/spec#/components/pathItems/Remote")),
                components = Components(),
                self = "https://valid.example.com/spec",
                refResolver = PathItemRefResolver { _, _ -> null })
            .isEmpty())
    assertTrue(
        OpenApiPathFlattener.flattenPaths(
                paths = mapOf("/blankSelf" to PathItem(ref = "#/components/pathItems/Missing")),
                components = Components(),
                self = "   ")
            .isEmpty())

    // Additional operation with op-level summary, description, and servers
    val customOpItem =
        PathItem(
            summary = "ItemSum",
            description = "ItemDesc",
            servers = listOf(Server("https://item.example.com")),
            additionalOperations =
                mapOf(
                    "LOCK" to
                        EndpointDefinition(
                            path = "/lock",
                            method = HttpMethod.CUSTOM,
                            customMethod = "LOCK",
                            operationId = "lockOp",
                            summary = "OpSum",
                            description = "OpDesc",
                            servers = listOf(Server("https://op.example.com")))))
    val lockEndpoints = OpenApiPathFlattener.flattenPaths(mapOf("/lock" to customOpItem))
    assertEquals(1, lockEndpoints.size)
    assertEquals("OpSum", lockEndpoints.first().summary)
    assertEquals("OpDesc", lockEndpoints.first().description)
    assertEquals("https://op.example.com", lockEndpoints.first().servers.first().url)
  }
}
