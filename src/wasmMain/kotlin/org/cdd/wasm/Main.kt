/** WebAssembly (WASI) entrypoints and exported C-ABI functions for cdd-kotlin. */
package org.cdd.wasm

import getEnvVar
import kotlin.wasm.WasmExport
import runCli

/**
 * Processes WASM arguments into a diagnostic string.
 *
 * @param args Command-line arguments array.
 * @return Formatted argument status string.
 */
fun processWasmArgs(args: Array<String>): String {
  if (args.isEmpty()) {
    return "No arguments provided to cdd-kotlin WASM."
  }
  return "cdd-kotlin WASM received: " + args.joinToString(", ")
}

private fun resolveWasmArgs(defaultCommand: String): Array<String> {
  val argsStr = getEnvVar("CDD_ARGS") ?: defaultCommand
  return argsStr.split(" ").filter { it.isNotEmpty() }.toTypedArray()
}

/**
 * Exported WASM function for `from_openapi`.
 *
 * @return CLI exit code.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun from_openapi(): Int {
  return runCli(resolveWasmArgs("from_openapi to_sdk -o out"))
}

/**
 * Exported WASM function for `to_openapi`.
 *
 * @return CLI exit code.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun to_openapi(): Int {
  return runCli(resolveWasmArgs("to_openapi"))
}

/**
 * Exported WASM function for `from_arazzo`.
 *
 * @return CLI exit code.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun from_arazzo(): Int {
  return runCli(resolveWasmArgs("from_arazzo to_sdk -o out"))
}

/**
 * Exported WASM function for `to_arazzo`.
 *
 * @return CLI exit code.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun to_arazzo(): Int {
  return runCli(resolveWasmArgs("to_arazzo"))
}

/**
 * Exported WASM function for `to_docs_json`.
 *
 * @return CLI exit code.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun to_docs_json(): Int {
  return runCli(resolveWasmArgs("to_docs_json"))
}

/**
 * Exported WASM function for `to_sdk`.
 *
 * @return CLI exit code.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun to_sdk(): Int {
  return runCli(resolveWasmArgs("to_sdk"))
}
