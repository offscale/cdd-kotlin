package cdd.functions

import cdd.openapi.EndpointDefinition
import cdd.openapi.HttpMethod
import cdd.openapi.EndpointResponse

/** Parses Top-Level Functions back into EndpointDefinitions. */
class FunctionsParse {
    /** Parses Kotlin source containing top-level functions and infers endpoint definitions. */
    fun parse(sourceCode: String): List<EndpointDefinition> {
        val results = mutableListOf<EndpointDefinition>()
        val regex = Regex("suspend fun handle([A-Z][a-zA-Z0-9_]*)")
        val matches = regex.findAll(sourceCode)
        for (match in matches) {
            val opIdName = match.groupValues[1]
            val opId = opIdName.replaceFirstChar { it.lowercase() }
            results.add(EndpointDefinition(
                path = "/$opId",
                method = HttpMethod.GET,
                operationId = opId,
                responses = mapOf("200" to EndpointResponse(statusCode = "200"))
            ))
        }
        return results
    }
}
