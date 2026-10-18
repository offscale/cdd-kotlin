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

/**
 * Exported WASM function for `from_openapi`.
 *
 * @return CLI exit code.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun from_openapi(): Int {
  val argsStr = getEnvVar("CDD_ARGS") ?: "from_openapi to_sdk -o out"
  val args = argsStr.split(" ").filter { it.isNotEmpty() }.toTypedArray()
  return runCli(args)
}

/**
 * Exported WASM function for `to_openapi`.
 *
 * @return CLI exit code.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun to_openapi(): Int {
  return runCli(arrayOf("to_openapi"))
}

/**
 * Exported WASM function for `from_arazzo`.
 *
 * @return CLI exit code.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun from_arazzo(): Int {
  val argsStr = getEnvVar("CDD_ARGS") ?: "from_arazzo to_sdk -o out"
  val args = argsStr.split(" ").filter { it.isNotEmpty() }.toTypedArray()
  return runCli(args)
}

/**
 * Exported WASM function for `to_arazzo`.
 *
 * @return CLI exit code.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun to_arazzo(): Int {
  return runCli(arrayOf("to_arazzo"))
}

/**
 * Exported WASM function for `to_docs_json`.
 *
 * @return CLI exit code.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun to_docs_json(): Int {
  return runCli(arrayOf("to_docs_json"))
}

/**
 * Exported WASM function for `to_sdk`.
 *
 * @return CLI exit code.
 */
@OptIn(kotlin.wasm.ExperimentalWasmInterop::class)
@WasmExport
fun to_sdk(): Int {
  return runCli(arrayOf("to_sdk"))
}
