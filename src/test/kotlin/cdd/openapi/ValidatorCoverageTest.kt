package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ValidatorCoverageTest {
    @Test
    fun `validate servers`() {
        val validator = OpenApiValidator()
        val issues = mutableListOf<OpenApiIssue>()
        validator.validateServers(listOf(Server("http://abc.com", "desc")), "base", issues)
    }
}
