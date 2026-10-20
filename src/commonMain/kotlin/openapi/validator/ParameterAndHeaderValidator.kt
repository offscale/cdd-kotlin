/** Validator rule for OpenAPI [EndpointParameter] and [Header] definitions. */
package openapi.validator

import domain.Components
import domain.EndpointParameter
import domain.Header
import domain.OpenApiDefinition
import domain.ParameterLocation
import domain.ParameterStyle
import domain.PathItem
import openapi.OpenApiIssue
import openapi.OpenApiIssueSeverity

/**
 * Validates parameter locations, styles, explode rules, schema/content mutual exclusivity, and
 * header constraints.
 */
object ParameterAndHeaderValidator : OpenApiValidationRule {
  override fun validate(
      definition: OpenApiDefinition,
      issues: MutableList<OpenApiIssue>,
      currentSelfBase: String?
  ) {
    val components = definition.components ?: return
    components.parameters.forEach { (name, param) ->
      validateParameter(param, "$.components.parameters.$name", issues, components, currentSelfBase)
    }
    components.headers.forEach { (name, header) ->
      validateHeader(header, "$.components.headers.$name", issues, components, currentSelfBase)
    }
  }

  /**
   * Validates PathItem-level parameter uniqueness and individual parameter constraints.
   *
   * @param item PathItem containing parameters.
   * @param basePath JSONPath location.
   * @param issues Issue accumulator.
   * @param components Components registry.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validatePathItemParameters(
      item: PathItem,
      basePath: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?,
      currentSelfBase: String?
  ) {
    if (item.parameters.isEmpty()) return
    val duplicates = item.parameters.groupBy { it.name to it.location }.filter { it.value.size > 1 }
    duplicates.forEach { (key, values) ->
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = basePath,
              message =
                  "Duplicate parameter '${key.first}' in ${key.second} (count ${values.size}).")
    }
    item.parameters.forEachIndexed { index, param ->
      validateParameter(param, "$basePath[$index]", issues, components, currentSelfBase)
    }
  }

  /**
   * Validates a single [EndpointParameter].
   *
   * @param param Parameter to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   * @param components Components registry.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validateParameter(
      param: EndpointParameter,
      path: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?,
      currentSelfBase: String?
  ) {
    param.reference?.let {
      ValidationCommon.validateReferenceObject(it, path, issues)
      ReferenceAndLinkValidator.validateComponentReference(
          it.ref, "parameters", components, path, issues, currentSelfBase)
      return
    }
    if (param.location == ParameterLocation.PATH && !param.isRequired) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Path parameters must be required.")
    }
    if (param.location == ParameterLocation.HEADER &&
        !ValidationCommon.isValidHeaderToken(param.name)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Header parameter name '${param.name}' must be a valid HTTP header token.")
    }
    if (param.allowEmptyValue != null && param.location != ParameterLocation.QUERY) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "allowEmptyValue is only valid for query parameters.")
    }
    if (param.location == ParameterLocation.HEADER &&
        setOf("accept", "content-type", "authorization").contains(param.name.lowercase())) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = path,
              message =
                  "Header parameters named Accept, Content-Type, or Authorization (${param.name.lowercase()}) are ignored by the specification.")
    }
    if (param.schema == null && param.content.isEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Parameter must define either schema or content.")
    }
    if (param.schema != null && param.content.isNotEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Parameter must not define both schema and content.")
    }
    if (param.content.size > 1) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$path.content",
              message = "Parameter content must contain exactly one media type.")
    }
    val hasSerializationFlags =
        listOfNotNull(param.style, param.explode, param.allowReserved).isNotEmpty()
    val schemaTypes = param.schema?.types.orEmpty()
    if (param.content.isNotEmpty() && hasSerializationFlags) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Parameters using content must not define style/explode/allowReserved.")
    }
    if (param.example != null && param.examples.isNotEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Parameter must not define both example and examples.")
    }
    param.style?.let { style ->
      val allowed =
          when (param.location) {
            ParameterLocation.PATH ->
                setOf(ParameterStyle.MATRIX, ParameterStyle.LABEL, ParameterStyle.SIMPLE)
            ParameterLocation.QUERY ->
                setOf(
                    ParameterStyle.FORM,
                    ParameterStyle.SPACE_DELIMITED,
                    ParameterStyle.PIPE_DELIMITED,
                    ParameterStyle.DEEP_OBJECT)
            ParameterLocation.HEADER -> setOf(ParameterStyle.SIMPLE)
            ParameterLocation.COOKIE -> setOf(ParameterStyle.FORM, ParameterStyle.COOKIE)
            else -> emptySet()
          }
      if (allowed.isNotEmpty() && !allowed.contains(style)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Parameter style '$style' is not allowed for ${param.location}.")
      }
    }
    if (param.style == ParameterStyle.SPACE_DELIMITED && param.explode == true) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "spaceDelimited style does not support explode=true.")
    }
    if (param.style == ParameterStyle.PIPE_DELIMITED && param.explode == true) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "pipeDelimited style does not support explode=true.")
    }
    if (param.style == ParameterStyle.DEEP_OBJECT) {
      if (schemaTypes.isNotEmpty() && !schemaTypes.contains("object")) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "deepObject style only applies to object parameters.")
      }
    }
    if (param.location == ParameterLocation.QUERYSTRING) {
      if (param.schema != null) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "querystring parameters must use content instead of schema.")
      }
      if (param.content.isEmpty()) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "querystring parameters must define content.")
      }
      if (hasSerializationFlags) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "querystring parameters must not use style/explode/allowReserved.")
      }
    }
    param.example?.let {
      ResponseAndContentValidator.validateExampleObject(
          it, "$path.example", issues, components, currentSelfBase)
    }
    param.examples.forEach { (name, example) ->
      ResponseAndContentValidator.validateExampleObject(
          example, "$path.examples.$name", issues, components, currentSelfBase)
    }
    param.content.forEach { (mediaType, media) ->
      ResponseAndContentValidator.validateMediaTypeObject(
          media, "$path.content.$mediaType", issues, mediaType, components, currentSelfBase)
    }
  }

  /**
   * Validates a single [Header] object.
   *
   * @param header Header to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   * @param components Components registry.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validateHeader(
      header: Header,
      path: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?,
      currentSelfBase: String?
  ) {
    header.reference?.let {
      ValidationCommon.validateReferenceObject(it, path, issues)
      ReferenceAndLinkValidator.validateComponentReference(
          it.ref, "headers", components, path, issues, currentSelfBase)
      return
    }
    if (header.schema == null && header.content.isEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Header must define either schema or content.")
    }
    if (header.schema != null && header.content.isNotEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Header must not define both schema and content.")
    }
    if (header.content.size > 1) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$path.content",
              message = "Header content must contain exactly one media type.")
    }
    val hasNonSimpleStyle = header.style in (ParameterStyle.entries - ParameterStyle.SIMPLE)
    if (header.content.isNotEmpty()) {
      if (hasNonSimpleStyle) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Header style must not be set when content is used.")
      }
      if (header.explode == true) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Header explode must not be set when content is used.")
      }
    }
    if (header.example != null && header.examples.isNotEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Header must not define both example and examples.")
    }
    if (hasNonSimpleStyle) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Header style must be 'simple'.")
    }
    header.example?.let {
      ResponseAndContentValidator.validateExampleObject(
          it, "$path.example", issues, components, currentSelfBase)
    }
    header.examples.forEach { (name, example) ->
      ResponseAndContentValidator.validateExampleObject(
          example, "$path.examples.$name", issues, components, currentSelfBase)
    }
    header.content.forEach { (mediaType, media) ->
      ResponseAndContentValidator.validateMediaTypeObject(
          media, "$path.content.$mediaType", issues, mediaType, components, currentSelfBase)
    }
  }
}
