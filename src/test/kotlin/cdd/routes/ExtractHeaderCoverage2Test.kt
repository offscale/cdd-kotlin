package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class ExtractHeaderCoverage2Test {
    @Test
    fun `extractHeaderExampleValue coverage`() {
        val gen = NetworkGenerator()
        
        val h1 = Header(type = "string", example = ExampleObject(serializedValue = "s"))
        assertEquals("s", gen.extractHeaderExampleValue(h1))
        
        val h2 = Header(type = "string", examples = mapOf("ex" to ExampleObject(dataValue = "d")))
        assertEquals("d", gen.extractHeaderExampleValue(h2))
        
        val h3 = Header(type = "string", examples = mapOf("ex" to ExampleObject(value = "v")))
        assertEquals("v", gen.extractHeaderExampleValue(h3))
        
        val h4 = Header(type = "string")
        assertNull(gen.extractHeaderExampleValue(h4))
    }
}
