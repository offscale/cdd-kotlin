package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage44Test {
    @Test
    fun `validate servers additional properties`() {
        val validator = OpenApiValidator()
        
        val servers = listOf(
            Server(name = "s1", url = "http://{a}?query#fragment", variables = mapOf("a" to ServerVariable(default="x"), "b" to ServerVariable(default="x"))),
            Server(name = "s2", url = "http://{a}{a}", variables = mapOf("a" to ServerVariable(default="x"))),
            Server(name = "s2", url = "http://a", variables = mapOf("a" to ServerVariable(default="x"))),
            Server(name = "s3", url = "http://a"),
            Server(url="http://{a}", variables=null)
        )
        
        val issues = mutableListOf<OpenApiIssue>()
        validator.validateServers(servers, "path", issues)
        
        assertTrue(issues.any { it.message.contains("must be unique within the server list") })
        assertTrue(issues.any { it.message.contains("must not include query or fragment") })
        assertTrue(issues.any { it.message.contains("must not appear more than once") })
        assertTrue(issues.any { it.message.contains("uses variables but no variables map is defined") })
    }
}
