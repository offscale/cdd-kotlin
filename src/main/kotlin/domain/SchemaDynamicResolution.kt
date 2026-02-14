package domain

import java.util.IdentityHashMap

/**
 * Holds the dynamic anchor state for a single schema resource.
 */
data class DynamicAnchorResource(
    val anchors: Map<String, SchemaProperty>
)

/**
 * Provides dynamic anchor resolution for schema properties in a document.
 */
class DynamicAnchorScope(
    private val scopeBySchema: IdentityHashMap<SchemaProperty, List<DynamicAnchorResource>>
) {
    /**
     * Resolve a `$dynamicRef` using the dynamic anchor stack available to [schema].
     * Returns the resolved schema when a matching dynamic anchor is found.
     */
    fun resolveDynamicRef(schema: SchemaProperty, ref: String): SchemaProperty? {
        val anchorName = extractDynamicAnchorName(ref) ?: return null
        val resources = scopeBySchema[schema].orEmpty()
        for (index in resources.indices.reversed()) {
            resources[index].anchors[anchorName]?.let { return it }
        }
        return null
    }
}

/**
 * Bundles the root schema property with its dynamic anchor scope.
 */
data class DynamicAnchorContext(
    val root: SchemaProperty,
    val scope: DynamicAnchorScope
)

/**
 * Build a dynamic anchor context for a root schema definition.
 */
fun buildDynamicAnchorContext(definition: SchemaDefinition): DynamicAnchorContext {
    val root = definition.toSchemaProperty()
    val scope = buildDynamicAnchorScope(root)
    return DynamicAnchorContext(root, scope)
}

/**
 * Build a dynamic anchor context for a root schema property.
 */
fun buildDynamicAnchorContext(schema: SchemaProperty): DynamicAnchorContext {
    val scope = buildDynamicAnchorScope(schema)
    return DynamicAnchorContext(schema, scope)
}

/**
 * Build a dynamic anchor scope for a schema resource.
 */
fun buildDynamicAnchorScope(root: SchemaProperty): DynamicAnchorScope {
    val scopeBySchema = IdentityHashMap<SchemaProperty, List<DynamicAnchorResource>>()
    val resourceCache = IdentityHashMap<SchemaProperty, DynamicAnchorResource>()

    fun resourceFor(node: SchemaProperty): DynamicAnchorResource {
        return resourceCache[node] ?: run {
            val anchors = LinkedHashMap<String, SchemaProperty>()
            collectResourceAnchors(node, anchors)
            val resource = DynamicAnchorResource(anchors)
            resourceCache[node] = resource
            resource
        }
    }

    fun traverse(node: SchemaProperty, stack: List<DynamicAnchorResource>) {
        val isNewResource = !node.schemaId.isNullOrBlank()
        val resource = resourceFor(node)
        val shouldPush = stack.isEmpty() || isNewResource
        val nextStack = if (shouldPush && (stack.isEmpty() || stack.last() !== resource)) {
            stack + resource
        } else {
            stack
        }
        scopeBySchema[node] = nextStack
        node.forEachSubschema { child ->
            traverse(child, nextStack)
        }
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
 */
fun extractDynamicAnchorName(ref: String): String? {
    val fragment = ref.substringAfter("#", "")
    if (fragment.isBlank()) return null
    if (fragment.startsWith("/")) return null
    return percentDecode(fragment)
}

private fun percentDecode(value: String): String {
    if (!value.contains("%")) return value
    val bytes = ByteArray(value.length)
    var byteCount = 0
    var index = 0
    while (index < value.length) {
        val ch = value[index]
        if (ch == '%' && index + 2 < value.length) {
            val hi = hexToInt(value[index + 1])
            val lo = hexToInt(value[index + 2])
            if (hi >= 0 && lo >= 0) {
                bytes[byteCount++] = ((hi shl 4) + lo).toByte()
                index += 3
                continue
            }
        }
        bytes[byteCount++] = ch.code.toByte()
        index += 1
    }
    return bytes.copyOf(byteCount).toString(Charsets.UTF_8)
}

private fun hexToInt(ch: Char): Int {
    return when (ch) {
        in '0'..'9' -> ch.code - '0'.code
        in 'a'..'f' -> ch.code - 'a'.code + 10
        in 'A'..'F' -> ch.code - 'A'.code + 10
        else -> -1
    }
}
