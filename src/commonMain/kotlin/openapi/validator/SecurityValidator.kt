/**
 * Validator rule for OpenAPI [SecurityScheme], [OAuthFlows], and [SecurityRequirement] definitions.
 */
package openapi.validator

import domain.Components
import domain.OAuthFlows
import domain.OpenApiDefinition
import domain.SecurityRequirement
import domain.SecurityScheme
import openapi.OpenApiIssue
import openapi.OpenApiIssueSeverity

/** Validates security schemes, OAuth2 flows, and security requirement references. */
object SecurityValidator : OpenApiValidationRule {
  override fun validate(
      definition: OpenApiDefinition,
      issues: MutableList<OpenApiIssue>,
      currentSelfBase: String?
  ) {
    validateSecurityRequirements(definition.security, definition.components, "$.security", issues)
  }

  /**
   * Validates a list of [SecurityRequirement] objects against [components].
   *
   * @param requirements Security requirements to validate.
   * @param components Components registry.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   */
  fun validateSecurityRequirements(
      requirements: List<SecurityRequirement>,
      components: Components?,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    if (requirements.isEmpty()) return
    val schemeNames = if (components != null) components.securitySchemes.keys else emptySet()
    requirements.forEachIndexed { index, requirement ->
      requirement.keys
          .filter { it.isNotBlank() }
          .forEach { schemeName ->
            val known = schemeNames.contains(schemeName)
            val looksLikeUri = ValidationCommon.looksLikeUriReference(schemeName)
            if (!known && !looksLikeUri) {
              issues +=
                  OpenApiIssue(
                      severity = OpenApiIssueSeverity.ERROR,
                      path = "$path[$index]",
                      message = "Security requirement references undefined scheme '$schemeName'.")
            }
          }
    }
  }

  /**
   * Validates a [SecurityScheme] object based on its `type`.
   *
   * @param scheme SecurityScheme to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   * @param components Components registry.
   * @param currentSelfBase Active `$self` base URI.
   */
  fun validateSecurityScheme(
      scheme: SecurityScheme,
      path: String,
      issues: MutableList<OpenApiIssue>,
      components: Components?,
      currentSelfBase: String?
  ) {
    scheme.reference?.let {
      ValidationCommon.validateReferenceObject(it, path, issues)
      ReferenceAndLinkValidator.validateComponentReference(
          it.ref, "securitySchemes", components, path, issues, currentSelfBase)
      return
    }
    val validTypes = setOf("apiKey", "http", "mutualTLS", "oauth2", "openIdConnect")
    if (!validTypes.contains(scheme.type)) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = "$path.type",
              message =
                  "Security scheme type '${scheme.type}' is invalid. Must be one of: ${validTypes.joinToString()}.")
    }
    when (scheme.type) {
      "apiKey" -> {
        if (scheme.name.orEmpty().isBlank() or scheme.`in`.orEmpty().isBlank()) {
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.ERROR,
                  path = path,
                  message = "apiKey security scheme requires name and in.")
        }
        val location = scheme.`in`
        if (!location.isNullOrBlank() && location !in setOf("query", "header", "cookie")) {
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.ERROR,
                  path = "$path.in",
                  message = "apiKey security scheme 'in' must be one of: query, header, cookie.")
        }
      }
      "http" -> {
        if (scheme.scheme.isNullOrBlank()) {
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.ERROR,
                  path = path,
                  message = "http security scheme requires scheme.")
        }
      }
      "oauth2" -> {
        if (scheme.flows == null) {
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.ERROR,
                  path = path,
                  message = "oauth2 security scheme requires flows.")
        } else {
          validateOAuthFlows(scheme.flows, "$path.flows", issues)
        }
        scheme.oauth2MetadataUrl?.let {
          ValidationCommon.validateUrl(it, "$path.oauth2MetadataUrl", issues, requireHttps = true)
        }
      }
      "openIdConnect" -> {
        if (scheme.openIdConnectUrl.isNullOrBlank()) {
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.ERROR,
                  path = path,
                  message = "openIdConnect security scheme requires openIdConnectUrl.")
        } else {
          ValidationCommon.validateUrl(
              scheme.openIdConnectUrl, "$path.openIdConnectUrl", issues, requireHttps = true)
        }
      }
    }
    if (scheme.type != "oauth2" && scheme.oauth2MetadataUrl != null) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = "$path.oauth2MetadataUrl",
              message = "oauth2MetadataUrl is only valid for oauth2 security schemes.")
    }
    if (scheme.type != "openIdConnect" && scheme.openIdConnectUrl != null) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = "$path.openIdConnectUrl",
              message = "openIdConnectUrl is only valid for openIdConnect security schemes.")
    }
    if (scheme.type != "oauth2" && scheme.flows != null) {
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.WARNING,
              path = "$path.flows",
              message = "OAuth flows are only valid for oauth2 security schemes.")
    }
  }

  /**
   * Validates [OAuthFlows] required URLs per flow type.
   *
   * @param flows OAuthFlows to validate.
   * @param path JSONPath location.
   * @param issues Issue accumulator.
   */
  fun validateOAuthFlows(flows: OAuthFlows, path: String, issues: MutableList<OpenApiIssue>) {
    flows.implicit?.let { flow ->
      if (isMissingFlowUrl(flow.authorizationUrl)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.implicit.authorizationUrl",
                message = "implicit OAuth flow requires authorizationUrl.")
      }
      validateOptionalFlowUrl(flow.authorizationUrl, "$path.implicit.authorizationUrl", issues)
    }
    flows.password?.let { flow ->
      if (isMissingFlowUrl(flow.tokenUrl)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.password.tokenUrl",
                message = "password OAuth flow requires tokenUrl.")
      }
      validateOptionalFlowUrl(flow.tokenUrl, "$path.password.tokenUrl", issues)
      validateOptionalFlowUrl(flow.refreshUrl, "$path.password.refreshUrl", issues)
    }
    flows.clientCredentials?.let { flow ->
      if (isMissingFlowUrl(flow.tokenUrl)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.clientCredentials.tokenUrl",
                message = "clientCredentials OAuth flow requires tokenUrl.")
      }
      validateOptionalFlowUrl(flow.tokenUrl, "$path.clientCredentials.tokenUrl", issues)
      validateOptionalFlowUrl(flow.refreshUrl, "$path.clientCredentials.refreshUrl", issues)
    }
    flows.authorizationCode?.let { flow ->
      if (isMissingFlowUrl(flow.authorizationUrl) or isMissingFlowUrl(flow.tokenUrl)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.authorizationCode",
                message = "authorizationCode OAuth flow requires authorizationUrl and tokenUrl.")
      }
      validateOptionalFlowUrl(
          flow.authorizationUrl, "$path.authorizationCode.authorizationUrl", issues)
      validateOptionalFlowUrl(flow.tokenUrl, "$path.authorizationCode.tokenUrl", issues)
      validateOptionalFlowUrl(flow.refreshUrl, "$path.authorizationCode.refreshUrl", issues)
    }
    flows.deviceAuthorization?.let { flow ->
      if (isMissingFlowUrl(flow.deviceAuthorizationUrl) or isMissingFlowUrl(flow.tokenUrl)) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$path.deviceAuthorization",
                message =
                    "deviceAuthorization OAuth flow requires deviceAuthorizationUrl and tokenUrl.")
      }
      validateOptionalFlowUrl(
          flow.deviceAuthorizationUrl, "$path.deviceAuthorization.deviceAuthorizationUrl", issues)
      validateOptionalFlowUrl(flow.tokenUrl, "$path.deviceAuthorization.tokenUrl", issues)
      validateOptionalFlowUrl(flow.refreshUrl, "$path.deviceAuthorization.refreshUrl", issues)
    }
  }

  private fun isMissingFlowUrl(url: String?): Boolean = url.orEmpty().isBlank()

  private fun validateOptionalFlowUrl(
      url: String?,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    if (url != null) {
      ValidationCommon.validateUrl(url, path, issues, requireHttps = true)
    }
  }
}
