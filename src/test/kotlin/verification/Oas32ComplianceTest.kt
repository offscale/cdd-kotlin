package verification

import domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class Oas32ComplianceTest {

    @Test
    fun `Domain Supports Root Configuration (jsonSchemaDialect)`() {
        val root = OpenApiDefinition(
            info = Info("Test", "1.0"),
            jsonSchemaDialect = "https://spec.openapis.org/oas/3.1/dialect/base"
        )
        // Verify default version is 3.2.0
        assertEquals("3.2.0", root.openapi)
        assertEquals("https://spec.openapis.org/oas/3.1/dialect/base", root.jsonSchemaDialect)
    }

    @Test
    fun `Domain Supports Webhooks`() {
        val root = OpenApiDefinition(
            info = Info("Webhooks API", "1.0"),
            webhooks = mapOf(
                // Webhooks are modeled as PathItem objects (which contain operations)
                "newPet" to PathItem(
                    post = EndpointDefinition(
                        path = "/", // Relative to the webhook URL (runtime expression)
                        method = HttpMethod.POST,
                        operationId = "onNewPet",
                        requestBodyType = "Pet"
                    )
                )
            )
        )
        assertTrue(root.webhooks.containsKey("newPet"))
        val pathItem = root.webhooks["newPet"]
        assertNotNull(pathItem)
        assertNotNull(pathItem?.post)
        assertEquals(HttpMethod.POST, pathItem?.post?.method)
        assertEquals("onNewPet", pathItem?.post?.operationId)
    }

    @Test
    fun `Domain Supports Callbacks`() {
        // Construct an endpoint definition with a callback
        val registerOp = EndpointDefinition(
            path = "/subscribe",
            method = HttpMethod.POST,
            operationId = "subscribeHook",
            callbacks = mapOf(
                "onDataEvent" to mapOf(
                    // Runtime Expression -> Path Item
                    "{\$request.query.callbackUrl}" to PathItem(
                        post = EndpointDefinition(
                            path = "/",
                            method = HttpMethod.POST,
                            operationId = "sendEvent",
                            requestBodyType = "EventPayload"
                        )
                    )
                )
            )
        )

        // Verify the callback structure
        assertTrue(registerOp.callbacks.containsKey("onDataEvent"))
        val callbackMap = registerOp.callbacks["onDataEvent"]!!
        assertTrue(callbackMap.containsKey("{\$request.query.callbackUrl}"))

        val callbackPathItem = callbackMap["{\$request.query.callbackUrl}"]
        assertNotNull(callbackPathItem)
        assertEquals("sendEvent", callbackPathItem?.post?.operationId)
    }

    @Test
    fun `Domain Supports Links`() {
        val link = Link(
            operationId = "getUser",
            parameters = mapOf("userId" to "\$response.body#/id"),
            requestBody = "\$request.body",
            description = "Get the user associated with this resource"
        )

        val response = EndpointResponse(
            statusCode = "200",
            links = mapOf("UserLink" to link)
        )

        assertNotNull(response.links)
        assertTrue(response.links!!.containsKey("UserLink"))

        val actualLink = response.links["UserLink"]
        assertEquals("getUser", actualLink?.operationId)
        assertEquals("\$response.body#/id", actualLink?.parameters?.get("userId"))
        assertEquals("\$request.body", actualLink?.requestBody)
    }

    @Test
    fun `Domain Supports Global Security Requirements`() {
        // Defined in Root
        val root = OpenApiDefinition(
            info = Info("Secure API", "1.0"),
            security = listOf(
                mapOf("api_key" to emptyList()), // ApiKey
                mapOf("petstore_auth" to listOf("write:pets", "read:pets")) // OAuth
            )
        )

        assertEquals(2, root.security.size)
        // Check First Requirement
        assertTrue(root.security[0].containsKey("api_key"))
        assertTrue(root.security[0]["api_key"]!!.isEmpty())

        // Check Second Requirement (Alternative)
        assertTrue(root.security[1]["petstore_auth"]!!.contains("write:pets"))
        assertTrue(root.security[1]["petstore_auth"]!!.contains("read:pets"))
    }

    @Test
    fun `Domain Supports Components and Security Schemes`() {
        val components = Components(
            securitySchemes = mapOf(
                "OAuth2" to SecurityScheme(
                    type = "oauth2",
                    flows = OAuthFlows(
                        implicit = OAuthFlow(
                            authorizationUrl = "https://example.com/api/oauth/dialog",
                            scopes = mapOf("write:pets" to "modify pets")
                        )
                    )
                ),
                "ApiKey" to SecurityScheme(
                    type = "apiKey",
                    name = "X-API-KEY",
                    `in` = "header"
                ),
                "Basic" to SecurityScheme(
                    type = "http",
                    scheme = "basic"
                )
            )
        )

        val oauth = components.securitySchemes["OAuth2"]
        val apikey = components.securitySchemes["ApiKey"]
        val basic = components.securitySchemes["Basic"]

        // OAuth Checks
        assertNotNull(oauth)
        assertEquals("oauth2", oauth?.type)
        assertEquals("https://example.com/api/oauth/dialog", oauth?.flows?.implicit?.authorizationUrl)
        assertEquals("modify pets", oauth?.flows?.implicit?.scopes?.get("write:pets"))

        // ApiKey Checks
        assertNotNull(apikey)
        assertEquals("apiKey", apikey?.type)
        assertEquals("header", apikey?.`in`)
        assertEquals("X-API-KEY", apikey?.name)

        // Basic Auth Checks
        assertNotNull(basic)
        assertEquals("http", basic?.type)
        assertEquals("basic", basic?.scheme)
    }

    @Test
    fun `Domain Components holds Schemas and Parameters`() {
        val components = Components(
            schemas = mapOf(
                "User" to SchemaDefinition(name = "User", type = "object")
            ),
            parameters = mapOf(
                "limitParam" to EndpointParameter(name = "limit", type = "integer", location = ParameterLocation.QUERY)
            ),
            responses = mapOf(
                "NotFound" to EndpointResponse(statusCode = "404", description = "Entity not found")
            )
        )

        // Schemas
        assertTrue(components.schemas.containsKey("User"))
        assertEquals("object", components.schemas["User"]?.type)

        // Parameters
        val param = components.parameters["limitParam"]
        assertNotNull(param)
        assertEquals("limit", param?.name)
        assertEquals(ParameterLocation.QUERY, param?.location)

        // Responses
        val resp = components.responses["NotFound"]
        assertNotNull(resp)
        assertEquals("404", resp?.statusCode)
        assertEquals("Entity not found", resp?.description)
    }

    @Test
    fun `Domain Components holds Headers and PathItems`() {
        val components = Components(
            headers = mapOf(
                "X-Rate-Limit" to Header(
                    type = "integer",
                    description = "Request limit",
                    required = true
                )
            ),
            pathItems = mapOf(
                "UserPath" to PathItem(
                    summary = "User Operations",
                    get = EndpointDefinition(
                        path = "/users",
                        method = HttpMethod.GET,
                        operationId = "getAllUsers"
                    )
                )
            )
        )

        // Headers
        assertTrue(components.headers.containsKey("X-Rate-Limit"))
        val header = components.headers["X-Rate-Limit"]
        assertNotNull(header)
        assertEquals("integer", header?.type)
        assertEquals("Request limit", header?.description)
        assertEquals(true, header?.required)

        // PathItems
        assertTrue(components.pathItems.containsKey("UserPath"))
        val pathItem = components.pathItems["UserPath"]
        assertNotNull(pathItem)
        assertEquals("User Operations", pathItem?.summary)
        assertEquals("getAllUsers", pathItem?.get?.operationId)
    }

    @Test
    fun `Domain Supports Discriminator Mapping`() {
        val schema = SchemaDefinition(
            name = "Pet",
            type = "object",
            discriminator = Discriminator(
                propertyName = "petType",
                mapping = mapOf("dog" to "#/components/schemas/Dog")
            )
        )

        assertEquals("petType", schema.discriminator?.propertyName)
        assertEquals("#/components/schemas/Dog", schema.discriminator?.mapping?.get("dog"))
    }

    @Test
    fun `Domain Supports XML Metadata`() {
        val schema = SchemaDefinition(
            name = "Customer",
            type = "object",
            xml = Xml(
                name = "customer_record",
                namespace = "http://example.com/schema",
                prefix = "crm",
                nodeType = "element"
            ),
            properties = mapOf(
                "id" to SchemaProperty("string", xml = Xml(attribute = true, name = "cid")),
                "tags" to SchemaProperty("array", xml = Xml(wrapped = true, name = "tag_list"))
            )
        )

        // Verify Schema-level XML
        assertNotNull(schema.xml)
        assertEquals("customer_record", schema.xml?.name)
        assertEquals("http://example.com/schema", schema.xml?.namespace)
        assertEquals("crm", schema.xml?.prefix)
        assertEquals("element", schema.xml?.nodeType)

        // Verify Property-level XML (Attribute)
        val idProp = schema.properties["id"]
        assertNotNull(idProp?.xml)
        assertEquals(true, idProp?.xml?.attribute)
        assertEquals("cid", idProp?.xml?.name)

        // Verify Property-level XML (Wrapped Array)
        val tagsProp = schema.properties["tags"]
        assertNotNull(tagsProp?.xml)
        assertEquals(true, tagsProp?.xml?.wrapped)
        assertEquals("tag_list", tagsProp?.xml?.name)
    }
}
