package org.cdd.mcp

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Concise, parameterized round-trip serialization, equality, copy, and sealed-hierarchy tests for
 * all MCP 1.0.0 protocol models in [McpModels.kt].
 */
class McpModelsCoverageTest {
  private val jsonCodec = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private val jsonCompact = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
  }

  private fun <T : Any> verifyModel(
      serializer: KSerializer<T>,
      primary: T,
      duplicate: T,
      alternate: T,
      copySame: (T) -> T
  ) {
    val encoded = jsonCodec.encodeToString(serializer, primary)
    assertTrue(encoded.isNotBlank())
    val decoded = jsonCodec.decodeFromString(serializer, encoded)
    assertEquals(primary, decoded)

    val encodedCompact = jsonCompact.encodeToString(serializer, primary)
    assertEquals(primary, jsonCompact.decodeFromString(serializer, encodedCompact))

    val encodedAlt = jsonCodec.encodeToString(serializer, alternate)
    val decodedAlt = jsonCodec.decodeFromString(serializer, encodedAlt)
    assertEquals(alternate, decodedAlt)

    val encodedAltCompact = jsonCompact.encodeToString(serializer, alternate)
    assertEquals(alternate, jsonCompact.decodeFromString(serializer, encodedAltCompact))

    runCatching { jsonCompact.decodeFromString(serializer, "{}") }

    assertEquals(primary, duplicate)
    assertEquals(primary.hashCode(), duplicate.hashCode())
    assertEquals(primary.toString(), duplicate.toString())
    assertTrue(primary.equals(primary))
    assertFalse(primary.equals(null))
    assertFalse(primary.equals("unrelated"))
    assertNotEquals(primary, alternate)
    assertEquals(primary, copySame(primary))
  }

  @Test
  fun testCorePrimitivesAndEnums() {
    for (level in LoggingLevel.entries) {
      val encoded = jsonCodec.encodeToString(LoggingLevel.serializer(), level)
      assertEquals(level, jsonCodec.decodeFromString(LoggingLevel.serializer(), encoded))
    }
    assertNotEquals(LoggingLevel.debug, LoggingLevel.error)

    for (role in Role.entries) {
      val encoded = jsonCodec.encodeToString(Role.serializer(), role)
      assertEquals(role, jsonCodec.decodeFromString(Role.serializer(), encoded))
    }
    assertNotEquals(Role.user, Role.assistant)

    val cursor: Cursor = "cursor-1"
    val progressToken: ProgressToken = "tok-1"
    val requestId: RequestId = "req-1"
    assertEquals("cursor-1", cursor)
    assertEquals("tok-1", progressToken)
    assertEquals("req-1", requestId)
  }

  @Test
  fun testResourceAndContentModels() {
    verifyModel(
        Annotated.serializer(),
        Annotated(),
        Annotated(),
        Annotated(annotations = mapOf("k" to "v"))) {
          it.copy(annotations = it.annotations)
        }

    verifyModel(
        BlobResourceContents.serializer(),
        BlobResourceContents(blob = "YmxvYg==", uri = "file:///a"),
        BlobResourceContents(blob = "YmxvYg==", uri = "file:///a"),
        BlobResourceContents(
            blob = "YWx0", mimeType = "application/octet-stream", uri = "file:///b")) {
          it.copy(blob = it.blob, mimeType = it.mimeType, uri = it.uri)
        }

    verifyModel(
        TextResourceContents.serializer(),
        TextResourceContents(text = "hello", uri = "file:///a.txt"),
        TextResourceContents(text = "hello", uri = "file:///a.txt"),
        TextResourceContents(mimeType = "text/plain", text = "world", uri = "file:///b.txt")) {
          it.copy(mimeType = it.mimeType, text = it.text, uri = it.uri)
        }

    val rc1 = ResourceContents(uri = "file:///a")
    val rc2 =
        ResourceContents(
            mimeType = "text/plain", uri = "file:///b", text = "bye", blob = "YmxvYg==")
    verifyModel(ResourceContents.serializer(), rc1, rc1.copy(), rc2) {
      it.copy(mimeType = it.mimeType, uri = it.uri, text = it.text, blob = it.blob)
    }

    verifyModel(
        EmbeddedResource.serializer(),
        EmbeddedResource(resource = rc1),
        EmbeddedResource(resource = rc1),
        EmbeddedResource(annotations = mapOf("a" to "b"), resource = rc2, type = "resource")) {
          it.copy(annotations = it.annotations, resource = it.resource, type = it.type)
        }

    verifyModel(
        ImageContent.serializer(),
        ImageContent(data = "img1", mimeType = "image/png"),
        ImageContent(data = "img1", mimeType = "image/png"),
        ImageContent(
            annotations = mapOf("a" to "1"),
            data = "img2",
            mimeType = "image/jpeg",
            type = "image")) {
          it.copy(
              annotations = it.annotations, data = it.data, mimeType = it.mimeType, type = it.type)
        }

    verifyModel(
        TextContent.serializer(),
        TextContent(text = "hello"),
        TextContent(text = "hello"),
        TextContent(annotations = mapOf("a" to "1"), text = "world", type = "text")) {
          it.copy(annotations = it.annotations, text = it.text, type = it.type)
        }

    verifyModel(
        Resource.serializer(),
        Resource(name = "r1", uri = "file:///r1"),
        Resource(name = "r1", uri = "file:///r1"),
        Resource(
            annotations = mapOf("a" to "b"),
            description = "desc",
            mimeType = "text/plain",
            name = "r2",
            size = 42,
            uri = "file:///r2")) {
          it.copy(
              annotations = it.annotations,
              description = it.description,
              mimeType = it.mimeType,
              name = it.name,
              size = it.size,
              uri = it.uri)
        }

    verifyModel(
        ResourceReference.serializer(),
        ResourceReference(uri = "file:///r1"),
        ResourceReference(uri = "file:///r1"),
        ResourceReference(type = "resource", uri = "file:///r2")) {
          it.copy(type = it.type, uri = it.uri)
        }

    verifyModel(
        ResourceTemplate.serializer(),
        ResourceTemplate(name = "tpl1", uriTemplate = "file:///{path}"),
        ResourceTemplate(name = "tpl1", uriTemplate = "file:///{path}"),
        ResourceTemplate(
            annotations = mapOf("a" to "b"),
            description = "desc",
            mimeType = "text/plain",
            name = "tpl2",
            uriTemplate = "file:///{id}")) {
          it.copy(
              annotations = it.annotations,
              description = it.description,
              mimeType = it.mimeType,
              name = it.name,
              uriTemplate = it.uriTemplate)
        }

    verifyModel(
        Root.serializer(),
        Root(uri = "file:///workspace"),
        Root(uri = "file:///workspace"),
        Root(name = "main", uri = "file:///other")) {
          it.copy(name = it.name, uri = it.uri)
        }
  }

  @Test
  fun testCapabilitiesAndImplementationModels() {
    val rootsCap1 = ClientCapabilitiesRoots()
    val rootsCap2 = ClientCapabilitiesRoots(listChanged = true)
    verifyModel(ClientCapabilitiesRoots.serializer(), rootsCap1, rootsCap1.copy(), rootsCap2) {
      it.copy(listChanged = it.listChanged)
    }

    val clientCap1 = ClientCapabilities()
    val clientCap2 =
        ClientCapabilities(
            experimental = mapOf("exp" to JsonPrimitive(true)),
            roots = rootsCap2,
            sampling = mapOf("s" to JsonPrimitive(1)))
    verifyModel(ClientCapabilities.serializer(), clientCap1, clientCap1.copy(), clientCap2) {
      it.copy(experimental = it.experimental, roots = it.roots, sampling = it.sampling)
    }

    val promptsCap1 = ServerCapabilitiesPrompts()
    val promptsCap2 = ServerCapabilitiesPrompts(listChanged = true)
    verifyModel(
        ServerCapabilitiesPrompts.serializer(), promptsCap1, promptsCap1.copy(), promptsCap2) {
          it.copy(listChanged = it.listChanged)
        }

    val resCap1 = ServerCapabilitiesResources()
    val resCap2 = ServerCapabilitiesResources(listChanged = true, subscribe = true)
    verifyModel(ServerCapabilitiesResources.serializer(), resCap1, resCap1.copy(), resCap2) {
      it.copy(listChanged = it.listChanged, subscribe = it.subscribe)
    }

    val toolsCap1 = ServerCapabilitiesTools()
    val toolsCap2 = ServerCapabilitiesTools(listChanged = true)
    verifyModel(ServerCapabilitiesTools.serializer(), toolsCap1, toolsCap1.copy(), toolsCap2) {
      it.copy(listChanged = it.listChanged)
    }

    val serverCap1 = ServerCapabilities()
    val serverCap2 =
        ServerCapabilities(
            experimental = mapOf("e" to JsonNull),
            logging = mapOf("l" to JsonPrimitive("info")),
            prompts = promptsCap2,
            resources = resCap2,
            tools = toolsCap2)
    verifyModel(ServerCapabilities.serializer(), serverCap1, serverCap1.copy(), serverCap2) {
      it.copy(
          experimental = it.experimental,
          logging = it.logging,
          prompts = it.prompts,
          resources = it.resources,
          tools = it.tools)
    }

    val impl1 = Implementation(name = "cdd-kotlin", version = "1.0.0")
    val impl2 = Implementation(name = "cdd-other", version = "2.0.0")
    verifyModel(Implementation.serializer(), impl1, impl1.copy(), impl2) {
      it.copy(name = it.name, version = it.version)
    }

    val initParams1 =
        InitializeRequestParams(
            capabilities = clientCap1, clientInfo = impl1, protocolVersion = "2024-11-05")
    val initParams2 =
        InitializeRequestParams(
            capabilities = clientCap2, clientInfo = impl2, protocolVersion = "2025-01-01")
    verifyModel(
        InitializeRequestParams.serializer(), initParams1, initParams1.copy(), initParams2) {
          it.copy(
              capabilities = it.capabilities,
              clientInfo = it.clientInfo,
              protocolVersion = it.protocolVersion)
        }

    val initReq1 = InitializeRequest(params = initParams1)
    val initReq2 = InitializeRequest(method = "initialize", params = initParams2)
    verifyModel(InitializeRequest.serializer(), initReq1, initReq1.copy(), initReq2) {
      it.copy(method = it.method, params = it.params)
    }

    val initRes1 =
        InitializeResult(
            capabilities = serverCap1, protocolVersion = "2024-11-05", serverInfo = impl1)
    val initRes2 =
        InitializeResult(
            _meta = mapOf("m" to JsonPrimitive("v")),
            capabilities = serverCap2,
            instructions = "Use tools",
            protocolVersion = "2025-01-01",
            serverInfo = impl2)
    verifyModel(InitializeResult.serializer(), initRes1, initRes1.copy(), initRes2) {
      it.copy(
          _meta = it._meta,
          capabilities = it.capabilities,
          instructions = it.instructions,
          protocolVersion = it.protocolVersion,
          serverInfo = it.serverInfo)
    }
  }

  @Test
  fun testJsonRpcAndGenericMessageModels() {
    val errDetail1 = JSONRPCErrorError(code = -32600, message = "Invalid Request")
    val errDetail2 =
        JSONRPCErrorError(code = -32603, data = JsonPrimitive("detail"), message = "Internal error")
    verifyModel(JSONRPCErrorError.serializer(), errDetail1, errDetail1.copy(), errDetail2) {
      it.copy(code = it.code, data = it.data, message = it.message)
    }

    val rpcErr1 = JSONRPCError(error = errDetail1, id = "1")
    val rpcErr2 = JSONRPCError(error = errDetail2, id = "2", jsonrpc = "2.0")
    verifyModel(JSONRPCError.serializer(), rpcErr1, rpcErr1.copy(), rpcErr2) {
      it.copy(error = it.error, id = it.id, jsonrpc = it.jsonrpc)
    }

    val rpcNotif1 = JSONRPCNotification(method = "notifications/initialized")
    val rpcNotif2 =
        JSONRPCNotification(
            jsonrpc = "2.0",
            method = "notifications/cancelled",
            params = buildJsonObject { put("requestId", "1") })
    verifyModel(JSONRPCNotification.serializer(), rpcNotif1, rpcNotif1.copy(), rpcNotif2) {
      it.copy(jsonrpc = it.jsonrpc, method = it.method, params = it.params)
    }

    val rpcReq1 = JSONRPCRequest(id = "1", method = "ping")
    val rpcReq2 =
        JSONRPCRequest(
            id = "2",
            jsonrpc = "2.0",
            method = "tools/list",
            params = buildJsonObject { put("cursor", "c1") })
    verifyModel(JSONRPCRequest.serializer(), rpcReq1, rpcReq1.copy(), rpcReq2) {
      it.copy(id = it.id, jsonrpc = it.jsonrpc, method = it.method, params = it.params)
    }

    val rpcResp1 = JSONRPCResponse(id = "1", result = buildJsonObject {})
    val rpcResp2 =
        JSONRPCResponse(id = "2", jsonrpc = "2.0", result = buildJsonObject { put("ok", true) })
    verifyModel(JSONRPCResponse.serializer(), rpcResp1, rpcResp1.copy(), rpcResp2) {
      it.copy(id = it.id, jsonrpc = it.jsonrpc, result = it.result)
    }

    val messages: List<JSONRPCMessage> = listOf(rpcErr1, rpcNotif1, rpcReq1, rpcResp1)
    assertEquals(4, messages.size)

    verifyModel(
        Notification.serializer(),
        Notification(method = "n1"),
        Notification(method = "n1"),
        Notification(method = "n2", params = mapOf("k" to JsonPrimitive("v")))) {
          it.copy(method = it.method, params = it.params)
        }

    verifyModel(
        Request.serializer(),
        Request(method = "r1"),
        Request(method = "r1"),
        Request(method = "r2", params = mapOf("k" to JsonPrimitive("v")))) {
          it.copy(method = it.method, params = it.params)
        }

    verifyModel(
        Result.serializer(), Result(), Result(), Result(_meta = mapOf("k" to JsonPrimitive("v")))) {
          it.copy(_meta = it._meta)
        }

    verifyModel(
        EmptyResult.serializer(),
        EmptyResult(),
        EmptyResult(),
        EmptyResult(_meta = mapOf("k" to JsonPrimitive("v")))) {
          it.copy(_meta = it._meta)
        }

    val pagParams1 = PaginatedRequestParams()
    val pagParams2 = PaginatedRequestParams(cursor = "c1")
    verifyModel(PaginatedRequestParams.serializer(), pagParams1, pagParams1.copy(), pagParams2) {
      it.copy(cursor = it.cursor)
    }

    verifyModel(
        PaginatedRequest.serializer(),
        PaginatedRequest(method = "m1"),
        PaginatedRequest(method = "m1"),
        PaginatedRequest(method = "m2", params = pagParams2)) {
          it.copy(method = it.method, params = it.params)
        }

    verifyModel(
        PaginatedResult.serializer(),
        PaginatedResult(),
        PaginatedResult(),
        PaginatedResult(_meta = mapOf("k" to JsonNull), nextCursor = "c2")) {
          it.copy(_meta = it._meta, nextCursor = it.nextCursor)
        }
  }

  @Test
  fun testToolsPromptsResourcesAndCompletionModels() {
    val callParams1 = CallToolRequestParams(name = "tool1")
    val callParams2 =
        CallToolRequestParams(arguments = mapOf("arg" to JsonPrimitive("val")), name = "tool2")
    verifyModel(CallToolRequestParams.serializer(), callParams1, callParams1.copy(), callParams2) {
      it.copy(arguments = it.arguments, name = it.name)
    }

    verifyModel(
        CallToolRequest.serializer(),
        CallToolRequest(params = callParams1),
        CallToolRequest(params = callParams1),
        CallToolRequest(method = "tools/call", params = callParams2)) {
          it.copy(method = it.method, params = it.params)
        }

    verifyModel(
        CallToolResult.serializer(),
        CallToolResult(content = emptyList()),
        CallToolResult(content = emptyList()),
        CallToolResult(
            _meta = mapOf("m" to JsonNull),
            content = listOf(JsonPrimitive("out")),
            isError = false)) {
          it.copy(_meta = it._meta, content = it.content, isError = it.isError)
        }

    val schema1 = ToolInputSchema()
    val schema2 =
        ToolInputSchema(
            properties = mapOf("p" to JsonPrimitive("string")),
            required = listOf("p"),
            type = "object")
    verifyModel(ToolInputSchema.serializer(), schema1, schema1.copy(), schema2) {
      it.copy(properties = it.properties, required = it.required, type = it.type)
    }

    val tool1 = Tool(inputSchema = schema1, name = "t1")
    val tool2 = Tool(description = "desc", inputSchema = schema2, name = "t2")
    verifyModel(Tool.serializer(), tool1, tool1.copy(), tool2) {
      it.copy(description = it.description, inputSchema = it.inputSchema, name = it.name)
    }

    val listToolsParams1 = ListToolsRequestParams()
    val listToolsParams2 = ListToolsRequestParams(cursor = "next")
    verifyModel(
        ListToolsRequestParams.serializer(),
        listToolsParams1,
        listToolsParams1.copy(),
        listToolsParams2) {
          it.copy(cursor = it.cursor)
        }

    verifyModel(
        ListToolsRequest.serializer(),
        ListToolsRequest(),
        ListToolsRequest(),
        ListToolsRequest(method = "tools/list", params = listToolsParams2)) {
          it.copy(method = it.method, params = it.params)
        }

    verifyModel(
        ListToolsResult.serializer(),
        ListToolsResult(tools = emptyList()),
        ListToolsResult(tools = emptyList()),
        ListToolsResult(_meta = mapOf("m" to JsonNull), nextCursor = "c2", tools = listOf(tool1))) {
          it.copy(_meta = it._meta, nextCursor = it.nextCursor, tools = it.tools)
        }

    val compArg1 = CompleteRequestArgument(name = "a1", value = "v1")
    val compArg2 = CompleteRequestArgument(name = "a2", value = "v2")
    verifyModel(CompleteRequestArgument.serializer(), compArg1, compArg1.copy(), compArg2) {
      it.copy(name = it.name, value = it.value)
    }

    val compRef1 = CompleteRequestRef(type = "ref/prompt")
    val compRef2 = CompleteRequestRef(name = "p1", type = "ref/resource")
    verifyModel(CompleteRequestRef.serializer(), compRef1, compRef1.copy(), compRef2) {
      it.copy(name = it.name, type = it.type)
    }

    val compParams1 = CompleteRequestParams(argument = compArg1, ref = compRef1)
    val compParams2 = CompleteRequestParams(argument = compArg2, ref = compRef2)
    verifyModel(CompleteRequestParams.serializer(), compParams1, compParams1.copy(), compParams2) {
      it.copy(argument = it.argument, ref = it.ref)
    }

    verifyModel(
        CompleteRequest.serializer(),
        CompleteRequest(params = compParams1),
        CompleteRequest(params = compParams1),
        CompleteRequest(method = "completion/complete", params = compParams2)) {
          it.copy(method = it.method, params = it.params)
        }

    val compOut1 = CompleteResultCompletion(values = listOf("a"))
    val compOut2 = CompleteResultCompletion(hasMore = true, total = 10, values = listOf("a", "b"))
    verifyModel(CompleteResultCompletion.serializer(), compOut1, compOut1.copy(), compOut2) {
      it.copy(hasMore = it.hasMore, total = it.total, values = it.values)
    }

    verifyModel(
        CompleteResult.serializer(),
        CompleteResult(completion = compOut1),
        CompleteResult(completion = compOut1),
        CompleteResult(_meta = mapOf("k" to JsonNull), completion = compOut2)) {
          it.copy(_meta = it._meta, completion = it.completion)
        }

    val promptArg1 = PromptArgument(name = "pa1")
    val promptArg2 = PromptArgument(description = "d", name = "pa2", required = true)
    verifyModel(PromptArgument.serializer(), promptArg1, promptArg1.copy(), promptArg2) {
      it.copy(description = it.description, name = it.name, required = it.required)
    }

    val prompt1 = Prompt(name = "p1")
    val prompt2 = Prompt(arguments = listOf(promptArg2), description = "d", name = "p2")
    verifyModel(Prompt.serializer(), prompt1, prompt1.copy(), prompt2) {
      it.copy(arguments = it.arguments, description = it.description, name = it.name)
    }

    val pmContent1 = PromptMessageContent(type = "text")
    val pmContent2 =
        PromptMessageContent(text = "hi", type = "image", data = "ZGF0YQ==", mimeType = "image/png")
    verifyModel(PromptMessageContent.serializer(), pmContent1, pmContent1.copy(), pmContent2) {
      it.copy(text = it.text, type = it.type, data = it.data, mimeType = it.mimeType)
    }

    val pm1 = PromptMessage(content = pmContent1, role = Role.user)
    val pm2 = PromptMessage(content = pmContent2, role = Role.assistant)
    verifyModel(PromptMessage.serializer(), pm1, pm1.copy(), pm2) {
      it.copy(content = it.content, role = it.role)
    }

    verifyModel(
        PromptReference.serializer(),
        PromptReference(name = "pr1"),
        PromptReference(name = "pr1"),
        PromptReference(name = "pr2", type = "prompt")) {
          it.copy(name = it.name, type = it.type)
        }

    val gpParams1 = GetPromptRequestParams(name = "p1")
    val gpParams2 = GetPromptRequestParams(arguments = mapOf("k" to "v"), name = "p2")
    verifyModel(GetPromptRequestParams.serializer(), gpParams1, gpParams1.copy(), gpParams2) {
      it.copy(arguments = it.arguments, name = it.name)
    }

    verifyModel(
        GetPromptRequest.serializer(),
        GetPromptRequest(params = gpParams1),
        GetPromptRequest(params = gpParams1),
        GetPromptRequest(method = "prompts/get", params = gpParams2)) {
          it.copy(method = it.method, params = it.params)
        }

    verifyModel(
        GetPromptResult.serializer(),
        GetPromptResult(messages = emptyList()),
        GetPromptResult(messages = emptyList()),
        GetPromptResult(
            _meta = mapOf("k" to JsonNull), description = "d", messages = listOf(pm1))) {
          it.copy(_meta = it._meta, description = it.description, messages = it.messages)
        }

    val lpParams1 = ListPromptsRequestParams()
    val lpParams2 = ListPromptsRequestParams(cursor = "c")
    verifyModel(ListPromptsRequestParams.serializer(), lpParams1, lpParams1.copy(), lpParams2) {
      it.copy(cursor = it.cursor)
    }

    verifyModel(
        ListPromptsRequest.serializer(),
        ListPromptsRequest(),
        ListPromptsRequest(),
        ListPromptsRequest(method = "prompts/list", params = lpParams2)) {
          it.copy(method = it.method, params = it.params)
        }

    verifyModel(
        ListPromptsResult.serializer(),
        ListPromptsResult(prompts = emptyList()),
        ListPromptsResult(prompts = emptyList()),
        ListPromptsResult(
            _meta = mapOf("k" to JsonNull), nextCursor = "c", prompts = listOf(prompt1))) {
          it.copy(_meta = it._meta, nextCursor = it.nextCursor, prompts = it.prompts)
        }

    val lrtParams1 = ListResourceTemplatesRequestParams()
    val lrtParams2 = ListResourceTemplatesRequestParams(cursor = "c")
    verifyModel(
        ListResourceTemplatesRequestParams.serializer(),
        lrtParams1,
        lrtParams1.copy(),
        lrtParams2) {
          it.copy(cursor = it.cursor)
        }

    verifyModel(
        ListResourceTemplatesRequest.serializer(),
        ListResourceTemplatesRequest(),
        ListResourceTemplatesRequest(),
        ListResourceTemplatesRequest(method = "resources/templates/list", params = lrtParams2)) {
          it.copy(method = it.method, params = it.params)
        }

    verifyModel(
        ListResourceTemplatesResult.serializer(),
        ListResourceTemplatesResult(resourceTemplates = emptyList()),
        ListResourceTemplatesResult(resourceTemplates = emptyList()),
        ListResourceTemplatesResult(
            _meta = mapOf("k" to JsonNull),
            nextCursor = "c",
            resourceTemplates = listOf(ResourceTemplate(name = "t", uriTemplate = "u")))) {
          it.copy(
              _meta = it._meta,
              nextCursor = it.nextCursor,
              resourceTemplates = it.resourceTemplates)
        }

    val lrParams1 = ListResourcesRequestParams()
    val lrParams2 = ListResourcesRequestParams(cursor = "c")
    verifyModel(ListResourcesRequestParams.serializer(), lrParams1, lrParams1.copy(), lrParams2) {
      it.copy(cursor = it.cursor)
    }

    verifyModel(
        ListResourcesRequest.serializer(),
        ListResourcesRequest(),
        ListResourcesRequest(),
        ListResourcesRequest(method = "resources/list", params = lrParams2)) {
          it.copy(method = it.method, params = it.params)
        }

    verifyModel(
        ListResourcesResult.serializer(),
        ListResourcesResult(resources = emptyList()),
        ListResourcesResult(resources = emptyList()),
        ListResourcesResult(
            _meta = mapOf("k" to JsonNull),
            nextCursor = "c",
            resources = listOf(Resource(name = "r", uri = "u")))) {
          it.copy(_meta = it._meta, nextCursor = it.nextCursor, resources = it.resources)
        }

    val lrootsParams1 = ListRootsRequestParams()
    val lrootsParams2 = ListRootsRequestParams(_meta = mapOf("k" to JsonNull))
    verifyModel(
        ListRootsRequestParams.serializer(), lrootsParams1, lrootsParams1.copy(), lrootsParams2) {
          it.copy(_meta = it._meta)
        }

    verifyModel(
        ListRootsRequest.serializer(),
        ListRootsRequest(),
        ListRootsRequest(),
        ListRootsRequest(method = "roots/list", params = lrootsParams2)) {
          it.copy(method = it.method, params = it.params)
        }

    verifyModel(
        ListRootsResult.serializer(),
        ListRootsResult(roots = emptyList()),
        ListRootsResult(roots = emptyList()),
        ListRootsResult(
            _meta = mapOf("k" to JsonNull), roots = listOf(Root(name = "r", uri = "u")))) {
          it.copy(_meta = it._meta, roots = it.roots)
        }

    val rrParams1 = ReadResourceRequestParams(uri = "file:///a")
    val rrParams2 = ReadResourceRequestParams(uri = "file:///b")
    verifyModel(ReadResourceRequestParams.serializer(), rrParams1, rrParams1.copy(), rrParams2) {
      it.copy(uri = it.uri)
    }

    verifyModel(
        ReadResourceRequest.serializer(),
        ReadResourceRequest(params = rrParams1),
        ReadResourceRequest(params = rrParams1),
        ReadResourceRequest(method = "resources/read", params = rrParams2)) {
          it.copy(method = it.method, params = it.params)
        }

    verifyModel(
        ReadResourceResult.serializer(),
        ReadResourceResult(contents = emptyList()),
        ReadResourceResult(contents = emptyList()),
        ReadResourceResult(
            _meta = mapOf("k" to JsonNull),
            contents = listOf(ResourceContents(uri = "file:///a", text = "hi")))) {
          it.copy(_meta = it._meta, contents = it.contents)
        }

    val subParams1 = SubscribeRequestParams(uri = "file:///a")
    val subParams2 = SubscribeRequestParams(uri = "file:///b")
    verifyModel(SubscribeRequestParams.serializer(), subParams1, subParams1.copy(), subParams2) {
      it.copy(uri = it.uri)
    }

    verifyModel(
        SubscribeRequest.serializer(),
        SubscribeRequest(params = subParams1),
        SubscribeRequest(params = subParams1),
        SubscribeRequest(method = "resources/subscribe", params = subParams2)) {
          it.copy(method = it.method, params = it.params)
        }

    val unsubParams1 = UnsubscribeRequestParams(uri = "file:///a")
    val unsubParams2 = UnsubscribeRequestParams(uri = "file:///b")
    verifyModel(
        UnsubscribeRequestParams.serializer(), unsubParams1, unsubParams1.copy(), unsubParams2) {
          it.copy(uri = it.uri)
        }

    verifyModel(
        UnsubscribeRequest.serializer(),
        UnsubscribeRequest(params = unsubParams1),
        UnsubscribeRequest(params = unsubParams1),
        UnsubscribeRequest(method = "resources/unsubscribe", params = unsubParams2)) {
          it.copy(method = it.method, params = it.params)
        }
  }

  @Test
  fun testSamplingNotificationsAndSealedHierarchies() {
    val smContent1 = SamplingMessageContent(type = "text")
    val smContent2 =
        SamplingMessageContent(
            text = "hello", type = "image", data = "ZGF0YQ==", mimeType = "image/png")
    verifyModel(SamplingMessageContent.serializer(), smContent1, smContent1.copy(), smContent2) {
      it.copy(text = it.text, type = it.type, data = it.data, mimeType = it.mimeType)
    }

    val sm1 = SamplingMessage(content = smContent1, role = Role.user)
    val sm2 = SamplingMessage(content = smContent2, role = Role.assistant)
    verifyModel(SamplingMessage.serializer(), sm1, sm1.copy(), sm2) {
      it.copy(content = it.content, role = it.role)
    }

    val hint1 = ModelHint(name = "gemini-2.5-pro")
    val hint2 = ModelHint(name = "gemini-2.5-flash")
    verifyModel(ModelHint.serializer(), hint1, hint1.copy(), hint2) { it.copy(name = it.name) }

    val prefs1 = ModelPreferences()
    val prefs2 =
        ModelPreferences(
            costPriority = 0.5,
            hints = listOf(hint1),
            intelligencePriority = 0.9,
            speedPriority = 0.7)
    verifyModel(ModelPreferences.serializer(), prefs1, prefs1.copy(), prefs2) {
      it.copy(
          costPriority = it.costPriority,
          hints = it.hints,
          intelligencePriority = it.intelligencePriority,
          speedPriority = it.speedPriority)
    }

    val cmParams1 = CreateMessageRequestParams(maxTokens = 100, messages = listOf(sm1))
    val cmParams2 =
        CreateMessageRequestParams(
            includeContext = "allServers",
            maxTokens = 500,
            messages = listOf(sm2),
            metadata = mapOf("k" to JsonNull),
            modelPreferences = prefs2,
            stopSequences = listOf("STOP"),
            systemPrompt = "sys",
            temperature = 0.2)
    verifyModel(CreateMessageRequestParams.serializer(), cmParams1, cmParams1.copy(), cmParams2) {
      it.copy(
          includeContext = it.includeContext,
          maxTokens = it.maxTokens,
          messages = it.messages,
          metadata = it.metadata,
          modelPreferences = it.modelPreferences,
          stopSequences = it.stopSequences,
          systemPrompt = it.systemPrompt,
          temperature = it.temperature)
    }

    val cmReq1 = CreateMessageRequest(params = cmParams1)
    val cmReq2 = CreateMessageRequest(method = "sampling/createMessage", params = cmParams2)
    verifyModel(CreateMessageRequest.serializer(), cmReq1, cmReq1.copy(), cmReq2) {
      it.copy(method = it.method, params = it.params)
    }

    val cmRes1 = CreateMessageResult(content = smContent1, model = "gemini", role = Role.assistant)
    val cmRes2 =
        CreateMessageResult(
            _meta = mapOf("k" to JsonNull),
            content = smContent2,
            model = "gemini-pro",
            role = Role.user,
            stopReason = "endTurn")
    verifyModel(CreateMessageResult.serializer(), cmRes1, cmRes1.copy(), cmRes2) {
      it.copy(
          _meta = it._meta,
          content = it.content,
          model = it.model,
          role = it.role,
          stopReason = it.stopReason)
    }

    val cancelParams1 = CancelledNotificationParams(requestId = "1")
    val cancelParams2 = CancelledNotificationParams(reason = "timeout", requestId = "2")
    verifyModel(
        CancelledNotificationParams.serializer(),
        cancelParams1,
        cancelParams1.copy(),
        cancelParams2) {
          it.copy(reason = it.reason, requestId = it.requestId)
        }

    val cancelNotif1 = CancelledNotification(params = cancelParams1)
    val cancelNotif2 =
        CancelledNotification(method = "notifications/cancelled", params = cancelParams2)
    verifyModel(
        CancelledNotification.serializer(), cancelNotif1, cancelNotif1.copy(), cancelNotif2) {
          it.copy(method = it.method, params = it.params)
        }

    val initNotifParams1 = InitializedNotificationParams()
    val initNotifParams2 = InitializedNotificationParams(_meta = mapOf("k" to JsonNull))
    verifyModel(
        InitializedNotificationParams.serializer(),
        initNotifParams1,
        initNotifParams1.copy(),
        initNotifParams2) {
          it.copy(_meta = it._meta)
        }

    val initNotif1 = InitializedNotification()
    val initNotif2 =
        InitializedNotification(method = "notifications/initialized", params = initNotifParams2)
    verifyModel(InitializedNotification.serializer(), initNotif1, initNotif1.copy(), initNotif2) {
      it.copy(method = it.method, params = it.params)
    }

    val logParams1 =
        LoggingMessageNotificationParams(data = JsonPrimitive("msg"), level = LoggingLevel.info)
    val logParams2 =
        LoggingMessageNotificationParams(
            data = JsonPrimitive("err"), level = LoggingLevel.error, logger = "main")
    verifyModel(
        LoggingMessageNotificationParams.serializer(), logParams1, logParams1.copy(), logParams2) {
          it.copy(data = it.data, level = it.level, logger = it.logger)
        }

    val logNotif1 = LoggingMessageNotification(params = logParams1)
    val logNotif2 =
        LoggingMessageNotification(method = "notifications/message", params = logParams2)
    verifyModel(LoggingMessageNotification.serializer(), logNotif1, logNotif1.copy(), logNotif2) {
      it.copy(method = it.method, params = it.params)
    }

    val pingParams1 = PingRequestParams()
    val pingParams2 = PingRequestParams(_meta = mapOf("k" to JsonNull))
    verifyModel(PingRequestParams.serializer(), pingParams1, pingParams1.copy(), pingParams2) {
      it.copy(_meta = it._meta)
    }

    val pingReq1 = PingRequest()
    val pingReq2 = PingRequest(method = "ping", params = pingParams2)
    verifyModel(PingRequest.serializer(), pingReq1, pingReq1.copy(), pingReq2) {
      it.copy(method = it.method, params = it.params)
    }

    val progParams1 = ProgressNotificationParams(progressToken = "t1", progress = 0.5)
    val progParams2 = ProgressNotificationParams(progressToken = "t2", progress = 1.0, total = 10.0)
    verifyModel(
        ProgressNotificationParams.serializer(), progParams1, progParams1.copy(), progParams2) {
          it.copy(progressToken = it.progressToken, progress = it.progress, total = it.total)
        }

    val progNotif1 = ProgressNotification(params = progParams1)
    val progNotif2 = ProgressNotification(method = "notifications/progress", params = progParams2)
    verifyModel(ProgressNotification.serializer(), progNotif1, progNotif1.copy(), progNotif2) {
      it.copy(method = it.method, params = it.params)
    }

    val plcParams1 = PromptListChangedNotificationParams()
    val plcParams2 = PromptListChangedNotificationParams(_meta = mapOf("k" to JsonNull))
    verifyModel(
        PromptListChangedNotificationParams.serializer(),
        plcParams1,
        plcParams1.copy(),
        plcParams2) {
          it.copy(_meta = it._meta)
        }

    val plcNotif1 = PromptListChangedNotification()
    val plcNotif2 =
        PromptListChangedNotification(
            method = "notifications/prompts/list_changed", params = plcParams2)
    verifyModel(
        PromptListChangedNotification.serializer(), plcNotif1, plcNotif1.copy(), plcNotif2) {
          it.copy(method = it.method, params = it.params)
        }

    val rlcParams1 = ResourceListChangedNotificationParams()
    val rlcParams2 = ResourceListChangedNotificationParams(_meta = mapOf("k" to JsonNull))
    verifyModel(
        ResourceListChangedNotificationParams.serializer(),
        rlcParams1,
        rlcParams1.copy(),
        rlcParams2) {
          it.copy(_meta = it._meta)
        }

    val rlcNotif1 = ResourceListChangedNotification()
    val rlcNotif2 =
        ResourceListChangedNotification(
            method = "notifications/resources/list_changed", params = rlcParams2)
    verifyModel(
        ResourceListChangedNotification.serializer(), rlcNotif1, rlcNotif1.copy(), rlcNotif2) {
          it.copy(method = it.method, params = it.params)
        }

    val ruParams1 = ResourceUpdatedNotificationParams(uri = "file:///a")
    val ruParams2 = ResourceUpdatedNotificationParams(uri = "file:///b")
    verifyModel(
        ResourceUpdatedNotificationParams.serializer(), ruParams1, ruParams1.copy(), ruParams2) {
          it.copy(uri = it.uri)
        }

    val ruNotif1 = ResourceUpdatedNotification(params = ruParams1)
    val ruNotif2 =
        ResourceUpdatedNotification(method = "notifications/resources/updated", params = ruParams2)
    verifyModel(ResourceUpdatedNotification.serializer(), ruNotif1, ruNotif1.copy(), ruNotif2) {
      it.copy(method = it.method, params = it.params)
    }

    val rootsLcParams1 = RootsListChangedNotificationParams()
    val rootsLcParams2 = RootsListChangedNotificationParams(_meta = mapOf("k" to JsonNull))
    verifyModel(
        RootsListChangedNotificationParams.serializer(),
        rootsLcParams1,
        rootsLcParams1.copy(),
        rootsLcParams2) {
          it.copy(_meta = it._meta)
        }

    val rootsLcNotif1 = RootsListChangedNotification()
    val rootsLcNotif2 =
        RootsListChangedNotification(
            method = "notifications/roots/list_changed", params = rootsLcParams2)
    verifyModel(
        RootsListChangedNotification.serializer(),
        rootsLcNotif1,
        rootsLcNotif1.copy(),
        rootsLcNotif2) {
          it.copy(method = it.method, params = it.params)
        }

    val setLevelParams1 = SetLevelRequestParams(level = LoggingLevel.debug)
    val setLevelParams2 = SetLevelRequestParams(level = LoggingLevel.warning)
    verifyModel(
        SetLevelRequestParams.serializer(),
        setLevelParams1,
        setLevelParams1.copy(),
        setLevelParams2) {
          it.copy(level = it.level)
        }

    val setLevelReq1 = SetLevelRequest(params = setLevelParams1)
    val setLevelReq2 = SetLevelRequest(method = "logging/setLevel", params = setLevelParams2)
    verifyModel(SetLevelRequest.serializer(), setLevelReq1, setLevelReq1.copy(), setLevelReq2) {
      it.copy(method = it.method, params = it.params)
    }

    val tlcParams1 = ToolListChangedNotificationParams()
    val tlcParams2 = ToolListChangedNotificationParams(_meta = mapOf("k" to JsonNull))
    verifyModel(
        ToolListChangedNotificationParams.serializer(), tlcParams1, tlcParams1.copy(), tlcParams2) {
          it.copy(_meta = it._meta)
        }

    val tlcNotif1 = ToolListChangedNotification()
    val tlcNotif2 =
        ToolListChangedNotification(
            method = "notifications/tools/list_changed", params = tlcParams2)
    verifyModel(ToolListChangedNotification.serializer(), tlcNotif1, tlcNotif1.copy(), tlcNotif2) {
      it.copy(method = it.method, params = it.params)
    }

    val clientNotifications: List<ClientNotification> =
        listOf(cancelNotif1, initNotif1, progNotif1, rootsLcNotif1)
    val serverNotifications: List<ServerNotification> =
        listOf(cancelNotif1, logNotif1, progNotif1, plcNotif1, rlcNotif1, ruNotif1, tlcNotif1)
    val clientRequests: List<ClientRequest> = listOf(pingReq1, setLevelReq1)
    val serverRequests: List<ServerRequest> = listOf(pingReq1, cmReq1)
    val clientResults: List<ClientResult> = listOf(EmptyResult(), Result(), cmRes1)
    val serverResults: List<ServerResult> = listOf(EmptyResult(), Result())

    assertEquals(4, clientNotifications.size)
    assertEquals(7, serverNotifications.size)
    assertEquals(2, clientRequests.size)
    assertEquals(2, serverRequests.size)
    assertEquals(3, clientResults.size)
    assertEquals(2, serverResults.size)
    assertNotNull(ClientNotification.serializer())
    assertNotNull(ServerNotification.serializer())
    assertNotNull(ClientRequest.serializer())
    assertNotNull(ServerRequest.serializer())
    assertNotNull(ClientResult.serializer())
    assertNotNull(ServerResult.serializer())
    assertNotNull(JSONRPCMessage.serializer())
  }
}
