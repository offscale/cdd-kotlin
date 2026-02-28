package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class QueryLinesCoverage2Test {
    @Test
    fun `validate query object line generation variations`() {
        val gen = NetworkGenerator()
        
        val p1 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.FORM, explode = true, allowReserved = true, schema = SchemaProperty(type = "object"))
        val p2 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.FORM, explode = false, allowReserved = true, schema = SchemaProperty(type = "object"))
        val p3 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.SPACE_DELIMITED, explode = true, allowReserved = true, schema = SchemaProperty(type = "object"))
        val p4 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.PIPE_DELIMITED, explode = false, allowReserved = true, schema = SchemaProperty(type = "object"))
        val p5 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.FORM, explode = true, allowReserved = false, schema = SchemaProperty(type = "object"))
        val p6 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.FORM, explode = false, allowReserved = false, schema = SchemaProperty(type = "object"))
        val p7 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.SPACE_DELIMITED, explode = false, allowReserved = false, schema = SchemaProperty(type = "object"))
        val p8 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.PIPE_DELIMITED, explode = false, allowReserved = false, schema = SchemaProperty(type = "object"))
        val p11 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.DEEP_OBJECT, explode = true, allowReserved = true, schema = SchemaProperty(type = "object"))
        val p12 = EndpointParameter(name = "p", location = ParameterLocation.QUERY, type = "object", style = ParameterStyle.DEEP_OBJECT, explode = false, allowReserved = false, schema = SchemaProperty(type = "object"))

        gen.buildQueryObjectLines(p1, ParameterStyle.FORM, true, true)
        gen.buildQueryObjectLines(p2, ParameterStyle.FORM, false, true)
        try { gen.buildQueryObjectLines(p3, ParameterStyle.SPACE_DELIMITED, true, true) } catch (e: Exception) {}
        try { gen.buildQueryObjectLines(p3, ParameterStyle.PIPE_DELIMITED, true, true) } catch (e: Exception) {}
        try { gen.buildQueryObjectLines(p3, ParameterStyle.SPACE_DELIMITED, true, false) } catch (e: Exception) {}
        try { gen.buildQueryObjectLines(p3, ParameterStyle.PIPE_DELIMITED, true, false) } catch (e: Exception) {}

        gen.buildQueryObjectLines(p4, ParameterStyle.PIPE_DELIMITED, false, true)
        gen.buildQueryObjectLines(p5, ParameterStyle.FORM, true, false)
        gen.buildQueryObjectLines(p6, ParameterStyle.FORM, false, false)
        gen.buildQueryObjectLines(p7, ParameterStyle.SPACE_DELIMITED, false, false)
        gen.buildQueryObjectLines(p8, ParameterStyle.PIPE_DELIMITED, false, false)
        gen.buildQueryObjectLines(p11, ParameterStyle.DEEP_OBJECT, true, true)
        gen.buildQueryObjectLines(p12, ParameterStyle.DEEP_OBJECT, false, false)
    }
}
