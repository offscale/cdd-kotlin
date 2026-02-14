package openapi

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class OpenApiSchemaTypeOmissionTest {

    private val parser = OpenApiParser()
    private val writer = OpenApiWriter()
    private val jsonMapper = ObjectMapper(JsonFactory())

    @Test
    fun `parse and write preserves omitted schema type`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Test", "version": "1.0" },
              "components": {
                "schemas": {
                  "Loose": {
                    "properties": {
                      "id": { "type": "string" }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json)
        val schema = definition.components?.schemas?.get("Loose")
        assertEquals("object", schema?.type)
        assertFalse(schema?.typeExplicit ?: true)

        val output = writer.writeJson(definition)
        val node = jsonMapper.readTree(output)
        val looseNode = node["components"]["schemas"]["Loose"]
        assertNull(looseNode.get("type"))
        assertEquals("string", looseNode["properties"]["id"]["type"].asText())
    }

    @Test
    fun `parse infers array type when items are present and type omitted`() {
        val json = """
            {
              "openapi": "3.2.0",
              "info": { "title": "Test", "version": "1.0" },
              "components": {
                "schemas": {
                  "Tuple": {
                    "prefixItems": [
                      { "type": "string" },
                      { "type": "integer" }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val definition = parser.parseString(json)
        val schema = definition.components?.schemas?.get("Tuple")
        assertEquals("array", schema?.type)
        assertFalse(schema?.typeExplicit ?: true)

        val output = writer.writeJson(definition)
        val node = jsonMapper.readTree(output)
        val tupleNode = node["components"]["schemas"]["Tuple"]
        assertNull(tupleNode.get("type"))
        assertEquals("string", tupleNode["prefixItems"][0]["type"].asText())
    }
}
