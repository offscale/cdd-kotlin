package domain

/**
 * Utility to build OpenAPI Paths/PathItems from a flat list of endpoints.
 *
 * This is the inverse of [OpenApiPathFlattener] and is intended for
 * Kotlin -> OpenAPI assembly workflows.
 */
object OpenApiPathBuilder {

    /**
     * Groups endpoints by path and builds a PathItem per path.
     *
     * - Standard HTTP methods are mapped to fixed PathItem fields.
     * - Custom methods are placed in [PathItem.additionalOperations].
     * - Path-level parameters are not inferred by default; when [liftCommonPathMetadata] is true,
     *   parameters shared by all operations are lifted to the Path Item.
     */
    fun buildPaths(
        endpoints: List<EndpointDefinition>,
        liftCommonPathMetadata: Boolean = false
    ): Map<String, PathItem> {
        if (endpoints.isEmpty()) return emptyMap()

        val byPath = endpoints.groupBy { it.path }
        return byPath.mapValues { (_, ops) ->
            buildPathItem(ops, liftCommonPathMetadata)
        }
    }

    private fun buildPathItem(
        ops: List<EndpointDefinition>,
        liftCommonPathMetadata: Boolean
    ): PathItem {
        val commonParams = if (liftCommonPathMetadata) findCommonParameters(ops) else emptyList()
        val commonSummary = if (liftCommonPathMetadata) findCommonString(ops.map { it.summary }) else null
        val commonDescription = if (liftCommonPathMetadata) findCommonString(ops.map { it.description }) else null
        val commonServers = if (liftCommonPathMetadata) findCommonServers(ops) else null

        var get: EndpointDefinition? = null
        var put: EndpointDefinition? = null
        var post: EndpointDefinition? = null
        var delete: EndpointDefinition? = null
        var options: EndpointDefinition? = null
        var head: EndpointDefinition? = null
        var patch: EndpointDefinition? = null
        var trace: EndpointDefinition? = null
        var query: EndpointDefinition? = null
        val additional = linkedMapOf<String, EndpointDefinition>()

        ops.forEach { ep ->
            val normalized = if (!liftCommonPathMetadata) {
                ep
            } else {
                val filteredParams = if (commonParams.isEmpty()) ep.parameters else ep.parameters.filterNot { commonParams.contains(it) }
                val summary = if (commonSummary != null) null else ep.summary
                val description = if (commonDescription != null) null else ep.description
                val servers = if (commonServers != null) emptyList() else ep.servers
                ep.copy(parameters = filteredParams, summary = summary, description = description, servers = servers)
            }

            when (normalized.method) {
                HttpMethod.GET -> get = normalized
                HttpMethod.PUT -> put = normalized
                HttpMethod.POST -> post = normalized
                HttpMethod.DELETE -> delete = normalized
                HttpMethod.OPTIONS -> options = normalized
                HttpMethod.HEAD -> head = normalized
                HttpMethod.PATCH -> patch = normalized
                HttpMethod.TRACE -> trace = normalized
                HttpMethod.QUERY -> query = normalized
                HttpMethod.CUSTOM -> {
                    val methodToken = normalized.customMethod ?: normalized.methodName
                    additional[methodToken] = normalized
                }
            }
        }

        return PathItem(
            summary = commonSummary,
            description = commonDescription,
            parameters = commonParams,
            servers = commonServers ?: emptyList(),
            get = get,
            put = put,
            post = post,
            delete = delete,
            options = options,
            head = head,
            patch = patch,
            trace = trace,
            query = query,
            additionalOperations = additional
        )
    }

    private fun findCommonParameters(ops: List<EndpointDefinition>): List<EndpointParameter> {
        if (ops.isEmpty()) return emptyList()
        val first = ops.first().parameters
        if (first.isEmpty()) return emptyList()
        return first.filter { param ->
            ops.all { op -> op.parameters.any { it == param } }
        }
    }

    private fun findCommonString(values: List<String?>): String? {
        if (values.isEmpty()) return null
        val distinct = values.distinct()
        val candidate = distinct.singleOrNull() ?: return null
        return candidate ?: return null
    }

    private fun findCommonServers(ops: List<EndpointDefinition>): List<Server>? {
        if (ops.isEmpty()) return null
        val distinct = ops.map { it.servers }.distinct()
        val candidate = distinct.singleOrNull() ?: return null
        return candidate.takeIf { it.isNotEmpty() }
    }
}
