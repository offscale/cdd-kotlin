package cdd.classes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ParseCoverage3Test {
    @Test
    fun `test type alias, sealed interface, enum`() {
        val code = """
            package x
            
            /** 
             * @title MyAlias
             * @default "test"
             * @const "test2"
             * @deprecated
             * @readOnly
             * @writeOnly
             * @schemaId id1
             * @schemaDialect dialect1
             * @anchor anchor1
             * @dynamicAnchor danchor1
             * @dynamicRef dref1
             * @contentMediaType text/plain
             * @contentEncoding base64
             * @minLength 1
             * @maxLength 10
             * @pattern ^[a-z]+$
             * @minItems 1
             * @maxItems 10
             * @uniqueItems true
             * @multipleOf 2.0
             * @minimum 1.0
             * @exclusiveMinimum 0.0
             * @maximum 10.0
             * @exclusiveMaximum 11.0
             * @defs {"a": {"type": "string"}}
             */
            typealias MyString = String
            
            /**
             * @title MyMap
             */
            typealias MyMap = Map<String, Int>
            
            /**
             * @title MyList
             */
            typealias MyList = List<Boolean>
            
            /**
             * @title BadAlias
             */
            typealias BadAlias = MyCustomType
            
            /**
             * @title SealedOne
             * @discriminator type
             */
            sealed interface Shape
            
            class Circle(val radius: Int) : Shape
            class Square(val side: Int) : Shape
            
            /**
             * @title ColorEnum
             */
            enum class Color {
                RED, GREEN, BLUE
            }
        """.trimIndent()
        
        println("Running test!!!"); println("ParseCoverageTest3 RAN!!!"); val parser = DtoParser()
        val schemas = parser.parse(code)
        
        val myString = schemas.find { it.name == "MyString" }
        assertNotNull(myString)
        assertTrue(myString!!.types.contains("string"))
        
        val myMap = schemas.find { it.name == "MyMap" }
        assertNotNull(myMap)
        assertTrue(myMap!!.types.contains("object"))
        
        val myList = schemas.find { it.name == "MyList" }
        assertNotNull(myList)
        assertTrue(myList!!.types.contains("array"))
        
        val badAlias = schemas.find { it.name == "BadAlias" }
        assertNull(badAlias)
        
        val shape = schemas.find { it.name == "Shape" }
        assertNotNull(shape)
        
        val color = schemas.find { it.name == "Color" }
        assertNotNull(color)
    }
}
