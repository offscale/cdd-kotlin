package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class Oas32ComplianceCoverage18Test {
    @Test
    fun `validate schema percentage percentDecode`() {
        assertEquals(" A", percentDecode("%20A"))
        assertEquals("A", percentDecode("A"))
        assertEquals("%2G", percentDecode("%2G"))
    }
}
