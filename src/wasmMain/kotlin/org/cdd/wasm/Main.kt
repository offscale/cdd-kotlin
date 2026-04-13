package org.cdd.wasm

/**
 * Processes WASM arguments.
 */
fun processWasmArgs(args: Array<String>): String {
    if (args.isEmpty()) {
        return "No arguments provided to cdd-kotlin WASM."
    }
    return "cdd-kotlin WASM received: " + args.joinToString(", ")
}

