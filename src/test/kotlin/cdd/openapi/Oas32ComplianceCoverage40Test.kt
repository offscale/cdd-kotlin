package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage40Test {
    @Test
    fun `validate callbacks directly`() {
        val validator = OpenApiValidator()
        
        val c = Components(
            callbacks = mapOf(
                "c1" to Callback.Inline(expressions = mapOf("{\\\$request.body}" to PathItem())),
                "c2" to Callback.Reference(reference = ReferenceObject(ref = "#/components/callbacks/c1"))
            )
        )
        
        val issues = mutableListOf<OpenApiIssue>()
        validator.validateCallbacks(c.callbacks, "path", issues, c)
        
        // Also a bad reference
        validator.validateCallbacks(mapOf("bad" to Callback.Reference(reference = ReferenceObject(ref = "#/components/callbacks/missing"))), "path", issues, c)
        
        assertTrue(issues.any { it.message.contains("could not be resolved") })
    }
}
