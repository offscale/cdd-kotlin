package domain

/**
 * Represents a simplified definition of an independent Schema (e.g. from components/schemas).
 * Used as the inputs for the Code Generator.
 */
data class SchemaDefinition(
    /** The name of the class or enum (e.g. "UserProfile"). */
    val name: String,

    /** The type of schema: "object", "enum", "string", "integer", etc. */
    val type: String,

    /** If type is "object", this map contains its fields. */
    val properties: Map<String, SchemaProperty> = emptyMap(),

    /** List of field names that are mandatory (not nullable). */
    val required: List<String> = emptyList(),

    /** If type is "enum", this list contains the allowed values. */
    val enumValues: List<String>? = null,

    /** Documentation or description for the KDoc. */
    val description: String? = null
)

/**
 * Represents a single property within a Schema.
 */
data class SchemaProperty(
    /** The raw type string from OpenAPI (e.g., "string", "integer", "array"). */
    val type: String,

    /** The format refinement (e.g., "int64", "date-time"). */
    val format: String? = null,

    /** If type is "array", this describes the contents of the list. */
    val items: SchemaProperty? = null,

    /** If this property is a reference to another object, this holds the class name (e.g. "Address"). */
    val ref: String? = null,

    /** Documentation for this specific property. */
    val description: String? = null
)
