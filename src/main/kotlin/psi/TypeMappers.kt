package psi

import domain.SchemaProperty

/**
 * Shared utilities for mapping OpenAPI types to Kotlin types and vice versa.
 */
object TypeMappers {

    /**
     * Maps an OpenAPI Schema Property to a Kotlin type string.
     * Handles basics: string, integer, number, boolean, array.
     */
    fun mapType(prop: SchemaProperty): String {
        if (prop.ref != null) return prop.ref

        return when (prop.type) {
            "string" -> "String"
            "integer" -> {
                if (prop.format == "int64") "Long" else "Int"
            }
            "number" -> "Double"
            "boolean" -> "Boolean"
            "array" -> {
                val itemType = prop.items?.let { mapType(it) } ?: "Any"
                "List<$itemType>"
            }
            // Fallback for unknown types
            else -> "String"
        }
    }

    /**
     * Maps a Kotlin type string (PSI representation) back to an OpenAPI Schema Property.
     * e.g. "List<String>" -> type: array, items: string
     */
    fun kotlinToSchemaProperty(typeString: String): SchemaProperty {
        // Remove whitespace and nullable markers for analysis
        val cleanType = typeString.replace(" ", "").replace("?", "")

        return when {
            cleanType == "String" -> SchemaProperty(type = "string")
            cleanType == "Int" -> SchemaProperty(type = "integer", format = "int32")
            cleanType == "Long" -> SchemaProperty(type = "integer", format = "int64")
            cleanType == "Double" -> SchemaProperty(type = "number")
            cleanType == "Boolean" -> SchemaProperty(type = "boolean")
            cleanType.startsWith("List<") -> {
                val innerType = cleanType.substringAfter("List<").substringBeforeLast(">")
                SchemaProperty(type = "array", items = kotlinToSchemaProperty(innerType))
            }
            // Heuristic: If it starts with Uppercase, treat as Object Reference
            cleanType.first().isUpperCase() -> {
                SchemaProperty(type = "object", ref = cleanType)
            }
            else -> SchemaProperty(type = "string") // Conservative fallback
        }
    }
}
