package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage29Test {
    @Test
    fun `validate runtime expression extraction directly`() {
        val validator = OpenApiValidator()
        val m1 = OpenApiValidator::class.java.getDeclaredMethod("extractRuntimeExpression", String::class.java)
        m1.isAccessible = true
        
        assertTrue(m1.invoke(validator, "   ") == null)
        assertTrue(m1.invoke(validator, "{}") == null)
        assertTrue(m1.invoke(validator, "{ \$url }") == "\$url")
        assertTrue(m1.invoke(validator, "\$url") == "\$url")
        
        val m2 = OpenApiValidator::class.java.getDeclaredMethod("extractEmbeddedRuntimeExpressions", String::class.java)
        m2.isAccessible = true
        
        val l1 = m2.invoke(validator, "\$url") as List<*>
        assertTrue(l1.size == 1 && l1[0] == "\$url")
        
        val l2 = m2.invoke(validator, "hello {\$request.path.id} world") as List<*>
        assertTrue(l2.size == 1 && l2[0] == "\$request.path.id")
    }
}
