package org.cdd.wasm

import kotlin.wasm.WasmExport
import runCli

/**
 * Processes WASM arguments.
 */
fun processWasmArgs(args: Array<String>): String {
    if (args.isEmpty()) {
        return "No arguments provided to cdd-kotlin WASM."
    }
    return "cdd-kotlin WASM received: " + args.joinToString(", ")
}

/**
 * Exported WASM function for from_openapi.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun from_openapi(): Int {
    return runCli(arrayOf("from_openapi"))
}

/**
 * Exported WASM function for to_openapi.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun to_openapi(): Int {
    return runCli(arrayOf("to_openapi"))
}

/**
 * Exported WASM function for to_docs_json.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun to_docs_json(): Int {
    return runCli(arrayOf("to_docs_json"))
}
