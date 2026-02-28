package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class MergeClassCoverageTest {
    @Test
    fun `mergeClass returns source if not found or no body`() {
        val merger = NetworkMerger()
        
        val source1 = "class Missing { }"
        assertEquals(source1, merger.mergeClass(source1, "WrongClass", emptyList(), false))
        
        val source2 = "package a\nclass Found"
        assertEquals(source2, merger.mergeClass(source2, "Found", emptyList(), false))
    }
    
    @Test
    fun `mergeClass updates and deletes`() {
        val merger = NetworkMerger()
        val source = """
            package a
            
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*
            
            interface MyApi {
                /**
                 * @operationId get1
                 * @method GET
                 * @path /a
                 */
                suspend fun get1(): Result<Unit>
                
                /**
                 * @operationId get2
                 * @method GET
                 * @path /b
                 */
                suspend fun get2(): Result<Unit>
            }
            
            class MyApiImpl(val client: HttpClient) : MyApi {
                /**
                 * @operationId get1
                 * @method GET
                 * @path /a
                 */
                override suspend fun get1(): Result<Unit> {
                    return try {
                        val response = client.request("${"$"}{"baseUrl"}/a") {
                            method = HttpMethod.Get
                        }
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Result.failure(Exception())
                    }
                }
                
                /**
                 * @operationId get2
                 * @method GET
                 * @path /b
                 */
                override suspend fun get2(): Result<Unit> {
                    return try {
                        val response = client.request("${"$"}{"baseUrl"}/b") {
                            method = HttpMethod.Get
                        }
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Result.failure(Exception())
                    }
                }
            }
        """.trimIndent()
        
        val spec = listOf(
            EndpointDefinition(operationId = "get1", method = HttpMethod.GET, path = "/a")
        )
        
        val mergedInterface = merger.mergeClass(source, "MyApi", spec, true)
        println(mergedInterface)
        assertTrue(mergedInterface.contains("fun get1"))
        
        val mergedImpl = merger.mergeClass(mergedInterface, "MyApiImpl", spec, false)
        println(mergedImpl)
        assertTrue(mergedImpl.contains("fun get1"))
        val sameSpec = listOf(EndpointDefinition(operationId = "get1", method = HttpMethod.GET, path = "/a"))
        val sameMerge = merger.mergeClass(mergedImpl, "MyApiImpl", sameSpec, false)
        
        val fullMerge = merger.mergeApiSource(source, spec, "MyApi", "MyApiImpl")
        
        assertTrue(fullMerge.contains("fun get1"))
        
        // Add case
        val specAdd = listOf(
            EndpointDefinition(operationId = "get1", method = HttpMethod.GET, path = "/a"),
            EndpointDefinition(operationId = "get3", method = HttpMethod.GET, path = "/c")
        )
        val added = merger.mergeClass(mergedImpl, "MyApiImpl", specAdd, false)
        assertTrue(added.contains("fun get3"))
    }
}
