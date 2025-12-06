package domain

/**
 * Supported HTTP Methods for API operations.
 * Updated to include QUERY (OAS 3.2) support.
 *
 * See [Path Item Object](https://spec.openapis.org/oas/v3.2.0#path-item-object)
 */
enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE, QUERY
}

/**
 * The location of the parameter.
 * Possible values are "query", "header", "path" or "cookie".
 *
 * See [Parameter Object](https://spec.openapis.org/oas/v3.2.0#parameter-object)
 */
enum class ParameterLocation {
    PATH, QUERY, HEADER, COOKIE
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
    DEEP_OBJECT
}

/**
 * Describes a single operation parameter.
 * A unique parameter is defined by a combination of a name and location.
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
     * A brief description of the parameter. This could contain examples of use.
     * CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

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
    val allowReserved: Boolean? = null
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
     * Describes how the header value will be serialized.
     * The default (and only legal value for headers) is "simple".
     */
    val style: ParameterStyle? = ParameterStyle.SIMPLE,

    /**
     * When this is true, header values of type `array` or `object` generate a single header whose value
     * is a comma-separated list of the array items or key-value pairs of the map.
     * The default value is `false`.
     */
    val explode: Boolean? = false
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
     * A description of the response. CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /**
     * The Kotlin class name of the response payload, or null if no content is returned (Unit).
     * This abstracts the `content` map for simplified code generation.
     */
    val type: String? = null,

    /**
     * A map of operations links that can be followed from the response.
     * The key of the map is a short name for the link.
     */
    val links: Map<String, Link>? = null
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
    val parameters: Map<String, String> = emptyMap(),

    /**
     * A literal value or {expression} to use as a request body when calling the target operation.
     */
    val requestBody: String? = null,

    /**
     * A description of the link. CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,

    /**
     * A server object to be used by the target operation.
     */
    val server: Server? = null
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
     * Unique string used to identify the operation. The id MUST be unique among all operations described in the API.
     * Tools and libraries MAY use the operationId to uniquely identify an operation.
     */
    val operationId: String,

    /**
     * A list of parameters that are applicable for this operation.
     */
    val parameters: List<EndpointParameter> = emptyList(),

    /**
     * The Kotlin class name of the request body, or null if none.
     * Abstracts `requestBody.content`.
     */
    val requestBodyType: String? = null,

    /**
     * The list of possible responses as they are returned from executing this operation.
     */
    val responses: Map<String, EndpointResponse> = emptyMap(),

    /**
     * A short summary of what the operation does.
     */
    val summary: String? = null,

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
     * Each value in the map is a map of Runtime Expressions to Path Items.
     *
     * Example:
     * `{"onDataProcessed": {"{$request.query.callbackUrl}": PathItem(...)}}`
     *
     * See [Callback Object](https://spec.openapis.org/oas/v3.2.0#callback-object)
     */
    val callbacks: Map<String, Map<String, PathItem>> = emptyMap(),

    /**
     * Declares this operation to be deprecated. Consumers SHOULD refrain from usage of the declared operation.
     */
    val deprecated: Boolean = false,

    /**
     * A declaration of which security mechanisms can be used for this operation.
     * The list of values includes alternative Security Requirement Objects that can be used.
     */
    val security: List<SecurityRequirement> = emptyList()
) {
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
     * A list of parameters that are applicable for all the operations described under this path.
     * These parameters can be overridden at the operation level, but cannot be removed there.
     */
    val parameters: List<EndpointParameter> = emptyList(),

    /**
     * An alternative server array to service all operations in this path.
     */
    val servers: List<Server> = emptyList()
)
