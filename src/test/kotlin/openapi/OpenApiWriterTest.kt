package openapi

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import domain.Callback
import domain.Components
import domain.Contact
import domain.Discriminator
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.ExampleObject
import domain.ExternalDocumentation
import domain.Header
import domain.HttpMethod
import domain.Info
import domain.Link
import domain.MediaTypeObject
import domain.OAuthFlow
import domain.OAuthFlows
import domain.OpenApiDefinition
import domain.ParameterLocation
import domain.ParameterStyle
import domain.PathItem
import domain.RequestBody
import domain.ReferenceObject
import domain.SchemaDefinition
import domain.SchemaProperty
import domain.SecurityScheme
import domain.Server
import domain.ServerVariable
import domain.Tag
import domain.Xml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiWriterTest {

    @Test
    fun `writeJson serializes full definition`() {
        val writer = OpenApiWriter()
        val definition = buildFullDefinition()

        val json = writer.writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)

        assertEquals("3.2.0", node["openapi"].asText())
        assertEquals("Pets", node["info"]["title"].asText())
        assertEquals("root-ext", node["x-root"].asText())
        assertEquals("info-ext", node["info"]["x-info"].asText())
        assertEquals("https://example.com/openapi", node["${'$'}self"].asText())
        assertTrue(node["paths"].has("/pets"))
        assertTrue(node["components"].has("schemas"))
        assertTrue(node["components"].has("securitySchemes"))
        assertTrue(node["components"].has("callbacks"))
        assertTrue(node["paths"]["/pets"]["x-path"].asBoolean())
        assertTrue(node["paths"]["/pets"]["get"]["x-op"]["flag"].asBoolean())
        assertEquals("paths-ext", node["paths"]["x-paths"].asText())
        assertEquals(true, node["webhooks"]["x-webhooks"]["flag"].asBoolean())
        val callbackNode = node["components"]["callbacks"]["onEvent"]
        assertTrue(callbackNode["x-callback"].asBoolean())
        assertNotNull(callbackNode["{\$request.body#/url}"])
        val callbackRef = node["components"]["callbacks"]["refEvent"]
        assertEquals("#/components/callbacks/onEvent", callbackRef["${'$'}ref"].asText())
        assertEquals("Callback ref", callbackRef["summary"].asText())
        assertEquals("Callback ref desc", callbackRef["description"].asText())
        val mediaTypeRef = node["components"]["mediaTypes"]["text/plain"]
        assertEquals("#/components/mediaTypes/application~1json", mediaTypeRef["${'$'}ref"].asText())
        assertEquals("MediaType ref", mediaTypeRef["summary"].asText())
        assertEquals("MediaType ref desc", mediaTypeRef["description"].asText())
        val refScheme = node["components"]["securitySchemes"]["RefScheme"]
        assertEquals("#/components/securitySchemes/ApiKey", refScheme["${'$'}ref"].asText())
        assertEquals("Ref scheme", refScheme["description"].asText())

        val petSchema = node["components"]["schemas"]["Pet"]
        assertEquals("https://example.com/schemas/Pet", petSchema["${'$'}id"].asText())
        assertEquals("https://json-schema.org/draft/2020-12/schema", petSchema["${'$'}schema"].asText())
        assertEquals("pet", petSchema["${'$'}anchor"].asText())
        assertEquals("petDyn", petSchema["${'$'}dynamicAnchor"].asText())
        assertEquals(7, petSchema["x-schema"].asInt())
        assertEquals("prop-ext", petSchema["properties"]["id"]["x-prop"].asText())
        assertEquals(2, petSchema["prefixItems"].size())
        assertEquals(1, petSchema["minContains"].asInt())
        assertEquals(2, petSchema["maxContains"].asInt())
        assertEquals("string", petSchema["contains"]["type"].asText())
        assertTrue(petSchema["properties"]["meta"]["properties"].has("version"))
        assertEquals(1, petSchema["properties"]["meta"]["required"].size())
        assertTrue(petSchema["properties"]["meta"].has("not"))
        val profile = petSchema["properties"]["profile"]
        assertEquals("https://example.com/profile", profile["externalDocs"]["url"].asText())
        assertEquals("Profile docs", profile["externalDocs"]["description"].asText())
        assertEquals("kind", profile["discriminator"]["propertyName"].asText())
        assertEquals("#/components/schemas/User", profile["discriminator"]["mapping"]["user"].asText())
        assertEquals("#/components/schemas/User", profile["discriminator"]["defaultMapping"].asText())
        assertEquals(2, petSchema["properties"]["pet"]["oneOf"].size())
        assertEquals(2, petSchema["properties"]["labels"]["examples"].size())

        val cookieParam = node["components"]["parameters"]["session"]
        assertEquals("cookie", cookieParam["style"].asText())

        val refExample = node["components"]["examples"]["RefExample"]
        assertEquals("https://example.com/example.json", refExample["${'$'}ref"].asText())

        val refLink = node["components"]["links"]["RefLink"]
        assertEquals("#/components/links/Next", refLink["${'$'}ref"].asText())
        assertEquals("Ref link summary", refLink["summary"].asText())
        assertEquals("Ref link", refLink["description"].asText())

        val nextLink = node["paths"]["/pets"]["get"]["responses"]["200"]["links"]["Next"]
        assertEquals(3, nextLink["parameters"]["priority"].asInt())
        assertEquals(true, nextLink["parameters"]["flags"][0].asBoolean())
        assertEquals(1, nextLink["requestBody"]["id"].asInt())

        val refResponse = node["components"]["responses"]["RefResponse"]
        assertEquals("#/components/responses/Ok", refResponse["${'$'}ref"].asText())
        assertEquals("Ref summary", refResponse["summary"].asText())
        assertEquals("Ref desc", refResponse["description"].asText())

        val refParam = node["components"]["parameters"]["RefParam"]
        assertEquals("#/components/parameters/limit", refParam["${'$'}ref"].asText())
        assertEquals("Ref param", refParam["summary"].asText())
        assertEquals("Ref param desc", refParam["description"].asText())

        val refHeader = node["components"]["headers"]["RefHeader"]
        assertEquals("#/components/headers/X-Trace", refHeader["${'$'}ref"].asText())
        assertEquals("Ref header desc", refHeader["description"].asText())

        val refBody = node["components"]["requestBodies"]["RefBody"]
        assertEquals("#/components/requestBodies/CreatePet", refBody["${'$'}ref"].asText())
    }

    @Test
    fun `writeJson omits header style and explode when content is used`() {
        val writer = OpenApiWriter()
        val header = Header(
            type = "string",
            content = mapOf("text/plain" to MediaTypeObject(schema = SchemaProperty("string"))),
            style = ParameterStyle.FORM,
            explode = true
        )
        val response = EndpointResponse(
            statusCode = "200",
            description = "ok",
            headers = mapOf("X-Test" to header)
        )
        val endpoint = EndpointDefinition(
            path = "/headers",
            method = HttpMethod.GET,
            operationId = "headers",
            responses = mapOf("200" to response)
        )
        val definition = OpenApiDefinition(
            info = Info("Headers", "1.0"),
            paths = mapOf("/headers" to PathItem(get = endpoint))
        )

        val json = writer.writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)
        val headerNode = node["paths"]["/headers"]["get"]["responses"]["200"]["headers"]["X-Test"]

        assertTrue(!headerNode.has("style"))
        assertTrue(!headerNode.has("explode"))
        assertTrue(headerNode.has("content"))
    }

    @Test
    fun `writeJson omits Content-Type response header`() {
        val writer = OpenApiWriter()
        val response = EndpointResponse(
            statusCode = "200",
            description = "ok",
            headers = mapOf(
                "Content-Type" to Header(
                    type = "string",
                    schema = SchemaProperty(types = setOf("string"))
                ),
                "X-Trace" to Header(
                    type = "string",
                    schema = SchemaProperty(types = setOf("string"))
                )
            )
        )
        val endpoint = EndpointDefinition(
            path = "/headers",
            method = HttpMethod.GET,
            operationId = "headers",
            responses = mapOf("200" to response)
        )
        val definition = OpenApiDefinition(
            info = Info("Headers", "1.0"),
            paths = mapOf("/headers" to PathItem(get = endpoint))
        )

        val json = writer.writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)
        val headersNode = node["paths"]["/headers"]["get"]["responses"]["200"]["headers"]

        assertTrue(headersNode.has("X-Trace"))
        assertTrue(!headersNode.has("Content-Type"))
    }

    @Test
    fun `writeJson preserves explicit empty response content`() {
        val writer = OpenApiWriter()
        val response = EndpointResponse(
            statusCode = "204",
            description = "no content",
            type = "Pet",
            contentPresent = true
        )
        val endpoint = EndpointDefinition(
            path = "/ping",
            method = HttpMethod.GET,
            operationId = "ping",
            responses = mapOf("204" to response)
        )
        val definition = OpenApiDefinition(
            info = Info("Ping", "1.0"),
            paths = mapOf("/ping" to PathItem(get = endpoint))
        )

        val json = writer.writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)
        val contentNode = node["paths"]["/ping"]["get"]["responses"]["204"]["content"]
        assertTrue(contentNode.isObject)
        assertEquals(0, contentNode.size())
    }

    @Test
    fun `writeJson preserves schema property ref siblings`() {
        val writer = OpenApiWriter()
        val definition = OpenApiDefinition(
            info = Info(title = "Test", version = "1.0"),
            components = Components(
                schemas = mapOf(
                    "Wrapper" to SchemaDefinition(
                        name = "Wrapper",
                        type = "object",
                        properties = mapOf(
                            "id" to SchemaProperty(
                                ref = "#/components/schemas/Id",
                                description = "Referenced id",
                                deprecated = true
                            )
                        )
                    ),
                    "Id" to SchemaDefinition(name = "Id", type = "string")
                )
            )
        )

        val json = writer.writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)
        val idNode = node["components"]["schemas"]["Wrapper"]["properties"]["id"]

        assertEquals("#/components/schemas/Id", idNode["${'$'}ref"].asText())
        assertEquals("Referenced id", idNode["description"].asText())
        assertTrue(idNode["deprecated"].asBoolean())
    }

    @Test
    fun `writeJson supports non-string enum values`() {
        val schema = SchemaDefinition(
            name = "MixedEnum",
            type = "object",
            properties = mapOf(
                "level" to SchemaProperty(types = setOf("integer"), enumValues = listOf(1, 2, 3)),
                "flag" to SchemaProperty(types = setOf("boolean"), enumValues = listOf(true, false)),
                "config" to SchemaProperty(
                    types = setOf("object"),
                    enumValues = listOf(mapOf("mode" to "fast"), mapOf("mode" to "slow"))
                )
            )
        )

        val definition = OpenApiDefinition(
            info = Info(title = "Enum API", version = "1.0"),
            components = Components(schemas = mapOf("MixedEnum" to schema))
        )

        val json = OpenApiWriter().writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)
        val props = node["components"]["schemas"]["MixedEnum"]["properties"]

        assertEquals(1, props["level"]["enum"][0].asInt())
        assertEquals(true, props["flag"]["enum"][0].asBoolean())
        assertEquals("fast", props["config"]["enum"][0]["mode"].asText())
    }

    @Test
    fun `writeJson emits explicit empty paths and webhooks`() {
        val definition = OpenApiDefinition(
            info = Info("Empty API", "1.0.0"),
            paths = emptyMap(),
            webhooks = emptyMap(),
            pathsExplicitEmpty = true,
            webhooksExplicitEmpty = true
        )

        val json = OpenApiWriter().writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)

        assertTrue(node.has("paths"), "Expected paths to be present when explicit empty")
        assertTrue(node.has("webhooks"), "Expected webhooks to be present when explicit empty")
        assertEquals(0, node["paths"].size())
        assertEquals(0, node["webhooks"].size())
    }

    @Test
    fun `writeJson includes oauth flow scopes even when empty`() {
        val definition = OpenApiDefinition(
            info = Info(title = "Oauth API", version = "1.0"),
            components = Components(
                securitySchemes = mapOf(
                    "OAuth" to SecurityScheme(
                        type = "oauth2",
                        flows = OAuthFlows(
                            authorizationCode = OAuthFlow(
                                authorizationUrl = "https://auth.example.com",
                                tokenUrl = "https://token.example.com",
                                scopes = emptyMap()
                            )
                        )
                    )
                )
            )
        )

        val json = OpenApiWriter().writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)
        val scopesNode = node["components"]["securitySchemes"]["OAuth"]["flows"]["authorizationCode"]["scopes"]

        assertNotNull(scopesNode)
        assertTrue(scopesNode.isObject)
        assertEquals(0, scopesNode.size())
    }

    @Test
    fun `writeYaml serializes minimal definition`() {
        val writer = OpenApiWriter()
        val minimal = OpenApiDefinition(
            info = Info("Minimal", "1.0"),
            paths = mapOf(
                "/ping" to PathItem(
                    get = EndpointDefinition(
                        path = "/ping",
                        method = HttpMethod.GET,
                        operationId = "ping"
                    )
                )
            )
        )

        val yaml = writer.write(minimal, OpenApiWriter.Format.AUTO, "spec.yaml")
        val node = ObjectMapper(YAMLFactory()).readTree(yaml)

        assertEquals("3.2.0", node["openapi"].asText())
        assertEquals("Minimal", node["info"]["title"].asText())
        assertNotNull(node["paths"]["/ping"])
    }

    @Test
    fun `writeJson preserves explicit empty security arrays`() {
        val writer = OpenApiWriter()
        val operation = EndpointDefinition(
            path = "/pets",
            method = HttpMethod.GET,
            operationId = "listPets",
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")),
            securityExplicitEmpty = true
        )
        val definition = OpenApiDefinition(
            info = Info("Security", "1.0"),
            paths = mapOf("/pets" to PathItem(get = operation)),
            securityExplicitEmpty = true
        )

        val json = writer.writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)

        assertTrue(node.has("security"))
        assertEquals(0, node["security"].size())
        assertEquals(0, node["paths"]["/pets"]["get"]["security"].size())
    }

    @Test
    fun `writeToFile writes serialized output`() {
        val writer = OpenApiWriter()
        val definition = OpenApiDefinition(info = Info("File API", "1.0"))
        val file = kotlin.io.path.createTempFile(prefix = "oas", suffix = ".json").toFile()
        file.deleteOnExit()

        writer.writeToFile(definition, file, OpenApiWriter.Format.AUTO)
        val content = file.readText()
        val node = ObjectMapper(JsonFactory()).readTree(content)

        assertEquals("3.2.0", node["openapi"].asText())
        assertEquals("File API", node["info"]["title"].asText())
    }

    @Test
    fun `writeJson serializes inline schema compositions`() {
        val writer = OpenApiWriter()
        val schema = SchemaDefinition(
            name = "InlineComposition",
            type = "object",
            oneOf = listOf("#/components/schemas/RefType"),
            oneOfSchemas = listOf(
                SchemaProperty(types = setOf("string")),
                SchemaProperty(types = setOf("integer"))
            ),
            anyOfSchemas = listOf(
                SchemaProperty(types = setOf("boolean"))
            ),
            allOfSchemas = listOf(
                SchemaProperty(types = setOf("object"))
            )
        )
        val definition = OpenApiDefinition(
            info = Info("Inline", "1.0"),
            components = Components(schemas = mapOf("InlineComposition" to schema))
        )

        val json = writer.writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)
        val inlineSchema = node["components"]["schemas"]["InlineComposition"]

        assertEquals(3, inlineSchema["oneOf"].size())
        assertEquals("#/components/schemas/RefType", inlineSchema["oneOf"][0]["${'$'}ref"].asText())
        assertEquals("string", inlineSchema["oneOf"][1]["type"].asText())
        assertEquals("integer", inlineSchema["oneOf"][2]["type"].asText())
        assertEquals("boolean", inlineSchema["anyOf"][0]["type"].asText())
        assertEquals("object", inlineSchema["allOf"][0]["type"].asText())
    }

    @Test
    fun `writeJson serializes schema examples list`() {
        val writer = OpenApiWriter()
        val schema = SchemaDefinition(
            name = "Example",
            type = "object",
            examplesList = listOf("alpha", "beta")
        )
        val definition = OpenApiDefinition(
            info = Info("Examples", "1.0"),
            components = Components(schemas = mapOf("Example" to schema))
        )

        val json = writer.writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)
        val examplesNode = node["components"]["schemas"]["Example"]["examples"]

        assertTrue(examplesNode.isArray)
        assertEquals(2, examplesNode.size())
        assertEquals("alpha", examplesNode[0].asText())
    }

    @Test
    fun `writeJson uses self as base for component refs`() {
        val writer = OpenApiWriter()
        val petSchema = SchemaDefinition(
            name = "Pet",
            type = "object",
            oneOf = listOf("Cat")
        )
        val catSchema = SchemaDefinition(name = "Cat", type = "object")
        val requestBodySchema = SchemaDefinition(name = "PetCreate", type = "object")
        val response = EndpointResponse(
            statusCode = "204",
            description = "No content",
            type = "Pet"
        )
        val operation = EndpointDefinition(
            path = "/pets",
            method = HttpMethod.GET,
            operationId = "listPets",
            requestBodyType = "PetCreate",
            responses = mapOf("204" to response)
        )

        val definition = OpenApiDefinition(
            info = Info("Self", "1.0.0"),
            self = "https://example.com/openapi",
            paths = mapOf("/pets" to PathItem(get = operation)),
            components = Components(
                schemas = mapOf(
                    "Pet" to petSchema,
                    "Cat" to catSchema,
                    "PetCreate" to requestBodySchema
                )
            )
        )

        val json = writer.writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)

        val petOneOfRef = node["components"]["schemas"]["Pet"]["oneOf"][0]["${'$'}ref"].asText()
        assertEquals("https://example.com/openapi#/components/schemas/Cat", petOneOfRef)

        val requestBodyRef = node["paths"]["/pets"]["get"]["requestBody"]["content"]["application/json"]["schema"]["${'$'}ref"].asText()
        assertEquals("https://example.com/openapi#/components/schemas/PetCreate", requestBodyRef)

        val responseRef = node["paths"]["/pets"]["get"]["responses"]["204"]["content"]["application/json"]["schema"]["${'$'}ref"].asText()
        assertEquals("https://example.com/openapi#/components/schemas/Pet", responseRef)
    }

    private fun buildFullDefinition(): OpenApiDefinition {
        val example = ExampleObject(
            summary = "Example",
            description = "Example description",
            dataValue = mapOf("id" to 1)
        )
        val serializedExample = ExampleObject(serializedValue = "{\"id\":1}")
        val externalExample = ExampleObject(externalValue = "https://example.com/example.json")
        val refExample = ExampleObject(ref = "https://example.com/example.json")

        val header = Header(
            type = "string",
            schema = SchemaProperty("string"),
            description = "Trace",
            required = true,
            deprecated = true,
            example = ExampleObject(value = "abc"),
            explode = true
        )

        val headerWithContent = Header(
            type = "string",
            content = mapOf("text/plain" to MediaTypeObject(schema = SchemaProperty("string")))
        )

        val nestedEncoding = domain.EncodingObject(contentType = "text/plain")
        val encoding = domain.EncodingObject(
            contentType = "application/json",
            headers = mapOf("X-Trace" to header, "X-Alt" to headerWithContent),
            style = ParameterStyle.FORM,
            explode = false,
            allowReserved = true,
            encoding = mapOf("nested" to nestedEncoding),
            prefixEncoding = listOf(nestedEncoding),
            itemEncoding = nestedEncoding
        )

        val mediaType = MediaTypeObject(
            schema = SchemaProperty(ref = "#/components/schemas/Pet"),
            itemSchema = SchemaProperty("string"),
            example = ExampleObject(dataValue = mapOf("id" to 1)),
            examples = mapOf(
                "pet" to example,
                "serialized" to serializedExample,
                "external" to externalExample,
                "ref" to refExample
            ),
            encoding = mapOf("payload" to encoding),
            prefixEncoding = listOf(encoding),
            itemEncoding = encoding
        )
        val refMediaType = MediaTypeObject(
            reference = ReferenceObject(
                ref = "#/components/mediaTypes/application~1json",
                summary = "MediaType ref",
                description = "MediaType ref desc"
            )
        )

        val requestBody = RequestBody(
            description = "Create",
            content = mapOf("application/json" to mediaType),
            required = true
        )

        val pathMatrixParam = EndpointParameter(
            name = "petId",
            type = "String",
            location = ParameterLocation.PATH,
            isRequired = true,
            schema = SchemaProperty("string"),
            style = ParameterStyle.MATRIX
        )

        val pathLabelParam = EndpointParameter(
            name = "ownerId",
            type = "String",
            location = ParameterLocation.PATH,
            isRequired = true,
            schema = SchemaProperty("string"),
            style = ParameterStyle.LABEL
        )

        val queryFormParam = EndpointParameter(
            name = "limit",
            type = "Int",
            location = ParameterLocation.QUERY,
            isRequired = false,
            schema = SchemaProperty("integer", format = "int32"),
            description = "Max results",
            deprecated = true,
            allowEmptyValue = false,
            style = ParameterStyle.FORM,
            explode = false,
            allowReserved = true,
            example = ExampleObject(value = 10),
            examples = mapOf("ten" to ExampleObject(value = 10))
        )

        val querySpaceParam = EndpointParameter(
            name = "tags",
            type = "List<String>",
            location = ParameterLocation.QUERY,
            isRequired = false,
            schema = SchemaProperty("array", items = SchemaProperty("string")),
            style = ParameterStyle.SPACE_DELIMITED,
            explode = true,
            example = serializedExample
        )

        val queryPipeParam = EndpointParameter(
            name = "ids",
            type = "List<String>",
            location = ParameterLocation.QUERY,
            isRequired = false,
            schema = SchemaProperty("array", items = SchemaProperty("string")),
            style = ParameterStyle.PIPE_DELIMITED,
            explode = false,
            example = externalExample
        )

        val queryDeepParam = EndpointParameter(
            name = "filter",
            type = "Map<String, String>",
            location = ParameterLocation.QUERY,
            isRequired = false,
            schema = SchemaProperty("object", additionalProperties = SchemaProperty("string")),
            style = ParameterStyle.DEEP_OBJECT,
            explode = true
        )

        val cookieParam = EndpointParameter(
            name = "session",
            type = "String",
            location = ParameterLocation.COOKIE,
            isRequired = false,
            schema = SchemaProperty("string"),
            style = ParameterStyle.COOKIE
        )

        val headerParam = EndpointParameter(
            name = "X-Client",
            type = "String",
            location = ParameterLocation.HEADER,
            isRequired = false,
            schema = SchemaProperty("string"),
            style = ParameterStyle.SIMPLE
        )

        val queryStringParam = EndpointParameter(
            name = "payload",
            type = "String",
            location = ParameterLocation.QUERYSTRING,
            isRequired = true,
            content = mapOf("application/json" to mediaType)
        )

        val response = EndpointResponse(
            statusCode = "200",
            summary = "Ok",
            description = "Success",
            headers = mapOf("X-Trace" to header),
            content = mapOf("application/json" to mediaType),
            links = mapOf(
                "Next" to Link(
                    operationId = "getNext",
                    parameters = mapOf(
                        "id" to "\$response.body#/id",
                        "priority" to 3,
                        "flags" to listOf(true, false)
                    ),
                    requestBody = mapOf("id" to 1),
                    description = "Next page",
                    server = Server(url = "https://api.example.com")
                )
            )
        )
        val fallbackResponse = EndpointResponse(
            statusCode = "204",
            description = "No content",
            type = "Pet"
        )
        val refParameter = EndpointParameter(
            name = "refParam",
            type = "String",
            location = ParameterLocation.QUERY,
            reference = ReferenceObject(
                ref = "#/components/parameters/limit",
                summary = "Ref param",
                description = "Ref param desc"
            )
        )
        val refHeader = Header(
            type = "string",
            reference = ReferenceObject(
                ref = "#/components/headers/X-Trace",
                description = "Ref header desc"
            )
        )
        val refRequestBody = RequestBody(
            reference = ReferenceObject(
                ref = "#/components/requestBodies/CreatePet",
                description = "Ref body desc"
            )
        )
        val refResponse = EndpointResponse(
            statusCode = "RefResponse",
            reference = ReferenceObject(
                ref = "#/components/responses/Ok",
                summary = "Ref summary",
                description = "Ref desc"
            )
        )

        val callback: Callback = Callback.Inline(
            expressions = mapOf(
                "{\$request.body#/url}" to PathItem(
                    post = EndpointDefinition(
                        path = "/callback",
                        method = HttpMethod.POST,
                        operationId = "onCallback",
                        requestBody = requestBody,
                        responses = mapOf("200" to response)
                    )
                )
            ),
            extensions = mapOf("x-callback" to true)
        )
        val refCallback: Callback = Callback.Reference(
            reference = ReferenceObject(
                ref = "#/components/callbacks/onEvent",
                summary = "Callback ref",
                description = "Callback ref desc"
            )
        )

        val schema = SchemaDefinition(
            name = "Pet",
            type = "object",
            schemaId = "https://example.com/schemas/Pet",
            schemaDialect = "https://json-schema.org/draft/2020-12/schema",
            anchor = "pet",
            dynamicAnchor = "petDyn",
            types = setOf("object", "null"),
            format = "json",
            contentMediaType = "application/json",
            contentEncoding = "base64",
            minLength = 1,
            maxLength = 255,
            pattern = "[a-z]+",
            minimum = 0.0,
            maximum = 100.0,
            minItems = 1,
            maxItems = 10,
            uniqueItems = true,
            minProperties = 1,
            maxProperties = 10,
            items = SchemaProperty("string"),
            prefixItems = listOf(SchemaProperty("string"), SchemaProperty("integer")),
            contains = SchemaProperty("string"),
            minContains = 1,
            maxContains = 2,
            properties = mapOf(
                "id" to SchemaProperty(
                    "string",
                    format = "uuid",
                    deprecated = true,
                    xml = Xml(attribute = true),
                    extensions = mapOf("x-prop" to "prop-ext")
                ),
                "owner" to SchemaProperty(ref = "#/components/schemas/User"),
                "status" to SchemaProperty(types = setOf("string"), enumValues = listOf("active", "disabled")),
                "meta" to SchemaProperty(
                    types = setOf("object"),
                    properties = mapOf("version" to SchemaProperty("string")),
                    required = listOf("version"),
                    not = SchemaProperty(types = setOf("null"))
                ),
                "profile" to SchemaProperty(
                    types = setOf("object"),
                    externalDocs = ExternalDocumentation(
                        url = "https://example.com/profile",
                        description = "Profile docs"
                    ),
                    discriminator = Discriminator(
                        propertyName = "kind",
                        mapping = mapOf("user" to "#/components/schemas/User"),
                        defaultMapping = "#/components/schemas/User"
                    )
                ),
                "labels" to SchemaProperty(
                    types = setOf("array"),
                    items = SchemaProperty("string"),
                    examples = listOf("a", "b")
                ),
                "pet" to SchemaProperty(
                    types = setOf("object"),
                    oneOf = listOf(SchemaProperty("string"), SchemaProperty("integer"))
                )
            ),
            additionalProperties = SchemaProperty("string"),
            required = listOf("id"),
            enumValues = listOf("cat", "dog"),
            description = "Pet",
            title = "Pet",
            defaultValue = "cat",
            constValue = "cat",
            deprecated = true,
            readOnly = true,
            writeOnly = true,
            externalDocs = ExternalDocumentation(url = "https://example.com/docs", description = "Docs"),
            discriminator = Discriminator(propertyName = "petType", mapping = mapOf("cat" to "#/components/schemas/Cat")),
            xml = Xml(name = "pet", namespace = "https://example.com/schema", prefix = "p", nodeType = "element", attribute = true, wrapped = true),
            oneOf = listOf("Cat", "#/components/schemas/Dog"),
            anyOf = listOf("Bird"),
            allOf = listOf("BasePet"),
            not = SchemaProperty(types = setOf("null")),
            example = "cat",
            examples = mapOf("ex1" to "cat"),
            extensions = mapOf("x-schema" to 7)
        )

        val oauthFlow = OAuthFlow(
            authorizationUrl = "https://auth.example.com/authorize",
            tokenUrl = "https://auth.example.com/token",
            refreshUrl = "https://auth.example.com/refresh",
            scopes = mapOf("read" to "read"),
            deviceAuthorizationUrl = "https://auth.example.com/device"
        )

        val components = Components(
            schemas = mapOf(
                "Pet" to schema,
                "User" to SchemaDefinition(name = "User", type = "object")
            ),
            responses = mapOf("Ok" to response, "NoContent" to fallbackResponse, "RefResponse" to refResponse),
            parameters = mapOf(
                "limit" to queryFormParam,
                "session" to cookieParam,
                "RefParam" to refParameter
            ),
            requestBodies = mapOf("CreatePet" to requestBody, "RefBody" to refRequestBody),
            headers = mapOf("X-Trace" to header, "RefHeader" to refHeader),
            securitySchemes = mapOf(
                "ApiKey" to SecurityScheme(
                    type = "apiKey",
                    name = "X-API-KEY",
                    `in` = "header",
                    deprecated = true
                ),
                "OAuth" to SecurityScheme(
                    type = "oauth2",
                    flows = OAuthFlows(
                        implicit = oauthFlow,
                        password = oauthFlow,
                        clientCredentials = oauthFlow,
                        authorizationCode = oauthFlow,
                        deviceAuthorization = oauthFlow
                    )
                ),
                "RefScheme" to SecurityScheme(
                    reference = ReferenceObject(
                        ref = "#/components/securitySchemes/ApiKey",
                        description = "Ref scheme"
                    )
                )
            ),
            examples = mapOf(
                "Pet" to example,
                "RefExample" to refExample
            ),
            links = mapOf(
                "Next" to Link(operationRef = "#/paths/~1pets/get"),
                "RefLink" to Link(
                    reference = ReferenceObject(
                        ref = "#/components/links/Next",
                        summary = "Ref link summary",
                        description = "Ref link"
                    )
                )
            ),
            callbacks = mapOf("onEvent" to callback, "refEvent" to refCallback),
            pathItems = mapOf(
                "Pets" to PathItem(
                    get = EndpointDefinition(
                        path = "/pets",
                        method = HttpMethod.GET,
                        operationId = "listPets"
                    )
                )
            ),
            mediaTypes = mapOf(
                "application/json" to mediaType,
                "text/plain" to refMediaType
            )
        )

        val server = Server(
            url = "https://api.example.com",
            description = "Prod",
            name = "prod",
            variables = mapOf(
                "version" to ServerVariable(default = "v1", enum = listOf("v1", "v2"), description = "API version")
            )
        )

        val operation = EndpointDefinition(
            path = "/pets",
            method = HttpMethod.GET,
            operationId = "listPets",
            parameters = listOf(
                pathMatrixParam,
                pathLabelParam,
                queryFormParam,
                querySpaceParam,
                queryPipeParam,
                queryDeepParam,
                headerParam,
                cookieParam,
                queryStringParam
            ),
            requestBody = requestBody,
            responses = mapOf("200" to response),
            summary = "List pets",
            description = "Returns all pets",
            externalDocs = ExternalDocumentation(url = "https://example.com/op", description = "Op docs"),
            tags = listOf("pets"),
            callbacks = mapOf("onEvent" to callback),
            deprecated = true,
            security = listOf(mapOf("ApiKey" to emptyList())),
            servers = listOf(server),
            extensions = mapOf("x-op" to mapOf("flag" to true))
        )

        val putOperation = operation.copy(method = HttpMethod.PUT, operationId = "replacePets")
        val postOperation = operation.copy(method = HttpMethod.POST, operationId = "createPets")
        val deleteOperation = operation.copy(method = HttpMethod.DELETE, operationId = "deletePets")
        val optionsOperation = operation.copy(method = HttpMethod.OPTIONS, operationId = "optionsPets")
        val headOperation = operation.copy(
            method = HttpMethod.HEAD,
            operationId = "headPets",
            requestBody = null,
            requestBodyType = "Pet"
        )
        val patchOperation = operation.copy(method = HttpMethod.PATCH, operationId = "patchPets")
        val traceOperation = operation.copy(method = HttpMethod.TRACE, operationId = "tracePets")
        val queryOperation = operation.copy(method = HttpMethod.QUERY, operationId = "queryPets")
        val customOperation = operation.copy(method = HttpMethod.CUSTOM, customMethod = "COPY", operationId = "copyPets")

        return OpenApiDefinition(
            openapi = "3.2.0",
            info = Info(
                title = "Pets",
                version = "1.0.0",
                summary = "Pets API",
                description = "API",
                termsOfService = "https://example.com/terms",
                contact = Contact(name = "Support", email = "support@example.com", url = "https://example.com/support"),
                license = domain.License(name = "Apache 2.0", identifier = "Apache-2.0"),
                extensions = mapOf("x-info" to "info-ext")
            ),
            jsonSchemaDialect = "https://spec.openapis.org/oas/3.1/dialect/base",
            servers = listOf(server),
            paths = mapOf(
                "/pets" to PathItem(
                    summary = "Pets",
                    description = "All pets",
                    get = operation,
                    put = putOperation,
                    post = postOperation,
                    delete = deleteOperation,
                    options = optionsOperation,
                    head = headOperation,
                    patch = patchOperation,
                    trace = traceOperation,
                    query = queryOperation,
                    additionalOperations = mapOf(
                        "COPY" to customOperation
                    ),
                    parameters = listOf(pathMatrixParam),
                    servers = listOf(server),
                    extensions = mapOf("x-path" to true)
                )
            ),
            pathsExtensions = mapOf("x-paths" to "paths-ext"),
            webhooks = mapOf(
                "onPet" to PathItem(post = postOperation.copy(path = "/webhook", method = HttpMethod.POST))
            ),
            webhooksExtensions = mapOf("x-webhooks" to mapOf("flag" to true)),
            components = components,
            security = listOf(mapOf("ApiKey" to emptyList())),
            tags = listOf(Tag(name = "pets", summary = "Pets", kind = "nav")),
            externalDocs = ExternalDocumentation(url = "https://example.com/external", description = "External"),
            self = "https://example.com/openapi",
            extensions = mapOf("x-root" to "root-ext")
        )
    }

    @Test
    fun `writeJson preserves path item ref siblings`() {
        val writer = OpenApiWriter()
        val definition = OpenApiDefinition(
            info = Info("Ref API", "1.0"),
            paths = mapOf(
                "/ref" to PathItem(
                    ref = "#/components/pathItems/UsersPath",
                    summary = "Ref summary",
                    parameters = listOf(
                        EndpointParameter(
                            name = "refParam",
                            type = "String",
                            location = ParameterLocation.QUERY,
                            schema = SchemaProperty("string")
                        )
                    )
                )
            )
        )

        val json = writer.writeJson(definition)
        val node = ObjectMapper(JsonFactory()).readTree(json)
        val refNode = node["paths"]["/ref"]

        assertEquals("#/components/pathItems/UsersPath", refNode["${'$'}ref"].asText())
        assertEquals("Ref summary", refNode["summary"].asText())
        assertEquals("refParam", refNode["parameters"][0]["name"].asText())
        assertEquals("query", refNode["parameters"][0]["in"].asText())
    }

    @Test
    fun `writeSchema outputs schema document`() {
        val schema = SchemaProperty(types = setOf("string"), minLength = 2)
        val json = OpenApiWriter().writeSchema(schema)
        assertTrue(json.contains("\"type\""))
        assertTrue(json.contains("\"string\""))
        assertTrue(json.contains("\"minLength\""))
        assertTrue(!json.contains("\"openapi\""))
    }

    @Test
    fun `writeDocument outputs OpenAPI document`() {
        val definition = OpenApiDefinition(
            info = Info("Doc", "1.0"),
            paths = emptyMap()
        )
        val json = OpenApiWriter().writeDocument(OpenApiDocument.OpenApi(definition))
        assertTrue(json.contains("\"openapi\""))
        assertTrue(json.contains("\"info\""))
    }

    @Test
    fun `write omits requestBody content when content was absent`() {
        val endpoint = EndpointDefinition(
            path = "/submit",
            method = HttpMethod.POST,
            operationId = "submit",
            requestBody = RequestBody(
                description = "raw payload",
                contentPresent = false
            ),
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )
        val definition = OpenApiDefinition(
            info = Info(title = "Body", version = "1.0"),
            paths = mapOf("/submit" to PathItem(post = endpoint))
        )

        val json = OpenApiWriter().writeJson(definition)
        val root = ObjectMapper(JsonFactory()).readTree(json)
        val requestBodyNode = root["paths"]["/submit"]["post"]["requestBody"]
        assertNotNull(requestBodyNode)
        assertNull(requestBodyNode["content"])
    }

    @Test
    fun `write omits operationId when not explicit`() {
        val endpoint = EndpointDefinition(
            path = "/pets",
            method = HttpMethod.GET,
            operationId = "get_pets",
            operationIdExplicit = false,
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )
        val definition = OpenApiDefinition(
            info = Info(title = "Pets", version = "1.0"),
            paths = mapOf("/pets" to PathItem(get = endpoint))
        )

        val json = OpenApiWriter().writeJson(definition)
        val root = ObjectMapper(JsonFactory()).readTree(json)
        val opNode = root["paths"]["/pets"]["get"]
        assertNotNull(opNode)
        assertNull(opNode["operationId"])
    }
}
