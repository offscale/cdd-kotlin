package org.cdd.mcp

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpPeerLifecycleTest {
  @Test
  fun testLifecycle() {
    val transport = DummyTransport()
    val peer = McpPeer(transport)

    assertFalse(peer.isInitialized)

    transport.simulateReceive(
        Json.parseToJsonElement(
            """{"jsonrpc":"2.0", "method": "notifications/initialized", "params": {}}"""))
    assertTrue(peer.isInitialized)

    // The initialize method expects InitializeRequestParams, so we need to pass valid params
    val initializeParamsJson =
        """
      {
        "protocolVersion": "2024-11-05",
        "capabilities": {
          "experimental": {}
        },
        "clientInfo": {
          "name": "test-client",
          "version": "1.0.0"
        }
      }
    """
            .trimIndent()
    transport.simulateReceive(
        Json.parseToJsonElement(
            """{"jsonrpc":"2.0", "id": "3", "method": "initialize", "params": $initializeParamsJson}"""))
    assertFalse(peer.isInitialized)

    transport.simulateReceive(
        Json.parseToJsonElement("""{"jsonrpc":"2.0", "id": "4", "method": "ping", "params": {}}"""))
  }
}
