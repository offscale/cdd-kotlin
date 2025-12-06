package psi

import domain.HttpMethod
import domain.ParameterLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.AfterAll

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetworkParserTest {

    private val parser = NetworkParser()

    @AfterAll
    fun tearDown() {
        PsiInfrastructure.dispose()
    }

    @Test
    fun `parse extracts basic GET endpoint and strips absolute URL`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class UserApi(private val client: HttpClient) {
                suspend fun getUsers(): Result<String> {
                    return try {
                        val response = client.request("https://api.com/users") {
                            method = HttpMethod.Get
                        }
                        Result.success(response.body())
                    } catch(e: Exception) { Result.failure(e) }
                }
            }
        """.trimIndent()

        val results = parser.parse(code)
        assertEquals(1, results.size)

        val endpoint = results[0]
        assertEquals("/users", endpoint.path)
        assertEquals(HttpMethod.GET, endpoint.method)
        assertEquals("getUsers", endpoint.operationId)
        assertEquals("String", endpoint.responseType)
    }

    @Test
    fun `parse extracts Path Parameters from URL template`() {
        val code = """
            class UserApi {
                suspend fun getUserById(id: String): Result<User> {
                     client.request("https://api.com/users/{id}") { method = HttpMethod.Get }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals("/users/{id}", endpoint.path)

        val param = endpoint.parameters.firstOrNull()
        assertNotNull(param)
        assertEquals("id", param?.name)
        assertEquals(ParameterLocation.PATH, param?.location)
        assertEquals("User", endpoint.responseType)
    }

    @Test
    fun `parse extracts POST Body`() {
        val code = """
            class PostApi {
                suspend fun createPost(data: ResultDto): Result<ResultDto> {
                    client.request("https://api.com/posts") {
                        method = HttpMethod.Post
                        setBody(data)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals(HttpMethod.POST, endpoint.method)
        assertEquals("ResultDto", endpoint.requestBodyType)
        assertEquals("ResultDto", endpoint.responseType)
    }

    @Test
    fun `parse falls back to function return type if body generic is missing`() {
        val code = """
            class ListApi {
                suspend fun getItems(): Result<List<String>> {
                    client.request("/items")
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        assertEquals("List<String>", endpoint.responseType)
    }

    @Test
    fun `parse extracts Query, Header and Cookie params`() {
        val code = """
            class SearchApi {
                suspend fun search(q: String, token: String, sid: String): Result<Unit> {
                    client.request("/search") {
                        method = HttpMethod.Get
                        parameter("q", q)
                        header("Authorization", token)
                        cookie("session_id", sid)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        val query = endpoint.parameters.find { it.name == "q" }
        assertEquals(ParameterLocation.QUERY, query?.location)

        val header = endpoint.parameters.find { it.name == "Authorization" }
        assertNotNull(header)
        assertEquals(ParameterLocation.HEADER, header?.location)

        val cookie = endpoint.parameters.find { it.name == "session_id" }
        assertNotNull(cookie)
        assertEquals(ParameterLocation.COOKIE, cookie?.location)
    }
}
