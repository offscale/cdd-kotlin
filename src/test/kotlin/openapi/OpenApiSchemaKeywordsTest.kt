package openapi

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import domain.Components
import domain.Info
import domain.OpenApiDefinition
import domain.SchemaDefinition
import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiSchemaKeywordsTest {

    private val parser = OpenApiParser()
    private val writer = OpenApiWriter()
    private val jsonMapper = ObjectMapper(JsonFactory())

    @Test
    fun `parse supports advanced json schema keywords`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Advanced", "version": "1.0" },
              "components": {
                "schemas": {
                  "Advanced": {
                    "type": "object",
                    "${"$"}comment": "Advanced schema comment",
                    "${"$"}dynamicRef": "#/components/schemas/Base",
                    "${"$"}defs": {
                      "PositiveInt": { "type": "integer", "minimum": 1 }
                    },
                    "if": {
                      "properties": { "country": { "const": "US" } }
                    },
                    "then": {
                      "required": ["state"],
                      "properties": { "state": { "type": "string" } }
                    },
                    "else": {
                      "required": ["province"],
                      "properties": { "province": { "type": "string" } }
                    },
                    "properties": {
                      "dynamic": { "${"$"}dynamicRef": "#/components/schemas/Other" },
                      "identity": {
                        "${"$"}id": "https://example.com/schemas/Identity",
                        "${"$"}schema": "https://json-schema.org/draft/2020-12/schema",
                        "${"$"}anchor": "identity",
                        "${"$"}dynamicAnchor": "identityDyn",
                        "type": "string"
                      }
                    },
                    "patternProperties": {
                      "^x-": { "type": "string" }
                    },
                    "propertyNames": { "type": "string", "pattern": "^[a-z]+$" },
                    "dependentRequired": {
                      "credit_card": ["billing_address"]
                    },
                    "dependentSchemas": {
                      "shipping_address": {
                        "type": "object",
                        "required": ["country"],
                        "properties": { "country": { "type": "string" } }
                      }
                    },
                    "unevaluatedProperties": false,
                    "unevaluatedItems": { "type": "string" },
                    "contentSchema": {
                      "type": "object",
                      "properties": { "id": { "type": "integer" } }
                    },
                    "additionalProperties": false
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val schema = definition.components?.schemas?.get("Advanced")
        assertNotNull(schema)

        assertEquals("string", schema?.patternProperties?.get("^x-")?.type)
        assertEquals("^[a-z]+$", schema?.propertyNames?.pattern)
        assertEquals(listOf("billing_address"), schema?.dependentRequired?.get("credit_card"))
        assertEquals(listOf("country"), schema?.dependentSchemas?.get("shipping_address")?.required)
        assertEquals("Advanced schema comment", schema?.comment)
        assertEquals("#/components/schemas/Base", schema?.dynamicRef)
        assertEquals("integer", schema?.defs?.get("PositiveInt")?.type)
        assertEquals("US", schema?.ifSchema?.properties?.get("country")?.constValue)
        assertEquals("state", schema?.thenSchema?.required?.first())
        assertEquals("province", schema?.elseSchema?.required?.first())
        assertEquals("#/components/schemas/Other", schema?.properties?.get("dynamic")?.dynamicRef)
        val identity = schema?.properties?.get("identity")
        assertEquals("https://example.com/schemas/Identity", identity?.schemaId)
        assertEquals("https://json-schema.org/draft/2020-12/schema", identity?.schemaDialect)
        assertEquals("identity", identity?.anchor)
        assertEquals("identityDyn", identity?.dynamicAnchor)
        assertEquals(false, schema?.unevaluatedProperties?.booleanSchema)
        assertEquals("string", schema?.unevaluatedItems?.type)
        assertEquals("integer", schema?.contentSchema?.properties?.get("id")?.type)
        assertEquals(false, schema?.additionalProperties?.booleanSchema)
    }

    @Test
    fun `write supports advanced json schema keywords`() {
        val schema = SchemaDefinition(
            name = "Advanced",
            type = "object",
            comment = "Advanced schema comment",
            dynamicRef = "#/components/schemas/Base",
            defs = mapOf("PositiveInt" to SchemaProperty(types = setOf("integer"), minimum = 1.0)),
            ifSchema = SchemaProperty(
                types = setOf("object"),
                properties = mapOf("country" to SchemaProperty("string", constValue = "US"))
            ),
            thenSchema = SchemaProperty(
                types = setOf("object"),
                required = listOf("state"),
                properties = mapOf("state" to SchemaProperty("string"))
            ),
            elseSchema = SchemaProperty(
                types = setOf("object"),
                required = listOf("province"),
                properties = mapOf("province" to SchemaProperty("string"))
            ),
            properties = mapOf(
                "dynamic" to SchemaProperty(dynamicRef = "#/components/schemas/Other"),
                "identity" to SchemaProperty(
                    types = setOf("string"),
                    schemaId = "https://example.com/schemas/Identity",
                    schemaDialect = "https://json-schema.org/draft/2020-12/schema",
                    anchor = "identity",
                    dynamicAnchor = "identityDyn"
                )
            ),
            patternProperties = mapOf("^x-" to SchemaProperty("string")),
            propertyNames = SchemaProperty("string", pattern = "^[a-z]+$"),
            dependentRequired = mapOf("credit_card" to listOf("billing_address")),
            dependentSchemas = mapOf(
                "shipping_address" to SchemaProperty(
                    types = setOf("object"),
                    required = listOf("country"),
                    properties = mapOf("country" to SchemaProperty("string"))
                )
            ),
            unevaluatedProperties = SchemaProperty(booleanSchema = false),
            unevaluatedItems = SchemaProperty("string"),
            contentSchema = SchemaProperty(
                types = setOf("object"),
                properties = mapOf("id" to SchemaProperty("integer"))
            ),
            additionalProperties = SchemaProperty(booleanSchema = false)
        )

        val definition = OpenApiDefinition(
            info = Info(title = "Advanced", version = "1.0"),
            components = Components(schemas = mapOf("Advanced" to schema))
        )

        val json = writer.writeJson(definition)
        val root = jsonMapper.readTree(json)
        val advanced = root["components"]["schemas"]["Advanced"]

        assertEquals("Advanced schema comment", advanced["\$comment"].asText())
        assertEquals("#/components/schemas/Base", advanced["\$dynamicRef"].asText())
        assertEquals("integer", advanced["\$defs"]["PositiveInt"]["type"].asText())
        assertEquals("US", advanced["if"]["properties"]["country"]["const"].asText())
        assertEquals("state", advanced["then"]["required"].first().asText())
        assertEquals("province", advanced["else"]["required"].first().asText())
        assertEquals("#/components/schemas/Other", advanced["properties"]["dynamic"]["\$dynamicRef"].asText())
        val identity = advanced["properties"]["identity"]
        assertEquals("https://example.com/schemas/Identity", identity["\$id"].asText())
        assertEquals("https://json-schema.org/draft/2020-12/schema", identity["\$schema"].asText())
        assertEquals("identity", identity["\$anchor"].asText())
        assertEquals("identityDyn", identity["\$dynamicAnchor"].asText())
        assertEquals("string", advanced["patternProperties"]["^x-"]["type"].asText())
        assertEquals("^[a-z]+$", advanced["propertyNames"]["pattern"].asText())
        assertTrue(advanced["dependentRequired"]["credit_card"].isArray)
        assertEquals("country", advanced["dependentSchemas"]["shipping_address"]["required"].first().asText())
        assertEquals(false, advanced["unevaluatedProperties"].booleanValue())
        assertEquals("string", advanced["unevaluatedItems"]["type"].asText())
        assertEquals("integer", advanced["contentSchema"]["properties"]["id"]["type"].asText())
        assertEquals(false, advanced["additionalProperties"].booleanValue())
    }

    @Test
    fun `parse preserves custom schema keywords`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Custom", "version": "1.0" },
              "components": {
                "schemas": {
                  "Custom": {
                    "type": "object",
                    "vendorKeyword": "alpha",
                    "threshold": 3,
                    "x-ext": "keep",
                    "properties": {
                      "name": {
                        "type": "string",
                        "custom": ["a", "b"]
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val schema = definition.components?.schemas?.get("Custom")
        assertNotNull(schema)
        assertEquals("alpha", schema?.customKeywords?.get("vendorKeyword"))
        assertEquals(3, (schema?.customKeywords?.get("threshold") as? Number)?.toInt())
        assertEquals("keep", schema?.extensions?.get("x-ext"))
        val nameProp = schema?.properties?.get("name")
        assertEquals(listOf("a", "b"), nameProp?.customKeywords?.get("custom"))
    }

    @Test
    fun `write preserves custom schema keywords`() {
        val schema = SchemaDefinition(
            name = "Custom",
            type = "object",
            customKeywords = mapOf(
                "vendorKeyword" to "alpha",
                "threshold" to 3
            ),
            properties = mapOf(
                "name" to SchemaProperty(
                    type = "string",
                    customKeywords = mapOf("custom" to listOf("a", "b"))
                )
            ),
            extensions = mapOf("x-ext" to "keep")
        )

        val definition = OpenApiDefinition(
            info = Info(title = "Custom", version = "1.0"),
            components = Components(schemas = mapOf("Custom" to schema))
        )

        val json = writer.writeJson(definition)
        val root = jsonMapper.readTree(json)
        val custom = root["components"]["schemas"]["Custom"]
        assertEquals("alpha", custom["vendorKeyword"].asText())
        assertEquals(3, custom["threshold"].asInt())
        assertEquals("keep", custom["x-ext"].asText())
        assertEquals("a", custom["properties"]["name"]["custom"][0].asText())
    }

    @Test
    fun `parse maps legacy nullable keywords to null types`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Legacy", "version": "1.0" },
              "components": {
                "schemas": {
                  "Legacy": {
                    "type": "object",
                    "nullable": true,
                    "properties": {
                      "count": {
                        "type": "integer",
                        "x-nullable": true
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json, OpenApiParser.Format.JSON)
        val schema = definition.components?.schemas?.get("Legacy")
        assertNotNull(schema)
        assertTrue(schema?.types?.contains("null") == true)
        assertTrue(schema?.properties?.get("count")?.types?.contains("null") == true)
    }
}
