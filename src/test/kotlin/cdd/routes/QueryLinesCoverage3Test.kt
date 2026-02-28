package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class QueryLinesCoverage3Test {
    @Test
    fun `buildQueryArrayLines DEEP_OBJECT and fallback`() {
        val gen = NetworkGenerator()
        val p = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "array", style = ParameterStyle.DEEP_OBJECT)
        val p2 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "array", style = ParameterStyle.MATRIX)
        val p3 = EndpointParameter(name = "body", location = ParameterLocation.QUERY, type = "array", style = ParameterStyle.MATRIX)
        
        try { gen.buildQueryArrayLines(p, ParameterStyle.DEEP_OBJECT, true, true) } catch(e: Exception){}
        try { gen.buildQueryArrayLines(p, ParameterStyle.DEEP_OBJECT, true, false) } catch(e: Exception){}
        
        gen.buildQueryArrayLines(p2, ParameterStyle.MATRIX, true, true)
        gen.buildQueryArrayLines(p2, ParameterStyle.MATRIX, true, false)
        gen.buildQueryArrayLines(p3, ParameterStyle.MATRIX, true, false)
    }
}
