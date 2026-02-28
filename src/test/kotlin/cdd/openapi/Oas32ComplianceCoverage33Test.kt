package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class Oas32ComplianceCoverage33Test {
    @Test
    fun `selectSchema resolves properties directly`() {
        val parser = OpenApiParser()
        val m = OpenApiParser::class.java.getDeclaredMethod("selectSchema", Map::class.java, Components::class.java)
        m.isAccessible = true
        
        val content = mapOf(
            "application/json" to MediaTypeObject(itemSchema = SchemaProperty(type = "string"))
        )
        val s1 = m.invoke(parser, content, null) as SchemaProperty
        assertEquals(setOf("array"), s1.types)
        assertNotNull(s1.items)
        
        val c2 = mapOf(
            "application/json" to MediaTypeObject(ref = "#/components/mediaTypes/m1")
        )
        val comps = Components(mediaTypes = mapOf("m1" to MediaTypeObject(itemSchema = SchemaProperty(type = "string"))))
        val s2 = m.invoke(parser, c2, comps) as SchemaProperty
        assertEquals(setOf("array"), s2.types)
        
        val c3 = mapOf(
            "application/json" to MediaTypeObject(ref = "#/components/mediaTypes/m2")
        )
        val s3 = m.invoke(parser, c3, comps) as SchemaProperty
        assertEquals("#/components/mediaTypes/m2", s3.ref)
    }
}
