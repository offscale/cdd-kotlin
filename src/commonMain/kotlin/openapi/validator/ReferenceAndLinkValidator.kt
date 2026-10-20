/**
 * Validator rule for OpenAPI component `$ref` targets, [Link] objects, [Callback] expressions, and
 * runtime expressions.
 */
package openapi.validator

import arazzo.RuntimeExpressionEngine
import domain.Callback
import domain.Components
import domain.Link
import domain.OpenApiDefinition
import domain.OpenApiValue
import domain.PathItem
import domain.stringOrNull
import openapi.OpenApiIssue
import openapi.OpenApiIssueSeverity
import openapi.decodeJsonPointerToken
import openapi.isAbsoluteUri
import openapi.percentDecode

/**
 * Parsed component `$ref` target information.
 *
 * @property base Optional base URI before `#/components/...`.
 * @property name Decoded component key name.
 */
data class ComponentRefInfo(val base: String?, val name: String)

/**
 * Validates component references, link `operationId`/`operationRef` targets, callback expressions,
 * and OAS runtime expressions.
 */
object ReferenceAndLinkValidator : OpenApiValidationRule {
  override fun validate(
      definition: OpenApiDefinition,
      issues: MutableList<OpenApiIssue>,
      currentSelfBase: String?
  ) {
    validateLinkTargets(definition, issues, currentSelfBase)
  }

  /**
   * Validates all [Link] targets across operations and components against known `operationId`s and
   * `operationRef`s.
   *
   * @param definition OpenAPI definition.
   * @param issues Issue accumulator.
   * @param selfBase Resolved `$self` base URI.
   */
  fun validateLinkTargets(
      definition: OpenApiDefinition,
      issues: MutableList<OpenApiIssue>,
      selfBase: String?
  ) {
    val operations = ValidationCommon.collectAllOperations(definition)
    if (operations.isEmpty()) return
    val operationIds = operations.filter { it.operationIdExplicit }.map { it.operationId }.toSet()
    val operationRefs = collectOperationRefs(definition)

    operations.forEach { op ->
      op.responses.forEach { (code, response) ->
        response.links?.forEach { (name, link) ->
          val basePath =
              "$.paths.${op.path}.${op.methodName.lowercase()}.responses.$code.links.$name"
          validateLinkTarget(link, basePath, operationIds, operationRefs, selfBase, issues)
        }
      }
    }

    val components = definition.components
    if (components != null) {
      components.responses.forEach { (name, response) ->
        response.links?.forEach { (linkName, link) ->
          val basePath = "$.components.responses.$name.links.$linkName"
          validateLinkTarget(link, basePath, operationIds, operationRefs, selfBase, issues)
        }
      }

      components.links.forEach { (name, link) ->
        val basePath = "$.components.links.$name"
        validateLinkTarget(link, basePath, operationIds, operationRefs, selfBase, issues)
      }
    }
  }

  private fun validateLinkTarget(
      link: Link,
      path: String,
      operationIds: Set<String>,
      operationRefs: Set<String>,
      selfBase: String?,
      issues: MutableList<OpenApiIssue>
  ) {
    if (link.ref != null || link.reference != null) return
    val operationId = link.operationId
    if (!operationId.isNullOrBlank() && !operationIds.contains(operationId)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Link operationId '$operationId' does not match any known operationId.")
    }
    val operationRef = link.operationRef
    if (!operationRef.isNullOrBlank()) {
      val localRef = toLocalOperationRef(operationRef, selfBase)
      if (localRef != null && !operationRefs.contains(localRef)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message =
                    "Link operationRef '$operationRef' does not resolve to a known operation.")
      }
    }
  }

  /**
   * Validates a [Link] object for mutual exclusivity of `operationId` and `operationRef`, `$ref`
   * siblings, and runtime expressions.
   *
   * @param link Link to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   * @param components Components registry.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validateLink(
      link: Link,
      path: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?,
      currentSelfBase: String?
  ) {
    val refTarget = link.ref ?: link.reference?.ref
    if (refTarget != null) {
      if (link.ref != null) {
        ValidationCommon.validateUri(link.ref, "$path.\$ref", issues)
      }
      if (link.reference != null) {
        ValidationCommon.validateReferenceObject(link.reference, path, issues)
      }
      validateComponentReference(refTarget, "links", components, path, issues, currentSelfBase)
      val hasOverrides =
          listOfNotNull(link.operationId, link.operationRef, link.requestBody, link.server)
              .isNotEmpty() || link.parameters.isNotEmpty()
      if (hasOverrides) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = path,
                message = "Link with \$ref should not define other fields (they are ignored).")
      }
      return
    }
    val hasOperationId = !link.operationId.isNullOrBlank()
    val hasOperationRef = !link.operationRef.isNullOrBlank()
    if (hasOperationId && hasOperationRef) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Link must not define both operationId and operationRef.")
    }
    if (!hasOperationId && !hasOperationRef) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Link must define either operationId or operationRef.")
    }
    link.operationRef?.let { ValidationCommon.validateUri(it, "$path.operationRef", issues) }
    validateLinkRuntimeExpressions(link, path, issues)
  }

  private fun validateLinkRuntimeExpressions(
      link: Link,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    link.parameters.forEach { (name, value) ->
      validateRuntimeExpressionValue(value, "$path.parameters.$name", issues)
    }
    link.requestBody?.let { validateRuntimeExpressionValue(it, "$path.requestBody", issues) }
  }

  private fun validateRuntimeExpressionValue(
      value: OpenApiValue?,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    val text = value.stringOrNull().orEmpty()
    val expressions = extractEmbeddedRuntimeExpressions(text)
    expressions.forEach { expression ->
      if (!isValidRuntimeExpression(expression)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Runtime expression '$expression' is not valid.")
      }
    }
  }

  private fun extractEmbeddedRuntimeExpressions(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.startsWith("$")) return listOf(trimmed)
    return ValidationCommon.RUNTIME_EXPRESSION_EMBED_REGEX.findAll(raw)
        .map { match -> match.groupValues[1].trim() }
        .filter { it.startsWith("$") }
        .toList()
  }

  /**
   * Validates a map of [Callback]s for valid runtime expression keys and resolvable `$ref`s.
   *
   * @param callbacks Map of callback names to [Callback]s.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   * @param components Components registry.
   */
  fun validateCallbacks(
      callbacks: Map<String, Callback>,
      path: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?
  ) {
    callbacks.forEach { (name, callback) ->
      when (callback) {
        is Callback.Reference -> {
          ValidationCommon.validateReferenceObject(callback.reference, "$path.$name", issues)
          val ref = callback.reference.ref
          if (resolveCallbackRef(ref, components) == null) {
            issues +=
                OpenApiIssue(
                    severity = OpenApiIssueSeverity.WARNING,
                    path = "$path.$name",
                    message = "Callback reference '$ref' could not be resolved.")
          }
        }
        is Callback.Inline -> {
          callback.expressions.forEach { (rawExpression, _) ->
            val expressions = extractEmbeddedRuntimeExpressions(rawExpression)
            if (expressions.isEmpty()) {
              issues +=
                  OpenApiIssue(
                      severity = OpenApiIssueSeverity.ERROR,
                      path = "$path.$name",
                      message =
                          "Callback expression '$rawExpression' must include a valid runtime expression.")
              return@forEach
            }
            expressions.forEach { expression ->
              if (!isValidRuntimeExpression(expression)) {
                issues +=
                    OpenApiIssue(
                        severity = OpenApiIssueSeverity.ERROR,
                        path = "$path.$name",
                        message =
                            "Callback expression '$rawExpression' contains invalid runtime expression '$expression'.")
              }
            }
          }
        }
      }
    }
  }

  private fun isValidRuntimeExpression(value: String): Boolean =
      RuntimeExpressionEngine.isValidOpenApiRuntimeExpression(
          value, ValidationCommon::isValidHeaderToken)

  /**
   * Validates that a component `$ref` resolves to a defined key in [components].
   *
   * @param ref Reference URI string.
   * @param component Component section name.
   * @param components Components registry.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validateComponentReference(
      ref: String,
      component: String,
      components: Components?,
      path: String,
      issues: MutableList<OpenApiIssue>,
      currentSelfBase: String?
  ) {
    val info = extractComponentRefInfo(ref, component, currentSelfBase) ?: return
    if (!isLocalComponentRef(info.base, currentSelfBase)) return
    val keys = componentKeys(components, component)
    if (!keys.contains(info.name)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Reference '$ref' does not resolve to components.$component.")
    }
  }

  private fun componentKeys(components: Components?, component: String): Set<String> {
    if (components == null) return emptySet()
    return when (component) {
      "responses" -> components.responses.keys
      "parameters" -> components.parameters.keys
      "requestBodies" -> components.requestBodies.keys
      "headers" -> components.headers.keys
      "securitySchemes" -> components.securitySchemes.keys
      "examples" -> components.examples.keys
      "links" -> components.links.keys
      "pathItems" -> components.pathItems.keys
      else -> components.mediaTypes.keys
    }
  }

  /**
   * Extracts component reference info from [ref] for [component].
   *
   * @param ref Reference URI string.
   * @param component Component section name.
   * @param currentSelfBase Active `$self` base URI.
   * @return [ComponentRefInfo] or `null`.
   */
  fun extractComponentRefInfo(
      ref: String,
      component: String,
      currentSelfBase: String?
  ): ComponentRefInfo? {
    val marker = "#/components/$component/"
    val index = ref.indexOf(marker)
    if (index < 0) return null
    val base = ref.substring(0, index).ifBlank { null }
    val raw = ref.substring(index + marker.length)
    val first = raw.substringBefore("/")
    val name = decodeJsonPointerToken(percentDecode(first))
    if (name.isBlank()) return null
    return ComponentRefInfo(base = resolveReferenceBase(base, currentSelfBase), name = name)
  }

  private fun resolveReferenceBase(base: String?, currentSelfBase: String?): String? {
    val normalized = ValidationCommon.normalizeSelfBase(base) ?: return null
    val selfBase = ValidationCommon.normalizeSelfBase(currentSelfBase)
    if (selfBase == null || isAbsoluteUri(normalized)) return normalized
    return ValidationCommon.resolveAgainstBase(selfBase, normalized)
  }

  /**
   * Checks whether [refBase] points to the local document (or matches [currentSelfBase]).
   *
   * @param refBase Base URI from reference.
   * @param currentSelfBase Active `$self` base URI.
   * @return `true` if local.
   */
  fun isLocalComponentRef(refBase: String?, currentSelfBase: String?): Boolean {
    if (refBase.isNullOrBlank()) return true
    val selfBase = ValidationCommon.normalizeSelfBase(currentSelfBase) ?: return false
    return ValidationCommon.normalizeSelfBase(refBase) == selfBase
  }

  /**
   * Resolves a `#/components/callbacks/<key>` reference against [components].
   *
   * @param ref Callback reference string.
   * @param components Components registry.
   * @return Resolved [Callback] or `null`.
   */
  fun resolveCallbackRef(ref: String, components: Components?): Callback? {
    val key = extractComponentKey(ref, "callbacks")
    val callbacks = if (components != null) components.callbacks else emptyMap()
    return if (key != null) callbacks[key] else null
  }

  private fun extractComponentKey(ref: String, component: String): String? {
    val marker = "#/components/$component/"
    val raw = ref.substringAfter(marker, "")
    if (raw.isBlank() || raw.contains("/")) return null
    return decodeJsonPointerToken(raw)
  }

  private fun collectOperationRefs(definition: OpenApiDefinition): Set<String> {
    val refs = LinkedHashSet<String>()

    definition.paths.forEach { (path, item) -> addPathItemRefs("#/paths", path, item, refs) }
    definition.webhooks.forEach { (name, item) -> addPathItemRefs("#/webhooks", name, item, refs) }
    val pathItems = definition.components?.pathItems.orEmpty()
    pathItems.forEach { (name, item) ->
      addPathItemRefs("#/components/pathItems", name, item, refs)
    }

    return refs
  }

  private fun addPathItemRefs(base: String, key: String, item: PathItem, refs: MutableSet<String>) {
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

  private fun toLocalOperationRef(operationRef: String, selfBase: String?): String? {
    if (operationRef.startsWith("#/")) return normalizeOperationRef(operationRef)
    val hashIndex = operationRef.indexOf("#/")
    if (hashIndex < 0) return null
    val base = operationRef.substring(0, hashIndex).trimEnd('#')
    val normalizedSelf = selfBase.orEmpty().trimEnd('#')
    if (normalizedSelf.isBlank() || base != normalizedSelf) return null
    return normalizeOperationRef(operationRef.substring(hashIndex))
  }

  private fun encodeJsonPointerSegment(value: String): String {
    return value.replace("~", "~0").replace("/", "~1").replace("{", "%7B").replace("}", "%7D")
  }

  private fun normalizeOperationRef(ref: String): String {
    return ref.replace("{", "%7B").replace("}", "%7D").replace("%7b", "%7B").replace("%7d", "%7D")
  }
}
