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

        val mapProp = SchemaProperty(
            type = "object",
            additionalProperties = SchemaProperty(type = "string")
        )
        assertEquals("Map<String, String>", TypeMappers.mapType(mapProp))
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
}
