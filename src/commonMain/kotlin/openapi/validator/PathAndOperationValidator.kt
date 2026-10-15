/**
 * Validator rule for OpenAPI `paths`, `webhooks`, [PathItem], and [EndpointDefinition] operations.
 */
package openapi.validator

import domain.Components
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.OpenApiDefinition
import domain.ParameterLocation
import domain.PathItem
import openapi.OpenApiIssue
import openapi.OpenApiIssueSeverity

/**
 * Validates paths, path templating, template collisions, webhooks, PathItem objects, operations,
 * and operationId uniqueness.
 */
object PathAndOperationValidator : OpenApiValidationRule {
  override fun validate(
      definition: OpenApiDefinition,
      issues: MutableList<OpenApiIssue>,
      currentSelfBase: String?
  ) {
    validatePaths(definition.paths, "$.paths", issues, definition.components, currentSelfBase)
    validateWebhooks(
        definition.webhooks, "$.webhooks", issues, definition.components, currentSelfBase)
    validateOperationIds(definition, issues)
  }

  /**
   * Validates `paths` entries, path key rules, and path template collisions.
   *
   * @param paths Map of path strings to [PathItem]s.
   * @param basePath JSONPath prefix.
   * @param issues Issue accumulator.
   * @param components Components registry.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validatePaths(
      paths: Map<String, PathItem>,
      basePath: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?,
      currentSelfBase: String?
  ) {
    paths.forEach { (pathKey, item) ->
      if (!pathKey.startsWith("/")) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.$pathKey",
                message = "Path keys must start with '/'.")
      }
      if (pathKey.contains("?") || pathKey.contains("#")) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.$pathKey",
                message = "Path keys must not include query or fragment.")
      }
      validatePathItem(
          pathKey,
          item,
          "$basePath.$pathKey",
          issues,
          components,
          validateTemplating = true,
          currentSelfBase = currentSelfBase)
    }
    validatePathTemplateCollisions(paths.keys, basePath, issues)
  }

  /**
   * Validates `webhooks` entries.
   *
   * @param webhooks Map of webhook names to [PathItem]s.
   * @param basePath JSONPath prefix.
   * @param issues Issue accumulator.
   * @param components Components registry.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validateWebhooks(
      webhooks: Map<String, PathItem>,
      basePath: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?,
      currentSelfBase: String?
  ) {
    webhooks.forEach { (name, item) ->
      validatePathItem(
          name,
          item,
          "$basePath.$name",
          issues,
          components,
          validateTemplating = false,
          currentSelfBase = currentSelfBase)
    }
  }

  /**
   * Validates a single [PathItem] and its operations.
   *
   * @param pathKey Path or webhook key.
   * @param item Path item to validate.
   * @param basePath JSONPath location.
   * @param issues Issue accumulator.
   * @param components Components registry.
   * @param validateTemplating Whether to validate path parameter templates.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validatePathItem(
      pathKey: String,
      item: PathItem,
      basePath: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?,
      validateTemplating: Boolean,
      currentSelfBase: String?
  ) {
    item.ref?.let {
      ValidationCommon.validateUri(it, "$basePath.\$ref", issues)
      ReferenceAndLinkValidator.validateComponentReference(
          it, "pathItems", components, "$basePath.\$ref", issues, currentSelfBase)
    }
    if (item.ref != null && hasPathItemSiblings(item)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = "$basePath.\$ref",
              message =
                  "Path Item with \$ref should not define other fields; sibling fields are ignored.")
    }
    if (item.ref != null) {
      return
    }
    if (validateTemplating) {
      validatePathTemplating(pathKey, item, basePath, issues)
    }
    ParameterAndHeaderValidator.validatePathItemParameters(
        item, "$basePath.parameters", issues, components, currentSelfBase)
    ServerValidator.validateServers(item.servers, "$basePath.servers", issues)
    item.get?.let {
      validateOperation(it, "$basePath.get", issues, components, item.parameters, currentSelfBase)
    }
    item.put?.let {
      validateOperation(it, "$basePath.put", issues, components, item.parameters, currentSelfBase)
    }
    item.post?.let {
      validateOperation(it, "$basePath.post", issues, components, item.parameters, currentSelfBase)
    }
    item.delete?.let {
      validateOperation(
          it, "$basePath.delete", issues, components, item.parameters, currentSelfBase)
    }
    item.options?.let {
      validateOperation(
          it, "$basePath.options", issues, components, item.parameters, currentSelfBase)
    }
    item.head?.let {
      validateOperation(it, "$basePath.head", issues, components, item.parameters, currentSelfBase)
    }
    item.patch?.let {
      validateOperation(it, "$basePath.patch", issues, components, item.parameters, currentSelfBase)
    }
    item.trace?.let {
      validateOperation(it, "$basePath.trace", issues, components, item.parameters, currentSelfBase)
    }
    item.query?.let {
      validateOperation(it, "$basePath.query", issues, components, item.parameters, currentSelfBase)
    }

    val reserved =
        setOf("get", "put", "post", "delete", "options", "head", "patch", "trace", "query")
    item.additionalOperations.keys.forEach { method ->
      if (reserved.contains(method.lowercase())) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.additionalOperations.$method",
                message = "additionalOperations must not include standard HTTP methods.")
      }
      if (!ValidationCommon.HTTP_METHOD_TOKEN_REGEX.matches(method)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.additionalOperations.$method",
                message = "additionalOperations method '$method' must be a valid HTTP token.")
      }
    }
  }

  /**
   * Validates a single [EndpointDefinition] operation.
   *
   * @param operation Operation to validate.
   * @param basePath JSONPath location.
   * @param issues Issue accumulator.
   * @param components Components registry.
   * @param pathItemParameters Inherited path-level parameters.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validateOperation(
      operation: EndpointDefinition,
      basePath: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?,
      pathItemParameters: List<EndpointParameter>,
      currentSelfBase: String?
  ) {
    if (operation.responses.isEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$basePath.responses",
              message = "Operation must define at least one response.")
    }
    if (operation.responses.size == 1) {
      val onlyCode = operation.responses.keys.first()
      val isSuccess = onlyCode.startsWith("2") || onlyCode.equals("default", ignoreCase = true)
      if (!isSuccess) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$basePath.responses",
                message = "Only one response is defined and it is not a success response (2XX).")
      }
    }
    operation.responses.keys.forEach { code ->
      if (!ResponseAndContentValidator.isValidResponseCode(code)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.responses.$code",
                message =
                    "Invalid response code '$code'. Use HTTP status codes, ranges (1XX-5XX), or 'default'.")
      }
    }

    val combinedParams = pathItemParameters + operation.parameters
    val queryParams = combinedParams.filter { it.location == ParameterLocation.QUERY }
    val queryStringParams = combinedParams.filter { it.location == ParameterLocation.QUERYSTRING }
    if (queryParams.isNotEmpty() && queryStringParams.isNotEmpty()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$basePath.parameters",
              message = "query and querystring parameters cannot be used together.")
    }
    if (queryStringParams.size > 1) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$basePath.parameters",
              message = "Only one querystring parameter is allowed per operation.")
    }

    val duplicates =
        operation.parameters.groupBy { it.name to it.location }.filter { it.value.size > 1 }
    duplicates.forEach { (key, values) ->
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$basePath.parameters",
              message =
                  "Duplicate parameter '${key.first}' in ${key.second} (count ${values.size}).")
    }

    operation.parameters.forEachIndexed { index, param ->
      ParameterAndHeaderValidator.validateParameter(
          param, "$basePath.parameters[$index]", issues, components, currentSelfBase)
    }

    operation.responses.entries.forEachIndexed { index, entry ->
      val responsePath = "$basePath.responses[$index]"
      entry.value.headers.forEach { (headerName, header) ->
        if (headerName.equals("content-type", ignoreCase = true)) {
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.WARNING,
                  path = "$responsePath.headers.$headerName",
                  message = "Response headers must not include 'Content-Type' (it is ignored).")
        }
        ParameterAndHeaderValidator.validateHeader(
            header, "$responsePath.headers.$headerName", issues, components, currentSelfBase)
      }
      ResponseAndContentValidator.validateResponse(
          entry.value, responsePath, issues, components, currentSelfBase)
    }

    operation.requestBody?.let {
      ResponseAndContentValidator.validateRequestBody(
          it, "$basePath.requestBody", issues, components, currentSelfBase)
    }
    if (operation.callbacks.isNotEmpty()) {
      ReferenceAndLinkValidator.validateCallbacks(
          operation.callbacks, "$basePath.callbacks", issues, components)
    }
    operation.externalDocs?.let {
      ValidationCommon.validateExternalDocs(it, "$basePath.externalDocs", issues)
    }
    SecurityValidator.validateSecurityRequirements(
        operation.security, components, "$basePath.security", issues)
    ServerValidator.validateServers(operation.servers, "$basePath.servers", issues)
  }

  /**
   * Validates global `operationId` uniqueness across the OpenAPI document.
   *
   * @param definition OpenAPI definition.
   * @param issues Issue accumulator.
   */
  fun validateOperationIds(definition: OpenApiDefinition, issues: MutableList<OpenApiIssue>) {
    val operations =
        ValidationCommon.collectAllOperations(definition).filter { it.operationIdExplicit }
    val duplicates = operations.groupBy { it.operationId }.filter { it.value.size > 1 }
    duplicates.forEach { (operationId, ops) ->
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$.paths",
              message = "operationId '$operationId' must be unique (found ${ops.size}).")
    }
  }

  private fun hasPathItemSiblings(item: PathItem): Boolean {
    val items =
        listOfNotNull(item.summary, item.description) +
            ValidationCommon.collectOperations(item) +
            item.parameters +
            item.servers +
            item.extensions.keys
    return items.isNotEmpty()
  }

  private fun validatePathTemplating(
      pathKey: String,
      item: PathItem,
      basePath: String,
      issues: MutableList<OpenApiIssue>
  ) {
    val templateParams =
        ValidationCommon.PATH_TEMPLATE_REGEX.findAll(pathKey).map { it.groupValues[1] }.toList()
    if (templateParams.isEmpty()) return

    val duplicates = templateParams.groupBy { it }.filter { it.value.size > 1 }
    duplicates.forEach { (name, values) ->
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = basePath,
              message =
                  "Path template parameter '$name' must not appear more than once (found ${values.size}).")
    }

    item.parameters.forEach { param ->
      if (param.location == ParameterLocation.PATH && !templateParams.contains(param.name)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.parameters",
                message = "Path parameter '${param.name}' is not present in the path template.")
      }
    }

    val operations = ValidationCommon.collectOperations(item)
    operations.forEach { op ->
      val effectiveParams =
          (item.parameters + op.parameters).filter { it.location == ParameterLocation.PATH }
      templateParams.forEach { name ->
        if (effectiveParams.none { it.name == name }) {
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.ERROR,
                  path = "$basePath.${op.methodName.lowercase()}",
                  message = "Missing path parameter '$name' for operation ${op.operationId}.")
        }
      }
      op.parameters
          .filter { it.location == ParameterLocation.PATH }
          .forEach { param ->
            if (!templateParams.contains(param.name)) {
              issues +=
                  OpenApiIssue(
                      severity = OpenApiIssueSeverity.ERROR,
                      path = "$basePath.${op.methodName.lowercase()}.parameters",
                      message =
                          "Path parameter '${param.name}' is not present in the path template.")
            }
          }
    }
  }

  private fun validatePathTemplateCollisions(
      pathKeys: Set<String>,
      basePath: String,
      issues: MutableList<OpenApiIssue>
  ) {
    val normalized = pathKeys.groupBy { normalizePathTemplate(it) }.filter { it.value.size > 1 }
    normalized.forEach { (template, paths) ->
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = basePath,
              message =
                  "Paths share the same templated structure '$template': ${paths.joinToString(", ")}.")
    }
  }

  private fun normalizePathTemplate(path: String): String {
    return ValidationCommon.PATH_TEMPLATE_REGEX.replace(path, "{}")
  }
}
