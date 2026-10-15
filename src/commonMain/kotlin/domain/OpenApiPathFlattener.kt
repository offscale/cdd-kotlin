/**
 * Flattens OpenAPI 3.2 Paths, PathItems, and Webhooks into flat [EndpointDefinition] lists.
 *
 * Inverse of [OpenApiPathBuilder]; supports `$ref` PathItem resolution and `$self`-aware base URI
 * matching across all Kotlin Multiplatform targets.
 */
package domain

import openapi.decodeJsonPointerToken
import openapi.resolveUri

/**
 * Resolved Path Item metadata returned by [PathItemRefResolver].
 *
 * @property item The resolved [PathItem].
 * @property components Optional [Components] from the target document.
 * @property selfBase Optional normalized `$self` base URI of the target document.
 */
data class PathItemResolution(
    val item: PathItem,
    val components: Components? = null,
    val selfBase: String? = null
)

/** Resolves external Path Item `$ref` targets (e.g. external `#/components/pathItems/{name}`). */
fun interface PathItemRefResolver {
  /**
   * Resolves the target [PathItem] by base URI and component key.
   *
   * @param baseUri Base URI of the referenced document.
   * @param key Component key under `components/pathItems`.
   * @return The resolved [PathItemResolution], or `null` if not found.
   */
  fun resolve(baseUri: String?, key: String): PathItemResolution?
}

/** Utility to flatten OpenAPI Paths/PathItems into a list of [EndpointDefinition]s. */
object OpenApiPathFlattener {

  /**
   * Flattens an OpenAPI Paths map into a list of [EndpointDefinition]s.
   *
   * @param paths Map of path templates to [PathItem] objects.
   * @param components Optional [Components] for resolving local `pathItems` references.
   * @param self Optional `$self` URI of the current document.
   * @param refResolver Optional external [PathItemRefResolver].
   * @return Ordered list of flattened [EndpointDefinition]s.
   */
  fun flattenPaths(
      paths: Map<String, PathItem>,
      components: Components? = null,
      self: String? = null,
      refResolver: PathItemRefResolver? = null
  ): List<EndpointDefinition> {
    if (paths.isEmpty()) return emptyList()
    val selfBase = normalizeSelfBase(self)
    return paths.flatMap { (path, item) ->
      flattenPathItem(path, item, components, selfBase, refResolver)
    }
  }

  /**
   * Flattens a Webhooks map into [EndpointDefinition]s.
   *
   * @param webhooks Map of webhook names to [PathItem] objects.
   * @param components Optional [Components] for resolving local `pathItems` references.
   * @param self Optional `$self` URI of the current document.
   * @param refResolver Optional external [PathItemRefResolver].
   * @return Ordered list of flattened [EndpointDefinition]s.
   */
  fun flattenWebhooks(
      webhooks: Map<String, PathItem>,
      components: Components? = null,
      self: String? = null,
      refResolver: PathItemRefResolver? = null
  ): List<EndpointDefinition> {
    return flattenPaths(webhooks, components, self, refResolver)
  }

  /**
   * Flattens both Paths and Webhooks into a single [EndpointDefinition] list.
   *
   * @param paths Map of path templates to [PathItem] objects.
   * @param webhooks Map of webhook names to [PathItem] objects.
   * @param components Optional [Components] for resolving local `pathItems` references.
   * @param self Optional `$self` URI of the current document.
   * @param refResolver Optional external [PathItemRefResolver].
   * @return Combined list of flattened [EndpointDefinition]s.
   */
  fun flattenAll(
      paths: Map<String, PathItem>,
      webhooks: Map<String, PathItem>,
      components: Components? = null,
      self: String? = null,
      refResolver: PathItemRefResolver? = null
  ): List<EndpointDefinition> {
    if (paths.isEmpty() && webhooks.isEmpty()) return emptyList()
    val endpoints = mutableListOf<EndpointDefinition>()
    endpoints += flattenPaths(paths, components, self, refResolver)
    endpoints += flattenWebhooks(webhooks, components, self, refResolver)
    return endpoints
  }

  /**
   * Flattens a single [PathItem] into [EndpointDefinition]s for each defined operation.
   *
   * @param path Path template or webhook identifier.
   * @param item The [PathItem] to flatten.
   * @param components Optional [Components] for resolving local `pathItems` references.
   * @param selfBase Optional normalized `$self` base URI.
   * @param refResolver Optional external [PathItemRefResolver].
   * @return Flattened [EndpointDefinition]s for this path item.
   */
  fun flattenPathItem(
      path: String,
      item: PathItem,
      components: Components? = null,
      selfBase: String? = null,
      refResolver: PathItemRefResolver? = null
  ): List<EndpointDefinition> {
    val endpoints = mutableListOf<EndpointDefinition>()

    val resolved = resolvePathItem(item, components, selfBase, refResolver)
    if (resolved != null) {
      addIfPresent(endpoints, path, HttpMethod.GET, resolved, resolved.get)
      addIfPresent(endpoints, path, HttpMethod.PUT, resolved, resolved.put)
      addIfPresent(endpoints, path, HttpMethod.POST, resolved, resolved.post)
      addIfPresent(endpoints, path, HttpMethod.DELETE, resolved, resolved.delete)
      addIfPresent(endpoints, path, HttpMethod.OPTIONS, resolved, resolved.options)
      addIfPresent(endpoints, path, HttpMethod.HEAD, resolved, resolved.head)
      addIfPresent(endpoints, path, HttpMethod.PATCH, resolved, resolved.patch)
      addIfPresent(endpoints, path, HttpMethod.TRACE, resolved, resolved.trace)
      addIfPresent(endpoints, path, HttpMethod.QUERY, resolved, resolved.query)
      addAdditionalOperations(endpoints, path, resolved)
    }

    return endpoints
  }

  private fun resolvePathItem(
      item: PathItem,
      components: Components?,
      selfBase: String?,
      refResolver: PathItemRefResolver?
  ): PathItem? {
    val ref = item.ref ?: return item
    val baseComponents = components ?: Components()
    val visited = mutableSetOf<String>()
    return resolvePathItemRef(item, ref, baseComponents, visited, selfBase, refResolver)
  }

  private fun resolvePathItemRef(
      item: PathItem,
      ref: String,
      components: Components,
      visited: MutableSet<String>,
      selfBase: String?,
      refResolver: PathItemRefResolver?
  ): PathItem? {
    if (!visited.add(ref)) return null
    val resolution = resolveComponentPathItem(ref, components, selfBase, refResolver) ?: return null
    val target = resolution.item
    val targetComponents = resolution.components ?: components
    val targetSelfBase = resolution.selfBase ?: selfBase
    val targetRef = target.ref
    val resolvedTarget =
        if (targetRef != null) {
          resolvePathItemRef(
              target, targetRef, targetComponents, visited, targetSelfBase, refResolver)
        } else {
          target
        }
    val base = resolvedTarget ?: target
    return base.copy(
        summary = item.summary ?: base.summary,
        description = item.description ?: base.description,
        parameters = if (item.parameters.isNotEmpty()) item.parameters else base.parameters,
        servers = if (item.servers.isNotEmpty()) item.servers else base.servers,
        extensions = mergeExtensions(base.extensions, item.extensions))
  }

  private fun resolveComponentPathItem(
      ref: String,
      components: Components,
      selfBase: String?,
      refResolver: PathItemRefResolver?
  ): PathItemResolution? {
    val componentRef = extractComponentRef(ref, "pathItems", selfBase) ?: return null
    val base = componentRef.base
    val key = componentRef.key
    if (base == null || isSelfBaseMatch(base, selfBase)) {
      val local = components.pathItems[key]
      if (local != null) {
        return PathItemResolution(local, components, selfBase)
      }
      return if (base != null && refResolver != null) refResolver.resolve(base, key) else null
    }
    return refResolver?.resolve(base, key)
  }

  private data class ComponentRef(val base: String?, val key: String)

  private fun extractComponentRef(
      ref: String,
      component: String,
      selfBase: String?
  ): ComponentRef? {
    val marker = "#/components/$component/"
    val index = ref.indexOf(marker)
    if (index < 0) return null
    val refBase = ref.substring(0, index)
    val raw = ref.substring(index + marker.length)
    if (raw.isBlank() || raw.contains("/")) return null
    val key = decodeJsonPointerToken(raw)
    val base = resolveReferenceBase(refBase, selfBase)
    return ComponentRef(base = base, key = key)
  }

  private fun normalizeSelfBase(self: String?): String? {
    val trimmed = self?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return trimmed.substringBefore("#")
  }

  private fun resolveReferenceBase(refBase: String, contextBase: String?): String? {
    val normalized = normalizeSelfBase(refBase) ?: return contextBase
    val resolved =
        if (contextBase != null) resolveAgainstBase(contextBase, normalized) else normalized
    return normalizeSelfBase(resolved)
  }

  private fun resolveAgainstBase(base: String, ref: String): String {
    if (base.any { it.isWhitespace() } || ref.any { it.isWhitespace() }) {
      return ref
    }
    return resolveUri(base, ref)
  }

  private fun isSelfBaseMatch(refBase: String, selfBase: String?): Boolean {
    val normalizedSelf = selfBase?.trimEnd('#').orEmpty()
    if (normalizedSelf.isBlank()) return true
    return refBase.trimEnd('#') == normalizedSelf
  }

  private fun mergeExtensions(
      base: Map<String, OpenApiValue>,
      overrides: Map<String, OpenApiValue>
  ): Map<String, OpenApiValue> {
    if (overrides.isEmpty()) return base
    if (base.isEmpty()) return overrides
    val merged = LinkedHashMap<String, OpenApiValue>()
    merged.putAll(base)
    merged.putAll(overrides)
    return merged
  }

  private fun addAdditionalOperations(
      target: MutableList<EndpointDefinition>,
      path: String,
      item: PathItem
  ) {
    if (item.additionalOperations.isEmpty()) return
    item.additionalOperations.forEach { (methodToken, op) ->
      val mergedParams = mergeParameters(item.parameters, op.parameters)
      val summary = op.summary ?: item.summary
      val description = op.description ?: item.description
      val servers = if (op.servers.isNotEmpty()) op.servers else item.servers

      target.add(
          op.copy(
              path = path,
              method = HttpMethod.CUSTOM,
              customMethod = methodToken,
              parameters = mergedParams,
              summary = summary,
              description = description,
              servers = servers))
    }
  }

  private fun addIfPresent(
      target: MutableList<EndpointDefinition>,
      path: String,
      method: HttpMethod,
      item: PathItem,
      op: EndpointDefinition?
  ) {
    if (op == null) return

    val mergedParams = mergeParameters(item.parameters, op.parameters)
    val summary = op.summary ?: item.summary
    val description = op.description ?: item.description
    val servers = if (op.servers.isNotEmpty()) op.servers else item.servers

    target.add(
        op.copy(
            path = path,
            method = method,
            parameters = mergedParams,
            summary = summary,
            description = description,
            servers = servers))
  }

  private fun mergeParameters(
      pathParams: List<EndpointParameter>,
      opParams: List<EndpointParameter>
  ): List<EndpointParameter> {
    if (pathParams.isEmpty()) return opParams
    if (opParams.isEmpty()) return pathParams

    val merged = LinkedHashMap<String, EndpointParameter>()
    /** Local helper doc */
    fun key(param: EndpointParameter): String = "${param.location}:${param.name}"

    pathParams.forEach { merged[key(it)] = it }
    opParams.forEach { merged[key(it)] = it }

    return merged.values.toList()
  }
}
