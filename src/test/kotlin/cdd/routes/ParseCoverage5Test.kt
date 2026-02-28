package cdd.routes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import com.fasterxml.jackson.databind.ObjectMapper

class ParseCoverage5Test {
    @Test
    fun `parseDependentRequired test`() {
        val parser = NetworkParser()
        val mapper = ObjectMapper()
        
        val map = parser.parseDependentRequired(mapper.readTree("{\"a\": [\"b\", \"c\"], \"d\": []}"))
        assertEquals(listOf("b", "c"), map["a"])
        assertEquals(emptyList<String>(), map["d"])
        
        val emptyMap = parser.parseDependentRequired(mapper.readTree("[]"))
        assertEquals(0, emptyMap.size)
    }
}
