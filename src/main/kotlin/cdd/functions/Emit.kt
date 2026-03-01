package cdd.functions

import cdd.openapi.EndpointDefinition

/** Generates Top-Level Functions for API operations. */
class FunctionsEmit {
    /** Emits top-level functions for the given endpoints. */
    fun emit(packageName: String, endpoints: List<EndpointDefinition>): String {
        val builder = StringBuilder()
        builder.appendLine("package $packageName\n")
        builder.appendLine("/** Top-level functions */\n")
        endpoints.forEach { ep ->
            builder.appendLine("/** ${ep.summary ?: "Handler for ${ep.operationId}"} */")
            builder.appendLine("suspend fun handle${ep.operationId.replaceFirstChar { it.uppercase() }}() {")
            builder.appendLine("    // TODO: Implement business logic")
            builder.appendLine("}")
        }
        return builder.toString()
    }
}
