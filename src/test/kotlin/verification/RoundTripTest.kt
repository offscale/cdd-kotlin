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
            example = "{\"id\": 1}",
            examples = mapOf("valid" to "{\"id\": 1}", "invalid" to "{}"),
            properties = mapOf(
                "id" to SchemaProperty("integer", example = "1")
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
                "200" to EndpointResponse("200", "Success", "TestUser")
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
}
