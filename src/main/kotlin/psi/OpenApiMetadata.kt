package psi

import domain.ExternalDocumentation
import domain.Info
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
     * Extensions applied to the root Webhooks Object (x- fields).
     */
    val webhooksExtensions: Map<String, Any?> = emptyMap(),

    /**
     * Root-level security schemes (components.securitySchemes).
     */
    val securitySchemes: Map<String, SecurityScheme> = emptyMap()
)
