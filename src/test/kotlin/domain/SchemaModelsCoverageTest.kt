/** Unit and integration tests in [SchemaModelsCoverageTest]. */
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
    assertEquals(BooleanSchemaMode.FALSE, schema.booleanSchemaMode)
  }

  @Test
  fun stronglyTypedAccessors_cover_all_branches() {
    val trueSchema = SchemaProperty(booleanSchema = true)
    assertEquals(BooleanSchemaMode.TRUE, trueSchema.booleanSchemaMode)
    assertEquals(true, BooleanSchemaMode.TRUE.value)

    val normalSchema = SchemaProperty(types = setOf("string", "null"), contentEncoding = "base64")
    assertEquals(null, normalSchema.booleanSchemaMode)
    assertEquals(setOf(SchemaType.STRING, SchemaType.NULL), normalSchema.schemaTypes)
    assertEquals(SchemaType.STRING, normalSchema.primarySchemaType)
    assertEquals(ContentEncoding.BASE64, normalSchema.parsedContentEncoding)

    val nullOnlySchema = SchemaProperty(types = setOf("null"), contentEncoding = "custom-enc")
    assertEquals(SchemaType.NULL, nullOnlySchema.primarySchemaType)
    assertEquals(null, nullOnlySchema.parsedContentEncoding)

    val emptyTypesSchema = SchemaProperty(types = emptySet(), contentEncoding = null)
    assertEquals(null, emptyTypesSchema.primarySchemaType)
    assertEquals(null, emptyTypesSchema.parsedContentEncoding)

    for (enc in ContentEncoding.entries) {
      assertEquals(enc, ContentEncoding.fromWireName(enc.wireName))
    }

    val def = OpenApiDefinition(openapi = "3.2.0-rc1", info = Info("T", "1"))
    assertEquals(OpenApiVersion(3, 2, 0, "3.2.0-rc1"), def.parsedVersion)
    assertEquals(OpenApiVersion(3, 1, 0, "3.1"), OpenApiVersion.parse("3.1"))
    assertEquals(OpenApiVersion(3, 1, 0, "3.1.x"), OpenApiVersion.parse("3.1.x"))
    assertEquals(null, OpenApiVersion.parse("3"))
    assertEquals(null, OpenApiVersion.parse("x.1.0"))
    assertEquals(null, OpenApiVersion.parse("3.x.0"))

    val respDirect = EndpointResponse(statusCode = "200", schema = SchemaProperty("integer"))
    assertEquals("integer", respDirect.resolvePrimarySchema()?.type)
    val respJson =
        EndpointResponse(
            statusCode = "200",
            content =
                mapOf("application/json" to MediaTypeObject(schema = SchemaProperty("string"))))
    assertEquals("string", respJson.resolvePrimarySchema()?.type)
    val respOther =
        EndpointResponse(
            statusCode = "200",
            content = mapOf("text/plain" to MediaTypeObject(schema = SchemaProperty("boolean"))))
    assertEquals("boolean", respOther.resolvePrimarySchema()?.type)
    val respEmpty = EndpointResponse(statusCode = "204")
    assertEquals(null, respEmpty.resolvePrimarySchema())
    val respNoSchemaMedia =
        EndpointResponse(statusCode = "200", content = mapOf("text/plain" to MediaTypeObject()))
    assertEquals(null, respNoSchemaMedia.resolvePrimarySchema())
  }
}
