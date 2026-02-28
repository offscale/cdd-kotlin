package cdd.openapi

import org.junit.jupiter.api.Test

class Oas32ComplianceCoverage24Test {
    @Test
    fun `validate examples directly`() {
        val validator = OpenApiValidator()
        val c = Components(
            examples = mapOf(
                "ex1" to ExampleObject(ref = "#/components/examples/ex2", value = "a"),
                "ex2" to ExampleObject(dataValue = mapOf("a" to "b"), value = "a"),
                "ex3" to ExampleObject(serializedValue = "a", value = "a"),
                "ex4" to ExampleObject(ref = "#/components/examples/ex2", extensions = mapOf("x-ext" to "a"))
            )
        )
        val def = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = c)
        val i = validator.validate(def)
        i.forEach { println(it.message) }
    }
}
