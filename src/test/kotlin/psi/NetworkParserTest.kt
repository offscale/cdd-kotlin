package psi

import domain.Callback
import domain.HttpMethod
import domain.ParameterLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `parse preserves paramRef and responseRef tags`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class RefApi(private val client: HttpClient) {
                /**
                 * @paramRef limit {"${'$'}ref":"#/components/parameters/Limit","summary":"Ref param","description":"Ref param desc"}
                 * @response 200 String OK
                 * @responseRef 200 {"${'$'}ref":"#/components/responses/Ok","summary":"Ref summary","description":"Ref desc"}
                 */
                suspend fun listItems(limit: String): Result<String> {
                    client.request("/items") {
                        method = HttpMethod.Get
                        parameter("limit", limit)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        val param = endpoint.parameters.firstOrNull { it.name == "limit" }
        assertNotNull(param?.reference)
        assertEquals("#/components/parameters/Limit", param?.reference?.ref)
        assertEquals("Ref param", param?.reference?.summary)
        assertEquals("Ref param desc", param?.reference?.description)

        val response = endpoint.responses["200"]
        assertNotNull(response?.reference)
        assertEquals("#/components/responses/Ok", response?.reference?.ref)
        assertEquals("Ref summary", response?.reference?.summary)
        assertEquals("Ref desc", response?.reference?.description)
    }

    @Test
    fun `parse honors requestBody KDoc override`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class UploadApi(private val client: HttpClient) {
                /**
                 * @requestBody {"description":"payload","required":false,"content":{"application/xml":{"schema":{"type":"string"}}}}
                 */
                suspend fun upload(payload: String): Result<Unit> {
                    client.request("/upload") {
                        method = HttpMethod.Post
                        setBody(payload)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals("payload", endpoint.requestBody?.description)
        assertEquals(false, endpoint.requestBody?.required)
        assertNotNull(endpoint.requestBody?.content?.get("application/xml"))
    }

    @Test
    fun `parse extracts response summary`() {
        val code = """
            class SummaryApi {
                /**
                 * @response 200 String ok
                 * @responseSummary 200 Short summary
                 */
                suspend fun getSummary(): Result<String> {
                    client.request("/summary") { method = HttpMethod.Get }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals("Short summary", endpoint.responses["200"]?.summary)
    }

    @Test
    fun `parse extracts callbacks from KDoc`() {
        val code = """
            class CallbackApi {
                /**
                 * @callbacks {"onEvent":{"${'$'}ref":"#/components/callbacks/EventCb"}}
                 */
                suspend fun withCallbacks(): Result<Unit> {
                    client.request("/callbacks") { method = HttpMethod.Post }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val callback = endpoint.callbacks["onEvent"]

        assertNotNull(callback)
        assertTrue(callback is Callback.Reference)
        assertEquals("#/components/callbacks/EventCb", (callback as Callback.Reference).reference.ref)
    }

    @Test
    fun `parse extracts operation extensions`() {
        val code = """
            class ExtensionApi {
                /**
                 * @extensions {"x-rate-limit":10,"x-flag":true}
                 */
                suspend fun getExtensions(): Result<Unit> {
                    client.request("/extensions") { method = HttpMethod.Get }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals(10, endpoint.extensions["x-rate-limit"])
        assertEquals(true, endpoint.extensions["x-flag"])
    }

    @Test
    fun `parse captures custom schema keywords from response content`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class CustomApi(private val client: HttpClient) {
                /**
                 * @response 200 String ok
                 * @responseContent 200 {"application/json":{"schema":{"type":"object","customKeyword":123,"x-extra":"yes"}}}
                 */
                suspend fun getCustom(): Result<String> {
                    client.request("/custom") {
                        method = HttpMethod.Get
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val schema = endpoint.responses["200"]?.content?.get("application/json")?.schema

        assertEquals(123, schema?.customKeywords?.get("customKeyword"))
        assertEquals("yes", schema?.extensions?.get("x-extra"))
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
    fun `parse extracts parameter metadata and deprecated tag`() {
        val code = """
            import io.ktor.http.*

            class MetaApi {
                /**
                 * @deprecated
                 * @paramStyle ids form
                 * @paramExplode ids false
                 * @paramAllowReserved ids true
                 * @paramAllowEmptyValue ids true
                 */
                suspend fun list(ids: String): Result<Unit> {
                    client.request("/items") {
                        method = HttpMethod.Get
                        parameter("ids", ids)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val param = endpoint.parameters.first()

        assertEquals(true, endpoint.deprecated)
        assertEquals(ParameterLocation.QUERY, param.location)
        assertEquals(domain.ParameterStyle.FORM, param.style)
        assertEquals(false, param.explode)
        assertEquals(true, param.allowReserved)
        assertEquals(true, param.allowEmptyValue)
    }

    @Test
    fun `parse extracts parameter extensions`() {
        val code = """
            import io.ktor.http.*

            class ParamExtensionApi {
                /**
                 * @paramExtensions limit {"x-rate-limit": 50}
                 */
                suspend fun list(limit: Int): Result<Unit> {
                    client.request("/items") {
                        method = HttpMethod.Get
                        parameter("limit", limit)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val param = endpoint.parameters.firstOrNull { it.name == "limit" }

        assertNotNull(param)
        assertEquals(50, param?.extensions?.get("x-rate-limit"))
    }

    @Test
    fun `parse extracts operation servers from KDoc`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class ServerApi(private val client: HttpClient) {
                /**
                 * @servers [{"url":"https://override.example.com","description":"Override"}]
                 */
                suspend fun getOverride(): Result<Unit> {
                    client.request("/override") {
                        method = HttpMethod.Get
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals(1, endpoint.servers.size)
        assertEquals("https://override.example.com", endpoint.servers.first().url)
    }

    @Test
    fun `parse extracts response headers links and content tags`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class ResponseMetaApi(private val client: HttpClient) {
                /**
                 * @response 200 String ok
                 * @responseHeaders 200 {"X-Rate-Limit":{"description":"Limit","schema":{"type":"integer"}}}
                 * @responseLinks 200 {"next":{"operationId":"listUsers"}}
                 * @responseContent 200 {"application/json":{"schema":{"type":"string"}}}
                 */
                suspend fun listUsers(): Result<String> {
                    client.request("/users") {
                        method = HttpMethod.Get
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val response = endpoint.responses["200"]

        assertNotNull(response)
        val headers = response?.headers?.get("X-Rate-Limit")
        assertNotNull(headers)
        assertTrue(headers?.schema?.types?.contains("integer") == true)

        val links = response?.links?.get("next")
        assertNotNull(links)
        assertEquals("listUsers", links?.operationId)

        assertTrue(response?.content?.containsKey("application/json") == true)
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
    fun `parse extracts response extensions`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class ResponseExtensionsApi(private val client: HttpClient) {
                /**
                 * @response 200 String ok
                 * @responseExtensions 200 {"x-response": "yes"}
                 */
                suspend fun getItems(): Result<String> {
                    client.request("/items") { method = HttpMethod.Get }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val response = endpoint.responses["200"]

        assertNotNull(response)
        assertEquals("yes", response?.extensions?.get("x-response"))
    }

    @Test
    fun `parse extracts externalDocs from explicit tag`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class ExternalDocsApi(private val client: HttpClient) {
                /**
                 * @externalDocs {"url":"https://docs.example.com","description":"Docs","x-docs":1}
                 */
                suspend fun getDocs(): Result<Unit> {
                    client.request("/docs") { method = HttpMethod.Get }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()

        assertEquals("https://docs.example.com", endpoint.externalDocs?.url)
        assertEquals("Docs", endpoint.externalDocs?.description)
        assertEquals(1, endpoint.externalDocs?.extensions?.get("x-docs"))
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
    fun `parse extracts structured parameter examples from KDoc`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class ExampleApi(private val client: HttpClient) {
                /**
                 * @paramExample filter {"summary":"Filter","dataValue":{"status":"active"}}
                 */
                suspend fun list(filter: String): Result<Unit> {
                    client.request("/items") {
                        method = HttpMethod.Get
                        parameter("filter", filter)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val param = endpoint.parameters.first { it.name == "filter" }

        assertEquals("Filter", param.example?.summary)
        val data = param.example?.dataValue as? Map<*, *>
        assertEquals("active", data?.get("status"))
    }

    @Test
    fun `parse extracts external parameter examples from KDoc`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class ExternalExampleApi(private val client: HttpClient) {
                /**
                 * @paramExample avatar external:https://example.com/avatar.png
                 */
                suspend fun avatar(avatar: String): Result<Unit> {
                    client.request("/avatar") {
                        method = HttpMethod.Get
                        parameter("avatar", avatar)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val param = endpoint.parameters.first { it.name == "avatar" }

        assertEquals("https://example.com/avatar.png", param.example?.externalValue)
    }

    @Test
    fun `parse extracts parameter schema and content from KDoc`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class ParamSchemaApi(private val client: HttpClient) {
                /**
                 * @paramSchema limit {"type":"integer","minimum":1}
                 * @paramContent filter {"application/json":{"schema":{"type":"string"}}}
                 */
                suspend fun list(limit: Int, filter: String): Result<Unit> {
                    client.request("/items") {
                        method = HttpMethod.Get
                        parameter("limit", limit)
                        parameter("filter", filter)
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val limit = endpoint.parameters.first { it.name == "limit" }
        val filter = endpoint.parameters.first { it.name == "filter" }

        assertEquals(1.0, limit.schema?.minimum)
        assertTrue(filter.content.containsKey("application/json"))
        assertNull(filter.schema)
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
    fun `parse extracts parameters inside foreach loops`() {
        val code = """
            class FilterApi {
                suspend fun filter(filters: Map<String, String>): Result<Unit> {
                    client.request("/filters") {
                        method = HttpMethod.Get
                        filters.forEach { (key, value) ->
                            parameter(key, value)
                        }
                    }
                }
            }
        """.trimIndent()

        val endpoint = parser.parse(code).first()
        val param = endpoint.parameters.firstOrNull { it.location == ParameterLocation.QUERY }

        assertNotNull(param)
        assertEquals("filters", param?.name)
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

    @Test
    fun `parse extracts security requirements from KDoc`() {
        val code = """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class SecureApi(private val client: HttpClient) {
                /**
                 * @security {"api_key":[]}
                 * @security {"oauth2":["read"]}
                 */
                suspend fun secureCall(): Result<Unit> {
                    return client.request("/secure") { method = HttpMethod.Get }
                }

                /**
                 * @securityEmpty
                 */
                suspend fun publicCall(): Result<Unit> {
                    return client.request("/public") { method = HttpMethod.Get }
                }
            }
        """.trimIndent()

        val endpoints = parser.parse(code)
        val secure = endpoints.first { it.operationId == "secureCall" }
        val public = endpoints.first { it.operationId == "publicCall" }

        assertEquals(2, secure.security.size)
        assertEquals(emptyList<String>(), secure.security[0]["api_key"])
        assertEquals(listOf("read"), secure.security[1]["oauth2"])
        assertEquals(false, secure.securityExplicitEmpty)

        assertTrue(public.security.isEmpty())
        assertEquals(true, public.securityExplicitEmpty)
    }

    @Test
    fun `parseWithMetadata extracts webhooks from interface KDoc`() {
        val code = """
            /**
             * @webhooks {"onPing":{"post":{"operationId":"onPing","responses":{"200":{"description":"ok"}}}}}
             */
            interface IWebhookApi
        """.trimIndent()

        val result = parser.parseWithMetadata(code)
        val webhook = result.webhooks["onPing"]

        assertNotNull(webhook)
        assertEquals("onPing", webhook?.post?.operationId)
    }

    @Test
    fun `parseWithMetadata extracts root metadata from interface KDoc`() {
        val code = """
            /**
             * @openapi {"openapi":"3.2.0","jsonSchemaDialect":"https://spec.openapis.org/oas/3.1/dialect/base","${'$'}self":"https://example.com/openapi"}
             * @info {"title":"Meta API","version":"1.0.0","summary":"Summary"}
             * @servers [{"url":"https://api.example.com","description":"Prod"}]
             * @security {"api_key":[]}
             * @tags [{"name":"alpha","summary":"Alpha"}]
             * @externalDocs {"url":"https://docs.example.com","description":"Docs"}
             * @extensions {"x-root":true}
             * @pathsExtensions {"x-paths":"paths-ext"}
             * @webhooksExtensions {"x-webhooks":{"flag":true}}
             * @securitySchemes {"api_key":{"type":"apiKey","name":"X-API-KEY","in":"header"}}
             */
            interface IRootApi
        """.trimIndent()

        val result = parser.parseWithMetadata(code)
        val meta = result.metadata

        assertEquals("3.2.0", meta.openapi)
        assertEquals("https://spec.openapis.org/oas/3.1/dialect/base", meta.jsonSchemaDialect)
        assertEquals("https://example.com/openapi", meta.self)
        assertEquals("Meta API", meta.info?.title)
        assertEquals("1.0.0", meta.info?.version)
        assertEquals(1, meta.servers.size)
        assertEquals("https://api.example.com", meta.servers.first().url)
        assertEquals(1, meta.security.size)
        assertEquals(emptyList<String>(), meta.security.first()["api_key"])
        assertEquals(1, meta.tags.size)
        assertEquals("alpha", meta.tags.first().name)
        assertEquals("Docs", meta.externalDocs?.description)
        assertEquals(true, meta.extensions["x-root"])
        assertEquals("paths-ext", meta.pathsExtensions["x-paths"])
        assertEquals(mapOf("flag" to true), meta.webhooksExtensions["x-webhooks"])
        assertEquals("apiKey", meta.securitySchemes["api_key"]?.type)
    }
}
