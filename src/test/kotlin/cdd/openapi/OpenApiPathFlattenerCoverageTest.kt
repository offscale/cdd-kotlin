package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class OpenApiPathFlattenerCoverageTest {
    @Test
    fun `mergeExtensions test`() {
        val flattener = OpenApiPathFlattener
        val empty = flattener.mergeExtensions(emptyMap(), emptyMap())
        assertEquals(0, empty.size)
        
        val base = mapOf("x-a" to "1", "x-b" to "2")
        val over = mapOf("x-b" to "3", "x-c" to "4")
        val merged = flattener.mergeExtensions(base, over)
        assertEquals("1", merged["x-a"])
        assertEquals("3", merged["x-b"])
        assertEquals("4", merged["x-c"])
    }
}
