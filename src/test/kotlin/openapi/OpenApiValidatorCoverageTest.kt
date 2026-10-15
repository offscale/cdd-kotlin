package openapi

import domain.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiValidatorCoverageTest {
  @Test
  fun testUncoveredLines() {
    val validator = OpenApiValidator()
    val info = Info("Title", "1.0.0")

    val linkWithOverrides =
        Link(
            ref = "#/components/links/SomeLink",
            operationId = "opId",
            operationRef = "#/components/links/Other",
            parameters = mapOf("a" to OpenApiString("b")),
            requestBody = OpenApiString("{\$url}"),
            server = Server("http://a.b"))

    val invalidLink =
        Link(
            requestBody =
                OpenApiString(
                    "{invalid} {\$request.body#invalid~x} {\$request.query.} {\$request.header.} { } {}"),
            parameters = mapOf("a" to OpenApiString("{\$request.header.b}")))

    val megaDef =
        OpenApiDefinition(
            openapi = "3.1.0",
            info = info,
            paths =
                mapOf(
                    "/test/{id}" to
                        PathItem(
                            parameters =
                                listOf(
                                    EndpointParameter(
                                        name = "wrong",
                                        location = ParameterLocation.PATH,
                                        type = "string")),
                            post =
                                EndpointDefinition(
                                    path = "/test/{id}",
                                    method = HttpMethod.POST,
                                    operationId = "op1",
                                    responses =
                                        mapOf(
                                            "200" to
                                                EndpointResponse(
                                                    statusCode = "200",
                                                    links =
                                                        mapOf(
                                                            "L1" to linkWithOverrides,
                                                            "L2" to invalidLink)))),
                            get =
                                EndpointDefinition(
                                    path = "/test/{id}",
                                    method = HttpMethod.GET,
                                    operationId = "op2",
                                    responses = mapOf("200" to EndpointResponse("200")),
                                    callbacks =
                                        mapOf(
                                            "C1" to
                                                Callback.Reference(
                                                    ReferenceObject(
                                                        "#/components/callbacks/MissingCb")),
                                            "C2" to
                                                Callback.Reference(
                                                    ReferenceObject(
                                                        "#/components/callbacks/CbRef")))))),
            webhooks =
                mapOf(
                    "hook" to
                        PathItem(
                            parameters =
                                listOf(
                                    EndpointParameter(
                                        name = "p",
                                        location = ParameterLocation.QUERY,
                                        type = "string")))),
            components =
                Components(
                    schemas =
                        mapOf(
                            "A B" to SchemaDefinition(name = "A B", type = "string"),
                            "BadRef" to
                                SchemaDefinition(
                                    name = "BadRef", type = "string", ref = "invalid%ZZ"),
                            "B" to
                                SchemaDefinition(
                                    name = "B",
                                    type = "string",
                                    ref = "#/components/schemas/A%20B")),
                    responses =
                        mapOf(
                            "R" to
                                EndpointResponse(
                                    statusCode = "200",
                                    reference = ReferenceObject("#/components/responses/Missing"))),
                    parameters =
                        mapOf(
                            "P" to
                                EndpointParameter(
                                    name = "p",
                                    location = ParameterLocation.QUERY,
                                    type = "string",
                                    reference =
                                        ReferenceObject("#/components/parameters/Missing"))),
                    requestBodies =
                        mapOf(
                            "B" to
                                RequestBody(
                                    reference =
                                        ReferenceObject("#/components/requestBodies/Missing"))),
                    headers =
                        mapOf(
                            "H" to
                                Header(
                                    type = "string",
                                    reference = ReferenceObject("#/components/headers/Missing"))),
                    securitySchemes =
                        mapOf(
                            "S1" to
                                SecurityScheme(
                                    reference =
                                        ReferenceObject("#/components/securitySchemes/Missing")),
                            "S2" to SecurityScheme(type = "apiKey", name = "k", `in` = "header")),
                    examples = mapOf("E" to ExampleObject(ref = "#/components/examples/Missing")),
                    links = mapOf("L" to Link(ref = "#/components/links/Missing")),
                    callbacks =
                        mapOf(
                            "C" to
                                Callback.Reference(
                                    reference = ReferenceObject("#/components/callbacks/Missing")),
                            "CbRef" to
                                Callback.Inline(
                                    expressions = mapOf("{\$request.query.cb}" to PathItem()))),
                    pathItems = mapOf("PI" to PathItem(ref = "#/components/pathItems/Missing")),
                    mediaTypes =
                        mapOf(
                            "application/json" to
                                MediaTypeObject(
                                    schema =
                                        SchemaProperty(
                                            type = "string",
                                            ref = ("#/components/schemas/Missing"))))),
            security = listOf(mapOf("S2" to emptyList(), "MissingS" to emptyList())))

    val megaIssues = validator.validate(megaDef)
    assertTrue(megaIssues.isNotEmpty())
    assertTrue(
        megaIssues.any {
          it.severity == OpenApiIssueSeverity.WARNING &&
              it.path.contains("links") &&
              it.message.contains("should not define other fields")
        })
    assertTrue(
        megaIssues.any {
          it.severity == OpenApiIssueSeverity.ERROR &&
              it.path.contains("links") &&
              it.message.contains("either operationId or operationRef")
        })
    assertTrue(
        megaIssues.any {
          it.severity == OpenApiIssueSeverity.ERROR &&
              it.path.contains("BadRef") &&
              it.message.contains("valid URI")
        })
    assertTrue(
        megaIssues.any {
          it.severity == OpenApiIssueSeverity.ERROR &&
              it.path.contains("security") &&
              it.message.contains("MissingS")
        })

    // Invalid openapi versions / dialects
    val oas2Issues = validator.validate(OpenApiDefinition(openapi = "2.0", info = info))
    assertTrue(oas2Issues.isEmpty())
    val emptyRootIssues = validator.validate(OpenApiDefinition(openapi = "3.1.0", info = info))
    assertTrue(
        emptyRootIssues.any {
          it.severity == OpenApiIssueSeverity.ERROR &&
              it.path == "$.openapi" &&
              it.message.contains("at least one of: paths, webhooks, or components")
        })
    val dialectIssues =
        validator.validate(
            OpenApiDefinition(
                openapi = "3.1.0",
                info = info,
                paths = emptyMap(),
                jsonSchemaDialect = "unknown",
                components = Components()))
    assertTrue(
        dialectIssues.any {
          it.path == "$.jsonSchemaDialect" &&
              (it.severity == OpenApiIssueSeverity.WARNING ||
                  it.severity == OpenApiIssueSeverity.ERROR)
        })

    // Extra coverage for specific branches
    val extraDef =
        OpenApiDefinition(
            openapi = "3.1.0",
            info = info,
            paths =
                mapOf(
                    "/extra" to
                        PathItem(
                            parameters =
                                listOf(
                                    EndpointParameter(
                                        name = "q",
                                        location = ParameterLocation.QUERY,
                                        type = "string")),
                            get =
                                EndpointDefinition(
                                    operationId = "extraGet",
                                    method = HttpMethod.GET,
                                    path = "/extra",
                                    parameters =
                                        listOf(
                                            EndpointParameter(
                                                name = "q",
                                                location = ParameterLocation.QUERY,
                                                type = "string")),
                                    responses =
                                        mapOf(
                                            "200" to
                                                EndpointResponse(
                                                    statusCode = "200",
                                                    headers =
                                                        mapOf(
                                                            "H1" to Header(type = "string"),
                                                            "H2" to
                                                                Header(
                                                                    type = "string",
                                                                    reference =
                                                                        ReferenceObject(
                                                                            "#/components/headers/Missing"))),
                                                    content =
                                                        mapOf(
                                                            "application/json" to
                                                                MediaTypeObject(
                                                                    schema =
                                                                        SchemaProperty(
                                                                            type = "string"),
                                                                    encoding =
                                                                        mapOf(
                                                                            "prop" to
                                                                                EncodingObject(
                                                                                    contentType =
                                                                                        "image/png",
                                                                                    headers =
                                                                                        mapOf(
                                                                                            "EH" to
                                                                                                Header(
                                                                                                    type =
                                                                                                        "string")),
                                                                                    style =
                                                                                        ParameterStyle
                                                                                            .SIMPLE,
                                                                                    explode = true,
                                                                                    allowReserved =
                                                                                        false))))))))),
            components =
                Components(
                    securitySchemes =
                        mapOf(
                            "oauth2" to
                                SecurityScheme(
                                    type = "oauth2",
                                    flows =
                                        OAuthFlows(
                                            implicit =
                                                OAuthFlow(
                                                    authorizationUrl = "https://a.b",
                                                    scopes = mapOf("s" to "desc")),
                                            password =
                                                OAuthFlow(
                                                    tokenUrl = "https://a.b",
                                                    scopes = mapOf("s" to "desc")),
                                            clientCredentials =
                                                OAuthFlow(
                                                    tokenUrl = "https://a.b",
                                                    scopes = mapOf("s" to "desc")),
                                            authorizationCode =
                                                OAuthFlow(
                                                    authorizationUrl = "https://a.b",
                                                    tokenUrl = "https://a.b",
                                                    scopes = mapOf("s" to "desc")))),
                            "openId" to
                                SecurityScheme(
                                    type = "openIdConnect", openIdConnectUrl = "https://a.b"),
                            "http" to
                                SecurityScheme(
                                    type = "http", scheme = "bearer", bearerFormat = "JWT")),
                    parameters =
                        mapOf(
                            "P2" to
                                EndpointParameter(
                                    name = "p",
                                    location = ParameterLocation.QUERY,
                                    type = "string")),
                    headers = mapOf("H3" to Header(type = "string"))))
    val extraIssues = validator.validate(extraDef)
    assertTrue(
        extraIssues.any {
          it.severity == OpenApiIssueSeverity.ERROR &&
              it.path.contains("headers") &&
              it.message.contains("Missing")
        })
    assertEquals(true, extraIssues.all { it.path.startsWith("$") })

    val minimalContactLicenseDef =
        OpenApiDefinition(
            info =
                Info(
                    title = "T",
                    version = "1.0",
                    contact = Contact(name = "OnlyName"),
                    license = License(name = "MIT")),
            servers = listOf(Server(url = "https://example.com", name = "uniqueProd")),
            paths =
                mapOf(
                    "/items/{id}" to
                        PathItem(
                            parameters =
                                listOf(
                                    EndpointParameter(
                                        name = "q",
                                        type = "String",
                                        location = ParameterLocation.QUERY,
                                        schema = SchemaProperty(types = setOf("string"))),
                                    EndpointParameter(
                                        name = "id",
                                        type = "String",
                                        location = ParameterLocation.PATH,
                                        isRequired = true,
                                        schema = SchemaProperty(types = setOf("string")))),
                            get =
                                EndpointDefinition(
                                    path = "/items/{id}",
                                    method = HttpMethod.GET,
                                    operationId = "getItem",
                                    responses =
                                        mapOf(
                                            "200" to
                                                EndpointResponse(
                                                    statusCode = "200", description = "ok"))))))
    assertTrue(
        validator.validate(minimalContactLicenseDef).none {
          it.severity == OpenApiIssueSeverity.ERROR
        })

    assertEquals(
        "https://abs.com/a.json",
        openapi.validator.ValidationCommon.resolveSelfBase(
            "https://abs.com/a.json", "https://base.com/b.json"))
    assertEquals(
        null,
        openapi.validator.ReferenceAndLinkValidator.extractComponentRefInfo(
            "#/components/schemas/", "schemas", null))
    assertEquals(
        "https://abs.com/spec.json",
        openapi.validator.ReferenceAndLinkValidator.extractComponentRefInfo(
                "https://abs.com/spec.json#/components/schemas/Pet",
                "schemas",
                "https://base.com/spec.json")
            ?.base)
    assertEquals(
        null,
        openapi.validator.ReferenceAndLinkValidator.resolveCallbackRef(
            "#/components/callbacks/Cb", null))
    assertEquals(
        null,
        openapi.validator.ReferenceAndLinkValidator.resolveCallbackRef(
            "#/components/callbacks/", Components()))
    assertEquals(
        null,
        openapi.validator.ReferenceAndLinkValidator.resolveCallbackRef(
            "#/components/callbacks/a/b", Components()))

    val remainingBranchDef =
        OpenApiDefinition(
            self = null,
            info = Info(title = "T", version = "1.0"),
            components =
                Components(
                    schemas =
                        mapOf(
                            "DialectDef" to
                                SchemaDefinition(
                                    name = "DialectDef",
                                    schemaDialect = "https://json-schema.org/draft/2020-12/schema",
                                    examples = mapOf("e" to OpenApiString("v")),
                                    extensions = mapOf("x-ext" to OpenApiString("v")),
                                    customKeywords = emptyMap())),
                    mediaTypes =
                        mapOf(
                            "RefMedia" to
                                MediaTypeObject(
                                    reference = ReferenceObject("#/components/mediaTypes/Other")),
                            "ItemEncMedia" to
                                MediaTypeObject(
                                    schema =
                                        SchemaProperty(
                                            types = setOf("array"),
                                            minContains = 1,
                                            contains = SchemaProperty(types = setOf("string")),
                                            ref = "#/\$defs/",
                                            xml = Xml(nodeType = "text", name = null)),
                                    itemEncoding =
                                        EncodingObject(
                                            contentType = "text/plain",
                                            encoding = mapOf("sub" to EncodingObject()))),
                            "BoolSchemaEncMedia" to
                                MediaTypeObject(
                                    schema = SchemaProperty(booleanSchema = true),
                                    encoding = mapOf("a" to EncodingObject()))),
                    links =
                        mapOf(
                            "RefLinkObj" to
                                Link(reference = ReferenceObject("#/components/links/Other")),
                            "ExtOpRefLink" to
                                Link(
                                    operationRef = "https://self.com/spec.json#/paths/~1a/get",
                                    parameters = mapOf("p" to OpenApiInt(1)))),
                    securitySchemes =
                        mapOf(
                            "OAuthNoCode" to
                                SecurityScheme(
                                    type = "oauth2",
                                    flows =
                                        OAuthFlows(
                                            authorizationCode = null,
                                            deviceAuthorization =
                                                OAuthFlow(
                                                    deviceAuthorizationUrl = "https://a.b",
                                                    tokenUrl = "https://a.b"))))),
            paths =
                mapOf(
                    "/a" to
                        PathItem(
                            get =
                                EndpointDefinition(
                                    path = "/a",
                                    method = HttpMethod.GET,
                                    operationId = "getA",
                                    responses =
                                        mapOf(
                                            "200" to
                                                EndpointResponse(
                                                    statusCode = "200",
                                                    description = "ok",
                                                    links =
                                                        mapOf(
                                                            "RefLinkObj" to
                                                                Link(
                                                                    reference =
                                                                        ReferenceObject(
                                                                            "#/components/links/Other")),
                                                            "BodyLink" to
                                                                Link(
                                                                    operationId = "getA",
                                                                    parameters =
                                                                        mapOf(
                                                                            "p" to OpenApiInt(1),
                                                                            "b" to
                                                                                OpenApiString(
                                                                                    "\$request.body")))),
                                                    content =
                                                        mapOf(
                                                            "application/json" to
                                                                MediaTypeObject(
                                                                    itemEncoding =
                                                                        EncodingObject(
                                                                            contentType =
                                                                                "text/plain")))))))))
    assertTrue(validator.validate(remainingBranchDef).isNotEmpty())
    assertTrue(
        validator
            .validate(
                remainingBranchDef.copy(self = "https://other.com/spec.json"),
                baseUri = "https://other.com/spec.json")
            .isNotEmpty())
  }
}
