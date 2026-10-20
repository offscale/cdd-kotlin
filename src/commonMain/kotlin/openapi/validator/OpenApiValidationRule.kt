/** Common interface and shared validation helpers for modular OpenAPI 3.2 validation rules. */
package openapi.validator

import domain.Callback
import domain.Components
import domain.EndpointDefinition
import domain.IdentityKey
import domain.OpenApiDefinition
import domain.PathItem
import domain.ReferenceObject
import openapi.OpenApiIssue
import openapi.OpenApiIssueSeverity
import openapi.isAbsoluteUri
import openapi.parseUriSyntax
import openapi.resolveUri

/** Contract for a modular OpenAPI 3.2 validation rule. */
interface OpenApiValidationRule {
  /**
   * Validates [definition] and appends any discovered [OpenApiIssue]s to [issues].
   *
   * @param definition The OpenAPI document to validate.
   * @param issues Mutable list of issues to append to.
   * @param currentSelfBase Resolved `$self` base URI for the current document.
   */
  fun validate(
      definition: OpenApiDefinition,
      issues: MutableList<OpenApiIssue>,
      currentSelfBase: String?
  )
}

/**
 * Shared validation utilities for URIs, URLs, emails, media types, component keys, and operation
 * traversal.
 */
object ValidationCommon {
  /** Regex matching valid OpenAPI component map keys. */
  val COMPONENT_KEY_REGEX = Regex("""^[a-zA-Z0-9._-]+$""")

  /** Regex matching path template expressions `{param}`. */
  val PATH_TEMPLATE_REGEX = """\{([^}]+)}""".toRegex()

  /** Regex matching server variable expressions `{var}`. */
  val SERVER_VARIABLE_REGEX = """\{([^}]+)}""".toRegex()

  /** Regex matching embedded runtime expressions `{...}`. */
  val RUNTIME_EXPRESSION_EMBED_REGEX = """\{([^{}]+)}""".toRegex()

  /** Regex matching valid RFC 9110 HTTP method tokens. */
  val HTTP_METHOD_TOKEN_REGEX = """^[!#${'$'}%&'*+\-.^_`|~0-9A-Za-z]+$""".toRegex()

  /** Regex matching valid RFC 9110 HTTP header field names. */
  val HEADER_TOKEN_REGEX = """^[!#${'$'}%&'*+\-.^_`|~0-9A-Za-z]+$""".toRegex()

  /** Regex matching basic email addresses. */
  val EMAIL_REGEX = """^[^@\s]+@[^@\s]+\.[^@\s]+$""".toRegex()

  private const val MEDIA_TYPE_MAIN_PATTERN =
      """^[A-Za-z0-9!#${'$'}&^_.+\-]+/(?:\*|\*\+[A-Za-z0-9!#${'$'}&^_.+\-]+|[A-Za-z0-9!#${'$'}&^_.+\-]+(?:\+[A-Za-z0-9!#${'$'}&^_.+\-]+)?)$"""

  /** Regex matching valid media type or media type range keys. */
  val MEDIA_TYPE_MAIN_REGEX = Regex(MEDIA_TYPE_MAIN_PATTERN)

  /**
   * Validates that [value] is a syntactically valid email address.
   *
   * @param value Email string to validate.
   * @param path JSONPath location for error reporting.
   * @param issues Issue accumulator.
   */
  fun validateEmail(value: String, path: String, issues: MutableList<OpenApiIssue>) {
    if (!EMAIL_REGEX.matches(value)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Value must be a valid email address.")
    }
  }

  /**
   * Validates a [ReferenceObject]'s `$ref` URI syntax.
   *
   * @param reference Reference object to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   */
  fun validateReferenceObject(
      reference: ReferenceObject,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    validateUri(reference.ref, "$path.\$ref", issues)
  }

  /**
   * Validates that [value] is a syntactically valid URI reference.
   *
   * @param value URI string to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   */
  fun validateUri(value: String, path: String, issues: MutableList<OpenApiIssue>) {
    validateUriInternal(
        value, path, issues, requireAbsolute = false, requireHttps = false, label = "URI")
  }

  /**
   * Validates that [value] is an absolute URL, optionally requiring `https`.
   *
   * @param value URL string to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   * @param requireHttps Whether non-https schemes emit a warning.
   */
  fun validateUrl(
      value: String,
      path: String,
      issues: MutableList<OpenApiIssue>,
      requireHttps: Boolean
  ) {
    validateUriInternal(
        value, path, issues, requireAbsolute = true, requireHttps = requireHttps, label = "URL")
  }

  private fun validateUriInternal(
      value: String,
      path: String,
      issues: MutableList<OpenApiIssue>,
      requireAbsolute: Boolean,
      requireHttps: Boolean,
      label: String
  ) {
    val uri = parseUriSyntax(value)
    if (uri == null) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "Value must be a valid $label.")
      return
    }
    val scheme = uri.scheme.orEmpty().lowercase()
    if (!uri.isAbsolute) {
      if (requireAbsolute) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = path,
                message = "Value must be an absolute $label.")
      }
      return
    }
    if (requireHttps && scheme != "https") {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = path,
              message = "$label should use https scheme.")
    }
  }

  /**
   * Validates an [domain.ExternalDocumentation] object's URL.
   *
   * @param docs External documentation object.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   */
  fun validateExternalDocs(
      docs: domain.ExternalDocumentation,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    validateUri(docs.url, "$path.url", issues)
  }

  /**
   * Checks whether [token] is a valid HTTP header field name.
   *
   * @param token Header name string.
   * @return `true` if valid.
   */
  fun isValidHeaderToken(token: String): Boolean {
    return HEADER_TOKEN_REGEX.matches(token)
  }

  /**
   * Checks whether [value] is a valid media type or media type range key.
   *
   * @param value Media type key string.
   * @return `true` if valid.
   */
  fun isValidMediaTypeKey(value: String): Boolean {
    val main = value.trim().substringBefore(";").trim()
    return main == "*/*" || MEDIA_TYPE_MAIN_REGEX.matches(main)
  }

  /**
   * Checks whether [value] looks like a URI reference rather than a simple component key.
   *
   * @param value String to inspect.
   * @return `true` if it starts with URI/path markers or contains `://`.
   */
  fun looksLikeUriReference(value: String): Boolean {
    val prefixes = listOf("#", "./", "../", "/", "urn:")
    return prefixes.any { value.startsWith(it) } || value.contains("://")
  }

  /**
   * Normalizes a `$self` or base URI by trimming whitespace and stripping any `#` fragment.
   *
   * @param self Raw `$self` URI string.
   * @return Normalized base URI or `null`.
   */
  fun normalizeSelfBase(self: String?): String? {
    val trimmed = self?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return trimmed.substringBefore("#")
  }

  /**
   * Resolves a document's `$self` URI against an optional [baseUri].
   *
   * @param self Document `$self` value.
   * @param baseUri Optional retrieval base URI.
   * @return Resolved base URI or `null`.
   */
  fun resolveSelfBase(self: String?, baseUri: String?): String? {
    val normalizedSelf = normalizeSelfBase(self)
    val normalizedBase = normalizeSelfBase(baseUri)
    if (normalizedSelf != null) {
      if (normalizedBase == null || isAbsoluteUri(normalizedSelf)) {
        return normalizedSelf
      }
      return resolveAgainstBase(normalizedBase, normalizedSelf)
    }
    return normalizedBase
  }

  /**
   * Resolves [ref] against [base] URI.
   *
   * @param base Base URI string.
   * @param ref Reference URI string.
   * @return Resolved URI string.
   */
  fun resolveAgainstBase(base: String, ref: String): String {
    return resolveUri(base, ref)
  }

  /**
   * Collects all [EndpointDefinition] operations defined on a single [PathItem].
   *
   * @param item Path item to inspect.
   * @return List of operations on [item].
   */
  fun collectOperations(item: PathItem): List<EndpointDefinition> {
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

  /**
   * Collects all [EndpointDefinition] operations across a map of [PathItem]s.
   *
   * @param paths Map of path keys to [PathItem]s.
   * @return Flattened list of operations.
   */
  fun collectOperations(paths: Map<String, PathItem>): List<EndpointDefinition> {
    return paths.values.flatMap { collectOperations(it) }
  }

  /**
   * Collects all [EndpointDefinition] operations across paths, webhooks, component pathItems, and
   * nested callbacks in [definition].
   *
   * @param definition OpenAPI definition to traverse.
   * @return All discovered operations.
   */
  fun collectAllOperations(definition: OpenApiDefinition): List<EndpointDefinition> {
    val components = definition.components
    val rootOperations =
        collectOperations(definition.paths) +
            collectOperations(definition.webhooks) +
            collectOperations(components?.pathItems.orEmpty())
    val componentCallbackOps =
        collectCallbackOperations(components?.callbacks.orEmpty(), components)
    return collectOperationsWithCallbacks(rootOperations + componentCallbackOps, components)
  }

  private fun collectOperationsWithCallbacks(
      roots: List<EndpointDefinition>,
      components: Components?
  ): List<EndpointDefinition> {
    if (roots.isEmpty()) return emptyList()
    val visited = mutableSetOf<IdentityKey<EndpointDefinition>>()
    val queue = ArrayDeque<EndpointDefinition>()
    queue.addAll(roots)
    val collected = mutableListOf<EndpointDefinition>()

    while (queue.isNotEmpty()) {
      val op = queue.removeFirst()
      if (!visited.add(IdentityKey(op))) continue
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
      val resolved =
          when (callback) {
            is Callback.Inline -> callback
            is Callback.Reference ->
                ReferenceAndLinkValidator.resolveCallbackRef(callback.reference.ref, components)
                    as? Callback.Inline
          }
      val expressions = resolved?.expressions.orEmpty()
      expressions.values.forEach { pathItem -> operations.addAll(collectOperations(pathItem)) }
    }
    return operations
  }
}
