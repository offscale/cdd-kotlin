/**
 * Multiplatform in-memory registry for OpenAPI and standalone JSON Schema documents.
 *
 * Resolves cross-document `$ref` targets without network access across all targets (JVM, WASM).
 */
package openapi

import domain.OpenApiDefinition
import domain.PathItemRefResolver
import domain.PathItemResolution
import domain.SchemaProperty

/**
 * In-memory registry for OpenAPI/Schema documents to resolve `$ref` targets without network access.
 *
 * Register documents with their intended base URI (e.g., retrieval URI) and/or `$self`/`$id`.
 * Relative `$self`/`$id` values are resolved against the provided base URI for lookup purposes. The
 * parser can then resolve cross-document component references against this registry.
 */
class OpenApiDocumentRegistry {
  private val documents = LinkedHashMap<String, OpenApiDocument>()

  /**
   * Register any OpenAPI Description document.
   *
   * @param document Parsed OpenAPI or Schema document.
   * @param baseUri Optional base URI (e.g., retrieval URI) used to locate the document and resolve
   *   relative `$self`/`$id` values.
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
   *
   * @param definition The [OpenApiDefinition] to register.
   * @param baseUri Optional base URI for resolving relative references.
   */
  fun registerOpenApi(definition: OpenApiDefinition, baseUri: String? = null) {
    registerDocument(OpenApiDocument.OpenApi(definition), baseUri)
  }

  /**
   * Register a standalone Schema Object document.
   *
   * @param schema The [SchemaProperty] to register.
   * @param baseUri Optional base URI for resolving relative references.
   */
  fun registerSchema(schema: SchemaProperty, baseUri: String? = null) {
    registerDocument(OpenApiDocument.Schema(schema), baseUri)
  }

  /**
   * Resolve any registered document by base URI.
   *
   * @param baseUri The base URI to look up.
   * @return The registered [OpenApiDocument], or `null` if not found.
   */
  fun resolve(baseUri: String?): OpenApiDocument? {
    val normalized = normalizeBase(baseUri) ?: return null
    return documents[normalized]
  }

  /**
   * Resolve an OpenAPI Object document by base URI.
   *
   * @param baseUri The base URI to look up.
   * @return The registered [OpenApiDefinition], or `null` if not found or not an OpenAPI document.
   */
  fun resolveOpenApi(baseUri: String?): OpenApiDefinition? {
    return (resolve(baseUri) as? OpenApiDocument.OpenApi)?.definition
  }

  /**
   * Resolve a Schema Object document by base URI.
   *
   * @param baseUri The base URI to look up.
   * @return The registered [SchemaProperty], or `null` if not found or not a Schema document.
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
 * Builds a [PathItemRefResolver] that resolves external Path Item `$ref` targets from this
 * registry.
 *
 * @return A [PathItemRefResolver] backed by this [OpenApiDocumentRegistry].
 */
fun OpenApiDocumentRegistry.pathItemResolver(): PathItemRefResolver {
  return PathItemRefResolver { baseUri, key ->
    val normalizedBase = normalizeBase(baseUri) ?: return@PathItemRefResolver null
    val definition = resolveOpenApi(normalizedBase) ?: return@PathItemRefResolver null
    val pathItems = definition.components?.pathItems.orEmpty()
    val item = pathItems[key] ?: return@PathItemRefResolver null
    val resolvedSelf = run {
      val normalizedSelf = normalizeBase(definition.self)
      if (normalizedSelf == null || isAbsoluteUri(normalizedSelf)) {
        normalizedSelf ?: definition.self
      } else if ((normalizedBase + normalizedSelf).any { it.isWhitespace() }) {
        normalizedSelf
      } else {
        resolveUri(normalizedBase, normalizedSelf).substringBefore("#")
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
  if ((baseUri + normalized).any { it.isWhitespace() }) return normalized
  return resolveUri(baseUri, normalized).substringBefore("#")
}
