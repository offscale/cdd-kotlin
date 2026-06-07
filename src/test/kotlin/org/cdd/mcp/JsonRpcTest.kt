package org.cdd.mcp

import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonRpcTest {
  @Test
  fun testErrorCodes() {
    assertEquals(-32700, JsonRpcErrorCodes.PARSE_ERROR)
    assertEquals(-32600, JsonRpcErrorCodes.INVALID_REQUEST)
    assertEquals(-32601, JsonRpcErrorCodes.METHOD_NOT_FOUND)
    assertEquals(-32602, JsonRpcErrorCodes.INVALID_PARAMS)
    assertEquals(-32603, JsonRpcErrorCodes.INTERNAL_ERROR)
  }

  @Test
  fun testException() {
    val ex = JsonRpcException(-32700, "Parse error", JsonNull)
    assertEquals(-32700, ex.code)
    assertEquals("Parse error", ex.message)
    assertEquals(JsonNull, ex.data)
  }

  @Test
  fun testParser() {
    val element = JsonRpcParser.parseMessage("""{"jsonrpc":"2.0","id":1,"method":"test"}""")
    assertTrue(element is kotlinx.serialization.json.JsonObject)
    assertEquals(
        "2.0",
        (element as kotlinx.serialization.json.JsonObject)["jsonrpc"]?.let {
          if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null
        })
    assertEquals(
        "1",
        (element as kotlinx.serialization.json.JsonObject)["id"]?.let {
          if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null
        })

    assertThrows(JsonRpcException::class.java) { JsonRpcParser.parseMessage("{invalid json}") }

    val serialized = JsonRpcParser.serializeMessage(element)
    assertTrue(serialized.contains("test"))
  }
}
