package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DomainModelsCoverageTest {

    @Test
    fun `instantiate core domain models`() {
        val contact = Contact(name = "Support", url = "https://example.com", email = "support@example.com")
        val license = License(name = "Apache-2.0", identifier = "Apache-2.0")

        val serverVar = ServerVariable(
            default = "v1",
            enum = listOf("v1", "v2"),
            description = "API version"
        )
        val server = Server(
            url = "https://api.example.com/{version}",
            description = "Primary API",
            name = "primary",
            variables = mapOf("version" to serverVar)
        )

        val flow = OAuthFlow(
            authorizationUrl = "https://auth.example.com/authorize",
            deviceAuthorizationUrl = "https://auth.example.com/device",
            tokenUrl = "https://auth.example.com/token",
            refreshUrl = "https://auth.example.com/refresh",
            scopes = mapOf("read" to "Read access")
        )
        val flows = OAuthFlows(
            implicit = flow,
            password = flow,
            clientCredentials = flow,
            authorizationCode = flow,
            deviceAuthorization = flow
        )

        val scheme = SecurityScheme(
            type = "http",
            description = "Bearer auth",
            name = "Authorization",
            `in` = "header",
            scheme = "bearer",
            bearerFormat = "JWT",
            flows = flows,
            openIdConnectUrl = "https://id.example.com/.well-known/openid-configuration",
            oauth2MetadataUrl = "https://auth.example.com/.well-known/oauth-authorization-server",
            deprecated = true
        )

        val tag = Tag(
            name = "users",
            summary = "User operations",
            description = "Operations about users",
            externalDocs = ExternalDocumentation("Docs", "https://docs.example.com/users"),
            parent = "root",
            kind = "nav"
        )

        val example = ExampleObject(
            summary = "Example payload",
            dataValue = mapOf("id" to 1)
        )
        val encoding = EncodingObject(contentType = "application/json")
        val mediaType = MediaTypeObject(
            schema = SchemaProperty("string"),
            examples = mapOf("sample" to example),
            encoding = mapOf("field" to encoding)
        )
        val requestBody = RequestBody(
            description = "Create payload",
            content = mapOf("application/json" to mediaType),
            required = true
        )

        val info = Info(
            title = "Test API",
            version = "1.0",
            summary = "Summary",
            description = "Description",
            termsOfService = "https://example.com/tos",
            contact = contact,
            license = license
        )

        val components = Components(
            schemas = mapOf(
                "User" to SchemaDefinition(
                    name = "User",
                    type = "object",
                    schemaId = "https://example.com/schemas/User",
                    schemaDialect = "https://json-schema.org/draft/2020-12/schema",
                    anchor = "user",
                    dynamicAnchor = "userDyn",
                    dynamicRef = "#/components/schemas/BaseUser",
                    comment = "User schema comment",
                    defs = mapOf(
                        "PositiveInt" to SchemaProperty(types = setOf("integer"), minimum = 1.0)
                    ),
                    title = "User schema",
                    defaultValue = "{\"id\":1}",
                    constValue = "{\"id\":1}",
                    deprecated = true,
                    readOnly = true,
                    writeOnly = false,
                    prefixItems = listOf(SchemaProperty("string")),
                    contains = SchemaProperty("string"),
                    minContains = 1,
                    maxContains = 1,
                    not = SchemaProperty(types = setOf("null"), comment = "no nulls"),
                    ifSchema = SchemaProperty(
                        types = setOf("object"),
                        properties = mapOf("country" to SchemaProperty("string", constValue = "US"))
                    ),
                    thenSchema = SchemaProperty(
                        types = setOf("object"),
                        required = listOf("state"),
                        properties = mapOf("state" to SchemaProperty("string"))
                    ),
                    elseSchema = SchemaProperty(
                        types = setOf("object"),
                        required = listOf("province"),
                        properties = mapOf("province" to SchemaProperty("string"))
                    )
                )
            ),
            responses = mapOf(
                "Ok" to EndpointResponse(
                    statusCode = "200",
                    description = "OK",
                    type = "User",
                    headers = mapOf(
                        "X-Rate-Limit" to Header(
                            type = "integer",
                            schema = SchemaProperty("integer"),
                            example = example,
                            examples = mapOf("sample" to example)
                        )
                    ),
                    content = mapOf("application/json" to mediaType)
                )
            ),
            parameters = mapOf(
                "limit" to EndpointParameter(
                    "limit",
                    "integer",
                    ParameterLocation.QUERY,
                    schema = SchemaProperty("integer"),
                    deprecated = true,
                    allowEmptyValue = true
                )
            ),
            requestBodies = mapOf("CreateUser" to requestBody),
            securitySchemes = mapOf("bearerAuth" to scheme),
            headers = mapOf(
                "X-Rate-Limit" to Header(
                    type = "integer",
                    schema = SchemaProperty("integer"),
                    example = example,
                    examples = mapOf("sample" to example)
                )
            ),
            examples = mapOf("ExampleUser" to example),
            links = mapOf("UserLink" to Link(operationId = "getUser")),
            callbacks = mapOf(
                "OnEvent" to Callback.Inline(
                    expressions = mapOf("{\$request.body#/url}" to PathItem())
                )
            ),
            pathItems = mapOf("Users" to PathItem(summary = "Users path")),
            mediaTypes = mapOf("application/json" to mediaType)
        )

        val root = OpenApiDefinition(
            info = info,
            self = "https://example.com/api/openapi",
            servers = listOf(server),
            components = components,
            tags = listOf(tag),
            externalDocs = ExternalDocumentation("Root docs", "https://docs.example.com")
        )

        assertEquals("3.2.0", root.openapi)
        assertEquals("https://example.com/api/openapi", root.self)
        assertEquals("Test API", root.info.title)
        assertEquals("https://api.example.com/{version}", root.servers.first().url)
        assertEquals("primary", root.servers.first().name)
        assertEquals("https://auth.example.com/.well-known/oauth-authorization-server", scheme.oauth2MetadataUrl)
        assertEquals(true, scheme.deprecated)
        assertEquals("https://auth.example.com/device", flow.deviceAuthorizationUrl)
        assertEquals("User schema", components.schemas["User"]?.title)
        assertEquals("https://example.com/schemas/User", components.schemas["User"]?.schemaId)
        assertEquals("https://json-schema.org/draft/2020-12/schema", components.schemas["User"]?.schemaDialect)
        assertEquals("user", components.schemas["User"]?.anchor)
        assertEquals("userDyn", components.schemas["User"]?.dynamicAnchor)
        assertEquals("#/components/schemas/BaseUser", components.schemas["User"]?.dynamicRef)
        assertEquals("User schema comment", components.schemas["User"]?.comment)
        assertEquals("integer", components.schemas["User"]?.defs?.get("PositiveInt")?.type)
        assertEquals("{\"id\":1}", components.schemas["User"]?.defaultValue)
        assertEquals("{\"id\":1}", components.schemas["User"]?.constValue)
        assertEquals(true, components.schemas["User"]?.deprecated)
        assertEquals(true, components.parameters["limit"]?.deprecated)
        assertEquals("state", components.schemas["User"]?.thenSchema?.required?.first())
        assertEquals("province", components.schemas["User"]?.elseSchema?.required?.first())
    }

    @Test
    fun `responseType returns null when no success responses`() {
        val endpoint = EndpointDefinition(
            path = "/error",
            method = HttpMethod.GET,
            operationId = "getError",
            responses = mapOf("400" to EndpointResponse(statusCode = "400", description = "Bad Request", type = "Error"))
        )

        assertEquals(null, endpoint.responseType)
    }

    @Test
    fun `schema property type falls back to string`() {
        val prop = SchemaProperty(types = emptySet())
        assertEquals("string", prop.type)
    }

    @Test
    fun `enum values are accessible`() {
        assertTrue(HttpMethod.values().contains(HttpMethod.QUERY))
        assertTrue(HttpMethod.values().contains(HttpMethod.CUSTOM))
        assertTrue(ParameterLocation.values().contains(ParameterLocation.COOKIE))
        assertTrue(ParameterLocation.values().contains(ParameterLocation.QUERYSTRING))
        assertTrue(ParameterStyle.values().contains(ParameterStyle.DEEP_OBJECT))
        assertTrue(ParameterStyle.values().contains(ParameterStyle.COOKIE))
    }

    @Test
    fun `schema definition supports explicit type sets`() {
        val schema = SchemaDefinition(
            name = "MaybeTags",
            type = "array",
            types = setOf("array", "null"),
            items = SchemaProperty("string")
        )

        assertTrue(schema.effectiveTypes.contains("array"))
        assertTrue(schema.effectiveTypes.contains("null"))
        assertEquals("array", schema.primaryType)
    }
}
