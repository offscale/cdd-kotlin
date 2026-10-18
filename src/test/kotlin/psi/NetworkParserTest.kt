/** Unit and integration tests in [NetworkParserTest]. */
package psi

import domain.Callback
import domain.HttpMethod
import domain.OpenApiBoolean
import domain.OpenApiInt
import domain.OpenApiObject
import domain.OpenApiString
import domain.ParameterLocation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetworkParserTest {

  private val parser = NetworkParser()

  @AfterAll
  fun tearDown() {
    PsiInfrastructure.dispose()
  }

  @Test
  fun `parse extracts basic GET endpoint and strips absolute URL`() {
    val code =
        """
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
        """
            .trimIndent()

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
    val code =
        """
            class UserApi {
                suspend fun getUserById(id: String): Result<User> {
                     client.request("https://api.com/users/{id}") { method = HttpMethod.Get }
                }
            }
        """
            .trimIndent()

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
    val code =
        """
            class PostApi {
                suspend fun createPost(data: ResultDto): Result<ResultDto> {
                    client.request("https://api.com/posts") {
                        method = HttpMethod.Post
                        setBody(data)
                    }
                }
            }
        """
            .trimIndent()

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
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()

    assertNotNull(endpoint.requestBody?.content?.get("application/xml"))
  }

  @Test
  fun `parse preserves paramRef and responseRef tags`() {
    val code =
        """
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
        """
            .trimIndent()

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
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()

    assertEquals("payload", endpoint.requestBody?.description)
    assertEquals(false, endpoint.requestBody?.required)
    assertNotNull(endpoint.requestBody?.content?.get("application/xml"))
  }

  @Test
  fun `parse preserves missing requestBody content in KDoc`() {
    val code =
        """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class RawApi(private val client: HttpClient) {
                /**
                 * @requestBody {"description":"raw payload"}
                 */
                suspend fun sendRaw(): Result<Unit> {
                    client.request("/raw") {
                        method = HttpMethod.Post
                    }
                }
            }
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    val body = endpoint.requestBody

    assertNotNull(body)
    assertEquals("raw payload", body?.description)
    assertTrue(body?.content?.isEmpty() == true)
    assertFalse(body?.contentPresent ?: true)
  }

  @Test
  fun `parse extracts response summary`() {
    val code =
        """
            class SummaryApi {
                /**
                 * @response 200 String ok
                 * @responseSummary 200 Short summary
                 */
                suspend fun getSummary(): Result<String> {
                    client.request("/summary") { method = HttpMethod.Get }
                }
            }
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()

    assertEquals("Short summary", endpoint.responses["200"]?.summary)
  }

  @Test
  fun `parse captures component metadata tags`() {
    val code =
        """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            /**
             * @componentExamples {"Example1":{"summary":"example","value":{"id":1}}}
             * @componentLinks {"GetUser":{"operationId":"getUser"}}
             * @componentCallbacks {"OnEvent":{"{${'$'}request.body#/url}":{"post":{"responses":{"200":{"description":"ok"}}}}}}
             * @componentParameters {"Limit":{"name":"limit","in":"query","schema":{"type":"integer"}}}
             * @componentResponses {"NotFound":{"description":"missing"}}
             * @componentRequestBodies {"UserBody":{"content":{"application/json":{"schema":{"type":"object"}}}}}
             * @componentHeaders {"X-Rate-Limit":{"schema":{"type":"integer"}}}
             * @componentPathItems {"UserPath":{"get":{"responses":{"200":{"description":"ok"}}}}}
             * @componentMediaTypes {"application/json":{"schema":{"type":"string"}}}
             * @componentSchemas {"Extra":{"type":"object","properties":{"id":{"type":"string"}}}}
             * @componentsExtensions {"x-component-flag":true}
             */
            class ComponentApi(private val client: HttpClient) {
                suspend fun listUsers(): Result<String> {
                    client.request("/users") { method = HttpMethod.Get }
                }
            }
        """
            .trimIndent()

    val result = parser.parseWithMetadata(code)
    val metadata = result.metadata

    assertEquals(1, metadata.componentExamples.size)
    assertEquals(1, metadata.componentLinks.size)
    assertEquals(1, metadata.componentCallbacks.size)
    assertEquals(1, metadata.componentParameters.size)
    assertEquals(1, metadata.componentResponses.size)
    assertEquals(1, metadata.componentRequestBodies.size)
    assertEquals(1, metadata.componentHeaders.size)
    assertEquals(1, metadata.componentPathItems.size)
    assertEquals(1, metadata.componentMediaTypes.size)
    assertEquals(1, metadata.componentSchemas.size)
    assertEquals(1, metadata.componentsExtensions.size)
    assertTrue(metadata.componentExamples.containsKey("Example1"))
    assertTrue(metadata.componentLinks.containsKey("GetUser"))
    assertTrue(metadata.componentCallbacks.containsKey("OnEvent"))
    assertTrue(metadata.componentParameters.containsKey("Limit"))
    assertTrue(metadata.componentResponses.containsKey("NotFound"))
    assertTrue(metadata.componentRequestBodies.containsKey("UserBody"))
    assertTrue(metadata.componentHeaders.containsKey("X-Rate-Limit"))
    assertTrue(metadata.componentPathItems.containsKey("UserPath"))
    assertTrue(metadata.componentMediaTypes.containsKey("application/json"))
    assertTrue(metadata.componentSchemas.containsKey("Extra"))
    assertTrue(metadata.componentsExtensions.containsKey("x-component-flag"))

    val components = metadata.toComponents()
    assertNotNull(components)
    assertTrue(components!!.schemas.containsKey("Extra"))
    assertTrue(components!!.examples.containsKey("Example1"))
    assertTrue(components.links.containsKey("GetUser"))
    assertTrue(components.callbacks.containsKey("OnEvent"))
    assertTrue(components.parameters.containsKey("Limit"))
    assertTrue(components.responses.containsKey("NotFound"))
    assertTrue(components.requestBodies.containsKey("UserBody"))
    assertTrue(components.headers.containsKey("X-Rate-Limit"))
    assertTrue(components.pathItems.containsKey("UserPath"))
    assertTrue(components.mediaTypes.containsKey("application/json"))
    assertTrue(components.extensions.containsKey("x-component-flag"))
  }

  @Test
  fun `parse extracts callbacks from KDoc`() {
    val code =
        """
            class CallbackApi {
                /**
                 * @callbacks {"onEvent":{"${'$'}ref":"#/components/callbacks/EventCb"}}
                 */
                suspend fun withCallbacks(): Result<Unit> {
                    client.request("/callbacks") { method = HttpMethod.Post }
                }
            }
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    val callback = endpoint.callbacks["onEvent"]

    assertNotNull(callback)
    assertTrue(callback is Callback.Reference)
    assertEquals("#/components/callbacks/EventCb", (callback as Callback.Reference).reference.ref)
  }

  @Test
  fun `parse extracts operation extensions`() {
    val code =
        """
            class ExtensionApi {
                /**
                 * @extensions {"x-rate-limit":10,"x-flag":true}
                 */
                suspend fun getExtensions(): Result<Unit> {
                    client.request("/extensions") { method = HttpMethod.Get }
                }
            }
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()

    assertEquals(OpenApiInt(10L), endpoint.extensions["x-rate-limit"])
    assertEquals(OpenApiBoolean(true), endpoint.extensions["x-flag"])
  }

  @Test
  fun `parse captures custom schema keywords from response content`() {
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    val schema = endpoint.responses["200"]?.content?.get("application/json")?.schema

    assertEquals(OpenApiInt(123L), schema?.customKeywords?.get("customKeyword"))
    assertEquals(OpenApiString("yes"), schema?.extensions?.get("x-extra"))
  }

  @Test
  fun `parse falls back to function return type if body generic is missing`() {
    val code =
        """
            class ListApi {
                suspend fun getItems(): Result<List<String>> {
                    client.request("/items")
                }
            }
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    assertEquals("List<String>", endpoint.responseType)
  }

  @Test
  fun `parse extracts Query, Header and Cookie params`() {
    val code =
        """
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
        """
            .trimIndent()

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
    val code =
        """
            class QueryStringApi {
                suspend fun searchRaw(rawQuery: String): Result<Unit> {
                    client.request("/search") {
                        method = HttpMethod.Get
                        url.encodedQuery = rawQuery
                    }
                }
            }
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    val qs = endpoint.parameters.find { it.location == ParameterLocation.QUERYSTRING }

    assertNotNull(qs)
    assertEquals("rawQuery", qs?.name)
  }

  @Test
  fun `parse extracts parameter metadata and deprecated tag`() {
    val code =
        """
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
        """
            .trimIndent()

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
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    val param = endpoint.parameters.firstOrNull { it.name == "limit" }

    assertNotNull(param)
    assertEquals(OpenApiInt(50L), param?.extensions?.get("x-rate-limit"))
  }

  @Test
  fun `parse extracts operation servers from KDoc`() {
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()

    assertEquals(1, endpoint.servers.size)
    assertEquals("https://override.example.com", endpoint.servers.first().url)
  }

  @Test
  fun `parse extracts response headers links and content tags`() {
    val code =
        """
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
        """
            .trimIndent()

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
  fun `parse prefers most specific media type for response content`() {
    val code =
        """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class MediaPrefApi(private val client: HttpClient) {
                /**
                 * @responseContent 200 {"application/xml":{"schema":{"type":"string"}},"application/json":{"schema":{"type":"integer"}}}
                 */
                suspend fun list(): Result<Unit> {
                    client.request("/items") {
                        method = HttpMethod.Get
                    }
                }
            }
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    assertEquals("Int", endpoint.responses["200"]?.type)
  }

  @Test
  fun `parse infers list type from itemSchema response content`() {
    val code =
        """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class StreamApi(private val client: HttpClient) {
                /**
                 * @responseContent 200 {"application/jsonl":{"itemSchema":{"type":"integer"}}}
                 */
                suspend fun stream(): Result<Unit> {
                    client.request("/stream") {
                        method = HttpMethod.Get
                    }
                }
            }
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    assertEquals("List<Int>", endpoint.responses["200"]?.type)
  }

  @Test
  fun `parse extracts KDoc metadata and responses`() {
    val code =
        """
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
        """
            .trimIndent()

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
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    val response = endpoint.responses["200"]

    assertNotNull(response)
    assertEquals(OpenApiString("yes"), response?.extensions?.get("x-response"))
  }

  @Test
  fun `parse extracts externalDocs from explicit tag`() {
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()

    assertEquals("https://docs.example.com", endpoint.externalDocs?.url)
    assertEquals("Docs", endpoint.externalDocs?.description)
    assertEquals(OpenApiInt(1L), endpoint.externalDocs?.extensions?.get("x-docs"))
  }

  @Test
  fun `parse extracts parameter descriptions from KDoc`() {
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()

    val q = endpoint.parameters.find { it.name == "q" }
    val limit = endpoint.parameters.find { it.name == "limit" }

    assertEquals("Search term", q?.description)
    assertEquals("Max items", limit?.description)
  }

  @Test
  fun `parse extracts parameter examples from KDoc`() {
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    val param = endpoint.parameters.first { it.name == "q" }

    assertEquals("cats", param.example?.serializedValue)
    assertEquals("dogs", param.examples["dogs"]?.serializedValue)
  }

  @Test
  fun `parse extracts structured parameter examples from KDoc`() {
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    val param = endpoint.parameters.first { it.name == "filter" }

    assertEquals("Filter", param.example?.summary)
    val data = param.example?.dataValue as? OpenApiObject
    assertEquals(OpenApiString("active"), data?.properties?.get("status"))
  }

  @Test
  fun `parse extracts external parameter examples from KDoc`() {
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    val param = endpoint.parameters.first { it.name == "avatar" }

    assertEquals("https://example.com/avatar.png", param.example?.externalValue)
  }

  @Test
  fun `parse extracts parameter schema and content from KDoc`() {
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    val limit = endpoint.parameters.first { it.name == "limit" }
    val filter = endpoint.parameters.first { it.name == "filter" }

    assertEquals(1.0, limit.schema?.minimum)
    assertTrue(filter.content.containsKey("application/json"))
    assertNull(filter.schema)
  }

  @Test
  fun `parse extracts custom HTTP methods`() {
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    assertEquals(HttpMethod.CUSTOM, endpoint.method)
    assertEquals("COPY", endpoint.customMethod)
  }

  @Test
  fun `parse detects optional request bodies`() {
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    assertEquals("Payload?", endpoint.requestBodyType)
    assertEquals(false, endpoint.requestBody?.required)
  }

  @Test
  fun `parse marks optional parameters from nullability and defaults but keeps path required`() {
    val code =
        """
            class OptionalApi {
                suspend fun getItem(id: String?, q: String?, limit: Int = 10): Result<Unit> {
                    client.request("/items/{id}") {
                        parameter("q", q)
                        parameter("limit", limit)
                    }
                }
            }
        """
            .trimIndent()

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
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()
    val param = endpoint.parameters.firstOrNull { it.location == ParameterLocation.QUERY }

    assertNotNull(param)
    assertEquals("filters", param?.name)
  }

  @Test
  fun `parse ignores functions without request calls`() {
    val code =
        """
            class PlainApi {
                fun noop() { println("no request") }
            }
        """
            .trimIndent()

    val results = parser.parse(code)
    assertEquals(0, results.size)
  }

  @Test
  fun `parse handles baseUrl template and filters baseUrl param`() {
    val code =
        """
            import io.ktor.client.*
            import io.ktor.client.request.*

            class BaseApi(private val client: HttpClient) {
                suspend fun getOrg(baseUrl: String, org: String): Result<Unit> {
                    return client.request("${'$'}baseUrl/orgs/${'$'}{org}") {
                        parameter("q")
                    }
                }
            }
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()

    assertEquals("/orgs/{org}", endpoint.path)
    assertEquals(1, endpoint.parameters.size)
    assertEquals("org", endpoint.parameters.first().name)
  }

  @Test
  fun `parse defaults to GET on invalid method`() {
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()

    assertEquals("/", endpoint.path)
    assertEquals(HttpMethod.GET, endpoint.method)
  }

  @Test
  fun `parse handles empty path`() {
    val code =
        """
            import io.ktor.client.*
            import io.ktor.client.request.*

            class EmptyPathApi(private val client: HttpClient) {
                suspend fun root(): Result<Unit> {
                    return client.request("") { }
                }
            }
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()

    assertEquals("/", endpoint.path)
  }

  @Test
  fun `parse captures parameter schema and deprecation annotations`() {
    val code =
        """
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
        """
            .trimIndent()

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
    val code =
        """
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
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()

    assertEquals(null, endpoint.requestBodyType)
  }

  @Test
  fun `parse uses direct return type when not Result`() {
    val code =
        """
            import io.ktor.client.*
            import io.ktor.client.request.*

            class DirectReturnApi(private val client: HttpClient) {
                suspend fun count(): Int {
                    return client.request("/count")
                }
            }
        """
            .trimIndent()

    val endpoint = parser.parse(code).first()

    assertEquals("Int", endpoint.responseType)
  }

  @Test
  fun `parse extracts security requirements from KDoc`() {
    val code =
        """
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
        """
            .trimIndent()

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
    val code =
        """
            /**
             * @webhooks {"onPing":{"post":{"operationId":"onPing","responses":{"200":{"description":"ok"}}}}}
             */
            interface IWebhookApi
        """
            .trimIndent()

    val result = parser.parseWithMetadata(code)
    val webhook = result.webhooks["onPing"]

    assertNotNull(webhook)
    assertEquals("onPing", webhook?.post?.operationId)
  }

  @Test
  fun `parseWithMetadata extracts root metadata from interface KDoc`() {
    val code =
        """
            /**
             * @openapi {"openapi":"3.2.0","jsonSchemaDialect":"https://spec.openapis.org/oas/3.1/dialect/base","${'$'}self":"https://example.com/openapi"}
             * @info {"title":"Meta API","version":"1.0.0","summary":"Summary"}
             * @servers [{"url":"https://api.example.com","description":"Prod"}]
             * @security {"api_key":[]}
             * @tags [{"name":"alpha","summary":"Alpha"}]
             * @externalDocs {"url":"https://docs.example.com","description":"Docs"}
             * @extensions {"x-root":true}
             * @pathsExtensions {"x-paths":"paths-ext"}
             * @pathsEmpty
             * @pathItems {"/pets":{"summary":"Pets","description":"All pets"}}
             * @webhooksExtensions {"x-webhooks":{"flag":true}}
             * @webhooksEmpty
             * @securitySchemes {"api_key":{"type":"apiKey","name":"X-API-KEY","in":"header"}}
             */
            interface IRootApi
        """
            .trimIndent()

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
    assertTrue(meta.pathsExplicitEmpty)
    assertTrue(meta.webhooksExplicitEmpty)
    assertEquals(OpenApiBoolean(true), meta.extensions["x-root"])
    assertEquals(OpenApiString("paths-ext"), meta.pathsExtensions["x-paths"])
    assertEquals("Pets", meta.pathItems["/pets"]?.summary)
    assertEquals("All pets", meta.pathItems["/pets"]?.description)
    assertEquals(
        OpenApiObject(mapOf("flag" to OpenApiBoolean(true))), meta.webhooksExtensions["x-webhooks"])
    assertEquals("apiKey", meta.securitySchemes["api_key"]?.type)
  }

  @Test
  fun `parse root metadata captures pathsEmpty when it is the only tag`() {
    val code =
        """
            /**
             * @pathsEmpty
             */
            interface IEmptyPaths
        """
            .trimIndent()

    val result = parser.parseWithMetadata(code)
    assertTrue(result.metadata.pathsExplicitEmpty)
  }

  @Test
  fun `parse root metadata captures webhooksEmpty when it is the only tag`() {
    val code =
        """
            /**
             * @webhooksEmpty
             */
            interface IEmptyWebhooks
        """
            .trimIndent()

    val result = parser.parseWithMetadata(code)
    assertTrue(result.metadata.webhooksExplicitEmpty)
  }

  @Test
  fun `parse covers serializeContentValue, joinToString, ContentType constants, and parameter styles`() {
    val code =
        """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class EdgeApi(private val client: HttpClient) {
                /**
                 * @paramStyle p1 matrix
                 * @paramStyle p2 label
                 * @paramStyle p3 simple
                 * @paramStyle p4 form
                 * @paramStyle p5 cookie
                 * @paramStyle p6 spaceDelimited
                 * @paramStyle p7 pipeDelimited
                 * @paramStyle p8 deepObject
                 * @paramStyle p9 unknownStyle
                 * @paramExample p1 {"x-ext":1}
                 * @paramExample p2 {"notAnExampleKey":1}
                 * @paramExplode p1 false
                 * @paramRef p1 "#/components/parameters/P1"
                 * @paramRef p2 {"notRef":"x"}
                 * @requestBody {"${'$'}ref":"#/components/requestBodies/RB","summary":"RB sum","description":"RB desc"}
                 * @deprecated true
                 * @securityEmpty true
                 */
                suspend fun testStyles(
                    p1: String,
                    p2: String,
                    p3: String,
                    p4: String,
                    p5: String,
                    p6: String,
                    p7: String,
                    p8: String,
                    p9: String
                ): Result<Unit> {
                    client.request("/items/${'$'}{serializeContentValue(p1, "application/json")}/${'$'}{p2.entries.joinToString(",")}/${'$'}{p3.joinToString(",")}") {
                        method = HttpMethod.InvalidMethodName
                        contentType(ContentType.Application.Json)
                        parameter("p4", p4)
                        parameter("p5", p5)
                        parameter("p6", p6)
                        parameter("p7", p7)
                        parameter("p8", p8)
                        parameter("p9", p9)
                        parameter("onlyOneArg")
                        p1.forEach { parameter(it, "v") }
                    }
                }

                /**
                 * @security {"apiKey":"notArray"}
                 * @paramExample body {"x-ext":1}
                 */
                @Deprecated("old")
                suspend fun testUnknownContentType(body: String): Result<Unit> {
                    client.request("/other") {
                        method = CustomMethodExpr
                        contentType(CustomType)
                        forEach { parameter(it, "v") }
                        setBody(body)
                    }
                }
            }
        """
            .trimIndent()

    val endpoints = parser.parse(code)
    assertEquals(2, endpoints.size)
    val ep1 = endpoints[0]
    assertEquals("/items/{p1}/{p2}/{p3}", ep1.path)
    assertEquals(HttpMethod.GET, ep1.method)
    assertTrue(ep1.deprecated)
    assertTrue(ep1.securityExplicitEmpty)
    assertEquals("#/components/requestBodies/RB", ep1.requestBody?.reference?.ref)
    assertEquals(domain.ParameterStyle.MATRIX, ep1.parameters.first { it.name == "p1" }.style)
    assertEquals(domain.ParameterStyle.LABEL, ep1.parameters.first { it.name == "p2" }.style)
    assertEquals(domain.ParameterStyle.SIMPLE, ep1.parameters.first { it.name == "p3" }.style)
    assertEquals(domain.ParameterStyle.FORM, ep1.parameters.first { it.name == "p4" }.style)
    assertEquals(domain.ParameterStyle.COOKIE, ep1.parameters.first { it.name == "p5" }.style)
    assertEquals(
        domain.ParameterStyle.SPACE_DELIMITED, ep1.parameters.first { it.name == "p6" }.style)
    assertEquals(
        domain.ParameterStyle.PIPE_DELIMITED, ep1.parameters.first { it.name == "p7" }.style)
    assertEquals(domain.ParameterStyle.DEEP_OBJECT, ep1.parameters.first { it.name == "p8" }.style)
    assertNull(ep1.parameters.first { it.name == "p9" }.style)

    val ep2 = endpoints[1]
    assertEquals(HttpMethod.GET, ep2.method)
    assertTrue(ep2.deprecated)
    assertEquals(emptyList<String>(), ep2.security.first()["apiKey"])
    assertEquals(true, psi.network.AuthPsiParser.parseBooleanTagValue(null))
    assertEquals(false, psi.network.AuthPsiParser.parseBooleanTagValue("false"))
    assertNull(psi.network.AuthPsiParser.parseBooleanTagValue("invalid"))
    assertEquals(
        "{unclosed", psi.network.RequestBodyPsiParser.parseReferenceObjectValue("{unclosed")?.ref)
    assertNull(psi.network.RequestBodyPsiParser.parseReferenceObjectValue("123"))

    // AuthPsiParser edge cases for invalid/empty root metadata tags
    val badMetaCode =
        """
            /**
             * @pathsEmpty invalidBool
             * @webhooksEmpty false
             * @extensions
             * @extensions {bad
             * @extensions [1]
             * @extensions {"x-e":1}
             * @pathsExtensions [1]
             * @webhooksExtensions {bad
             * @componentExamples
             * @componentExamples {bad
             * @componentLinks
             * @componentLinks {bad
             * @componentCallbacks
             * @componentCallbacks {bad
             * @componentCallbacks [1]
             */
            interface IBadMeta

            class UnannotatedClass {
                /** Regular kdoc without root tags */
                fun noop() {}
            }
        """
            .trimIndent()
    val badMetaRes = parser.parseWithMetadata(badMetaCode)
    assertFalse(badMetaRes.metadata.pathsExplicitEmpty)
    assertEquals(1, badMetaRes.metadata.extensions.size)
    assertTrue(badMetaRes.metadata.pathsExtensions.isEmpty())
    assertTrue(badMetaRes.metadata.componentExamples.isEmpty())
    assertTrue(badMetaRes.metadata.componentLinks.isEmpty())
    assertTrue(badMetaRes.metadata.componentCallbacks.isEmpty())

    // RequestBodyPsiParser edge cases
    val rbEdgeCode =
        """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class RbEdgeApi(private val client: HttpClient) {
                /**
                 * @requestBody
                 * @response 200 Unit ok
                 * @response 211
                 * @responseHeaders 200
                 * @responseHeaders 200   
                 * @responseHeaders 200 {bad
                 * @responseHeaders 208 {"X-H":{"type":"string"}}
                 * @responseLinks 200
                 * @responseLinks 200 {"L0":{"operationId":"op"}}
                 * @responseLinks 206 {"L":{"operationId":"op"}}
                 * @responseContent 200
                 * @responseContent 200 {"application/json":{"schema":{"type":"string"}}}
                 * @responseContent 201 {"application/json":{}}
                 * @responseContent 210 {"application/json":{"schema":{"type":"string"}}}
                 * @responseContent 205 {"application/json":{"schema":{"type":"string"}}}
                 * @responseSummary
                 * @responseSummary 200
                 * @responseSummary 200   
                 * @responseSummary 200 Summary for 200
                 * @responseSummary 202 Valid summary
                 * @responseRef 203 "#/components/responses/R203"
                 * @responseRef 200 {"${'$'}ref":"#/components/responses/R200","summary":"S200","description":"D200"}
                 * @responseRef 204 {"notRef":1}
                 * @response 205 String ok
                 * @responseExtensions 205 {"x-ext":1}
                 * @responseExtensions 205 {"x-ext2":2}
                 * @responseExtensions 205 {bad
                 * @responseExtensions 205 [1]
                 * @responseExtensions 205 {"non-x":1}
                 * @responseExtensions 207 {"x-new":1}
                 * @responseExtensions 209
                 */
                suspend fun op1(): Result<Unit> {
                    client.request("/op1") {
                        contentType()
                        contentType(ContentType.Application)
                    }
                }

                /**
                 * @param body Body param doc
                 * @requestBody {"required":false}
                 */
                suspend fun op2(body: String): Result<Unit> {
                    client.request("/op2") { setBody(body) }
                }

                /**
                 * @requestBody {bad
                 * @responseRef 200
                 */
                suspend fun op2b(body: String): Result<Unit> {
                    client.request("/op2b") { setBody(body) }
                }

                /**
                 * @requestBody [1]
                 */
                suspend fun op3(body: String): Result<Unit> {
                    client.request("/op3") { setBody(body) }
                }

                /**
                 * @requestBody {"description":"only desc"}
                 */
                suspend fun op4(): Result<Unit> {
                    client.request("/op4") {}
                }

                /**
                 * @requestBody {"description":"desc5","content":{"text/plain":{"schema":{"type":"string"}}},"required":false,"x-rb":1}
                 */
                suspend fun op5(body: String): Result<Unit> {
                    client.request("/op5") { setBody(body) }
                }
            }
        """
            .trimIndent()
    val rbEps = parser.parse(rbEdgeCode)
    assertEquals(6, rbEps.size)
    assertEquals("S200", rbEps[0].responses["200"]?.summary)
    assertEquals("D200", rbEps[0].responses["200"]?.description)
    assertEquals("Valid summary", rbEps[0].responses["202"]?.summary)
    assertEquals("#/components/responses/R203", rbEps[0].responses["203"]?.reference?.ref)
    assertEquals(2, rbEps[0].responses["205"]?.extensions?.size)
    assertEquals(1, rbEps[0].responses["207"]?.extensions?.size)
    assertEquals(false, rbEps[1].requestBody?.required)
    val mergedRb =
        psi.network.RequestBodyPsiParser.mergeRequestBody(
            domain.RequestBody(description = "base desc"),
            psi.network.RequestBodyParseResult(domain.RequestBody(description = null), false))
    assertEquals("base desc", mergedRb.description)
    assertEquals("only desc", rbEps[4].requestBody?.description)
    assertEquals(false, rbEps[5].requestBody?.required)
    assertEquals(OpenApiInt(1L), rbEps[5].requestBody?.extensions?.get("x-rb"))
    assertTrue(psi.network.AuthPsiParser.parseCallbacksJson("[1]").isEmpty())
    assertTrue(psi.network.AuthPsiParser.parseCallbacksJson("{bad").isEmpty())
    assertTrue(psi.network.RequestBodyPsiParser.parseContentJson("{bad").isEmpty())
    assertTrue(psi.network.RequestBodyPsiParser.contentForType(null).isEmpty())
    assertTrue(psi.network.RequestBodyPsiParser.contentForType("Unit").isEmpty())

    // EndpointPsiParser comprehensive edge cases
    val epEdgeCode =
        """
            import io.ktor.client.*
            import io.ktor.client.request.*
            import io.ktor.http.*

            class EpEdgeApi(private val client: HttpClient) {
                fun noLambdaRequest() {
                    client.request()
                }

                /**
                 * @param
                 * @param pOnlyName
                 * @paramExample
                 * @paramExample p1
                 * @paramExample p1 bad k: v
                 * @paramExample p1 {"x-ext":1,"value":"v"}
                 * @paramExample p1 [1, 2]
                 * @paramExample p1 {badJson
                 * @paramStyle
                 * @paramStyle p1
                 * @paramStyle p1 invalidStyle
                 * @paramStyle p1 matrix
                 * @paramStyle p2 label
                 * @paramStyle p3 simple
                 * @paramStyle p4 form
                 * @paramStyle p5 cookie
                 * @paramStyle p6 spaceDelimited
                 * @paramStyle p7 pipeDelimited
                 * @paramStyle p8 deepObject
                 * @paramExplode
                 * @paramExplode p1 invalidBool
                 * @paramExplode p1 true
                 * @paramAllowReserved
                 * @paramAllowReserved p1 invalidBool
                 * @paramAllowReserved p1 true
                 * @paramAllowEmptyValue
                 * @paramAllowEmptyValue p1 invalidBool
                 * @paramAllowEmptyValue p1 true
                 * @paramSchema
                 * @paramSchema p1
                 * @paramSchema p1 {bad
                 * @paramContent
                 * @paramContent p1
                 * @paramContent p1 {bad
                 * @paramContent p2 {}
                 * @paramRef
                 * @paramRef p1
                 * @paramRef p1 123
                 * @paramExtensions
                 * @paramExtensions p1
                 * @paramExtensions p1 {bad
                 * @paramExtensions p1 {"non-x":1}
                 * @paramExtensions p1 {"x-a":1}
                 * @paramExtensions p1 {"x-b":2}
                 * @callbacks
                 * @callbacks {"cb":{}}
                 * @extensions
                 * @extensions {bad
                 * @extensions [1]
                 * @description
                 * @operationIdOmitted true
                 * @deprecated reason
                 * @tag a, , b
                 */
                suspend fun edgeOp(
                    @Deprecated("old") p1: String,
                    p2: String = "def",
                    `class`: String,
                    optBody: String? = null
                ) {
                    client.request("http://example.com/${'$'}{1 + 2}/${'$'}{`class`}") {
                        method = HttpMethod("GET")
                        url.encodedQuery = ""
                        parameter("onlyOneArg")
                        header("onlyOneArg")
                        cookie("onlyOneArg")
                        parameter(123, "unknownKey")
                        someObj.otherCall { parameter(123, "notForEach") }
                        setBody()
                        setBody(unknownBodyVar)
                    }
                }

                suspend fun edgeOp2(optBody: String = "def"): String {
                    client.request("http://example.com:8080/sub") {
                        method = HttpMethod("")
                        setBody(optBody)
                    }
                }

                suspend fun edgeOp3(): Unit {
                    client.request("httpnotproto") {
                        method = OtherMethod
                    }
                }
            }
        """
            .trimIndent()
    val epEdges = parser.parse(epEdgeCode)
    assertEquals(4, epEdges.size)
    assertFalse(epEdges[1].operationIdExplicit)
    assertTrue(epEdges[1].deprecated)
    assertEquals(listOf("a", "b"), epEdges[1].tags)
    assertEquals(false, epEdges[2].requestBody?.required)
    assertEquals("String", epEdges[2].responses["200"]?.type)
    assertNull(epEdges[3].responses["200"]?.type)
  }
}
