/**
 * Validator rule for JSON Schema Draft 2020-12 and OpenAPI 3.2 [SchemaDefinition] and
 * [SchemaProperty] trees.
 */
package openapi.validator

import domain.Callback
import domain.Discriminator
import domain.DynamicAnchorContext
import domain.DynamicAnchorScope
import domain.EndpointParameter
import domain.EndpointResponse
import domain.Header
import domain.MediaTypeObject
import domain.OpenApiDefinition
import domain.PathItem
import domain.RequestBody
import domain.SchemaDefinition
import domain.SchemaProperty
import domain.Xml
import domain.buildDynamicAnchorContext
import domain.extractDynamicAnchorName
import openapi.OpenApiIssue
import openapi.OpenApiIssueSeverity
import openapi.decodeJsonPointerToken
import openapi.percentDecode

/**
 * Validates all schemas across components, parameters, headers, request bodies, responses, media
 * types, callbacks, paths, and webhooks.
 */
object SchemaValidator : OpenApiValidationRule {
  private const val OAS_DIALECT_URI = "https://spec.openapis.org/oas/3.1/dialect/base"
  private const val JSON_SCHEMA_2020_12 = "https://json-schema.org/draft/2020-12/schema"
  private const val JSON_SCHEMA_2019_09 = "https://json-schema.org/draft/2019-09/schema"
  private const val JSON_SCHEMA_2020_12_HTTP = "http://json-schema.org/draft/2020-12/schema"
  private const val JSON_SCHEMA_2019_09_HTTP = "http://json-schema.org/draft/2019-09/schema"
  private val KNOWN_JSON_SCHEMA_DIALECTS =
      setOf(
          JSON_SCHEMA_2020_12,
          JSON_SCHEMA_2019_09,
          JSON_SCHEMA_2020_12_HTTP,
          JSON_SCHEMA_2019_09_HTTP)

  private data class SchemaValidationState(
      val definitions: MutableSet<SchemaDefinition> = mutableSetOf(),
      val properties: MutableSet<SchemaProperty> = mutableSetOf(),
      val defaultDialect: String,
      val componentSchemaKeys: Set<String>,
      val currentSelfBase: String?
  )

  override fun validate(
      definition: OpenApiDefinition,
      issues: MutableList<OpenApiIssue>,
      currentSelfBase: String?
  ) {
    val defaultDialect = normalizeDialect(definition.jsonSchemaDialect) ?: OAS_DIALECT_URI
    definition.jsonSchemaDialect?.let { rawDialect ->
      if (!isKnownDialect(defaultDialect)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$.jsonSchemaDialect",
                message = "jsonSchemaDialect '$rawDialect' is not a recognized schema dialect.")
      }
    }
    val components = definition.components
    val componentSchemaKeys = if (components != null) components.schemas.keys else emptySet()
    val state =
        SchemaValidationState(
            defaultDialect = defaultDialect,
            componentSchemaKeys = componentSchemaKeys,
            currentSelfBase = currentSelfBase)

    if (components != null) {
      components.schemas.forEach { (name, schema) ->
        validateSchemaDefinition(schema, "$.components.schemas.$name", issues, state)
      }
      components.parameters.forEach { (name, param) ->
        validateSchemaInParameter(param, "$.components.parameters.$name", issues, state)
      }
      components.headers.forEach { (name, header) ->
        validateSchemaInHeader(header, "$.components.headers.$name", issues, state)
      }
      components.requestBodies.forEach { (name, body) ->
        validateSchemaInRequestBody(body, "$.components.requestBodies.$name", issues, state)
      }
      components.responses.forEach { (name, response) ->
        validateSchemaInResponse(response, "$.components.responses.$name", issues, state)
      }
      components.mediaTypes.forEach { (name, mediaType) ->
        validateSchemaInMediaType(mediaType, "$.components.mediaTypes.$name", issues, state)
      }
      components.callbacks.forEach { (name, callback) ->
        validateSchemaInCallbacks(mapOf(name to callback), "$.components.callbacks", issues, state)
      }
      components.pathItems.forEach { (name, item) ->
        validateSchemaInPathItem(item, "$.components.pathItems.$name", issues, state)
      }
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
    val operations = ValidationCommon.collectOperations(item)
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
      media: MediaTypeObject,
      path: String,
      issues: MutableList<OpenApiIssue>,
      state: SchemaValidationState
  ) {
    if ((media.ref != null) or (media.reference != null)) return
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
    validateSchemaDefinition(
        schema, path, issues, state, dynamicContext, state.defaultDialect, emptySet())
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
    val defsInScope = mergeDefsScope(defsScope, schema.defs.keys)
    val effectiveDialect = resolveDialect(schema.schemaDialect, currentDialect)
    if (schema.schemaDialect != null && !isKnownDialect(effectiveDialect)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = "$path.\$schema",
              message = "Schema dialect '$effectiveDialect' is not a recognized schema dialect.")
    }
    schema.ref?.let {
      ValidationCommon.validateUri(it, "$path.\$ref", issues)
      validateSchemaRefTargets(it, "$path.\$ref", issues, state, defsInScope)
    }
    schema.dynamicRef?.let { ref ->
      ValidationCommon.validateUri(ref, "$path.\$dynamicRef", issues)
      validateDynamicRefResolution(
          dynamicContext.root, ref, "$path.\$dynamicRef", issues, dynamicContext.scope)
    }
    schema.schemaId?.let { ValidationCommon.validateUri(it, "$path.\$id", issues) }
    schema.schemaDialect?.let { ValidationCommon.validateUri(it, "$path.\$schema", issues) }
    validateDialectWarnings(schema, path, issues, effectiveDialect)
    validateDiscriminator(
        schema.discriminator,
        schema.required,
        schema.properties,
        schemaHasComposition(
            schema.oneOf,
            schema.anyOf,
            schema.allOf,
            schema.oneOfSchemas,
            schema.anyOfSchemas,
            schema.allOfSchemas),
        path,
        issues)
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
        issues = issues)

    schema.defs.forEach { (name, sub) ->
      validateSchemaPropertyInternal(
          sub,
          "$path.\$defs.$name",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.items?.let {
      validateSchemaPropertyInternal(
          it, "$path.items", issues, state, dynamicContext.scope, effectiveDialect, defsInScope)
    }
    schema.prefixItems.forEachIndexed { index, item ->
      validateSchemaPropertyInternal(
          item,
          "$path.prefixItems[$index]",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.contains?.let {
      validateSchemaPropertyInternal(
          it, "$path.contains", issues, state, dynamicContext.scope, effectiveDialect, defsInScope)
    }
    schema.properties.forEach { (name, prop) ->
      validateSchemaPropertyInternal(
          prop,
          "$path.properties.$name",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.patternProperties.forEach { (name, prop) ->
      validateSchemaPropertyInternal(
          prop,
          "$path.patternProperties.$name",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.propertyNames?.let {
      validateSchemaPropertyInternal(
          it,
          "$path.propertyNames",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.additionalProperties?.let {
      validateSchemaPropertyInternal(
          it,
          "$path.additionalProperties",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.dependentSchemas.forEach { (name, prop) ->
      validateSchemaPropertyInternal(
          prop,
          "$path.dependentSchemas.$name",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.unevaluatedProperties?.let {
      validateSchemaPropertyInternal(
          it,
          "$path.unevaluatedProperties",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.unevaluatedItems?.let {
      validateSchemaPropertyInternal(
          it,
          "$path.unevaluatedItems",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.contentSchema?.let {
      validateSchemaPropertyInternal(
          it,
          "$path.contentSchema",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.oneOfSchemas.forEachIndexed { index, prop ->
      validateSchemaPropertyInternal(
          prop,
          "$path.oneOf[$index]",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.anyOfSchemas.forEachIndexed { index, prop ->
      validateSchemaPropertyInternal(
          prop,
          "$path.anyOf[$index]",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.allOfSchemas.forEachIndexed { index, prop ->
      validateSchemaPropertyInternal(
          prop,
          "$path.allOf[$index]",
          issues,
          state,
          dynamicContext.scope,
          effectiveDialect,
          defsInScope)
    }
    schema.not?.let {
      validateSchemaPropertyInternal(
          it, "$path.not", issues, state, dynamicContext.scope, effectiveDialect, defsInScope)
    }
    schema.ifSchema?.let {
      validateSchemaPropertyInternal(
          it, "$path.if", issues, state, dynamicContext.scope, effectiveDialect, defsInScope)
    }
    schema.thenSchema?.let {
      validateSchemaPropertyInternal(
          it, "$path.then", issues, state, dynamicContext.scope, effectiveDialect, defsInScope)
    }
    schema.elseSchema?.let {
      validateSchemaPropertyInternal(
          it, "$path.else", issues, state, dynamicContext.scope, effectiveDialect, defsInScope)
    }
  }

  private fun validateSchemaProperty(
      schema: SchemaProperty,
      path: String,
      issues: MutableList<OpenApiIssue>,
      state: SchemaValidationState
  ) {
    val dynamicContext = buildDynamicAnchorContext(schema)
    validateSchemaPropertyInternal(
        schema, path, issues, state, dynamicContext.scope, state.defaultDialect, emptySet())
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
    if ((schema.schemaDialect != null) and !isKnownDialect(effectiveDialect)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = "$path.\$schema",
              message = "Schema dialect '$effectiveDialect' is not a recognized schema dialect.")
    }
    schema.ref?.let {
      ValidationCommon.validateUri(it, "$path.\$ref", issues)
      validateSchemaRefTargets(it, "$path.\$ref", issues, state, defsInScope)
    }
    schema.dynamicRef?.let { ref ->
      ValidationCommon.validateUri(ref, "$path.\$dynamicRef", issues)
      validateDynamicRefResolution(schema, ref, "$path.\$dynamicRef", issues, dynamicScope)
    }
    schema.schemaId?.let { ValidationCommon.validateUri(it, "$path.\$id", issues) }
    schema.schemaDialect?.let { ValidationCommon.validateUri(it, "$path.\$schema", issues) }
    validateDialectWarnings(schema, path, issues, effectiveDialect)
    val hasComposition = (schema.oneOf + schema.anyOf + schema.allOf).isNotEmpty()
    validateDiscriminator(
        schema.discriminator, schema.required, schema.properties, hasComposition, path, issues)
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
        issues = issues)

    schema.defs.forEach { (name, sub) ->
      validateSchemaPropertyInternal(
          sub, "$path.\$defs.$name", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
    schema.items?.let {
      validateSchemaPropertyInternal(
          it, "$path.items", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
    schema.prefixItems.forEachIndexed { index, item ->
      validateSchemaPropertyInternal(
          item,
          "$path.prefixItems[$index]",
          issues,
          state,
          dynamicScope,
          effectiveDialect,
          defsInScope)
    }
    schema.contains?.let {
      validateSchemaPropertyInternal(
          it, "$path.contains", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
    schema.properties.forEach { (name, prop) ->
      validateSchemaPropertyInternal(
          prop,
          "$path.properties.$name",
          issues,
          state,
          dynamicScope,
          effectiveDialect,
          defsInScope)
    }
    schema.patternProperties.forEach { (name, prop) ->
      validateSchemaPropertyInternal(
          prop,
          "$path.patternProperties.$name",
          issues,
          state,
          dynamicScope,
          effectiveDialect,
          defsInScope)
    }
    schema.propertyNames?.let {
      validateSchemaPropertyInternal(
          it, "$path.propertyNames", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
    schema.additionalProperties?.let {
      validateSchemaPropertyInternal(
          it,
          "$path.additionalProperties",
          issues,
          state,
          dynamicScope,
          effectiveDialect,
          defsInScope)
    }
    schema.dependentSchemas.forEach { (name, prop) ->
      validateSchemaPropertyInternal(
          prop,
          "$path.dependentSchemas.$name",
          issues,
          state,
          dynamicScope,
          effectiveDialect,
          defsInScope)
    }
    schema.unevaluatedProperties?.let {
      validateSchemaPropertyInternal(
          it,
          "$path.unevaluatedProperties",
          issues,
          state,
          dynamicScope,
          effectiveDialect,
          defsInScope)
    }
    schema.unevaluatedItems?.let {
      validateSchemaPropertyInternal(
          it, "$path.unevaluatedItems", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
    schema.contentSchema?.let {
      validateSchemaPropertyInternal(
          it, "$path.contentSchema", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
    schema.oneOf.forEachIndexed { index, prop ->
      validateSchemaPropertyInternal(
          prop, "$path.oneOf[$index]", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
    schema.anyOf.forEachIndexed { index, prop ->
      validateSchemaPropertyInternal(
          prop, "$path.anyOf[$index]", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
    schema.allOf.forEachIndexed { index, prop ->
      validateSchemaPropertyInternal(
          prop, "$path.allOf[$index]", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
    schema.not?.let {
      validateSchemaPropertyInternal(
          it, "$path.not", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
    schema.ifSchema?.let {
      validateSchemaPropertyInternal(
          it, "$path.if", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
    schema.thenSchema?.let {
      validateSchemaPropertyInternal(
          it, "$path.then", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
    schema.elseSchema?.let {
      validateSchemaPropertyInternal(
          it, "$path.else", issues, state, dynamicScope, effectiveDialect, defsInScope)
    }
  }

  private fun checkNonNegative(
      value: Int?,
      keyword: String,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    if (value != null && value < 0) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$path.$keyword",
              message = "$keyword must be greater than or equal to 0.")
    }
  }

  private fun checkMinMaxOrder(
      min: Int?,
      max: Int?,
      label: String,
      minKey: String,
      maxKey: String,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    if (min != null && max != null && min > max) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$path.$minKey",
              message = "$minKey must be less than or equal to $maxKey for $label.")
    }
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

    checkNonNegative(minLength, "minLength", path, issues)
    checkNonNegative(maxLength, "maxLength", path, issues)
    checkNonNegative(minItems, "minItems", path, issues)
    checkNonNegative(maxItems, "maxItems", path, issues)
    checkNonNegative(minProperties, "minProperties", path, issues)
    checkNonNegative(maxProperties, "maxProperties", path, issues)
    checkNonNegative(minContains, "minContains", path, issues)
    checkNonNegative(maxContains, "maxContains", path, issues)

    checkMinMaxOrder(minLength, maxLength, "string length", "minLength", "maxLength", path, issues)
    checkMinMaxOrder(minItems, maxItems, "array size", "minItems", "maxItems", path, issues)
    checkMinMaxOrder(
        minProperties, maxProperties, "object size", "minProperties", "maxProperties", path, issues)
    checkMinMaxOrder(
        minContains, maxContains, "contains bounds", "minContains", "maxContains", path, issues)

    if (listOfNotNull(minContains, maxContains).isNotEmpty() && contains == null) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = "$path.contains",
              message = "minContains/maxContains are ignored without a contains schema.")
    }

    if (contentMediaType != null && !ValidationCommon.isValidMediaTypeKey(contentMediaType)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$path.contentMediaType",
              message = "contentMediaType must be a valid media type.")
    }

    if (contentEncoding != null) {
      if (contentEncoding.isBlank()) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.contentEncoding",
                message = "contentEncoding must not be blank.")
      }
      if (types.isNotEmpty() and !types.contains("string")) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$path.contentEncoding",
                message = "contentEncoding is only applicable to string schemas.")
      }
    }
  }

  private fun mergeDefsScope(current: Set<String>, defs: Set<String>): Set<String> {
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
    val componentRef =
        ReferenceAndLinkValidator.extractComponentRefInfo(ref, "schemas", state.currentSelfBase)
    val isLocalComponent =
        componentRef != null &&
            ReferenceAndLinkValidator.isLocalComponentRef(componentRef.base, state.currentSelfBase)
    if (isLocalComponent && !state.componentSchemaKeys.contains(componentRef!!.name)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Schema \$ref '$ref' does not resolve to components.schemas.")
    }
    val defsRef = extractDefsRefName(ref)
    if ((defsRef != null) and defsScope.isNotEmpty() and !defsScope.contains(defsRef)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Schema \$ref '$ref' does not resolve to a \$defs entry in scope.")
    }
  }

  private fun extractDefsRefName(ref: String): String? {
    if (!ref.startsWith("#/\$defs/")) return null
    val raw = ref.removePrefix("#/\$defs/")
    val first = raw.substringBefore("/")
    val decoded = decodeJsonPointerToken(percentDecode(first))
    return decoded.ifBlank { null }
  }

  private fun normalizeDialect(value: String?): String? {
    val trimmed = value.orEmpty().trim().trimEnd('#')
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
    return KNOWN_JSON_SCHEMA_DIALECTS.contains(normalizeDialect(dialect))
  }

  private fun validateDialectWarnings(
      schema: SchemaDefinition,
      path: String,
      issues: MutableList<OpenApiIssue>,
      dialect: String
  ) {
    val oasKeywords =
        collectOasKeywords(
            schema.discriminator,
            schema.xml,
            schema.externalDocs,
            schema.example,
            schema.examples?.keys)
    warnDialectUsage(
        dialect = dialect,
        path = path,
        issues = issues,
        oasKeywords = oasKeywords,
        hasCustomKeywords = schema.customKeywords.isNotEmpty(),
        hasExtensions = schema.extensions.isNotEmpty())
  }

  private fun validateDialectWarnings(
      schema: SchemaProperty,
      path: String,
      issues: MutableList<OpenApiIssue>,
      dialect: String
  ) {
    val oasKeywords =
        collectOasKeywords(
            schema.discriminator, schema.xml, schema.externalDocs, schema.example, schema.examples)
    warnDialectUsage(
        dialect = dialect,
        path = path,
        issues = issues,
        oasKeywords = oasKeywords,
        hasCustomKeywords = schema.customKeywords.isNotEmpty(),
        hasExtensions = schema.extensions.isNotEmpty())
  }

  private fun collectOasKeywords(
      discriminator: Discriminator?,
      xml: Xml?,
      externalDocs: domain.ExternalDocumentation?,
      example: domain.OpenApiValue?,
      examples: Collection<*>?
  ): List<String> {
    val oasKeywords = mutableListOf<String>()
    if (discriminator != null) oasKeywords += "discriminator"
    if (xml != null) oasKeywords += "xml"
    if (externalDocs != null) oasKeywords += "externalDocs"
    if (example != null) oasKeywords += "example"
    if (!examples.isNullOrEmpty()) oasKeywords += "examples"
    return oasKeywords
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
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = path,
              message =
                  "Schema dialect '$dialect' does not include the OpenAPI vocabulary; keywords " +
                      "${oasKeywords.sorted().joinToString(", ")} may be ignored.")
    }

    val sources = buildList {
      if (hasCustomKeywords) add("custom keywords")
      if (hasExtensions) add("extensions")
    }
    if (sources.isNotEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = path,
              message =
                  "Schema dialect '$dialect' does not allow ${sources.joinToString(" and ")}; " +
                      "unknown keywords may be ignored.")
    }
  }

  private fun validateDynamicRefResolution(
      schema: SchemaProperty,
      ref: String,
      path: String,
      issues: MutableList<OpenApiIssue>,
      dynamicScope: DynamicAnchorScope
  ) {
    extractDynamicAnchorName(ref) ?: return
    val resolved = dynamicScope.resolveDynamicRef(schema, ref)
    if (resolved == null) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = path,
              message = "dynamicRef '$ref' does not resolve to a dynamicAnchor in scope.")
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
    return (oneOfRefs + anyOfRefs + allOfRefs).isNotEmpty() or
        (oneOfSchemas + anyOfSchemas + allOfSchemas).isNotEmpty()
  }

  private fun validateDiscriminator(
      discriminator: Discriminator?,
      required: List<String>,
      properties: Map<String, SchemaProperty>,
      hasComposition: Boolean,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    val disc = discriminator ?: return
    if (!hasComposition) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$path.discriminator",
              message = "Discriminator requires oneOf, anyOf, or allOf on the same schema.")
    }
    if (properties.isNotEmpty() && !properties.containsKey(disc.propertyName)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = "$path.discriminator.propertyName",
              message =
                  "Discriminator property '${disc.propertyName}' is not defined in schema properties.")
    }
    if (!required.contains(disc.propertyName) && disc.defaultMapping.isNullOrBlank()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$path.discriminator.defaultMapping",
              message =
                  "Discriminator defaultMapping is required when the property is not required.")
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
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.xml.attribute",
                message = "xml.attribute must not be set when xml.nodeType is present.")
      }
      if (xmlObj.wrapped) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.xml.wrapped",
                message = "xml.wrapped must not be set when xml.nodeType is present.")
      }
    }

    if (xmlObj.wrapped and types.isNotEmpty() and !isArray) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$path.xml.wrapped",
              message = "xml.wrapped is only valid for array schemas.")
    }

    if ((nodeType == "attribute") and types.isNotEmpty() and isArray) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$path.xml.nodeType",
              message = "xml.nodeType 'attribute' is not valid for array schemas.")
    }

    if (nodeType in setOf("text", "cdata", "none") && !xmlObj.name.isNullOrBlank()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = "$path.xml.name",
              message = "xml.name is ignored for nodeType '$nodeType'.")
    }
  }
}
