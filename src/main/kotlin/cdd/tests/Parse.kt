package cdd.tests

import cdd.openapi.EndpointDefinition
import cdd.openapi.HttpMethod
import cdd.openapi.EndpointResponse

/** Parses a Unit Test implementation back into EndpointDefinitions. */
class TestsParse {
    /** Parses Kotlin source containing unit tests and infers endpoint operations tested. */
    fun parse(sourceCode: String): List<EndpointDefinition> {
        val results = mutableListOf<EndpointDefinition>()
        val regex = Regex("fun `test ([^`]+)`")
        val matches = regex.findAll(sourceCode)
        for (match in matches) {
            val opId = match.groupValues[1]
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
