/**
 * Multiplatform JSON Schema Draft 2020-12 `$dynamicAnchor` and `$dynamicRef` resolution.
 *
 * Uses [IdentityKey] to track object identity across all Kotlin Multiplatform targets (JVM, WASM)
 * without relying on `java.util.IdentityHashMap`.
 */
package domain

import openapi.percentDecode

/**
 * Multiplatform identity wrapper that compares and hashes [value] by object reference (`===`)
 * rather than structural equality (`==`).
 *
 * @param T The wrapped reference type.
 * @property value The underlying object instance.
 */
class IdentityKey<T : Any>(val value: T) {
  override fun equals(other: Any?): Boolean = other is IdentityKey<*> && other.value === value

  override fun hashCode(): Int = value.hashCode()
}

/**
 * Holds the dynamic anchor state for a single schema resource.
 *
 * @property anchors Map of anchor names to their defining [SchemaProperty] nodes.
 */
data class DynamicAnchorResource(val anchors: Map<String, SchemaProperty>)

/**
 * Provides dynamic anchor resolution for schema properties in a document.
 *
 * @property scopeBySchema Map from [IdentityKey] of a [SchemaProperty] to its active stack of
 *   [DynamicAnchorResource]s.
 */
class DynamicAnchorScope(
    private val scopeBySchema: Map<IdentityKey<SchemaProperty>, List<DynamicAnchorResource>>
) {
  /**
   * Resolve a `$dynamicRef` using the dynamic anchor stack available to [schema]. Returns the
   * resolved schema when a matching dynamic anchor is found.
   *
   * @param schema The context [SchemaProperty] where `$dynamicRef` is evaluated.
   * @param ref The `$dynamicRef` URI/fragment string.
   * @return The resolved [SchemaProperty], or `null` if no matching dynamic anchor is in scope.
   */
  fun resolveDynamicRef(schema: SchemaProperty, ref: String): SchemaProperty? {
    val anchorName = extractDynamicAnchorName(ref) ?: return null
    val resources = scopeBySchema[IdentityKey(schema)] ?: return null
    var index = resources.size - 1
    while (index >= 0) {
      val anchor = resources[index].anchors[anchorName]
      if (anchor != null) return anchor
      index--
    }
    return null
  }
}

/**
 * Bundles the root schema property with its dynamic anchor scope.
 *
 * @property root The root [SchemaProperty].
 * @property scope The computed [DynamicAnchorScope] for the root.
 */
data class DynamicAnchorContext(val root: SchemaProperty, val scope: DynamicAnchorScope)

/**
 * Build a dynamic anchor context for a root [SchemaDefinition].
 *
 * @param definition The root [SchemaDefinition].
 * @return The constructed [DynamicAnchorContext].
 */
fun buildDynamicAnchorContext(definition: SchemaDefinition): DynamicAnchorContext {
  val root = definition.toSchemaProperty()
  val scope = buildDynamicAnchorScope(root)
  return DynamicAnchorContext(root, scope)
}

/**
 * Build a dynamic anchor context for a root [SchemaProperty].
 *
 * @param schema The root [SchemaProperty].
 * @return The constructed [DynamicAnchorContext].
 */
fun buildDynamicAnchorContext(schema: SchemaProperty): DynamicAnchorContext {
  val scope = buildDynamicAnchorScope(schema)
  return DynamicAnchorContext(schema, scope)
}

/**
 * Build a dynamic anchor scope for a schema resource tree rooted at [root].
 *
 * @param root The root [SchemaProperty] to traverse.
 * @return The populated [DynamicAnchorScope].
 */
fun buildDynamicAnchorScope(root: SchemaProperty): DynamicAnchorScope {
  val scopeBySchema = LinkedHashMap<IdentityKey<SchemaProperty>, List<DynamicAnchorResource>>()
  val resourceCache = LinkedHashMap<IdentityKey<SchemaProperty>, DynamicAnchorResource>()

  /**
   * @param node Schema node to compute dynamic anchors for.
   * @return The [DynamicAnchorResource] for [node].
   */
  fun resourceFor(node: SchemaProperty): DynamicAnchorResource {
    val key = IdentityKey(node)
    return resourceCache[key]
        ?: run {
          val anchors = LinkedHashMap<String, SchemaProperty>()
          collectResourceAnchors(node, anchors)
          val resource = DynamicAnchorResource(anchors)
          resourceCache[key] = resource
          resource
        }
  }

  /**
   * @param node Current schema node.
   * @param stack Active dynamic anchor resource stack.
   */
  fun traverse(node: SchemaProperty, stack: List<DynamicAnchorResource>) {
    val isNewResource = !node.schemaId.isNullOrBlank()
    val nextStack =
        if (stack.isEmpty() || isNewResource) {
          stack + resourceFor(node)
        } else {
          stack
        }
    scopeBySchema[IdentityKey(node)] = nextStack
    node.forEachSubschema { child -> traverse(child, nextStack) }
  }

  traverse(root, emptyList())
  return DynamicAnchorScope(scopeBySchema)
}

private fun collectResourceAnchors(
    node: SchemaProperty,
    anchors: MutableMap<String, SchemaProperty>
) {
  val anchorName = node.dynamicAnchor?.trim().orEmpty()
  if (anchorName.isNotBlank()) {
    anchors[anchorName] = node
  }
  node.forEachSubschema { child ->
    if (child.schemaId.isNullOrBlank()) {
      collectResourceAnchors(child, anchors)
    }
  }
}

private fun SchemaProperty.forEachSubschema(block: (SchemaProperty) -> Unit) {
  defs.values.forEach(block)
  items?.let(block)
  prefixItems.forEach(block)
  contains?.let(block)
  properties.values.forEach(block)
  patternProperties.values.forEach(block)
  propertyNames?.let(block)
  additionalProperties?.let(block)
  dependentSchemas.values.forEach(block)
  unevaluatedProperties?.let(block)
  unevaluatedItems?.let(block)
  contentSchema?.let(block)
  oneOf.forEach(block)
  anyOf.forEach(block)
  allOf.forEach(block)
  not?.let(block)
  ifSchema?.let(block)
  thenSchema?.let(block)
  elseSchema?.let(block)
}

/**
 * Extract a dynamic anchor name from a `$dynamicRef`.
 *
 * Dynamic anchors use plain-name fragments (e.g. `#foo`) rather than JSON Pointers.
 *
 * @param ref The `$dynamicRef` string.
 * @return The decoded anchor name, or `null` if [ref] has no plain-name fragment.
 */
fun extractDynamicAnchorName(ref: String): String? {
  val fragment = ref.substringAfter("#", "")
  if (fragment.isBlank()) return null
  if (fragment.startsWith("/")) return null
  return percentDecode(fragment)
}
