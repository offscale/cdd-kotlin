package openapi

import domain.OpenApiDefinition
import domain.SchemaProperty
import domain.PathItemRefResolver
import domain.PathItemResolution
import java.net.URI

/**
 * In-memory registry for OpenAPI/Schema documents to resolve $ref targets without network access.
 *
 * Register documents with their intended base URI (e.g., retrieval URI) and/or `$self`/`$id`.
 * Relative `$self`/`$id` values are resolved against the provided base URI for lookup purposes.
 * The parser can then resolve cross-document component references against this registry.
 */
class OpenApiDocumentRegistry {
    private val documents = LinkedHashMap<String, OpenApiDocument>()

    /**
     * Register any OpenAPI Description document.
     *
     * @param document Parsed OpenAPI or Schema document.
     * @param baseUri Optional base URI (e.g., retrieval URI) used to locate the document and resolve relative `$self`/`$id` values.
     */
    fun registerDocument(document: OpenApiDocument, baseUri: String? = null) {
        val normalizedBase = normalizeBase(baseUri)
        registerInternal(normalizedBase, document)
        when (document) {
            is OpenApiDocument.OpenApi -> {
                registerInternal(document.definition.self, document)
                registerInternal(resolveRelative(document.definition.self, normalizedBase), document)
            }
            is OpenApiDocument.Schema -> {
                registerInternal(document.schema.schemaId, document)
                registerInternal(resolveRelative(document.schema.schemaId, normalizedBase), document)
            }
        }
    }

    /**
     * Register an OpenAPI Object document.
     */
    fun registerOpenApi(definition: OpenApiDefinition, baseUri: String? = null) {
        registerDocument(OpenApiDocument.OpenApi(definition), baseUri)
    }

    /**
     * Register a standalone Schema Object document.
     */
    fun registerSchema(schema: SchemaProperty, baseUri: String? = null) {
        registerDocument(OpenApiDocument.Schema(schema), baseUri)
    }

    /**
     * Resolve any registered document by base URI.
     */
    fun resolve(baseUri: String?): OpenApiDocument? {
        val normalized = normalizeBase(baseUri) ?: return null
        return documents[normalized]
    }

    /**
     * Resolve an OpenAPI Object document by base URI.
     */
    fun resolveOpenApi(baseUri: String?): OpenApiDefinition? {
        return (resolve(baseUri) as? OpenApiDocument.OpenApi)?.definition
    }

    /**
     * Resolve a Schema Object document by base URI.
     */
    fun resolveSchema(baseUri: String?): SchemaProperty? {
        return (resolve(baseUri) as? OpenApiDocument.Schema)?.schema
    }

    private fun registerInternal(uri: String?, document: OpenApiDocument) {
        val normalized = normalizeBase(uri) ?: return
        documents[normalized] = document
    }

}

/**
 * Builds a [PathItemRefResolver] that resolves external Path Item $ref targets from this registry.
 */
fun OpenApiDocumentRegistry.pathItemResolver(): PathItemRefResolver {
    return PathItemRefResolver { baseUri, key ->
        val definition = resolveOpenApi(baseUri) ?: return@PathItemRefResolver null
        val item = definition.components?.pathItems?.get(key) ?: return@PathItemRefResolver null
        val resolvedSelf = run {
            val normalizedBase = normalizeBase(baseUri)
            val normalizedSelf = normalizeBase(definition.self)
            if (normalizedSelf == null || normalizedBase == null || isAbsoluteUri(normalizedSelf)) {
                normalizedSelf ?: definition.self
            } else {
                try {
                    URI(normalizedBase).resolve(normalizedSelf).toString().substringBefore("#")
                } catch (_: Exception) {
                    normalizedSelf
                }
            }
        }
        PathItemResolution(item = item, components = definition.components, selfBase = resolvedSelf)
    }
}

private fun normalizeBase(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return trimmed.substringBefore("#")
}

private fun resolveRelative(reference: String?, baseUri: String?): String? {
    val normalized = normalizeBase(reference) ?: return null
    if (baseUri.isNullOrBlank()) return normalized
    if (isAbsoluteUri(normalized)) return normalized
    return try {
        URI(baseUri).resolve(normalized).toString().substringBefore("#")
    } catch (_: Exception) {
        normalized
    }
}

private fun isAbsoluteUri(value: String): Boolean {
    return try {
        URI(value).isAbsolute
    } catch (_: Exception) {
        false
    }
}
