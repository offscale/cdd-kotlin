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

  @Test
  fun `mergeDto returns failure when primary constructor has no parameter list`() {
    val existing = "class TargetClass constructor { }"
    val schema = SchemaDefinition(name = "TargetClass")

    val res = merger.mergeDto(existing, schema)
    assertTrue(res.isFailure)
    assertTrue(res.cddErrorOrNull()!!.message.contains("parameter list"))
  }

  @Test
  fun `mergeDto escapes Kotlin keyword properties`() {
    val existing = "data class TargetClass(val id: Int)"
    val schema =
        SchemaDefinition(
            name = "TargetClass",
            properties =
                mapOf(
                    "id" to domain.SchemaProperty(types = setOf("integer")),
                    "in" to domain.SchemaProperty(types = setOf("string"))),
            required = listOf("id", "in"))

    val res = merger.mergeDto(existing, schema)
    assertTrue(res.isSuccess)
    assertTrue(res.getOrThrow().contains("val `in`: String"))
  }
}
