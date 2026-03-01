package cdd.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

/** Auto generated docs */
class TestsParseTest {
    @Test
    /** Auto generated docs */
    fun `test parse`() {
        val instance = TestsParse()
        val testCode = "fun `test getUsers`() {"
        val res = instance.parse(testCode)
        assertTrue(res.isNotEmpty())
    }
}
