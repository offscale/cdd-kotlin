package psi

import domain.EndpointDefinition
import domain.HttpMethod
import domain.ParameterLocation
import domain.EndpointParameter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.AfterAll

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetworkMergerTest {

    private val merger = NetworkMerger()
    private val generator = NetworkGenerator()

    @AfterAll
    fun tearDown() {
        PsiInfrastructure.dispose()
    }

    @Test
    fun `mergeApi adds missing function to Interface and Implementation`() {
        // 1. Initial State: API with 1 endpoint
        val ep1 = EndpointDefinition(
            path = "/users",
            method = HttpMethod.GET,
            operationId = "getUsers",
            responseType = "List<String>",
            parameters = emptyList()
        )
        val initialFile = generator.generateApi("com.test", "UserApi", listOf(ep1))
        val initialSource = initialFile.text

        // 2. New State: Add 2nd endpoint
        val ep2 = EndpointDefinition(
            path = "/users/{id}",
            method = HttpMethod.GET,
            operationId = "getUserById",
            responseType = "String",
            parameters = listOf(EndpointParameter("id", "String", ParameterLocation.PATH))
        )

        val mergedSource = merger.mergeApi(initialSource, listOf(ep2))

        // 3. Assertions
        // Interface Check
        assertTrue(mergedSource.contains("suspend fun getUsers(): List<String>"))
        assertTrue(mergedSource.contains("suspend fun getUserById(id: String): String"))

        // Implementation Check
        assertTrue(mergedSource.contains("override suspend fun getUsers(): List<String>"))
        assertTrue(mergedSource.contains("override suspend fun getUserById(id: String): String"))

        // Structure Check
        assertTrue(mergedSource.contains("interface IUserApi"))
        assertTrue(mergedSource.contains("class UserApi(private val client: HttpClient) : IUserApi"))
    }

    @Test
    fun `mergeApi does not duplicate existing methods`() {
        val ep1 = EndpointDefinition(
            path = "/items",
            method = HttpMethod.GET,
            operationId = "getItems",
            responseType = "List<String>",
            parameters = emptyList()
        )
        val initialSource = generator.generateApi("com.test", "ItemApi", listOf(ep1)).text

        // Try to merge the SAME endpoint again
        val mergedSource = merger.mergeApi(initialSource, listOf(ep1))

        // Should basically be identical or at least not have duplicates
        // Simple distinct check:
        val occurrences = mergedSource.windowed("suspend fun getItems".length)
            .count { it == "suspend fun getItems" }

        // 1 in Interface, 1 in Implementation = 2 Total
        // (If duplicated, it would be 4)
        assertEquals(2, occurrences, "Should not duplicate existing methods")
    }

    @Test
    fun `mergeApi handles missing Interface method but present Impl (Edge Case)`() {
        // Simulate a broken file where Impl has it but Interface doesn't (manual edit)
        val source = """
            package com.test
            import io.ktor.client.*
            interface IBrokenApi {
               // Empty
            }
            class BrokenApi(val client: HttpClient) : IBrokenApi {
               suspend fun existing() {}
            }
            class ApiException(message: String) : Exception(message)
        """.trimIndent()

        val ep = EndpointDefinition(
            path = "/exist",
            method = HttpMethod.GET,
            operationId = "existing",
            responseType = "Unit",
            parameters = emptyList()
        )

        val merged = merger.mergeApi(source, listOf(ep))

        // Should add to Interface because it's missing there
        assertTrue(merged.contains("interface IBrokenApi {"), "Interface decl missing")
        assertTrue(merged.contains("suspend fun existing(): Unit"), "Should add signature to interface")

        // Should NOT add to Impl because it is already there
        // Count occurrences of 'fun existing' -> 1 in Interface (new), 1 in Impl (old) = 2
        val count = merged.split("fun existing").size - 1
        assertEquals(2, count)
    }

    @Test
    fun `mergeApi handles complex parameters`() {
        val initialSource = generator.generateApi("com.complex", "ComplexApi", emptyList()).text

        val ep = EndpointDefinition(
            path = "/data",
            method = HttpMethod.POST,
            operationId = "postData",
            responseType = "Unit",
            parameters = listOf(
                EndpointParameter("token", "String", ParameterLocation.HEADER),
                EndpointParameter("q", "String", ParameterLocation.QUERY)
            ),
            requestBodyType = "MyData"
        )

        val merged = merger.mergeApi(initialSource, listOf(ep))

        assertTrue(merged.contains("header(\"token\", token)"))
        assertTrue(merged.contains("parameter(\"q\", q)"))
        assertTrue(merged.contains("setBody(body)"))
        assertTrue(merged.contains("body: MyData"))
    }
}
