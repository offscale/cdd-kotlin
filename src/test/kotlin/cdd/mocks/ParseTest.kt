package cdd.mocks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

/** Auto generated docs */
class MocksParseTest {
    @Test
    /** Auto generated docs */
    fun `test parse`() {
        val instance = MocksParse()
        val mockCode = """
            path.matches(Regex("^/users/(.+)$")) && method == HttpMethod.GET
        """.trimIndent()
        val res = instance.parse(mockCode)
        assertTrue(res.isNotEmpty())
    }
}
