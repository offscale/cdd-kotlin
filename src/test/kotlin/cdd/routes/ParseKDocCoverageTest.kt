package cdd.routes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertEquals

class ParseKDocCoverageTest {
    @Test
    fun `extractExternalDocsLine coverage`() {
        val parser = NetworkParser()
        val m = NetworkParser::class.java.getDeclaredMethod("extractExternalDocsLine", List::class.java)
        m.isAccessible = true
        
        assertNull(m.invoke(parser, listOf("@externalDocs ")))
        assertNull(m.invoke(parser, listOf("@externalDocs []")))
        assertNotNull(m.invoke(parser, listOf("@externalDocs {\"url\":\"http://a\"}")))
        
        val doc = m.invoke(parser, listOf("@see http://a desc")) as cdd.openapi.ExternalDocumentation
        assertEquals("http://a", doc.url)
        assertEquals("desc", doc.description)
    }

    @Test
    fun `extractPathParamNameFromExpression coverage`() {
        val parser = NetworkParser()
        val m = NetworkParser::class.java.getDeclaredMethod("extractPathParamNameFromExpression", String::class.java)
        m.isAccessible = true
        
        assertNull(m.invoke(parser, "   "))
        assertEquals("id", m.invoke(parser, "encodePathComponent(id.toString(), false)"))
        assertEquals("obj", m.invoke(parser, "serializeContentValue(obj, false)"))
        assertEquals("map", m.invoke(parser, "map.entries.joinToString()"))
        assertEquals("list", m.invoke(parser, "list.joinToString()"))
    }
}
