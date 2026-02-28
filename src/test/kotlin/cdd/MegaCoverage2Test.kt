package cdd

import cdd.openapi.*
import cdd.classes.*
import cdd.routes.*
import cdd.docstrings.*
import cdd.shared.*
import cdd.scaffold.*

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class MegaCoverage2Test {

    @Test
    fun `mega DTO parsing edge cases`() {
        val parser = DtoParser()
        val code = """
            package com.test
            
            /**
             * @examples [{"prop": "val"}]
             * @externalDocs https://example.com/docs
             */
            data class MegaModel(
                /**
                 * @example "my_val"
                 * @externalDocs https://example.com/prop
                 */
                @SerialName("the_string")
                val str: String? = null,
                
                @SerialName("the_bool")
                val b: Boolean = true,
                
                val map: Map<String, List<Int>>,
                val types: TypesNode,
                
                /**
                 * @types ["string", "null"]
                 */
                val explicitTypes: Any?
            )
            
            data class TypesNode(val x: Int)
            
            enum class SimpleEnum { A, B }
            
            /**
             * @discriminator type
             */
            sealed interface DiscBase
            
            /**
             * @examples [{"x":1}]
             */
            data class SubA(val x: Int) : DiscBase
        """.trimIndent()
        
        val result = parser.parse(code)
        assertTrue(result.isNotEmpty())
    }
}
