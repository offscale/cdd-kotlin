/** Unit and integration tests in [StdioTransportImplTest]. */
package org.cdd.mcp

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StdioTransportImplTest {
  @Test
  fun testStdioTransport() {
    val transport = StdioTransportImpl()
    var received = false
    var errored = false
    var closed = false

    transport.onReceive { received = true }
    transport.onError { errored = true }
    transport.onClose { closed = true }

    val msg = Json.parseToJsonElement("""{"test": true}""")
    transport.simulateReceive(msg)
    assertTrue(received)

    transport.send(msg)

    transport.close()
    assertTrue(closed)
  }

  @Test
  fun testStartReading() {
    val transport = StdioTransportImpl()
    val receivedMessages = mutableListOf<kotlinx.serialization.json.JsonElement>()
    val errors = mutableListOf<Exception>()
    var closed = false

    transport.onReceive { receivedMessages.add(it) }
    transport.onError { errors.add(it) }
    transport.onClose { closed = true }

    val lines = ArrayDeque(listOf("", """{"jsonrpc":"2.0","method":"ping"}""", "{bad json", null))
    transport.startReading { lines.removeFirst() }

    assertEquals(1, receivedMessages.size)
    assertEquals(1, errors.size)
    assertTrue(closed)
  }

  @Test
  fun testSendError() {
    val transport = StdioTransportImpl()
    val errors = mutableListOf<Exception>()
    var closed = false
    transport.onError { errors.add(it) }
    transport.onClose { closed = true }

    transport.startReadingResult { kotlin.Result.failure(AssertionError("fatal read error")) }
    assertEquals(1, errors.size)
    assertEquals("fatal read error", errors.first().message)
    assertTrue(closed)
  }
}
