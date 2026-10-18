/** Unit and integration tests in [TypeMappersTest]. */
package psi

import domain.OpenApiArray
import domain.OpenApiBoolean
import domain.OpenApiInt
import domain.OpenApiNull
import domain.OpenApiNumber
import domain.OpenApiObject
import domain.OpenApiString
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
    assertEquals("kotlinx.serialization.json.JsonElement", TypeMappers.mapType(anySchema))

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
    assertEquals("kotlinx.serialization.json.JsonElement", TypeMappers.mapType(unknownProp))

    val arrayProp = SchemaProperty(type = "array")
    assertEquals("List<kotlinx.serialization.json.JsonElement>", TypeMappers.mapType(arrayProp))

    val mapProp =
        SchemaProperty(type = "object", additionalProperties = SchemaProperty(type = "string"))
    assertEquals("Map<String, String>", TypeMappers.mapType(mapProp))
  }

  @Test
  fun `mapType infers types when schema type is omitted`() {
    val objectProp = SchemaProperty(properties = mapOf("id" to SchemaProperty(type = "string")))
    assertEquals("kotlinx.serialization.json.JsonElement", TypeMappers.mapType(objectProp))

    val mapProp = SchemaProperty(additionalProperties = SchemaProperty(type = "string"))
    assertEquals("Map<String, String>", TypeMappers.mapType(mapProp))

    val arrayProp = SchemaProperty(items = SchemaProperty(type = "string"))
    assertEquals("List<String>", TypeMappers.mapType(arrayProp))

    val numericProp = SchemaProperty(minimum = 1.0)
    assertEquals("Double", TypeMappers.mapType(numericProp))

    val enumBoolProp =
        SchemaProperty(enumValues = listOf(OpenApiBoolean(true), OpenApiBoolean(false)))
    assertEquals("Boolean", TypeMappers.mapType(enumBoolProp))

    val constStringProp = SchemaProperty(constValue = OpenApiString("fixed"))
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

    // constValue = null
    val nullConstProp = SchemaProperty(constValue = OpenApiNull)
    assertEquals("kotlinx.serialization.json.JsonElement", TypeMappers.mapType(nullConstProp))

    val intConstProp = SchemaProperty(constValue = OpenApiInt(10L))
    assertEquals("Int", TypeMappers.mapType(intConstProp))

    val numConstProp = SchemaProperty(constValue = OpenApiNumber(3.14))
    assertEquals("Double", TypeMappers.mapType(numConstProp))

    // enum mixed
    val mixedEnumProp = SchemaProperty(enumValues = listOf(OpenApiString("string"), OpenApiInt(1L)))
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
    val emptyEnumProp = SchemaProperty(enumValues = emptyList())
    assertEquals("String", TypeMappers.mapType(emptyEnumProp))

    // enum all null
    val nullOnlyEnumProp = SchemaProperty(enumValues = listOf(OpenApiNull))
    assertEquals("kotlinx.serialization.json.JsonElement", TypeMappers.mapType(nullOnlyEnumProp))

    // enum with 3 distinct types (including null)
    val threeTypesEnumProp =
        SchemaProperty(enumValues = listOf(OpenApiString("a"), OpenApiInt(1L), OpenApiNull))
    assertEquals("String", TypeMappers.mapType(threeTypesEnumProp))

    // enum with null
    val nullEnumProp = SchemaProperty(enumValues = listOf(OpenApiString("test"), OpenApiNull))
    assertEquals(
        "String",
        TypeMappers.mapType(
            nullEnumProp)) // actually it should be nullable string but mapType just maps type

    // map const
    val mapConstProp = SchemaProperty(constValue = OpenApiObject(mapOf("a" to OpenApiInt(1L))))
    assertEquals("kotlinx.serialization.json.JsonElement", TypeMappers.mapType(mapConstProp))

    // list const
    val listConstProp = SchemaProperty(constValue = OpenApiArray(listOf(OpenApiInt(1L))))
    assertEquals("List<kotlinx.serialization.json.JsonElement>", TypeMappers.mapType(listConstProp))

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
    val emptyArgsMap = TypeMappers.kotlinToSchemaProperty("Map<>")
    assertEquals(true, emptyArgsMap.additionalProperties?.booleanSchema)
    val mutableMap = TypeMappers.kotlinToSchemaProperty("MutableMap<String, Int>")
    assertEquals("integer", mutableMap.additionalProperties?.type)

    // Cycle detection in mapTypeInternal and dynamicRefResolver returning self
    val dynProp = SchemaProperty(dynamicRef = "#node")
    assertEquals("Node", TypeMappers.mapType(dynProp) { p, _ -> p })
    assertEquals("Node", TypeMappers.mapType(dynProp) { _, _ -> null })
    val cycleA = SchemaProperty(dynamicRef = "#b")
    val cycleB = SchemaProperty(dynamicRef = "#a")
    assertEquals(
        "kotlinx.serialization.json.JsonElement",
        TypeMappers.mapType(cycleA) { p, _ -> if (p === cycleA) cycleB else cycleA })

    // mapStringType text/* contentMediaType vs binary contentMediaType
    assertEquals("String", TypeMappers.mapType(SchemaProperty(contentMediaType = "text/plain")))
    assertEquals(
        "ByteArray", TypeMappers.mapType(SchemaProperty(contentMediaType = "application/pdf")))

    // inferTypes all individual branches
    assertEquals(
        "List<kotlinx.serialization.json.JsonElement>",
        TypeMappers.mapType(SchemaProperty(prefixItems = listOf(SchemaProperty("string")))))
    assertEquals(
        "List<kotlinx.serialization.json.JsonElement>",
        TypeMappers.mapType(SchemaProperty(contains = SchemaProperty("string"))))
    assertEquals(
        "List<kotlinx.serialization.json.JsonElement>",
        TypeMappers.mapType(SchemaProperty(minItems = 1)))
    assertEquals(
        "List<kotlinx.serialization.json.JsonElement>",
        TypeMappers.mapType(SchemaProperty(maxItems = 5)))
    assertEquals(
        "List<kotlinx.serialization.json.JsonElement>",
        TypeMappers.mapType(SchemaProperty(uniqueItems = true)))

    assertEquals(
        "kotlinx.serialization.json.JsonElement",
        TypeMappers.mapType(
            SchemaProperty(patternProperties = mapOf("^a" to SchemaProperty("string")))))
    assertEquals(
        "kotlinx.serialization.json.JsonElement",
        TypeMappers.mapType(SchemaProperty(propertyNames = SchemaProperty("string"))))
    assertEquals(
        "kotlinx.serialization.json.JsonElement",
        TypeMappers.mapType(
            SchemaProperty(dependentSchemas = mapOf("a" to SchemaProperty("string")))))
    assertEquals(
        "kotlinx.serialization.json.JsonElement",
        TypeMappers.mapType(SchemaProperty(minProperties = 1)))
    assertEquals(
        "kotlinx.serialization.json.JsonElement",
        TypeMappers.mapType(SchemaProperty(maxProperties = 5)))
    assertEquals(
        "kotlinx.serialization.json.JsonElement",
        TypeMappers.mapType(SchemaProperty(required = listOf("a"))))

    assertEquals("Double", TypeMappers.mapType(SchemaProperty(maximum = 10.0)))
    assertEquals("Double", TypeMappers.mapType(SchemaProperty(multipleOf = 2.0)))
    assertEquals("Double", TypeMappers.mapType(SchemaProperty(exclusiveMinimum = 0.0)))
    assertEquals("Double", TypeMappers.mapType(SchemaProperty(exclusiveMaximum = 10.0)))
    assertEquals("LocalDate", TypeMappers.mapType(SchemaProperty(format = "date")))

    // capitalizeName and decapitalizeName empty string branch
    assertEquals("", capitalizeName(""))
    assertEquals("Abc", capitalizeName("abc"))
    assertEquals("", decapitalizeName(""))
    assertEquals("abc", decapitalizeName("Abc"))
  }
}
