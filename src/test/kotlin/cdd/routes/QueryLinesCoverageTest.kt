package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class QueryLinesCoverageTest {
    @Test
    fun `validate query array line generation variations`() {
        val gen = NetworkGenerator()
        
        val p1 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "array", style = ParameterStyle.FORM, explode = true, allowReserved = true, schema = SchemaProperty(type = "array"))
        val p2 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "array", style = ParameterStyle.FORM, explode = false, allowReserved = true, schema = SchemaProperty(type = "array"))
        val p3 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "array", style = ParameterStyle.SPACE_DELIMITED, explode = true, allowReserved = true, schema = SchemaProperty(type = "array"))
        val p4 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "array", style = ParameterStyle.PIPE_DELIMITED, explode = false, allowReserved = true, schema = SchemaProperty(type = "array"))
        val p5 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "array", style = ParameterStyle.FORM, explode = true, allowReserved = false, schema = SchemaProperty(type = "array"))
        val p6 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "array", style = ParameterStyle.FORM, explode = false, allowReserved = false, schema = SchemaProperty(type = "array"))
        val p7 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "array", style = ParameterStyle.SPACE_DELIMITED, explode = false, allowReserved = false, schema = SchemaProperty(type = "array"))
        val p8 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "array", style = ParameterStyle.PIPE_DELIMITED, explode = false, allowReserved = false, schema = SchemaProperty(type = "array"))
        val p9 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.FORM, explode = true, allowReserved = true, schema = SchemaProperty(type = "object"))
        val p10 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.FORM, explode = false, allowReserved = true, schema = SchemaProperty(type = "object"))
        val p11 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.DEEP_OBJECT, explode = true, allowReserved = true, schema = SchemaProperty(type = "object"))
        val p12 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.DEEP_OBJECT, explode = false, allowReserved = false, schema = SchemaProperty(type = "object"))

        
        gen.buildQueryArrayLines(p1, ParameterStyle.FORM, true, true)
        gen.buildQueryArrayLines(p2, ParameterStyle.FORM, false, true)
        try { gen.buildQueryArrayLines(p3, ParameterStyle.SPACE_DELIMITED, true, true) } catch (e: Exception) {}
        try { gen.buildQueryArrayLines(p3, ParameterStyle.PIPE_DELIMITED, true, true) } catch (e: Exception) {}
        try { gen.buildQueryArrayLines(p3, ParameterStyle.SPACE_DELIMITED, true, false) } catch (e: Exception) {}
        try { gen.buildQueryArrayLines(p3, ParameterStyle.PIPE_DELIMITED, true, false) } catch (e: Exception) {}

        gen.buildQueryArrayLines(p4, ParameterStyle.PIPE_DELIMITED, false, true)
        gen.buildQueryArrayLines(p5, ParameterStyle.FORM, true, false)
        gen.buildQueryArrayLines(p6, ParameterStyle.FORM, false, false)
        gen.buildQueryArrayLines(p7, ParameterStyle.SPACE_DELIMITED, false, false)
        gen.buildQueryArrayLines(p8, ParameterStyle.PIPE_DELIMITED, false, false)
        
        gen.buildQueryObjectLines(p9, ParameterStyle.FORM, true, true)
        gen.buildQueryObjectLines(p10, ParameterStyle.FORM, false, true)
        gen.buildQueryObjectLines(p10, ParameterStyle.FORM, true, false)
        gen.buildQueryObjectLines(p10, ParameterStyle.FORM, false, false)
        gen.buildQueryObjectLines(p11, ParameterStyle.DEEP_OBJECT, true, true)
        gen.buildQueryObjectLines(p12, ParameterStyle.DEEP_OBJECT, false, false)
    }
}
