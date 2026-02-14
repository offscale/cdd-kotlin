package openapi

import domain.Callback
import domain.Components
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.Header
import domain.Link
import domain.OpenApiDefinition
import domain.ParameterLocation
import domain.ParameterStyle
import domain.PathItem
import domain.ReferenceObject
import domain.RequestBody
import domain.SchemaDefinition
import domain.SchemaProperty
import domain.Server
import domain.Xml
import domain.DynamicAnchorContext
import domain.DynamicAnchorScope
import domain.buildDynamicAnchorContext
import domain.extractDynamicAnchorName
import java.net.URI
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Validates OpenAPI 3.2 documents for common specification constraints.
 *
 * This validator is intentionally focused on structural rules that are easy to
 * enforce without full semantic resolution or network access.
 *
 * Note: When a Reference Object is used for a Parameter, Header, RequestBody,
 * Response, or Path Item, validation short-circuits for that object to avoid
 * false positives from resolved/denormalized IR fields.
 */
class OpenApiValidator {
    private val componentKeyRegex = Regex("^[a-zA-Z0-9\\._-]+$")
    private var currentSelfBase: String? = null

    /**
     * Validate the given OpenAPI definition and return any detected issues.
     *
     * @param baseUri Optional base URI used to resolve relative `$self` bases for link operationRef validation.
     */
    fun validate(definition: OpenApiDefinition, baseUri: String? = null): List<OpenApiIssue> {
        if (!definition.openapi.startsWith("3.")) return emptyList()

        val issues = mutableListOf<OpenApiIssue>()
        val resolvedSelfBase = resolveSelfBase(definition.self, baseUri)
        val previousSelfBase = currentSelfBase
        currentSelfBase = resolvedSelfBase

        try {
            val hasPaths = definition.paths.isNotEmpty() || definition.pathsExplicitEmpty
            val hasWebhooks = definition.webhooks.isNotEmpty() || definition.webhooksExplicitEmpty
            if (!hasPaths && !hasWebhooks && definition.components == null) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$.openapi",
                    message = "OpenAPI requires at least one of: paths, webhooks, or components."
                )
            }

            validateOpenApiVersion(definition, "$.openapi", issues)
            definition.jsonSchemaDialect?.let { validateUri(it, "$.jsonSchemaDialect", issues) }
            definition.self?.let { validateUri(it, "$.\$self", issues) }
            validateServers(definition.servers, "$.servers", issues)
            validateInfo(definition.info, "$.info", issues)
            definition.externalDocs?.let { validateExternalDocs(it, "$.externalDocs", issues) }
            validatePaths(definition.paths, "$.paths", issues, definition.components)
            validateWebhooks(definition.webhooks, "$.webhooks", issues, definition.components)
            validateComponents(definition.components, "$.components", issues)
            validateSecurityRequirements(definition.security, definition.components, "$.security", issues)
            validateOperationIds(definition, issues)
            validateLinkTargets(definition, issues, resolvedSelfBase)
            validateTags(definition, issues)
            validateSchemas(definition, issues)

            return issues
        } finally {
            currentSelfBase = previousSelfBase
        }
    }

    private data class SchemaValidationState(
        val definitions: MutableSet<SchemaDefinition> = Collections.newSetFromMap(IdentityHashMap()),
        val properties: MutableSet<SchemaProperty> = Collections.newSetFromMap(IdentityHashMap()),
        val defaultDialect: String,
        val componentSchemaKeys: Set<String>
    )

    private fun validateSchemas(definition: OpenApiDefinition, issues: MutableList<OpenApiIssue>) {
        val defaultDialect = normalizeDialect(definition.jsonSchemaDialect) ?: OAS_DIALECT_URI
        definition.jsonSchemaDialect?.let { rawDialect ->
            if (!isKnownDialect(defaultDialect)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.WARNING,
                    path = "$.jsonSchemaDialect",
                    message = "jsonSchemaDialect '$rawDialect' is not a recognized schema dialect."
                )
            }
        }
        val componentSchemaKeys = definition.components?.schemas?.keys.orEmpty()
        val state = SchemaValidationState(
            defaultDialect = defaultDialect,
            componentSchemaKeys = componentSchemaKeys
        )

        definition.components?.schemas?.forEach { (name, schema) ->
            validateSchemaDefinition(schema, "$.components.schemas.$name", issues, state)
        }
        definition.components?.parameters?.forEach { (name, param) ->
            validateSchemaInParameter(param, "$.components.parameters.$name", issues, state)
        }
        definition.components?.headers?.forEach { (name, header) ->
            validateSchemaInHeader(header, "$.components.headers.$name", issues, state)
        }
        definition.components?.requestBodies?.forEach { (name, body) ->
            validateSchemaInRequestBody(body, "$.components.requestBodies.$name", issues, state)
        }
        definition.components?.responses?.forEach { (name, response) ->
            validateSchemaInResponse(response, "$.components.responses.$name", issues, state)
        }
        definition.components?.mediaTypes?.forEach { (name, mediaType) ->
            validateSchemaInMediaType(mediaType, "$.components.mediaTypes.$name", issues, state)
        }
        definition.components?.callbacks?.forEach { (name, callback) ->
            validateSchemaInCallbacks(mapOf(name to callback), "$.components.callbacks", issues, state)
        }
        definition.components?.pathItems?.forEach { (name, item) ->
            validateSchemaInPathItem(item, "$.components.pathItems.$name", issues, state)
        }

        validateSchemaInPaths(definition.paths, "$.paths", issues, state)
        validateSchemaInPaths(definition.webhooks, "$.webhooks", issues, state)
    }

    private fun validateSchemaInPaths(
        paths: Map<String, PathItem>,
        basePath: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState
    ) {
        paths.forEach { (pathKey, item) ->
            validateSchemaInPathItem(item, "$basePath.$pathKey", issues, state)
        }
    }

    private fun validateSchemaInCallbacks(
        callbacks: Map<String, Callback>,
        path: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState
    ) {
        callbacks.forEach { (name, callback) ->
            if (callback is Callback.Inline) {
                callback.expressions.forEach { (expression, pathItem) ->
                    val pathItemPath = "$path.$name.$expression"
                    validateSchemaInPathItem(pathItem, pathItemPath, issues, state)
                }
            }
        }
    }

    private fun validateSchemaInPathItem(
        item: PathItem,
        basePath: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState
    ) {
        if (item.ref != null) return
        if (item.parameters.isNotEmpty()) {
            validateSchemaInParameters(item.parameters, "$basePath.parameters", issues, state)
        }
        val operations = collectOperations(item)
        operations.forEach { op ->
            val opPath = "$basePath.${op.methodName.lowercase()}"
            if (op.parameters.isNotEmpty()) {
                validateSchemaInParameters(op.parameters, "$opPath.parameters", issues, state)
            }
            op.requestBody?.let { validateSchemaInRequestBody(it, "$opPath.requestBody", issues, state) }
            op.responses.forEach { (code, response) ->
                validateSchemaInResponse(response, "$opPath.responses.$code", issues, state)
            }
            if (op.callbacks.isNotEmpty()) {
                validateSchemaInCallbacks(op.callbacks, "$opPath.callbacks", issues, state)
            }
        }
    }

    private fun validateSchemaInParameters(
        parameters: List<EndpointParameter>,
        basePath: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState
    ) {
        parameters.forEachIndexed { index, param ->
            validateSchemaInParameter(param, "$basePath[$index]", issues, state)
        }
    }

    private fun validateSchemaInParameter(
        parameter: EndpointParameter,
        path: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState
    ) {
        if (parameter.reference != null) return
        parameter.schema?.let { validateSchemaProperty(it, "$path.schema", issues, state) }
        parameter.content.forEach { (mediaType, media) ->
            validateSchemaInMediaType(media, "$path.content.$mediaType", issues, state)
        }
    }

    private fun validateSchemaInHeader(
        header: Header,
        path: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState
    ) {
        if (header.reference != null) return
        header.schema?.let { validateSchemaProperty(it, "$path.schema", issues, state) }
        header.content.forEach { (mediaType, media) ->
            validateSchemaInMediaType(media, "$path.content.$mediaType", issues, state)
        }
    }

    private fun validateSchemaInRequestBody(
        body: RequestBody,
        path: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState
    ) {
        if (body.reference != null) return
        body.content.forEach { (mediaType, media) ->
            validateSchemaInMediaType(media, "$path.content.$mediaType", issues, state)
        }
    }

    private fun validateSchemaInResponse(
        response: EndpointResponse,
        path: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState
    ) {
        if (response.reference != null) return
        response.content.forEach { (mediaType, media) ->
            validateSchemaInMediaType(media, "$path.content.$mediaType", issues, state)
        }
    }

    private fun validateSchemaInMediaType(
        media: domain.MediaTypeObject,
        path: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState
    ) {
        if (media.ref != null || media.reference != null) return
        media.schema?.let { validateSchemaProperty(it, "$path.schema", issues, state) }
        media.itemSchema?.let { validateSchemaProperty(it, "$path.itemSchema", issues, state) }
    }

    private fun validateSchemaDefinition(
        schema: SchemaDefinition,
        path: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState
    ) {
        val dynamicContext = buildDynamicAnchorContext(schema)
        validateSchemaDefinition(schema, path, issues, state, dynamicContext, state.defaultDialect, emptySet())
    }

    private fun validateSchemaDefinition(
        schema: SchemaDefinition,
        path: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState,
        dynamicContext: DynamicAnchorContext,
        currentDialect: String,
        defsScope: Set<String>
    ) {
        if (!state.definitions.add(schema)) return
        val defsInScope = mergeDefsScope(defsScope, schema.defs.keys)
        val effectiveDialect = resolveDialect(schema.schemaDialect, currentDialect)
        if (schema.schemaDialect != null && !isKnownDialect(effectiveDialect)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$path.\$schema",
                message = "Schema dialect '$effectiveDialect' is not a recognized schema dialect."
            )
        }
        schema.ref?.let {
            validateUri(it, "$path.\$ref", issues)
            validateSchemaRefTargets(it, "$path.\$ref", issues, state, defsInScope)
        }
        schema.dynamicRef?.let { ref ->
            validateUri(ref, "$path.\$dynamicRef", issues)
            validateDynamicRefResolution(dynamicContext.root, ref, "$path.\$dynamicRef", issues, dynamicContext.scope)
        }
        schema.schemaId?.let { validateUri(it, "$path.\$id", issues) }
        schema.schemaDialect?.let { validateUri(it, "$path.\$schema", issues) }
        validateDialectWarnings(schema, path, issues, effectiveDialect)
        validateDiscriminator(
            schema.discriminator,
            schema.required,
            schema.properties,
            schemaHasComposition(schema.oneOf, schema.anyOf, schema.allOf, schema.oneOfSchemas, schema.anyOfSchemas, schema.allOfSchemas),
            path,
            issues
        )
        validateXml(schema.xml, schema.types.ifEmpty { setOf(schema.type) }, path, issues)
        validateSchemaConstraints(
            booleanSchema = schema.booleanSchema,
            types = schema.types.ifEmpty { setOf(schema.type) },
            minLength = schema.minLength,
            maxLength = schema.maxLength,
            minItems = schema.minItems,
            maxItems = schema.maxItems,
            minProperties = schema.minProperties,
            maxProperties = schema.maxProperties,
            minContains = schema.minContains,
            maxContains = schema.maxContains,
            contains = schema.contains,
            contentMediaType = schema.contentMediaType,
            contentEncoding = schema.contentEncoding,
            path = path,
            issues = issues
        )

        schema.defs.forEach { (name, sub) ->
            validateSchemaPropertyInternal(
                sub,
                "$path.\$defs.$name",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.items?.let {
            validateSchemaPropertyInternal(it, "$path.items", issues, state, dynamicContext.scope, effectiveDialect, defsInScope)
        }
        schema.prefixItems.forEachIndexed { index, item ->
            validateSchemaPropertyInternal(
                item,
                "$path.prefixItems[$index]",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.contains?.let {
            validateSchemaPropertyInternal(it, "$path.contains", issues, state, dynamicContext.scope, effectiveDialect, defsInScope)
        }
        schema.properties.forEach { (name, prop) ->
            validateSchemaPropertyInternal(
                prop,
                "$path.properties.$name",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.patternProperties.forEach { (name, prop) ->
            validateSchemaPropertyInternal(
                prop,
                "$path.patternProperties.$name",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.propertyNames?.let {
            validateSchemaPropertyInternal(
                it,
                "$path.propertyNames",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.additionalProperties?.let {
            validateSchemaPropertyInternal(
                it,
                "$path.additionalProperties",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.dependentSchemas.forEach { (name, prop) ->
            validateSchemaPropertyInternal(
                prop,
                "$path.dependentSchemas.$name",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.unevaluatedProperties?.let {
            validateSchemaPropertyInternal(
                it,
                "$path.unevaluatedProperties",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.unevaluatedItems?.let {
            validateSchemaPropertyInternal(
                it,
                "$path.unevaluatedItems",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.contentSchema?.let {
            validateSchemaPropertyInternal(
                it,
                "$path.contentSchema",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.oneOfSchemas.forEachIndexed { index, prop ->
            validateSchemaPropertyInternal(
                prop,
                "$path.oneOf[$index]",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.anyOfSchemas.forEachIndexed { index, prop ->
            validateSchemaPropertyInternal(
                prop,
                "$path.anyOf[$index]",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.allOfSchemas.forEachIndexed { index, prop ->
            validateSchemaPropertyInternal(
                prop,
                "$path.allOf[$index]",
                issues,
                state,
                dynamicContext.scope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.not?.let { validateSchemaPropertyInternal(it, "$path.not", issues, state, dynamicContext.scope, effectiveDialect, defsInScope) }
        schema.ifSchema?.let { validateSchemaPropertyInternal(it, "$path.if", issues, state, dynamicContext.scope, effectiveDialect, defsInScope) }
        schema.thenSchema?.let { validateSchemaPropertyInternal(it, "$path.then", issues, state, dynamicContext.scope, effectiveDialect, defsInScope) }
        schema.elseSchema?.let { validateSchemaPropertyInternal(it, "$path.else", issues, state, dynamicContext.scope, effectiveDialect, defsInScope) }
    }

    private fun validateSchemaProperty(
        schema: SchemaProperty,
        path: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState
    ) {
        val dynamicContext = buildDynamicAnchorContext(schema)
        validateSchemaPropertyInternal(schema, path, issues, state, dynamicContext.scope, state.defaultDialect, emptySet())
    }

    private fun validateSchemaPropertyInternal(
        schema: SchemaProperty,
        path: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState,
        dynamicScope: DynamicAnchorScope,
        currentDialect: String,
        defsScope: Set<String>
    ) {
        if (!state.properties.add(schema)) return
        val defsInScope = mergeDefsScope(defsScope, schema.defs.keys)
        val effectiveDialect = resolveDialect(schema.schemaDialect, currentDialect)
        if (schema.schemaDialect != null && !isKnownDialect(effectiveDialect)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$path.\$schema",
                message = "Schema dialect '$effectiveDialect' is not a recognized schema dialect."
            )
        }
        schema.ref?.let {
            validateUri(it, "$path.\$ref", issues)
            validateSchemaRefTargets(it, "$path.\$ref", issues, state, defsInScope)
        }
        schema.dynamicRef?.let { ref ->
            validateUri(ref, "$path.\$dynamicRef", issues)
            validateDynamicRefResolution(schema, ref, "$path.\$dynamicRef", issues, dynamicScope)
        }
        schema.schemaId?.let { validateUri(it, "$path.\$id", issues) }
        schema.schemaDialect?.let { validateUri(it, "$path.\$schema", issues) }
        validateDialectWarnings(schema, path, issues, effectiveDialect)
        val hasComposition = schema.oneOf.isNotEmpty() || schema.anyOf.isNotEmpty() || schema.allOf.isNotEmpty()
        validateDiscriminator(
            schema.discriminator,
            schema.required,
            schema.properties,
            hasComposition,
            path,
            issues
        )
        validateXml(schema.xml, schema.types, path, issues)
        validateSchemaConstraints(
            booleanSchema = schema.booleanSchema,
            types = schema.types,
            minLength = schema.minLength,
            maxLength = schema.maxLength,
            minItems = schema.minItems,
            maxItems = schema.maxItems,
            minProperties = schema.minProperties,
            maxProperties = schema.maxProperties,
            minContains = schema.minContains,
            maxContains = schema.maxContains,
            contains = schema.contains,
            contentMediaType = schema.contentMediaType,
            contentEncoding = schema.contentEncoding,
            path = path,
            issues = issues
        )

        schema.defs.forEach { (name, sub) ->
            validateSchemaPropertyInternal(
                sub,
                "$path.\$defs.$name",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.items?.let {
            validateSchemaPropertyInternal(it, "$path.items", issues, state, dynamicScope, effectiveDialect, defsInScope)
        }
        schema.prefixItems.forEachIndexed { index, item ->
            validateSchemaPropertyInternal(
                item,
                "$path.prefixItems[$index]",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.contains?.let {
            validateSchemaPropertyInternal(it, "$path.contains", issues, state, dynamicScope, effectiveDialect, defsInScope)
        }
        schema.properties.forEach { (name, prop) ->
            validateSchemaPropertyInternal(
                prop,
                "$path.properties.$name",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.patternProperties.forEach { (name, prop) ->
            validateSchemaPropertyInternal(
                prop,
                "$path.patternProperties.$name",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.propertyNames?.let {
            validateSchemaPropertyInternal(
                it,
                "$path.propertyNames",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.additionalProperties?.let {
            validateSchemaPropertyInternal(
                it,
                "$path.additionalProperties",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.dependentSchemas.forEach { (name, prop) ->
            validateSchemaPropertyInternal(
                prop,
                "$path.dependentSchemas.$name",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.unevaluatedProperties?.let {
            validateSchemaPropertyInternal(
                it,
                "$path.unevaluatedProperties",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.unevaluatedItems?.let {
            validateSchemaPropertyInternal(
                it,
                "$path.unevaluatedItems",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.contentSchema?.let {
            validateSchemaPropertyInternal(
                it,
                "$path.contentSchema",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.oneOf.forEachIndexed { index, prop ->
            validateSchemaPropertyInternal(
                prop,
                "$path.oneOf[$index]",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.anyOf.forEachIndexed { index, prop ->
            validateSchemaPropertyInternal(
                prop,
                "$path.anyOf[$index]",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.allOf.forEachIndexed { index, prop ->
            validateSchemaPropertyInternal(
                prop,
                "$path.allOf[$index]",
                issues,
                state,
                dynamicScope,
                effectiveDialect,
                defsInScope
            )
        }
        schema.not?.let { validateSchemaPropertyInternal(it, "$path.not", issues, state, dynamicScope, effectiveDialect, defsInScope) }
        schema.ifSchema?.let { validateSchemaPropertyInternal(it, "$path.if", issues, state, dynamicScope, effectiveDialect, defsInScope) }
        schema.thenSchema?.let { validateSchemaPropertyInternal(it, "$path.then", issues, state, dynamicScope, effectiveDialect, defsInScope) }
        schema.elseSchema?.let { validateSchemaPropertyInternal(it, "$path.else", issues, state, dynamicScope, effectiveDialect, defsInScope) }
    }

    private fun validateSchemaConstraints(
        booleanSchema: Boolean?,
        types: Set<String>,
        minLength: Int?,
        maxLength: Int?,
        minItems: Int?,
        maxItems: Int?,
        minProperties: Int?,
        maxProperties: Int?,
        minContains: Int?,
        maxContains: Int?,
        contains: SchemaProperty?,
        contentMediaType: String?,
        contentEncoding: String?,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        if (booleanSchema != null) return

        fun checkNonNegative(value: Int?, keyword: String) {
            if (value != null && value < 0) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.$keyword",
                    message = "$keyword must be greater than or equal to 0."
                )
            }
        }

        fun checkMinMaxOrder(min: Int?, max: Int?, label: String, minKey: String, maxKey: String) {
            if (min != null && max != null && min > max) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.$minKey",
                    message = "$minKey must be less than or equal to $maxKey for $label."
                )
            }
        }

        checkNonNegative(minLength, "minLength")
        checkNonNegative(maxLength, "maxLength")
        checkNonNegative(minItems, "minItems")
        checkNonNegative(maxItems, "maxItems")
        checkNonNegative(minProperties, "minProperties")
        checkNonNegative(maxProperties, "maxProperties")
        checkNonNegative(minContains, "minContains")
        checkNonNegative(maxContains, "maxContains")

        checkMinMaxOrder(minLength, maxLength, "string length", "minLength", "maxLength")
        checkMinMaxOrder(minItems, maxItems, "array size", "minItems", "maxItems")
        checkMinMaxOrder(minProperties, maxProperties, "object size", "minProperties", "maxProperties")
        checkMinMaxOrder(minContains, maxContains, "contains bounds", "minContains", "maxContains")

        if ((minContains != null || maxContains != null) && contains == null) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$path.contains",
                message = "minContains/maxContains are ignored without a contains schema."
            )
        }

        if (contentMediaType != null && !isValidMediaTypeKey(contentMediaType)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.contentMediaType",
                message = "contentMediaType must be a valid media type."
            )
        }

        if (contentEncoding != null) {
            if (contentEncoding.isBlank()) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.contentEncoding",
                    message = "contentEncoding must not be blank."
                )
            }
            if (types.isNotEmpty() && !types.contains("string")) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.WARNING,
                    path = "$path.contentEncoding",
                    message = "contentEncoding is only applicable to string schemas."
                )
            }
        }
    }

    private fun mergeDefsScope(
        current: Set<String>,
        defs: Set<String>
    ): Set<String> {
        if (defs.isEmpty()) return current
        if (current.isEmpty()) return defs
        return current + defs
    }

    private fun validateSchemaRefTargets(
        ref: String,
        path: String,
        issues: MutableList<OpenApiIssue>,
        state: SchemaValidationState,
        defsScope: Set<String>
    ) {
        val componentRef = extractComponentRefInfo(ref, "schemas")
        if (componentRef != null && isLocalComponentRef(componentRef.base)) {
            if (!state.componentSchemaKeys.contains(componentRef.name)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = path,
                    message = "Schema \$ref '$ref' does not resolve to components.schemas."
                )
            }
        }
        val defsRef = extractDefsRefName(ref)
        if (defsRef != null && defsScope.isNotEmpty() && !defsScope.contains(defsRef)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Schema \$ref '$ref' does not resolve to a \$defs entry in scope."
            )
        }
    }

    private fun extractDefsRefName(ref: String): String? {
        val marker = "#/\$defs/"
        val index = ref.indexOf(marker)
        if (index < 0) return null
        val base = ref.substring(0, index)
        if (base.isNotBlank()) return null
        val raw = ref.substring(index + marker.length)
        if (raw.isBlank()) return null
        val first = raw.substringBefore("/")
        return decodeJsonPointerSegment(percentDecode(first))
    }

    private fun normalizeDialect(value: String?): String? {
        val trimmed = value?.trim()?.trimEnd('#') ?: return null
        return trimmed.ifBlank { null }
    }

    private fun resolveDialect(explicitDialect: String?, fallbackDialect: String): String {
        return normalizeDialect(explicitDialect) ?: fallbackDialect
    }

    private fun isKnownDialect(dialect: String): Boolean {
        return isOasDialect(dialect) || isKnownJsonSchemaDialect(dialect)
    }

    private fun isOasDialect(dialect: String): Boolean {
        return normalizeDialect(dialect) == OAS_DIALECT_URI
    }

    private fun isKnownJsonSchemaDialect(dialect: String): Boolean {
        val normalized = normalizeDialect(dialect) ?: return false
        return KNOWN_JSON_SCHEMA_DIALECTS.contains(normalized)
    }

    private fun validateDialectWarnings(
        schema: SchemaDefinition,
        path: String,
        issues: MutableList<OpenApiIssue>,
        dialect: String
    ) {
        val oasKeywords = mutableListOf<String>()
        if (schema.discriminator != null) oasKeywords += "discriminator"
        if (schema.xml != null) oasKeywords += "xml"
        if (schema.externalDocs != null) oasKeywords += "externalDocs"
        if (schema.example != null) oasKeywords += "example"
        if (!schema.examples.isNullOrEmpty()) oasKeywords += "examples"
        warnDialectUsage(
            dialect = dialect,
            path = path,
            issues = issues,
            oasKeywords = oasKeywords,
            hasCustomKeywords = schema.customKeywords.isNotEmpty(),
            hasExtensions = schema.extensions.isNotEmpty()
        )
    }

    private fun validateDialectWarnings(
        schema: SchemaProperty,
        path: String,
        issues: MutableList<OpenApiIssue>,
        dialect: String
    ) {
        val oasKeywords = mutableListOf<String>()
        if (schema.discriminator != null) oasKeywords += "discriminator"
        if (schema.xml != null) oasKeywords += "xml"
        if (schema.externalDocs != null) oasKeywords += "externalDocs"
        if (schema.example != null) oasKeywords += "example"
        warnDialectUsage(
            dialect = dialect,
            path = path,
            issues = issues,
            oasKeywords = oasKeywords,
            hasCustomKeywords = schema.customKeywords.isNotEmpty(),
            hasExtensions = schema.extensions.isNotEmpty()
        )
    }

    private fun warnDialectUsage(
        dialect: String,
        path: String,
        issues: MutableList<OpenApiIssue>,
        oasKeywords: List<String>,
        hasCustomKeywords: Boolean,
        hasExtensions: Boolean
    ) {
        if (isOasDialect(dialect)) return

        if (oasKeywords.isNotEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "Schema dialect '$dialect' does not include the OpenAPI vocabulary; keywords " +
                    "${oasKeywords.sorted().joinToString(", ")} may be ignored."
            )
        }

        if (hasCustomKeywords || hasExtensions) {
            val sources = buildList {
                if (hasCustomKeywords) add("custom keywords")
                if (hasExtensions) add("extensions")
            }
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "Schema dialect '$dialect' does not allow ${sources.joinToString(" and ")}; " +
                    "unknown keywords may be ignored."
            )
        }
    }

    private fun validateDynamicRefResolution(
        schema: SchemaProperty,
        ref: String,
        path: String,
        issues: MutableList<OpenApiIssue>,
        dynamicScope: DynamicAnchorScope
    ) {
        val anchorName = extractDynamicAnchorName(ref) ?: return
        val resolved = dynamicScope.resolveDynamicRef(schema, ref)
        if (resolved == null) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "dynamicRef '$ref' does not resolve to a dynamicAnchor in scope."
            )
        }
    }

    private fun schemaHasComposition(
        oneOfRefs: List<String>,
        anyOfRefs: List<String>,
        allOfRefs: List<String>,
        oneOfSchemas: List<SchemaProperty>,
        anyOfSchemas: List<SchemaProperty>,
        allOfSchemas: List<SchemaProperty>
    ): Boolean {
        return oneOfRefs.isNotEmpty() ||
            anyOfRefs.isNotEmpty() ||
            allOfRefs.isNotEmpty() ||
            oneOfSchemas.isNotEmpty() ||
            anyOfSchemas.isNotEmpty() ||
            allOfSchemas.isNotEmpty()
    }

    private fun validateDiscriminator(
        discriminator: domain.Discriminator?,
        required: List<String>,
        properties: Map<String, SchemaProperty>,
        hasComposition: Boolean,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        val disc = discriminator ?: return
        if (!hasComposition) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.discriminator",
                message = "Discriminator requires oneOf, anyOf, or allOf on the same schema."
            )
        }
        if (properties.isNotEmpty() && !properties.containsKey(disc.propertyName)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$path.discriminator.propertyName",
                message = "Discriminator property '${disc.propertyName}' is not defined in schema properties."
            )
        }
        if (!required.contains(disc.propertyName) && disc.defaultMapping.isNullOrBlank()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.discriminator.defaultMapping",
                message = "Discriminator defaultMapping is required when the property is not required."
            )
        }
    }

    private fun validateXml(
        xml: Xml?,
        types: Set<String>,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        val xmlObj = xml ?: return
        val nodeType = xmlObj.nodeType?.lowercase()
        val isArray = types.contains("array")

        if (nodeType != null) {
            if (xmlObj.attribute) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.xml.attribute",
                    message = "xml.attribute must not be set when xml.nodeType is present."
                )
            }
            if (xmlObj.wrapped) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.xml.wrapped",
                    message = "xml.wrapped must not be set when xml.nodeType is present."
                )
            }
        }

        if (xmlObj.wrapped && types.isNotEmpty() && !isArray) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.xml.wrapped",
                message = "xml.wrapped is only valid for array schemas."
            )
        }

        if (nodeType == "attribute" && types.isNotEmpty() && isArray) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.xml.nodeType",
                message = "xml.nodeType 'attribute' is not valid for array schemas."
            )
        }

        if (nodeType == "text" || nodeType == "cdata" || nodeType == "none") {
            if (!xmlObj.name.isNullOrBlank()) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.WARNING,
                    path = "$path.xml.name",
                    message = "xml.name is ignored for nodeType '$nodeType'."
                )
            }
        }
    }

    /**
     * Validate Info object constraints that are not enforced by the data model.
     */
    private fun validateInfo(info: domain.Info, basePath: String, issues: MutableList<OpenApiIssue>) {
        if (info.title.isBlank()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.title",
                message = "Info title must not be blank."
            )
        }
        if (info.version.isBlank()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.version",
                message = "Info version must not be blank."
            )
        }
        info.termsOfService?.let { validateUri(it, "$basePath.termsOfService", issues) }
        info.contact?.let { validateContact(it, "$basePath.contact", issues) }
        info.license?.let { validateLicense(it, "$basePath.license", issues) }
    }

    private fun validateContact(contact: domain.Contact, path: String, issues: MutableList<OpenApiIssue>) {
        contact.url?.let { validateUri(it, "$path.url", issues) }
        contact.email?.let { validateEmail(it, "$path.email", issues) }
    }

    private fun validateOpenApiVersion(
        definition: OpenApiDefinition,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        if (!isSupportedOpenApiVersion(definition.openapi)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "OpenAPI version '${definition.openapi}' is not 3.2.x; 3.2 features may be unsupported."
            )
        }
    }

    private fun isSupportedOpenApiVersion(version: String): Boolean {
        val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(version) ?: return false
        val (major, minor, _) = match.destructured
        return major == "3" && minor == "2"
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
        license.url?.let { validateUri(it, "$path.url", issues) }
    }

    private fun validateServers(servers: List<Server>, basePath: String, issues: MutableList<OpenApiIssue>) {
        if (servers.isEmpty()) return
        val duplicateNames = servers.mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }
            .groupBy { it }
            .filter { it.value.size > 1 }
        duplicateNames.forEach { (name, values) ->
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = basePath,
                message = "Server name '$name' must be unique within the server list (found ${values.size})."
            )
        }
        servers.forEachIndexed { index, server ->
            if (server.url.contains("?") || server.url.contains("#")) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath[$index].url",
                    message = "Server url must not include query or fragment."
                )
            }
            validateUri(server.url, "$basePath[$index].url", issues)
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
            if (definedVariables.isNotEmpty()) {
                val unused = definedVariables.filterNot { variablesInUrl.contains(it) }
                if (unused.isNotEmpty()) {
                    val detail = if (variablesInUrl.isEmpty()) {
                        "Server variables are defined but no variables are used in the url."
                    } else {
                        "Server variables are defined but not used in the url: ${unused.joinToString(", ")}."
                    }
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.WARNING,
                        path = "$basePath[$index].variables",
                        message = detail
                    )
                }
            }
            server.variables?.forEach { (name, variable) ->
                if (name.contains("{") || name.contains("}")) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = "$basePath[$index].variables.$name",
                        message = "Server variable name '$name' must not contain '{' or '}'."
                    )
                }
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
            if (pathKey.contains("?") || pathKey.contains("#")) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath.$pathKey",
                    message = "Path keys must not include query or fragment."
                )
            }
            validatePathItem(pathKey, item, "$basePath.$pathKey", issues, components, validateTemplating = true)
        }
        validatePathTemplateCollisions(paths.keys, basePath, issues)
    }

    /**
     * Validate webhook entries. Webhook keys are free-form identifiers and do not use path key rules,
     * but their Path Items and operations should still be validated.
     */
    private fun validateWebhooks(
        webhooks: Map<String, PathItem>,
        basePath: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        webhooks.forEach { (name, item) ->
            validatePathItem(name, item, "$basePath.$name", issues, components, validateTemplating = false)
        }
    }

    private fun validatePathItem(
        pathKey: String,
        item: PathItem,
        basePath: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?,
        validateTemplating: Boolean
    ) {
        item.ref?.let {
            validateUri(it, "$basePath.\$ref", issues)
            validateComponentReference(it, "pathItems", components, "$basePath.\$ref", issues)
        }
        if (item.ref != null && hasPathItemSiblings(item)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$basePath.\$ref",
                message = "Path Item with \$ref should not define other fields; sibling fields are ignored."
            )
        }
        if (item.ref != null) {
            return
        }
        if (validateTemplating) {
            validatePathTemplating(pathKey, item, basePath, issues)
        }
        validatePathItemParameters(item, "$basePath.parameters", issues, components)
        validateServers(item.servers, "$basePath.servers", issues)
        item.get?.let { validateOperation(it, "$basePath.get", issues, components, item.parameters) }
        item.put?.let { validateOperation(it, "$basePath.put", issues, components, item.parameters) }
        item.post?.let { validateOperation(it, "$basePath.post", issues, components, item.parameters) }
        item.delete?.let { validateOperation(it, "$basePath.delete", issues, components, item.parameters) }
        item.options?.let { validateOperation(it, "$basePath.options", issues, components, item.parameters) }
        item.head?.let { validateOperation(it, "$basePath.head", issues, components, item.parameters) }
        item.patch?.let { validateOperation(it, "$basePath.patch", issues, components, item.parameters) }
        item.trace?.let { validateOperation(it, "$basePath.trace", issues, components, item.parameters) }
        item.query?.let { validateOperation(it, "$basePath.query", issues, components, item.parameters) }

        val reserved = setOf("get", "put", "post", "delete", "options", "head", "patch", "trace", "query")
        item.additionalOperations.keys.forEach { method ->
            if (reserved.contains(method.lowercase())) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath.additionalOperations.$method",
                    message = "additionalOperations must not include standard HTTP methods."
                )
            }
            if (!HTTP_METHOD_TOKEN_REGEX.matches(method)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath.additionalOperations.$method",
                    message = "additionalOperations method '$method' must be a valid HTTP token."
                )
            }
        }
    }

    private fun validateOperation(
        operation: EndpointDefinition,
        basePath: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?,
        pathItemParameters: List<EndpointParameter> = emptyList()
    ) {
        if (operation.responses.isEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.responses",
                message = "Operation must define at least one response."
            )
        }
        if (operation.responses.size == 1) {
            val onlyCode = operation.responses.keys.firstOrNull().orEmpty()
            val isSuccess = onlyCode.startsWith("2") || onlyCode.equals("default", ignoreCase = true)
            if (!isSuccess) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.WARNING,
                    path = "$basePath.responses",
                    message = "Only one response is defined and it is not a success response (2XX)."
                )
            }
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

        val combinedParams = pathItemParameters + operation.parameters
        val queryParams = combinedParams.filter { it.location == ParameterLocation.QUERY }
        val queryStringParams = combinedParams.filter { it.location == ParameterLocation.QUERYSTRING }
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
            validateParameter(param, "$basePath.parameters[$index]", issues, components)
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
                validateHeader(header, "$responsePath.headers.$headerName", issues, components)
            }
            validateResponse(entry.value, responsePath, issues, components)
        }

        operation.requestBody?.let { validateRequestBody(it, "$basePath.requestBody", issues, components) }
        if (operation.callbacks.isNotEmpty()) {
            validateCallbacks(operation.callbacks, "$basePath.callbacks", issues, components)
        }
        operation.externalDocs?.let { validateExternalDocs(it, "$basePath.externalDocs", issues) }
        validateSecurityRequirements(operation.security, components, "$basePath.security", issues)
        validateServers(operation.servers, "$basePath.servers", issues)
    }

    private fun validateParameter(
        param: EndpointParameter,
        path: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        param.reference?.let {
            validateReferenceObject(it, path, issues)
            validateComponentReference(it.ref, "parameters", components, path, issues)
            return
        }
        if (param.location == ParameterLocation.PATH && !param.isRequired) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Path parameters must be required."
            )
        }
        if (param.location == ParameterLocation.HEADER && !isValidHeaderToken(param.name)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Header parameter name '${param.name}' must be a valid HTTP header token."
            )
        }
        if (param.allowEmptyValue != null && param.location != ParameterLocation.QUERY) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "allowEmptyValue is only valid for query parameters."
            )
        }
        if (param.location == ParameterLocation.HEADER &&
            setOf("accept", "content-type", "authorization").contains(param.name.lowercase())
        ) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "Header parameters named Accept, Content-Type, or Authorization are ignored by the spec."
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
                path = "$path.content",
                message = "Parameter content must contain exactly one media type."
            )
        }
        if (param.content.isNotEmpty() &&
            (param.style != null || param.explode != null || param.allowReserved != null)
        ) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Parameters using content must not define style/explode/allowReserved."
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
        param.example?.let { validateExampleObject(it, "$path.example", issues, components) }
        param.examples.forEach { (name, example) ->
            validateExampleObject(example, "$path.examples.$name", issues, components)
        }
        param.content.forEach { (mediaType, media) ->
            validateMediaTypeObject(media, "$path.content.$mediaType", issues, mediaType, components)
        }
    }

    private fun validateHeader(
        header: Header,
        path: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        header.reference?.let {
            validateReferenceObject(it, path, issues)
            validateComponentReference(it.ref, "headers", components, path, issues)
            return
        }
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
                path = "$path.content",
                message = "Header content must contain exactly one media type."
            )
        }
        if (header.content.size > 1) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Header content must contain exactly one media type."
            )
        }
        if (header.content.isNotEmpty()) {
            if (header.style != null && header.style != ParameterStyle.SIMPLE) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = path,
                    message = "Header style must not be set when content is used."
                )
            }
            if (header.explode == true) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = path,
                    message = "Header explode must not be set when content is used."
                )
            }
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
        header.example?.let { validateExampleObject(it, "$path.example", issues, components) }
        header.examples.forEach { (name, example) ->
            validateExampleObject(example, "$path.examples.$name", issues, components)
        }
        header.content.forEach { (mediaType, media) ->
            validateMediaTypeObject(media, "$path.content.$mediaType", issues, mediaType, components)
        }
    }

    /**
     * Validate Path Item parameter uniqueness and shared rules.
     */
    private fun validatePathItemParameters(
        item: PathItem,
        basePath: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        if (item.parameters.isEmpty()) return
        val duplicates = item.parameters.groupBy { it.name to it.location }
            .filter { it.value.size > 1 }
        duplicates.forEach { (key, values) ->
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = basePath,
                message = "Duplicate parameter '${key.first}' in ${key.second} (count ${values.size})."
            )
        }
        item.parameters.forEachIndexed { index, param ->
            validateParameter(param, "$basePath[$index]", issues, components)
        }
    }

    private fun hasPathItemSiblings(item: PathItem): Boolean {
        return item.summary != null ||
            item.description != null ||
            item.get != null ||
            item.put != null ||
            item.post != null ||
            item.delete != null ||
            item.options != null ||
            item.head != null ||
            item.patch != null ||
            item.trace != null ||
            item.query != null ||
            item.additionalOperations.isNotEmpty() ||
            item.parameters.isNotEmpty() ||
            item.servers.isNotEmpty() ||
            item.extensions.isNotEmpty()
    }

    private fun validateRequestBody(
        body: RequestBody,
        path: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        body.reference?.let {
            validateReferenceObject(it, path, issues)
            validateComponentReference(it.ref, "requestBodies", components, path, issues)
            return
        }
        if (body.content.isEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "RequestBody content should contain at least one media type."
            )
        }
        body.content.forEach { (mediaType, media) ->
            validateMediaTypeObject(media, "$path.content.$mediaType", issues, mediaType, components)
        }
    }

    private fun validateComponents(components: Components?, basePath: String, issues: MutableList<OpenApiIssue>) {
        if (components == null) return

        fun validateKeys(keys: Set<String>, componentPath: String) {
            keys.filterNot { componentKeyRegex.matches(it) }.forEach { key ->
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
            if (looksLikeUriReference(name)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.WARNING,
                    path = "$basePath.securitySchemes.$name",
                    message = "Security scheme component name '$name' looks like a URI; component names take precedence over URI references in Security Requirements."
                )
            }
            validateSecurityScheme(scheme, "$basePath.securitySchemes.$name", issues, components)
        }
        components.examples.forEach { (name, example) ->
            validateExampleObject(example, "$basePath.examples.$name", issues, components)
        }
        components.parameters.forEach { (name, param) ->
            validateParameter(param, "$basePath.parameters.$name", issues, components)
        }
        components.headers.forEach { (name, header) ->
            if (!isValidHeaderToken(name)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$basePath.headers.$name",
                    message = "Header name '$name' must be a valid HTTP header token."
                )
            }
            validateHeader(header, "$basePath.headers.$name", issues, components)
        }
        components.requestBodies.forEach { (name, body) ->
            validateRequestBody(body, "$basePath.requestBodies.$name", issues, components)
        }
        components.responses.forEach { (name, response) ->
            validateResponse(response, "$basePath.responses.$name", issues, components)
        }
        components.mediaTypes.forEach { (name, mediaType) ->
            validateMediaTypeObject(mediaType, "$basePath.mediaTypes.$name", issues, null, components)
        }
    }

    /**
     * Validate Response Objects, including their content and examples.
     */
    private fun validateResponse(
        response: EndpointResponse,
        path: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        response.reference?.let {
            validateReferenceObject(it, path, issues)
            validateComponentReference(it.ref, "responses", components, path, issues)
            return
        }
        if (response.description.isNullOrBlank()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.description",
                message = "Response description is required."
            )
        }
        response.headers.forEach { (headerName, header) ->
            if (!isValidHeaderToken(headerName)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.headers.$headerName",
                    message = "Header name '$headerName' must be a valid HTTP header token."
                )
            }
            validateHeader(header, "$path.headers.$headerName", issues, components)
        }
        response.content.forEach { (mediaType, media) ->
            validateMediaTypeObject(media, "$path.content.$mediaType", issues, mediaType, components)
        }
        response.links?.forEach { (name, link) ->
            if (!componentKeyRegex.matches(name)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.links.$name",
                    message = "Link key '$name' must match ^[a-zA-Z0-9._-]+$."
                )
            }
            validateLink(link, "$path.links.$name", issues, components)
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
        mediaTypeKey: String? = null,
        components: Components?
    ) {
        if (mediaTypeKey != null && !isValidMediaTypeKey(mediaTypeKey)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path",
                message = "Invalid media type key '$mediaTypeKey'. Keys must be a media type or media type range."
            )
        }
        val hasRef = media.ref != null || media.reference?.ref != null
        if (hasRef) {
            media.ref?.let { validateUri(it, "$path.\$ref", issues) }
            media.reference?.let {
                validateReferenceObject(it, path, issues)
                validateComponentReference(it.ref, "mediaTypes", components, path, issues)
            }
            media.ref?.let { validateComponentReference(it, "mediaTypes", components, path, issues) }
            val hasSiblings = media.schema != null ||
                media.itemSchema != null ||
                media.example != null ||
                media.examples.isNotEmpty() ||
                media.encoding.isNotEmpty() ||
                media.prefixEncoding.isNotEmpty() ||
                media.itemEncoding != null ||
                media.extensions.isNotEmpty()
            if (hasSiblings) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.WARNING,
                    path = path,
                    message = "Media type with \$ref should not define other fields (they are ignored)."
                )
            }
            return
        }
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
        validateEncodingByName(media, path, issues)
        media.example?.let { validateExampleObject(it, "$path.example", issues, components) }
        media.examples.forEach { (name, example) ->
            validateExampleObject(example, "$path.examples.$name", issues, components)
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

    /**
     * Validate Encoding Object keys match schema properties for encoding-by-name.
     */
    private fun validateEncodingByName(
        media: domain.MediaTypeObject,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        if (media.encoding.isEmpty()) return
        val schema = media.schema ?: run {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$path.encoding",
                message = "Encoding entries are ignored when no schema is defined."
            )
            return
        }
        if (schema.ref != null || schema.booleanSchema != null) return
        val properties = schema.properties
        if (properties.isEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$path.encoding",
                message = "Encoding entries are ignored when schema has no properties."
            )
            return
        }
        media.encoding.keys.filterNot { properties.containsKey(it) }.forEach { key ->
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$path.encoding.$key",
                message = "Encoding entry '$key' has no matching schema property and will be ignored."
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
        encoding.headers.keys.forEach { headerName ->
            if (!isValidHeaderToken(headerName)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = "$path.headers.$headerName",
                    message = "Header name '$headerName' must be a valid HTTP header token."
                )
            }
            if (headerName.equals("content-type", ignoreCase = true)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.WARNING,
                    path = "$path.headers.$headerName",
                    message = "Encoding headers must not include Content-Type (it is ignored)."
                )
            }
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
    private fun validateExampleObject(
        example: domain.ExampleObject,
        path: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        val hasValue = example.value != null
        val hasDataValue = example.dataValue != null
        val hasSerialized = example.serializedValue != null
        val hasExternal = example.externalValue != null

        example.externalValue?.let { validateUri(it, "$path.externalValue", issues) }
        example.ref?.let { validateUri(it, "$path.\$ref", issues) }
        example.ref?.let { validateComponentReference(it, "examples", components, path, issues) }

        if (example.ref != null && (hasValue || hasDataValue || hasSerialized || hasExternal)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Example with \$ref must not define value/dataValue/serializedValue/externalValue."
            )
        }
        if (example.ref != null && example.extensions.isNotEmpty()) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "Example with \$ref must not define extensions (they are ignored)."
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
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        scheme.reference?.let {
            validateReferenceObject(it, path, issues)
            validateComponentReference(it.ref, "securitySchemes", components, path, issues)
            return
        }
        val validTypes = setOf("apiKey", "http", "mutualTLS", "oauth2", "openIdConnect")
        if (!validTypes.contains(scheme.type)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.type",
                message = "Security scheme type '${scheme.type}' is invalid. Must be one of: ${validTypes.joinToString()}."
            )
        }
        when (scheme.type) {
            "apiKey" -> {
                if (scheme.name.isNullOrBlank() || scheme.`in`.isNullOrBlank()) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = path,
                        message = "apiKey security scheme requires name and in."
                    )
                }
                val location = scheme.`in`
                if (!location.isNullOrBlank() && location !in setOf("query", "header", "cookie")) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = "$path.in",
                        message = "apiKey security scheme 'in' must be one of: query, header, cookie."
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
                scheme.oauth2MetadataUrl?.let {
                    validateUrl(it, "$path.oauth2MetadataUrl", issues, requireHttps = true)
                }
            }
            "openIdConnect" -> {
                if (scheme.openIdConnectUrl.isNullOrBlank()) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = path,
                        message = "openIdConnect security scheme requires openIdConnectUrl."
                    )
                } else {
                    validateUrl(scheme.openIdConnectUrl, "$path.openIdConnectUrl", issues, requireHttps = true)
                }
            }
        }
        if (scheme.type != "oauth2" && scheme.oauth2MetadataUrl != null) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$path.oauth2MetadataUrl",
                message = "oauth2MetadataUrl is only valid for oauth2 security schemes."
            )
        }
        if (scheme.type != "openIdConnect" && scheme.openIdConnectUrl != null) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$path.openIdConnectUrl",
                message = "openIdConnectUrl is only valid for openIdConnect security schemes."
            )
        }
        if (scheme.type != "oauth2" && scheme.flows != null) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$path.flows",
                message = "OAuth flows are only valid for oauth2 security schemes."
            )
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
            flow.authorizationUrl?.let {
                validateUrl(it, "$path.implicit.authorizationUrl", issues, requireHttps = true)
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
            flow.tokenUrl?.let {
                validateUrl(it, "$path.password.tokenUrl", issues, requireHttps = true)
            }
            flow.refreshUrl?.let {
                validateUrl(it, "$path.password.refreshUrl", issues, requireHttps = true)
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
            flow.tokenUrl?.let {
                validateUrl(it, "$path.clientCredentials.tokenUrl", issues, requireHttps = true)
            }
            flow.refreshUrl?.let {
                validateUrl(it, "$path.clientCredentials.refreshUrl", issues, requireHttps = true)
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
            flow.authorizationUrl?.let {
                validateUrl(it, "$path.authorizationCode.authorizationUrl", issues, requireHttps = true)
            }
            flow.tokenUrl?.let {
                validateUrl(it, "$path.authorizationCode.tokenUrl", issues, requireHttps = true)
            }
            flow.refreshUrl?.let {
                validateUrl(it, "$path.authorizationCode.refreshUrl", issues, requireHttps = true)
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
            flow.deviceAuthorizationUrl?.let {
                validateUrl(it, "$path.deviceAuthorization.deviceAuthorizationUrl", issues, requireHttps = true)
            }
            flow.tokenUrl?.let {
                validateUrl(it, "$path.deviceAuthorization.tokenUrl", issues, requireHttps = true)
            }
            flow.refreshUrl?.let {
                validateUrl(it, "$path.deviceAuthorization.refreshUrl", issues, requireHttps = true)
            }
        }
    }

    private fun validateOperationIds(definition: OpenApiDefinition, issues: MutableList<OpenApiIssue>) {
        val operations = collectAllOperations(definition).filter { it.operationIdExplicit }
        val duplicates = operations.groupBy { it.operationId }.filter { it.value.size > 1 }
        duplicates.forEach { (operationId, ops) ->
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$.paths",
                message = "operationId '$operationId' must be unique (found ${ops.size})."
            )
        }
    }

    private fun validateLinkTargets(
        definition: OpenApiDefinition,
        issues: MutableList<OpenApiIssue>,
        selfBase: String?
    ) {
        val operations = collectAllOperations(definition)
        if (operations.isEmpty()) return
        val operationIds = operations.filter { it.operationIdExplicit }.map { it.operationId }.toSet()
        val operationRefs = collectOperationRefs(definition)

        fun validateLinkTarget(link: Link, path: String) {
            if (link.ref != null || link.reference != null) return
            val operationId = link.operationId
            if (!operationId.isNullOrBlank() && !operationIds.contains(operationId)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = path,
                    message = "Link operationId '$operationId' does not match any known operationId."
                )
            }
            val operationRef = link.operationRef
            if (!operationRef.isNullOrBlank()) {
                val localRef = toLocalOperationRef(operationRef, selfBase)
                if (localRef != null && !operationRefs.contains(localRef)) {
                    issues += OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = path,
                        message = "Link operationRef '$operationRef' does not resolve to a known operation."
                    )
                }
            }
        }

        operations.forEach { op ->
            op.responses.forEach { (code, response) ->
                response.links?.forEach { (name, link) ->
                    val basePath = "$.paths.${op.path}.${op.methodName.lowercase()}.responses.$code.links.$name"
                    validateLinkTarget(link, basePath)
                }
            }
        }

        definition.components?.responses?.forEach { (name, response) ->
            response.links?.forEach { (linkName, link) ->
                val basePath = "$.components.responses.$name.links.$linkName"
                validateLinkTarget(link, basePath)
            }
        }

        definition.components?.links?.forEach { (name, link) ->
            val basePath = "$.components.links.$name"
            validateLinkTarget(link, basePath)
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
            tag.externalDocs?.let { validateExternalDocs(it, "$.tags.${tag.name}.externalDocs", issues) }
        }
        detectTagCycles(tagsByName, issues)
    }

    private fun validateExternalDocs(
        docs: domain.ExternalDocumentation,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        validateUri(docs.url, "$path.url", issues)
    }

    private fun validateEmail(value: String, path: String, issues: MutableList<OpenApiIssue>) {
        if (!EMAIL_REGEX.matches(value)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Value must be a valid email address."
            )
        }
    }

    private fun validateReferenceObject(
        reference: ReferenceObject,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        validateUri(reference.ref, "$path.\$ref", issues)
    }

    private fun validateUri(value: String, path: String, issues: MutableList<OpenApiIssue>) {
        validateUriInternal(value, path, issues, requireAbsolute = false, requireHttps = false, label = "URI")
    }

    private fun validateUrl(
        value: String,
        path: String,
        issues: MutableList<OpenApiIssue>,
        requireHttps: Boolean = false
    ) {
        validateUriInternal(value, path, issues, requireAbsolute = true, requireHttps = requireHttps, label = "URL")
    }

    private fun validateUriInternal(
        value: String,
        path: String,
        issues: MutableList<OpenApiIssue>,
        requireAbsolute: Boolean,
        requireHttps: Boolean,
        label: String
    ) {
        val uri = runCatching { java.net.URI(value) }.getOrNull()
        if (uri == null) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Value must be a valid $label."
            )
            return
        }
        if (requireAbsolute && !uri.isAbsolute) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Value must be an absolute $label."
            )
        }
        if (requireHttps && uri.isAbsolute && uri.scheme?.lowercase() != "https") {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "$label should use https scheme."
            )
        }
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
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        val isRef = link.ref != null || link.reference != null
        if (isRef) {
            link.ref?.let { validateUri(it, "$path.\$ref", issues) }
            link.reference?.let {
                validateReferenceObject(it, path, issues)
                validateComponentReference(it.ref, "links", components, path, issues)
            }
            link.ref?.let { validateComponentReference(it, "links", components, path, issues) }
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
        link.operationRef?.let { validateUri(it, "$path.operationRef", issues) }
        validateLinkRuntimeExpressions(link, path, issues)
    }

    private fun validateLinkRuntimeExpressions(
        link: domain.Link,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        link.parameters.forEach { (name, value) ->
            validateRuntimeExpressionValue(value, "$path.parameters.$name", issues)
        }
        link.requestBody?.let { validateRuntimeExpressionValue(it, "$path.requestBody", issues) }
    }

    private fun validateRuntimeExpressionValue(
        value: Any?,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        val text = value as? String ?: return
        val expressions = extractEmbeddedRuntimeExpressions(text)
        if (expressions.isEmpty()) return
        expressions.forEach { expression ->
            if (!isValidRuntimeExpression(expression)) {
                issues += OpenApiIssue(
                    severity = OpenApiIssueSeverity.ERROR,
                    path = path,
                    message = "Runtime expression '$expression' is not valid."
                )
            }
        }
    }

    private fun extractEmbeddedRuntimeExpressions(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("$")) return listOf(trimmed)
        return RUNTIME_EXPRESSION_EMBED_REGEX.findAll(raw).mapNotNull { match ->
            val inner = match.groupValues.getOrNull(1)?.trim().orEmpty()
            inner.takeIf { it.startsWith("$") }
        }.toList()
    }

    private fun validateCallbacks(
        callbacks: Map<String, Callback>,
        path: String,
        issues: MutableList<OpenApiIssue>,
        components: Components?
    ) {
        callbacks.forEach { (name, callback) ->
            when (callback) {
                is Callback.Reference -> {
                    validateReferenceObject(callback.reference, "$path.$name", issues)
                    val ref = callback.reference.ref
                    if (resolveCallbackRef(ref, components) == null) {
                        issues += OpenApiIssue(
                            severity = OpenApiIssueSeverity.WARNING,
                            path = "$path.$name",
                            message = "Callback reference '$ref' could not be resolved."
                        )
                    }
                }
                is Callback.Inline -> {
                    callback.expressions.forEach { (rawExpression, _) ->
                        val expressions = extractEmbeddedRuntimeExpressions(rawExpression)
                        if (expressions.isEmpty()) {
                            issues += OpenApiIssue(
                                severity = OpenApiIssueSeverity.ERROR,
                                path = "$path.$name",
                                message = "Callback expression '$rawExpression' must include a valid runtime expression."
                            )
                            return@forEach
                        }
                        expressions.forEach { expression ->
                            if (!isValidRuntimeExpression(expression)) {
                                issues += OpenApiIssue(
                                    severity = OpenApiIssueSeverity.ERROR,
                                    path = "$path.$name",
                                    message = "Callback expression '$rawExpression' contains invalid runtime expression '$expression'."
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractRuntimeExpression(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            return inner.ifEmpty { null }
        }
        return trimmed
    }

    private fun isValidRuntimeExpression(value: String): Boolean {
        return when (value) {
            "\$url", "\$method", "\$statusCode" -> true
            else -> when {
                value.startsWith("\$request.") -> isValidRuntimeSource(value.removePrefix("\$request."))
                value.startsWith("\$response.") -> isValidRuntimeSource(value.removePrefix("\$response."))
                else -> false
            }
        }
    }

    private fun isValidRuntimeSource(source: String): Boolean {
        return when {
            source.startsWith("header.") -> isValidHeaderToken(source.removePrefix("header."))
            source.startsWith("query.") -> isValidParamName(source.removePrefix("query."))
            source.startsWith("path.") -> isValidParamName(source.removePrefix("path."))
            source.startsWith("body") -> {
                val suffix = source.removePrefix("body")
                if (suffix.isEmpty()) true
                else if (!suffix.startsWith("#")) false
                else isValidJsonPointer(suffix.removePrefix("#"))
            }
            else -> false
        }
    }

    private fun isValidHeaderToken(token: String): Boolean {
        return token.isNotEmpty() && HEADER_TOKEN_REGEX.matches(token)
    }

    private fun isValidParamName(name: String): Boolean {
        return name.isNotEmpty()
    }

    private fun isValidJsonPointer(pointer: String): Boolean {
        if (pointer.isEmpty()) return true
        if (!pointer.startsWith("/")) return false
        val segments = pointer.substring(1).split("/")
        segments.forEach { segment ->
            var i = 0
            while (i < segment.length) {
                val ch = segment[i]
                if (ch == '~') {
                    if (i + 1 >= segment.length) return false
                    val next = segment[i + 1]
                    if (next != '0' && next != '1') return false
                    i += 2
                } else {
                    i += 1
                }
            }
        }
        return true
    }

    private data class ComponentRefInfo(
        val base: String?,
        val name: String
    )

    private fun validateComponentReference(
        ref: String,
        component: String,
        components: Components?,
        path: String,
        issues: MutableList<OpenApiIssue>
    ) {
        val info = extractComponentRefInfo(ref, component) ?: return
        if (!isLocalComponentRef(info.base)) return
        val keys = componentKeys(components, component)
        if (!keys.contains(info.name)) {
            issues += OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Reference '$ref' does not resolve to components.$component."
            )
        }
    }

    private fun componentKeys(components: Components?, component: String): Set<String> {
        return when (component) {
            "schemas" -> components?.schemas?.keys.orEmpty()
            "responses" -> components?.responses?.keys.orEmpty()
            "parameters" -> components?.parameters?.keys.orEmpty()
            "requestBodies" -> components?.requestBodies?.keys.orEmpty()
            "headers" -> components?.headers?.keys.orEmpty()
            "securitySchemes" -> components?.securitySchemes?.keys.orEmpty()
            "examples" -> components?.examples?.keys.orEmpty()
            "links" -> components?.links?.keys.orEmpty()
            "callbacks" -> components?.callbacks?.keys.orEmpty()
            "pathItems" -> components?.pathItems?.keys.orEmpty()
            "mediaTypes" -> components?.mediaTypes?.keys.orEmpty()
            else -> emptySet()
        }
    }

    private fun extractComponentRefInfo(ref: String, component: String): ComponentRefInfo? {
        val marker = "#/components/$component/"
        val index = ref.indexOf(marker)
        if (index < 0) return null
        val base = ref.substring(0, index).ifBlank { null }
        val raw = ref.substring(index + marker.length)
        if (raw.isBlank()) return null
        val first = raw.substringBefore("/")
        val name = decodeJsonPointerSegment(percentDecode(first))
        if (name.isBlank()) return null
        return ComponentRefInfo(base = resolveReferenceBase(base), name = name)
    }

    private fun resolveReferenceBase(base: String?): String? {
        val normalized = normalizeSelfBase(base)
        if (normalized.isNullOrBlank()) return null
        val selfBase = normalizeSelfBase(currentSelfBase)
        if (selfBase.isNullOrBlank()) return normalized
        if (isAbsoluteUri(normalized)) return normalized
        return resolveAgainstBase(selfBase, normalized)
    }

    private fun isLocalComponentRef(refBase: String?): Boolean {
        if (refBase.isNullOrBlank()) return true
        val selfBase = normalizeSelfBase(currentSelfBase) ?: return false
        val normalizedRef = normalizeSelfBase(refBase) ?: refBase
        return normalizedRef == selfBase
    }

    private fun decodeJsonPointerSegment(value: String): String {
        return value.replace("~1", "/").replace("~0", "~")
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

    private fun isValidMediaTypeKey(value: String): Boolean {
        val main = value.trim().substringBefore(";").trim()
        if (main == "*/*") return true
        return MEDIA_TYPE_MAIN_REGEX.matches(main)
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

    private fun collectOperationsWithCallbacks(
        roots: List<EndpointDefinition>,
        components: Components?
    ): List<EndpointDefinition> {
        if (roots.isEmpty()) return emptyList()
        val visited = Collections.newSetFromMap(IdentityHashMap<EndpointDefinition, Boolean>())
        val queue = ArrayDeque<EndpointDefinition>()
        queue.addAll(roots)
        val collected = mutableListOf<EndpointDefinition>()

        while (queue.isNotEmpty()) {
            val op = queue.removeFirst()
            if (!visited.add(op)) continue
            collected.add(op)
            val callbackOps = collectCallbackOperations(op.callbacks, components)
            if (callbackOps.isNotEmpty()) {
                queue.addAll(callbackOps)
            }
        }

        return collected
    }

    private fun collectCallbackOperations(
        callbacks: Map<String, Callback>,
        components: Components?
    ): List<EndpointDefinition> {
        if (callbacks.isEmpty()) return emptyList()
        val operations = mutableListOf<EndpointDefinition>()
        callbacks.values.forEach { callback ->
            val resolved = when (callback) {
                is Callback.Inline -> callback
                is Callback.Reference -> resolveCallbackRef(callback.reference.ref, components) as? Callback.Inline
            }
            resolved?.expressions?.values?.forEach { pathItem ->
                operations.addAll(collectOperations(pathItem))
            }
        }
        return operations
    }

    private fun collectAllOperations(definition: OpenApiDefinition): List<EndpointDefinition> {
        val components = definition.components
        val rootOperations = collectOperations(definition.paths) +
            collectOperations(definition.webhooks) +
            collectOperations(components?.pathItems.orEmpty())
        val componentCallbackOps = collectCallbackOperations(components?.callbacks.orEmpty(), components)
        return collectOperationsWithCallbacks(rootOperations + componentCallbackOps, components)
    }

    private fun collectOperationRefs(definition: OpenApiDefinition): Set<String> {
        val refs = LinkedHashSet<String>()

        fun addPathItemRefs(base: String, key: String, item: PathItem) {
            val encodedKey = encodeJsonPointerSegment(key)
            val prefix = "$base/$encodedKey"
            item.get?.let { refs += "$prefix/get" }
            item.put?.let { refs += "$prefix/put" }
            item.post?.let { refs += "$prefix/post" }
            item.delete?.let { refs += "$prefix/delete" }
            item.options?.let { refs += "$prefix/options" }
            item.head?.let { refs += "$prefix/head" }
            item.patch?.let { refs += "$prefix/patch" }
            item.trace?.let { refs += "$prefix/trace" }
            item.query?.let { refs += "$prefix/query" }
            if (item.additionalOperations.isNotEmpty()) {
                item.additionalOperations.keys.forEach { method ->
                    refs += "$prefix/additionalOperations/$method"
                }
            }
        }

        definition.paths.forEach { (path, item) -> addPathItemRefs("#/paths", path, item) }
        definition.webhooks.forEach { (name, item) -> addPathItemRefs("#/webhooks", name, item) }
        definition.components?.pathItems?.forEach { (name, item) ->
            addPathItemRefs("#/components/pathItems", name, item)
        }

        return refs
    }

    private fun normalizeSelfBase(self: String?): String? {
        val trimmed = self?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return trimmed.substringBefore("#")
    }

    private fun resolveSelfBase(self: String?, baseUri: String?): String? {
        val normalizedSelf = normalizeSelfBase(self)
        if (!normalizedSelf.isNullOrBlank()) {
            val normalizedBase = normalizeSelfBase(baseUri)
            if (normalizedBase.isNullOrBlank() || isAbsoluteUri(normalizedSelf)) {
                return normalizedSelf
            }
            return resolveAgainstBase(normalizedBase, normalizedSelf)
        }
        return normalizeSelfBase(baseUri)
    }

    private fun resolveAgainstBase(base: String, ref: String): String {
        return try {
            URI(base).resolve(ref).toString()
        } catch (_: Exception) {
            ref
        }
    }

    private fun isAbsoluteUri(value: String): Boolean {
        return try {
            URI(value).isAbsolute
        } catch (_: Exception) {
            false
        }
    }

    private fun toLocalOperationRef(operationRef: String, selfBase: String?): String? {
        if (operationRef.startsWith("#/")) return normalizeOperationRef(operationRef)
        val hashIndex = operationRef.indexOf("#/")
        if (hashIndex < 0) return null
        val base = operationRef.substring(0, hashIndex).trimEnd('#')
        val normalizedSelf = selfBase?.trimEnd('#').orEmpty()
        if (normalizedSelf.isBlank() || base != normalizedSelf) return null
        return normalizeOperationRef(operationRef.substring(hashIndex))
    }

    private fun resolveCallbackRef(ref: String, components: Components?): Callback? {
        val key = extractComponentKey(ref, "callbacks") ?: return null
        return components?.callbacks?.get(key)
    }

    private fun extractComponentKey(ref: String, component: String): String? {
        val marker = "#/components/$component/"
        val index = ref.indexOf(marker)
        if (index < 0) return null
        val raw = ref.substring(index + marker.length)
        if (raw.isBlank() || raw.contains("/")) return null
        return raw.replace("~1", "/").replace("~0", "~")
    }

    private fun encodeJsonPointerSegment(value: String): String {
        return value
            .replace("~", "~0")
            .replace("/", "~1")
            .replace("{", "%7B")
            .replace("}", "%7D")
    }

    private fun normalizeOperationRef(ref: String): String {
        return ref
            .replace("{", "%7B")
            .replace("}", "%7D")
            .replace("%7b", "%7B")
            .replace("%7d", "%7D")
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
        private const val OAS_DIALECT_URI = "https://spec.openapis.org/oas/3.1/dialect/base"
        private const val JSON_SCHEMA_2020_12 = "https://json-schema.org/draft/2020-12/schema"
        private const val JSON_SCHEMA_2019_09 = "https://json-schema.org/draft/2019-09/schema"
        private const val JSON_SCHEMA_2020_12_HTTP = "http://json-schema.org/draft/2020-12/schema"
        private const val JSON_SCHEMA_2019_09_HTTP = "http://json-schema.org/draft/2019-09/schema"
        private val KNOWN_JSON_SCHEMA_DIALECTS = setOf(
            JSON_SCHEMA_2020_12,
            JSON_SCHEMA_2019_09,
            JSON_SCHEMA_2020_12_HTTP,
            JSON_SCHEMA_2019_09_HTTP
        )
        val PATH_TEMPLATE_REGEX = "\\{([^}]+)}".toRegex()
        val SERVER_VARIABLE_REGEX = "\\{([^}]+)}".toRegex()
        val RUNTIME_EXPRESSION_EMBED_REGEX = "\\{([^{}]+)}".toRegex()
        val HTTP_METHOD_TOKEN_REGEX = "^[!#\$%&'*+\\-\\.^_`|~0-9A-Za-z]+$".toRegex()
        val HEADER_TOKEN_REGEX = "^[!#\$%&'*+\\-\\.^_`|~0-9A-Za-z]+$".toRegex()
        val EMAIL_REGEX = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$".toRegex()
        val MEDIA_TYPE_MAIN_REGEX =
            "^[A-Za-z0-9!#\$&^_.+\\-]+/(?:\\*|\\*\\+[A-Za-z0-9!#\$&^_.+\\-]+|[A-Za-z0-9!#\$&^_.+\\-]+(?:\\+[A-Za-z0-9!#\$&^_.+\\-]+)?)$"
                .toRegex()
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
