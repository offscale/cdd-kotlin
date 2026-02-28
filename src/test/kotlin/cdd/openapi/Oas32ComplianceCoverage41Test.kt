package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage41Test {
    @Test
    fun `validate servers direct`() {
        val validator = OpenApiValidator()
        
        val servers = listOf(
            Server(name = "s1", url = "http://a?b=c"),
            Server(name = "s1", url = "http://a#frag"),
            Server(name = "s2", url = "http://{a}/{a}", variables = mapOf("a" to ServerVariable(default = "x"))),
            Server(name = "s3", url = "http://{a}")
        )
        
        val m = OpenApiValidator::class.java.getDeclaredMethod("validateServers", List::class.java, String::class.java, MutableList::class.java)
        m.isAccessible = true
        
        val issues = mutableListOf<OpenApiIssue>()
        m.invoke(validator, servers, "path", issues)
        
        assertTrue(issues.any { it.message.contains("must be unique within the server list") })
        assertTrue(issues.any { it.message.contains("must not include query or fragment") })
        assertTrue(issues.any { it.message.contains("must not appear more than once in the url") })
        assertTrue(issues.any { it.message.contains("uses variables but no variables map is defined") })
    }
}
