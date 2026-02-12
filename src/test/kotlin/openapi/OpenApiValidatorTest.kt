package openapi

import domain.Components
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.EncodingObject
import domain.ExampleObject
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
import domain.SecurityScheme
import domain.SchemaProperty
import domain.Server
import domain.Tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiValidatorTest {

    private val validator = OpenApiValidator()

    @Test
    fun `validate returns empty for minimal valid definition`() {
        val endpoint = EndpointDefinition(
            path = "/pets",
            method = HttpMethod.GET,
            operationId = "listPets",
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )
        val definition = OpenApiDefinition(
            info = Info("Pets", "1.0"),
            paths = mapOf("/pets" to PathItem(get = endpoint))
        )

        val issues = validator.validate(definition)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `validate flags common spec violations`() {
        val badParam = EndpointParameter(
            name = "q",
            type = "String",
            location = ParameterLocation.QUERY,
            schema = SchemaProperty(types = setOf("string")),
            content = mapOf("application/json" to MediaTypeObject(schema = SchemaProperty(types = setOf("string"))))
        )
        val queryStringParam = EndpointParameter(
            name = "raw",
            type = "String",
            location = ParameterLocation.QUERYSTRING,
            schema = SchemaProperty(types = setOf("string"))
        )
        val response = EndpointResponse(
            statusCode = "200",
            headers = mapOf("X-Test" to Header(type = "String", style = ParameterStyle.FORM))
        )
        val endpoint = EndpointDefinition(
            path = "search",
            method = HttpMethod.GET,
            operationId = "search",
            parameters = listOf(badParam, queryStringParam),
            responses = mapOf("200" to response)
        )

        val definition = OpenApiDefinition(
            info = Info("Bad", "1.0"),
            servers = listOf(Server(url = "https://example.com/api?debug=true")),
            components = Components(
                schemas = mapOf("Bad Name" to domain.SchemaDefinition(name = "Bad Name", type = "object"))
            ),
            paths = mapOf("search" to PathItem(get = endpoint))
        )

        val issues = validator.validate(definition)
        val messages = issues.map { it.message }

        assertTrue(messages.any { it.contains("Path keys must start with") })
        assertTrue(messages.any { it.contains("Server url must not include query or fragment") })
        assertTrue(messages.any { it.contains("Parameter must not define both schema and content") })
        assertTrue(messages.any { it.contains("query and querystring parameters cannot be used together") })
        assertTrue(messages.any { it.contains("querystring parameters must use content") })
        assertTrue(messages.any { it.contains("Header style must be 'simple'") })
        assertTrue(messages.any { it.contains("Component keys must match") })
        assertTrue(messages.any { it.contains("Response description is required") })

        val errorCount = issues.count { it.severity == OpenApiIssueSeverity.ERROR }
        assertTrue(errorCount >= 7)
    }

    @Test
    fun `validate flags itemSchema on non-sequential media types`() {
        val response = EndpointResponse(
            statusCode = "200",
            description = "ok",
            content = mapOf(
                "application/json" to MediaTypeObject(
                    itemSchema = SchemaProperty(types = setOf("object"))
                )
            )
        )
        val endpoint = EndpointDefinition(
            path = "/events",
            method = HttpMethod.GET,
            operationId = "getEvents",
            responses = mapOf("200" to response)
        )
        val definition = OpenApiDefinition(
            info = Info("Events", "1.0"),
            paths = mapOf("/events" to PathItem(get = endpoint))
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("itemSchema is only valid for sequential") })
    }

    @Test
    fun `validate allows itemSchema for sequential media types`() {
        val response = EndpointResponse(
            statusCode = "200",
            description = "ok",
            content = mapOf(
                "application/jsonl" to MediaTypeObject(
                    itemSchema = SchemaProperty(types = setOf("object"))
                )
            )
        )
        val endpoint = EndpointDefinition(
            path = "/stream",
            method = HttpMethod.GET,
            operationId = "streamEvents",
            responses = mapOf("200" to response)
        )
        val definition = OpenApiDefinition(
            info = Info("Stream", "1.0"),
            paths = mapOf("/stream" to PathItem(get = endpoint))
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.none { it.contains("itemSchema is only valid for sequential") })
    }

    @Test
    fun `validate flags positional encoding without array schema`() {
        val response = EndpointResponse(
            statusCode = "200",
            description = "ok",
            content = mapOf(
                "multipart/mixed" to MediaTypeObject(
                    schema = SchemaProperty(types = setOf("string")),
                    prefixEncoding = listOf(EncodingObject())
                )
            )
        )
        val endpoint = EndpointDefinition(
            path = "/parts",
            method = HttpMethod.GET,
            operationId = "listParts",
            responses = mapOf("200" to response)
        )
        val definition = OpenApiDefinition(
            info = Info("Parts", "1.0"),
            paths = mapOf("/parts" to PathItem(get = endpoint))
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("prefixEncoding/itemEncoding requires an array schema") })
    }

    @Test
    fun `validate flags path templating and uniqueness rules`() {
        val missingParamOp = EndpointDefinition(
            path = "/users/{id}/{id}",
            method = HttpMethod.GET,
            operationId = "getUser",
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )
        val extraParamOp = EndpointDefinition(
            path = "/users/{id}/{id}",
            method = HttpMethod.PUT,
            operationId = "putUser",
            parameters = listOf(
                EndpointParameter(name = "id", type = "String", location = ParameterLocation.PATH),
                EndpointParameter(name = "oops", type = "String", location = ParameterLocation.PATH)
            ),
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )

        val definition = OpenApiDefinition(
            info = Info("Users", "1.0"),
            paths = mapOf(
                "/users/{id}/{id}" to PathItem(get = missingParamOp, put = extraParamOp)
            )
        )

        val issues = validator.validate(definition)
        val messages = issues.map { it.message }

        assertTrue(messages.any { it.contains("must not appear more than once") })
        assertTrue(messages.any { it.contains("Missing path parameter 'id'") })
        assertTrue(messages.any { it.contains("Path parameter 'oops' is not present") })
    }

    @Test
    fun `validate flags templated path collisions`() {
        val opA = EndpointDefinition(
            path = "/pets/{petId}",
            method = HttpMethod.GET,
            operationId = "getPet",
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )
        val opB = EndpointDefinition(
            path = "/pets/{name}",
            method = HttpMethod.GET,
            operationId = "getPetByName",
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )

        val definition = OpenApiDefinition(
            info = Info("Pets", "1.0"),
            paths = mapOf(
                "/pets/{petId}" to PathItem(get = opA),
                "/pets/{name}" to PathItem(get = opB)
            )
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("templated structure") })
    }

    @Test
    fun `validate flags server variable duplicates and missing definitions`() {
        val definition = OpenApiDefinition(
            info = Info("Servers", "1.0"),
            servers = listOf(
                Server(
                    url = "https://{env}.example.com/{env}",
                    variables = mapOf(
                        "env" to domain.ServerVariable(default = "prod", enum = listOf("prod"))
                    )
                ),
                Server(
                    url = "https://{region}.example.com"
                )
            ),
            paths = mapOf(
                "/ping" to PathItem(
                    get = EndpointDefinition(
                        path = "/ping",
                        method = HttpMethod.GET,
                        operationId = "ping",
                        responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
                    )
                )
            )
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("must not appear more than once") })
        assertTrue(messages.any { it.contains("no variables map is defined") })
    }

    @Test
    fun `validate flags header content violations and content-type headers`() {
        val response = EndpointResponse(
            statusCode = "200",
            headers = mapOf(
                "Content-Type" to Header(type = "String", schema = SchemaProperty(types = setOf("string"))),
                "X-Multi" to Header(
                    type = "String",
                    content = mapOf(
                        "text/plain" to MediaTypeObject(schema = SchemaProperty(types = setOf("string"))),
                        "application/json" to MediaTypeObject(schema = SchemaProperty(types = setOf("string")))
                    )
                ),
                "X-Empty" to Header(type = "String")
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

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("Header content must contain exactly one media type") })
        assertTrue(messages.any { it.contains("Header must define either schema or content") })
        assertTrue(messages.any { it.contains("must not include 'Content-Type'") })
    }

    @Test
    fun `validate flags tag parent issues and cycles`() {
        val endpoint = EndpointDefinition(
            path = "/ping",
            method = HttpMethod.GET,
            operationId = "ping",
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )
        val definition = OpenApiDefinition(
            info = Info("Tags", "1.0"),
            paths = mapOf("/ping" to PathItem(get = endpoint)),
            tags = listOf(
                Tag(name = "child", parent = "missing"),
                Tag(name = "A", parent = "B"),
                Tag(name = "B", parent = "A")
            )
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("Tag parent 'missing'") })
        assertTrue(messages.any { it.contains("Tag parent cycle detected") })
    }

    @Test
    fun `validate flags link operation target rules`() {
        val response = EndpointResponse(
            statusCode = "200",
            description = "ok",
            links = mapOf(
                "missingTarget" to Link(description = "no target"),
                "bothTargets" to Link(operationId = "getUser", operationRef = "#/paths/~1users/get")
            )
        )
        val endpoint = EndpointDefinition(
            path = "/users",
            method = HttpMethod.GET,
            operationId = "listUsers",
            responses = mapOf("200" to response)
        )
        val definition = OpenApiDefinition(
            info = Info("Links", "1.0"),
            paths = mapOf("/users" to PathItem(get = endpoint))
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("Link must define either operationId or operationRef") })
        assertTrue(messages.any { it.contains("Link must not define both operationId and operationRef") })
    }

    @Test
    fun `validate flags undefined security requirement schemes`() {
        val endpoint = EndpointDefinition(
            path = "/secure",
            method = HttpMethod.GET,
            operationId = "secure",
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")),
            security = listOf(mapOf("UnknownScheme" to emptyList()))
        )
        val definition = OpenApiDefinition(
            info = Info("Security", "1.0"),
            components = Components(
                securitySchemes = mapOf(
                    "ApiKey" to SecurityScheme(type = "apiKey", name = "X-API-KEY", `in` = "header")
                )
            ),
            security = listOf(
                mapOf("ApiKey" to emptyList()),
                mapOf("https://example.com/auth" to emptyList()),
                mapOf("Missing" to emptyList())
            ),
            paths = mapOf("/secure" to PathItem(get = endpoint))
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("Security requirement references undefined scheme 'Missing'") })
        assertTrue(messages.any { it.contains("Security requirement references undefined scheme 'UnknownScheme'") })
        assertTrue(messages.none { it.contains("https://example.com/auth") })
    }

    @Test
    fun `validate flags encoding usage outside multipart and form urlencoded`() {
        val jsonMedia = MediaTypeObject(
            schema = SchemaProperty(types = setOf("string")),
            encoding = mapOf("field" to EncodingObject(contentType = "text/plain"))
        )
        val formMedia = MediaTypeObject(
            prefixEncoding = listOf(EncodingObject(contentType = "text/plain"))
        )

        val endpoint = EndpointDefinition(
            path = "/submit",
            method = HttpMethod.POST,
            operationId = "submit",
            requestBody = RequestBody(
                content = mapOf(
                    "application/json" to jsonMedia,
                    "application/x-www-form-urlencoded" to formMedia
                )
            ),
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )
        val definition = OpenApiDefinition(
            info = Info("Encoding", "1.0"),
            paths = mapOf("/submit" to PathItem(post = endpoint))
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("Encoding is only applicable") })
        assertTrue(messages.any { it.contains("prefixEncoding/itemEncoding apply only to multipart") })
    }

    @Test
    fun `validate flags parameter style constraints and missing content`() {
        val deepObjectParam = EndpointParameter(
            name = "filter",
            type = "String",
            location = ParameterLocation.QUERY,
            schema = SchemaProperty(types = setOf("string")),
            style = ParameterStyle.DEEP_OBJECT
        )
        val spaceDelimitedParam = EndpointParameter(
            name = "ids",
            type = "String",
            location = ParameterLocation.QUERY,
            schema = SchemaProperty(types = setOf("array")),
            style = ParameterStyle.SPACE_DELIMITED,
            explode = true
        )
        val pathStyleParam = EndpointParameter(
            name = "id",
            type = "String",
            location = ParameterLocation.PATH,
            schema = SchemaProperty(types = setOf("string")),
            style = ParameterStyle.FORM,
            isRequired = true
        )
        val cookieStyleParam = EndpointParameter(
            name = "session",
            type = "String",
            location = ParameterLocation.COOKIE,
            schema = SchemaProperty(types = setOf("string")),
            style = ParameterStyle.SIMPLE
        )
        val queryStringParam = EndpointParameter(
            name = "raw",
            type = "String",
            location = ParameterLocation.QUERYSTRING
        )
        val missingSchemaParam = EndpointParameter(
            name = "missing",
            type = "String",
            location = ParameterLocation.QUERY
        )

        val endpoint = EndpointDefinition(
            path = "/params",
            method = HttpMethod.GET,
            operationId = "params",
            parameters = listOf(
                deepObjectParam,
                spaceDelimitedParam,
                pathStyleParam,
                cookieStyleParam,
                queryStringParam,
                missingSchemaParam
            ),
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )

        val definition = OpenApiDefinition(
            info = Info("Params", "1.0"),
            paths = mapOf("/params" to PathItem(get = endpoint))
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("deepObject style only applies") })
        assertTrue(messages.any { it.contains("spaceDelimited style does not support explode=true") })
        assertTrue(messages.any { it.contains("Parameter style 'FORM' is not allowed for PATH") })
        assertTrue(messages.any { it.contains("Parameter style 'SIMPLE' is not allowed for COOKIE") })
        assertTrue(messages.any { it.contains("querystring parameters must define content") })
        assertTrue(messages.any { it.contains("Parameter must define either schema or content") })
    }

    @Test
    fun `validate flags unique operationIds and tags`() {
        val opA = EndpointDefinition(
            path = "/one",
            method = HttpMethod.GET,
            operationId = "dupId",
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )
        val opB = EndpointDefinition(
            path = "/two",
            method = HttpMethod.POST,
            operationId = "dupId",
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
        )

        val definition = OpenApiDefinition(
            info = Info("Ops", "1.0"),
            tags = listOf(
                domain.Tag("users"),
                domain.Tag("users")
            ),
            paths = mapOf(
                "/one" to PathItem(get = opA),
                "/two" to PathItem(post = opB)
            )
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("operationId 'dupId'") })
        assertTrue(messages.any { it.contains("Tag name 'users'") })
    }

    @Test
    fun `validate flags invalid response codes`() {
        val operation = EndpointDefinition(
            path = "/codes",
            method = HttpMethod.GET,
            operationId = "codes",
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", description = "ok"),
                "2XX" to EndpointResponse(statusCode = "2XX", description = "range"),
                "default" to EndpointResponse(statusCode = "default", description = "fallback"),
                "20X" to EndpointResponse(statusCode = "20X", description = "bad"),
                "600" to EndpointResponse(statusCode = "600", description = "bad")
            )
        )
        val definition = OpenApiDefinition(
            info = Info("Codes", "1.0"),
            paths = mapOf("/codes" to PathItem(get = operation))
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("Invalid response code '20X'") })
        assertTrue(messages.any { it.contains("Invalid response code '600'") })
    }

    @Test
    fun `validate flags server variable default outside enum`() {
        val definition = OpenApiDefinition(
            info = Info("Servers", "1.0"),
            servers = listOf(
                Server(
                    url = "https://{env}.example.com",
                    variables = mapOf(
                        "env" to domain.ServerVariable(default = "prod", enum = listOf("dev"))
                    )
                )
            ),
            paths = mapOf(
                "/ping" to PathItem(
                    get = EndpointDefinition(
                        path = "/ping",
                        method = HttpMethod.GET,
                        operationId = "ping",
                        responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
                    )
                )
            )
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("Server variable default must be one of the enum values") })
    }

    @Test
    fun `validate flags example conflicts and invalid example object fields`() {
        val badExample = ExampleObject(dataValue = 1, value = 2)
        val paramWithExamples = EndpointParameter(
            name = "filter",
            type = "String",
            location = ParameterLocation.QUERY,
            schema = SchemaProperty(types = setOf("string")),
            example = badExample,
            examples = mapOf("one" to ExampleObject(value = "a"))
        )
        val response = EndpointResponse(
            statusCode = "200",
            content = mapOf(
                "application/json" to MediaTypeObject(
                    example = ExampleObject(value = "x"),
                    examples = mapOf("y" to ExampleObject(value = "y"))
                )
            )
        )

        val definition = OpenApiDefinition(
            info = Info("Examples", "1.0"),
            paths = mapOf(
                "/examples" to PathItem(
                    get = EndpointDefinition(
                        path = "/examples",
                        method = HttpMethod.GET,
                        operationId = "examples",
                        parameters = listOf(paramWithExamples),
                        responses = mapOf("200" to response)
                    )
                )
            )
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("Parameter must not define both example and examples") })
        assertTrue(messages.any { it.contains("Example must not define both dataValue and value") })
        assertTrue(messages.any { it.contains("Media type must not define both example and examples") })
    }

    @Test
    fun `validate flags media type and encoding exclusivity`() {
        val encoding = domain.EncodingObject(
            encoding = mapOf("nested" to domain.EncodingObject()),
            itemEncoding = domain.EncodingObject()
        )
        val response = EndpointResponse(
            statusCode = "200",
            content = mapOf(
                "multipart/mixed" to MediaTypeObject(
                    encoding = mapOf("file" to encoding),
                    prefixEncoding = listOf(domain.EncodingObject())
                )
            )
        )

        val definition = OpenApiDefinition(
            info = Info("Encoding", "1.0"),
            paths = mapOf(
                "/enc" to PathItem(
                    get = EndpointDefinition(
                        path = "/enc",
                        method = HttpMethod.GET,
                        operationId = "enc",
                        responses = mapOf("200" to response)
                    )
                )
            )
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("Media type must not define encoding with prefixEncoding/itemEncoding") })
        assertTrue(messages.any { it.contains("Encoding object must not define encoding with prefixEncoding/itemEncoding") })
    }

    @Test
    fun `validate flags security scheme required fields and oauth flow requirements`() {
        val schemes = mapOf(
            "missingApiKey" to SecurityScheme(type = "apiKey"),
            "missingHttp" to SecurityScheme(type = "http"),
            "missingOauth" to SecurityScheme(type = "oauth2"),
            "missingOidc" to SecurityScheme(type = "openIdConnect"),
            "badFlow" to SecurityScheme(
                type = "oauth2",
                flows = OAuthFlows(
                    authorizationCode = OAuthFlow(authorizationUrl = "https://auth.example.com")
                )
            )
        )

        val definition = OpenApiDefinition(
            info = Info("Security", "1.0"),
            components = Components(securitySchemes = schemes),
            paths = mapOf(
                "/secure" to PathItem(
                    get = EndpointDefinition(
                        path = "/secure",
                        method = HttpMethod.GET,
                        operationId = "secure",
                        responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
                    )
                )
            )
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("apiKey security scheme requires name and in") })
        assertTrue(messages.any { it.contains("http security scheme requires scheme") })
        assertTrue(messages.any { it.contains("oauth2 security scheme requires flows") })
        assertTrue(messages.any { it.contains("openIdConnect security scheme requires openIdConnectUrl") })
        assertTrue(messages.any { it.contains("authorizationCode OAuth flow requires authorizationUrl and tokenUrl") })
    }

    @Test
    fun `validate flags license identifier and url conflict`() {
        val definition = OpenApiDefinition(
            info = Info(
                "Licenses",
                "1.0",
                license = domain.License(
                    name = "Apache 2.0",
                    identifier = "Apache-2.0",
                    url = "https://www.apache.org/licenses/LICENSE-2.0.html"
                )
            ),
            paths = mapOf(
                "/license" to PathItem(
                    get = EndpointDefinition(
                        path = "/license",
                        method = HttpMethod.GET,
                        operationId = "license",
                        responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok"))
                    )
                )
            )
        )

        val messages = validator.validate(definition).map { it.message }
        assertTrue(messages.any { it.contains("License must not define both identifier and url") })
    }
}
