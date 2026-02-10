package domain

/**
 * An object grouping an internal or external example value with basic `summary` and `description` metadata.
 *
 * See [Example Object](https://spec.openapis.org/oas/v3.2.0#example-object)
 */
data class ExampleObject(
    /** Short description for the example. */
    val summary: String? = null,
    /** Long description for the example. */
    val description: String? = null,
    /**
     * An example of the data structure that MUST be valid according to the relevant Schema Object.
     * If present, `value` MUST be absent.
     */
    val dataValue: Any? = null,
    /**
     * An example of the serialized form of the value, including encoding and escaping.
     * If present, `value` and `externalValue` MUST be absent.
     */
    val serializedValue: String? = null,
    /**
     * A URI that identifies the serialized example in a separate document.
     * If present, `serializedValue` and `value` MUST be absent.
     */
    val externalValue: String? = null,
    /**
     * Embedded literal example.
     *
     * **Deprecated for non-JSON serialization targets.**
     */
    val value: Any? = null
)

/**
 * A single encoding definition applied to a single value, with the mapping determined by the Media Type Object.
 *
 * See [Encoding Object](https://spec.openapis.org/oas/v3.2.0#encoding-object)
 */
data class EncodingObject(
    /**
     * The Content-Type for encoding a specific property.
     * The value is a comma-separated list of media types.
     */
    val contentType: String? = null,
    /**
     * A map allowing additional information to be provided as headers.
     * `Content-Type` is described separately and SHALL be ignored in this section.
     */
    val headers: Map<String, Header> = emptyMap(),
    /**
     * Describes how a specific property value will be serialized depending on its type.
     * Uses the same values as [ParameterStyle].
     */
    val style: ParameterStyle? = null,
    /**
     * When true, property values of type `array` or `object` generate separate parameters
     * for each value of the array or key-value pair of the map.
     */
    val explode: Boolean? = null,
    /**
     * When true, parameter values are serialized using reserved expansion (RFC6570).
     */
    val allowReserved: Boolean? = null,
    /**
     * Applies nested Encoding Objects by name.
     */
    val encoding: Map<String, EncodingObject> = emptyMap(),
    /**
     * Applies nested Encoding Objects by position for `multipart` media types.
     */
    val prefixEncoding: List<EncodingObject> = emptyList(),
    /**
     * Applies a single Encoding Object to remaining items in a positional `multipart` array.
     */
    val itemEncoding: EncodingObject? = null
)

/**
 * Each Media Type Object describes content structured in accordance with the media type identified by its key.
 *
 * See [Media Type Object](https://spec.openapis.org/oas/v3.2.0#media-type-object)
 */
data class MediaTypeObject(
    /**
     * A schema describing the complete content of the request, response, parameter, or header.
     */
    val schema: SchemaProperty? = null,
    /**
     * A schema describing each item within a sequential media type.
     */
    val itemSchema: SchemaProperty? = null,
    /**
     * Example of the media type.
     */
    val example: ExampleObject? = null,
    /**
     * Examples of the media type.
     */
    val examples: Map<String, ExampleObject> = emptyMap(),
    /**
     * Encoding information mapped by property name.
     */
    val encoding: Map<String, EncodingObject> = emptyMap(),
    /**
     * Positional encoding information for `multipart` media types.
     */
    val prefixEncoding: List<EncodingObject> = emptyList(),
    /**
     * Single encoding information for `multipart` array items.
     */
    val itemEncoding: EncodingObject? = null
)

/**
 * Describes a single request body.
 *
 * See [Request Body Object](https://spec.openapis.org/oas/v3.2.0#request-body-object)
 */
data class RequestBody(
    /**
     * A brief description of the request body.
     */
    val description: String? = null,
    /**
     * The content of the request body.
     */
    val content: Map<String, MediaTypeObject> = emptyMap(),
    /**
     * Determines if the request body is required in the request.
     */
    val required: Boolean = false
)

/**
 * A map of possible out-of-band callbacks related to the parent operation.
 * The key is a runtime expression evaluated at runtime to identify the callback URL.
 *
 * See [Callback Object](https://spec.openapis.org/oas/v3.2.0#callback-object)
 */
typealias Callback = Map<String, PathItem>
