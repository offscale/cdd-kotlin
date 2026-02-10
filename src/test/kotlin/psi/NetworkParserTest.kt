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
        assertNotNull(endpoint.responses["200"]?.content?.get("application/json"))
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
        assertNotNull(endpoint.requestBody)
        assertEquals(true, endpoint.requestBody?.required)
        assertNotNull(endpoint.requestBody?.content?.get("application/json")?.schema)
        assertEquals("ResultDto", endpoint.responseType)
    }

    @Test
    fun `parse captures request content type`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class XmlApi(private val client: HttpClient) {
                suspend fun sendXml(payload: String): Result<Unit> {
                    client.request("/xml") {
                        method = HttpMethod.Post
                        contentType(ContentType.parse("application/xml"))
                        setBody(payload)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertNotNull(endpoint.requestBody?.content?.get("application/xml"))
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

    @Test
    fun `parse extracts querystring parameter`() {
        val code = """
            class QueryStringApi {
                suspend fun searchRaw(rawQuery: String): Result<Unit> {
                    client.request("/search") {
                        method = HttpMethod.Get
                        url.encodedQuery = rawQuery
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val qs = endpoint.parameters.find { it.location == ParameterLocation.QUERYSTRING }

        assertNotNull(qs)
        assertEquals("rawQuery", qs?.name)
    }

    @Test
    fun `parse extracts KDoc metadata and responses`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class MetaApi(private val client: HttpClient) {
                /**
                 * Fetch items.
                 * @description Returns items with enriched metadata.
                 * @see https://docs.example.com Items docs
                 * @tag alpha, beta
                 * @response 201 Item Created
                 * @response 204 Unit NoContent
                 */
                suspend fun getItems(id: String): Result<Item> {
                    return client.request("items/${'$'}id") {
                        method = HttpMethod.Post
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals("/items/{id}", endpoint.path)
        assertEquals("Fetch items.", endpoint.summary)
        assertEquals("Returns items with enriched metadata.", endpoint.description)
        assertEquals("https://docs.example.com", endpoint.externalDocs?.url)
        assertEquals("Items docs", endpoint.externalDocs?.description)
        assertEquals(listOf("alpha", "beta"), endpoint.tags)
        assertEquals("Item", endpoint.responses["201"]?.type)
        assertEquals("Created", endpoint.responses["201"]?.description)
        assertNotNull(endpoint.responses["201"]?.content?.get("application/json"))
        assertEquals(null, endpoint.responses["204"]?.type)
        assertEquals("NoContent", endpoint.responses["204"]?.description)
    }

    @Test
    fun `parse extracts parameter descriptions from KDoc`() {
        val code = """
            class ParamDocApi {
                /**
                 * @param q Search term
                 * @param limit Max items
                 */
                suspend fun search(q: String, limit: Int): Result<Unit> {
                    client.request("/search") {
                        parameter("q", q)
                        parameter("limit", limit)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        val q = endpoint.parameters.find { it.name == "q" }
        val limit = endpoint.parameters.find { it.name == "limit" }

        assertEquals("Search term", q?.description)
        assertEquals("Max items", limit?.description)
    }

    @Test
    fun `parse extracts parameter examples from KDoc`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class ExampleApi(private val client: HttpClient) {
                /**
                 * @param q query text
                 * @paramExample q cats
                 * @paramExample q dogs: dogs
                 */
                suspend fun search(q: String): Result<Unit> {
                    client.request("/search") {
                        method = HttpMethod.Get
                        parameter("q", q)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val param = endpoint.parameters.first { it.name == "q" }

        assertEquals("cats", param.example?.serializedValue)
        assertEquals("dogs", param.examples["dogs"]?.serializedValue)
    }

    @Test
    fun `parse extracts custom HTTP methods`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class CustomApi(private val client: HttpClient) {
                suspend fun copyItem(): Result<Unit> {
                    client.request("/copy") {
                        method = HttpMethod("COPY")
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        assertEquals(HttpMethod.CUSTOM, endpoint.method)
        assertEquals("COPY", endpoint.customMethod)
    }

    @Test
    fun `parse detects optional request bodies`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class OptionalBodyApi(private val client: HttpClient) {
                suspend fun send(payload: Payload? = null): Result<Unit> {
                    client.request("/optional") {
                        method = HttpMethod.Post
                        if (payload != null) {
                            setBody(payload)
                        }
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        assertEquals("Payload?", endpoint.requestBodyType)
        assertEquals(false, endpoint.requestBody?.required)
    }

    @Test
    fun `parse marks optional parameters from nullability and defaults but keeps path required`() {
        val code = """
            class OptionalApi {
                suspend fun getItem(id: String?, q: String?, limit: Int = 10): Result<Unit> {
                    client.request("/items/{id}") {
                        parameter("q", q)
                        parameter("limit", limit)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        val id = endpoint.parameters.find { it.location == ParameterLocation.PATH }
        val q = endpoint.parameters.find { it.name == "q" }
        val limit = endpoint.parameters.find { it.name == "limit" }

        assertEquals(true, id?.isRequired)
        assertEquals(false, q?.isRequired)
        assertEquals(false, limit?.isRequired)
    }

    @Test
    fun `parse ignores functions without request calls`() {
        val code = """
            class PlainApi {
                fun noop() { println("no request") }
            }
        """.trimIndent()

        val results = parser.parse(code)
        assertEquals(0, results.size)
    }

    @Test
    fun `parse handles baseUrl template and filters baseUrl param`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*

            class BaseApi(private val client: HttpClient) {
                suspend fun getOrg(baseUrl: String, org: String): Result<Unit> {
                    return client.request("${'$'}baseUrl/orgs/${'$'}{org}") {
                        parameter("q")
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals("/orgs/{org}", endpoint.path)
        assertEquals(1, endpoint.parameters.size)
        assertEquals("org", endpoint.parameters.first().name)
    }

    @Test
    fun `parse defaults to GET on invalid method`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class InvalidMethodApi(private val client: HttpClient) {
                suspend fun bad(): Result<Unit> {
                    return client.request("https://api.example.com") {
                        method = HttpMethod.FOO
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals("/", endpoint.path)
        assertEquals(HttpMethod.GET, endpoint.method)
    }

    @Test
    fun `parse handles empty path`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*

            class EmptyPathApi(private val client: HttpClient) {
                suspend fun root(): Result<Unit> {
                    return client.request("") { }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals("/", endpoint.path)
    }

    @Test
    fun `parse captures parameter schema and deprecation annotations`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class ParamApi(private val client: HttpClient) {
                suspend fun list(@Deprecated("old") limit: Int): Result<Unit> {
                    return client.request("/items") {
                        method = HttpMethod.Get
                        parameter("limit", limit)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val limit = endpoint.parameters.firstOrNull { it.name == "limit" }

        assertNotNull(limit)
        assertEquals(ParameterLocation.QUERY, limit?.location)
        assertEquals("integer", limit?.schema?.type)
        assertEquals("int32", limit?.schema?.format)
        assertEquals(true, limit?.deprecated)
    }

    @Test
    fun `parse ignores non-parameter setBody values`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class LiteralBodyApi(private val client: HttpClient) {
                suspend fun send(): Result<Unit> {
                    return client.request("/send") {
                        method = HttpMethod.Post
                        setBody("raw")
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals(null, endpoint.requestBodyType)
    }

    @Test
    fun `parse uses direct return type when not Result`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*

            class DirectReturnApi(private val client: HttpClient) {
                suspend fun count(): Int {
                    return client.request("/count")
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals("Int", endpoint.responseType)
    }
}
