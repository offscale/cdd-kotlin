/** Root-level OpenAPI metadata model and merge/assembly helpers for Kotlin KDoc round-tripping. */
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
import domain.OpenApiValue
import domain.PathItem
import domain.RequestBody
import domain.SchemaDefinition
import domain.SecurityRequirement
import domain.SecurityScheme
import domain.Server
import domain.Tag
import psi.metadata.ComponentsMetadataCodec

/**
 * Root-level OpenAPI metadata captured from interface-level KDoc. This mirrors the OpenAPI Object
 * fields that can be round-tripped from Kotlin.
 *
 * Encoders and decoders per domain area are provided by [psi.metadata.InfoMetadataCodec],
 * [psi.metadata.ComponentsMetadataCodec], [psi.metadata.SecurityMetadataCodec], and
 * [psi.metadata.TagsMetadataCodec].
 */
data class OpenApiMetadata(
    /** OpenAPI version string (e.g., "3.2.0"). */
    val openapi: String? = null,

    /** Default JSON Schema dialect for Schema Objects. */
    val jsonSchemaDialect: String? = null,

    /** Optional `$self` URI for the OpenAPI document. */
    val self: String? = null,

    /** Required OpenAPI Info object. */
    val info: Info? = null,

    /** Root-level server list. */
    val servers: List<Server> = emptyList(),

    /** Root-level security requirements. */
    val security: List<SecurityRequirement> = emptyList(),

    /** When true, serialize an explicit empty security array (`security: []`). */
    val securityExplicitEmpty: Boolean = false,

    /** Root-level tag metadata. */
    val tags: List<Tag> = emptyList(),

    /** Root-level external documentation. */
    val externalDocs: ExternalDocumentation? = null,

    /** Root-level extensions (x- fields). */
    val extensions: Map<String, OpenApiValue> = emptyMap(),

    /** Extensions applied to the root Paths Object (x- fields). */
    val pathsExtensions: Map<String, OpenApiValue> = emptyMap(),

    /**
     * When true, serialize an explicit empty Paths Object (`paths: {}`). This preserves ACL-style
     * "no paths available" semantics distinct from omission.
     */
    val pathsExplicitEmpty: Boolean = false,

    /**
     * Root-level Path Item metadata keyed by path (e.g. "/pets"). Intended for path-level
     * summary/description/parameters/servers/extensions or $ref.
     */
    val pathItems: Map<String, PathItem> = emptyMap(),

    /** Root-level webhooks map (OpenAPI `webhooks`). */
    val webhooks: Map<String, PathItem> = emptyMap(),

    /** Extensions applied to the root Webhooks Object (x- fields). */
    val webhooksExtensions: Map<String, OpenApiValue> = emptyMap(),

    /**
     * When true, serialize an explicit empty Webhooks Object (`webhooks: {}`). This preserves
     * ACL-style "no webhooks available" semantics distinct from omission.
     */
    val webhooksExplicitEmpty: Boolean = false,

    /** Root-level security schemes (components.securitySchemes). */
    val securitySchemes: Map<String, SecurityScheme> = emptyMap(),

    /** Reusable Schema Objects (components.schemas) preserved via metadata. */
    val componentSchemas: Map<String, SchemaDefinition> = emptyMap(),

    /** Reusable Example Objects (components.examples). */
    val componentExamples: Map<String, ExampleObject> = emptyMap(),

    /** Reusable Link Objects (components.links). */
    val componentLinks: Map<String, Link> = emptyMap(),

    /** Reusable Callback Objects (components.callbacks). */
    val componentCallbacks: Map<String, Callback> = emptyMap(),
    /** Reusable Parameter Objects (components.parameters). */
    val componentParameters: Map<String, EndpointParameter> = emptyMap(),

    /** Reusable Response Objects (components.responses). */
    val componentResponses: Map<String, EndpointResponse> = emptyMap(),

    /** Reusable Request Body Objects (components.requestBodies). */
    val componentRequestBodies: Map<String, RequestBody> = emptyMap(),

    /** Reusable Header Objects (components.headers). */
    val componentHeaders: Map<String, Header> = emptyMap(),

    /** Reusable Path Item Objects (components.pathItems). */
    val componentPathItems: Map<String, PathItem> = emptyMap(),

    /** Reusable Media Type Objects (components.mediaTypes). */
    val componentMediaTypes: Map<String, MediaTypeObject> = emptyMap(),

    /** Specification extensions applied to the Components Object (x- fields). */
    val componentsExtensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Builds a Components object from metadata component maps.
 *
 * @param base Optional base components to merge into. Metadata keys override base keys on
 *   conflicts.
 * @return The merged [Components], or `null` if empty.
 */
fun OpenApiMetadata.toComponents(base: Components? = null): Components? =
    ComponentsMetadataCodec.toComponents(this, base)

/**
 * Builds OpenAPI metadata from a full [OpenApiDefinition].
 *
 * This is the OpenAPI -> Kotlin bridge for preserving root-level metadata and non-schema Components
 * through Kotlin round-trips.
 *
 * @param includePathItems When true, lifts path-level metadata into [OpenApiMetadata.pathItems].
 * @param includeComponents When true, copies non-schema component maps into metadata.
 * @param includeWebhooks When true, copies webhooks into metadata.
 * @param includeComponentSchemas When true, copies `components.schemas` into metadata.
 * @return The constructed [OpenApiMetadata].
 */
fun OpenApiDefinition.toMetadata(
    includePathItems: Boolean = true,
    includeComponents: Boolean = true,
    includeWebhooks: Boolean = true,
    includeComponentSchemas: Boolean = false
): OpenApiMetadata {
  val comps = (if (includeComponents) components else null) ?: Components()
  val pathItemMeta =
      if (includePathItems) ComponentsMetadataCodec.extractPathItemMetadata(paths) else emptyMap()
  val webhookItems = if (includeWebhooks) webhooks else emptyMap()
  val componentSchemas = if (includeComponentSchemas) comps.schemas else emptyMap()

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
      securitySchemes = comps.securitySchemes,
      componentSchemas = componentSchemas,
      componentExamples = comps.examples,
      componentLinks = comps.links,
      componentCallbacks = comps.callbacks,
      componentParameters = comps.parameters,
      componentResponses = comps.responses,
      componentRequestBodies = comps.requestBodies,
      componentHeaders = comps.headers,
      componentPathItems = comps.pathItems,
      componentMediaTypes = comps.mediaTypes,
      componentsExtensions = comps.extensions)
}
