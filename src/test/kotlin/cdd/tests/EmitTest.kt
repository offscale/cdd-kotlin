package cdd.tests

import cdd.openapi.EndpointDefinition
import cdd.openapi.HttpMethod
import cdd.openapi.EndpointResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

/** Auto generated docs */
class TestsEmitTest {
    @Test
    /** Auto generated docs */
    fun `test emit`() {
        val instance = TestsEmit()
        val eps = listOf(
            EndpointDefinition(
                path = "/a",
                method = HttpMethod.GET,
                operationId = "getA",
                responses = emptyMap()
            )
        )
        val res = instance.emit("com.example", "MyTest", eps)
        assertTrue(res.contains("class MyTestTest"))
    }
}
