/**
 * Validator rule for root OpenAPI fields (`openapi`, `jsonSchemaDialect`, `$self`, `info`,
 * `contact`, `license`, `tags`, `externalDocs`, and `components` keys).
 */
package openapi.validator

import domain.Components
import domain.Contact
import domain.Info
import domain.License
import domain.OpenApiDefinition
import domain.Tag
import openapi.OpenApiIssue
import openapi.OpenApiIssueSeverity

/**
 * Validates root-level OpenAPI document structure, version, Info, Contact, License, Tags, External
 * Documentation, and Components map keys.
 */
object RootAndInfoValidator : OpenApiValidationRule {
  override fun validate(
      definition: OpenApiDefinition,
      issues: MutableList<OpenApiIssue>,
      currentSelfBase: String?
  ) {
    val hasPaths = definition.paths.isNotEmpty() || definition.pathsExplicitEmpty
    val hasWebhooks = definition.webhooks.isNotEmpty() || definition.webhooksExplicitEmpty
    if (!hasPaths && !hasWebhooks && definition.components == null) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$.openapi",
              message = "OpenAPI requires at least one of: paths, webhooks, or components.")
    }

    validateOpenApiVersion(definition, "$.openapi", issues)
    definition.jsonSchemaDialect?.let {
      ValidationCommon.validateUri(it, "$.jsonSchemaDialect", issues)
    }
    definition.self?.let { ValidationCommon.validateUri(it, "$.\$self", issues) }
    validateInfo(definition.info, "$.info", issues)
    definition.externalDocs?.let {
      ValidationCommon.validateExternalDocs(it, "$.externalDocs", issues)
    }
    validateComponents(definition.components, "$.components", issues, currentSelfBase)
    validateTags(definition, issues)
  }

  /**
   * Validates `info`, `contact`, and `license` fields.
   *
   * @param info Info object to validate.
   * @param basePath JSONPath prefix.
   * @param issues Issue accumulator.
   */
  fun validateInfo(info: Info, basePath: String, issues: MutableList<OpenApiIssue>) {
    if (info.title.isBlank()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$basePath.title",
              message = "Info title must not be blank.")
    }
    if (info.version.isBlank()) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$basePath.version",
              message = "Info version must not be blank.")
    }
    info.termsOfService?.let {
      ValidationCommon.validateUri(it, "$basePath.termsOfService", issues)
    }
    info.contact?.let { validateContact(it, "$basePath.contact", issues) }
    info.license?.let { validateLicense(it, "$basePath.license", issues) }
  }

  private fun validateContact(contact: Contact, path: String, issues: MutableList<OpenApiIssue>) {
    if (contact.url != null) ValidationCommon.validateUri(contact.url, "$path.url", issues)
    if (contact.email != null) ValidationCommon.validateEmail(contact.email, "$path.email", issues)
  }

  private fun validateOpenApiVersion(
      definition: OpenApiDefinition,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    if (!isSupportedOpenApiVersion(definition.openapi)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = path,
              message =
                  "OpenAPI version '${definition.openapi}' is not 3.2.x; 3.2 features may be unsupported.")
    }
  }

  private fun isSupportedOpenApiVersion(version: String): Boolean {
    return Regex("""^3\.2\.\d+$""").matches(version)
  }

  private fun validateLicense(license: License, path: String, issues: MutableList<OpenApiIssue>) {
    if (listOfNotNull(license.identifier, license.url).size == 2) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = path,
              message = "License must not define both identifier and url.")
    }
    if (license.url != null) ValidationCommon.validateUri(license.url, "$path.url", issues)
  }

  /**
   * Validates the `components` object and all its contained maps.
   *
   * @param components Components object or `null`.
   * @param basePath JSONPath prefix.
   * @param issues Issue accumulator.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validateComponents(
      components: Components?,
      basePath: String,
      issues: MutableList<OpenApiIssue>,
      currentSelfBase: String?
  ) {
    if (components == null) return

    validateKeys(components.schemas.keys, basePath, "schemas", issues)
    validateKeys(components.responses.keys, basePath, "responses", issues)
    validateKeys(components.parameters.keys, basePath, "parameters", issues)
    validateKeys(components.requestBodies.keys, basePath, "requestBodies", issues)
    validateKeys(components.headers.keys, basePath, "headers", issues)
    validateKeys(components.securitySchemes.keys, basePath, "securitySchemes", issues)
    validateKeys(components.examples.keys, basePath, "examples", issues)
    validateKeys(components.links.keys, basePath, "links", issues)
    validateKeys(components.callbacks.keys, basePath, "callbacks", issues)
    validateKeys(components.pathItems.keys, basePath, "pathItems", issues)
    validateKeys(components.mediaTypes.keys, basePath, "mediaTypes", issues)

    components.securitySchemes.forEach { (name, scheme) ->
      if (ValidationCommon.looksLikeUriReference(name)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.WARNING,
                path = "$basePath.securitySchemes.$name",
                message =
                    "Security scheme component name '$name' looks like a URI; component names take precedence over URI references in Security Requirements.")
      }
      SecurityValidator.validateSecurityScheme(
          scheme, "$basePath.securitySchemes.$name", issues, components, currentSelfBase)
    }
    components.headers.keys.forEach { name ->
      if (!ValidationCommon.isValidHeaderToken(name)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath.headers.$name",
                message = "Header name '$name' must be a valid HTTP header token.")
      }
    }
  }

  private fun validateKeys(
      keys: Set<String>,
      basePath: String,
      componentPath: String,
      issues: MutableList<OpenApiIssue>
  ) {
    keys
        .filterNot { ValidationCommon.COMPONENT_KEY_REGEX.matches(it) }
        .forEach { key ->
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.ERROR,
                  path = "$basePath.$componentPath.$key",
                  message = "Component keys must match ^[a-zA-Z0-9._-]+$.")
        }
  }

  /**
   * Validates root `tags` uniqueness, parent existence, externalDocs, and parent hierarchy cycles.
   *
   * @param definition OpenAPI definition.
   * @param issues Issue accumulator.
   */
  fun validateTags(definition: OpenApiDefinition, issues: MutableList<OpenApiIssue>) {
    val duplicates = definition.tags.groupBy { it.name }.filter { it.value.size > 1 }
    duplicates.forEach { (name, tags) ->
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$.tags",
              message = "Tag name '$name' must be unique (found ${tags.size}).")
    }
    val tagsByName = definition.tags.associateBy { it.name }
    definition.tags.forEach { tag ->
      tag.externalDocs?.let {
        ValidationCommon.validateExternalDocs(it, "$.tags.${tag.name}.externalDocs", issues)
      }
      val parent = tag.parent ?: return@forEach
      if (!tagsByName.containsKey(parent)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$.tags",
                message = "Tag parent '$parent' for '${tag.name}' must exist in tags.")
      }
    }
    detectTagCycles(tagsByName, issues)
  }

  private fun detectTagCycles(tagsByName: Map<String, Tag>, issues: MutableList<OpenApiIssue>) {
    tagsByName.keys.forEach { start ->
      val seen = mutableSetOf<String>()
      var current = start
      while (true) {
        val parent = tagsByName[current]?.parent ?: break
        if (!seen.add(parent)) {
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.ERROR,
                  path = "$.tags",
                  message = "Tag parent cycle detected involving '$start'.")
          break
        }
        current = parent
      }
    }
  }
}
