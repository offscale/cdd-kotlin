package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SchemaModelsCoverageTest {

  @Test
  fun getPrimaryType_single_type() {
    val schema = SchemaDefinition(name = "Test", types = setOf("string"))
    assertEquals("string", schema.primaryType)
  }

  @Test
  fun getPrimaryType_with_null() {
    val schema = SchemaDefinition(name = "Test", types = setOf("string", "null"))
    assertEquals("string", schema.primaryType)
  }

  @Test
  fun getPrimaryType_only_null() {
    val schema = SchemaDefinition(name = "Test", types = setOf("null"))
    assertEquals("null", schema.primaryType)
  }

  @Test
  fun getPrimaryType_fallback_to_type() {
    val schema = SchemaDefinition(name = "Test", types = emptySet(), type = "fallback_type")
    assertEquals("fallback_type", schema.primaryType)
  }

  @Test
  fun getType_boolean_true() {
    val schema = SchemaProperty(booleanSchema = true)
    assertEquals("any", schema.type)
  }

  @Test
  fun getType_boolean_false() {
    val schema = SchemaProperty(booleanSchema = false)
    assertEquals("never", schema.type)
  }

  @Test
  fun getType_types_null_only() {
    val schema = SchemaProperty(types = setOf("null"))
    assertEquals("null", schema.type)
  }

  @Test
  fun getEffectiveTypes_boolean_true() {
    val schema = SchemaDefinition(name = "Test", booleanSchema = true)
    assertEquals(setOf("any"), schema.effectiveTypes)
  }

  @Test
  fun getEffectiveTypes_boolean_false() {
    val schema = SchemaDefinition(name = "Test", booleanSchema = false)
    assertEquals(setOf("never"), schema.effectiveTypes)
  }
}
