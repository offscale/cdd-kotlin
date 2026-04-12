package openapi

import domain.Components
import domain.EndpointDefinition
import domain.ExternalDocumentation
import domain.Info
import domain.OpenApiDefinition
import domain.OpenApiPathBuilder
import domain.SchemaDefinition
import domain.SecurityRequirement
import domain.Server
import domain.Tag
import domain.PathItem

/**
 * Builds an [OpenApiDefinition] from Kotlin-parsed domain models.
 *
 * This provides the Kotlin -> OpenAPI bridge by assembling
 * schemas into components and endpoints into paths.
 */
class OpenApiAssembler {

    /**
     * Assembles a complete [OpenApiDefinition].
     *
     * @param info Required API metadata.
     * @param schemas Schema definitions to include under components/schemas.
     * @param endpoints Endpoint definitions to include under paths.
     * @param servers Optional server list.
     * @param components Optional pre-built components (merged with schemas).
     * @param webhooks Optional webhook definitions.
     * @param security Optional global security requirements.
     * @param tags Optional tag metadata.
     * @param externalDocs Optional external documentation.
     * @param extensions Optional root-level OpenAPI extensions (x- fields).
     * @param openapi Version string to emit (defaults to 3.2.0).
     * @param jsonSchemaDialect Optional JSON Schema dialect URI.
     * @param self Optional $self URI reference.
     * @param pathItems Optional Path Item metadata to merge into built paths (summary/description/parameters/servers/extensions).
     * @param liftCommonPathMetadata When true, lift shared operation metadata (params/summary/description/servers)
     * to the Path Item for more concise OpenAPI output.
     */
    fun assemble(
        info: Info,
        schemas: List<SchemaDefinition> = emptyList(),
        endpoints: List<EndpointDefinition> = emptyList(),
        servers: List<Server> = emptyList(),
        components: Components? = null,
        webhooks: Map<String, PathItem> = emptyMap(),
        pathsExtensions: Map<String, Any?> = emptyMap(),
        webhooksExtensions: Map<String, Any?> = emptyMap(),
        pathsExplicitEmpty: Boolean = false,
        webhooksExplicitEmpty: Boolean = false,
        pathItems: Map<String, PathItem> = emptyMap(),
        security: List<SecurityRequirement> = emptyList(),
        securityExplicitEmpty: Boolean = false,
        tags: List<Tag> = emptyList(),
        externalDocs: ExternalDocumentation? = null,
        extensions: Map<String, Any?> = emptyMap(),
        openapi: String = "3.2.0",
        jsonSchemaDialect: String? = null,
        self: String? = null,
        liftCommonPathMetadata: Boolean = false
    ): OpenApiDefinition {
        val builtPaths = OpenApiPathBuilder.buildPaths(endpoints, liftCommonPathMetadata)
        val paths = mergePathItems(builtPaths, pathItems)
        val mergedComponents = mergeComponents(components, schemas)

        return OpenApiDefinition(
            openapi = openapi,
            info = info,
            jsonSchemaDialect = jsonSchemaDialect,
            servers = servers,
            paths = paths,
            pathsExtensions = pathsExtensions,
            pathsExplicitEmpty = pathsExplicitEmpty,
            webhooks = webhooks,
            webhooksExtensions = webhooksExtensions,
            webhooksExplicitEmpty = webhooksExplicitEmpty,
            components = mergedComponents,
            security = security,
            securityExplicitEmpty = securityExplicitEmpty,
            tags = tags,
            externalDocs = externalDocs,
            extensions = extensions,
            self = self
        )
    }

    private fun mergeComponents(
        base: Components?,
        schemas: List<SchemaDefinition>
    ): Components? {
        val schemaMap = schemas.associateBy { it.name }
        if (base == null && schemaMap.isEmpty()) return null

        val mergedSchemas = LinkedHashMap<String, SchemaDefinition>()
        mergedSchemas.putAll(schemaMap)
        if (base != null) {
            mergedSchemas.putAll(base.schemas)
            return base.copy(schemas = mergedSchemas)
        }

        return Components(schemas = mergedSchemas)
    }

    private fun mergePathItems(
        base: Map<String, PathItem>,
        overrides: Map<String, PathItem>
    ): Map<String, PathItem> {
        if (overrides.isEmpty()) return base
        val merged = LinkedHashMap<String, PathItem>()
        merged.putAll(base)

        overrides.forEach { (path, meta) ->
            val existing = merged[path]
            merged[path] = when {
                meta.ref != null -> meta
                existing == null -> meta
                else -> existing.copy(
                    summary = meta.summary ?: existing.summary,
                    description = meta.description ?: existing.description,
                    parameters = if (meta.parameters.isNotEmpty()) meta.parameters else existing.parameters,
                    servers = if (meta.servers.isNotEmpty()) meta.servers else existing.servers,
                    extensions = mergeExtensions(existing.extensions, meta.extensions)
                )
            }
        }

        return merged
    }

    private fun mergeExtensions(
        base: Map<String, Any?>,
        overrides: Map<String, Any?>
    ): Map<String, Any?> {
        if (base.isEmpty() && overrides.isEmpty()) return emptyMap()
        val merged = LinkedHashMap<String, Any?>()
        merged.putAll(base)
        merged.putAll(overrides)
        return merged
    }
}
