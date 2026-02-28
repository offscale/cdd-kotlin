package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull

class HeaderCoverageTest {
    @Test
    fun `headerToDocValue test`() {
        val gen = NetworkGenerator()
        val h1 = Header(
            type = "string",
            description = "d",
            required = true,
            deprecated = true,
            style = ParameterStyle.SIMPLE,
            explode = true,
            example = ExampleObject(value = "ex"),
            examples = mapOf("exs" to ExampleObject(value = "exs")),
            extensions = mapOf("x-e" to "v")
        )
        assertNotNull(gen.headerToDocValue(h1))
        
        val h2 = Header(type = "string", content = mapOf("a" to MediaTypeObject()))
        assertNotNull(gen.headerToDocValue(h2))
    }
}
