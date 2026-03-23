package domain

/**
 * Supported HTTP Methods for API operations.
 * Updated to include QUERY (OAS 3.2) support.
 *
 * See [Path Item Object](https://spec.openapis.org/oas/v3.2.0#path-item-object)
 */
enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE, QUERY,
    /**
     * Represents a non-standard or extension HTTP method.
     * Use [EndpointDefinition.customMethod] to provide the raw method token (e.g. "COPY").
     */
    CUSTOM
}

/**
 * The location of the parameter.
 * Possible values are "query", "querystring", "header", "path" or "cookie".
 *
 * See [Parameter Object](https://spec.openapis.org/oas/v3.2.0#parameter-object)
 */
enum class ParameterLocation {
    PATH, QUERY, QUERYSTRING, HEADER, COOKIE
}

/**
 * Serialization style for parameters.
 * Describes how the parameter value will be serialized depending on the type of the parameter value.
 *
 * See [Style Values](https://spec.openapis.org/oas/v3.2.0#style-values)
 */
enum class ParameterStyle {
    /** Path: default, e.g. /users/value */
    SIMPLE,
    /** Query: default, e.g. id=value */
    FORM,
    /** Path: ;name=value */
    MATRIX,
    /** Path: .value */
    LABEL,
    /** Query: id=v1 v2 */
    SPACE_DELIMITED,
    /** Query: id=v1|v2 */
    PIPE_DELIMITED,
    /** Query: nested */
    DEEP_OBJECT,
    /** Cookie: name=value; name2=value2 */
    COOKIE
}

/**
 * Describes a single operation parameter.
 * A unique parameter is defined by a combination of a name and location.
 * Note: For `in: querystring`, the parameter represents the *entire* query string and
 * the `name` is not used for serialization (it is still useful for codegen signatures).
 *
 * See [Parameter Object](https://spec.openapis.org/oas/v3.2.0#parameter-object)
 */
data class EndpointParameter(
    /**
     * **REQUIRED**. The name of the parameter. Parameter names are case-sensitive.
     * If `in` is "path", the name field MUST correspond to a single template expression occurring within the path field in the Paths Object.
     */
    val name: String,

    /**
     * The type string (simplified Schema) for the parameter.
     */
    val type: String,

    /**
     * **REQUIRED**. The location of the parameter.
     */
    val location: ParameterLocation,

    /**
     * Determines whether this parameter is mandatory.
     * If the parameter location is "path", this value is REQUIRED and its value MUST be true.
     * Otherwise, the field MAY be included and its default value is false.
     */
    val isRequired: Boolean = true,

    /**
     * The schema defining the type used for the parameter.
     * Mutually exclusive with [content] in the OpenAPI spec.
     */
    val schema: SchemaProperty? = null,

    /**
     * A map containing the representations for the parameter.
     * Mutually exclusive with [schema] in the OpenAPI spec.
     */
    val content: Map<String, MediaTypeObject> = emptyMap(),

    /**
     * A brief description of the parameter. This could contain examples of use.
     * CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /**
     * Specifies that a parameter is deprecated and SHOULD be transitioned out of usage.
     * Default value is `false`.
     */
    val deprecated: Boolean = false,

    /**
     * When true, clients MAY pass a zero-length string value in place of parameters
     * that would otherwise be omitted entirely.
     */
    val allowEmptyValue: Boolean? = null,

    /**
     * Describes how the parameter value will be serialized depending on the type of the parameter value.
     */
    val style: ParameterStyle? = null,

    /**
     * When this is true, parameter values of type `array` or `object` generate separate parameters
     * for each value of the array or key-value pair of the map.
     */
    val explode: Boolean? = null,

    /**
     * When this is true, parameter values are serialized using reserved expansion, as defined by RFC6570.
     */
    val allowReserved: Boolean? = null,

    /**
     * Example of the parameter's potential value.
     * Equivalent to the Parameter Object's `example` field (shorthand Example Object).
     */
    val example: ExampleObject? = null,

    /**
     * Examples of the parameter's potential value.
     * Equivalent to the Parameter Object's `examples` field.
     */
    val examples: Map<String, ExampleObject> = emptyMap(),

    /**
     * Reference Object allowing `$ref` with optional summary/description overrides.
     * When present, this is treated as a Reference Object and other fields are ignored for serialization.
     */
    val reference: ReferenceObject? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * Describes a single header for HTTP responses and for individual parts in multipart representations.
 * The Header Object follows the structure of the Parameter Object with the following changes:
 * 1. `name` MUST NOT be specified, it is given in the corresponding `headers` map.
 * 2. `in` MUST NOT be specified, it is implicitly in `header`.
 * 3. All traits that are affected by the location MUST be applicable to a location of `header` (e.g., style).
 *
 * See [Header Object](https://spec.openapis.org/oas/v3.2.0#header-object)
 */
data class Header(
    /**
     * The type string (simplified Schema) for the header.
     */
    val type: String,

    /**
     * The schema defining the type used for the header.
     * Mutually exclusive with [content] in the OpenAPI spec.
     */
    val schema: SchemaProperty? = null,

    /**
     * A map containing the representations for the header.
     * Mutually exclusive with [schema] in the OpenAPI spec.
     */
    val content: Map<String, MediaTypeObject> = emptyMap(),

    /**
     * A brief description of the header.
     * CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /**
     * Determines whether this header is mandatory. The default value is `false`.
     */
    val required: Boolean = false,

    /**
     * Specifies that the header is deprecated and SHOULD be transitioned out of usage.
     * Default value is `false`.
     */
    val deprecated: Boolean = false,

    /**
     * Example of the header's potential value.
     */
    val example: ExampleObject? = null,

    /**
     * Examples of the header's potential value.
     */
    val examples: Map<String, ExampleObject> = emptyMap(),

    /**
     * Describes how the header value will be serialized.
     * The default (and only legal value for headers) is "simple".
     */
    val style: ParameterStyle? = ParameterStyle.SIMPLE,

    /**
     * When this is true, header values of type `array` or `object` generate a single header whose value
     * is a comma-separated list of the array items or key-value pairs of the map.
     * The default value is `false`.
     */
    val explode: Boolean? = false,

    /**
     * Reference Object allowing `$ref` with optional summary/description overrides.
     * When present, this is treated as a Reference Object and other fields are ignored for serialization.
     */
    val reference: ReferenceObject? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * Describes a single response from an API operation, including design-time, static `links` to operations based on the response.
 *
 * See [Response Object](https://spec.openapis.org/oas/v3.2.0#response-object)
 */
data class EndpointResponse(
    /**
     * The HTTP status code (e.g. "200", "404", "default") or range (e.g. "2XX").
     */
    val statusCode: String,

    /**
     * A short summary of the meaning of the response.
     */
    val summary: String? = null,

    /**
     * A description of the response. CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /**
     * Maps a header name to its definition.
     */
    val headers: Map<String, Header> = emptyMap(),

    /**
     * A map containing descriptions of potential response payloads.
     * The key is a media type or media type range and the value describes it.
     */
    val content: Map<String, MediaTypeObject> = emptyMap(),

    /**
     * Whether the `content` field was explicitly present on the OpenAPI document.
     *
     * This preserves the distinction between an omitted `content` field
     * and an explicit empty map (`content: {}`) for round-trip fidelity.
     */
    val contentPresent: Boolean = false,

    /**
     * The Kotlin class name of the response payload, or null if no content is returned (Unit).
     * This abstracts the `content` map for simplified code generation.
     * Legacy-friendly for Swagger 2.0 / OAS 3.0 style generators.
     */
    val type: String? = null,

    /**
     * A map of operations links that can be followed from the response.
     * The key of the map is a short name for the link.
     */
    val links: Map<String, Link>? = null,

    /**
     * Reference Object allowing `$ref` with optional summary/description overrides.
     * When present, this is treated as a Reference Object and other fields are ignored for serialization.
     */
    val reference: ReferenceObject? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * The Link Object represents a possible design-time link for a response.
 * The presence of a link does not guarantee the caller's ability to successfully invoke it,
 * rather it provides a known relationship and traversal mechanism between responses and other operations.
 *
 * See [Link Object](https://spec.openapis.org/oas/v3.2.0#link-object)
 */
data class Link(
    /**
     * Reference to a Link Object.
     * When present, this is treated as a Reference Object and other fields are ignored.
     */
    val ref: String? = null,

    /**
     * Reference Object allowing `$ref` with optional summary/description overrides.
     * When present, this is treated as a Reference Object and other fields are ignored for serialization.
     */
    val reference: ReferenceObject? = null,
    /**
     * The name of an *existing*, resolvable OAS operation, as defined with a unique `operationId`.
     * Mutually exclusive with `operationRef`.
     */
    val operationId: String? = null,

    /**
     * A URI reference to an OAS operation. Mutually exclusive with `operationId`.
     */
    val operationRef: String? = null,

    /**
     * A map representing parameters to pass to an operation as specified with `operationId` or identified via `operationRef`.
     * The key is the parameter name to be used, whereas the value can be a constant or an expression to be evaluated.
     */
    val parameters: Map<String, Any?> = emptyMap(),

    /**
     * A literal value or {expression} to use as a request body when calling the target operation.
     */
    val requestBody: Any? = null,

    /**
     * A description of the link. CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /**
     * A server object to be used by the target operation.
     */
    val server: Server? = null,

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)

/**
 * Describes a single API operation on a path.
 *
 * See [Operation Object](https://spec.openapis.org/oas/v3.2.0#operation-object)
 */
data class EndpointDefinition(
    /**
     * The value of the Path Item Key (e.g. "/users/{id}").
     * Not strictly inside the Operation Object, but required for context.
     */
    val path: String,

    /**
     * The HTTP method for this operation.
     */
    val method: HttpMethod,

    /**
     * Raw method token for [HttpMethod.CUSTOM] (e.g. "COPY", "PROPFIND").
     * Ignored for standard methods.
     */
    val customMethod: String? = null,

    /**
     * Unique string used to identify the operation. The id MUST be unique among all operations described in the API.
     * Tools and libraries MAY use the operationId to uniquely identify an operation.
     */
    val operationId: String,

    /**
     * Whether `operationId` was explicitly present in the OpenAPI document.
     * When false, serializers SHOULD omit `operationId` to preserve spec-legal omission.
     */
    val operationIdExplicit: Boolean = true,

    /**
     * A list of parameters that are applicable for this operation.
     */
    val parameters: List<EndpointParameter> = emptyList(),

    /**
     * The Kotlin class name of the request body, or null if none.
     * Abstracts `requestBody.content`.
     * Legacy-friendly for Swagger 2.0 / OAS 3.0 style generators.
     */
    val requestBodyType: String? = null,

    /**
     * The request body applicable for this operation.
     */
    val requestBody: RequestBody? = null,

    /**
     * The list of possible responses as they are returned from executing this operation.
     */
    val responses: Map<String, EndpointResponse> = emptyMap(),

    /**
     * A short summary of what the operation does.
     */
    val summary: String? = null,

    /**
     * A verbose explanation of the operation behavior.
     */
    val description: String? = null,

    /**
     * Additional external documentation for this operation.
     */
    val externalDocs: ExternalDocumentation? = null,

    /**
     * A list of tags for API documentation control.
     * Tags can be used for logical grouping of operations by resources or any other qualifier.
     */
    val tags: List<String> = emptyList(),

    /**
     * A map of possible out-of band callbacks related to the parent operation.
     * The key is a unique identifier for the Callback Object.
     * Each value is either an inline map of Runtime Expressions to Path Items
     * or a Reference Object to a reusable callback definition.
     *
     * Example:
     * `{"onDataProcessed": Callback.Inline(expressions = {"{$request.query.callbackUrl}": PathItem(...)})}`
     *
     * See [Callback Object](https://spec.openapis.org/oas/v3.2.0#callback-object)
     */
    val callbacks: Map<String, Callback> = emptyMap(),

    /**
     * Declares this operation to be deprecated. Consumers SHOULD refrain from usage of the declared operation.
     */
    val deprecated: Boolean = false,

    /**
     * A declaration of which security mechanisms can be used for this operation.
     * The list of values includes alternative Security Requirement Objects that can be used.
     */
    val security: List<SecurityRequirement> = emptyList(),

    /**
     * When true, serialize an explicit empty security array (`security: []`) for this operation.
     * This clears inherited security requirements from the OpenAPI root.
     */
    val securityExplicitEmpty: Boolean = false,

    /**
     * An alternative `servers` array to service this operation.
     * If specified, it overrides any servers defined at the Path Item or OpenAPI root.
     */
    val servers: List<Server> = emptyList(),

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
) {
    /**
     * Returns the raw HTTP method token for this operation.
     * For standard methods, this is the enum name (e.g. "GET").
     * For custom methods, this is [customMethod] or "CUSTOM".
     */
    val methodName: String
        get() = if (method == HttpMethod.CUSTOM) customMethod ?: "CUSTOM" else method.name

    /**
     * Helper to get the primary success type (lowest 2xx code).
     * Used for return type generation in Ktor clients.
     */
    val responseType: String?
        get() {
            // Find lowest 2xx code
            val success = responses.keys
                .filter { it.startsWith("2") }
                .minOrNull() ?: return null
            return responses[success]?.type
        }
}

/**
 * Describes the operations available on a single path.
 * A Path Item MAY be empty, due to ACL constraints.
 *
 * See [Path Item Object](https://spec.openapis.org/oas/v3.2.0#path-item-object)
 */
data class PathItem(
    /**
     * Allows for an external definition of this path item.
     */
    val ref: String? = null,

    /**
     * An optional, string summary, intended to apply to all operations in this path.
     */
    val summary: String? = null,

    /**
     * An optional, string description, intended to apply to all operations in this path.
     * CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /** A definition of a GET operation on this path. */
    val get: EndpointDefinition? = null,
    /** A definition of a PUT operation on this path. */
    val put: EndpointDefinition? = null,
    /** A definition of a POST operation on this path. */
    val post: EndpointDefinition? = null,
    /** A definition of a DELETE operation on this path. */
    val delete: EndpointDefinition? = null,
    /** A definition of a OPTIONS operation on this path. */
    val options: EndpointDefinition? = null,
    /** A definition of a HEAD operation on this path. */
    val head: EndpointDefinition? = null,
    /** A definition of a PATCH operation on this path. */
    val patch: EndpointDefinition? = null,
    /** A definition of a TRACE operation on this path. */
    val trace: EndpointDefinition? = null,
    /** A definition of a QUERY operation on this path (OAS 3.2). */
    val query: EndpointDefinition? = null,

    /**
     * A map of additional operations on this path.
     * The key is the HTTP method with the same capitalization that is to be sent in the request.
     * Must not contain methods covered by fixed fields (e.g. no `POST` entry).
     */
    val additionalOperations: Map<String, EndpointDefinition> = emptyMap(),

    /**
     * A list of parameters that are applicable for all the operations described under this path.
     * These parameters can be overridden at the operation level, but cannot be removed there.
     */
    val parameters: List<EndpointParameter> = emptyList(),

    /**
     * An alternative server array to service all operations in this path.
     */
    val servers: List<Server> = emptyList(),

    /**
     * Specification extensions (keys starting with `x-`).
     */
    val extensions: Map<String, Any?> = emptyMap()
)
