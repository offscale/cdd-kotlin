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

    peer.onRequest("errorMethod") { params -> throw RuntimeException("custom error") }

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

    // Test coverage for empty/stubbed methods
    peer.handlePagination("cursor")
    assertTrue(peer.checkRootBoundary("uri"))
    org.junit.jupiter.api.Assertions.assertEquals("uri", peer.resolveUri("uri"))
    assertTrue(peer.checkToolApproval("toolName"))
  }
}
