package org.cdd.wasm

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun testProcessWasmArgsEmpty() {
        assertEquals("No arguments provided to cdd-kotlin WASM.", processWasmArgs(emptyArray()))
    }

    @Test
    fun testProcessWasmArgs() {
        assertEquals("cdd-kotlin WASM received: a, b", processWasmArgs(arrayOf("a", "b")))
    }
}
