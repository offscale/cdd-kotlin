package cdd.shared

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class TypeMappersCoverageTest {
    @Test
    fun `inferTypeFromValue test`() {
        assertEquals("null", TypeMappers.inferTypeFromValue(null))
        assertEquals("string", TypeMappers.inferTypeFromValue("a"))
        assertEquals("integer", TypeMappers.inferTypeFromValue(1))
        assertEquals("integer", TypeMappers.inferTypeFromValue(1L))
        assertEquals("integer", TypeMappers.inferTypeFromValue(1.toShort()))
        assertEquals("integer", TypeMappers.inferTypeFromValue(1.toByte()))
        assertEquals("number", TypeMappers.inferTypeFromValue(1.0))
        assertEquals("number", TypeMappers.inferTypeFromValue(1.0f))
        assertEquals("boolean", TypeMappers.inferTypeFromValue(true))
        assertEquals("object", TypeMappers.inferTypeFromValue(mapOf<Any, Any>()))
        assertEquals("array", TypeMappers.inferTypeFromValue(listOf<Any>()))
        assertEquals(null, TypeMappers.inferTypeFromValue(Any()))
    }
}
