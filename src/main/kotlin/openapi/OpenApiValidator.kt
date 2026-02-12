package openapi

import domain.Components
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.Header
import domain.OpenApiDefinition
import domain.ParameterLocation
import domain.ParameterStyle
import domain.PathItem
import domain.RequestBody
import domain.Server

/**
 * Validates OpenAPI 3.2 documents for common specification constraints.
 *
 * This validator is intentionally focused on structural rules that are easy to
 * enforce without full semantic resolution or network access.
 */
class OpenApiValidator {

    /**
     * Validate the given OpenAPI definition and return any detected issues.
     */
    fun validate(definition: OpenApiDefinition): List<OpenApiIssue> {
        if (!definition.openapi.startsWith("3.")) return emptyList()

        val issues = mutableListOf<OpenApiIssue>()

        if (definition.paths.isEmpty() && definition.webhooks.isEmpty() && definition.components == null) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$.openapi",
                message = "OpenAPI requires at least one of: paths, webhooks, or components."
            )
        }

        validateServers(definition.servers, "$.servers", issues)
        validateInfo(definition.info, "$.info", issues)
        validatePaths(definition.paths, "$.paths", issues, definition.components)
        validatePaths(definition.webhooks, "$.webhooks", issues, definition.components)
        validateComponents(definition.components, "$.components", issues)
        validateSecurityRequirements(definition.security, definition.components, "$.security", issues)
        validateOperationIds(definition, issues)
        validateTags(definition, issues)

        return issues
    }

    /**
     * Validate Info object constraints that are not enforced by the data model.
     */
    private fun validateInfo(info: domain.Info, basePath: String, issues: MutableList<OpenApiIssue>) {
        info.license?.let { validateLicense(it, "$basePath.license", issues) }
    }

    /**
     * Enforce mutually exclusive License fields.
     */
    private fun validateLicense(license: domain.License, path: String, issues: MutableList<OpenApiIssue>) {
        if (license.identifier != null && license.url != null) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "License must not define both identifier and url."
            )
        }
    }

    private fun validateServers(servers: List<Server>, basePath: String, issues: MutableList<OpenApiIssue>) {
        servers.forEachIndexed { index, server ->
            if (server.url.contains("?") || server.url.contains("#")) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath[$index].url",
                    message = "Server url must not include query or fragment."
                )
            }
            val variablesInUrl = SERVER_VARIABLE_REGEX.findAll(server.url).map { it.groupValues[1] }.toList()
            val duplicates = variablesInUrl.groupBy { it }.filter { it.value.size > 1 }
            duplicates.forEach { (name, values) ->
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath[$index].url",
                    message = "Server variable '$name' must not appear more than once in the url (found ${values.size})."
                )
            }
            if (variablesInUrl.isNotEmpty() && server.variables == null) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath[$index].variables",
                    message = "Server url uses variables but no variables map is defined."
                )
            }
            val definedVariables = server.variables?.keys.orEmpty()
            variablesInUrl.forEach { variable ->
                if (!definedVariables.contains(variable)) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = "$basePath[$index].variables",
                        message = "Server url variable '$variable' is not defined in variables."
                    )
                }
            }
            if (definedVariables.isNotEmpty() && variablesInUrl.isEmpty()) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.WARNING,
                    path = "$basePath[$index].variables",
                    message = "Server variables are defined but no variables are used in the url."
                )
            }
            server.variables?.forEach { (name, variable) ->
                val enumValues = variable.enum
                if (enumValues != null && enumValues.isEmpty()) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = "$basePath[$index].variables.$name.enum",
                        message = "Server variable enum must not be empty."
                    )
                }
                if (enumValues != null && enumValues.isNotEmpty() && !enumValues.contains(variable.default)) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = "$basePath[$index].variables.$name.default",
                        message = "Server variable default must be one of the enum values."
                    )
                }
            }
        }
    }

    private fun validatePaths(
        paths: Map<String, PathItem>,
        basePath: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        paths.forEach { (pathKey, item) ->
            if (!pathKey.startsWith("/")) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath.$pathKey",
                    message = "Path keys must start with '/'."
                )
            }
            validatePathItem(pathKey, item, "$basePath.$pathKey", issues, components)
        }
        validatePathTemplateCollisions(paths.keys, basePath, issues)
    }

    private fun validatePathItem(
        pathKey: String,
        item: PathItem,
        basePath: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        validatePathTemplating(pathKey, item, basePath, issues)
        item.get?.let { validateOperation(it, "$basePath.get", issues, components) }
        item.put?.let { validateOperation(it, "$basePath.put", issues, components) }
        item.post?.let { validateOperation(it, "$basePath.post", issues, components) }
        item.delete?.let { validateOperation(it, "$basePath.delete", issues, components) }
        item.options?.let { validateOperation(it, "$basePath.options", issues, components) }
        item.head?.let { validateOperation(it, "$basePath.head", issues, components) }
        item.patch?.let { validateOperation(it, "$basePath.patch", issues, components) }
        item.trace?.let { validateOperation(it, "$basePath.trace", issues, components) }
        item.query?.let { validateOperation(it, "$basePath.query", issues, components) }

        val reserved = setOf("get", "put", "post", "delete", "options", "head", "patch", "trace", "query")
        item.additionalOperations.keys.forEach { method ->
            if (reserved.contains(method.lowercase())) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath.additionalOperations.$method",
                    message = "additionalOperations must not include standard HTTP methods."
                )
            }
        }
    }

    private fun validateOperation(
        operation: EndpointDefinition,
        basePath: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        if (operation.responses.isEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.responses",
                message = "Operation must define at least one response."
            )
        }
        operation.responses.keys.forEach { code ->
            if (!isValidResponseCode(code)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath.responses.$code",
                    message = "Invalid response code '$code'. Use HTTP status codes, ranges (1XX-5XX), or 'default'."
                )
            }
        }

        val queryParams = operation.parameters.filter { it.location == ParameterLocation.QUERY }
        val queryStringParams = operation.parameters.filter { it.location == ParameterLocation.QUERYSTRING }
        if (queryParams.isNotEmpty() && queryStringParams.isNotEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.parameters",
                message = "query and querystring parameters cannot be used together."
            )
        }
        if (queryStringParams.size > 1) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.parameters",
                message = "Only one querystring parameter is allowed per operation."
            )
        }

        val duplicates = operation.parameters.groupBy { it.name to it.location }
            .filter { it.value.size > 1 }
        duplicates.forEach { (key, values) ->
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.parameters",
                message = "Duplicate parameter '${key.first}' in ${key.second} (count ${values.size})."
            )
        }

        operation.parameters.forEachIndexed { index, param ->
            validateParameter(param, "$basePath.parameters[$index]", issues)
        }

        operation.responses.entries.forEachIndexed { index, entry ->
            val responsePath = "$basePath.responses[$index]"
            entry.value.headers.forEach { (headerName, header) ->
                if (headerName.equals("content-type", ignoreCase = true)) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.WARNING,
                        path = "$responsePath.headers.$headerName",
                        message = "Response headers must not include 'Content-Type' (it is ignored)."
                    )
                }
                validateHeader(header, "$responsePath.headers.$headerName", issues)
            }
            validateResponse(entry.value, responsePath, issues)
        }

        operation.requestBody?.let { validateRequestBody(it, "$basePath.requestBody", issues) }
        validateSecurityRequirements(operation.security, components, "$basePath.security", issues)
    }

    private fun validateParameter(param: EndpointParameter, path: String, issues: MutableList<OpenApiIssue>) {
        if (param.location == ParameterLocation.PATH && !param.isRequired) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Path parameters must be required."
            )
        }
        if (param.schema == null && param.content.isEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Parameter must define either schema or content."
            )
        }
        if (param.schema != null && param.content.isNotEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Parameter must not define both schema and content."
            )
        }
        if (param.content.size > 1) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Parameter content must contain exactly one media type."
            )
        }
        if (param.example != null && param.examples.isNotEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Parameter must not define both example and examples."
            )
        }
        param.style?.let { style ->
            val allowed = when (param.location) {
                ParameterLocation.PATH -> setOf(ParameterStyle.MATRIX, ParameterStyle.LABEL, ParameterStyle.SIMPLE)
                ParameterLocation.QUERY -> setOf(
                    ParameterStyle.FORM,
                    ParameterStyle.SPACE_DELIMITED,
                    ParameterStyle.PIPE_DELIMITED,
                    ParameterStyle.DEEP_OBJECT
                )
                ParameterLocation.HEADER -> setOf(ParameterStyle.SIMPLE)
                ParameterLocation.COOKIE -> setOf(ParameterStyle.FORM, ParameterStyle.COOKIE)
                ParameterLocation.QUERYSTRING -> emptySet()
            }
            if (allowed.isNotEmpty() && !allowed.contains(style)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = path,
                    message = "Parameter style '$style' is not allowed for ${param.location}."
                )
            }
        }
        if (param.style == ParameterStyle.SPACE_DELIMITED && param.explode == true) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "spaceDelimited style does not support explode=true."
            )
        }
        if (param.style == ParameterStyle.PIPE_DELIMITED && param.explode == true) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "pipeDelimited style does not support explode=true."
            )
        }
        if (param.style == ParameterStyle.DEEP_OBJECT) {
            val schemaTypes = param.schema?.types.orEmpty()
            if (schemaTypes.isNotEmpty() && !schemaTypes.contains("object")) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = path,
                    message = "deepObject style only applies to object parameters."
                )
            }
        }
        if (param.location == ParameterLocation.QUERYSTRING) {
            if (param.schema != null) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = path,
                    message = "querystring parameters must use content instead of schema."
                )
            }
            if (param.content.isEmpty()) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = path,
                    message = "querystring parameters must define content."
                )
            }
            if (param.style != null || param.explode != null || param.allowReserved != null) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = path,
                    message = "querystring parameters must not use style/explode/allowReserved."
                )
            }
        }
        if (param.allowEmptyValue != null && param.location != ParameterLocation.QUERY) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "allowEmptyValue is only valid for query parameters."
            )
        }
        if (param.location == ParameterLocation.HEADER) {
            val headerName = param.name.lowercase()
            if (headerName == "accept" || headerName == "content-type" || headerName == "authorization") {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.WARNING,
                    path = path,
                    message = "Header parameter '$headerName' is ignored by the specification."
                )
            }
        }
        param.example?.let { validateExampleObject(it, "$path.example", issues) }
        param.examples.forEach { (name, example) ->
            validateExampleObject(example, "$path.examples.$name", issues)
        }
        param.content.forEach { (mediaType, media) ->
            validateMediaTypeObject(media, "$path.content.$mediaType", issues, mediaType)
        }
    }

    private fun validateHeader(header: Header, path: String, issues: MutableList<OpenApiIssue>) {
        if (header.schema == null && header.content.isEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Header must define either schema or content."
            )
        }
        if (header.schema != null && header.content.isNotEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Header must not define both schema and content."
            )
        }
        if (header.content.size > 1) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Header content must contain exactly one media type."
            )
        }
        if (header.example != null && header.examples.isNotEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Header must not define both example and examples."
            )
        }
        if (header.style != null && header.style != ParameterStyle.SIMPLE) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Header style must be 'simple'."
            )
        }
        header.example?.let { validateExampleObject(it, "$path.example", issues) }
        header.examples.forEach { (name, example) ->
            validateExampleObject(example, "$path.examples.$name", issues)
        }
        header.content.forEach { (mediaType, media) ->
            validateMediaTypeObject(media, "$path.content.$mediaType", issues, mediaType)
        }
    }

    private fun validateRequestBody(body: RequestBody, path: String, issues: MutableList<OpenApiIssue>) {
        if (body.reference == null && body.content.isEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "RequestBody content should contain at least one media type."
            )
        }
        body.content.forEach { (mediaType, media) ->
            validateMediaTypeObject(media, "$path.content.$mediaType", issues, mediaType)
        }
    }

    private fun validateComponents(components: Components?, basePath: String, issues: MutableList<OpenApiIssue>) {
        if (components == null) return
        val keyRegex = Regex("^[a-zA-Z0-9\\._-]+$")

        fun validateKeys(keys: Set<String>, componentPath: String) {
            keys.filterNot { keyRegex.matches(it) }.forEach { key ->
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath.$componentPath.$key",
                    message = "Component keys must match ^[a-zA-Z0-9._-]+$."
                )
            }
        }

        validateKeys(components.schemas.keys, "schemas")
        validateKeys(components.responses.keys, "responses")
        validateKeys(components.parameters.keys, "parameters")
        validateKeys(components.requestBodies.keys, "requestBodies")
        validateKeys(components.headers.keys, "headers")
        validateKeys(components.securitySchemes.keys, "securitySchemes")
        validateKeys(components.examples.keys, "examples")
        validateKeys(components.links.keys, "links")
        validateKeys(components.callbacks.keys, "callbacks")
        validateKeys(components.pathItems.keys, "pathItems")
        validateKeys(components.mediaTypes.keys, "mediaTypes")

        components.securitySchemes.forEach { (name, scheme) ->
            validateSecurityScheme(scheme, "$basePath.securitySchemes.$name", issues)
        }
        components.examples.forEach { (name, example) ->
            validateExampleObject(example, "$basePath.examples.$name", issues)
        }
        components.parameters.forEach { (name, param) ->
            validateParameter(param, "$basePath.parameters.$name", issues)
        }
        components.headers.forEach { (name, header) ->
            validateHeader(header, "$basePath.headers.$name", issues)
        }
        components.requestBodies.forEach { (name, body) ->
            validateRequestBody(body, "$basePath.requestBodies.$name", issues)
        }
        components.responses.forEach { (name, response) ->
            validateResponse(response, "$basePath.responses.$name", issues)
        }
        components.mediaTypes.forEach { (name, mediaType) ->
            validateMediaTypeObject(mediaType, "$basePath.mediaTypes.$name", issues)
        }
    }

    /**
     * Validate Response Objects, including their content and examples.
     */
    private fun validateResponse(response: EndpointResponse, path: String, issues: MutableList<OpenApiIssue>) {
        if (response.description.isNullOrBlank()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.description",
                message = "Response description is required."
            )
        }
        response.content.forEach { (mediaType, media) ->
            validateMediaTypeObject(media, "$path.content.$mediaType", issues, mediaType)
        }
        response.links?.forEach { (name, link) ->
            validateLink(link, "$path.links.$name", issues)
        }
    }

    private fun isValidResponseCode(code: String): Boolean {
        if (code == "default") return true
        if (code.matches(Regex("^[1-5][0-9]{2}$"))) return true
        if (code.matches(Regex("^[1-5]XX$"))) return true
        return false
    }

    /**
     * Validate Media Type Objects, including encoding constraints and examples.
     */
    private fun validateMediaTypeObject(
        media: domain.MediaTypeObject,
        path: String,
        issues: MutableList<OpenApiIssue>,
        mediaTypeKey: String? = null
    ) {
        if (media.example != null && media.examples.isNotEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Media type must not define both example and examples."
            )
        }
        val hasPositionalEncoding = media.prefixEncoding.isNotEmpty() || media.itemEncoding != null
        if (media.encoding.isNotEmpty() && hasPositionalEncoding) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Media type must not define encoding with prefixEncoding/itemEncoding."
            )
        }
        if (mediaTypeKey != null && media.itemSchema != null && !isSequentialMediaType(mediaTypeKey)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.itemSchema",
                message = "itemSchema is only valid for sequential or multipart media types."
            )
        }
        if (hasPositionalEncoding && !hasArraySchemaOrItemSchema(media)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "prefixEncoding/itemEncoding requires an array schema or itemSchema."
            )
        }
        validateEncodingApplicability(media, mediaTypeKey, path, issues)
        media.example?.let { validateExampleObject(it, "$path.example", issues) }
        media.examples.forEach { (name, example) ->
            validateExampleObject(example, "$path.examples.$name", issues)
        }
        media.encoding.forEach { (name, encoding) ->
            validateEncodingObject(encoding, "$path.encoding.$name", issues)
        }
        media.prefixEncoding.forEachIndexed { index, encoding ->
            validateEncodingObject(encoding, "$path.prefixEncoding[$index]", issues)
        }
        media.itemEncoding?.let { validateEncodingObject(it, "$path.itemEncoding", issues) }
    }

    /**
     * Validate that encoding fields are used only with applicable media types.
     */
    private fun validateEncodingApplicability(
        media: domain.MediaTypeObject,
        mediaTypeKey: String?,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        if (mediaTypeKey.isNullOrBlank()) return
        val baseType = mediaTypeKey.substringBefore(";").trim().lowercase()
        val isMultipart = baseType.startsWith("multipart/")
        val isFormUrlEncoded = baseType == "application/x-www-form-urlencoded"
        val hasEncoding = media.encoding.isNotEmpty() || media.prefixEncoding.isNotEmpty() || media.itemEncoding != null
        if (hasEncoding && !(isMultipart || isFormUrlEncoded)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "Encoding is only applicable to multipart or application/x-www-form-urlencoded media types."
            )
        }
        if (!isMultipart && (media.prefixEncoding.isNotEmpty() || media.itemEncoding != null)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "prefixEncoding/itemEncoding apply only to multipart media types."
            )
        }
    }

    private fun isSequentialMediaType(mediaTypeKey: String): Boolean {
        val baseType = mediaTypeKey.substringBefore(";").trim().lowercase()
        if (baseType.startsWith("multipart/")) return true
        if (baseType == "text/event-stream") return true
        if (baseType == "application/jsonl") return true
        if (baseType == "application/x-ndjson") return true
        if (baseType == "application/ndjson") return true
        if (baseType.endsWith("json-seq")) return true
        return false
    }

    private fun hasArraySchemaOrItemSchema(media: domain.MediaTypeObject): Boolean {
        if (media.itemSchema != null) return true
        val schema = media.schema ?: return false
        if (schema.types.contains("array")) return true
        if (schema.items != null || schema.prefixItems.isNotEmpty()) return true
        return false
    }

    /**
     * Validate Encoding Objects for mutual exclusivity of encoding vs positional encoding.
     */
    private fun validateEncodingObject(
        encoding: domain.EncodingObject,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        val hasPositional = encoding.prefixEncoding.isNotEmpty() || encoding.itemEncoding != null
        if (encoding.encoding.isNotEmpty() && hasPositional) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Encoding object must not define encoding with prefixEncoding/itemEncoding."
            )
        }
        encoding.encoding.forEach { (name, nested) ->
            validateEncodingObject(nested, "$path.encoding.$name", issues)
        }
        encoding.prefixEncoding.forEachIndexed { index, nested ->
            validateEncodingObject(nested, "$path.prefixEncoding[$index]", issues)
        }
        encoding.itemEncoding?.let { validateEncodingObject(it, "$path.itemEncoding", issues) }
    }

    /**
     * Validate Example Object mutual exclusivity rules.
     */
    private fun validateExampleObject(example: domain.ExampleObject, path: String, issues: MutableList<OpenApiIssue>) {
        val hasValue = example.value != null
        val hasDataValue = example.dataValue != null
        val hasSerialized = example.serializedValue != null
        val hasExternal = example.externalValue != null

        if (example.ref != null && (hasValue || hasDataValue || hasSerialized || hasExternal)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Example with \$ref must not define value/dataValue/serializedValue/externalValue."
            )
        }
        if (hasDataValue && hasValue) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Example must not define both dataValue and value."
            )
        }
        if (hasSerialized && (hasValue || hasExternal)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Example must not define serializedValue with value or externalValue."
            )
        }
        if (hasExternal && hasValue) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Example must not define externalValue with value."
            )
        }
    }

    /**
     * Validate Security Scheme required fields based on type.
     */
    private fun validateSecurityScheme(
        scheme: domain.SecurityScheme,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        if (scheme.reference != null) return
        when (scheme.type) {
            "apiKey" -> {
                if (scheme.name.isNullOrBlank() || scheme.`in`.isNullOrBlank()) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = path,
                        message = "apiKey security scheme requires name and in."
                    )
                }
            }
            "http" -> {
                if (scheme.scheme.isNullOrBlank()) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = path,
                        message = "http security scheme requires scheme."
                    )
                }
            }
            "oauth2" -> {
                if (scheme.flows == null) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = path,
                        message = "oauth2 security scheme requires flows."
                    )
                } else {
                    validateOAuthFlows(scheme.flows, "$path.flows", issues)
                }
            }
            "openIdConnect" -> {
                if (scheme.openIdConnectUrl.isNullOrBlank()) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = path,
                        message = "openIdConnect security scheme requires openIdConnectUrl."
                    )
                }
            }
        }
    }

    /**
     * Validate OAuth Flow required fields per flow type.
     */
    private fun validateOAuthFlows(
        flows: domain.OAuthFlows,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        flows.implicit?.let { flow ->
            if (flow.authorizationUrl.isNullOrBlank()) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.implicit.authorizationUrl",
                    message = "implicit OAuth flow requires authorizationUrl."
                )
            }
        }
        flows.password?.let { flow ->
            if (flow.tokenUrl.isNullOrBlank()) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.password.tokenUrl",
                    message = "password OAuth flow requires tokenUrl."
                )
            }
        }
        flows.clientCredentials?.let { flow ->
            if (flow.tokenUrl.isNullOrBlank()) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.clientCredentials.tokenUrl",
                    message = "clientCredentials OAuth flow requires tokenUrl."
                )
            }
        }
        flows.authorizationCode?.let { flow ->
            if (flow.authorizationUrl.isNullOrBlank() || flow.tokenUrl.isNullOrBlank()) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.authorizationCode",
                    message = "authorizationCode OAuth flow requires authorizationUrl and tokenUrl."
                )
            }
        }
        flows.deviceAuthorization?.let { flow ->
            if (flow.deviceAuthorizationUrl.isNullOrBlank() || flow.tokenUrl.isNullOrBlank()) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.deviceAuthorization",
                    message = "deviceAuthorization OAuth flow requires deviceAuthorizationUrl and tokenUrl."
                )
            }
        }
    }

    private fun validateOperationIds(definition: OpenApiDefinition, issues: MutableList<OpenApiIssue>) {
        val operations = collectOperations(definition.paths) + collectOperations(definition.webhooks)
        val duplicates = operations.groupBy { it.operationId }.filter { it.value.size > 1 }
        duplicates.forEach { (operationId, ops) ->
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$.paths",
                message = "operationId '$operationId' must be unique (found ${ops.size})."
            )
        }
    }

    private fun validateTags(definition: OpenApiDefinition, issues: MutableList<OpenApiIssue>) {
        val duplicates = definition.tags.groupBy { it.name }.filter { it.value.size > 1 }
        duplicates.forEach { (name, tags) ->
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$.tags",
                message = "Tag name '$name' must be unique (found ${tags.size})."
            )
        }
        val tagsByName = definition.tags.associateBy { it.name }
        definition.tags.forEach { tag ->
            val parent = tag.parent ?: return@forEach
            if (!tagsByName.containsKey(parent)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$.tags",
                    message = "Tag parent '$parent' for '${tag.name}' must exist in tags."
                )
            }
        }
        detectTagCycles(tagsByName, issues)
    }

    /**
     * Detect and flag circular tag parent relationships.
     */
    private fun detectTagCycles(tagsByName: Map<String, domain.Tag>, issues: MutableList<OpenApiIssue>) {
        tagsByName.keys.forEach { start ->
            val seen = mutableSetOf<String>()
            var current = start
            while (true) {
                val parent = tagsByName[current]?.parent ?: break
                if (!seen.add(parent)) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = "$.tags",
                        message = "Tag parent cycle detected involving '$start'."
                    )
                    break
                }
                current = parent
            }
        }
    }

    /**
     * Validate Link Objects for required target identifiers and mutual exclusivity.
     */
    private fun validateLink(
        link: domain.Link,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        val isRef = link.ref != null || link.reference != null
        if (isRef) {
            val hasOverrides = link.operationId != null ||
                link.operationRef != null ||
                link.parameters.isNotEmpty() ||
                link.requestBody != null ||
                link.server != null
            if (hasOverrides) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.WARNING,
                    path = path,
                    message = "Link with \$ref should not define other fields (they are ignored)."
                )
            }
            return
        }
        val hasOperationId = !link.operationId.isNullOrBlank()
        val hasOperationRef = !link.operationRef.isNullOrBlank()
        if (hasOperationId && hasOperationRef) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Link must not define both operationId and operationRef."
            )
        }
        if (!hasOperationId && !hasOperationRef) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Link must define either operationId or operationRef."
            )
        }
    }

    /**
     * Validate Security Requirement Object scheme references.
     */
    private fun validateSecurityRequirements(
        requirements: List<domain.SecurityRequirement>,
        components: Components?,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        if (requirements.isEmpty()) return
        val schemeNames = components?.securitySchemes?.keys.orEmpty()
        requirements.forEachIndexed { index, requirement ->
            requirement.keys.forEach { schemeName ->
                if (schemeName.isBlank()) return@forEach
                val known = schemeNames.contains(schemeName)
                val looksLikeUri = looksLikeUriReference(schemeName)
                if (!known && !looksLikeUri) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = "$path[$index]",
                        message = "Security requirement references undefined scheme '$schemeName'."
                    )
                }
            }
        }
    }

    private fun looksLikeUriReference(value: String): Boolean {
        return value.startsWith("#") ||
            value.startsWith("./") ||
            value.startsWith("../") ||
            value.startsWith("/") ||
            value.startsWith("urn:") ||
            value.contains("://")
    }

    private fun validatePathTemplating(
        pathKey: String,
        item: PathItem,
        basePath: String,
        issues: MutableList<OpenApiIssue>
    ) {
        val templateParams = PATH_TEMPLATE_REGEX.findAll(pathKey)
            .map { it.groupValues[1] }
            .toList()
        if (templateParams.isEmpty()) return

        val duplicates = templateParams.groupBy { it }.filter { it.value.size > 1 }
        duplicates.forEach { (name, values) ->
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = basePath,
                message = "Path template parameter '$name' must not appear more than once (found ${values.size})."
            )
        }

        val pathLevelParams = item.parameters.filter { it.location == ParameterLocation.PATH }
        pathLevelParams.forEach { param ->
            if (!templateParams.contains(param.name)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath.parameters",
                    message = "Path parameter '${param.name}' is not present in the path template."
                )
            }
        }

        val operations = collectOperations(item)
        operations.forEach { op ->
            val effectiveParams = (item.parameters + op.parameters).filter { it.location == ParameterLocation.PATH }
            templateParams.forEach { name ->
                if (effectiveParams.none { it.name == name }) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = "$basePath.${op.methodName.lowercase()}",
                        message = "Missing path parameter '$name' for operation ${op.operationId}."
                    )
                }
            }
            op.parameters.filter { it.location == ParameterLocation.PATH }.forEach { param ->
                if (!templateParams.contains(param.name)) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = "$basePath.${op.methodName.lowercase()}.parameters",
                        message = "Path parameter '${param.name}' is not present in the path template."
                    )
                }
            }
        }
    }

    private fun collectOperations(paths: Map<String, PathItem>): List<EndpointDefinition> {
        return paths.values.flatMap { collectOperations(it) }
    }

    private fun collectOperations(item: PathItem): List<EndpointDefinition> {
        val ops = mutableListOf<EndpointDefinition>()
        item.get?.let { ops.add(it) }
        item.put?.let { ops.add(it) }
        item.post?.let { ops.add(it) }
        item.delete?.let { ops.add(it) }
        item.options?.let { ops.add(it) }
        item.head?.let { ops.add(it) }
        item.patch?.let { ops.add(it) }
        item.trace?.let { ops.add(it) }
        item.query?.let { ops.add(it) }
        if (item.additionalOperations.isNotEmpty()) {
            ops.addAll(item.additionalOperations.values)
        }
        return ops
    }

    private fun validatePathTemplateCollisions(
        pathKeys: Set<String>,
        basePath: String,
        issues: MutableList<OpenApiIssue>
    ) {
        val normalized = pathKeys.groupBy { normalizePathTemplate(it) }
            .filter { it.value.size > 1 }
        normalized.forEach { (template, paths) ->
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = basePath,
                message = "Paths share the same templated structure '$template': ${paths.joinToString(", ")}."
            )
        }
    }

    private fun normalizePathTemplate(path: String): String {
        return PATH_TEMPLATE_REGEX.replace(path, "{}")
    }

    private companion object {
        val PATH_TEMPLATE_REGEX = "\\{([^}]+)}".toRegex()
        val SERVER_VARIABLE_REGEX = "\\{([^}]+)}".toRegex()
    }
}

/**
 * Validation issue severity.
 */
enum class OpenApiIssueSeverity {
    ERROR,
    WARNING
}

/**
 * A single validation issue discovered in an OpenAPI definition.
 */
data class OpenApiIssue(
    val severity: OpenApiIssueSeverity,
    val path: String,
    val message: String
)
