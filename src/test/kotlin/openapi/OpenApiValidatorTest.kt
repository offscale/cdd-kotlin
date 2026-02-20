package openapi

import domain.Callback
import domain.Components
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.EncodingObject
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
import domain.ReferenceObject
import domain.RequestBody
import domain.SchemaDefinition
import domain.SecurityScheme
import domain.Contact
import domain.License
import domain.Discriminator
import domain.SchemaProperty
import domain.Server
import domain.Tag
import domain.Xml
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
    fun `validate warns when openapi version is not 3_2_x`() { 
        val definition = OpenApiDefinition( 
            openapi = "3.1.0", 
            info = Info("Pets", "1.0"), 
            paths = mapOf( 
                "/pets" to PathItem( 
                    get = EndpointDefinition( 
                        path = "/pets", 
                        method = HttpMethod.GET, 
                        operationId = "listPets", 
                        responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
                    ) 
                ) 
            ) 
        ) 

        val issues = validator.validate(definition) 
        val messages = issues.map { it.message } 
        assertTrue(messages.any { it.contains("OpenAPI version") }) 
    } 

    @Test
    fun `validate accepts explicit empty paths for root presence`() { 
        val definition = OpenApiDefinition( 
            info = Info("Empty Paths", "1.0.0"), 
            paths = emptyMap(), 
            pathsExplicitEmpty = true
        ) 

        val issues = OpenApiValidator().validate(definition) 

        assertTrue(issues.none { it.message.contains("at least one of: paths, webhooks, or components") }) 
    } 

    @Test
    fun `isValidJsonPointer rejects invalid pointers`() { 
        val endpoint = EndpointDefinition( 
            path = "/ev", 
            method = HttpMethod.POST, 
            operationId = "ev", 
            callbacks = mapOf( 
                "invalid" to Callback.Inline( 
                    expressions = mapOf( 
                        "{\$request.body#bad/pointer}" to PathItem(), // missing slash
                        "{\$request.body#/bad~2escape}" to PathItem() // invalid ~2
                    ) 
                ) 
            ), 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("JSON Pointer", "1.0"), 
            paths = mapOf("/ev" to PathItem(post = endpoint)) 
        ) 
        val issues = validator.validate(definition) 
        val messages = issues.map { it.message } 
        // Our runtime-expression check correctly fails bad pointers
        assertTrue(messages.any { it.contains("invalid runtime expression '\$request.body#bad/pointer'") }) 
        assertTrue(messages.any { it.contains("invalid runtime expression '\$request.body#/bad~2escape'") }) 
    } 

    @Test
    fun `validate accepts openapi patch versions`() { 
        val definition = OpenApiDefinition( 
            openapi = "3.2.1", 
            info = Info("Pets", "1.0"), 
            paths = mapOf( 
                "/pets" to PathItem( 
                    get = EndpointDefinition( 
                        path = "/pets", 
                        method = HttpMethod.GET, 
                        operationId = "listPets", 
                        responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
                    ) 
                ) 
            ) 
        ) 

        val issues = validator.validate(definition) 
        val messages = issues.map { it.message } 
        assertTrue(messages.none { it.contains("OpenAPI version") }) 
    } 

    @Test
    fun `validate resolves dynamicRef when anchor exists`() { 
        val schema = SchemaDefinition( 
            name = "Box", 
            type = "object", 
            defs = mapOf( 
                "Payload" to SchemaProperty(type = "string", dynamicAnchor = "payload") 
            ), 
            properties = mapOf( 
                "value" to SchemaProperty(dynamicRef = "#payload") 
            ), 
            required = listOf("value") 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Dynamic", "1.0"), 
            components = Components(schemas = mapOf("Box" to schema)) 
        ) 

        val issues = validator.validate(definition) 

        assertTrue(issues.none { it.message.contains("dynamicRef") }) 
    } 

    @Test
    fun `validate warns when dynamicRef has no anchor in scope`() { 
        val schema = SchemaDefinition( 
            name = "Box", 
            type = "object", 
            properties = mapOf( 
                "value" to SchemaProperty(dynamicRef = "#missing") 
            ) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Dynamic", "1.0"), 
            components = Components(schemas = mapOf("Box" to schema)) 
        ) 

        val issues = validator.validate(definition) 

        assertTrue(issues.any { it.message.contains("dynamicRef") }) 
    } 

    @Test
    fun `validate warns when media type with ref defines siblings`() { 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            paths = mapOf( 
                "/pets" to PathItem( 
                    get = EndpointDefinition( 
                        path = "/pets", 
                        method = HttpMethod.GET, 
                        operationId = "listPets", 
                        responses = mapOf( 
                            "200" to EndpointResponse( 
                                statusCode = "200", 
                                description = "ok", 
                                content = mapOf( 
                                    "application/json" to MediaTypeObject( 
                                        ref = "#/components/mediaTypes/Pet", 
                                        schema = SchemaProperty(types = setOf("string")) 
                                    ) 
                                ) 
                            ) 
                        ) 
                    ) 
                ) 
            ) 
        ) 

        val issues = validator.validate(definition) 
        val messages = issues.map { it.message } 
        assertTrue(messages.any { it.contains("Media type with \$ref should not define other fields") }) 
    } 

    @Test
    fun `validate warns when example ref defines extensions`() { 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            components = Components( 
                examples = mapOf( 
                    "Example" to ExampleObject( 
                        ref = "#/components/examples/Other", 
                        extensions = mapOf("x-extra" to true) 
                    ) 
                ) 
            ) 
        ) 

        val issues = validator.validate(definition) 
        val messages = issues.map { it.message } 
        assertTrue(messages.any { it.contains("Example with \$ref must not define extensions") }) 
    } 

    @Test
    fun `validate flags querystring conflict across path and operation`() { 
        val pathQueryString = EndpointParameter( 
            name = "raw", 
            type = "String", 
            location = ParameterLocation.QUERYSTRING, 
            content = mapOf("application/json" to MediaTypeObject(schema = SchemaProperty(types = setOf("string")))) 
        ) 
        val opQuery = EndpointParameter( 
            name = "q", 
            type = "String", 
            location = ParameterLocation.QUERY, 
            schema = SchemaProperty(types = setOf("string")) 
        ) 
        val endpoint = EndpointDefinition( 
            path = "/search", 
            method = HttpMethod.GET, 
            operationId = "search", 
            parameters = listOf(opQuery), 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Search", "1.0"), 
            paths = mapOf( 
                "/search" to PathItem( 
                    parameters = listOf(pathQueryString), 
                    get = endpoint
                ) 
            ) 
        ) 

        val issues = validator.validate(definition) 
        val messages = issues.map { it.message } 
        assertTrue(messages.any { it.contains("query and querystring parameters cannot be used together") }) 
    } 

    @Test
    fun `validate accepts callback url with embedded runtime expression`() { 
        val callback = Callback.Inline( 
            expressions = mapOf( 
                "https://example.com/hook?target={\$request.body#/url}" to PathItem() 
            ) 
        ) 
        val endpoint = EndpointDefinition( 
            path = "/events", 
            method = HttpMethod.POST, 
            operationId = "createEvent", 
            callbacks = mapOf("onEvent" to callback), 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Events", "1.0"), 
            paths = mapOf("/events" to PathItem(post = endpoint)) 
        ) 

        val issues = validator.validate(definition) 
        assertTrue(issues.none { it.message.contains("Callback expression") }) 
    } 

    @Test
    fun `validate warns when security scheme name looks like uri`() { 
        val definition = OpenApiDefinition( 
            info = Info("Secure", "1.0"), 
            components = Components( 
                securitySchemes = mapOf( 
                    "https://example.com/auth" to SecurityScheme(type = "http", scheme = "bearer") 
                ) 
            ) 
        ) 

        val issues = validator.validate(definition) 
        assertTrue(issues.any { it.message.contains("looks like a URI") }) 
    } 

    @Test
    fun `validate flags invalid header tokens in parameters and responses`() { 
        val badHeaderParam = EndpointParameter( 
            name = "Bad Header", 
            type = "String", 
            location = ParameterLocation.HEADER, 
            schema = SchemaProperty(types = setOf("string")) 
        ) 
        val endpoint = EndpointDefinition( 
            path = "/headers", 
            method = HttpMethod.GET, 
            operationId = "badHeaders", 
            parameters = listOf(badHeaderParam), 
            responses = mapOf( 
                "200" to EndpointResponse( 
                    statusCode = "200", 
                    description = "ok", 
                    headers = mapOf( 
                        "Bad Header" to Header(type = "String", schema = SchemaProperty(types = setOf("string"))) 
                    ) 
                ) 
            ) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Headers", "1.0"), 
            paths = mapOf("/headers" to PathItem(get = endpoint)) 
        ) 

        val issues = validator.validate(definition) 
        assertTrue(issues.any { it.message.contains("Header name 'Bad Header'") }) 
        assertTrue(issues.any { it.message.contains("Header parameter name 'Bad Header'") }) 
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
    fun `validate flags invalid self uri`() { 
        val definition = OpenApiDefinition( 
            info = Info("Self URI", "1.0"), 
            self = "http://[bad", 
            paths = mapOf( 
                "/pets" to PathItem( 
                    get = EndpointDefinition( 
                        path = "/pets", 
                        method = HttpMethod.GET, 
                        operationId = "listPets", 
                        responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
                    ) 
                ) 
            ) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("valid URI") }) 
    } 

    @Test
    fun `validate flags invalid callback runtime expression`() { 
        val endpoint = EndpointDefinition( 
            path = "/subscribe", 
            method = HttpMethod.POST, 
            operationId = "subscribe", 
            callbacks = mapOf( 
                "onData" to Callback.Inline( 
                    expressions = mapOf( 
                        "notAnExpression" to PathItem( 
                            post = EndpointDefinition( 
                                path = "/", 
                                method = HttpMethod.POST, 
                                operationId = "pushEvent", 
                                responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
                            ) 
                        ) 
                    ) 
                ) 
            ), 
            responses = mapOf("202" to EndpointResponse(statusCode = "202", description = "accepted")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Callbacks", "1.0"), 
            paths = mapOf("/subscribe" to PathItem(post = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Callback expression") }) 
    } 

    @Test
    fun `validate flags parameter and header content with multiple media types`() { 
        val multiContentParam = EndpointParameter( 
            name = "payload", 
            type = "String", 
            location = ParameterLocation.QUERY, 
            content = mapOf( 
                "application/json" to MediaTypeObject(schema = SchemaProperty(types = setOf("string"))), 
                "text/plain" to MediaTypeObject(schema = SchemaProperty(types = setOf("string"))) 
            ) 
        ) 
        val multiContentHeader = Header( 
            type = "String", 
            content = mapOf( 
                "application/json" to MediaTypeObject(schema = SchemaProperty(types = setOf("string"))), 
                "text/plain" to MediaTypeObject(schema = SchemaProperty(types = setOf("string"))) 
            ) 
        ) 
        val response = EndpointResponse( 
            statusCode = "200", 
            description = "ok", 
            headers = mapOf("X-Multi" to multiContentHeader) 
        ) 
        val endpoint = EndpointDefinition( 
            path = "/multi", 
            method = HttpMethod.GET, 
            operationId = "getMulti", 
            parameters = listOf(multiContentParam), 
            responses = mapOf("200" to response) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Multi", "1.0"), 
            paths = mapOf("/multi" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Parameter content must contain exactly one media type") }) 
        assertTrue(messages.any { it.contains("Header content must contain exactly one media type") }) 
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
    fun `validate flags invalid uri and email fields`() { 
        val endpoint = EndpointDefinition( 
            path = "/pets", 
            method = HttpMethod.GET, 
            operationId = "listPets", 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val oauthFlows = OAuthFlows( 
            authorizationCode = OAuthFlow( 
                authorizationUrl = "http://example.com/auth", 
                tokenUrl = "not-a-url", 
                scopes = emptyMap() 
            ) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info( 
                title = "", 
                version = "", 
                termsOfService = "not a uri", 
                contact = Contact(url = "ht!tp://bad", email = "not-an-email"), 
                license = License(name = "MIT", url = "://bad") 
            ), 
            externalDocs = ExternalDocumentation(url = "bad uri"), 
            components = Components( 
                securitySchemes = mapOf( 
                    "oidc" to SecurityScheme(type = "openIdConnect", openIdConnectUrl = "bad"), 
                    "oauth" to SecurityScheme(type = "oauth2", flows = oauthFlows) 
                ) 
            ), 
            paths = mapOf("/pets" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Info title must not be blank") }) 
        assertTrue(messages.any { it.contains("Info version must not be blank") }) 
        assertTrue(messages.any { it.contains("valid URI") }) 
        assertTrue(messages.any { it.contains("valid email") }) 
        assertTrue(messages.any { it.contains("valid URL") || it.contains("absolute URL") }) 
        assertTrue(messages.any { it.contains("https scheme") }) 
    } 

    @Test
    fun `validate warns when encoding headers include content type`() { 
        val response = EndpointResponse( 
            statusCode = "200", 
            description = "ok", 
            content = mapOf( 
                "multipart/form-data" to MediaTypeObject( 
                    schema = SchemaProperty(types = setOf("object")), 
                    encoding = mapOf( 
                        "file" to EncodingObject( 
                            headers = mapOf( 
                                "Content-Type" to Header( 
                                    type = "String", 
                                    schema = SchemaProperty(types = setOf("string")) 
                                ) 
                            ) 
                        ) 
                    ) 
                ) 
            ) 
        ) 
        val endpoint = EndpointDefinition( 
            path = "/upload", 
            method = HttpMethod.POST, 
            operationId = "upload", 
            responses = mapOf("200" to response) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Upload", "1.0"), 
            paths = mapOf("/upload" to PathItem(post = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Encoding headers must not include Content-Type") }) 
    } 

    @Test
    fun `validate flags parameter content with serialization fields`() { 
        val badParam = EndpointParameter( 
            name = "q", 
            type = "String", 
            location = ParameterLocation.QUERY, 
            content = mapOf( 
                "application/json" to MediaTypeObject(schema = SchemaProperty(types = setOf("string"))) 
            ), 
            style = ParameterStyle.FORM, 
            explode = true, 
            allowReserved = true
        ) 
        val endpoint = EndpointDefinition( 
            path = "/search", 
            method = HttpMethod.GET, 
            operationId = "search", 
            parameters = listOf(badParam), 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Search", "1.0"), 
            paths = mapOf("/search" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Parameters using content must not define style/explode/allowReserved") }) 
    } 

    @Test
    fun `validate flags encoding entries without matching schema properties`() { 
        val response = EndpointResponse( 
            statusCode = "200", 
            description = "ok", 
            content = mapOf( 
                "multipart/form-data" to MediaTypeObject( 
                    schema = SchemaProperty( 
                        types = setOf("object"), 
                        properties = mapOf("file" to SchemaProperty(types = setOf("string"))) 
                    ), 
                    encoding = mapOf( 
                        "file" to EncodingObject(contentType = "image/png"), 
                        "missing" to EncodingObject(contentType = "text/plain") 
                    ) 
                ) 
            ) 
        ) 
        val endpoint = EndpointDefinition( 
            path = "/upload", 
            method = HttpMethod.POST, 
            operationId = "upload", 
            responses = mapOf("200" to response) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Uploads", "1.0"), 
            paths = mapOf("/upload" to PathItem(post = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Encoding entry 'missing' has no matching schema property") }) 
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
    fun `validate flags duplicate server names`() { 
        val definition = OpenApiDefinition( 
            info = Info("Servers", "1.0"), 
            servers = listOf( 
                Server(url = "https://a.example.com", name = "dup"), 
                Server(url = "https://b.example.com", name = "dup") 
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
        assertTrue(messages.any { it.contains("Server name 'dup' must be unique") }) 
    } 

    @Test
    fun `validate flags server variable names with braces`() { 
        val definition = OpenApiDefinition( 
            info = Info("Servers", "1.0"), 
            servers = listOf( 
                Server( 
                    url = "https://{env}.example.com", 
                    variables = mapOf( 
                        "env{bad}" to domain.ServerVariable(default = "prod") 
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
        assertTrue(messages.any { it.contains("must not contain '{' or '}'") }) 
    } 

    @Test
    fun `validate flags path item server url with query`() { 
        val endpoint = EndpointDefinition( 
            path = "/items", 
            method = HttpMethod.GET, 
            operationId = "listItems", 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Items", "1.0"), 
            paths = mapOf( 
                "/items" to PathItem( 
                    get = endpoint, 
                    servers = listOf(Server(url = "https://example.com/api?debug=true")) 
                ) 
            ) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Server url must not include query or fragment") }) 
    } 

    @Test
    fun `validate flags invalid additionalOperations method token`() { 
        val invalidOp = EndpointDefinition( 
            path = "/copy", 
            method = HttpMethod.CUSTOM, 
            customMethod = "BAD METHOD", 
            operationId = "copy", 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Custom", "1.0"), 
            paths = mapOf( 
                "/copy" to PathItem( 
                    additionalOperations = mapOf("BAD METHOD" to invalidOp) 
                ) 
            ) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("additionalOperations method 'BAD METHOD' must be a valid HTTP token") }) 
    } 

    @Test
    fun `validate allows webhook keys without leading slash`() { 
        val webhookOp = EndpointDefinition( 
            path = "/", 
            method = HttpMethod.POST, 
            operationId = "onPing", 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Webhooks", "1.0"), 
            webhooks = mapOf("onPing" to PathItem(post = webhookOp)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.none { it.contains("Path keys must start with '/'") }) 
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
    fun `validate flags path keys with query or fragment`() { 
        val opQuery = EndpointDefinition( 
            path = "/pets?draft=true", 
            method = HttpMethod.GET, 
            operationId = "getPetsDraft", 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val opFragment = EndpointDefinition( 
            path = "/pets#frag", 
            method = HttpMethod.GET, 
            operationId = "getPetsFragment", 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Paths", "1.0"), 
            paths = mapOf( 
                "/pets?draft=true" to PathItem(get = opQuery), 
                "/pets#frag" to PathItem(get = opFragment) 
            ) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Path keys must not include query or fragment") }) 
    } 

    @Test
    fun `validate flags duplicate path item parameters`() { 
        val endpoint = EndpointDefinition( 
            path = "/items", 
            method = HttpMethod.GET, 
            operationId = "listItems", 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val pathItem = PathItem( 
            parameters = listOf( 
                EndpointParameter( 
                    name = "filter", 
                    type = "String", 
                    location = ParameterLocation.QUERY, 
                    schema = SchemaProperty(types = setOf("string")) 
                ), 
                EndpointParameter( 
                    name = "filter", 
                    type = "String", 
                    location = ParameterLocation.QUERY, 
                    schema = SchemaProperty(types = setOf("string")) 
                ) 
            ), 
            get = endpoint
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Items", "1.0"), 
            paths = mapOf("/items" to pathItem) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Duplicate parameter 'filter'") }) 
    } 

    @Test
    fun `validate flags header serialization fields with content`() { 
        val response = EndpointResponse( 
            statusCode = "200", 
            description = "ok", 
            headers = mapOf( 
                "X-Test" to Header( 
                    type = "String", 
                    content = mapOf("text/plain" to MediaTypeObject(schema = SchemaProperty(types = setOf("string")))), 
                    style = ParameterStyle.FORM, 
                    explode = true
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

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Header style must not be set when content is used") }) 
        assertTrue(messages.any { it.contains("Header explode must not be set when content is used") }) 
    } 

    @Test
    fun `validate warns on path item ref with siblings`() { 
        val definition = OpenApiDefinition( 
            info = Info("Refs", "1.0"), 
            paths = mapOf( 
                "/shared" to PathItem( 
                    ref = "#/components/pathItems/Shared", 
                    summary = "Shared operations" 
                ) 
            ) 
        ) 

        val issues = validator.validate(definition) 
        val messages = issues.map { it.message } 
        assertTrue(messages.any { it.contains("Path Item with \$ref should not define other fields") }) 
        assertTrue(issues.any { it.severity == OpenApiIssueSeverity.WARNING }) 
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
    fun `validate flags invalid local operationRef`() { 
        val response = EndpointResponse( 
            statusCode = "200", 
            description = "ok", 
            links = mapOf( 
                "good" to Link(operationRef = "#/paths/~1users/get"), 
                "bad" to Link(operationRef = "#/paths/~1missing/get") 
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
        assertTrue(messages.any { it.contains("operationRef '#/paths/~1missing/get'") }) 
        assertTrue(messages.none { it.contains("operationRef '#/paths/~1users/get'") }) 
    } 

    @Test
    fun `validate accepts operationRef with encoded path template braces`() { 
        val response = EndpointResponse( 
            statusCode = "200", 
            description = "ok", 
            links = mapOf( 
                "encoded" to Link(operationRef = "#/paths/~1users~1%7Bid%7D/get"), 
                "raw" to Link(operationRef = "#/paths/~1users~1{id}/get") 
            ) 
        ) 
        val endpoint = EndpointDefinition( 
            path = "/users/{id}", 
            method = HttpMethod.GET, 
            operationId = "getUser", 
            responses = mapOf("200" to response) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Links", "1.0"), 
            paths = mapOf("/users/{id}" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.none { it.contains("operationRef") }) 
    } 

    @Test
    fun `validate supports self based operationRef`() { 
        val response = EndpointResponse( 
            statusCode = "200", 
            description = "ok", 
            links = mapOf( 
                "good" to Link(operationRef = "https://example.com/openapi#/paths/~1users/get"), 
                "bad" to Link(operationRef = "https://example.com/openapi#/paths/~1missing/get") 
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
            self = "https://example.com/openapi", 
            paths = mapOf("/users" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("operationRef 'https://example.com/openapi#/paths/~1missing/get'") }) 
        assertTrue(messages.none { it.contains("operationRef 'https://example.com/openapi#/paths/~1users/get'") }) 
    } 

    @Test
    fun `validate resolves relative self operationRef using base uri`() { 
        val response = EndpointResponse( 
            statusCode = "200", 
            description = "ok", 
            links = mapOf( 
                "good" to Link(operationRef = "https://example.com/api/openapi#/paths/~1users/get"), 
                "bad" to Link(operationRef = "https://example.com/api/openapi#/paths/~1missing/get") 
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
            self = "/api/openapi", 
            paths = mapOf("/users" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition, baseUri = "https://example.com/root/openapi").map { it.message } 
        assertTrue(messages.any { it.contains("operationRef 'https://example.com/api/openapi#/paths/~1missing/get'") }) 
        assertTrue(messages.none { it.contains("operationRef 'https://example.com/api/openapi#/paths/~1users/get'") }) 
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
    fun `validate flags operationId duplicates across callbacks and component path items`() { 
        val callbackOperation = EndpointDefinition( 
            path = "/callback", 
            method = HttpMethod.POST, 
            operationId = "dupCallback", 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val operation = EndpointDefinition( 
            path = "/events", 
            method = HttpMethod.POST, 
            operationId = "dupCallback", 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")), 
            callbacks = mapOf( 
                "OnEvent" to Callback.Inline( 
                    expressions = mapOf("\$request.body#/url" to PathItem(post = callbackOperation)) 
                ) 
            ) 
        ) 
        val componentOperation = EndpointDefinition( 
            path = "/components", 
            method = HttpMethod.GET, 
            operationId = "dupCallback", 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 

        val definition = OpenApiDefinition( 
            info = Info("Callbacks", "1.0"), 
            components = Components( 
                pathItems = mapOf("Shared" to PathItem(get = componentOperation)) 
            ), 
            paths = mapOf("/events" to PathItem(post = operation)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("operationId 'dupCallback'") }) 
    } 

    @Test
    fun `validate flags invalid media type keys in content maps`() { 
        val response = EndpointResponse( 
            statusCode = "200", 
            description = "ok", 
            content = mapOf( 
                "not-a-media-type" to MediaTypeObject(schema = SchemaProperty(types = setOf("string"))) 
            ) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Media", "1.0"), 
            paths = mapOf( 
                "/media" to PathItem( 
                    get = EndpointDefinition( 
                        path = "/media", 
                        method = HttpMethod.GET, 
                        operationId = "listMedia", 
                        responses = mapOf("200" to response) 
                    ) 
                ) 
            ) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Invalid media type key") }) 
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
    fun `validate warns when server variables are unused`() { 
        val definition = OpenApiDefinition( 
            info = Info("Servers", "1.0"), 
            servers = listOf( 
                Server( 
                    url = "https://{env}.example.com", 
                    variables = mapOf( 
                        "env" to domain.ServerVariable(default = "prod"), 
                        "region" to domain.ServerVariable(default = "us") 
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
        assertTrue(messages.any { it.contains("not used in the url: region") }) 
    } 

    @Test
    fun `validate warns when server variables are defined but url has none`() { 
        val definition = OpenApiDefinition( 
            info = Info("Servers", "1.0"), 
            servers = listOf( 
                Server( 
                    url = "https://example.com", 
                    variables = mapOf( 
                        "env" to domain.ServerVariable(default = "prod") 
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
        assertTrue(messages.any { it.contains("no variables are used in the url") }) 
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
    fun `validate flags invalid security scheme type and apiKey in`() { 
        val schemes = mapOf( 
            "badType" to SecurityScheme(type = "bearer"), 
            "badIn" to SecurityScheme(type = "apiKey", name = "X-API-KEY", `in` = "body") 
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
        assertTrue(messages.any { it.contains("Security scheme type 'bearer' is invalid") }) 
        assertTrue(messages.any { it.contains("apiKey security scheme 'in' must be one of") }) 
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

    @Test
    fun `validate flags allowEmptyValue outside query parameters`() { 
        val param = EndpointParameter( 
            name = "X-Optional", 
            type = "String", 
            location = ParameterLocation.HEADER, 
            allowEmptyValue = true, 
            schema = SchemaProperty(types = setOf("string")) 
        ) 
        val endpoint = EndpointDefinition( 
            path = "/empty", 
            method = HttpMethod.GET, 
            operationId = "empty", 
            parameters = listOf(param), 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Empty", "1.0"), 
            paths = mapOf("/empty" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("allowEmptyValue is only valid for query parameters") }) 
    } 

    @Test
    fun `validate warns on reserved header parameter names`() { 
        val param = EndpointParameter( 
            name = "Accept", 
            type = "String", 
            location = ParameterLocation.HEADER, 
            schema = SchemaProperty(types = setOf("string")) 
        ) 
        val endpoint = EndpointDefinition( 
            path = "/reserved", 
            method = HttpMethod.GET, 
            operationId = "reserved", 
            parameters = listOf(param), 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Reserved", "1.0"), 
            paths = mapOf("/reserved" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Header parameters named Accept, Content-Type, or Authorization") }) 
    } 

    @Test
    fun `validate flags discriminator without composition`() { 
        val schema = domain.SchemaDefinition( 
            name = "Pet", 
            type = "object", 
            properties = mapOf( 
                "petType" to SchemaProperty(types = setOf("string")) 
            ), 
            required = listOf("petType"), 
            discriminator = Discriminator(propertyName = "petType") 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            components = Components(schemas = mapOf("Pet" to schema)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Discriminator requires oneOf, anyOf, or allOf") }) 
    } 

    @Test
    fun `validate flags discriminator missing defaultMapping when optional`() { 
        val schema = domain.SchemaDefinition( 
            name = "Pet", 
            type = "object", 
            properties = mapOf( 
                "petType" to SchemaProperty(types = setOf("string")) 
            ), 
            oneOfSchemas = listOf( 
                SchemaProperty(ref = "#/components/schemas/Cat") 
            ), 
            discriminator = Discriminator(propertyName = "petType") 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            components = Components(schemas = mapOf("Pet" to schema)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("defaultMapping is required") }) 
    } 

    @Test
    fun `validate flags invalid xml usage`() { 
        val attrSchema = domain.SchemaDefinition( 
            name = "Attr", 
            type = "string", 
            xml = Xml(nodeType = "attribute", attribute = true) 
        ) 
        val wrappedSchema = domain.SchemaDefinition( 
            name = "Wrapped", 
            type = "string", 
            xml = Xml(wrapped = true) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Xml", "1.0"), 
            components = Components( 
                schemas = mapOf( 
                    "Attr" to attrSchema, 
                    "Wrapped" to wrappedSchema
                ) 
            ) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("xml.attribute must not be set when xml.nodeType is present") }) 
        assertTrue(messages.any { it.contains("xml.wrapped is only valid for array schemas") }) 
    } 

    @Test
    fun `validate flags invalid link runtime expressions`() { 
        val response = EndpointResponse( 
            statusCode = "200", 
            description = "ok", 
            links = mapOf( 
                "next" to Link( 
                    operationId = "getNext", 
                    parameters = mapOf( 
                        "id" to "\$response.body#/id", 
                        "bad" to "\$bad" 
                    ) 
                ) 
            ) 
        ) 
        val endpoint = EndpointDefinition( 
            path = "/pets", 
            method = HttpMethod.GET, 
            operationId = "listPets", 
            responses = mapOf("200" to response) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Links", "1.0"), 
            paths = mapOf("/pets" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Runtime expression '\$bad' is not valid") }) 
    } 

    @Test
    fun `validate warns when only non-success response is defined`() { 
        val endpoint = EndpointDefinition( 
            path = "/failure", 
            method = HttpMethod.GET, 
            operationId = "failure", 
            responses = mapOf("400" to EndpointResponse(statusCode = "400", description = "bad request")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Failure", "1.0"), 
            paths = mapOf("/failure" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Only one response is defined and it is not a success response") }) 
    } 

    @Test
    fun `validate flags invalid reference uri`() { 
        val endpoint = EndpointDefinition( 
            path = "/pets", 
            method = HttpMethod.POST, 
            operationId = "createPet", 
            requestBody = RequestBody(reference = ReferenceObject("not a uri")), 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            paths = mapOf("/pets" to PathItem(post = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("valid URI") }) 
    } 

    @Test
    fun `validate skips parameter rules when reference is present`() { 
        val endpoint = EndpointDefinition( 
            path = "/pets", 
            method = HttpMethod.GET, 
            operationId = "listPets", 
            parameters = listOf( 
                EndpointParameter( 
                    name = "limit", 
                    type = "Int", 
                    location = ParameterLocation.QUERY, 
                    isRequired = false, 
                    reference = ReferenceObject("#/components/parameters/Limit") 
                ) 
            ), 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            paths = mapOf("/pets" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.none { it.contains("Parameter must define either schema or content") }) 
    } 

    @Test
    fun `validate skips response description when reference is present`() { 
        val endpoint = EndpointDefinition( 
            path = "/pets", 
            method = HttpMethod.GET, 
            operationId = "getPets", 
            responses = mapOf( 
                "200" to EndpointResponse( 
                    statusCode = "200", 
                    reference = ReferenceObject("#/components/responses/Ok") 
                ) 
            ) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            paths = mapOf("/pets" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.none { it.contains("Response description is required") }) 
    } 

    @Test
    fun `validate skips requestBody and header rules when references are present`() { 
        val endpoint = EndpointDefinition( 
            path = "/pets", 
            method = HttpMethod.POST, 
            operationId = "createPet", 
            requestBody = RequestBody(reference = ReferenceObject("#/components/requestBodies/PetCreate")), 
            responses = mapOf( 
                "200" to EndpointResponse( 
                    statusCode = "200", 
                    description = "ok", 
                    headers = mapOf( 
                        "X-Rate-Limit" to Header( 
                            type = "String", 
                            reference = ReferenceObject("#/components/headers/RateLimit") 
                        ) 
                    ) 
                ) 
            ) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            paths = mapOf("/pets" to PathItem(post = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.none { it.contains("RequestBody content should contain at least one media type") }) 
        assertTrue(messages.none { it.contains("Header must define either schema or content") }) 
    } 

    @Test
    fun `validate flags invalid example externalValue uri`() { 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            components = Components( 
                examples = mapOf( 
                    "BadExample" to ExampleObject(externalValue = "not a uri") 
                ) 
            ) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("valid URI") }) 
    } 

    @Test
    fun `validate flags link operationId that does not exist`() { 
        val endpoint = EndpointDefinition( 
            path = "/pets", 
            method = HttpMethod.GET, 
            operationId = "listPets", 
            responses = mapOf( 
                "200" to EndpointResponse( 
                    statusCode = "200", 
                    description = "ok", 
                    links = mapOf("next" to Link(operationId = "missingOp")) 
                ) 
            ) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            paths = mapOf("/pets" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("does not match any known operationId") }) 
    } 

    @Test
    fun `validate flags invalid schema ref uri`() { 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            components = Components( 
                schemas = mapOf( 
                    "BadSchema" to SchemaDefinition( 
                        name = "BadSchema", 
                        type = "object", 
                        ref = "not a uri" 
                    ) 
                ) 
            ) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("valid URI") }) 
    } 

    @Test
    fun `validate resolves callback references with absolute uri`() { 
        val callbackPath = PathItem( 
            post = EndpointDefinition( 
                path = "/", 
                method = HttpMethod.POST, 
                operationId = "onEvent", 
                responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
            ) 
        ) 
        val callback = Callback.Inline(expressions = mapOf("{\$request.query.url}" to callbackPath)) 
        val components = Components(callbacks = mapOf("OnEvent" to callback)) 

        val endpoint = EndpointDefinition( 
            path = "/pets", 
            method = HttpMethod.GET, 
            operationId = "listPets", 
            callbacks = mapOf( 
                "onEvent" to Callback.Reference( 
                    ReferenceObject(ref = "https://example.com/openapi.yaml#/components/callbacks/OnEvent") 
                ) 
            ), 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            paths = mapOf("/pets" to PathItem(get = endpoint)), 
            components = components
        ) 

        val issues = validator.validate(definition) 
        val messages = issues.map { it.message } 
        assertTrue(messages.none { it.contains("Callback reference") && it.contains("could not be resolved") }) 
    } 

    @Test
    fun `validate flags invalid schema bounds and ordering`() { 
        val badLengths = SchemaDefinition( 
            name = "BadLengths", 
            type = "string", 
            minLength = -1, 
            maxLength = 2
        ) 
        val badObject = SchemaDefinition( 
            name = "BadObject", 
            type = "object", 
            minProperties = 5, 
            maxProperties = 2
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Bounds", "1.0"), 
            components = Components( 
                schemas = mapOf( 
                    "BadLengths" to badLengths, 
                    "BadObject" to badObject
                ) 
            ) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("minLength must be greater than or equal to 0") }) 
        assertTrue(messages.any { it.contains("minProperties must be less than or equal to maxProperties") }) 
    } 

    @Test
    fun `validate flags contains bounds without contains`() { 
        val schema = SchemaDefinition( 
            name = "MissingContains", 
            type = "array", 
            minContains = 1
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Contains", "1.0"), 
            components = Components( 
                schemas = mapOf("MissingContains" to schema) 
            ) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("minContains/maxContains are ignored without a contains schema") }) 
    } 

    @Test
    fun `validate flags invalid content metadata`() { 
        val schema = SchemaDefinition( 
            name = "ContentMeta", 
            type = "number", 
            contentMediaType = "not-a-media-type", 
            contentEncoding = "base64" 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Content", "1.0"), 
            components = Components( 
                schemas = mapOf("ContentMeta" to schema) 
            ) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("contentMediaType must be a valid media type") }) 
        assertTrue(messages.any { it.contains("contentEncoding is only applicable to string schemas") }) 
    } 

    @Test
    fun `validate warns when oas keywords appear under json schema dialect`() { 
        val schema = SchemaDefinition( 
            name = "Pet", 
            type = "object", 
            xml = Xml(name = "pet") 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            jsonSchemaDialect = "https://json-schema.org/draft/2020-12/schema", 
            components = Components(schemas = mapOf("Pet" to schema)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("OpenAPI vocabulary") }) 
    } 

    @Test
    fun `validate warns when custom keywords appear under json schema dialect`() { 
        val schema = SchemaDefinition( 
            name = "Pet", 
            type = "object", 
            customKeywords = mapOf("x-extra" to true) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            jsonSchemaDialect = "https://json-schema.org/draft/2020-12/schema", 
            components = Components(schemas = mapOf("Pet" to schema)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("unknown keywords") }) 
    } 

    @Test
    fun `validate allows oas keywords under oas dialect`() { 
        val schema = SchemaDefinition( 
            name = "Pet", 
            type = "object", 
            xml = Xml(name = "pet") 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            jsonSchemaDialect = "https://spec.openapis.org/oas/3.1/dialect/base", 
            components = Components(schemas = mapOf("Pet" to schema)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.none { it.contains("OpenAPI vocabulary") }) 
    } 

    @Test
    fun `validate respects schema dialect overrides`() { 
        val schema = SchemaDefinition( 
            name = "Pet", 
            type = "object", 
            schemaDialect = "https://spec.openapis.org/oas/3.1/dialect/base", 
            xml = Xml(name = "pet") 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            jsonSchemaDialect = "https://json-schema.org/draft/2020-12/schema", 
            components = Components(schemas = mapOf("Pet" to schema)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.none { it.contains("OpenAPI vocabulary") }) 
    } 

    @Test
    fun `validate errors when schema ref targets missing component`() { 
        val schema = SchemaDefinition( 
            name = "Pet", 
            ref = "#/components/schemas/Missing" 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            components = Components(schemas = mapOf("Pet" to schema)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("does not resolve to components.schemas") }) 
    } 

    @Test
    fun `validate errors when schema ref targets missing defs entry`() { 
        val schema = SchemaDefinition( 
            name = "Pet", 
            type = "object", 
            defs = mapOf( 
                "Known" to SchemaProperty(types = setOf("string")) 
            ), 
            properties = mapOf( 
                "missing" to SchemaProperty(ref = "#/\$defs/Unknown") 
            ) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            components = Components(schemas = mapOf("Pet" to schema)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("does not resolve to a \$defs entry") }) 
    } 

    @Test
    fun `validate errors when component reference cannot be resolved`() { 
        val endpoint = EndpointDefinition( 
            path = "/pets", 
            method = HttpMethod.GET, 
            operationId = "listPets", 
            parameters = listOf( 
                EndpointParameter( 
                    name = "limit", 
                    type = "Int", 
                    location = ParameterLocation.QUERY, 
                    reference = ReferenceObject(ref = "#/components/parameters/Missing") 
                ) 
            ), 
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            paths = mapOf("/pets" to PathItem(get = endpoint)), 
            components = Components() 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("does not resolve to components.parameters") }) 
    } 

    @Test
    fun `validate errors when response link key is invalid`() { 
        val response = EndpointResponse( 
            statusCode = "200", 
            description = "ok", 
            links = mapOf( 
                "bad key" to Link(operationId = "listPets") 
            ) 
        ) 
        val endpoint = EndpointDefinition( 
            path = "/pets", 
            method = HttpMethod.GET, 
            operationId = "listPets", 
            responses = mapOf("200" to response) 
        ) 
        val definition = OpenApiDefinition( 
            info = Info("Pets", "1.0"), 
            paths = mapOf("/pets" to PathItem(get = endpoint)) 
        ) 

        val messages = validator.validate(definition).map { it.message } 
        assertTrue(messages.any { it.contains("Link key 'bad key'") }) 
    } 
}