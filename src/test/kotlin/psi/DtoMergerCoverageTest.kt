/** Unit and integration tests in [DtoMergerCoverageTest]. */
package psi

import domain.SchemaDefinition
import domain.cddErrorOrNull
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DtoMergerCoverageTest {

  private val merger = DtoMerger()

  @Test
  fun `mergeDto returns failure when class not found`() {
    val existing = "class WrongClass {}"
    val schema = SchemaDefinition(name = "TargetClass")

    val res = merger.mergeDto(existing, schema)
    assertTrue(res.isFailure)
    assertTrue(res.cddErrorOrNull()!!.message.contains("Class TargetClass not found"))
  }

  @Test
  fun `mergeDto returns failure when primary constructor is missing`() {
    val existing = "class TargetClass { }"
    val schema = SchemaDefinition(name = "TargetClass")

    val res = merger.mergeDto(existing, schema)
    assertTrue(res.isFailure)
    assertTrue(res.cddErrorOrNull()!!.message.contains("primary constructor"))
  }
}
