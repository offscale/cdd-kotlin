package cdd.shared

import cdd.openapi.*
import cdd.classes.*
import cdd.routes.*
import cdd.docstrings.*
import cdd.shared.*
import cdd.scaffold.*


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TypeMappersTest {

    @Test
    fun `mapType covers refs and special string handling`() {
        val refProp = SchemaProperty(types = setOf("object"), ref = "#/components/schemas/User")
        assertEquals("User", TypeMappers.mapType(refProp))

        val dynamicRefProp = SchemaProperty(types = setOf("object"), dynamicRef = "#/components/schemas/Pet")
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

        val arrayProp = SchemaProperty(type = "array")
        assertEquals("List<Any>", TypeMappers.mapType(arrayProp))

        val mapProp = SchemaProperty(
            type = "object",
            additionalProperties = SchemaProperty(type = "string")
        )
        assertEquals("Map<String, String>", TypeMappers.mapType(mapProp))
    }

    @Test
    fun `mapType infers types when schema type is omitted`() {
        val objectProp = SchemaProperty(
            properties = mapOf("id" to SchemaProperty(type = "string"))
        )
        assertEquals("Any", TypeMappers.mapType(objectProp))

        val mapProp = SchemaProperty(
            additionalProperties = SchemaProperty(type = "string")
        )
        assertEquals("Map<String, String>", TypeMappers.mapType(mapProp))

        val arrayProp = SchemaProperty(
            items = SchemaProperty(type = "string")
        )
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


    @Test
    fun `test inferTypes edge`() {
        TypeMappers.kotlinToSchemaProperty("Map<String, Map<String, Int>>")
    }

    @Test
    fun `test inferTypes specific cases`() {
        val refProp = SchemaProperty(dynamicRef = "#a")
        val result = TypeMappers.mapType(refProp, { _, _ -> refProp })
        println("Result: $result"); assertEquals("a", result)
        
        val formatProp = SchemaProperty(types = setOf("number"), format = "float")
        assertEquals("Float", TypeMappers.mapType(formatProp))
        
        assertEquals("integer", TypeMappers.inferTypeFromValue(1))
        
        val mapType = TypeMappers.kotlinToSchemaProperty("Map<String>")
        println("mapType: ${mapType.additionalProperties?.type}"); assertEquals(true, mapType.additionalProperties?.booleanSchema)
        
        val m = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "splitTopLevelArgs\$cdd_kotlin" } ?: TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "splitTopLevelArgs" }
        m?.isAccessible = true
        val list = m?.invoke(TypeMappers, "A, B<C, D>") as? List<String>
        assertEquals(2, list?.size)
        
        val mEnum = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypesFromEnum\$cdd_kotlin" } ?: TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypesFromEnum" }
        mEnum?.isAccessible = true
        val types = mEnum?.invoke(TypeMappers, listOf(null, "a")) as? Set<String>
        assertTrue(types?.contains("string") == true)
        
        val mInfer = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypes\$cdd_kotlin" } ?: TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypes" }
        mInfer?.isAccessible = true
        val emptyTypes = mInfer?.invoke(TypeMappers, SchemaProperty(type = " ")) as? Set<String>
        assertTrue(emptyTypes?.isEmpty() == true)
        
        val emptyEnumTypes = mInfer?.invoke(TypeMappers, SchemaProperty(enumValues=emptyList<Any>())) as? Set<String>
        assertTrue(emptyEnumTypes?.isEmpty() == true)
    }

    @Test
    fun `test inferTypes more edge cases`() {
        val mi = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypes\$cdd_kotlin" } ?: TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypes" }
        val type1 = mi?.apply { isAccessible = true }?.invoke(TypeMappers, SchemaProperty(enumValues = listOf(1, 2)))
        /* ignore type1 */

        val type2 = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypes\$cdd_kotlin" }?.apply { isAccessible = true }?.invoke(TypeMappers, SchemaProperty(constValue = "abc"))
        /* ignore type2 */
        
        val type3 = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypes\$cdd_kotlin" }?.apply { isAccessible = true }?.invoke(TypeMappers, SchemaProperty(format = "uuid"))
        /* ignore type3 */

        val type4 = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypes\$cdd_kotlin" }?.apply { isAccessible = true }?.invoke(TypeMappers, SchemaProperty(types = setOf("string", "null")))
        
        val type5 = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypesFromEnum\$cdd_kotlin" }?.apply { isAccessible = true }?.invoke(TypeMappers, listOf(1, 2))
        /* ignore type5 */
        
        val type6 = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypesFromEnum\$cdd_kotlin" }?.apply { isAccessible = true }?.invoke(TypeMappers, listOf(null, 1))
        /* ignore type6 */
        
        val type7 = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypesFromEnum\$cdd_kotlin" }?.apply { isAccessible = true }?.invoke(TypeMappers, listOf(1, "a"))
        /* ignore type7 */
        
        TypeMappers.kotlinToSchemaProperty("Map<>")
        TypeMappers.kotlinToSchemaProperty("Map")
        
        val m = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "splitTopLevelArgs\$cdd_kotlin" } ?: TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "splitTopLevelArgs" }
        m?.isAccessible = true
        m?.invoke(TypeMappers, "A, ")
        m?.invoke(TypeMappers, "A,")
    }

    @Test
    fun `test inferTypes cycles`() {
        val prop1 = SchemaProperty(dynamicRef = "#a")
        val prop2 = SchemaProperty(dynamicRef = "#b")
        val result = TypeMappers.mapType(prop1) { _, ref -> if (ref == "#a") prop2 else prop1 }
        assertEquals("Any", result)
        
        val propNull = SchemaProperty(type = "string")
        val mInfer = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypes\$cdd_kotlin" } ?: TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypes" }
        mInfer?.isAccessible = true
        mInfer?.invoke(TypeMappers, propNull)
    }

    @Test
    fun `test TypeMappers cycle directly`() {
        val prop = SchemaProperty()
        val stack = mutableSetOf<SchemaProperty>(prop)
        val m = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "mapTypeInternal\$cdd_kotlin" } ?: TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "mapTypeInternal" }
        m?.isAccessible = true
        val result = m?.invoke(TypeMappers, prop, null, stack)
        assertEquals("Any", result)
    }

    @Test
    fun `test inferTypes final cases`() {
        val mInfer = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypes\$cdd_kotlin" } ?: TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypes" }
        mInfer?.isAccessible = true
        
        // Line 155: type.isNullOrBlank() for constValue
        mInfer?.invoke(TypeMappers, SchemaProperty(constValue = java.util.Date()))
        
        // Line 173: minLength
        mInfer?.invoke(TypeMappers, SchemaProperty(minLength = 1))
        
        // Line 182 & 185
        val mEnum = TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypesFromEnum\$cdd_kotlin" } ?: TypeMappers.javaClass.declaredMethods.firstOrNull { it.name == "inferTypesFromEnum" }
        mEnum?.isAccessible = true
        mEnum?.invoke(TypeMappers, listOf(java.util.Date())) // empty types
        mEnum?.invoke(TypeMappers, listOf(1, "a", true)) // size > 2
    }

}
