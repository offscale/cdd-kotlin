package cdd.classes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ReferenceResolverCoverageTest {
    @Test
    fun `hexToInt resolves completely`() {
        assertEquals(0, ReferenceResolver.hexToInt('0'))
        assertEquals(9, ReferenceResolver.hexToInt('9'))
        assertEquals(10, ReferenceResolver.hexToInt('a'))
        assertEquals(15, ReferenceResolver.hexToInt('f'))
        assertEquals(10, ReferenceResolver.hexToInt('A'))
        assertEquals(15, ReferenceResolver.hexToInt('F'))
        assertEquals(-1, ReferenceResolver.hexToInt('G'))
    }
}
