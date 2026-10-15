/**
 * Validator rule for OpenAPI [EndpointResponse], [RequestBody], [MediaTypeObject],
 * [EncodingObject], and [ExampleObject] definitions.
 */
package openapi.validator

import domain.Components
import domain.EncodingObject
import domain.EndpointResponse
import domain.ExampleObject
import domain.MediaTypeObject
import domain.OpenApiDefinition
import domain.RequestBody
import openapi.OpenApiIssue
import openapi.OpenApiIssueSeverity

/**
 * Validates responses, request bodies, media type objects, encoding objects, and example objects.
 */
object ResponseAndContentValidator : OpenApiValidationRule {
  override fun validate(
      definition: OpenApiDefinition,
      issues: MutableList<OpenApiIssue>,
      currentSelfBase: String?
  ) {
    val components = definition.components ?: return
    components.responses.forEach { (name, response) ->
      validateResponse(
          response, "$.components.responses.$name", issues, components, currentSelfBase)
    }
    components.requestBodies.forEach { (name, body) ->
      validateRequestBody(
          body, "$.components.requestBodies.$name", issues, components, currentSelfBase)
    }
    components.mediaTypes.forEach { (name, mediaType) ->
      validateMediaTypeObject(
          mediaType, "$.components.mediaTypes.$name", issues, null, components, currentSelfBase)
    }
    components.examples.forEach { (name, example) ->
      validateExampleObject(
          example, "$.components.examples.$name", issues, components, currentSelfBase)
    }
  }

  /**
   * Validates a [RequestBody] object.
   *
   * @param body RequestBody to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   * @param components Components registry.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validateRequestBody(
      body: RequestBody,
      path: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?,
      currentSelfBase: String?
  ) {
    body.reference?.let {
      ValidationCommon.validateReferenceObject(it, path, issues)
      ReferenceAndLinkValidator.validateComponentReference(
          it.ref, "requestBodies", components, path, issues, currentSelfBase)
      return
    }
    if (body.content.isEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = path,
              message = "RequestBody content should contain at least one media type.")
    }
    body.content.forEach { (mediaType, media) ->
      validateMediaTypeObject(
          media, "$path.content.$mediaType", issues, mediaType, components, currentSelfBase)
    }
  }

  /**
   * Validates an [EndpointResponse] object.
   *
   * @param response Response to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   * @param components Components registry.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validateResponse(
      response: EndpointResponse,
      path: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?,
      currentSelfBase: String?
  ) {
    response.reference?.let {
      ValidationCommon.validateReferenceObject(it, path, issues)
      ReferenceAndLinkValidator.validateComponentReference(
          it.ref, "responses", components, path, issues, currentSelfBase)
      return
    }
    if (response.description.isNullOrBlank()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$path.description",
              message = "Response description is required.")
    }
    response.headers.forEach { (headerName, header) ->
      if (!ValidationCommon.isValidHeaderToken(headerName)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.headers.$headerName",
                message = "Header name '$headerName' must be a valid HTTP header token.")
      }
      ParameterAndHeaderValidator.validateHeader(
          header, "$path.headers.$headerName", issues, components, currentSelfBase)
    }
    response.content.forEach { (mediaType, media) ->
      validateMediaTypeObject(
          media, "$path.content.$mediaType", issues, mediaType, components, currentSelfBase)
    }
    response.links?.forEach { (name, link) ->
      if (!ValidationCommon.COMPONENT_KEY_REGEX.matches(name)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.links.$name",
                message = "Link key '$name' must match ^[a-zA-Z0-9._-]+$.")
      }
      ReferenceAndLinkValidator.validateLink(
          link, "$path.links.$name", issues, components, currentSelfBase)
    }
  }

  /**
   * Checks whether [code] is a valid OpenAPI status code (`default`, `100`-`599`, or `1XX`-`5XX`).
   *
   * @param code Status code string.
   * @return `true` if valid.
   */
  fun isValidResponseCode(code: String): Boolean {
    if (code == "default") return true
    if (code.matches(Regex("^[1-5][0-9]{2}$"))) return true
    if (code.matches(Regex("^[1-5]XX$"))) return true
    return false
  }

  /**
   * Validates a [MediaTypeObject] including encoding constraints and examples.
   *
   * @param media MediaTypeObject to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   * @param mediaTypeKey Optional media type key string.
   * @param components Components registry.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validateMediaTypeObject(
      media: MediaTypeObject,
      path: String,
      issues: MutableList<OpenApiIssue>,
      mediaTypeKey: String?,
      components: Components?,
      currentSelfBase: String?
  ) {
    if (mediaTypeKey != null && !ValidationCommon.isValidMediaTypeKey(mediaTypeKey)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message =
                  "Invalid media type key '$mediaTypeKey'. Keys must be a media type or media type range.")
    }
    val refTarget = media.ref ?: media.reference?.ref
    if (refTarget != null) {
      if (media.ref != null) {
        ValidationCommon.validateUri(media.ref, "$path.\$ref", issues)
      }
      if (media.reference != null) {
        ValidationCommon.validateReferenceObject(media.reference, path, issues)
      }
      ReferenceAndLinkValidator.validateComponentReference(
          refTarget, "mediaTypes", components, path, issues, currentSelfBase)
      val siblingObjects =
          listOfNotNull(media.schema, media.itemSchema, media.example, media.itemEncoding) +
              media.examples.keys +
              media.encoding.keys +
              media.prefixEncoding +
              media.extensions.keys
      if (siblingObjects.isNotEmpty()) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message =
                    "Media type with \$ref should not define other fields (they are ignored).")
      }
      return
    }
    if ((media.example != null) and media.examples.isNotEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Media type must not define both example and examples.")
    }
    val hasPositionalEncoding = media.prefixEncoding.isNotEmpty() or (media.itemEncoding != null)
    if (media.encoding.isNotEmpty() && hasPositionalEncoding) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Media type must not define encoding with prefixEncoding/itemEncoding.")
    }
    if (mediaTypeKey != null && media.itemSchema != null && !isSequentialMediaType(mediaTypeKey)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$path.itemSchema",
              message = "itemSchema is only valid for sequential or multipart media types.")
    }
    if (hasPositionalEncoding && !hasArraySchemaOrItemSchema(media)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "prefixEncoding/itemEncoding requires an array schema or itemSchema.")
    }
    validateEncodingApplicability(media, mediaTypeKey, path, issues)
    validateEncodingByName(media, path, issues)
    media.example?.let {
      validateExampleObject(it, "$path.example", issues, components, currentSelfBase)
    }
    media.examples.forEach { (name, example) ->
      validateExampleObject(example, "$path.examples.$name", issues, components, currentSelfBase)
    }
    media.encoding.forEach { (name, encoding) ->
      validateEncodingObject(encoding, "$path.encoding.$name", issues)
    }
    media.prefixEncoding.forEachIndexed { index, encoding ->
      validateEncodingObject(encoding, "$path.prefixEncoding[$index]", issues)
    }
    if (media.itemEncoding != null) {
      validateEncodingObject(media.itemEncoding, "$path.itemEncoding", issues)
    }
  }

  private fun validateEncodingApplicability(
      media: MediaTypeObject,
      mediaTypeKey: String?,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    if (mediaTypeKey.isNullOrBlank()) return
    val baseType = mediaTypeKey.substringBefore(";").trim().lowercase()
    val isMultipart = baseType.startsWith("multipart/")
    val isFormUrlEncoded = baseType == "application/x-www-form-urlencoded"
    val hasPositional = media.prefixEncoding.isNotEmpty() or (media.itemEncoding != null)
    val hasEncoding = media.encoding.isNotEmpty() or hasPositional
    if (hasEncoding && !(isMultipart || isFormUrlEncoded)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = path,
              message =
                  "Encoding is only applicable to multipart or application/x-www-form-urlencoded media types.")
    }
    if (!isMultipart && hasPositional) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = path,
              message = "prefixEncoding/itemEncoding apply only to multipart media types.")
    }
  }

  private fun validateEncodingByName(
      media: MediaTypeObject,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    if (media.encoding.isEmpty()) return
    val schema =
        media.schema
            ?: run {
              issues +=
                  OpenApiIssue(
                      severity = OpenApiIssueSeverity.WARNING,
                      path = "$path.encoding",
                      message = "Encoding entries are ignored when no schema is defined.")
              return
            }
    if ((schema.ref != null) or (schema.booleanSchema != null)) return
    val properties = schema.properties
    if (properties.isEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = "$path.encoding",
              message = "Encoding entries are ignored when schema has no properties.")
      return
    }
    media.encoding.keys
        .filterNot { properties.containsKey(it) }
        .forEach { key ->
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.WARNING,
                  path = "$path.encoding.$key",
                  message =
                      "Encoding entry '$key' has no matching schema property and will be ignored.")
        }
  }

  private fun isSequentialMediaType(mediaTypeKey: String): Boolean {
    val baseType = mediaTypeKey.substringBefore(";").trim().lowercase()
    val exactSequential =
        setOf(
            "text/event-stream", "application/jsonl", "application/x-ndjson", "application/ndjson")
    return baseType.startsWith("multipart/") or
        baseType.endsWith("json-seq") or
        (baseType in exactSequential)
  }

  private fun hasArraySchemaOrItemSchema(media: MediaTypeObject): Boolean {
    val schema = media.schema
    val hasArrayItems =
        listOfNotNull(media.itemSchema, schema?.items).isNotEmpty() or
            schema?.prefixItems.orEmpty().isNotEmpty()
    val isArrayType = "array" in schema?.types.orEmpty()
    return hasArrayItems or isArrayType
  }

  /**
   * Validates an [EncodingObject].
   *
   * @param encoding EncodingObject to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   */
  fun validateEncodingObject(
      encoding: EncodingObject,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    val hasPositional = encoding.prefixEncoding.isNotEmpty() or (encoding.itemEncoding != null)
    if (encoding.encoding.isNotEmpty() && hasPositional) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message =
                  "Encoding object must not define encoding with prefixEncoding/itemEncoding.")
    }
    encoding.headers.keys.forEach { headerName ->
      if (!ValidationCommon.isValidHeaderToken(headerName)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.headers.$headerName",
                message = "Header name '$headerName' must be a valid HTTP header token.")
      }
      if (headerName.equals("content-type", ignoreCase = true)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$path.headers.$headerName",
                message = "Encoding headers must not include Content-Type (it is ignored).")
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
   * Validates an [ExampleObject] for mutual exclusivity of value fields and `$ref`.
   *
   * @param example ExampleObject to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   * @param components Components registry.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validateExampleObject(
      example: ExampleObject,
      path: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?,
      currentSelfBase: String?
  ) {
    val hasValue = example.value != null
    val hasDataValue = example.dataValue != null
    val hasSerialized = example.serializedValue != null
    val hasExternal = example.externalValue != null

    example.externalValue?.let { ValidationCommon.validateUri(it, "$path.externalValue", issues) }
    example.ref?.let { ValidationCommon.validateUri(it, "$path.\$ref", issues) }
    example.ref?.let {
      ReferenceAndLinkValidator.validateComponentReference(
          it, "examples", components, path, issues, currentSelfBase)
    }

    val hasAnyPayload = hasValue or hasDataValue or hasSerialized or hasExternal
    if ((example.ref != null) and hasAnyPayload) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message =
                  "Example with \$ref must not define value/dataValue/serializedValue/externalValue.")
    }
    if (example.ref != null && example.extensions.isNotEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = path,
              message = "Example with \$ref must not define extensions (they are ignored).")
    }
    if (hasDataValue and hasValue) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Example must not define both dataValue and value.")
    }
    if (hasSerialized and (hasValue or hasExternal)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Example must not define serializedValue with value or externalValue.")
    }
    if (hasExternal and hasValue) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Example must not define externalValue with value.")
    }
  }
}
