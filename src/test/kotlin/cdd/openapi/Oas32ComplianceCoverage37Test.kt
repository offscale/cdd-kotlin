package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage37Test {
    @Test
    fun `validate base resolution`() {
        val validator = OpenApiValidator()
        
        // Let's call resolveReferenceBase directly via reflection
        val m = OpenApiValidator::class.java.getDeclaredMethod("resolveReferenceBase", String::class.java)
        m.isAccessible = true
        
        // This simulates currentSelfBase = null state
        assertTrue(m.invoke(validator, null) == null)
        assertTrue(m.invoke(validator, "  ") == null)
        
        // Absolute URL
        assertTrue(m.invoke(validator, "http://absolute.com") == "http://absolute.com")
    }
}
