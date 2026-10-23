/** Unit and integration tests in [Oas32ComplianceTest]. */
package verification

import domain.*
import openapi.OpenApiAssembler
import openapi.OpenApiParser
import openapi.OpenApiValidator
import openapi.OpenApiWriter
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import psi.DtoGenerator
import psi.DtoParser
import psi.NetworkGenerator
import psi.NetworkParser
import psi.PsiInfrastructure
import psi.toMetadata

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Oas32ComplianceTest {

  private val parser = OpenApiParser()
  private val writer = OpenApiWriter()
  private val validator = OpenApiValidator()
  private val dtoGenerator = DtoGenerator()
  private val dtoParser = DtoParser()
  private val networkGenerator = NetworkGenerator()
  private val networkParser = NetworkParser()
  private val assembler = OpenApiAssembler()

  @AfterAll
  fun tearDown() {
    PsiInfrastructure.dispose()
  }

  private fun roundTripViaPsiAndJson(definition: OpenApiDefinition): OpenApiDefinition {
    val json = writer.writeJson(definition)
    val parsedJson = parser.parseString(json, OpenApiParser.Format.JSON)
    val yaml = writer.writeYaml(parsedJson)
    val parsedYaml = parser.parseString(yaml, OpenApiParser.Format.YAML)

    val metadata = parsedYaml.toMetadata()
    val endpoints = OpenApiPathFlattener.flattenPaths(parsedYaml.paths)

    val apiFile =
        networkGenerator
            .generateApi(
                packageName = "com.compliance",
                apiName = "ComplianceApi",
                endpoints = endpoints,
                webhooks = parsedYaml.webhooks,
                metadata = metadata)
            .getOrThrow()

    val parsedNetwork = networkParser.parseWithMetadata(apiFile.text)
    val schemas =
        parsedYaml.components?.schemas?.values.orEmpty().flatMap { schema ->
          val ktFile = dtoGenerator.generateDto("com.compliance", schema)
          dtoParser.parse(ktFile.text)
        }

    return assembler.assemble(
        info = parsedNetwork.metadata.info ?: Info("Test", "1.0"),
        schemas = schemas,
        endpoints = parsedNetwork.endpoints,
        servers = parsedNetwork.metadata.servers,
        webhooks = parsedNetwork.webhooks,
        webhooksExtensions = parsedNetwork.metadata.webhooksExtensions,
        webhooksExplicitEmpty = parsedNetwork.metadata.webhooksExplicitEmpty,
        pathsExtensions = parsedNetwork.metadata.pathsExtensions,
        pathsExplicitEmpty = parsedNetwork.metadata.pathsExplicitEmpty,
        pathItems = parsedNetwork.metadata.pathItems,
        security = parsedNetwork.metadata.security,
        securityExplicitEmpty = parsedNetwork.metadata.securityExplicitEmpty,
        tags = parsedNetwork.metadata.tags,
        externalDocs = parsedNetwork.metadata.externalDocs,
        extensions = parsedNetwork.metadata.extensions,
        openapi = parsedNetwork.metadata.openapi ?: "3.2.0",
        jsonSchemaDialect = parsedNetwork.metadata.jsonSchemaDialect,
        self = parsedNetwork.metadata.self,
        liftCommonPathMetadata = true,
        components =
            Components(
                schemas = emptyMap(),
                securitySchemes = parsedNetwork.metadata.securitySchemes,
                examples = parsedNetwork.metadata.componentExamples,
                links = parsedNetwork.metadata.componentLinks,
                callbacks = parsedNetwork.metadata.componentCallbacks,
                parameters = parsedNetwork.metadata.componentParameters,
                responses = parsedNetwork.metadata.componentResponses,
                requestBodies = parsedNetwork.metadata.componentRequestBodies,
                headers = parsedNetwork.metadata.componentHeaders,
                pathItems = parsedNetwork.metadata.componentPathItems,
                mediaTypes = parsedNetwork.metadata.componentMediaTypes,
                extensions = parsedNetwork.metadata.componentsExtensions))
  }

  @Test
  fun `Domain Supports Root Configuration (jsonSchemaDialect)`() {
    val root =
        OpenApiDefinition(
            info = Info("Test", "1.0"),
            pathsExplicitEmpty = true,
            jsonSchemaDialect = "https://spec.openapis.org/oas/3.1/dialect/base")
    assertTrue(validator.validate(root).isEmpty())

    val roundTripped = roundTripViaPsiAndJson(root)
    assertEquals("3.2.0", roundTripped.openapi)
    assertEquals("https://spec.openapis.org/oas/3.1/dialect/base", roundTripped.jsonSchemaDialect)
  }

  @Test
  fun `Domain Supports Webhooks`() {
    val root =
        OpenApiDefinition(
            info = Info("Webhooks API", "1.0"),
            components =
                Components(
                    schemas =
                        mapOf(
                            "Pet" to
                                SchemaDefinition(
                                    name = "Pet",
                                    type = "object",
                                    properties = mapOf("name" to SchemaProperty("string"))))),
            webhooks =
                mapOf(
                    "newPet" to
                        PathItem(
                            post =
                                EndpointDefinition(
                                    path = "/",
                                    method = HttpMethod.POST,
                                    operationId = "onNewPet",
                                    requestBodyType = "Pet",
                                    responses =
                                        mapOf(
                                            "200" to
                                                EndpointResponse(
                                                    statusCode = "200", description = "ok"))))))
    assertTrue(validator.validate(root).isEmpty())

    val roundTripped = roundTripViaPsiAndJson(root)
    assertTrue(roundTripped.webhooks.containsKey("newPet"))
    val pathItem = roundTripped.webhooks["newPet"]
    assertNotNull(pathItem?.post)
    assertEquals(HttpMethod.POST, pathItem?.post?.method)
    assertEquals("onNewPet", pathItem?.post?.operationId)
  }

  @Test
  fun `Domain Supports Callbacks`() {
    val registerOp =
        EndpointDefinition(
            path = "/subscribe",
            method = HttpMethod.POST,
            operationId = "subscribeHook",
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")),
            callbacks =
                mapOf(
                    "onDataEvent" to
                        Callback.Inline(
                            expressions =
                                mapOf(
                                    "{\$request.query.callbackUrl}" to
                                        PathItem(
                                            post =
                                                EndpointDefinition(
                                                    path = "/",
                                                    method = HttpMethod.POST,
                                                    operationId = "sendEvent",
                                                    requestBodyType = "String",
                                                    responses =
                                                        mapOf(
                                                            "200" to
                                                                EndpointResponse(
                                                                    statusCode = "200",
                                                                    description = "ok"))))))))
    val root =
        OpenApiDefinition(
            info = Info("Callback API", "1.0"),
            paths = mapOf("/subscribe" to PathItem(post = registerOp)))
    assertTrue(validator.validate(root).isEmpty())

    val roundTripped = roundTripViaPsiAndJson(root)
    val rtOp = roundTripped.paths["/subscribe"]?.post
    assertNotNull(rtOp)
    assertTrue(rtOp!!.callbacks.containsKey("onDataEvent"))
    val callback = rtOp.callbacks["onDataEvent"] as? Callback.Inline
    assertTrue(callback?.expressions?.containsKey("{\$request.query.callbackUrl}") == true)
    assertEquals(
        "sendEvent", callback?.expressions?.get("{\$request.query.callbackUrl}")?.post?.operationId)
  }

  @Test
  fun `Domain Supports Links`() {
    val link =
        Link(
            operationId = "getUser",
            parameters = mapOf("userId" to OpenApiString("\$response.body#/id")),
            requestBody = OpenApiString("\$request.body"),
            description = "Get the user associated with this resource")

    val response =
        EndpointResponse(statusCode = "200", description = "ok", links = mapOf("UserLink" to link))
    val root =
        OpenApiDefinition(
            info = Info("Links API", "1.0"),
            paths =
                mapOf(
                    "/users" to
                        PathItem(
                            get =
                                EndpointDefinition(
                                    path = "/users",
                                    method = HttpMethod.GET,
                                    operationId = "getUser",
                                    responses = mapOf("200" to response)))))
    assertTrue(validator.validate(root).isEmpty())

    val roundTripped = roundTripViaPsiAndJson(root)
    val actualLink =
        roundTripped.paths["/users"]?.get?.responses?.get("200")?.links?.get("UserLink")
    assertNotNull(actualLink)
    assertEquals("getUser", actualLink?.operationId)
    assertEquals(OpenApiString("\$response.body#/id"), actualLink?.parameters?.get("userId"))
    assertEquals(OpenApiString("\$request.body"), actualLink?.requestBody)
  }

  @Test
  fun `Domain Supports Global Security Requirements`() {
    val root =
        OpenApiDefinition(
            info = Info("Secure API", "1.0"),
            pathsExplicitEmpty = true,
            components =
                Components(
                    securitySchemes =
                        mapOf(
                            "api_key" to
                                SecurityScheme(type = "apiKey", name = "X-Key", `in` = "header"),
                            "petstore_auth" to
                                SecurityScheme(
                                    type = "oauth2",
                                    flows =
                                        OAuthFlows(
                                            implicit =
                                                OAuthFlow(
                                                    authorizationUrl = "https://auth.example.com",
                                                    scopes =
                                                        mapOf(
                                                            "write:pets" to "write",
                                                            "read:pets" to "read")))))),
            security =
                listOf(
                    mapOf("api_key" to emptyList()),
                    mapOf("petstore_auth" to listOf("write:pets", "read:pets"))))
    assertTrue(validator.validate(root).isEmpty())

    val roundTripped = roundTripViaPsiAndJson(root)
    assertEquals(2, roundTripped.security.size)
    assertTrue(roundTripped.security[0].containsKey("api_key"))
    assertTrue(roundTripped.security[0]["api_key"]!!.isEmpty())
    assertEquals(listOf("write:pets", "read:pets"), roundTripped.security[1]["petstore_auth"])
  }

  @Test
  fun `Domain Supports Components and Security Schemes`() {
    val root =
        OpenApiDefinition(
            info = Info("Security Schemes API", "1.0"),
            components =
                Components(
                    securitySchemes =
                        mapOf(
                            "OAuth2" to
                                SecurityScheme(
                                    type = "oauth2",
                                    flows =
                                        OAuthFlows(
                                            implicit =
                                                OAuthFlow(
                                                    authorizationUrl =
                                                        "https://example.com/api/oauth/dialog",
                                                    scopes =
                                                        mapOf("write:pets" to "modify pets")))),
                            "ApiKey" to
                                SecurityScheme(
                                    type = "apiKey", name = "X-API-KEY", `in` = "header"),
                            "Basic" to SecurityScheme(type = "http", scheme = "basic"))))
    assertTrue(validator.validate(root).isEmpty())

    val roundTripped = roundTripViaPsiAndJson(root)
    val schemes = roundTripped.components?.securitySchemes.orEmpty()
    val oauth = schemes["OAuth2"]
    val apikey = schemes["ApiKey"]
    val basic = schemes["Basic"]

    assertNotNull(oauth)
    assertEquals("oauth2", oauth?.type)
    assertEquals("https://example.com/api/oauth/dialog", oauth?.flows?.implicit?.authorizationUrl)
    assertEquals("modify pets", oauth?.flows?.implicit?.scopes?.get("write:pets"))

    assertNotNull(apikey)
    assertEquals("apiKey", apikey?.type)
    assertEquals("header", apikey?.`in`)
    assertEquals("X-API-KEY", apikey?.name)

    assertNotNull(basic)
    assertEquals("http", basic?.type)
    assertEquals("basic", basic?.scheme)
  }

  @Test
  fun `Domain Components holds Schemas and Parameters`() {
    val root =
        OpenApiDefinition(
            info = Info("Components API", "1.0"),
            components =
                Components(
                    schemas =
                        mapOf(
                            "User" to
                                SchemaDefinition(
                                    name = "User",
                                    type = "object",
                                    properties = mapOf("id" to SchemaProperty("integer")))),
                    parameters =
                        mapOf(
                            "limitParam" to
                                EndpointParameter(
                                    name = "limit",
                                    type = "integer",
                                    location = ParameterLocation.QUERY,
                                    schema = SchemaProperty("integer"))),
                    responses =
                        mapOf(
                            "NotFound" to
                                EndpointResponse(
                                    statusCode = "404", description = "Entity not found"))))
    assertTrue(validator.validate(root).isEmpty())

    val roundTripped = roundTripViaPsiAndJson(root)
    val components = roundTripped.components!!
    assertTrue(components.schemas.containsKey("User"))
    assertEquals("object", components.schemas["User"]?.type)

    val param = components.parameters["limitParam"]
    assertNotNull(param)
    assertEquals("limit", param?.name)
    assertEquals(ParameterLocation.QUERY, param?.location)

    val resp = components.responses["NotFound"]
    assertNotNull(resp)
    assertEquals("Entity not found", resp?.description)
  }

  @Test
  fun `Domain Components holds Headers and PathItems`() {
    val root =
        OpenApiDefinition(
            info = Info("Headers & PathItems API", "1.0"),
            components =
                Components(
                    headers =
                        mapOf(
                            "X-Rate-Limit" to
                                Header(
                                    type = "integer",
                                    schema = SchemaProperty("integer"),
                                    description = "Request limit",
                                    required = true)),
                    pathItems =
                        mapOf(
                            "UserPath" to
                                PathItem(
                                    summary = "User Operations",
                                    get =
                                        EndpointDefinition(
                                            path = "/users",
                                            method = HttpMethod.GET,
                                            operationId = "getAllUsers",
                                            responses =
                                                mapOf(
                                                    "200" to
                                                        EndpointResponse(
                                                            statusCode = "200",
                                                            description = "ok")))))))
    assertTrue(validator.validate(root).isEmpty())

    val roundTripped = roundTripViaPsiAndJson(root)
    val components = roundTripped.components!!
    val header = components.headers["X-Rate-Limit"]
    assertNotNull(header)
    assertEquals("integer", header?.schema?.type)
    assertEquals("Request limit", header?.description)
    assertEquals(true, header?.required)

    val pathItem = components.pathItems["UserPath"]
    assertNotNull(pathItem)
    assertEquals("User Operations", pathItem?.summary)
    assertEquals("getAllUsers", pathItem?.get?.operationId)
  }

  @Test
  fun `Domain Components holds MediaTypes and Examples`() {
    val example = ExampleObject(summary = "Sample", serializedValue = "{\"id\":1}")
    val mediaType =
        MediaTypeObject(schema = SchemaProperty("string"), examples = mapOf("sample" to example))
    val requestBody =
        RequestBody(
            description = "Payload",
            content = mapOf("application/json" to mediaType),
            required = true)

    val root =
        OpenApiDefinition(
            info = Info("MediaTypes API", "1.0"),
            components =
                Components(
                    examples = mapOf("Sample" to example),
                    mediaTypes = mapOf("application_json" to mediaType),
                    requestBodies = mapOf("Create" to requestBody)))
    assertTrue(validator.validate(root).isEmpty())

    val roundTripped = roundTripViaPsiAndJson(root)
    val components = roundTripped.components!!
    assertEquals("Sample", components.examples.keys.first())
    assertEquals("{\"id\":1}", components.examples["Sample"]?.serializedValue)
    assertTrue(components.mediaTypes.containsKey("application_json"))
    assertEquals("Payload", components.requestBodies["Create"]?.description)
  }

  @Test
  fun `Domain Supports Discriminator Mapping`() {
    val dogSchema =
        SchemaDefinition(
            name = "Dog",
            type = "object",
            properties =
                mapOf("petType" to SchemaProperty("string"), "bark" to SchemaProperty("boolean")))
    val petSchema =
        SchemaDefinition(
            name = "Pet",
            type = "object",
            oneOf = listOf("#/components/schemas/Dog"),
            oneOfSchemas = listOf(SchemaProperty(ref = "#/components/schemas/Dog")),
            required = listOf("petType"),
            properties = mapOf("petType" to SchemaProperty("string")),
            discriminator =
                Discriminator(
                    propertyName = "petType", mapping = mapOf("dog" to "#/components/schemas/Dog")))
    val root =
        OpenApiDefinition(
            info = Info("Discriminator API", "1.0"),
            components = Components(schemas = mapOf("Pet" to petSchema, "Dog" to dogSchema)))
    assertTrue(validator.validate(root).isEmpty())

    val roundTripped = roundTripViaPsiAndJson(root)
    val rtPet = roundTripped.components?.schemas?.get("Pet")
    assertNotNull(rtPet)
    assertEquals("petType", rtPet?.discriminator?.propertyName)
    assertEquals("#/components/schemas/Dog", rtPet?.discriminator?.mapping?.get("dog"))
  }

  @Test
  fun `Domain Supports XML Metadata`() {
    val schema =
        SchemaDefinition(
            name = "Customer",
            type = "object",
            xml =
                Xml(
                    name = "customer_record",
                    namespace = "http://example.com/schema",
                    prefix = "crm",
                    nodeType = "element"),
            properties =
                mapOf(
                    "id" to SchemaProperty("string", xml = Xml(attribute = true, name = "cid")),
                    "tags" to
                        SchemaProperty(
                            "array",
                            items = SchemaProperty("string"),
                            xml = Xml(wrapped = true, name = "tag_list"))))
    val root =
        OpenApiDefinition(
            info = Info("XML API", "1.0"),
            components = Components(schemas = mapOf("Customer" to schema)))
    assertTrue(validator.validate(root).isEmpty())

    val roundTripped = roundTripViaPsiAndJson(root)
    val rtCustomer = roundTripped.components?.schemas?.get("Customer")
    assertNotNull(rtCustomer?.xml)
    assertEquals("customer_record", rtCustomer?.xml?.name)
    assertEquals("http://example.com/schema", rtCustomer?.xml?.namespace)
    assertEquals("crm", rtCustomer?.xml?.prefix)
    assertEquals("element", rtCustomer?.xml?.nodeType)

    val idProp = rtCustomer?.properties?.get("id")
    assertNotNull(idProp?.xml)
    assertEquals(true, idProp?.xml?.attribute)
    assertEquals("cid", idProp?.xml?.name)

    val tagsProp = rtCustomer?.properties?.get("tags")
    assertNotNull(tagsProp?.xml)
    assertEquals(true, tagsProp?.xml?.wrapped)
    assertEquals("tag_list", tagsProp?.xml?.name)
  }

  @Test
  fun `Domain Preserves Absence and Skipped Semantics Across OpenAPI 3_2_0 RoundTrip`() {
    val minimal =
        OpenApiDefinition(
            openapi = "3.2.0",
            info = Info(title = "Minimal API", version = "0.1.0"),
            pathsExplicitEmpty = true)
    assertTrue(validator.validate(minimal).isEmpty())

    val roundTripped = roundTripViaPsiAndJson(minimal)
    assertEquals("3.2.0", roundTripped.openapi)
    assertNull(roundTripped.self)
    assertNull(roundTripped.jsonSchemaDialect)
    assertNull(roundTripped.info.summary)
    assertNull(roundTripped.info.description)
    assertNull(roundTripped.info.termsOfService)
    assertNull(roundTripped.info.contact)
    assertNull(roundTripped.info.license)
    assertTrue(roundTripped.servers.isEmpty())
    assertTrue(roundTripped.paths.isEmpty())
    assertTrue(roundTripped.webhooks.isEmpty())
    assertTrue(roundTripped.security.isEmpty())
    assertTrue(roundTripped.tags.isEmpty())
    assertNull(roundTripped.externalDocs)
  }

  @Test
  fun `Domain Preserves Presence Absence and Skipped Semantics Across Swagger 2_0 RoundTrip`() {
    val swagger2Json =
        """
        {
          "swagger": "2.0",
          "info": {
            "title": "Minimal Swagger",
            "version": "1.0.0"
          },
          "paths": {}
        }
        """
            .trimIndent()
    val parsed = parser.parseString(swagger2Json, OpenApiParser.Format.JSON)
    assertEquals("Minimal Swagger", parsed.info.title)
    assertNull(parsed.info.description)
    assertTrue(parsed.paths.isEmpty())
    assertTrue(parsed.servers.isEmpty())

    val emittedSwaggerJson = writer.write(parsed, OpenApiWriter.Format.JSON)
    assertTrue(emittedSwaggerJson.contains("\"swagger\": \"2.0\""))
    val reparsed = parser.parseString(emittedSwaggerJson, OpenApiParser.Format.JSON)
    assertEquals("Minimal Swagger", reparsed.info.title)
    assertNull(reparsed.info.contact)
    assertNull(reparsed.info.license)
    assertNull(reparsed.externalDocs)
  }
}
