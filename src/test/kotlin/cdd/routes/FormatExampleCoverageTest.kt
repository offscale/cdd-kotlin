package cdd.routes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull

class FormatExampleCoverageTest {
    @Test
    fun `formatExampleValue variations`() {
        val gen = NetworkGenerator()
        val m = NetworkGenerator::class.java.getDeclaredMethod("formatExampleValue", ExampleObject::class.java)
        m.isAccessible = true
        
        m.invoke(gen, ExampleObject(externalValue = "http://a"))
        m.invoke(gen, ExampleObject(serializedValue = "s"))
        m.invoke(gen, ExampleObject(value = "v"))
        m.invoke(gen, ExampleObject(dataValue = mapOf("a" to "b")))
    }
}
