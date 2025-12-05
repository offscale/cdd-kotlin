package psi

import domain.HttpMethod
import domain.ParameterLocation
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `parse extracts basic GET endpoint`() {
        val code = """
            class Api(val client: HttpClient) {
                suspend fun checkHealth(): String {
                    return client.request("/health") {
                        method = HttpMethod.Get
                    }.body<String>()
                }
            }
        """.trimIndent()

        val results = parser.parse(code)
        assertEquals(1, results.size)

        val ep = results.first()
        assertEquals("/health", ep.path)
        assertEquals(HttpMethod.GET, ep.method)
        assertEquals("checkHealth", ep.operationId)
        assertEquals("String", ep.responseType)
    }

    @Test
    fun `parse extracts Path Parameters from URL template`() {
        val code = """
            suspend fun getUser(id: Long): User {
                return client.request("/users/${'$'}id") {
                    method = HttpMethod.Get
                }.body<User>()
            }
        """.trimIndent()

        val ep = parser.parse(code).first()

        assertEquals("/users/{id}", ep.path)
        assertEquals("User", ep.responseType)

        val param = ep.parameters.find { it.location == ParameterLocation.PATH }
        assertEquals("id", param?.name)
        assertEquals("Long", param?.type) // Extracted from function arg
    }

    @Test
    fun `parse extracts Query and Header params`() {
        val code = """
            suspend fun search(q: String, token: String): Unit {
                client.request("/search") {
                    method = HttpMethod.Get
                    parameter("query", q)
                    header("Authorization", token)
                }
            }
        """.trimIndent()

        val ep = parser.parse(code).first()

        // Query
        val query = ep.parameters.find { it.location == ParameterLocation.QUERY }
        assertEquals("query", query?.name)
        assertEquals("String", query?.type)

        // Header
        val header = ep.parameters.find { it.location == ParameterLocation.HEADER }
        assertEquals("Authorization", header?.name)
        assertEquals("String", header?.type)

        assertEquals(null, ep.responseType) // Unit -> null
    }

    @Test
    fun `parse extracts POST Body`() {
        val code = """
            suspend fun create(item: ItemDto): ResultDto {
                return client.request("/items") {
                    method = HttpMethod.Post
                    setBody(item)
                }.body<ResultDto>()
            }
        """.trimIndent()

        val ep = parser.parse(code).first()

        assertEquals(HttpMethod.POST, ep.method)
        assertEquals("ItemDto", ep.requestBodyType)
        assertEquals("ResultDto", ep.responseType)
    }

    @Test
    fun `parse falls back to function return type if body generic is missing`() {
        val code = """
            suspend fun getList(): List<String> {
                return client.request("/list")
            }
        """.trimIndent()

        val ep = parser.parse(code).first()
        assertEquals("List<String>", ep.responseType)
    }
}
