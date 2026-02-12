package openapi

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import domain.Components
import domain.Info
import domain.OpenApiDefinition
import domain.SchemaDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiSchemaBooleanRefTest {

    private val parser = OpenApiParser()
    private val writer = OpenApiWriter()
    private val jsonMapper = ObjectMapper(JsonFactory())

    @Test
    fun `parse supports boolean schemas refs and numeric constraints`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Test", "version": "1.0" },
              "components": {
                "schemas": {
                  "AnyValue": true,
                  "NoValue": false,
                  "Numeric": {
                    "type": "number",
                    "multipleOf": 2,
                    "exclusiveMinimum": 1,
                    "exclusiveMaximum": 9
                  },
                  "Alias": { "${'$'}ref": "#/components/schemas/Numeric" }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json)
        val schemas = definition.components?.schemas
        assertNotNull(schemas)

        val anySchema = schemas?.get("AnyValue")
        val noSchema = schemas?.get("NoValue")
        val numeric = schemas?.get("Numeric")
        val alias = schemas?.get("Alias")

        assertEquals(true, anySchema?.booleanSchema)
        assertEquals(false, noSchema?.booleanSchema)
        assertEquals(2.0, numeric?.multipleOf)
        assertEquals(1.0, numeric?.exclusiveMinimum)
        assertEquals(9.0, numeric?.exclusiveMaximum)
        assertEquals("#/components/schemas/Numeric", alias?.ref)
    }

    @Test
    fun `write emits boolean schemas refs and numeric constraints`() {
        val components = Components(
            schemas = mapOf(
                "AnyValue" to SchemaDefinition(
                    name = "AnyValue",
                    type = "object",
                    booleanSchema = true
                ),
                "NoValue" to SchemaDefinition(
                    name = "NoValue",
                    type = "object",
                    booleanSchema = false
                ),
                "Numeric" to SchemaDefinition(
                    name = "Numeric",
                    type = "number",
                    multipleOf = 2.0,
                    exclusiveMinimum = 1.0,
                    exclusiveMaximum = 9.0
                ),
                "Alias" to SchemaDefinition(
                    name = "Alias",
                    type = "object",
                    ref = "#/components/schemas/Numeric"
                )
            )
        )

        val definition = OpenApiDefinition(
            info = Info(title = "Test", version = "1.0"),
            components = components
        )

        val json = writer.writeJson(definition)
        val root = jsonMapper.readTree(json)
        val schemas = root["components"]["schemas"]

        assertTrue(schemas["AnyValue"].booleanValue())
        assertFalse(schemas["NoValue"].booleanValue())
        assertEquals("#/components/schemas/Numeric", schemas["Alias"]["${'$'}ref"].asText())
        assertEquals(2.0, schemas["Numeric"]["multipleOf"].asDouble())
        assertEquals(1.0, schemas["Numeric"]["exclusiveMinimum"].asDouble())
        assertEquals(9.0, schemas["Numeric"]["exclusiveMaximum"].asDouble())
    }
}
