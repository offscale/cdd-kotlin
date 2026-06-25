package openapi

import domain.*
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
            parameters = mapOf("a" to "b"),
            requestBody = "{\$url}",
            server = Server("http://a.b"))

    val invalidLink =
        Link(
            requestBody =
                "{invalid} {\$request.body#invalid~x} {\$request.query.} {\$request.header.} { } {}",
            parameters = mapOf("a" to "{\$request.header.b}"))

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

    validator.validate(megaDef)

    // Invalid openapi versions / dialects
    validator.validate(OpenApiDefinition(openapi = "2.0", info = info))
    validator.validate(OpenApiDefinition(openapi = "3.1.0", info = info))
    validator.validate(
        OpenApiDefinition(
            openapi = "3.1.0",
            info = info,
            paths = emptyMap(),
            jsonSchemaDialect = "unknown",
            components = Components()))

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
    validator.validate(extraDef)
  }
}
