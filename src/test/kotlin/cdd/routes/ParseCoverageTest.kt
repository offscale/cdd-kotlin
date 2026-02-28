package cdd.routes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import com.fasterxml.jackson.databind.ObjectMapper

class ParseCoverageTest {
    @Test
    fun `parseEncodingNode covers nested and array items`() {
        val json = """
        {
            "encoding": {
                "a": {
                    "contentType": "text/plain",
                    "style": "form",
                    "explode": true,
                    "allowReserved": false,
                    "encoding": {
                        "b": { "contentType": "image/png" }
                    },
                    "prefixEncoding": [
                        { "contentType": "text/html" }
                    ],
                    "itemEncoding": {
                        "contentType": "application/json"
                    },
                    "headers": {
                        "h1": {
                            "description": "d",
                            "required": true,
                            "deprecated": false,
                            "style": "simple",
                            "explode": false
                        }
                    },
                    "x-test": "v"
                }
            }
        }
        """
        val node = ObjectMapper().readTree(json)
        val parser = NetworkParser()
        val m = NetworkParser::class.java.getDeclaredMethod("parseEncodingMapNode", com.fasterxml.jackson.databind.JsonNode::class.java)
        m.isAccessible = true
        val result = m.invoke(parser, node.get("encoding")) as Map<*, *>
        assertNotNull(result["a"])
    }

    @Test
    fun `parseLinkNode`() {
        val json = """
        {
            "link1": {
                "${'$'}ref": "#/components/links/l2",
                "description": "d",
                "summary": "s"
            },
            "link2": {
                "operationId": "op",
                "operationRef": "ref",
                "parameters": { "a": "b" },
                "requestBody": "{}",
                "description": "desc",
                "server": { "url": "http://a" }
            }
        }
        """
        val node = ObjectMapper().readTree(json)
        val parser = NetworkParser()
        val m = NetworkParser::class.java.getDeclaredMethod("parseLinkNode", com.fasterxml.jackson.databind.JsonNode::class.java)
        m.isAccessible = true
        assertNotNull(m.invoke(parser, node.get("link1")))
        assertNotNull(m.invoke(parser, node.get("link2")))
        assertNotNull(m.invoke(parser, ObjectMapper().createArrayNode()))
    }

    @Test
    fun `parseMediaTypeNode and others`() {
        val json = """
        {
            "mediaType1": {
                "${'$'}ref": "ref", "summary": "s", "description": "d"
            },
            "mediaType2": {
                "schema": { "type": "string" },
                "itemSchema": { "type": "string" },
                "example": { "value": "a" },
                "examples": { "ex1": { "value": "b" } },
                "encoding": { "e": { "contentType": "text/plain" } },
                "x-ext": "v"
            }
        }
        """
        val node = ObjectMapper().readTree(json)
        val parser = NetworkParser()
        val m = NetworkParser::class.java.getDeclaredMethod("parseMediaTypeNode", com.fasterxml.jackson.databind.JsonNode::class.java)
        m.isAccessible = true
        assertNotNull(m.invoke(parser, node.get("mediaType1")))
        assertNotNull(m.invoke(parser, node.get("mediaType2")))
        assertNotNull(m.invoke(parser, ObjectMapper().createArrayNode()))
    }
}
