/** Unit and integration tests in [McpPeerTest]. */
package org.cdd.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DummyTransport : Transport {
  val sentMessages = mutableListOf<JsonElement>()
  private var receiveListener: ((JsonElement) -> Unit)? = null

  override fun send(message: JsonElement) {
    sentMessages.add(message)
  }

  override fun onReceive(listener: (JsonElement) -> Unit) {
    receiveListener = listener
  }

  override fun onError(listener: (Exception) -> Unit) {}

  override fun onClose(listener: () -> Unit) {}

  override fun close() {}

  fun simulateReceive(message: JsonElement) {
    receiveListener?.invoke(message)
  }
}

class McpPeerTest {
  @Test
  fun testPeer() {
    val transport = DummyTransport()
    val peer = McpPeer(transport)

    var notificationReceived = false
    peer.onNotification("testNotif") { params -> notificationReceived = true }

    var requestHandled = false
    peer.onRequest("testMethod") { params ->
      requestHandled = true
      JsonObject(emptyMap())
    }

    peer.onRequestResult("errorMethod") { _ ->
      kotlin.Result.failure(RuntimeException("custom error"))
    }

    transport.simulateReceive(
        Json.parseToJsonElement("""{"jsonrpc":"2.0", "method": "testNotif", "params": {}}"""))
    assertTrue(notificationReceived)

    // Test successful request
    transport.simulateReceive(
        Json.parseToJsonElement(
            """{"jsonrpc":"2.0", "id": "1", "method": "testMethod", "params": {}}"""))
    assertTrue(requestHandled)
    org.junit.jupiter.api.Assertions.assertEquals(1, transport.sentMessages.size)
    val successResponse = transport.sentMessages[0] as JsonObject
    org.junit.jupiter.api.Assertions.assertEquals(
        "1", successResponse["id"]?.let { it as JsonPrimitive }?.content)
    assertTrue(successResponse.containsKey("result"))

    // Test response parsing
    transport.simulateReceive(
        Json.parseToJsonElement("""{"jsonrpc":"2.0", "id": "1", "result": {}}"""))

    // Test missing method
    transport.simulateReceive(
        Json.parseToJsonElement(
            """{"jsonrpc":"2.0", "id": "2", "method": "missingMethod", "params": {}}"""))
    org.junit.jupiter.api.Assertions.assertEquals(2, transport.sentMessages.size)
    val missingError = transport.sentMessages[1] as JsonObject
    assertTrue(missingError.containsKey("error"))
    val missingErrorObj = missingError["error"] as JsonObject
    org.junit.jupiter.api.Assertions.assertEquals(
        JsonRpcErrorCodes.METHOD_NOT_FOUND,
        (missingErrorObj["code"] as JsonPrimitive).content.toInt())

    // Test runtime exception
    transport.simulateReceive(
        Json.parseToJsonElement(
            """{"jsonrpc":"2.0", "id": "3", "method": "errorMethod", "params": {}}"""))
    org.junit.jupiter.api.Assertions.assertEquals(3, transport.sentMessages.size)
    val runtimeError = transport.sentMessages[2] as JsonObject
    assertTrue(runtimeError.containsKey("error"))
    val runtimeErrorObj = runtimeError["error"] as JsonObject
    org.junit.jupiter.api.Assertions.assertEquals(
        JsonRpcErrorCodes.INTERNAL_ERROR,
        (runtimeErrorObj["code"] as JsonPrimitive).content.toInt())
    org.junit.jupiter.api.Assertions.assertEquals(
        "custom error", (runtimeErrorObj["message"] as JsonPrimitive).content)

    transport.simulateReceive(JsonPrimitive("invalid"))

    // Test coverage for pagination, root boundary, uri resolution, and tool approval
    peer.handlePagination("cursor")
    org.junit.jupiter.api.Assertions.assertEquals("cursor", peer.lastCursor)
    org.junit.jupiter.api.Assertions.assertNull(peer.handlePagination(null))
    org.junit.jupiter.api.Assertions.assertNull(peer.handlePagination("   "))
    assertTrue(peer.checkRootBoundary("uri"))
    assertTrue(peer.checkRootBoundary("file:///tmp/test"))
    org.junit.jupiter.api.Assertions.assertFalse(peer.checkRootBoundary("file:///tmp/../secret"))
    org.junit.jupiter.api.Assertions.assertFalse(peer.checkRootBoundary("http://evil.com/data"))
    peer.allowedRoots.clear()
    assertTrue(peer.checkRootBoundary("http://allowed-when-empty.com"))
    org.junit.jupiter.api.Assertions.assertEquals("uri", peer.resolveUri("  uri  "))
    assertTrue(peer.checkToolApproval("toolName"))
    org.junit.jupiter.api.Assertions.assertFalse(peer.checkToolApproval("   "))
    peer.deniedTools.add("blockedTool")
    org.junit.jupiter.api.Assertions.assertFalse(peer.checkToolApproval("blockedTool"))

    // Test sendRequest and matching response callback
    var receivedReply: JsonElement? = null
    peer.sendRequest("req-99", "ping") { reply -> receivedReply = reply }
    transport.simulateReceive(
        Json.parseToJsonElement("""{"jsonrpc":"2.0", "id": "req-99", "result": {"ok": true}}"""))
    org.junit.jupiter.api.Assertions.assertNotNull(receivedReply)

    // Test request without params and handler returning failure with null message
    peer.onRequestResult("nullMsgError") { kotlin.Result.failure(RuntimeException()) }
    transport.simulateReceive(
        Json.parseToJsonElement("""{"jsonrpc":"2.0", "id": "4", "method": "testMethod"}"""))
    transport.simulateReceive(
        Json.parseToJsonElement("""{"jsonrpc":"2.0", "id": "5", "method": "nullMsgError"}"""))

    // Test notification without params, unknown notification, and object with neither id nor method
    transport.simulateReceive(
        Json.parseToJsonElement("""{"jsonrpc":"2.0", "method": "testNotif"}"""))
    transport.simulateReceive(
        Json.parseToJsonElement("""{"jsonrpc":"2.0", "method": "unknownNotif"}"""))
    transport.simulateReceive(Json.parseToJsonElement("""{"jsonrpc":"2.0"}"""))

    // Test notifications/cancelled handler (with non-JsonObject params as well)
    transport.simulateReceive(
        Json.parseToJsonElement(
            """{"jsonrpc":"2.0", "method": "notifications/cancelled", "params": {"requestId": "cancel-me"}}"""))
    transport.simulateReceive(
        Json.parseToJsonElement(
            """{"jsonrpc":"2.0", "method": "notifications/cancelled", "params": {}}"""))
    transport.simulateReceive(
        Json.parseToJsonElement(
            """{"jsonrpc":"2.0", "method": "notifications/cancelled", "params": [1]}"""))
  }

  @Test
  fun testStdioTransportAndMcpClientGenerator() {
    val unconfigured = StdioTransportImpl()
    unconfigured.close()
    unconfigured.simulateReceive(JsonObject(emptyMap()))
    val unconfLines = ArrayDeque(listOf("""{"jsonrpc":"2.0","method":"ping"}""", "{bad", null))
    unconfigured.startReading { unconfLines.removeFirstOrNull() }
    unconfigured.startReadingResult {
      kotlin.Result.failure(RuntimeException("unconfigured reader error"))
    }

    val stdio = StdioTransportImpl()
    val received = mutableListOf<JsonElement>()
    val errors = mutableListOf<Exception>()
    var closed = false

    stdio.onReceive { received.add(it) }
    stdio.onError { errors.add(it) }
    stdio.onClose { closed = true }
    stdio.send(JsonObject(emptyMap()))
    stdio.simulateReceive(JsonObject(emptyMap()))
    org.junit.jupiter.api.Assertions.assertEquals(1, received.size)

    val lines = ArrayDeque(listOf("", """{"jsonrpc":"2.0","method":"ping"}""", "{bad json", null))
    stdio.startReading { lines.removeFirstOrNull() }
    org.junit.jupiter.api.Assertions.assertEquals(2, received.size)
    org.junit.jupiter.api.Assertions.assertEquals(1, errors.size)
    assertTrue(closed)

    var closed2 = false
    stdio.onClose { closed2 = true }
    stdio.startReadingResult { kotlin.Result.failure(RuntimeException("reader error")) }
    org.junit.jupiter.api.Assertions.assertEquals(2, errors.size)
    assertTrue(closed2)

    val files =
        McpClientGenerator.generateMcpFiles(
            "org.example",
            domain.OpenApiDefinition(
                info = domain.Info("T", "1"),
                paths =
                    mapOf(
                        "/users/{id}" to
                            domain.PathItem(
                                get =
                                    domain.EndpointDefinition(
                                        path = "/users/{id}",
                                        method = domain.HttpMethod.GET,
                                        operationId = "")))))
    assertTrue(files["McpToolAdapter.kt"]!!.contains("get_users_id"))

    val filesEmptyPaths =
        McpClientGenerator.generateMcpFiles(
            "org.example", domain.OpenApiDefinition(info = domain.Info("T", "1")))
    assertTrue(filesEmptyPaths["McpServer.kt"]!!.contains("T"))

    val filesNullDef = McpClientGenerator.generateMcpFiles("org.example", null)
    assertTrue(filesNullDef["McpServer.kt"]!!.contains("GeneratedMcpServer"))
  }
}
