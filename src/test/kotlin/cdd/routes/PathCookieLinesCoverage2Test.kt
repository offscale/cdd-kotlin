package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class PathCookieLinesCoverage2Test {
    @Test
    fun `buildCookieStyleLines tests`() {
        val gen = NetworkGenerator()
        val p = EndpointParameter(name = "p", location = ParameterLocation.COOKIE, type = "string")
        
        val l1 = gen.buildCookieStyleLines(p, true, false, true)
        assertTrue(l1[0].contains("forEach"))
        val l2 = gen.buildCookieStyleLines(p, true, false, false)
        assertTrue(l2[0].contains("joinToString"))
        val l3 = gen.buildCookieStyleLines(p, false, true, true)
        assertTrue(l3[0].contains("forEach"))
        val l4 = gen.buildCookieStyleLines(p, false, true, false)
        assertTrue(l4[0].contains("entries.joinToString"))
        val l5 = gen.buildCookieStyleLines(p, false, false, false)
        assertTrue(l5[0].contains("cookie"))
    }
}
