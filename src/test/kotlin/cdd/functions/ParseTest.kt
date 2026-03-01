package cdd.functions

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

/** Auto generated docs */
class FunctionsParseTest {
    @Test
    /** Auto generated docs */
    fun `test parse`() {
        val instance = FunctionsParse()
        val funcCode = """
            suspend fun handleGetUsers() {
        """.trimIndent()
        val res = instance.parse(funcCode)
        assertTrue(res.isNotEmpty())
    }
}
