package cdd.mocks

import cdd.openapi.EndpointDefinition
import cdd.openapi.HttpMethod
import cdd.openapi.EndpointResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

/** Auto generated docs */
class MocksEmitTest {
    @Test
    /** Auto generated docs */
    fun `test emit`() {
        val instance = MocksEmit()
        val eps = listOf(
            EndpointDefinition(
                path = "/a/{id}",
                method = HttpMethod.GET,
                operationId = "getA",
                responses = emptyMap()
            )
        )
        val res = instance.emit("com.example", "MyMock", eps)
        assertTrue(res.contains("MockEngine"))
    }
}
