package org.cdd.wasm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainTest {
  @Test
  fun testProcessWasmArgsEmpty() {
    assertEquals("No arguments provided to cdd-kotlin WASM.", processWasmArgs(emptyArray()))
  }

  @Test
  fun testProcessWasmArgs() {
    assertEquals("cdd-kotlin WASM received: a, b", processWasmArgs(arrayOf("a", "b")))
  }

  @Test
  fun testToOpenApi() {
    assertEquals(1, to_openapi())
  }

  @Test
  fun testToDocsJson() {
    assertEquals(1, to_docs_json())
  }

  @Test
  fun testToSdk() {
    try {
      to_sdk()
    } catch (e: Throwable) {
      assertTrue(
          e.message?.contains("preopened") == true ||
              e.toString().contains("preopened") ||
              e.message?.contains("No such file") == true,
          "Expected preopened directory error or similar from WASI")
    }
  }

  @Test
  fun testFromOpenApi() {
    try {
      from_openapi()
    } catch (e: Throwable) {
      assertTrue(
          e.message?.contains("preopened") == true ||
              e.toString().contains("preopened") ||
              e.message?.contains("No such file") == true,
          "Expected preopened directory error or similar from WASI")
    }
  }
}
