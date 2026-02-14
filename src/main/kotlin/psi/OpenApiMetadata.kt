package psi

import domain.Callback
import domain.Components
import domain.EndpointParameter
import domain.EndpointResponse
import domain.ExampleObject
import domain.ExternalDocumentation
import domain.Header
import domain.Info
import domain.Link
import domain.MediaTypeObject
import domain.OpenApiDefinition
import domain.PathItem
import domain.RequestBody
import domain.SchemaDefinition
import domain.SecurityRequirement
import domain.SecurityScheme
import domain.Server
import domain.Tag

/**
 * Root-level OpenAPI metadata captured from interface-level KDoc.
 * This mirrors the OpenAPI Object fields that can be round-tripped from Kotlin.
 */
data class OpenApiMetadata(
    /**
     * OpenAPI version string (e.g., "3.2.0").
     */
    val openapi: String? = null,

    /**
     * Default JSON Schema dialect for Schema Objects.
     */
    val jsonSchemaDialect: String? = null,

    /**
     * Optional `$self` URI for the OpenAPI document.
     */
    val self: String? = null,

    /**
     * Required OpenAPI Info object.
     */
    val info: Info? = null,

    /**
     * Root-level server list.
     */
    val servers: List<Server> = emptyList(),

    /**
     * Root-level security requirements.
     */
    val security: List<SecurityRequirement> = emptyList(),

    /**
     * When true, serialize an explicit empty security array (`security: []`).
     */
    val securityExplicitEmpty: Boolean = false,

    /**
     * Root-level tag metadata.
     */
    val tags: List<Tag> = emptyList(),

    /**
     * Root-level external documentation.
     */
    val externalDocs: ExternalDocumentation? = null,

    /**
     * Root-level extensions (x- fields).
     */
    val extensions: Map<String, Any?> = emptyMap(),

    /**
     * Extensions applied to the root Paths Object (x- fields).
     */
    val pathsExtensions: Map<String, Any?> = emptyMap(),

    /**
     * When true, serialize an explicit empty Paths Object (`paths: {}`).
     * This preserves ACL-style "no paths available" semantics distinct from omission.
     */
    val pathsExplicitEmpty: Boolean = false,

    /**
     * Root-level Path Item metadata keyed by path (e.g. "/pets").
     * Intended for path-level summary/description/parameters/servers/extensions or $ref.
     */
    val pathItems: Map<String, PathItem> = emptyMap(),

    /**
     * Root-level webhooks map (OpenAPI `webhooks`).
     */
    val webhooks: Map<String, PathItem> = emptyMap(),

    /**
     * Extensions applied to the root Webhooks Object (x- fields).
     */
    val webhooksExtensions: Map<String, Any?> = emptyMap(),

    /**
     * When true, serialize an explicit empty Webhooks Object (`webhooks: {}`).
     * This preserves ACL-style "no webhooks available" semantics distinct from omission.
     */
    val webhooksExplicitEmpty: Boolean = false,

    /**
     * Root-level security schemes (components.securitySchemes).
     */
    val securitySchemes: Map<String, SecurityScheme> = emptyMap(),

    /**
     * Reusable Schema Objects (components.schemas) preserved via metadata.
     */
    val componentSchemas: Map<String, SchemaDefinition> = emptyMap(),

    /**
     * Reusable Example Objects (components.examples).
     */
    val componentExamples: Map<String, ExampleObject> = emptyMap(),

    /**
     * Reusable Link Objects (components.links).
     */
    val componentLinks: Map<String, Link> = emptyMap(),

    /**
     * Reusable Callback Objects (components.callbacks).
     */
    val componentCallbacks: Map<String, Callback> = emptyMap(),
    /**
     * Reusable Parameter Objects (components.parameters).
     */
    val componentParameters: Map<String, EndpointParameter> = emptyMap(),

    /**
     * Reusable Response Objects (components.responses).
     */
    val componentResponses: Map<String, EndpointResponse> = emptyMap(),

    /**
     * Reusable Request Body Objects (components.requestBodies).
     */
    val componentRequestBodies: Map<String, RequestBody> = emptyMap(),

    /**
     * Reusable Header Objects (components.headers).
     */
    val componentHeaders: Map<String, Header> = emptyMap(),

    /**
     * Reusable Path Item Objects (components.pathItems).
     */
    val componentPathItems: Map<String, PathItem> = emptyMap(),

    /**
     * Reusable Media Type Objects (components.mediaTypes).
     */
    val componentMediaTypes: Map<String, MediaTypeObject> = emptyMap(),

    /**
     * Specification extensions applied to the Components Object (x- fields).
     */
    val componentsExtensions: Map<String, Any?> = emptyMap()
)

/**
 * Builds a Components object from metadata component maps.
 *
 * @param base Optional base components to merge into.
 *             Metadata keys override base keys on conflicts.
 */
fun OpenApiMetadata.toComponents(base: Components? = null): Components? {
    val hasMetadataComponents =
        securitySchemes.isNotEmpty() ||
            componentSchemas.isNotEmpty() ||
            componentExamples.isNotEmpty() ||
            componentLinks.isNotEmpty() ||
            componentCallbacks.isNotEmpty() ||
            componentParameters.isNotEmpty() ||
            componentResponses.isNotEmpty() ||
            componentRequestBodies.isNotEmpty() ||
            componentHeaders.isNotEmpty() ||
            componentPathItems.isNotEmpty() ||
            componentMediaTypes.isNotEmpty() ||
            componentsExtensions.isNotEmpty()

    if (base == null && !hasMetadataComponents) return null

    val seed = base ?: Components()
    val mergedSchemas = LinkedHashMap<String, SchemaDefinition>()
    mergedSchemas.putAll(seed.schemas)
    mergedSchemas.putAll(componentSchemas)

    val mergedSecuritySchemes = LinkedHashMap<String, SecurityScheme>()
    mergedSecuritySchemes.putAll(seed.securitySchemes)
    mergedSecuritySchemes.putAll(securitySchemes)

    val mergedExamples = LinkedHashMap<String, ExampleObject>()
    mergedExamples.putAll(seed.examples)
    mergedExamples.putAll(componentExamples)

    val mergedLinks = LinkedHashMap<String, Link>()
    mergedLinks.putAll(seed.links)
    mergedLinks.putAll(componentLinks)

    val mergedCallbacks = LinkedHashMap<String, Callback>()
    mergedCallbacks.putAll(seed.callbacks)
    mergedCallbacks.putAll(componentCallbacks)

    val mergedParameters = LinkedHashMap<String, EndpointParameter>()
    mergedParameters.putAll(seed.parameters)
    mergedParameters.putAll(componentParameters)

    val mergedResponses = LinkedHashMap<String, EndpointResponse>()
    mergedResponses.putAll(seed.responses)
    mergedResponses.putAll(componentResponses)

    val mergedRequestBodies = LinkedHashMap<String, RequestBody>()
    mergedRequestBodies.putAll(seed.requestBodies)
    mergedRequestBodies.putAll(componentRequestBodies)

    val mergedHeaders = LinkedHashMap<String, Header>()
    mergedHeaders.putAll(seed.headers)
    mergedHeaders.putAll(componentHeaders)

    val mergedPathItems = LinkedHashMap<String, PathItem>()
    mergedPathItems.putAll(seed.pathItems)
    mergedPathItems.putAll(componentPathItems)

    val mergedMediaTypes = LinkedHashMap<String, MediaTypeObject>()
    mergedMediaTypes.putAll(seed.mediaTypes)
    mergedMediaTypes.putAll(componentMediaTypes)

    val mergedExtensions = LinkedHashMap<String, Any?>()
    mergedExtensions.putAll(seed.extensions)
    mergedExtensions.putAll(componentsExtensions.filterKeys { it.startsWith("x-") })

    return seed.copy(
        schemas = mergedSchemas,
        securitySchemes = mergedSecuritySchemes,
        examples = mergedExamples,
        links = mergedLinks,
        callbacks = mergedCallbacks,
        parameters = mergedParameters,
        responses = mergedResponses,
        requestBodies = mergedRequestBodies,
        headers = mergedHeaders,
        pathItems = mergedPathItems,
        mediaTypes = mergedMediaTypes,
        extensions = mergedExtensions
    )
}

/**
 * Builds OpenAPI metadata from a full [OpenApiDefinition].
 *
 * This is the OpenAPI -> Kotlin bridge for preserving root-level metadata and
 * non-schema Components through Kotlin round-trips.
 *
 * @param includePathItems When true, lifts path-level metadata into [OpenApiMetadata.pathItems].
 * @param includeComponents When true, copies non-schema component maps into metadata.
 */
fun OpenApiDefinition.toMetadata(
    includePathItems: Boolean = true,
    includeComponents: Boolean = true,
    includeWebhooks: Boolean = true,
    includeComponentSchemas: Boolean = false
): OpenApiMetadata {
    val componentSource = if (includeComponents) components else null
    val pathItemMeta = if (includePathItems) extractPathItemMetadata(paths) else emptyMap()
    val webhookItems = if (includeWebhooks) webhooks else emptyMap()
    val componentSchemas = if (includeComponentSchemas) componentSource?.schemas.orEmpty() else emptyMap()

    return OpenApiMetadata(
        openapi = openapi,
        jsonSchemaDialect = jsonSchemaDialect,
        self = self,
        info = info,
        servers = servers,
        security = security,
        securityExplicitEmpty = securityExplicitEmpty,
        tags = tags,
        externalDocs = externalDocs,
        extensions = extensions,
        pathsExtensions = pathsExtensions,
        pathsExplicitEmpty = pathsExplicitEmpty,
        pathItems = pathItemMeta,
        webhooks = webhookItems,
        webhooksExtensions = webhooksExtensions,
        webhooksExplicitEmpty = webhooksExplicitEmpty,
        securitySchemes = componentSource?.securitySchemes.orEmpty(),
        componentSchemas = componentSchemas,
        componentExamples = componentSource?.examples.orEmpty(),
        componentLinks = componentSource?.links.orEmpty(),
        componentCallbacks = componentSource?.callbacks.orEmpty(),
        componentParameters = componentSource?.parameters.orEmpty(),
        componentResponses = componentSource?.responses.orEmpty(),
        componentRequestBodies = componentSource?.requestBodies.orEmpty(),
        componentHeaders = componentSource?.headers.orEmpty(),
        componentPathItems = componentSource?.pathItems.orEmpty(),
        componentMediaTypes = componentSource?.mediaTypes.orEmpty(),
        componentsExtensions = componentSource?.extensions.orEmpty()
    )
}

private fun extractPathItemMetadata(paths: Map<String, PathItem>): Map<String, PathItem> {
    if (paths.isEmpty()) return emptyMap()
    val result = LinkedHashMap<String, PathItem>()
    paths.forEach { (path, item) ->
        val metadata = PathItem(
            ref = item.ref,
            summary = item.summary,
            description = item.description,
            parameters = item.parameters,
            servers = item.servers,
            extensions = item.extensions
        )
        if (metadata.hasPathMetadata()) {
            result[path] = metadata
        }
    }
    return result
}

private fun PathItem.hasPathMetadata(): Boolean {
    return ref != null ||
        summary != null ||
        description != null ||
        parameters.isNotEmpty() ||
        servers.isNotEmpty() ||
        extensions.isNotEmpty()
}
