/** Unit and integration tests in [OpenApiMetadataTest]. */
package psi

import domain.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiMetadataTest {

  @Test
  fun `toMetadata copies root and component metadata`() {
    val definition =
        OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("Meta API", "1.0"),
            jsonSchemaDialect = "https://spec.openapis.org/oas/3.1/dialect/base",
            self = "https://example.com/openapi",
            servers = listOf(Server(url = "https://api.example.com")),
            security = emptyList(),
            securityExplicitEmpty = true,
            tags = listOf(Tag(name = "alpha")),
            externalDocs = ExternalDocumentation("docs", "https://example.com/docs"),
            extensions = mapOf("x-root" to OpenApiString("root")),
            pathsExtensions = mapOf("x-paths" to OpenApiString("paths")),
            pathsExplicitEmpty = true,
            webhooks =
                mapOf(
                    "onPing" to
                        PathItem(
                            post =
                                EndpointDefinition(
                                    path = "/",
                                    method = HttpMethod.POST,
                                    operationId = "onPing",
                                    responses =
                                        mapOf(
                                            "200" to
                                                EndpointResponse(
                                                    statusCode = "200", description = "ok"))))),
            webhooksExtensions = mapOf("x-webhooks" to OpenApiString("hooks")),
            webhooksExplicitEmpty = true,
            components =
                Components(
                    schemas = mapOf("Extra" to SchemaDefinition(name = "Extra", type = "object")),
                    securitySchemes =
                        mapOf(
                            "ApiKey" to
                                SecurityScheme(
                                    type = "apiKey", name = "X-API-KEY", `in` = "header")),
                    examples = mapOf("Example" to ExampleObject(summary = "example")),
                    links = mapOf("Link" to Link(operationId = "getUser")),
                    callbacks = mapOf("Callback" to Callback.Inline()),
                    parameters =
                        mapOf(
                            "Limit" to
                                EndpointParameter(
                                    name = "limit",
                                    type = "integer",
                                    location = ParameterLocation.QUERY,
                                    isRequired = false)),
                    responses =
                        mapOf("Ok" to EndpointResponse(statusCode = "200", description = "ok")),
                    requestBodies = mapOf("Body" to RequestBody(description = "body")),
                    headers = mapOf("X-Header" to Header(type = "string")),
                    pathItems = mapOf("PathItem" to PathItem(summary = "component")),
                    mediaTypes =
                        mapOf(
                            "application/json" to
                                MediaTypeObject(schema = SchemaProperty("string"))),
                    extensions = mapOf("x-components" to OpenApiBoolean(true))))

    val metadata = definition.toMetadata(includeComponentSchemas = true)

    assertEquals("3.2.0", metadata.openapi)
    assertEquals("https://spec.openapis.org/oas/3.1/dialect/base", metadata.jsonSchemaDialect)
    assertEquals("https://example.com/openapi", metadata.self)
    assertEquals("Meta API", metadata.info?.title)
    assertEquals(1, metadata.servers.size)
    assertTrue(metadata.securityExplicitEmpty)
    assertEquals("alpha", metadata.tags.first().name)
    assertEquals("docs", metadata.externalDocs?.description)
    assertEquals(OpenApiString("root"), metadata.extensions["x-root"])
    assertEquals(OpenApiString("paths"), metadata.pathsExtensions["x-paths"])
    assertTrue(metadata.pathsExplicitEmpty)
    assertTrue(metadata.webhooks.containsKey("onPing"))
    assertEquals(OpenApiString("hooks"), metadata.webhooksExtensions["x-webhooks"])
    assertTrue(metadata.webhooksExplicitEmpty)

    assertTrue(metadata.securitySchemes.containsKey("ApiKey"))
    assertTrue(metadata.componentSchemas.containsKey("Extra"))
    assertTrue(metadata.componentExamples.containsKey("Example"))
    assertTrue(metadata.componentLinks.containsKey("Link"))
    assertTrue(metadata.componentCallbacks.containsKey("Callback"))
    assertTrue(metadata.componentParameters.containsKey("Limit"))
    assertTrue(metadata.componentResponses.containsKey("Ok"))
    assertTrue(metadata.componentRequestBodies.containsKey("Body"))
    assertTrue(metadata.componentHeaders.containsKey("X-Header"))
    assertTrue(metadata.componentPathItems.containsKey("PathItem"))
    assertTrue(metadata.componentMediaTypes.containsKey("application/json"))
    assertEquals(OpenApiBoolean(true), metadata.componentsExtensions["x-components"])

    val components = metadata.toComponents()
    assertNotNull(components)
    assertTrue(components!!.schemas.containsKey("Extra"))

    val minMeta =
        definition.toMetadata(
            includePathItems = false,
            includeComponents = false,
            includeWebhooks = false,
            includeComponentSchemas = false)
    assertTrue(minMeta.pathItems.isEmpty())
    assertTrue(minMeta.webhooks.isEmpty())
    assertTrue(minMeta.componentSchemas.isEmpty())
    assertTrue(minMeta.securitySchemes.isEmpty())

    val nullCompMeta =
        OpenApiDefinition(info = Info("NoComp", "1.0"), components = null)
            .toMetadata(includeComponents = true)
    assertTrue(nullCompMeta.securitySchemes.isEmpty())
  }

  @Test
  fun `toMetadata captures only path-level metadata`() {
    val pathItem =
        PathItem(
            summary = "Users",
            description = "User operations",
            parameters = listOf(EndpointParameter("id", "String", ParameterLocation.PATH)),
            servers = listOf(Server(url = "https://users.example.com")),
            extensions = mapOf("x-path" to OpenApiString("meta")),
            get =
                EndpointDefinition(
                    path = "/users/{id}", method = HttpMethod.GET, operationId = "getUser"))
    val noMetadata =
        PathItem(
            get =
                EndpointDefinition(
                    path = "/health", method = HttpMethod.GET, operationId = "health"))

    val definition =
        OpenApiDefinition(
            info = Info("Paths", "1.0"),
            paths = mapOf("/users/{id}" to pathItem, "/health" to noMetadata))

    val metadata = definition.toMetadata()
    val captured = metadata.pathItems["/users/{id}"]

    assertNotNull(captured)
    assertEquals("Users", captured?.summary)
    assertEquals("User operations", captured?.description)
    assertEquals(1, captured?.parameters?.size)
    assertEquals(1, captured?.servers?.size)
    assertEquals(OpenApiString("meta"), captured?.extensions?.get("x-path"))
    assertNull(captured?.get)
    assertTrue(captured?.additionalOperations?.isEmpty() == true)

    assertFalse(metadata.pathItems.containsKey("/health"))

    // Test individual component branches in toComponents()
    assertNull(OpenApiMetadata().toComponents())
    assertNotNull(
        OpenApiMetadata(componentExamples = mapOf("E" to ExampleObject(summary = "s")))
            .toComponents())
    assertNotNull(
        OpenApiMetadata(componentLinks = mapOf("L" to Link(operationId = "op"))).toComponents())
    assertNotNull(
        OpenApiMetadata(componentCallbacks = mapOf("C" to Callback.Inline())).toComponents())
    assertNotNull(
        OpenApiMetadata(
                componentParameters =
                    mapOf(
                        "P" to
                            EndpointParameter(
                                "p", "String", ParameterLocation.QUERY, isRequired = false)))
            .toComponents())
    assertNotNull(
        OpenApiMetadata(
                componentResponses = mapOf("R" to EndpointResponse("200", description = "ok")))
            .toComponents())
    assertNotNull(
        OpenApiMetadata(componentRequestBodies = mapOf("B" to RequestBody())).toComponents())
    assertNotNull(
        OpenApiMetadata(componentHeaders = mapOf("H" to Header(type = "string"))).toComponents())
    assertNotNull(OpenApiMetadata(componentPathItems = mapOf("PI" to PathItem())).toComponents())
    assertNotNull(
        OpenApiMetadata(componentMediaTypes = mapOf("MT" to MediaTypeObject())).toComponents())
    assertNotNull(
        OpenApiMetadata(componentsExtensions = mapOf("x-ext" to OpenApiBoolean(true)))
            .toComponents())
    assertNotNull(
        OpenApiMetadata(
                securitySchemes =
                    mapOf("S" to SecurityScheme(type = "apiKey", name = "k", `in` = "header")))
            .toComponents())
  }

  @Test
  fun `InfoMetadataCodec SecurityMetadataCodec ComponentsMetadataCodec and TagsMetadataCodec cover edge cases`() {
    val emptyHeader = psi.metadata.InfoMetadataCodec.extractOpenApiLine(listOf("@openapi"))
    assertNull(emptyHeader.openapi)
    val invalidHeader = psi.metadata.InfoMetadataCodec.extractOpenApiLine(listOf("@openapi [1]"))
    assertNull(invalidHeader.openapi)
    val brokenHeader = psi.metadata.InfoMetadataCodec.extractOpenApiLine(listOf("@openapi {bad"))
    assertNull(brokenHeader.openapi)

    val info =
        Info(
            title = "API",
            version = "1.0",
            summary = "Sum",
            description = "Desc",
            termsOfService = "https://tos.example.com",
            contact =
                Contact(
                    name = "Dev",
                    url = "https://dev.example.com",
                    email = "dev@example.com",
                    extensions = mapOf("x-contact" to OpenApiString("c"))),
            license =
                License(
                    name = "MIT",
                    identifier = "MIT",
                    url = "https://mit.example.com",
                    extensions = mapOf("x-lic" to OpenApiString("l"))),
            extensions = mapOf("x-info" to OpenApiString("i")))
    val renderedInfo = psi.metadata.InfoMetadataCodec.renderInfo(info)
    val parsedInfo = psi.metadata.InfoMetadataCodec.extractInfoLine(listOf("@info $renderedInfo"))
    assertEquals(info, parsedInfo)
    assertNull(psi.metadata.InfoMetadataCodec.extractInfoLine(listOf("@info")))
    assertNull(psi.metadata.InfoMetadataCodec.extractInfoLine(listOf("@info [1]")))
    assertNull(psi.metadata.InfoMetadataCodec.extractInfoLine(listOf("@info {bad")))
    assertNull(psi.metadata.InfoMetadataCodec.parseInfoNode(null))
    assertNull(psi.metadata.InfoMetadataCodec.extractInfoLine(listOf("@info {\"title\":\"only\"}")))
    assertNull(psi.metadata.InfoMetadataCodec.parseLicenseNode(null))

    val extDocs =
        ExternalDocumentation(
            description = "Docs",
            url = "https://docs.example.com",
            extensions = mapOf("x-d" to OpenApiInt(1L)))
    val renderedDocs = psi.metadata.InfoMetadataCodec.renderExternalDocs(extDocs)
    assertEquals(
        extDocs,
        psi.metadata.InfoMetadataCodec.extractExternalDocsLine(
            listOf("@externalDocs $renderedDocs")))
    assertNull(psi.metadata.InfoMetadataCodec.extractExternalDocsLine(listOf("@externalDocs")))
    assertNull(psi.metadata.InfoMetadataCodec.extractExternalDocsLine(listOf("@externalDocs [1]")))
    assertNull(psi.metadata.InfoMetadataCodec.extractExternalDocsLine(listOf("@externalDocs {bad")))
    assertEquals(
        ExternalDocumentation(url = "https://see.example.com", description = "See docs"),
        psi.metadata.InfoMetadataCodec.extractExternalDocsLine(
            listOf("@see https://see.example.com See docs")))
    assertNull(psi.metadata.InfoMetadataCodec.extractExternalDocsLine(emptyList()))

    val server =
        Server(
            url = "https://{env}.example.com",
            description = "Srv",
            name = "prod",
            variables =
                mapOf(
                    "env" to
                        ServerVariable(
                            default = "api",
                            enum = listOf("api", "staging"),
                            description = "Env",
                            extensions = mapOf("x-var" to OpenApiString("v")))),
            extensions = mapOf("x-srv" to OpenApiString("s")))
    val serverDoc = psi.metadata.InfoMetadataCodec.serverToDocValue(server)
    val serverJson = OpenApiValues.encodeAnyToString(listOf(serverDoc))
    assertEquals(
        listOf(server),
        psi.metadata.InfoMetadataCodec.extractServersLine(listOf("@servers $serverJson")))
    val singleServerJson = OpenApiValues.encodeAnyToString(serverDoc)
    assertEquals(
        listOf(server),
        psi.metadata.InfoMetadataCodec.extractServersLine(listOf("@servers $singleServerJson")))
    assertTrue(psi.metadata.InfoMetadataCodec.extractServersLine(listOf("@servers")).isEmpty())
    assertTrue(psi.metadata.InfoMetadataCodec.extractServersLine(listOf("@servers 123")).isEmpty())
    assertTrue(psi.metadata.InfoMetadataCodec.extractServersLine(listOf("@servers {bad")).isEmpty())

    assertEquals("{}", psi.metadata.SecurityMetadataCodec.renderSecuritySchemes(emptyMap()))
    assertTrue(
        psi.metadata.SecurityMetadataCodec.extractSecurityLines(listOf("@securityEmpty true"))
            .explicitEmpty)
    val secRes =
        psi.metadata.SecurityMetadataCodec.extractSecurityLines(
            listOf(
                "@security   ",
                "@security {bad",
                "@security [1]",
                "@security {\"oauth\":[\"read\"],\"apiKey\":\"none\"}"))
    assertEquals(1, secRes.requirements.size)
    assertEquals(listOf("read"), secRes.requirements.first()["oauth"])
    assertEquals(emptyList<String>(), secRes.requirements.first()["apiKey"])
    assertTrue(
        psi.metadata.SecurityMetadataCodec.extractSecuritySchemesLine(listOf("@securitySchemes"))
            .isEmpty())
    assertNull(psi.metadata.ComponentsMetadataCodec.toComponents(OpenApiMetadata()))

    assertEquals("{}", psi.metadata.ComponentsMetadataCodec.renderPathItems(emptyMap()))
    assertEquals("{}", psi.metadata.ComponentsMetadataCodec.renderWebhooks(emptyMap()))
    assertNull(
        psi.metadata.ComponentsMetadataCodec.parseWrappedComponentSection(
            listOf("@sec"), "@sec", "T", "k"))
    assertNull(
        psi.metadata.ComponentsMetadataCodec.parseWrappedComponentSection(
            listOf("@sec {bad"), "@sec", "T", "k"))
    assertNull(
        psi.metadata.ComponentsMetadataCodec.parseWrappedComponentSection(
            listOf("@sec [1]"), "@sec", "T", "k"))
    assertTrue(
        psi.metadata.ComponentsMetadataCodec.extractPathItemsLine(listOf("@pathItems")).isEmpty())
    assertTrue(
        psi.metadata.ComponentsMetadataCodec.extractPathItemsLine(listOf("@pathItems {bad"))
            .isEmpty())
    assertTrue(
        psi.metadata.ComponentsMetadataCodec.extractPathItemsLine(listOf("@pathItems [1]"))
            .isEmpty())
    assertTrue(
        psi.metadata.ComponentsMetadataCodec.extractWebhooksLine(listOf("@webhooks")).isEmpty())
    assertTrue(
        psi.metadata.ComponentsMetadataCodec.extractWebhooksLine(listOf("@webhooks {bad"))
            .isEmpty())
    assertTrue(
        psi.metadata.ComponentsMetadataCodec.extractWebhooksLine(listOf("@webhooks [1]")).isEmpty())

    val tag =
        Tag(
            name = "users",
            summary = "Users",
            description = "User ops",
            externalDocs = extDocs,
            parent = "root",
            kind = "nav",
            extensions = mapOf("x-tag" to OpenApiString("t")))
    val tagsJson = psi.metadata.TagsMetadataCodec.renderTags(listOf(tag))
    assertEquals(
        listOf(tag), psi.metadata.TagsMetadataCodec.extractTagObjects(listOf("@tags $tagsJson")))
    assertTrue(psi.metadata.TagsMetadataCodec.extractTagObjects(listOf("@tags")).isEmpty())
    assertTrue(psi.metadata.TagsMetadataCodec.extractTagObjects(listOf("@tags {bad")).isEmpty())
    assertTrue(psi.metadata.TagsMetadataCodec.extractTagObjects(listOf("@tags {}")).isEmpty())
    assertTrue(psi.metadata.TagsMetadataCodec.extractTagObjects(listOf("@tags [{}]")).isEmpty())
    assertNull(psi.metadata.TagsMetadataCodec.parseTagNode(null))

    val minTag = Tag(name = "min", extensions = mapOf("non-x" to OpenApiString("ignore")))
    assertFalse(psi.metadata.TagsMetadataCodec.tagToDocValue(minTag).containsKey("non-x"))

    // InfoMetadataCodec minimal & non-x extensions & edge cases
    val minInfo =
        Info(
            title = "Min",
            version = "1",
            contact = Contact(extensions = mapOf("non-x" to OpenApiString("ignore"))),
            license = License(name = "MIT", extensions = mapOf("non-x" to OpenApiString("ignore"))),
            extensions = mapOf("non-x" to OpenApiString("ignore")))
    val minInfoDoc = psi.metadata.InfoMetadataCodec.infoToDocValue(minInfo)
    assertFalse(minInfoDoc.containsKey("non-x"))
    assertNull(psi.metadata.InfoMetadataCodec.extractInfoLine(listOf("@info {\"version\":\"1\"}")))
    assertNull(
        psi.metadata.InfoMetadataCodec.parseLicenseNode(
            kotlinx.serialization.json.Json.parseToJsonElement("{}")))

    val minExtDocs =
        ExternalDocumentation(
            url = "https://u.example.com", extensions = mapOf("non-x" to OpenApiString("ignore")))
    assertFalse(
        psi.metadata.InfoMetadataCodec.externalDocsToDocValue(minExtDocs).containsKey("non-x"))
    assertNull(psi.metadata.InfoMetadataCodec.extractExternalDocsLine(listOf("@see   ")))
    assertNull(
        psi.metadata.InfoMetadataCodec.parseExternalDocsNode(
            kotlinx.serialization.json.Json.parseToJsonElement("{}")))

    val minServer =
        Server(
            url = "https://s.example.com",
            variables = emptyMap(),
            extensions = mapOf("non-x" to OpenApiString("ignore")))
    assertFalse(psi.metadata.InfoMetadataCodec.serverToDocValue(minServer).containsKey("variables"))
    val srvMinVar =
        Server(
            url = "https://s.example.com",
            variables =
                mapOf(
                    "v1" to
                        ServerVariable(
                            default = "d",
                            enum = emptyList(),
                            description = null,
                            extensions = mapOf("non-x" to OpenApiString("ignore")))))
    assertTrue(psi.metadata.InfoMetadataCodec.serverToDocValue(srvMinVar).containsKey("variables"))
    assertNull(
        psi.metadata.InfoMetadataCodec.parseServerNode(
            kotlinx.serialization.json.Json.parseToJsonElement("123")))
    val srvDefaultUrl =
        psi.metadata.InfoMetadataCodec.parseServerNode(
            kotlinx.serialization.json.Json.parseToJsonElement(
                "{\"variables\":{\"v\":123,\"v2\":{\"enum\":[null,\"a\"]},\"v3\":{\"enum\":123}}}"))
    assertEquals("/", srvDefaultUrl?.url)
    assertEquals(listOf("a"), srvDefaultUrl?.variables?.get("v2")?.enum)
    assertNull(srvDefaultUrl?.variables?.get("v3")?.enum)

    assertEquals("{}", psi.metadata.SecurityMetadataCodec.renderSecuritySchemes(emptyMap()))
    assertFalse(
        psi.metadata.SecurityMetadataCodec.extractSecurityLines(
                listOf("@securityEmpty", "@security {\"a\":[]}"))
            .explicitEmpty)
    assertEquals("{}", psi.metadata.ComponentsMetadataCodec.renderComponentSchemas(emptyMap()))
    assertEquals("{}", psi.metadata.ComponentsMetadataCodec.renderComponentParameters(emptyMap()))
    assertEquals("{}", psi.metadata.ComponentsMetadataCodec.renderComponentResponses(emptyMap()))
    assertEquals(
        "{}", psi.metadata.ComponentsMetadataCodec.renderComponentRequestBodies(emptyMap()))
    assertEquals("{}", psi.metadata.ComponentsMetadataCodec.renderComponentHeaders(emptyMap()))
    assertEquals("{}", psi.metadata.ComponentsMetadataCodec.renderComponentPathItems(emptyMap()))
    assertEquals("{}", psi.metadata.ComponentsMetadataCodec.renderComponentMediaTypes(emptyMap()))

    val baseComp = Components(schemas = mapOf("Base" to SchemaDefinition("Base", "object")))
    val mergedFromEmptyMeta =
        psi.metadata.ComponentsMetadataCodec.toComponents(
            OpenApiMetadata(componentsExtensions = mapOf("non-x" to OpenApiString("ignore"))),
            baseComp)
    assertNotNull(mergedFromEmptyMeta)
    assertTrue(mergedFromEmptyMeta!!.extensions.isEmpty())

    // PathItem metadata individual branches (ref, description, parameters, servers, extensions)
    assertEquals(
        1,
        psi.metadata.ComponentsMetadataCodec.extractPathItemMetadata(
                mapOf("/r" to PathItem(ref = "#/components/pathItems/P")))
            .size)
    assertEquals(
        1,
        psi.metadata.ComponentsMetadataCodec.extractPathItemMetadata(
                mapOf("/d" to PathItem(description = "d")))
            .size)
    assertEquals(
        1,
        psi.metadata.ComponentsMetadataCodec.extractPathItemMetadata(
                mapOf(
                    "/p" to
                        PathItem(
                            parameters =
                                listOf(EndpointParameter("id", "String", ParameterLocation.PATH)))))
            .size)
    assertEquals(
        1,
        psi.metadata.ComponentsMetadataCodec.extractPathItemMetadata(
                mapOf("/s" to PathItem(servers = listOf(Server("https://s.example.com")))))
            .size)
    assertEquals(
        1,
        psi.metadata.ComponentsMetadataCodec.extractPathItemMetadata(
                mapOf("/e" to PathItem(extensions = mapOf("x-e" to OpenApiInt(1L)))))
            .size)
  }
}
