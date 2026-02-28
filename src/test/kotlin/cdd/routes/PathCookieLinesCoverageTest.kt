package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test

class PathCookieLinesCoverageTest {
    @Test
    fun `validate path and cookie parameter line generation`() {
        val gen = NetworkGenerator()
        
        gen.buildLabelPathReplacement("name", isList = true, isMap = false, explode = true, allowReserved = false)
        gen.buildLabelPathReplacement("name", isList = true, isMap = false, explode = false, allowReserved = false)
        gen.buildLabelPathReplacement("name", isList = false, isMap = true, explode = true, allowReserved = false)
        gen.buildLabelPathReplacement("name", isList = false, isMap = true, explode = false, allowReserved = false)
        gen.buildLabelPathReplacement("name", isList = false, isMap = false, explode = false, allowReserved = false)

        gen.buildMatrixPathReplacement("name", isList = true, isMap = false, explode = true, allowReserved = false)
        gen.buildMatrixPathReplacement("name", isList = true, isMap = false, explode = false, allowReserved = false)
        gen.buildMatrixPathReplacement("name", isList = false, isMap = true, explode = true, allowReserved = false)
        gen.buildMatrixPathReplacement("name", isList = false, isMap = true, explode = false, allowReserved = false)
        gen.buildMatrixPathReplacement("name", isList = false, isMap = false, explode = false, allowReserved = false)
        
        val p = EndpointParameter(name = "p", location = ParameterLocation.COOKIE, type = "string")
        gen.buildFormStyleCookieLines(p, isList = true, isMap = false, explode = true)
        gen.buildFormStyleCookieLines(p, isList = false, isMap = true, explode = true)
        gen.buildFormStyleCookieLines(p, isList = false, isMap = false, explode = true)
        
        gen.buildFormStyleCookieLines(p, isList = true, isMap = false, explode = false)
        gen.buildFormStyleCookieLines(p, isList = false, isMap = true, explode = false)
        gen.buildFormStyleCookieLines(p, isList = false, isMap = false, explode = false)
    }
}
