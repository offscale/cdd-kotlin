package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage42Test {
    @Test
    fun `validate schema constraints direct`() {
        val validator = OpenApiValidator()
        val m = OpenApiValidator::class.java.getDeclaredMethod("validateSchemaConstraints", Boolean::class.javaObjectType, Set::class.java, Int::class.javaObjectType, Int::class.javaObjectType, Int::class.javaObjectType, Int::class.javaObjectType, Int::class.javaObjectType, Int::class.javaObjectType, Int::class.javaObjectType, Int::class.javaObjectType, SchemaProperty::class.java, String::class.java, String::class.java, String::class.java, MutableList::class.java)
        m.isAccessible = true
        
        val issues = mutableListOf<OpenApiIssue>()
        m.invoke(validator, null, setOf("string"), -1, -2, -3, -4, -5, -6, -7, -8, null, "bad/media", "  ", "path", issues)
        m.invoke(validator, null, setOf("string"), 2, 1, 2, 1, 2, 1, 2, 1, null, "bad/media", "  ", "path", issues)
        m.invoke(validator, null, setOf("number"), null, null, null, null, null, null, null, null, null, "a/b", "base64", "path", issues)

        assertTrue(issues.any { it.message.contains("must be greater than or equal to 0") })
        assertTrue(issues.any { it.message.contains("must be less than or equal to") })
        assertTrue(issues.any { it.message.contains("ignored without a contains schema") })
        assertTrue(issues.any { it.message.contains("contentEncoding must not be blank") })
        assertTrue(issues.any { it.message.contains("contentEncoding is only applicable to string schemas") })
    }
}
