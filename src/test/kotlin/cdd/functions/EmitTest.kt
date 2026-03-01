package cdd.functions

import cdd.openapi.EndpointDefinition
import cdd.openapi.HttpMethod
import cdd.openapi.EndpointResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

/** Auto generated docs */
class FunctionsEmitTest {
    @Test
    /** Auto generated docs */
    fun `test emit`() {
        val instance = FunctionsEmit()
        val eps = listOf(
            EndpointDefinition(
                path = "/a",
                method = HttpMethod.GET,
                operationId = "getA",
                responses = emptyMap(),
                summary = "Some summary"
            ),
            EndpointDefinition(
                path = "/b",
                method = HttpMethod.GET,
                operationId = "getB",
                responses = emptyMap(),
                summary = null
            )
        )
        val res = instance.emit("com.example", eps)
        assertTrue(res.contains("handleGetA"))
        assertTrue(res.contains("handleGetB"))
    }
}
