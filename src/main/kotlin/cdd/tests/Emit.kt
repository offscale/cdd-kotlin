package cdd.tests

import cdd.openapi.EndpointDefinition

/** Generates Unit Tests for API operations. */
class TestsEmit {
    /** Emits a unit test class for the given endpoints. */
    fun emit(packageName: String, className: String, endpoints: List<EndpointDefinition>): String {
        val builder = StringBuilder()
        builder.appendLine("package $packageName\n")
        builder.appendLine("import kotlin.test.Test")
        builder.appendLine("import kotlin.test.assertTrue\n")
        builder.appendLine("/** Tests for $className */")
        builder.appendLine("class ${className}Test {")
        endpoints.forEach { ep ->
            val testName = "test ${ep.operationId}"
            builder.appendLine("    /** Test for ${ep.operationId} */")
            builder.appendLine("    @Test")
            builder.appendLine("    fun `$testName`() {")
            builder.appendLine("        // TODO: Implement test logic")
            builder.appendLine("        assertTrue(true)")
            builder.appendLine("    }")
        }
        builder.appendLine("}")
        return builder.toString()
    }
}
