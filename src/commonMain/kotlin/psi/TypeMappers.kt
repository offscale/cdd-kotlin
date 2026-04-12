package psi

import domain.SchemaProperty

/**
 * Shared utilities for mapping OpenAPI types to Kotlin types and vice versa.
 * Updated to support OAS 3.2:
 * - Multi-Type (e.g. ["string", "null"]) mappings.
 * - Map types via `additionalProperties`.
 * - Binary Content via `contentMediaType` / `contentEncoding` (replacing `format: binary`).
 * - Infer types when `type` is omitted by inspecting JSON Schema keywords.
 */
object TypeMappers {

    /**
     * Maps an OpenAPI Schema Property to a Kotlin type string.
     * Handles basics: string, integer, number, boolean, array.
     * Handles Dual Types (["string", "null"]) as Nullable Kotlin types.
     * Handles Binary Data (base64 encoded strings) as ByteArray.
     * Resolves references via [ReferenceResolver].
     */
    fun mapType(prop: SchemaProperty): String {
        return mapType(prop, null)
    }

    /**
     * Maps an OpenAPI Schema Property to a Kotlin type string, with optional
     * dynamic reference resolution support.
     *
     * @param dynamicRefResolver Optional resolver for `$dynamicRef` targets, scoped to the property.
     */
    fun mapType(
        prop: SchemaProperty,
        dynamicRefResolver: ((SchemaProperty, String) -> SchemaProperty?)?
    ): String {
        return mapTypeInternal(prop, dynamicRefResolver, LinkedHashSet())
    }

    private fun mapTypeInternal(
        prop: SchemaProperty,
        dynamicRefResolver: ((SchemaProperty, String) -> SchemaProperty?)?,
        stack: MutableSet<SchemaProperty>
    ): String {
        if (!stack.add(prop)) return "Any"

        try {
            prop.booleanSchema?.let { booleanValue ->
                return if (booleanValue) "Any" else "Nothing"
            }

            prop.dynamicRef?.let { dynamicRef ->
                val resolved = dynamicRefResolver?.invoke(prop, dynamicRef)
                if (resolved != null && resolved !== prop) {
                    return mapTypeInternal(resolved, dynamicRefResolver, stack)
                }
            }

            val ref = prop.ref ?: prop.dynamicRef
            if (ref != null) {
                val typeName = ReferenceResolver.resolveRefToType(ref)
                return typeName
            }

            val effectiveTypes = if (prop.types.isNotEmpty()) prop.types else inferTypes(prop)

            // Identify primary type (ignore "null" for type selection, it handled via nullability later)
            val primaryType = effectiveTypes.firstOrNull { it != "null" }
                ?: effectiveTypes.firstOrNull()
                ?: "string"

            return when (primaryType) {
                "string" -> mapStringType(prop)
                "integer" -> if (prop.format == "int64") "Long" else "Int"
                "number" -> if (prop.format == "float") "Float" else "Double"
                "boolean" -> "Boolean"
                "array" -> {
                    val itemType = prop.items?.let { mapTypeInternal(it, dynamicRefResolver, stack) } ?: "Any"
                    "List<$itemType>"
                }
                "object" -> {
                    if (prop.additionalProperties != null) {
                        val valueType = mapTypeInternal(prop.additionalProperties, dynamicRefResolver, stack)
                        "Map<String, $valueType>"
                    } else {
                        "Any"
                    }
                }
                else -> "Any" // Fallback for unknown types
            }
        } finally {
            stack.remove(prop)
        }
    }

    private fun mapStringType(prop: SchemaProperty): String {
        val fmt = prop.format

        // OAS 3.1+ Content handling for binary
        if (prop.contentEncoding == "base64" || prop.contentEncoding == "base64url") {
            return "ByteArray"
        }
        // If contentMediaType implies binary (e.g. not text), map to ByteArray.
        // We conservatively check non-null.
        if (prop.contentMediaType != null && !prop.contentMediaType.startsWith("text/")) {
            return "ByteArray"
        }

        return when (fmt) {
            "date" -> "LocalDate"
            "date-time" -> "Instant"
            "byte" -> "ByteArray"
            "binary" -> "ByteArray"
            "uuid" -> "String"
            else -> "String"
        }
    }

    private fun inferTypes(prop: SchemaProperty): Set<String> {
        if (prop.items != null || prop.prefixItems.isNotEmpty() || prop.contains != null ||
            prop.minItems != null || prop.maxItems != null || prop.uniqueItems != null
        ) {
            return setOf("array")
        }

        if (prop.properties.isNotEmpty() || prop.additionalProperties != null ||
            prop.patternProperties.isNotEmpty() || prop.propertyNames != null ||
            prop.dependentSchemas.isNotEmpty() || prop.minProperties != null || prop.maxProperties != null ||
            prop.required.isNotEmpty()
        ) {
            return setOf("object")
        }

        prop.constValue?.let { value ->
            val type = inferTypeFromValue(value)
            return if (type.isNullOrBlank()) emptySet() else setOf(type)
        }

        prop.enumValues?.let { values ->
            val inferred = inferTypesFromEnum(values)
            if (inferred.isNotEmpty()) return inferred
        }

        if (prop.minimum != null || prop.maximum != null || prop.multipleOf != null ||
            prop.exclusiveMinimum != null || prop.exclusiveMaximum != null
        ) {
            return setOf("number")
        }

        if (prop.minLength != null || prop.maxLength != null || prop.pattern != null ||
            prop.contentEncoding != null || prop.contentMediaType != null || prop.format != null
        ) {
            return setOf("string")
        }

        return emptySet()
    }

    private fun inferTypesFromEnum(values: List<Any?>): Set<String> {
        if (values.isEmpty()) return emptySet()
        val types = values.mapNotNull { inferTypeFromValue(it) }.toSet()
        if (types.isEmpty()) return emptySet()
        if (types.size == 1) return types
        if (types.size == 2 && types.contains("null")) return types
        return emptySet()
    }

    private fun inferTypeFromValue(value: Any?): String? {
        return when (value) {
            null -> "null"
            is String -> "string"
            is Int, is Long, is Short, is Byte -> "integer"
            is Float, is Double -> "number"
            is Boolean -> "boolean"
            is Map<*, *> -> "object"
            is List<*> -> "array"
            else -> null
        }
    }

    /**
     * Maps a Kotlin type string (PSI representation) back to an OpenAPI Schema Property.
     * e.g. "List<String>?" -> types: ["array", "null"], items: { types: ["string"] }
     * e.g. "ByteArray" -> types: ["string"], contentEncoding: "base64" (OAS 3.1 style)
     */
    fun kotlinToSchemaProperty(typeString: String): SchemaProperty {
        val isNullable = typeString.contains("?")
        val cleanType = typeString.replace(" ", "").replace("?", "")

        val types = mutableSetOf<String>()

        // Map core type
        var format: String? = null
        var items: SchemaProperty? = null
        var additionalProperties: SchemaProperty? = null
        var ref: String? = null
        var contentEncoding: String? = null

        when {
            cleanType == "Any" -> {
                return SchemaProperty(booleanSchema = true)
            }
            cleanType == "String" -> types.add("string")
            cleanType == "Int" -> { types.add("integer"); format = "int32" }
            cleanType == "Long" -> { types.add("integer"); format = "int64" }
            cleanType == "Double" -> { types.add("number"); format = "double" }
            cleanType == "Float" -> { types.add("number"); format = "float" }
            cleanType == "Boolean" -> types.add("boolean")
            cleanType == "LocalDate" -> { types.add("string"); format = "date" }
            cleanType == "Instant" -> { types.add("string"); format = "date-time" }
            cleanType == "ByteArray" -> {
                types.add("string")
                // OAS 3.1 Preferred: contentEncoding
                contentEncoding = "base64"
            }
            cleanType.startsWith("List<") -> {
                types.add("array")
                val innerType = cleanType.substringAfter("List<").substringBeforeLast(">")
                items = kotlinToSchemaProperty(innerType)
            }
            cleanType.startsWith("Map<") || cleanType.startsWith("MutableMap<") -> {
                types.add("object")
                val inner = cleanType.substringAfter("<").substringBeforeLast(">")
                val args = splitTopLevelArgs(inner)
                val valueType = if (args.size >= 2) args[1] else "Any"
                additionalProperties = kotlinToSchemaProperty(valueType)
            }
            cleanType.first().isUpperCase() -> {
                types.add("object")
                ref = cleanType
            }
            else -> types.add("string") // Default fallback
        }

        // Add "null" type if Kotlin type is nullable (OAS 3.2 / JSON Schema compliance)
        if (isNullable) {
            types.add("null")
        }

        return SchemaProperty(
            types = types,
            format = format,
            contentEncoding = contentEncoding,
            items = items,
            additionalProperties = additionalProperties,
            ref = ref
        )
    }

    private fun splitTopLevelArgs(raw: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0

        raw.forEach { ch ->
            when (ch) {
                '<' -> {
                    depth++
                    current.append(ch)
                }
                '>' -> {
                    depth--
                    current.append(ch)
                }
                ',' -> {
                    if (depth == 0) {
                        result.add(current.toString())
                        current.clear()
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result.map { it.trim() }
    }
}
