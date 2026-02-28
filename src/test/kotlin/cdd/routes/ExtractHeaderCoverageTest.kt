package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class ExtractHeaderCoverageTest {
    @Test
    fun `extractHeaderDefaultValue coverage`() {
        val gen = NetworkGenerator()
        
        val h1 = Header(type = "string", schema = SchemaProperty(types = setOf("string"), defaultValue = "v"))
        assertEquals("v", gen.extractHeaderDefaultValue(h1))
        
        val h2 = Header(type = "string", schema = SchemaProperty(types = setOf("string"), enumValues = listOf("e1")))
        assertEquals("e1", gen.extractHeaderDefaultValue(h2))
        
        val h3 = Header(type = "string", schema = SchemaProperty(types = setOf("string")))
        assertNull(gen.extractHeaderDefaultValue(h3))
        
        val h4 = Header(type = "string")
        assertNull(gen.extractHeaderDefaultValue(h4))
    }
}
