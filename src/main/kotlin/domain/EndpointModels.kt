package domain

/**
 * Supported HTTP Methods for Ktor generation.
 */
enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
}

/**
 * logic location of a parameter in an HTTP request.
 */
enum class ParameterLocation {
    PATH, QUERY, HEADER
}

/**
 * Describes a single parameter (Path variable, Query param, etc).
 */
data class EndpointParameter(
    val name: String,
    val type: String,
    val location: ParameterLocation,
    val isRequired: Boolean = true,
    val description: String? = null
)

/**
 * Describes a single API operation (endpoint).
 */
data class EndpointDefinition(
    /** The URL path, e.g. "/users/{id}" */
    val path: String,
    val method: HttpMethod,
    /** The function name, e.g. "getUserById" */
    val operationId: String,
    val parameters: List<EndpointParameter> = emptyList(),
    /** The Kotlin class name of the request body, or null if none. */
    val requestBodyType: String? = null,
    /** The Kotlin class name of the response, or null if Unit/void. */
    val responseType: String? = null,
    val summary: String? = null
)
