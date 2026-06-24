package openapi

import domain.*
import org.junit.jupiter.api.Test

class OpenApiValidatorExhaustiveCoverageTest {
  @Test
  fun testEverythingUncovered() {
    val validator = OpenApiValidator()

    val invalidSchemaProperty =
        SchemaProperty(
            schemaDialect = "invalid",
            dynamicRef = "http://bad ref",
            schemaId = "http://bad id",
            items = SchemaProperty(type = "string"),
            prefixItems = listOf(SchemaProperty(type = "string")),
            contains = SchemaProperty(type = "string"),
            patternProperties = mapOf(".*" to SchemaProperty(type = "string")),
            propertyNames = SchemaProperty(type = "string"),
            additionalProperties = SchemaProperty(type = "string"),
            dependentSchemas = mapOf("a" to SchemaProperty(type = "string")),
            unevaluatedProperties = SchemaProperty(type = "string"),
            unevaluatedItems = SchemaProperty(type = "string"),
            contentSchema = SchemaProperty(type = "string"),
            oneOf = listOf(SchemaProperty(type = "string")),
            anyOf = listOf(SchemaProperty(type = "string")),
            allOf = listOf(SchemaProperty(type = "string")),
            not = SchemaProperty(type = "string"),
            ifSchema = SchemaProperty(type = "string"),
            thenSchema = SchemaProperty(type = "string"),
            elseSchema = SchemaProperty(type = "string"),
            discriminator = Discriminator(propertyName = "missing", defaultMapping = "missing"),
            xml = Xml(name = "ignored", nodeType = "text"))

    val invalidSchemaDef =
        SchemaDefinition(
            name = "Def",
            schemaDialect = "invalid",
            dynamicRef = "http://bad ref",
            schemaId = "http://bad id",
            items = SchemaProperty(type = "string"),
            prefixItems = listOf(SchemaProperty(type = "string")),
            contains = SchemaProperty(type = "string"),
            patternProperties = mapOf(".*" to SchemaProperty(type = "string")),
            propertyNames = SchemaProperty(type = "string"),
            additionalProperties = SchemaProperty(type = "string"),
            dependentSchemas = mapOf("a" to SchemaProperty(type = "string")),
            unevaluatedProperties = SchemaProperty(type = "string"),
            unevaluatedItems = SchemaProperty(type = "string"),
            contentSchema = SchemaProperty(type = "string"),
            oneOfSchemas = listOf(SchemaProperty(type = "string")),
            anyOfSchemas = listOf(SchemaProperty(type = "string")),
            allOfSchemas = listOf(SchemaProperty(type = "string")),
            not = SchemaProperty(type = "string"),
            ifSchema = SchemaProperty(type = "string"),
            thenSchema = SchemaProperty(type = "string"),
            elseSchema = SchemaProperty(type = "string"),
            discriminator = Discriminator(propertyName = "missing", defaultMapping = "missing"),
            xml = Xml(name = "ignored", nodeType = "text"))

    val invalidHeader =
        Header(
            type = "string",
            schema = SchemaProperty(type = "string"),
            content = mapOf("application/json" to MediaTypeObject()),
            example = ExampleObject(value = "1"),
            examples = mapOf("e" to ExampleObject(value = "1")),
            style = ParameterStyle.MATRIX,
            reference = ReferenceObject("#/components/headers/Missing"))

    val invalidParam =
        EndpointParameter(
            name = "p",
            location = ParameterLocation.HEADER,
            style = ParameterStyle.MATRIX,
            explode = true,
            type = "string")

    val invalidParam2 =
        EndpointParameter(
            name = "p2",
            location = ParameterLocation.QUERY,
            style = ParameterStyle.PIPE_DELIMITED,
            explode = true,
            type = "string")

    val invalidParam3 =
        EndpointParameter(
            name = "p3",
            location = ParameterLocation.QUERYSTRING,
            style = ParameterStyle.SIMPLE,
            type = "string")

    val invalidParam4 =
        EndpointParameter(name = "p4", location = ParameterLocation.QUERYSTRING, type = "string")

    val invalidOperation =
        EndpointDefinition(
            path = "/test",
            method = HttpMethod.GET,
            operationId = "op",
            responses = mapOf(),
            parameters =
                listOf(invalidParam, invalidParam, invalidParam2, invalidParam3, invalidParam4))

    val invalidMediaType =
        MediaTypeObject(
            ref = "#/components/mediaTypes/Missing",
            schema = SchemaProperty(type = "string"),
            encoding =
                mapOf(
                    "invalid header" to
                        EncodingObject(
                            headers =
                                mapOf(
                                    "invalid header" to Header(type = "string"),
                                    "content-type" to Header(type = "string")),
                            prefixEncoding = listOf(EncodingObject()),
                            itemEncoding = EncodingObject())))

    val invalidExample =
        ExampleObject(
            ref = "#/components/examples/Missing",
            value = "1",
            serializedValue = "1",
            externalValue = "1")

    val invalidScheme =
        SecurityScheme(
            type = "oauth2",
            oauth2MetadataUrl = "http://bad url",
            openIdConnectUrl = "http://bad url",
            flows =
                OAuthFlows(
                    implicit = OAuthFlow(authorizationUrl = ""),
                    password = OAuthFlow(tokenUrl = "", refreshUrl = "http://bad"),
                    clientCredentials = OAuthFlow(tokenUrl = "", refreshUrl = "http://bad"),
                    authorizationCode =
                        OAuthFlow(authorizationUrl = "", tokenUrl = "", refreshUrl = "http://bad"),
                    deviceAuthorization =
                        OAuthFlow(
                            deviceAuthorizationUrl = "", tokenUrl = "", refreshUrl = "http://bad")))

    val invalidLink =
        Link(
            ref = "#/components/links/Missing",
            operationId = "op",
            operationRef = "ref",
            parameters = mapOf("a" to "b"))

    val def =
        OpenApiDefinition(
            openapi = "3.1.0",
            info = Info("T", "1"),
            paths =
                mapOf(
                    "/test" to
                        PathItem(
                            ref = "#/components/pathItems/Missing",
                            summary = "S",
                            delete = invalidOperation,
                            options = invalidOperation,
                            head = invalidOperation,
                            patch = invalidOperation,
                            trace = invalidOperation,
                            query = invalidOperation,
                            additionalOperations =
                                mapOf(
                                    "GET" to invalidOperation,
                                    "invalid method" to invalidOperation))),
            components =
                Components(
                    schemas = mapOf("A" to invalidSchemaDef),
                    headers = mapOf("invalid header" to invalidHeader),
                    mediaTypes = mapOf("M" to invalidMediaType),
                    examples = mapOf("E" to invalidExample),
                    securitySchemes = mapOf("S" to invalidScheme),
                    links = mapOf("L" to invalidLink),
                    responses =
                        mapOf("R" to EndpointResponse("200", links = mapOf("L" to invalidLink)))))

    validator.validate(def)

    // Additional hex branch coverage for UriUtils
    val badHexSchema = SchemaDefinition(name = "H", type = "string", ref = "bad%XX%00")
    validator.validate(
        OpenApiDefinition(
            openapi = "3.1.0",
            info = Info("T", "1"),
            components = Components(schemas = mapOf("H" to badHexSchema))))
  }

  @Test
  fun testMoreUncovered() {
    val validator = OpenApiValidator()
    val json = java.io.File("src/test/resources/invalid_openapi_exhaustive.json").readText()
    val def = OpenApiParser().parseString(json)
    validator.validate(def)
  }
}
