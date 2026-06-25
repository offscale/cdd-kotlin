package openapi

import domain.*
import org.junit.jupiter.api.Test

class OpenApiValidatorDirectCoverageTest {
  @Test
  fun testDirect() {
    val nested =
        SchemaProperty(
            schemaDialect = "invalid",
            dynamicRef = "http://bad ref",
            schemaId = "http://bad id",
            ref = "http://bad ref",
            booleanSchema = true,
            items = SchemaProperty(type = "inner1"),
            prefixItems = listOf(SchemaProperty(type = "inner2")),
            contains = SchemaProperty(type = "inner3"),
            patternProperties = mapOf(".*" to SchemaProperty(type = "inner4")),
            propertyNames = SchemaProperty(type = "inner5"),
            additionalProperties = SchemaProperty(type = "inner6"),
            dependentSchemas = mapOf("a" to SchemaProperty(type = "inner7")),
            unevaluatedProperties = SchemaProperty(type = "inner8"),
            unevaluatedItems = SchemaProperty(type = "inner9"),
            contentSchema = SchemaProperty(type = "inner10"),
            oneOf = listOf(SchemaProperty(type = "inner11")),
            anyOf = listOf(SchemaProperty(type = "inner12")),
            allOf = listOf(SchemaProperty(type = "inner13")),
            not = SchemaProperty(type = "inner14"),
            ifSchema = SchemaProperty(type = "inner15"),
            thenSchema = SchemaProperty(type = "inner16"),
            elseSchema = SchemaProperty(type = "inner17"),
            properties = mapOf("p" to SchemaProperty(type = "inner18")),
            discriminator = Discriminator(propertyName = "missing", defaultMapping = "missing"),
            xml = Xml(name = "ignored", nodeType = "text"))

    val def = SchemaDefinition(name = "Test", properties = mapOf("p" to nested))

    val openApiDef =
        OpenApiDefinition(
            openapi = "3.1.0",
            info = Info("t", "1"),
            components = Components(schemas = mapOf("A" to def)))

    println("NESTED ITEMS: ${nested.items}")
    println("NESTED PREFIX ITEMS: ${nested.prefixItems}")
    OpenApiValidator().validate(openApiDef)
    println("SUCCESS")
  }
}
