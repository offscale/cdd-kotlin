package verification

import domain.*
import psi.DtoGenerator
import psi.DtoParser
import psi.NetworkGenerator
import psi.NetworkParser
import psi.PsiInfrastructure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.AfterAll

/**
 * V-01: Round-Trip Test Suite.
 * Verifies that Spec A -> Code B -> Spec C results in Spec A == Spec C.
 */
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
        // Spec A
        val originalSchema = SchemaDefinition(
            name = "TestUser",
            type = "object",
            description = "A user model",
            required = listOf("id", "email"),
            properties = mapOf(
                "id" to SchemaProperty(type = "integer", format = "int64", description = "Unique ID"),
                "email" to SchemaProperty(type = "string"),
                "isActive" to SchemaProperty(type = "boolean", description = "Status flag")
            )
        )

        // Generate Code B
        val kotlinFile = dtoGenerator.generateDto(
            packageName = "com.test",
            definition = originalSchema
        )
        val sourceCode = kotlinFile.text

        // Parse Spec C
        val extractedSchemas = dtoParser.parse(sourceCode)
        assertEquals(1, extractedSchemas.size, "Should extract exactly one DTO")

        val extractedSchema = extractedSchemas.first()

        // Assert Spec A == Spec C
        assertEquals(originalSchema.name, extractedSchema.name)
        assertEquals(originalSchema.description, extractedSchema.description)

        // Check properties
        assertEquals(originalSchema.properties.size, extractedSchema.properties.size)
        originalSchema.properties.forEach { (key, originalProp) ->
            val extractedProp = extractedSchema.properties[key]
            assertEquals(originalProp.type, extractedProp?.type, "Type mismatch for $key")
            assertEquals(originalProp.description, extractedProp?.description, "Desc mismatch for $key")
        }
    }

    @Test
    fun `Network Endpoint Round Trip preserves structure`() {
        // Spec A
        val originalEndpoint = EndpointDefinition(
            path = "/users/{id}",
            method = HttpMethod.GET,
            operationId = "getUserById",
            summary = "Fetch user by ID",
            parameters = listOf(
                EndpointParameter(
                    name = "id",
                    type = "Long", // Long used in Kotlin, mapped from path variable
                    location = ParameterLocation.PATH,
                    isRequired = true // Path params always required
                ),
                EndpointParameter(
                    name = "detail",
                    type = "Boolean",
                    location = ParameterLocation.QUERY,
                    isRequired = true
                )
            ),
            responseType = "TestUser",
            requestBodyType = null
        )

        // Generate Code B
        // We use generateApi to get the Implementation class which Parser analyzes
        val kotlinFile = networkGenerator.generateApi(
            packageName = "com.test",
            apiName = "UserApi",
            endpoints = listOf(originalEndpoint)
        )
        val sourceCode = kotlinFile.text

        // Parse Spec C
        val extractedEndpoints = networkParser.parse(sourceCode)
        assertEquals(1, extractedEndpoints.size, "Should extract exactly one endpoint")

        val extractedEndpoint = extractedEndpoints.first()

        // Assert Spec A == Spec C
        assertEquals(originalEndpoint.path, extractedEndpoint.path)
        assertEquals(originalEndpoint.method, extractedEndpoint.method)
        assertEquals(originalEndpoint.operationId, extractedEndpoint.operationId)
        assertEquals(originalEndpoint.summary, extractedEndpoint.summary)
        assertEquals(originalEndpoint.responseType, extractedEndpoint.responseType)

        // Parameters
        assertEquals(originalEndpoint.parameters.size, extractedEndpoint.parameters.size)

        // Check specific parameters
        val idParam = extractedEndpoint.parameters.find { it.name == "id" }
        assertEquals(ParameterLocation.PATH, idParam?.location)
        assertEquals("Long", idParam?.type)

        val queryParam = extractedEndpoint.parameters.find { it.name == "detail" }
        assertEquals(ParameterLocation.QUERY, queryParam?.location)
    }
}
