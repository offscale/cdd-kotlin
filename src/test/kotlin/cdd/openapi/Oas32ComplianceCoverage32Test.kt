package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import com.fasterxml.jackson.databind.ObjectMapper

class Oas32ComplianceCoverage32Test {
    @Test
    fun `infer enum type`() {
        val parser = OpenApiParser()
        val m = OpenApiParser::class.java.getDeclaredMethod("inferEnumType", com.fasterxml.jackson.databind.JsonNode::class.java)
        m.isAccessible = true
        
        val mapper = ObjectMapper()
        
        // Single types
        assertEquals("string", m.invoke(parser, mapper.readTree("""["a"]""")))
        assertEquals("number", m.invoke(parser, mapper.readTree("""[1]""")))
        assertEquals("boolean", m.invoke(parser, mapper.readTree("""[true]""")))
        assertEquals("null", m.invoke(parser, mapper.readTree("""[null]""")))
        assertEquals("array", m.invoke(parser, mapper.readTree("""[[]]""")))
        assertEquals("object", m.invoke(parser, mapper.readTree("""[{}]""")))
        
        // Mixed types return null or dominant
        assertNull(m.invoke(parser, mapper.readTree("""["a", 1]""")))
        
        // Empty
        assertNull(m.invoke(parser, mapper.readTree("""[]""")))
        assertNull(m.invoke(parser, null))
    }
}
