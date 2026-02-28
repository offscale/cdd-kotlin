package cdd.classes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ParseSealedInterfaceTest {
    @Test
    fun `test sealed interface and subclasses`() {
        val code = """
            package x
            
            /**
             * A geometric shape
             * @title Shape
             * @discriminator type
             */
            sealed interface Shape
            
            /**
             * A circle
             * @title Circle
             */
            data class Circle(val radius: Int) : Shape
            
            /**
             * @title Square
             */
            data class Square(val side: Int) : Shape
        """.trimIndent()
        
        val parser = DtoParser()
        val schemas = parser.parse(code)
        
        val shape = schemas.find { it.name == "Shape" }
        assertNotNull(shape)
        assertEquals("Shape", shape!!.title)
        assertEquals("A geometric shape", shape.description)
        assertNotNull(shape.discriminator)
        assertEquals("type", shape.discriminator?.propertyName)
        
        val circle = schemas.find { it.name == "Circle" }
        assertNotNull(circle)
        assertEquals("A circle", circle!!.description)
        
        val square = schemas.find { it.name == "Square" }
        assertNotNull(square)
    }
}
