/**
 * Model Context Protocol (MCP) JSON-RPC peer connection manager and request/notification router.
 */
package org.cdd.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import openapi.text

/**
 * Represents a connection to an MCP peer over a [Transport].
 *
 * @property transport The transport used for communication.
 */
class McpPeer(val transport: Transport) {
  /** True if the initialize sequence has completed. */
  var isInitialized = false
    private set

  /** Last pagination cursor processed by this peer, if any. */
  var lastCursor: String? = null
    private set

  /** Allowed root URI prefixes for root boundary enforcement. */
  val allowedRoots: MutableList<String> = mutableListOf("file://", "openapi://", "mcp://", "/")

  /** Set of tools explicitly denied by human-in-the-loop policy. */
  val deniedTools: MutableSet<String> = mutableSetOf()

  private val pendingRequests = mutableMapOf<String, (JsonElement) -> Unit>()
  private val notificationHandlers = mutableMapOf<String, (JsonElement) -> Unit>()
  private val requestHandlers = mutableMapOf<String, (JsonElement) -> kotlin.Result<JsonElement>>()

  @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
  private val jsonParser =
      kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        explicitNulls = false
      }
  private val jsonEncoder = kotlinx.serialization.json.Json { encodeDefaults = true }

  init {
    transport.onReceive { message -> handleMessage(message) }

    onRequest("initialize") { params ->
      val requestParams =
          jsonParser.decodeFromJsonElement(InitializeRequestParams.serializer(), params)
      val actualVersion = requestParams.protocolVersion

      isInitialized = false

      val result =
          InitializeResult(
              protocolVersion = actualVersion,
              capabilities =
                  ServerCapabilities(
                      tools = ServerCapabilitiesTools(listChanged = true),
                      resources =
                          ServerCapabilitiesResources(listChanged = true, subscribe = true)),
              serverInfo = Implementation(name = "cdd-kotlin-mcp", version = "0.0.4"),
              instructions = "cdd-kotlin MCP server")

      jsonEncoder.encodeToJsonElement(InitializeResult.serializer(), result)
    }

    onNotification("notifications/initialized") { isInitialized = true }

    onRequest("ping") { _ -> JsonObject(emptyMap()) }

    onNotification("notifications/cancelled") { params ->
      val reqId = params.text("requestId")
      if (reqId != null) {
        pendingRequests.remove(reqId)
      }
    }

    onNotification("notifications/progress") { _ -> }

    onRequest("sampling/createMessage") { _ -> JsonObject(emptyMap()) }
  }

  /**
   * Handles paginated responses by recording and normalizing the active cursor.
   *
   * @param cursor The cursor to fetch next.
   * @return The normalized cursor string, or `null` if blank/absent.
   */
  fun handlePagination(cursor: String?): String? {
    lastCursor = cursor?.takeIf { it.isNotBlank() }
    return lastCursor
  }

  /**
   * Verifies that [uri] does not escape the allowed root boundary via path traversal (`..`).
   *
   * @param uri The URI to check against roots.
   * @return `true` if the URI is safe and within allowed boundaries, `false` otherwise.
   */
  fun checkRootBoundary(uri: String): Boolean {
    if (uri.contains("..")) return false
    return allowedRoots.isEmpty() || allowedRoots.any { uri.startsWith(it) } || !uri.contains("://")
  }

  /**
   * Normalizes and resolves an MCP resource or file URI.
   *
   * @param uri The custom URI to resolve.
   * @return The resolved URI string.
   */
  fun resolveUri(uri: String): String {
    return uri.trim()
  }

  /**
   * Checks whether [toolName] is approved for execution under human-in-the-loop policy.
   *
   * @param toolName The tool to check approval for.
   * @return `true` if the tool is approved and non-blank, `false` if denied.
   */
  fun checkToolApproval(toolName: String): Boolean {
    return toolName.isNotBlank() && !deniedTools.contains(toolName)
  }

  /**
   * Sends a JSON-RPC request to the remote peer and registers [onResponse] for the reply.
   *
   * @param id Unique request identifier.
   * @param method JSON-RPC method name.
   * @param params Optional request parameters.
   * @param onResponse Callback invoked when the matching response arrives.
   */
  fun sendRequest(
      id: String,
      method: String,
      params: JsonElement? = null,
      onResponse: (JsonElement) -> Unit
  ) {
    pendingRequests[id] = onResponse
    val req = JSONRPCRequest(id = id, method = method, params = params)
    transport.send(jsonEncoder.encodeToJsonElement(JSONRPCRequest.serializer(), req))
  }

  /**
   * Handles an incoming JSON-RPC message (request, response, or notification).
   *
   * @param message The parsed [JsonElement] message.
   */
  fun handleMessage(message: JsonElement) {
    if (message !is JsonObject) return

    val id = message.text("id")
    val method = message.text("method")

    if (id != null) {
      if (method != null) {
        handleRequest(id, method, message["params"])
      } else {
        val callback = pendingRequests.remove(id)
        callback?.invoke(message)
      }
    } else if (method != null) {
      handleNotification(method, message["params"])
    }
  }

  private fun handleRequest(id: String, method: String, params: JsonElement?) {
    val handler = requestHandlers[method]
    val outcome: kotlin.Result<JsonElement> =
        if (handler != null) {
          handler(params ?: JsonObject(emptyMap()))
        } else {
          kotlin.Result.failure(
              JsonRpcException(JsonRpcErrorCodes.METHOD_NOT_FOUND, "Method not found: $method"))
        }

    outcome.fold(
        onSuccess = { result ->
          val response = JSONRPCResponse(id = id, result = result)
          val jsonResponse =
              kotlinx.serialization.json.Json.encodeToJsonElement(
                  JSONRPCResponse.serializer(), response)
          transport.send(jsonResponse)
        },
        onFailure = { err ->
          val errorPayload =
              when (err) {
                is JsonRpcException -> JSONRPCErrorError(code = err.code, message = err.message)
                else ->
                    JSONRPCErrorError(
                        code = JsonRpcErrorCodes.INTERNAL_ERROR,
                        message = err.message ?: "Internal error")
              }
          val error = JSONRPCError(id = id, error = errorPayload)
          val jsonError =
              kotlinx.serialization.json.Json.encodeToJsonElement(JSONRPCError.serializer(), error)
          transport.send(jsonError)
        })
  }

  private fun handleNotification(method: String, params: JsonElement?) {
    val handler = notificationHandlers[method]
    handler?.invoke(params ?: JsonObject(emptyMap()))
  }

  /**
   * Registers a fallible request handler for [method] returning a [kotlin.Result].
   *
   * @param method The JSON-RPC method name.
   * @param handler Callback transforming request params into a `kotlin.Result<JsonElement>`.
   */
  fun onRequestResult(method: String, handler: (JsonElement) -> kotlin.Result<JsonElement>) {
    requestHandlers[method] = handler
  }

  /**
   * Registers a request handler for [method].
   *
   * @param method The JSON-RPC method name.
   * @param handler Callback transforming request params into a result [JsonElement].
   */
  fun onRequest(method: String, handler: (JsonElement) -> JsonElement) {
    requestHandlers[method] = { params -> kotlin.Result.success(handler(params)) }
  }

  /**
   * Registers a notification handler for [method].
   *
   * @param method The JSON-RPC notification method name.
   * @param handler Callback invoked with notification params.
   */
  fun onNotification(method: String, handler: (JsonElement) -> Unit) {
    notificationHandlers[method] = handler
  }
}
