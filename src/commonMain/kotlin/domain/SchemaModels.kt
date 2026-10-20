/**
 * Core JSON Schema Draft 2020-12 and OpenAPI 3.2 Schema Object domain models.
 *
 * Defines [SchemaCore], [SchemaDefinition], [SchemaProperty], [SchemaType], [XmlNodeType],
 * [Discriminator], and [Xml].
 */
package domain

/**
 * Strongly-typed enumeration of standard JSON Schema Draft 2020-12 / OpenAPI 3.2 primitive and
 * structural data types.
 *
 * @property wireName The lowercase JSON Schema keyword string.
 */
enum class SchemaType(val wireName: String) {
  /** JSON `string` type. */
  STRING("string"),
  /** JSON `number` type. */
  NUMBER("number"),
  /** JSON `integer` type. */
  INTEGER("integer"),
  /** JSON `boolean` type. */
  BOOLEAN("boolean"),
  /** JSON `array` type. */
  ARRAY("array"),
  /** JSON `object` type. */
  OBJECT("object"),
  /** JSON `null` type. */
  NULL("null");

  companion object {
    /**
     * Resolves a [SchemaType] from its lowercase wire name, or `null` if unrecognized.
     *
     * @param value The raw type string.
     * @return The matching [SchemaType] or `null`.
     */
    fun fromWireName(value: String): SchemaType? =
        entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }
  }
}

/**
 * Strongly-typed enumeration of OpenAPI 3.2 XML Object `nodeType` values.
 *
 * @property wireName The lowercase wire string (`element`, `attribute`, `text`, `cdata`, `none`).
 */
enum class XmlNodeType(val wireName: String) {
  /** XML element node. */
  ELEMENT("element"),
  /** XML attribute node. */
  ATTRIBUTE("attribute"),
  /** XML text node. */
  TEXT("text"),
  /** XML CDATA section node. */
  CDATA("cdata"),
  /** No XML wrapper node. */
  NONE("none");

  companion object {
    /**
     * Resolves an [XmlNodeType] from its wire string, or `null` if unrecognized.
     *
     * @param value The raw nodeType string.
     * @return The matching [XmlNodeType] or `null`.
     */
    fun fromWireName(value: String): XmlNodeType? =
        entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }
  }
}

/**
 * Strongly-typed mode for boolean JSON Schema literals (`true` vs `false`).
 *
 * @property value The underlying boolean value (`true` accepts any instance, `false` rejects all).
 */
enum class BooleanSchemaMode(val value: Boolean) {
  /** Schema `true` (accepts any instance). */
  TRUE(true),
  /** Schema `false` (rejects all instances). */
  FALSE(false);

  /** Companion factory methods for [BooleanSchemaMode]. */
  companion object {
    /**
     * Converts a nullable boolean schema flag into a [BooleanSchemaMode].
     *
     * @param flag The boolean schema flag.
     * @return Matching [BooleanSchemaMode] or `null`.
     */
    fun fromBoolean(flag: Boolean?): BooleanSchemaMode? =
        when (flag) {
          true -> TRUE
          false -> FALSE
          null -> null
        }
  }
}

/**
 * Strongly-typed representation of JSON Schema `contentEncoding` values (RFC 2045 / RFC 4648).
 *
 * @property wireName The lowercase wire encoding name.
 */
enum class ContentEncoding(val wireName: String) {
  /** Base64 encoding (RFC 4648 Section 4). */
  BASE64("base64"),
  /** URL-safe Base64 encoding (RFC 4648 Section 5). */
  BASE64URL("base64url"),
  /** Quoted-Printable encoding (RFC 2045 Section 6.7). */
  QUOTED_PRINTABLE("quoted-printable"),
  /** Unencoded binary data (`binary`). */
  BINARY("binary"),
  /** 7-bit ASCII data (`7bit`). */
  SEVEN_BIT("7bit"),
  /** 8-bit octets (`8bit`). */
  EIGHT_BIT("8bit");

  /** Companion factory methods for [ContentEncoding]. */
  companion object {
    /**
     * Resolves a [ContentEncoding] from its wire name, or `null` if unrecognized.
     *
     * @param value The raw `contentEncoding` string.
     * @return Matching [ContentEncoding] or `null`.
     */
    fun fromWireName(value: String): ContentEncoding? =
        entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }
  }
}

/**
 * Shared contract for JSON Schema Draft 2020-12 and OpenAPI 3.2 Schema Objects, unifying
 * root/component schemas ([SchemaDefinition]) and nested property/subschemas ([SchemaProperty]).
 */
sealed interface SchemaCore {
  /** URI Reference (`$ref`). */
  val ref: String?
  /** Dynamic URI Reference (`$dynamicRef`). */
  val dynamicRef: String?
  /** Boolean schema literal (`true` or `false`). */
  val booleanSchema: Boolean?
  /** Canonical schema resource identifier (`$id`). */
  val schemaId: String?
  /** JSON Schema dialect URI (`$schema`). */
  val schemaDialect: String?
  /** Plain name fragment identifier (`$anchor`). */
  val anchor: String?
  /** Dynamic anchor identifier (`$dynamicAnchor`). */
  val dynamicAnchor: String?
  /** Tooling comment (`$comment`). */
  val comment: String?
  /** Local schema definitions (`$defs`). */
  val defs: Map<String, SchemaProperty>
  /** Explicit multi-type set (`type`). */
  val types: Set<String>
  /** Data type format modifier (`format`). */
  val format: String?
  /** MIME type of string-encoded content (`contentMediaType`). */
  val contentMediaType: String?
  /** Encoding of string-encoded content (`contentEncoding`). */
  val contentEncoding: String?
  /** Minimum string length (`minLength`). */
  val minLength: Int?
  /** Maximum string length (`maxLength`). */
  val maxLength: Int?
  /** ECMA-262 regular expression (`pattern`). */
  val pattern: String?
  /** Inclusive lower numeric bound (`minimum`). */
  val minimum: Double?
  /** Inclusive upper numeric bound (`maximum`). */
  val maximum: Double?
  /** Numeric divisor constraint (`multipleOf`). */
  val multipleOf: Double?
  /** Exclusive lower numeric bound (`exclusiveMinimum`). */
  val exclusiveMinimum: Double?
  /** Exclusive upper numeric bound (`exclusiveMaximum`). */
  val exclusiveMaximum: Double?
  /** Minimum array length (`minItems`). */
  val minItems: Int?
  /** Maximum array length (`maxItems`). */
  val maxItems: Int?
  /** Swagger 2.0 array collection format (`collectionFormat`). */
  val collectionFormat: String?
  /** Whether array elements must be unique (`uniqueItems`). */
  val uniqueItems: Boolean?
  /** Minimum object property count (`minProperties`). */
  val minProperties: Int?
  /** Maximum object property count (`maxProperties`). */
  val maxProperties: Int?
  /** Array item subschema (`items`). */
  val items: SchemaProperty?
  /** Positional tuple array subschemas (`prefixItems`). */
  val prefixItems: List<SchemaProperty>
  /** Subschema that at least one array element must match (`contains`). */
  val contains: SchemaProperty?
  /** Minimum matches for `contains` (`minContains`). */
  val minContains: Int?
  /** Maximum matches for `contains` (`maxContains`). */
  val maxContains: Int?
  /** Object property definitions (`properties`). */
  val properties: Map<String, SchemaProperty>
  /** Schema for dynamic/additional object properties (`additionalProperties`). */
  val additionalProperties: SchemaProperty?
  /** Mandatory object property names (`required`). */
  val required: List<String>
  /** Allowed enumerated values (`enum`). */
  val enumValues: List<OpenApiValue>?
  /** Schema description (`description`). */
  val description: String?
  /** Short schema title (`title`). */
  val title: String?
  /** Default instance value (`default`). */
  val defaultValue: OpenApiValue?
  /** Constant instance value (`const`). */
  val constValue: OpenApiValue?
  /** Whether this schema is deprecated (`deprecated`). */
  val deprecated: Boolean
  /** Whether this schema is read-only (`readOnly`). */
  val readOnly: Boolean
  /** Whether this schema is write-only (`writeOnly`). */
  val writeOnly: Boolean
  /** Additional external documentation (`externalDocs`). */
  val externalDocs: ExternalDocumentation?
  /** Polymorphic discriminator metadata (`discriminator`). */
  val discriminator: Discriminator?
  /** XML serialization metadata (`xml`). */
  val xml: Xml?
  /** Negated subschema (`not`). */
  val not: SchemaProperty?
  /** Conditional `if` subschema (`if`). */
  val ifSchema: SchemaProperty?
  /** Conditional `then` subschema (`then`). */
  val thenSchema: SchemaProperty?
  /** Conditional `else` subschema (`else`). */
  val elseSchema: SchemaProperty?
  /** Single example value (`example`). */
  val example: OpenApiValue?
  /** Regex-keyed property subschemas (`patternProperties`). */
  val patternProperties: Map<String, SchemaProperty>
  /** Subschema validating object property names (`propertyNames`). */
  val propertyNames: SchemaProperty?
  /** Property presence dependencies (`dependentRequired`). */
  val dependentRequired: Map<String, List<String>>
  /** Schema dependencies triggered by property presence (`dependentSchemas`). */
  val dependentSchemas: Map<String, SchemaProperty>
  /** Subschema for unevaluated object properties (`unevaluatedProperties`). */
  val unevaluatedProperties: SchemaProperty?
  /** Subschema for unevaluated array items (`unevaluatedItems`). */
  val unevaluatedItems: SchemaProperty?
  /** Decoded content subschema (`contentSchema`). */
  val contentSchema: SchemaProperty?
  /** Arbitrary custom vocabulary keywords (`customKeywords`). */
  val customKeywords: Map<String, OpenApiValue>
  /** Specification extensions starting with `x-` (`extensions`). */
  val extensions: Map<String, OpenApiValue>
}

/** Strongly-typed set of [SchemaType]s parsed from [SchemaCore.types]. */
val SchemaCore.parsedTypes: Set<SchemaType>
  get() = types.mapNotNull { SchemaType.fromWireName(it) }.toSet()

/**
 * The Schema Object allows the definition of input and output data types. These types can be
 * objects, but also primitives and arrays. This object is a superset of the JSON Schema
 * Specification Draft 2020-12.
 *
 * For more information about the keywords, see JSON Schema Core and JSON Schema Validation. Unless
 * stated otherwise, the keyword definitions follow those of JSON Schema and do not add any
 * additional semantics.
 *
 * See [Schema Object](https://spec.openapis.org/oas/v3.2.0#schema-object)
 */
data class SchemaDefinition(
    /**
     * The name of the class or enum (e.g. "UserProfile"). Note: This is not strictly part of the
     * Schema Object (where names are keys in the Components), but is required here for Code
     * Generation context.
     */
    val name: String,
    /** Sanitized Kotlin identifier for [name]. */
    val safeName: String = psi.ReferenceResolver.sanitizeTypeName(name),

    /**
     * If this schema is a reference to another schema, this holds the URI Reference. JSON Schema
     * keyword: `$ref`.
     */
    override val ref: String? = null,

    /**
     * Dynamic reference resolved via `$dynamicAnchor` scopes. JSON Schema keyword: `$dynamicRef`.
     */
    override val dynamicRef: String? = null,

    /**
     * Represents a boolean schema (`true` or `false`). `true` means any instance is valid, `false`
     * means no instance is valid. When this is set, other schema keywords are ignored for
     * validation purposes.
     */
    override val booleanSchema: Boolean? = null,

    /**
     * The type of schema. Data types in the OAS are based on the types defined by the JSON Schema
     * Validation Specification Draft 2020-12: "null", "boolean", "object", "array", "number",
     * "string", or "integer".
     *
     * Defaults to "object" so boolean schemas can be constructed without specifying a dummy type.
     * When [booleanSchema] is set, this field is ignored during serialization.
     */
    val type: String = "object",

    /**
     * Whether the `type` keyword was explicitly present in the source document. When false,
     * serializers should omit `type` unless [types] is explicitly provided.
     */
    val typeExplicit: Boolean = true,

    /** Canonical identifier for the schema. JSON Schema keyword: `$id`. */
    override val schemaId: String? = null,

    /**
     * Dialect to apply when processing this schema resource root. JSON Schema keyword: `$schema`.
     */
    override val schemaDialect: String? = null,

    /**
     * Defines a named schema location within the current resource. JSON Schema keyword: `$anchor`.
     */
    override val anchor: String? = null,

    /**
     * Defines a dynamic anchor for resolving `$dynamicRef` keywords. JSON Schema keyword:
     * `$dynamicAnchor`.
     */
    override val dynamicAnchor: String? = null,

    /** A comment for tooling, not used in validation. JSON Schema keyword: `$comment`. */
    override val comment: String? = null,

    /** Schema definitions available for reuse within this schema. JSON Schema keyword: `$defs`. */
    override val defs: Map<String, SchemaProperty> = emptyMap(),

    /**
     * Optional explicit type set for OAS 3.2 / JSON Schema multi-type definitions. When provided,
     * this overrides [type] for codegen decisions. Example: {"string", "null"}.
     */
    override val types: Set<String> = emptySet(),

    /**
     * The format refinement (e.g., "int32", "int64", "float", "double", "date-time", "password").
     * This applies when [type] is a primitive.
     */
    override val format: String? = null,

    /**
     * The standard MIME type of the content (e.g., "image/png", "application/octet-stream").
     * Applies when [type] is "string" and content is binary/encoded.
     */
    override val contentMediaType: String? = null,

    /**
     * The Content-Encoding specifying how the content is encoded in the schema (e.g., "base64",
     * "base64url"). Applies when [type] is "string" and content is encoded.
     */
    override val contentEncoding: String? = null,

    /** Minimum string length (applies to string types). JSON Schema keyword: `minLength`. */
    override val minLength: Int? = null,

    /** Maximum string length (applies to string types). JSON Schema keyword: `maxLength`. */
    override val maxLength: Int? = null,

    /** Regular expression pattern that a string must match. JSON Schema keyword: `pattern`. */
    override val pattern: String? = null,

    /** Minimum numeric value (applies to number/integer types). JSON Schema keyword: `minimum`. */
    override val minimum: Double? = null,

    /** Maximum numeric value (applies to number/integer types). JSON Schema keyword: `maximum`. */
    override val maximum: Double? = null,

    /** Numbers must be a multiple of this value. JSON Schema keyword: `multipleOf`. */
    override val multipleOf: Double? = null,

    /** Exclusive lower bound for numbers. JSON Schema keyword: `exclusiveMinimum`. */
    override val exclusiveMinimum: Double? = null,

    /** Exclusive upper bound for numbers. JSON Schema keyword: `exclusiveMaximum`. */
    override val exclusiveMaximum: Double? = null,

    /** Minimum number of items for arrays. JSON Schema keyword: `minItems`. */
    override val minItems: Int? = null,

    /** Maximum number of items for arrays. JSON Schema keyword: `maxItems`. */
    override val maxItems: Int? = null,

    /** Swagger 2.0 (Items Object): Determines the format of the array if type array is used. */
    override val collectionFormat: String? = null,

    /** Whether array items must be unique. JSON Schema keyword: `uniqueItems`. */
    override val uniqueItems: Boolean? = null,

    /** Minimum number of properties for objects. JSON Schema keyword: `minProperties`. */
    override val minProperties: Int? = null,

    /** Maximum number of properties for objects. JSON Schema keyword: `maxProperties`. */
    override val maxProperties: Int? = null,

    /** If type is "array", this describes the items schema. JSON Schema keyword: `items`. */
    override val items: SchemaProperty? = null,

    /** Tuple typing for arrays using positional schemas. JSON Schema keyword: `prefixItems`. */
    override val prefixItems: List<SchemaProperty> = emptyList(),

    /**
     * Applies a subschema to array elements and requires at least one to match. JSON Schema
     * keyword: `contains`.
     */
    override val contains: SchemaProperty? = null,

    /**
     * Minimum number of array elements that must match [contains]. JSON Schema keyword:
     * `minContains`.
     */
    override val minContains: Int? = null,

    /**
     * Maximum number of array elements that must match [contains]. JSON Schema keyword:
     * `maxContains`.
     */
    override val maxContains: Int? = null,

    /** If type is "object", this map contains its properties. JSON Schema keyword: `properties`. */
    override val properties: Map<String, SchemaProperty> = emptyMap(),

    /**
     * If type is "object", this schema defines the value type for additional properties. JSON
     * Schema keyword: `additionalProperties`.
     */
    override val additionalProperties: SchemaProperty? = null,

    /** List of field names that are mandatory (not nullable). JSON Schema keyword: `required`. */
    override val required: List<String> = emptyList(),

    /**
     * If type is "enum", this list contains the allowed values. JSON Schema allows any JSON value
     * type (string, number, boolean, object, array, null). JSON Schema keyword: `enum`.
     */
    override val enumValues: List<OpenApiValue>? = null,

    /** A description of the schema. CommonMark syntax MAY be used for rich text representation. */
    override val description: String? = null,

    /** A short title for the schema. JSON Schema keyword: `title`. */
    override val title: String? = null,

    /**
     * The default value for the schema instance, expressed as a JSON-compatible literal value. JSON
     * Schema keyword: `default`.
     */
    override val defaultValue: OpenApiValue? = null,

    /**
     * A fixed value for the schema instance, expressed as a JSON-compatible literal value. JSON
     * Schema keyword: `const`.
     */
    override val constValue: OpenApiValue? = null,

    /**
     * Declares this schema to be deprecated. Consumers SHOULD refrain from usage. JSON Schema
     * keyword: `deprecated`.
     */
    override val deprecated: Boolean = false,

    /** Declares the schema is read-only (response-only). JSON Schema keyword: `readOnly`. */
    override val readOnly: Boolean = false,

    /** Declares the schema is write-only (request-only). JSON Schema keyword: `writeOnly`. */
    override val writeOnly: Boolean = false,

    /**
     * Additional external documentation for this schema.
     *
     * See
     * [External Documentation Object](https://spec.openapis.org/oas/v3.2.0#external-documentation-object)
     */
    override val externalDocs: ExternalDocumentation? = null,

    /**
     * The discriminator provides a "hint" for which of a set of schemas a payload is expected to
     * satisfy. Used with `oneOf`, `anyOf`, or `allOf`.
     *
     * See [Discriminator Object](https://spec.openapis.org/oas/v3.2.0#discriminator-object)
     */
    override val discriminator: Discriminator? = null,

    /**
     * Adds additional metadata to describe the XML representation of this schema.
     *
     * See [XML Object](https://spec.openapis.org/oas/v3.2.0#xml-object)
     */
    override val xml: Xml? = null,

    /**
     * Inline usage of `oneOf`. Ensure that exactly one of the subschemas validates.
     *
     * In this domain model, this is a list of type names or references.
     */
    val oneOf: List<String> = emptyList(),

    /**
     * Inline usage of `oneOf` with full subschemas. This preserves non-reference entries that
     * cannot be represented as component names.
     */
    val oneOfSchemas: List<SchemaProperty> = emptyList(),

    /**
     * Inline usage of `anyOf`. Ensure that at least one of the subschemas validates.
     *
     * In this domain model, this is a list of type names or references.
     */
    val anyOf: List<String> = emptyList(),

    /**
     * Inline usage of `anyOf` with full subschemas. This preserves non-reference entries that
     * cannot be represented as component names.
     */
    val anyOfSchemas: List<SchemaProperty> = emptyList(),

    /**
     * Inline usage of `allOf`. Ensure that all of the subschemas validate.
     *
     * In this domain model, this is a list of type names or references, often used for
     * inheritance/composition.
     */
    val allOf: List<String> = emptyList(),

    /**
     * Inline usage of `allOf` with full subschemas. This preserves non-reference entries that
     * cannot be represented as component names.
     */
    val allOfSchemas: List<SchemaProperty> = emptyList(),

    /** Negation of a schema. JSON Schema keyword: `not`. */
    override val not: SchemaProperty? = null,

    /** Conditional schema applied when `if` validates. JSON Schema keyword: `if`. */
    override val ifSchema: SchemaProperty? = null,

    /** Conditional schema applied when `if` validates. JSON Schema keyword: `then`. */
    override val thenSchema: SchemaProperty? = null,

    /** Conditional schema applied when `if` fails to validate. JSON Schema keyword: `else`. */
    override val elseSchema: SchemaProperty? = null,

    /**
     * A free-form field to include an example of an instance for this schema.
     *
     * **Deprecated:** The `example` field has been deprecated in favor of the JSON Schema
     * `examples` keyword. Use of `example` is discouraged, and later versions of this specification
     * may remove it.
     */
    override val example: OpenApiValue? = null,

    /**
     * A map of named sample values (OAS specific examples). Or can map to JSON Schema `examples`
     * array logic depending on serialization.
     */
    val examples: Map<String, OpenApiValue>? = null,

    /**
     * A list of example values for this schema (JSON Schema `examples` array). This is the
     * spec-compliant array form and preserves example ordering.
     */
    val examplesList: List<OpenApiValue>? = null,

    /**
     * Applies subschemas to properties matching the regex. JSON Schema keyword:
     * `patternProperties`.
     */
    override val patternProperties: Map<String, SchemaProperty> = emptyMap(),

    /** Applies a schema to all property names. JSON Schema keyword: `propertyNames`. */
    override val propertyNames: SchemaProperty? = null,

    /**
     * Declares required property sets that depend on the presence of another property. JSON Schema
     * keyword: `dependentRequired`.
     */
    override val dependentRequired: Map<String, List<String>> = emptyMap(),

    /**
     * Declares schemas that are conditionally applied based on property presence. JSON Schema
     * keyword: `dependentSchemas`.
     */
    override val dependentSchemas: Map<String, SchemaProperty> = emptyMap(),

    /**
     * Applies a schema to properties that were not evaluated by other keywords. JSON Schema
     * keyword: `unevaluatedProperties`.
     */
    override val unevaluatedProperties: SchemaProperty? = null,

    /**
     * Applies a schema to array items that were not evaluated by other keywords. JSON Schema
     * keyword: `unevaluatedItems`.
     */
    override val unevaluatedItems: SchemaProperty? = null,

    /**
     * A schema for string-encoded content (e.g., JSON in a string). JSON Schema keyword:
     * `contentSchema`.
     */
    override val contentSchema: SchemaProperty? = null,

    /**
     * Additional JSON Schema keywords not explicitly modeled by this IR. OAS 3.2 allows Schema
     * Objects to include arbitrary vocabulary keywords without an `x-` prefix.
     */
    override val customKeywords: Map<String, OpenApiValue> = emptyMap(),

    /** Specification extensions (keys starting with `x-`). */
    override val extensions: Map<String, OpenApiValue> = emptyMap()
) : SchemaCore {
  /** Effective types for this schema, using [types] when provided, otherwise [type]. */
  val effectiveTypes: Set<String>
    get() =
        when (booleanSchema) {
          true -> setOf("any")
          false -> setOf("never")
          null -> if (types.isNotEmpty()) types else setOf(type)
        }

  /** Primary type for codegen (ignores "null" when present). */
  val primaryType: String
    get() = effectiveTypes.firstOrNull { it != "null" } ?: effectiveTypes.first()
}

/**
 * Returns the strongly-typed [BooleanSchemaMode] of this [SchemaCore], or `null` if not a boolean
 * schema.
 */
val SchemaCore.booleanSchemaMode: BooleanSchemaMode?
  get() = BooleanSchemaMode.fromBoolean(booleanSchema)

/** Returns the set of resolved [SchemaType] enums declared on this [SchemaCore]. */
val SchemaCore.schemaTypes: Set<SchemaType>
  get() = types.mapNotNull { SchemaType.fromWireName(it) }.toSet()

/** Returns the primary non-null [SchemaType] of this [SchemaCore], or `null` if unspecified. */
val SchemaCore.primarySchemaType: SchemaType?
  get() = schemaTypes.firstOrNull { it != SchemaType.NULL } ?: schemaTypes.firstOrNull()

/**
 * Returns the strongly-typed [ContentEncoding] parsed from [SchemaCore.contentEncoding], or `null`
 * if absent or custom.
 */
val SchemaCore.parsedContentEncoding: ContentEncoding?
  get() = contentEncoding?.let { ContentEncoding.fromWireName(it) }

/**
 * When request bodies or response payloads may be one of a number of different schemas, these
 * should use the JSON Schema `anyOf` or `oneOf` keywords. A polymorphic schema MAY include a
 * Discriminator Object, which defines the name of the property that may be used as a hint for which
 * schema is expected to validate the structure of the model.
 *
 * See [Discriminator Object](https://spec.openapis.org/oas/v3.2.0#discriminator-object)
 */
data class Discriminator(
    /**
     * **REQUIRED**. The name of the discriminating property in the payload that will hold the
     * discriminating value.
     */
    val propertyName: String,

    /** An object to hold mappings between payload values and schema names or URI references. */
    val mapping: Map<String, String> = emptyMap(),

    /**
     * The schema name or URI reference to a schema that is expected to validate the structure of
     * the model when the discriminating property is not present in the payload or contains a value
     * for which there is no explicit or implicit mapping.
     */
    val defaultMapping: String? = null,

    /** Specification extensions (keys starting with `x-`). */
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * A metadata object that allows for more fine-tuned XML model definitions.
 *
 * See [XML Object](https://spec.openapis.org/oas/v3.2.0#xml-object)
 */
data class Xml(
    /**
     * Sets the name of the element/attribute corresponding to the schema, replacing the name that
     * was inferred from the property or component name.
     */
    val name: String? = null,

    /** The IRI of the namespace definition. Value MUST be in the form of a non-relative IRI. */
    val namespace: String? = null,

    /** The prefix to be used for the [name]. */
    val prefix: String? = null,

    /**
     * One of `element`, `attribute`, `text`, `cdata`, or `none`. The default value is `none` if
     * `$ref`, `$dynamicRef`, or `type: "array"` is present, and `element` otherwise.
     */
    val nodeType: String? = null,

    /**
     * Declares whether the property definition translates to an attribute instead of an element.
     * Default value is `false`.
     *
     * **Deprecated:** Use `nodeType: "attribute"` instead of `attribute: true`
     */
    val attribute: Boolean = false,

    /**
     * MAY be used only for an array definition. Signifies whether the array is wrapped (for
     * example, `<books><book/><book/></books>`) or unwrapped (`<book/><book/>`). Default value is
     * `false`.
     *
     * **Deprecated:** Use `nodeType: "element"` instead of `wrapped: true`
     */
    val wrapped: Boolean = false,

    /** Specification extensions (keys starting with `x-`). */
    val extensions: Map<String, OpenApiValue> = emptyMap()
) {
  /** Strongly-typed [XmlNodeType] parsed from [nodeType], or `null` if unspecified/custom. */
  val parsedNodeType: XmlNodeType?
    get() = nodeType?.let { XmlNodeType.fromWireName(it) }
}

/**
 * Represents a single property within a Schema. In a standard Schema Object, a property is simply a
 * sub-schema. This class mirrors [SchemaDefinition] but is tailored for nested fields in the code
 * generator context.
 *
 * Updated for OAS 3.1/3.2:
 * - Supports array of types (e.g. ["string", "null"]) via [types].
 * - Supports `contentMediaType` and `contentEncoding` for binary content handling.
 */
data class SchemaProperty(
    /**
     * Represents a boolean schema (`true` or `false`). `true` means any instance is valid, `false`
     * means no instance is valid. When this is set, other schema keywords are ignored for
     * validation purposes.
     */
    override val booleanSchema: Boolean? = null,

    /**
     * The set of JSON types allowed for this property. E.g. {"string"}, {"integer", "null"},
     * {"array"}. Replaces the single `type` string field.
     */
    override val types: Set<String> = emptySet(),

    /**
     * The format refinement (e.g., "int32", "int64", "float", "double", "date-time", "password").
     * See [Data Type Format](https://spec.openapis.org/oas/v3.2.0#data-type-format)
     */
    override val format: String? = null,

    /**
     * The standard MIME type of the content (e.g., "image/png", "application/octet-stream").
     * Replaces `format: binary` in OAS 3.1+.
     */
    override val contentMediaType: String? = null,

    /**
     * The Content-Encoding specifying how the content is encoded in the schema (e.g., "base64",
     * "base64url"). Should be used in conjunction with `type: string`.
     */
    override val contentEncoding: String? = null,

    /** Minimum string length (applies to string types). JSON Schema keyword: `minLength`. */
    override val minLength: Int? = null,

    /** Maximum string length (applies to string types). JSON Schema keyword: `maxLength`. */
    override val maxLength: Int? = null,

    /** Regular expression pattern that a string must match. JSON Schema keyword: `pattern`. */
    override val pattern: String? = null,

    /** Minimum numeric value (applies to number/integer types). JSON Schema keyword: `minimum`. */
    override val minimum: Double? = null,

    /** Maximum numeric value (applies to number/integer types). JSON Schema keyword: `maximum`. */
    override val maximum: Double? = null,

    /** Numbers must be a multiple of this value. JSON Schema keyword: `multipleOf`. */
    override val multipleOf: Double? = null,

    /** Exclusive lower bound for numbers. JSON Schema keyword: `exclusiveMinimum`. */
    override val exclusiveMinimum: Double? = null,

    /** Exclusive upper bound for numbers. JSON Schema keyword: `exclusiveMaximum`. */
    override val exclusiveMaximum: Double? = null,

    /** Minimum number of items for arrays. JSON Schema keyword: `minItems`. */
    override val minItems: Int? = null,

    /** Maximum number of items for arrays. JSON Schema keyword: `maxItems`. */
    override val maxItems: Int? = null,

    /** Swagger 2.0 (Items Object): Determines the format of the array if type array is used. */
    override val collectionFormat: String? = null,

    /** Whether array items must be unique. JSON Schema keyword: `uniqueItems`. */
    override val uniqueItems: Boolean? = null,

    /** Minimum number of properties for objects. JSON Schema keyword: `minProperties`. */
    override val minProperties: Int? = null,

    /** Maximum number of properties for objects. JSON Schema keyword: `maxProperties`. */
    override val maxProperties: Int? = null,

    /**
     * If type contains "array", this describes the contents of the list. Corresponds to JSON Schema
     * `items`.
     */
    override val items: SchemaProperty? = null,

    /**
     * Tuple typing for arrays using positional schemas. Corresponds to JSON Schema `prefixItems`.
     */
    override val prefixItems: List<SchemaProperty> = emptyList(),

    /**
     * Applies a subschema to array elements and requires at least one to match. Corresponds to JSON
     * Schema `contains`.
     */
    override val contains: SchemaProperty? = null,

    /**
     * Minimum number of array elements that must match [contains]. Corresponds to JSON Schema
     * `minContains`.
     */
    override val minContains: Int? = null,

    /**
     * Maximum number of array elements that must match [contains]. Corresponds to JSON Schema
     * `maxContains`.
     */
    override val maxContains: Int? = null,

    /**
     * If type contains "object", this map contains its properties. Corresponds to JSON Schema
     * `properties`.
     */
    override val properties: Map<String, SchemaProperty> = emptyMap(),

    /** Required property names for object schemas. Corresponds to JSON Schema `required`. */
    override val required: List<String> = emptyList(),

    /**
     * If type contains "object", this describes the value schema for dynamic keys. Corresponds to
     * JSON Schema `additionalProperties`.
     */
    override val additionalProperties: SchemaProperty? = null,

    /**
     * If this property is a reference to another object, this holds the URI Reference. Supports:
     * - Internal Pointers: `#/components/schemas/User`
     * - Relative Files: `./User.yaml`
     * - Class Names (Legacy/Simple): `User`
     */
    override val ref: String? = null,

    /** Canonical identifier for the schema. JSON Schema keyword: `$id`. */
    override val schemaId: String? = null,

    /**
     * Dialect to apply when processing this schema resource root. JSON Schema keyword: `$schema`.
     */
    override val schemaDialect: String? = null,

    /**
     * Defines a named schema location within the current resource. JSON Schema keyword: `$anchor`.
     */
    override val anchor: String? = null,

    /**
     * Defines a dynamic anchor for resolving `$dynamicRef` keywords. JSON Schema keyword:
     * `$dynamicAnchor`.
     */
    override val dynamicAnchor: String? = null,

    /**
     * Dynamic reference resolved via `$dynamicAnchor` scopes. JSON Schema keyword: `$dynamicRef`.
     */
    override val dynamicRef: String? = null,

    /** A comment for tooling, not used in validation. JSON Schema keyword: `$comment`. */
    override val comment: String? = null,

    /**
     * Schema definitions available for reuse within this subschema. JSON Schema keyword: `$defs`.
     */
    override val defs: Map<String, SchemaProperty> = emptyMap(),

    /** Documentation for this specific property. */
    override val description: String? = null,

    /** A short title for the property schema. JSON Schema keyword: `title`. */
    override val title: String? = null,

    /**
     * The default value for the property, expressed as a JSON-compatible literal value. JSON Schema
     * keyword: `default`.
     */
    override val defaultValue: OpenApiValue? = null,

    /**
     * A fixed value for the property, expressed as a JSON-compatible literal value. JSON Schema
     * keyword: `const`.
     */
    override val constValue: OpenApiValue? = null,

    /**
     * Declares this property to be deprecated. Consumers SHOULD refrain from usage. JSON Schema
     * keyword: `deprecated`.
     */
    override val deprecated: Boolean = false,

    /** Declares the property is read-only (response-only). JSON Schema keyword: `readOnly`. */
    override val readOnly: Boolean = false,

    /** Declares the property is write-only (request-only). JSON Schema keyword: `writeOnly`. */
    override val writeOnly: Boolean = false,

    /**
     * Additional external documentation for this schema property.
     *
     * See
     * [External Documentation Object](https://spec.openapis.org/oas/v3.2.0#external-documentation-object)
     */
    override val externalDocs: ExternalDocumentation? = null,

    /**
     * The discriminator provides a hint for which schema a payload is expected to satisfy.
     *
     * See [Discriminator Object](https://spec.openapis.org/oas/v3.2.0#discriminator-object)
     */
    override val discriminator: Discriminator? = null,

    /**
     * Enumerated allowed values for this schema. JSON Schema allows any JSON value type (string,
     * number, boolean, object, array, null). JSON Schema keyword: `enum`.
     */
    override val enumValues: List<OpenApiValue>? = null,

    /** Inline usage of `oneOf` for nested schemas. JSON Schema keyword: `oneOf`. */
    val oneOf: List<SchemaProperty> = emptyList(),

    /** Inline usage of `anyOf` for nested schemas. JSON Schema keyword: `anyOf`. */
    val anyOf: List<SchemaProperty> = emptyList(),

    /** Inline usage of `allOf` for nested schemas. JSON Schema keyword: `allOf`. */
    val allOf: List<SchemaProperty> = emptyList(),

    /** Negation of a nested schema. JSON Schema keyword: `not`. */
    override val not: SchemaProperty? = null,

    /** Conditional subschema applied when `if` validates. JSON Schema keyword: `if`. */
    override val ifSchema: SchemaProperty? = null,

    /** Conditional subschema applied when `if` validates. JSON Schema keyword: `then`. */
    override val thenSchema: SchemaProperty? = null,

    /** Conditional subschema applied when `if` fails to validate. JSON Schema keyword: `else`. */
    override val elseSchema: SchemaProperty? = null,

    /** A sample value for this property. */
    override val example: OpenApiValue? = null,

    /** A list of example values for this property. JSON Schema keyword: `examples`. */
    val examples: List<OpenApiValue>? = null,

    /** Adds additional metadata to describe the XML representation of this property. */
    override val xml: Xml? = null,

    /**
     * Applies subschemas to properties matching the regex. JSON Schema keyword:
     * `patternProperties`.
     */
    override val patternProperties: Map<String, SchemaProperty> = emptyMap(),

    /** Applies a schema to all property names. JSON Schema keyword: `propertyNames`. */
    override val propertyNames: SchemaProperty? = null,

    /**
     * Declares required property sets that depend on the presence of another property. JSON Schema
     * keyword: `dependentRequired`.
     */
    override val dependentRequired: Map<String, List<String>> = emptyMap(),

    /**
     * Declares schemas that are conditionally applied based on property presence. JSON Schema
     * keyword: `dependentSchemas`.
     */
    override val dependentSchemas: Map<String, SchemaProperty> = emptyMap(),

    /**
     * Applies a schema to properties that were not evaluated by other keywords. JSON Schema
     * keyword: `unevaluatedProperties`.
     */
    override val unevaluatedProperties: SchemaProperty? = null,

    /**
     * Applies a schema to array items that were not evaluated by other keywords. JSON Schema
     * keyword: `unevaluatedItems`.
     */
    override val unevaluatedItems: SchemaProperty? = null,

    /**
     * A schema for string-encoded content (e.g., JSON in a string). JSON Schema keyword:
     * `contentSchema`.
     */
    override val contentSchema: SchemaProperty? = null,

    /**
     * Additional JSON Schema keywords not explicitly modeled by this IR. OAS 3.2 allows Schema
     * Objects to include arbitrary vocabulary keywords without an `x-` prefix.
     */
    override val customKeywords: Map<String, OpenApiValue> = emptyMap(),

    /** Specification extensions (keys starting with `x-`). */
    override val extensions: Map<String, OpenApiValue> = emptyMap()
) : SchemaCore {
  /**
   * Legacy constructor for single-type definition (Swagger 2.0 / OAS 3.0 style). Maps the single
   * type to a set of one type.
   */
  constructor(
      type: String,
      format: String? = null,
      contentMediaType: String? = null,
      contentEncoding: String? = null,
      minLength: Int? = null,
      maxLength: Int? = null,
      pattern: String? = null,
      minimum: Double? = null,
      maximum: Double? = null,
      multipleOf: Double? = null,
      exclusiveMinimum: Double? = null,
      exclusiveMaximum: Double? = null,
      minItems: Int? = null,
      maxItems: Int? = null,
      uniqueItems: Boolean? = null,
      minProperties: Int? = null,
      maxProperties: Int? = null,
      items: SchemaProperty? = null,
      additionalProperties: SchemaProperty? = null,
      ref: String? = null,
      schemaId: String? = null,
      schemaDialect: String? = null,
      anchor: String? = null,
      dynamicAnchor: String? = null,
      dynamicRef: String? = null,
      comment: String? = null,
      defs: Map<String, SchemaProperty> = emptyMap(),
      description: String? = null,
      title: String? = null,
      defaultValue: OpenApiValue? = null,
      constValue: OpenApiValue? = null,
      deprecated: Boolean = false,
      readOnly: Boolean = false,
      writeOnly: Boolean = false,
      externalDocs: ExternalDocumentation? = null,
      discriminator: Discriminator? = null,
      example: OpenApiValue? = null,
      examples: List<OpenApiValue>? = null,
      xml: Xml? = null,
      patternProperties: Map<String, SchemaProperty> = emptyMap(),
      propertyNames: SchemaProperty? = null,
      dependentRequired: Map<String, List<String>> = emptyMap(),
      dependentSchemas: Map<String, SchemaProperty> = emptyMap(),
      unevaluatedProperties: SchemaProperty? = null,
      unevaluatedItems: SchemaProperty? = null,
      contentSchema: SchemaProperty? = null,
      customKeywords: Map<String, OpenApiValue> = emptyMap(),
      extensions: Map<String, OpenApiValue> = emptyMap(),
      ifSchema: SchemaProperty? = null,
      thenSchema: SchemaProperty? = null,
      elseSchema: SchemaProperty? = null
  ) : this(
      types = setOf(type),
      format = format,
      contentMediaType = contentMediaType,
      contentEncoding = contentEncoding,
      minLength = minLength,
      maxLength = maxLength,
      pattern = pattern,
      minimum = minimum,
      maximum = maximum,
      multipleOf = multipleOf,
      exclusiveMinimum = exclusiveMinimum,
      exclusiveMaximum = exclusiveMaximum,
      minItems = minItems,
      maxItems = maxItems,
      uniqueItems = uniqueItems,
      minProperties = minProperties,
      maxProperties = maxProperties,
      items = items,
      additionalProperties = additionalProperties,
      ref = ref,
      schemaId = schemaId,
      schemaDialect = schemaDialect,
      anchor = anchor,
      dynamicAnchor = dynamicAnchor,
      dynamicRef = dynamicRef,
      comment = comment,
      defs = defs,
      description = description,
      title = title,
      defaultValue = defaultValue,
      constValue = constValue,
      deprecated = deprecated,
      readOnly = readOnly,
      writeOnly = writeOnly,
      externalDocs = externalDocs,
      discriminator = discriminator,
      example = example,
      examples = examples,
      xml = xml,
      patternProperties = patternProperties,
      propertyNames = propertyNames,
      dependentRequired = dependentRequired,
      dependentSchemas = dependentSchemas,
      unevaluatedProperties = unevaluatedProperties,
      unevaluatedItems = unevaluatedItems,
      contentSchema = contentSchema,
      customKeywords = customKeywords,
      extensions = extensions,
      ifSchema = ifSchema,
      thenSchema = thenSchema,
      elseSchema = elseSchema)

  /**
   * Helper accessor for the primary type (the first non-null type). Useful for backwards
   * compatibility in logic that expects a single type.
   */
  val type: String
    get() =
        when (booleanSchema) {
          true -> "any"
          false -> "never"
          null -> types.firstOrNull { it != "null" } ?: types.firstOrNull() ?: "string"
        }
}
