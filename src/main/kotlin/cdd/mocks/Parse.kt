package cdd.mocks

import cdd.openapi.EndpointDefinition
import cdd.openapi.HttpMethod
import cdd.openapi.EndpointResponse

/** Parses a MockEngine implementation back into EndpointDefinitions. */
class MocksParse {
    /** Parses Kotlin source containing a MockEngine and returns found endpoint definitions. */
    fun parse(sourceCode: String): List<EndpointDefinition> {
        val results = mutableListOf<EndpointDefinition>()
        val regex = Regex("path\\.matches\\(Regex\\(\"\\^(.*?)\\$\"\\)\\) && method == HttpMethod\\.([A-Z]+)")
        val matches = regex.findAll(sourceCode)
        for (match in matches) {
            val pathRegex = match.groupValues[1]
            val path = pathRegex.replace(".+", "{id}")
            val methodStr = match.groupValues[2]
            val method = HttpMethod.entries.find { it.name == methodStr } ?: HttpMethod.GET
            results.add(EndpointDefinition(
                path = path,
                method = method,
                operationId = "mock${methodStr}${path.replace("\\W".toRegex(), "")}",
                responses = mapOf("200" to EndpointResponse(statusCode = "200"))
            ))
        }
        return results
    }
}
