package psi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DtoParserNumericConstraintsTest {

    private val parser = DtoParser()

    @Test
    fun `parse extracts numeric constraint tags`() {
        val source = """
            package sample

            /**
             * @multipleOf 2.5
             * @exclusiveMinimum 1.0
             * @exclusiveMaximum 9.0
             */
            data class Numeric(
                /**
                 * @multipleOf 2
                 * @exclusiveMinimum 3
                 * @exclusiveMaximum 7
                 */
                val count: Int
            )
        """.trimIndent()

        val schemas = parser.parse(source)
        val numeric = schemas.firstOrNull { it.name == "Numeric" }
        assertNotNull(numeric)

        assertEquals(2.5, numeric?.multipleOf)
        assertEquals(1.0, numeric?.exclusiveMinimum)
        assertEquals(9.0, numeric?.exclusiveMaximum)

        val count = numeric?.properties?.get("count")
        assertNotNull(count)
        assertEquals(2.0, count?.multipleOf)
        assertEquals(3.0, count?.exclusiveMinimum)
        assertEquals(7.0, count?.exclusiveMaximum)
    }
}
