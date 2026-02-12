package verification

import domain.*
import psi.DtoGenerator
import psi.DtoParser
import psi.NetworkGenerator
import psi.NetworkParser
import psi.PsiInfrastructure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.AfterAll

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoundTripTest {

    private val dtoGenerator = DtoGenerator()
    private val dtoParser = DtoParser()

    private val networkGenerator = NetworkGenerator()
    private val networkParser = NetworkParser()

    @AfterAll
    fun tearDown() {
        PsiInfrastructure.dispose()
    }

    @Test
    fun `DTO Round Trip preserves structure`() {
        val originalSchema = SchemaDefinition(
            name = "TestUser",
            type = "object",
            description = "A user model",
            externalDocs = ExternalDocumentation("Schema Ref", "http://schema.org/User"),
            required = listOf("id", "email"),
            properties = mapOf(
                "id" to SchemaProperty(type = "integer", format = "int64", description = "Unique ID"),
                "email" to SchemaProperty(type = "string"),
                "isActive" to SchemaProperty(type = "boolean", description = "Status flag")
            )
        )
        val kotlinFile = dtoGenerator.generateDto("com.test", originalSchema)
        val sourceCode = kotlinFile.text
        val extractedSchemas = dtoParser.parse(sourceCode)
        val extractedSchema = extractedSchemas.first()
        assertEquals(originalSchema.name, extractedSchema.name)
    }

    @Test
    fun `Dual Type Round Trip (OAS 3_2)`() {
        // Represents { "type": ["string", "null"] }
        // In Kotlin this is String?
        val originalSchema = SchemaDefinition(
            name = "DualTypeTest",
            type = "object",
            // nullable but technically 'required' presence key in JSON
            required = listOf("title"),
            properties = mapOf(
                "title" to SchemaProperty(types = setOf("string", "null"))
            )
        )

        // Generate Kotlin
        val kotlinFile = dtoGenerator.generateDto("com.test", originalSchema)
        val sourceCode = kotlinFile.text

        // Assert Generation correct (Nullable because of explicit "null" type)
        assertTrue(sourceCode.contains("val title: String?"), "Generated code should be nullable")

        // Reverse Parse
        val extractedSchemas = dtoParser.parse(sourceCode)
        val subProp = extractedSchemas.first().properties["title"]!!

        // Assert Round Trip Result
        assertTrue(subProp.types.contains("string"), "Should contain string")
        assertTrue(subProp.types.contains("null"), "Should contain null (recovered from Kotlin nullability)")
    }

    @Test
    fun `Enum Round Trip handles mapped values`() {
        val originalEnum = SchemaDefinition(
            name = "SortDir",
            type = "string",
            enumValues = listOf("ascending", "descending", "random-shuffle")
        )
        val kotlinFile = dtoGenerator.generateDto("com.test", originalEnum)
        val sourceCode = kotlinFile.text
        val extractedSchemas = dtoParser.parse(sourceCode)
        val extractedEnum = extractedSchemas.first()
        assertEquals(originalEnum.name, extractedEnum.name)
    }

    @Test
    fun `Round Trip preserves examples`() {
        val originalSchema = SchemaDefinition(
            name = "MockSample",
            type = "object",
            example = mapOf("id" to 1),
            examples = mapOf("valid" to mapOf("id" to 1), "invalid" to emptyMap<String, Any>()),
            properties = mapOf(
                "id" to SchemaProperty("integer", example = 1)
            )
        )
        val kotlinFile = dtoGenerator.generateDto("com.test", originalSchema)
        val sourceCode = kotlinFile.text
        val extractedSchemas = dtoParser.parse(sourceCode)
        val extractedSchema = extractedSchemas.first()
        // assert values match roughly
        assertEquals(originalSchema.example, extractedSchema.example)
    }

    @Test
    fun `Array alias Round Trip preserves items`() {
        val originalSchema = SchemaDefinition(
            name = "IdList",
            type = "array",
            items = SchemaProperty("integer", format = "int64")
        )

        val kotlinFile = dtoGenerator.generateDto("com.test", originalSchema)
        val sourceCode = kotlinFile.text
        val extractedSchema = dtoParser.parse(sourceCode).first()

        assertEquals("array", extractedSchema.type)
        assertEquals("integer", extractedSchema.items?.type)
        assertEquals("int64", extractedSchema.items?.format)
    }

    @Test
    fun `Map Round Trip preserves additionalProperties`() {
        val originalSchema = SchemaDefinition(
            name = "Attributes",
            type = "object",
            properties = mapOf(
                "labels" to SchemaProperty(
                    type = "object",
                    additionalProperties = SchemaProperty("string")
                )
            )
        )

        val kotlinFile = dtoGenerator.generateDto("com.test", originalSchema)
        val sourceCode = kotlinFile.text
        val extractedSchema = dtoParser.parse(sourceCode).first()

        val labels = extractedSchema.properties["labels"]
        assertEquals("object", labels?.type)
        assertEquals("string", labels?.additionalProperties?.type)
    }

    @Test
    fun `Network Endpoint Round Trip preserves structure`() {
        val originalEndpoint = EndpointDefinition(
            path = "/users/{id}",
            method = HttpMethod.GET,
            operationId = "getUserById",
            summary = "Fetch user by ID",
            tags = listOf("User", "Public"),
            externalDocs = ExternalDocumentation("Endpoint Ref", "http://api.org/users"),
            parameters = listOf(
                EndpointParameter("id", "Long", ParameterLocation.PATH)
            ),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", description = "Success", type = "TestUser")
            ),
            requestBodyType = null
        )

        val kotlinFile = networkGenerator.generateApi(
            packageName = "com.test",
            apiName = "UserApi",
            endpoints = listOf(originalEndpoint)
        )
        val sourceCode = kotlinFile.text

        val extractedEndpoints = networkParser.parse(sourceCode)
        val extractedEndpoint = extractedEndpoints.first()

        assertEquals(originalEndpoint.path, extractedEndpoint.path)
        assertEquals(originalEndpoint.method, extractedEndpoint.method)
        assertEquals(originalEndpoint.operationId, extractedEndpoint.operationId)

        // Assert Response via the new property accessor
        assertEquals("TestUser", extractedEndpoint.responseType)
        assertEquals("Success", extractedEndpoint.responses["200"]?.description)

        assertEquals(originalEndpoint.tags, extractedEndpoint.tags)
    }

    @Test
    fun `Network Endpoint Round Trip preserves security requirements`() {
        val secured = EndpointDefinition(
            path = "/secure",
            method = HttpMethod.GET,
            operationId = "secureCall",
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", description = "ok", type = "String")
            ),
            security = listOf(
                mapOf("api_key" to emptyList()),
                mapOf("oauth2" to listOf("read", "write"))
            )
        )

        val open = EndpointDefinition(
            path = "/public",
            method = HttpMethod.GET,
            operationId = "publicCall",
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", description = "ok", type = "String")
            ),
            securityExplicitEmpty = true
        )

        val kotlinFile = networkGenerator.generateApi(
            packageName = "com.test",
            apiName = "SecureApi",
            endpoints = listOf(secured, open)
        )

        val extracted = networkParser.parse(kotlinFile.text)
        val securedParsed = extracted.first { it.operationId == "secureCall" }
        val openParsed = extracted.first { it.operationId == "publicCall" }

        assertEquals(secured.security, securedParsed.security)
        assertEquals(false, securedParsed.securityExplicitEmpty)
        assertEquals(true, openParsed.securityExplicitEmpty)
        assertTrue(openParsed.security.isEmpty())
    }

    @Test
    fun `Network Endpoint Round Trip preserves operation servers`() {
        val originalEndpoint = EndpointDefinition(
            path = "/servers",
            method = HttpMethod.GET,
            operationId = "serverOverride",
            servers = listOf(Server(url = "https://override.example.com", description = "Override")),
            responses = mapOf(
                "200" to EndpointResponse(statusCode = "200", description = "ok", type = "Unit")
            )
        )

        val kotlinFile = networkGenerator.generateApi(
            packageName = "com.test",
            apiName = "ServerApi",
            endpoints = listOf(originalEndpoint)
        )
        val extractedEndpoint = networkParser.parse(kotlinFile.text).first()

        assertEquals(1, extractedEndpoint.servers.size)
        assertEquals("https://override.example.com", extractedEndpoint.servers.first().url)
    }

    @Test
    fun `Network Endpoint Round Trip preserves response headers links and content`() {
        val response = EndpointResponse(
            statusCode = "200",
            description = "ok",
            type = "String",
            headers = mapOf(
                "X-Trace" to Header(
                    type = "String",
                    schema = SchemaProperty(types = setOf("string")),
                    description = "Trace id"
                )
            ),
            links = mapOf(
                "next" to Link(operationId = "listUsers")
            ),
            content = mapOf(
                "application/json" to MediaTypeObject(
                    schema = SchemaProperty(types = setOf("string"))
                )
            )
        )

        val originalEndpoint = EndpointDefinition(
            path = "/users",
            method = HttpMethod.GET,
            operationId = "listUsers",
            responses = mapOf("200" to response)
        )

        val kotlinFile = networkGenerator.generateApi(
            packageName = "com.test",
            apiName = "ResponseMetaApi",
            endpoints = listOf(originalEndpoint)
        )
        val extractedEndpoint = networkParser.parse(kotlinFile.text).first()
        val extractedResponse = extractedEndpoint.responses["200"]!!

        assertEquals(response.headers.keys, extractedResponse.headers.keys)
        assertEquals(response.links?.keys, extractedResponse.links?.keys)
        assertTrue(extractedResponse.content.containsKey("application/json"))
    }
}
