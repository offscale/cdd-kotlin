package psi

import domain.SchemaDefinition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DtoMergerCoverageTest {

    private val merger = DtoMerger()

    @Test
    fun `mergeDto throws IllegalArgumentException when class not found`() {
        val existing = "class WrongClass {}"
        val schema = SchemaDefinition(name = "TargetClass")
        
        val ex = assertThrows(IllegalArgumentException::class.java) {
            merger.mergeDto(existing, schema)
        }
        assertTrue(ex.message!!.contains("Class TargetClass not found"))
    }

    @Test
    fun `mergeDto throws IllegalStateException when primary constructor is missing`() {
        val existing = "class TargetClass { }"
        val schema = SchemaDefinition(name = "TargetClass")
        
        val ex = assertThrows(IllegalStateException::class.java) {
            merger.mergeDto(existing, schema)
        }
        assertTrue(ex.message!!.contains("primary constructor"))
    }
}