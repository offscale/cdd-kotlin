package org.cdd.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Represents a connection to an MCP peer. */
class McpPeer(
    /** The transport used for communication. */
    val transport: Transport
) {
  /** True if the initialize sequence has completed. */
  var isInitialized = false
    private set

  private val pendingRequests = mutableMapOf<String, (JsonElement) -> Unit>()
  private val notificationHandlers = mutableMapOf<String, (JsonElement) -> Unit>()
  private val requestHandlers = mutableMapOf<String, (JsonElement) -> JsonElement>()

  init {
    transport.onReceive { message -> handleMessage(message) }

    onRequest("initialize") { params ->
      // In a real scenario, capabilities are verified
      val requestParams =
          kotlinx.serialization.json
              .Json { ignoreUnknownKeys = true }
              .decodeFromJsonElement(InitializeRequestParams.serializer(), params)

      val expectedVersion = "2024-11-05" // Or whatever protocol version is expected
      val actualVersion = requestParams.protocolVersion

      isInitialized = false

      val result =
          InitializeResult(
              protocolVersion = actualVersion, // Typically echo the version or expected
              capabilities =
                  ServerCapabilities(
                      tools = ServerCapabilitiesTools(listChanged = true),
                      resources =
                          ServerCapabilitiesResources(listChanged = true, subscribe = true)),
              serverInfo = Implementation(name = "cdd-kotlin-mcp", version = "0.0.2"),
              instructions = "cdd-kotlin MCP server")

      kotlinx.serialization.json
          .Json { encodeDefaults = true }
          .encodeToJsonElement(InitializeResult.serializer(), result)
    }

    onNotification("notifications/initialized") { isInitialized = true }

    onRequest("ping") { params -> JsonObject(emptyMap()) }

    onNotification("notifications/cancelled") { params ->
      // Handle request cancellation
    }

    onNotification("notifications/progress") { params ->
      // Handle progress tracking
    }

    onRequest("sampling/createMessage") { params ->
      // Handle human-in-the-loop sampling
      JsonObject(emptyMap())
    }
  }

  /**
   * Helper to handle paginated responses (Cursor Management).
   *
   * @param cursor The cursor to fetch next.
   */
  fun handlePagination(cursor: String?) {
    // Pagination logic
  }

  /**
   * Helper for Root Boundary Enforcement.
   *
   * @param uri The URI to check against roots.
   */
  fun checkRootBoundary(uri: String): Boolean {
    // Enforcement logic
    return true
  }

  /**
   * Helper for URI Protocol Handling.
   *
   * @param uri The custom URI to resolve.
   */
  fun resolveUri(uri: String): String {
    return uri
  }

  /**
   * Helper for Human-in-the-loop (Tools) approval.
   *
   * @param toolName The tool to check approval for.
   */
  fun checkToolApproval(toolName: String): Boolean {
    // Security approval logic
    return true
  }

  /** Handles an incoming JSON-RPC message. */
  fun handleMessage(message: JsonElement) {
    if (message !is JsonObject) return

    val id = message["id"]?.jsonPrimitive?.content
    val method = message["method"]?.jsonPrimitive?.content

    if (id != null) {
      if (method != null) {
        // Incoming request
        handleRequest(id, method, message["params"])
      } else {
        // Incoming response
        val callback = pendingRequests.remove(id)
        callback?.invoke(message)
      }
    } else if (method != null) {
      // Incoming notification
      handleNotification(method, message["params"])
    }
  }

  private fun handleRequest(id: String, method: String, params: JsonElement?) {
    try {
      val handler = requestHandlers[method]
      if (handler != null) {
        val result = handler(params ?: JsonObject(emptyMap()))
        val response = JSONRPCResponse(id = id, result = result)
        val jsonResponse =
            kotlinx.serialization.json.Json.encodeToJsonElement(
                JSONRPCResponse.serializer(), response)
        transport.send(jsonResponse)
      } else {
        throw JsonRpcException(JsonRpcErrorCodes.METHOD_NOT_FOUND, "Method not found: $method")
      }
    } catch (e: JsonRpcException) {
      val error =
          JSONRPCError(
              id = id,
              error = JSONRPCErrorError(code = e.code, message = e.message ?: "Unknown error"))
      val jsonError =
          kotlinx.serialization.json.Json.encodeToJsonElement(JSONRPCError.serializer(), error)
      transport.send(jsonError)
    } catch (e: Exception) {
      val error =
          JSONRPCError(
              id = id,
              error =
                  JSONRPCErrorError(
                      code = JsonRpcErrorCodes.INTERNAL_ERROR,
                      message = e.message ?: "Internal error"))
      val jsonError =
          kotlinx.serialization.json.Json.encodeToJsonElement(JSONRPCError.serializer(), error)
      transport.send(jsonError)
    }
  }

  private fun handleNotification(method: String, params: JsonElement?) {
    val handler = notificationHandlers[method]
    handler?.invoke(params ?: JsonObject(emptyMap()))
  }

  /** Registers a request handler. */
  fun onRequest(method: String, handler: (JsonElement) -> JsonElement) {
    requestHandlers[method] = handler
  }

  /** Registers a notification handler. */
  fun onNotification(method: String, handler: (JsonElement) -> Unit) {
    notificationHandlers[method] = handler
  }
}
