package cdd.routes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class ExtractListElementTypeCoverageTest {
    @Test
    fun `extractListElementType test`() {
        val gen = NetworkGenerator()
        assertEquals("String", gen.extractListElementType("List<String>"))
        assertEquals("Int", gen.extractListElementType("List<Int>?"))
        assertEquals("Float", gen.extractListElementType("MutableList<Float>"))
        assertEquals("Boolean", gen.extractListElementType("MutableList<Boolean>?"))
        assertNull(gen.extractListElementType("String"))
        assertNull(gen.extractListElementType("List<String"))
    }
}
