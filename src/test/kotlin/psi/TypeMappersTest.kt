package psi

import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TypeMappersTest {

  @Test
  fun `mapType covers refs and special string handling`() {
    val refProp = SchemaProperty(types = setOf("object"), ref = "#/components/schemas/User")
    assertEquals("User", TypeMappers.mapType(refProp))

    val dynamicRefProp =
        SchemaProperty(types = setOf("object"), dynamicRef = "#/components/schemas/Pet")
    assertEquals("Pet", TypeMappers.mapType(dynamicRefProp))

    val anySchema = SchemaProperty(booleanSchema = true)
    assertEquals("Any", TypeMappers.mapType(anySchema))

    val neverSchema = SchemaProperty(booleanSchema = false)
    assertEquals("Nothing", TypeMappers.mapType(neverSchema))

    val base64Prop = SchemaProperty(type = "string", contentEncoding = "base64url")
    assertEquals("ByteArray", TypeMappers.mapType(base64Prop))

    val mediaProp = SchemaProperty(type = "string", contentMediaType = "application/pdf")
    assertEquals("ByteArray", TypeMappers.mapType(mediaProp))

    val uuidProp = SchemaProperty(type = "string", format = "uuid")
    assertEquals("String", TypeMappers.mapType(uuidProp))

    val emptyTypesProp = SchemaProperty(types = emptySet())
    assertEquals("String", TypeMappers.mapType(emptyTypesProp))

    val unknownProp = SchemaProperty(types = setOf("object"))
    assertEquals("Any", TypeMappers.mapType(unknownProp))

    val arrayProp = SchemaProperty(type = "array")
    assertEquals("List<Any>", TypeMappers.mapType(arrayProp))

    val mapProp =
        SchemaProperty(type = "object", additionalProperties = SchemaProperty(type = "string"))
    assertEquals("Map<String, String>", TypeMappers.mapType(mapProp))
  }

  @Test
  fun `mapType infers types when schema type is omitted`() {
    val objectProp = SchemaProperty(properties = mapOf("id" to SchemaProperty(type = "string")))
    assertEquals("Any", TypeMappers.mapType(objectProp))

    val mapProp = SchemaProperty(additionalProperties = SchemaProperty(type = "string"))
    assertEquals("Map<String, String>", TypeMappers.mapType(mapProp))

    val arrayProp = SchemaProperty(items = SchemaProperty(type = "string"))
    assertEquals("List<String>", TypeMappers.mapType(arrayProp))

    val numericProp = SchemaProperty(minimum = 1.0)
    assertEquals("Double", TypeMappers.mapType(numericProp))

    val enumBoolProp = SchemaProperty(enumValues = listOf(true, false))
    assertEquals("Boolean", TypeMappers.mapType(enumBoolProp))

    val constStringProp = SchemaProperty(constValue = "fixed")
    assertEquals("String", TypeMappers.mapType(constStringProp))
  }

  @Test
  fun `kotlinToSchemaProperty maps core Kotlin types`() {
    assertEquals(setOf("string"), TypeMappers.kotlinToSchemaProperty("String").types)
    assertEquals("int32", TypeMappers.kotlinToSchemaProperty("Int").format)
    assertEquals("int64", TypeMappers.kotlinToSchemaProperty("Long").format)
    assertEquals("double", TypeMappers.kotlinToSchemaProperty("Double").format)
    assertEquals("float", TypeMappers.kotlinToSchemaProperty("Float").format)
    assertEquals(setOf("boolean"), TypeMappers.kotlinToSchemaProperty("Boolean").types)

    assertEquals("date", TypeMappers.kotlinToSchemaProperty("LocalDate").format)
    assertEquals("date-time", TypeMappers.kotlinToSchemaProperty("Instant").format)

    val bytes = TypeMappers.kotlinToSchemaProperty("ByteArray")
    assertEquals("base64", bytes.contentEncoding)

    val anySchema = TypeMappers.kotlinToSchemaProperty("Any")
    assertEquals(true, anySchema.booleanSchema)
    assertTrue(anySchema.types.isEmpty())

    val listNullable = TypeMappers.kotlinToSchemaProperty("List<String>?")
    assertTrue(listNullable.types.contains("array"))
    assertTrue(listNullable.types.contains("null"))
    assertEquals("string", listNullable.items?.type)

    val ref = TypeMappers.kotlinToSchemaProperty("User")
    assertEquals("User", ref.ref)
    assertTrue(ref.types.contains("object"))

    val map = TypeMappers.kotlinToSchemaProperty("Map<String, Int>")
    assertTrue(map.types.contains("object"))
    assertEquals("integer", map.additionalProperties?.type)
    assertEquals("int32", map.additionalProperties?.format)

    val nestedMap = TypeMappers.kotlinToSchemaProperty("Map<String, List<User>>?")
    assertTrue(nestedMap.types.contains("null"))
    assertEquals("array", nestedMap.additionalProperties?.type)
    assertEquals("User", nestedMap.additionalProperties?.items?.ref)

    val fallback = TypeMappers.kotlinToSchemaProperty("custom")
    assertEquals(setOf("string"), fallback.types)
  }

  class UnknownType

  @Test
  fun `TypeMappers exhaustive cases`() {

    // format = float
    val floatProp = SchemaProperty(type = "number", format = "float")
    assertEquals("Float", TypeMappers.mapType(floatProp))

    // constValue = unknown class
    val unknownConstProp = SchemaProperty(constValue = UnknownType())
    assertEquals("String", TypeMappers.mapType(unknownConstProp))

    // enum mixed
    val mixedEnumProp = SchemaProperty(enumValues = listOf("string", 1))
    assertEquals("String", TypeMappers.mapType(mixedEnumProp))

    // string fallback based on minLength etc
    val minLengthProp = SchemaProperty(minLength = 1)
    assertEquals("String", TypeMappers.mapType(minLengthProp))
    val maxLengthProp = SchemaProperty(maxLength = 1)
    assertEquals("String", TypeMappers.mapType(maxLengthProp))
    val patternProp = SchemaProperty(pattern = "^$")
    assertEquals("String", TypeMappers.mapType(patternProp))
    val contentEncodingProp = SchemaProperty(contentEncoding = "base64")
    assertEquals("ByteArray", TypeMappers.mapType(contentEncodingProp))

    // empty enum
    val emptyEnumProp = SchemaProperty(enumValues = emptyList<Any>())
    assertEquals("String", TypeMappers.mapType(emptyEnumProp))

    // enum all unknown
    val unknownEnumProp = SchemaProperty(enumValues = listOf(UnknownType()))
    assertEquals("String", TypeMappers.mapType(unknownEnumProp))

    // enum with null
    val nullEnumProp = SchemaProperty(enumValues = listOf("test", null))
    assertEquals(
        "String",
        TypeMappers.mapType(
            nullEnumProp)) // actually it should be nullable string but mapType just maps type

    // map const
    val mapConstProp = SchemaProperty(constValue = mapOf("a" to 1))
    assertEquals("Any", TypeMappers.mapType(mapConstProp))

    // list const
    val listConstProp = SchemaProperty(constValue = listOf(1))
    assertEquals("List<Any>", TypeMappers.mapType(listConstProp))

    // Map with 1 arg
    val map1Arg = TypeMappers.kotlinToSchemaProperty("Map<String>")
    assertEquals(true, map1Arg.additionalProperties?.booleanSchema)

    // empty cleanType or lower case start
    val emptyType = TypeMappers.kotlinToSchemaProperty("")
    assertEquals("string", emptyType.type)

    val lowerType = TypeMappers.kotlinToSchemaProperty("customType")
    assertEquals("string", lowerType.type)

    // Any?
    val anyNullable = TypeMappers.kotlinToSchemaProperty("Any?")
    assertTrue(anyNullable.types.contains("null"))

    // splitTopLevelArgs inner logic
    val nestedMap = TypeMappers.kotlinToSchemaProperty("Map<String, Map<String, Int>>")
    assertEquals("object", nestedMap.additionalProperties?.type)
  }
}
