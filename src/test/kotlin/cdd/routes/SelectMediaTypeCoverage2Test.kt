package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class SelectMediaTypeCoverage2Test {
    @Test
    fun `selectPreferredMediaTypeEntry full coverage`() {
        val gen = NetworkGenerator()
        
        val c1 = mapOf(
            "text/plain" to MediaTypeObject(),
            "application/json" to MediaTypeObject()
        )
        assertEquals("application/json", gen.selectPreferredMediaTypeEntry(c1).key)
        
        val c2 = mapOf(
            "*/*" to MediaTypeObject(),
            "text/*" to MediaTypeObject(),
            "text/plain" to MediaTypeObject()
        )
        assertEquals("text/plain", gen.selectPreferredMediaTypeEntry(c2).key)
        
        val c3 = mapOf(
            "application/xml" to MediaTypeObject(),
            "application/json" to MediaTypeObject()
        )
        assertEquals("application/json", gen.selectPreferredMediaTypeEntry(c3).key)
        
        val c4 = mapOf(
            "application/vnd.api+json" to MediaTypeObject(),
            "application/json" to MediaTypeObject()
        )
        
        val c5 = mapOf(
            "application/json" to MediaTypeObject(),
            "application/hal+json" to MediaTypeObject()
        )
        println(gen.selectPreferredMediaTypeEntry(c5).key)
    }
}
