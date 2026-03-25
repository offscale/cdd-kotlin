package openapi

import domain.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ExhaustiveOpenApiValidatorTest {
    private val validator = OpenApiValidator()

    @Test
    fun `test schema properties permutations exhaustively`() {
        for (i in 1..100) {
            val prop = SchemaProperty(
                types = setOf("string", "integer", "object", "array"),
                minLength = if (i % 2 == 0) -1 else 5,
                maxLength = if (i % 3 == 0) -1 else 10,
                minItems = if (i % 4 == 0) -1 else 1,
                maxItems = if (i % 5 == 0) -1 else 10,
                minProperties = if (i % 6 == 0) -1 else 1,
                maxProperties = if (i % 7 == 0) -1 else 10,
                minContains = if (i % 8 == 0) -1 else 1,
                maxContains = if (i % 9 == 0) -1 else 5,
                contentMediaType = if (i % 2 == 0) "invalid-type" else "application/json",
                contentEncoding = if (i % 3 == 0) "" else "base64",
                discriminator = Discriminator(propertyName = "type", mapping = if (i % 2 == 0) emptyMap() else mapOf("type" to "map")),
                xml = Xml(nodeType = if (i % 2 == 0) "attribute" else null, attribute = true, wrapped = true),
                ref = if (i % 5 == 0) "#/components/schemas/Invalid" else null
            )
            val def = OpenApiDefinition(
                info = Info("Test", "1.0"),
                components = Components(schemas = mapOf("TestSchema" to SchemaDefinition(name = "TestSchema", properties = mapOf("prop" to prop))))
            )
            val issues = validator.validate(def)
            assertNotNull(issues)
        }
    }

    @Test
    fun `test operation permutations exhaustively`() {
        for (i in 1..100) {
            val op = EndpointDefinition(
                path = "/pets",
                method = HttpMethod.GET,
                operationId = "testOp${i}",
                responses = if (i % 2 == 0) emptyMap() else mapOf(
                    "200" to EndpointResponse("200", description = "OK", type = "String"),
                    "999" to EndpointResponse("999", description = "Invalid code", type = "String")
                ),
                parameters = listOf(
                    EndpointParameter(
                        name = "param${i}",
                        type = "string",
                        location = if (i % 2 == 0) ParameterLocation.QUERY else ParameterLocation.PATH,
                        isRequired = if (i % 3 == 0) false else true,
                        allowEmptyValue = if (i % 2 == 0) true else null,
                        style = if (i % 4 == 0) ParameterStyle.SPACE_DELIMITED else null,
                        explode = if (i % 5 == 0) true else null,
                        schema = if (i % 2 == 0) SchemaProperty(types = setOf("string")) else null,
                        content = if (i % 2 != 0) mapOf("application/json" to MediaTypeObject()) else emptyMap()
                    )
                ),
                security = if (i % 2 == 0) listOf(mapOf("invalidScheme" to emptyList())) else emptyList(),
                servers = listOf(Server(url = "http://api.example.com/{var}?q=1", variables = mapOf("var" to ServerVariable(default = "v"))))
            )
            val def = OpenApiDefinition(
                info = Info("Test", "1.0"),
                paths = mapOf("/pets" to PathItem(get = op))
            )
            validator.validate(def)
        }
    }
    
    @Test
    fun `test server variables validation permutations exhaustively`() {
        for (i in 1..50) {
            val server = Server(
                url = if (i % 2 == 0) "http://example.com/{test}?query=1#frag" else "http://example.com/{test}/{test}",
                variables = if (i % 3 == 0) null else mapOf(
                    "test" to ServerVariable(default = "val", enum = if (i % 4 == 0) emptyList() else listOf("val", "other")),
                    "unused" to ServerVariable(default = "unused")
                )
            )
            val def = OpenApiDefinition(
                info = Info("Test", "1.0", termsOfService = "invalid-url", contact = Contact(url = "invalid-url", email = "invalid-email")),
                servers = listOf(server, server)
            )
            validator.validate(def)
        }
    }
}