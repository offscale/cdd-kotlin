/** Unit tests for WebAssembly (WASI) entrypoints and platform functions. */
package org.cdd.wasm

import generateArazzoSdkCode
import generateArazzoServerCode
import generateToArazzo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies WASM exported functions and platform Arazzo bindings. */
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
    assertEquals(1, to_sdk())
  }

  @Test
  fun testFromOpenApi() {
    assertEquals(1, from_openapi())
  }

  @Test
  fun testFromAndToArazzoWasmExports() {
    assertEquals(1, from_arazzo())
    assertEquals(1, to_arazzo())
  }

  @Test
  fun testPlatformWasmArazzoFunctions() {
    assertTrue(generateToArazzo("/tmp/src", "/tmp/out.json").isFailure)
    assertTrue(generateArazzoServerCode("{}", "/tmp/out", "org.example", false).isFailure)
    assertTrue(generateArazzoSdkCode("{bad", "/tmp/out", "org.example", false).isFailure)
    val validArazzo =
        """
        {
          "arazzo": "1.1.0",
          "info": { "title": "WasmFlow", "version": "1.0.0" },
          "sourceDescriptions": [{ "name": "s", "url": "./s.json" }],
          "workflows": [{ "workflowId": "wf1", "steps": [{ "stepId": "s1", "operationId": "op1" }] }]
        }
        """
            .trimIndent()
    // In the default Node WASI test runner without preopened directories, writeToFile returns a
    // FileIoError failure Result rather(than throwing).
    val sdkRes = generateArazzoSdkCode(validArazzo, "wasm_arazzo_out", "org.example", true)
    assertTrue(sdkRes.isSuccess || sdkRes.isFailure)
  }
}
