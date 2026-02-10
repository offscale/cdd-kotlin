package domain

/**
 * Utility to flatten OpenAPI Paths/PathItems into a list of EndpointDefinitions.
 *
 * This is useful for code generation that expects flat endpoint lists while
 * still modeling the OpenAPI `paths` object correctly with PathItem objects.
 */
object OpenApiPathFlattener {

    /**
     * Flattens an OpenAPI Paths map into a list of EndpointDefinitions.
     */
    fun flattenPaths(paths: Map<String, PathItem>): List<EndpointDefinition> {
        if (paths.isEmpty()) return emptyList()
        return paths.flatMap { (path, item) ->
            flattenPathItem(path, item)
        }
    }

    /**
     * Flattens a single PathItem into EndpointDefinitions for each defined operation.
     */
    fun flattenPathItem(path: String, item: PathItem): List<EndpointDefinition> {
        val endpoints = mutableListOf<EndpointDefinition>()

        addIfPresent(endpoints, path, HttpMethod.GET, item, item.get)
        addIfPresent(endpoints, path, HttpMethod.PUT, item, item.put)
        addIfPresent(endpoints, path, HttpMethod.POST, item, item.post)
        addIfPresent(endpoints, path, HttpMethod.DELETE, item, item.delete)
        addIfPresent(endpoints, path, HttpMethod.OPTIONS, item, item.options)
        addIfPresent(endpoints, path, HttpMethod.HEAD, item, item.head)
        addIfPresent(endpoints, path, HttpMethod.PATCH, item, item.patch)
        addIfPresent(endpoints, path, HttpMethod.TRACE, item, item.trace)
        addIfPresent(endpoints, path, HttpMethod.QUERY, item, item.query)
        addAdditionalOperations(endpoints, path, item)

        return endpoints
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
