package cdd.routes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import com.fasterxml.jackson.databind.ObjectMapper

class ParseCoverage4Test {
    @Test
    fun `parseExampleObjectNode tests`() {
        val parser = NetworkParser()
        val mapper = ObjectMapper()
        
        val ex1 = parser.parseExampleObjectNode(mapper.readTree("\"hello\""))
        assertEquals("hello", ex1.value)
        
        val ex2 = parser.parseExampleObjectNode(mapper.readTree("{\"\$ref\": \"myRef\", \"summary\": \"s\", \"description\": \"d\"}"))
        assertEquals("myRef", ex2.ref)
        assertEquals("s", ex2.summary)
        
        val ex3 = parser.parseExampleObjectNode(mapper.readTree("{\"summary\": \"s\", \"dataValue\": {}, \"serializedValue\": \"sv\", \"externalValue\": \"ev\", \"value\": 1}"))
        assertEquals("s", ex3.summary)
        assertNotNull(ex3.dataValue)
        assertEquals("sv", ex3.serializedValue)
        assertEquals("ev", ex3.externalValue)
        assertEquals(1, ex3.value)
    }
}
