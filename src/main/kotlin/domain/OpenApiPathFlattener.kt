package domain

import java.net.URI

/** 
 * Utility to flatten OpenAPI Paths/PathItems into a list of EndpointDefinitions. 
 * 
 * This is useful for code generation that expects flat endpoint lists while
 * still modeling the OpenAPI `paths` object correctly with PathItem objects. 
 */ 
/** 
 * Resolved Path Item metadata returned by [PathItemRefResolver]. 
 */ 
data class PathItemResolution( 
    val item: PathItem, 
    val components: Components? = null, 
    val selfBase: String? = null
) 

/** 
 * Resolves external Path Item $ref targets (e.g. external `#/components/pathItems/{name}`). 
 */ 
fun interface PathItemRefResolver { 
    /** 
     * Resolves the target PathItem by base URI and component key. 
     */ 
    fun resolve(baseUri: String?, key: String): PathItemResolution? 
} 

object OpenApiPathFlattener { 

    /** 
     * Flattens an OpenAPI Paths map into a list of EndpointDefinitions. 
     * 
     * If [components] contains `pathItems`, `$ref` Path Items are resolved
     * when they point to `#/components/pathItems/{name}`. 
     * 
     * When [self] is provided, component pathItem refs are resolved only if the
     * ref base matches the `$self` base (or the ref is fragment-only). 
     * 
     * External component refs can be resolved via [refResolver] when provided. 
     */ 
    @JvmOverloads
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
     * Flattens a Webhooks map into EndpointDefinitions. 
     * 
     * Webhook keys are free-form identifiers, so they are used as the path value for the generated endpoints. 
     * The same Path Item resolution rules apply (including component pathItems refs and `$self`-aware matching), 
     * with optional external resolution via [refResolver]. 
     */ 
    @JvmOverloads
    fun flattenWebhooks( 
        webhooks: Map<String, PathItem>, 
        components: Components? = null, 
        self: String? = null, 
        refResolver: PathItemRefResolver? = null
    ): List<EndpointDefinition> { 
        return flattenPaths(webhooks, components, self, refResolver) 
    } 

    /** 
     * Flattens both Paths and Webhooks into a single EndpointDefinitions list. 
     * External component refs can be resolved via [refResolver] when provided. 
     */ 
    @JvmOverloads
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
     * Flattens a single PathItem into EndpointDefinitions for each defined operation. 
     * 
     * If [components] contains `pathItems`, `$ref` Path Items are resolved
     * when they point to `#/components/pathItems/{name}`. 
     * 
     * When [selfBase] is provided, component pathItem refs are resolved only if the
     * ref base matches the `$self` base (or the ref is fragment-only). 
     * 
     * External component refs can be resolved via [refResolver] when provided. 
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
        if (item.ref == null) return item
        val baseComponents = components ?: Components() 
        val visited = mutableSetOf<String>() 
        return resolvePathItemRef(item, baseComponents, visited, selfBase, refResolver) 
    } 

    private fun resolvePathItemRef( 
        item: PathItem, 
        components: Components, 
        visited: MutableSet<String>, 
        selfBase: String?, 
        refResolver: PathItemRefResolver? 
    ): PathItem? { 
        val ref = item.ref ?: return item
        if (!visited.add(ref)) return null
        val resolution = resolveComponentPathItem(ref, components, selfBase, refResolver) ?: return null
        val target = resolution.item
        val targetComponents = resolution.components ?: components
        val targetSelfBase = resolution.selfBase ?: selfBase
        val resolvedTarget = if (target.ref != null) { 
            resolvePathItemRef(target, targetComponents, visited, targetSelfBase, refResolver) 
        } else { 
            target
        } 
        val base = resolvedTarget ?: target
        return base.copy( 
            summary = item.summary ?: base.summary, 
            description = item.description ?: base.description, 
            parameters = if (item.parameters.isNotEmpty()) item.parameters else base.parameters, 
            servers = if (item.servers.isNotEmpty()) item.servers else base.servers, 
            extensions = mergeExtensions(base.extensions, item.extensions) 
        ) 
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
            if (base != null && refResolver != null) { 
                return refResolver.resolve(base, key) 
            } 
            return null
        } 
        return refResolver?.resolve(base, key) 
    } 

    private data class ComponentRef(val base: String?, val key: String) 

    private fun extractComponentRef(ref: String, component: String, selfBase: String?): ComponentRef? { 
        val marker = "#/components/$component/" 
        val index = ref.indexOf(marker) 
        if (index < 0) return null
        val refBase = ref.substring(0, index) 
        val raw = ref.substring(index + marker.length) 
        if (raw.isBlank() || raw.contains("/")) return null
        val key = decodeJsonPointer(raw) 
        val base = resolveReferenceBase(refBase, selfBase) 
        return ComponentRef(base = base, key = key) 
    } 

    private fun normalizeSelfBase(self: String?): String? { 
        val trimmed = self?.trim().orEmpty() 
        if (trimmed.isBlank()) return null
        return trimmed.substringBefore("#") 
    } 

    private fun resolveReferenceBase(refBase: String, contextBase: String?): String? { 
        val normalized = normalizeSelfBase(refBase) 
        if (normalized.isNullOrBlank()) { 
            return normalizeSelfBase(contextBase) ?: contextBase
        } 
        val resolved = contextBase?.let { resolveAgainstBase(it, normalized) } ?: normalized
        return normalizeSelfBase(resolved) ?: resolved
    } 

    private fun resolveAgainstBase(base: String, ref: String): String { 
        return try { 
            URI(base).resolve(ref).toString() 
        } catch (_: Exception) { 
            ref
        } 
    } 

    private fun isSelfBaseMatch(refBase: String, selfBase: String?): Boolean { 
        val normalizedSelf = selfBase?.trimEnd('#').orEmpty() 
        if (normalizedSelf.isBlank()) return true
        if (refBase.isBlank()) return true
        return refBase.trimEnd('#') == normalizedSelf
    } 

    private fun decodeJsonPointer(value: String): String { 
        val token = value.split("/").lastOrNull()?.replace("~1", "/")?.replace("~0", "~") ?: value
        return percentDecode(token) 
    } 

    private fun percentDecode(value: String): String { 
        if (!value.contains("%")) return value
        val bytes = ByteArray(value.length) 
        var byteCount = 0
        var i = 0
        while (i < value.length) { 
            val ch = value[i] 
            if (ch == '%' && i + 2 < value.length) { 
                val hi = hexToInt(value[i + 1]) 
                val lo = hexToInt(value[i + 2]) 
                if (hi >= 0 && lo >= 0) { 
                    bytes[byteCount++] = ((hi shl 4) + lo).toByte() 
                    i += 3
                    continue
                } 
            } 
            bytes[byteCount++] = ch.code.toByte() 
            i += 1
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
                    servers = servers
                ) 
            ) 
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
                servers = servers
            ) 
        ) 
    } 

    private fun mergeParameters( 
        pathParams: List<EndpointParameter>, 
        opParams: List<EndpointParameter>
    ): List<EndpointParameter> { 
        if (pathParams.isEmpty()) return opParams
        if (opParams.isEmpty()) return pathParams

        val merged = LinkedHashMap<String, EndpointParameter>() 
        fun key(param: EndpointParameter): String = "${param.location}:${param.name}" 

        pathParams.forEach { merged[key(it)] = it } 
        opParams.forEach { merged[key(it)] = it } 

        return merged.values.toList() 
    } 
}