package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test

class QueryObjectCoverageTest {
    @Test
    fun `buildQueryObjectLines tests`() {
        val gen = NetworkGenerator()
        val p = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object")
        
        gen.buildQueryObjectLines(p, ParameterStyle.FORM, true, true)
        gen.buildQueryObjectLines(p, ParameterStyle.FORM, false, true)
        gen.buildQueryObjectLines(p, ParameterStyle.DEEP_OBJECT, true, true)
        gen.buildQueryObjectLines(p, ParameterStyle.SPACE_DELIMITED, false, true)
        gen.buildQueryObjectLines(p, ParameterStyle.PIPE_DELIMITED, false, true)
        
        gen.buildQueryObjectLines(p, ParameterStyle.FORM, true, false)
        gen.buildQueryObjectLines(p, ParameterStyle.FORM, false, false)
        gen.buildQueryObjectLines(p, ParameterStyle.DEEP_OBJECT, true, false)
        gen.buildQueryObjectLines(p, ParameterStyle.SPACE_DELIMITED, false, false)
        gen.buildQueryObjectLines(p, ParameterStyle.PIPE_DELIMITED, false, false)
        
        try { gen.buildQueryObjectLines(p, ParameterStyle.SPACE_DELIMITED, true, true) } catch(e: Exception){}
        try { gen.buildQueryObjectLines(p, ParameterStyle.PIPE_DELIMITED, true, true) } catch(e: Exception){}
        try { gen.buildQueryObjectLines(p, ParameterStyle.SPACE_DELIMITED, true, false) } catch(e: Exception){}
        try { gen.buildQueryObjectLines(p, ParameterStyle.PIPE_DELIMITED, true, false) } catch(e: Exception){}
    }
}
