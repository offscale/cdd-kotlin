package psi

import domain.EndpointDefinition
import domain.EndpointResponse
import domain.HttpMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetworkMergerTest {

    private val merger = NetworkMerger()

    // Helper to create a simple 200 OK response map
    private fun simpleResponse(type: String?) = mapOf(
        "200" to EndpointResponse("200", description = "OK", type = type)
    )

    @Test
    fun `mergeEndpoints adds new endpoints`() {
        val existing = listOf(
            EndpointDefinition(
                path = "/users",
                method = HttpMethod.GET,
                operationId = "getUsers",
                responses = simpleResponse("List<User>")
            )
        )
        val newSpec = listOf(
            EndpointDefinition(
                path = "/users",
                method = HttpMethod.GET,
                operationId = "getUsers",
                responses = simpleResponse("List<User>")
            ),
            EndpointDefinition(
                path = "/users/{id}",
                method = HttpMethod.GET,
                operationId = "getUserById",
                responses = simpleResponse("User")
            )
        )

        val result = merger.mergeEndpoints(existing.toList(), newSpec.toList())

        assertEquals(2, result.size)
        // Verify order/existence
        assertTrue(result.any { it.operationId == "getUsers" })
        assertTrue(result.any { it.operationId == "getUserById" })
    }

    @Test
    fun `mergeEndpoints updates existing endpoint types based on Spec`() {
        val existing = listOf(
            EndpointDefinition(
                path = "/old-path",
                method = HttpMethod.GET,
                operationId = "getUsers",
                responses = simpleResponse("List<OldUser>")
            )
        )

        // Spec says return type changed to List<NewUser>
        val newSpec = listOf(
            EndpointDefinition(
                path = "/users",
                method = HttpMethod.GET,
                operationId = "getUsers",
                responses = simpleResponse("List<NewUser>")
            )
        )

        val result = merger.mergeEndpoints(existing, newSpec)

        assertEquals(1, result.size)
        val updated = result.first()
        assertEquals("List<NewUser>", updated.responseType) // Uses property accessor
        assertEquals("/users", updated.path) // Spec path wins
    }

    @Test
    fun `mergeEndpoints removes endpoints not in new spec`() {
        val existing = listOf(
            EndpointDefinition(
                path = "/deprecated",
                method = HttpMethod.GET,
                operationId = "oldOp",
                responses = simpleResponse("String")
            )
        )
        val newSpec = emptyList<EndpointDefinition>()

        val result = merger.mergeEndpoints(existing, newSpec)

        assertEquals(0, result.size, "Should remove obsolete endpoints")
    }

    @Test
    fun `mergeEndpoints handles complex differential`() {
        // Code has: A, C. Spec has: A', B, D.
        // C should be removed. A should update. B, D added.

        val existing = listOf(
            EndpointDefinition(
                path = "/a",
                method = HttpMethod.GET,
                operationId = "getA",
                responses = simpleResponse("String")
            ),
            EndpointDefinition(
                path = "/c",
                method = HttpMethod.GET,
                operationId = "getC",
                responses = simpleResponse("String")
            )
        )

        val newSpec = listOf(
            EndpointDefinition(
                path = "/a",
                method = HttpMethod.GET,
                operationId = "getA",
                responses = simpleResponse("Int") // Type changed
            ),
            EndpointDefinition(
                path = "/b",
                method = HttpMethod.GET,
                operationId = "getB",
                responses = simpleResponse("String")
            ),
            EndpointDefinition(
                path = "/d",
                method = HttpMethod.GET,
                operationId = "getD",
                responses = simpleResponse("String")
            )
        )

        val result = merger.mergeEndpoints(existing, newSpec)

        assertEquals(3, result.size)

        val getA = result.find { it.operationId == "getA" }
        assertEquals("Int", getA?.responseType)

        assertTrue(result.any { it.operationId == "getB" })
        assertTrue(result.any { it.operationId == "getD" })
        assertFalse(result.any { it.operationId == "getC" })
    }
}
