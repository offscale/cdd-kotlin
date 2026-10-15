/** Multiplatform OpenAPI 3.2 structural and semantic specification validator. */
package openapi

import domain.OpenApiDefinition
import openapi.validator.OpenApiValidationRule
import openapi.validator.ParameterAndHeaderValidator
import openapi.validator.PathAndOperationValidator
import openapi.validator.ReferenceAndLinkValidator
import openapi.validator.ResponseAndContentValidator
import openapi.validator.RootAndInfoValidator
import openapi.validator.SchemaValidator
import openapi.validator.SecurityValidator
import openapi.validator.ServerValidator
import openapi.validator.ValidationCommon

/**
 * Validates OpenAPI 3.2 documents for common specification constraints by orchestrating modular
 * [OpenApiValidationRule] implementations.
 *
 * Note: When a Reference Object is used for a Parameter, Header, RequestBody, Response, or Path
 * Item, validation short-circuits for that object to avoid false positives from
 * resolved/denormalized IR fields.
 */
class OpenApiValidator {
  /** Ordered list of modular [OpenApiValidationRule]s applied to OpenAPI 3.x documents. */
  val rules: List<OpenApiValidationRule> =
      listOf(
          RootAndInfoValidator,
          ServerValidator,
          PathAndOperationValidator,
          ParameterAndHeaderValidator,
          ResponseAndContentValidator,
          SecurityValidator,
          ReferenceAndLinkValidator,
          SchemaValidator)

  /**
   * Validate the given OpenAPI [definition] and return any detected issues.
   *
   * @param definition The OpenAPI definition to validate.
   * @param baseUri Optional base URI used to resolve relative `$self` bases for link operationRef
   *   validation.
   * @return List of discovered [OpenApiIssue]s.
   */
  fun validate(definition: OpenApiDefinition, baseUri: String? = null): List<OpenApiIssue> {
    if (!definition.openapi.startsWith("3.")) return emptyList()

    val issues = mutableListOf<OpenApiIssue>()
    val resolvedSelfBase = ValidationCommon.resolveSelfBase(definition.self, baseUri)

    rules.forEach { it.validate(definition, issues, resolvedSelfBase) }

    return issues
  }
}

/** Validation issue severity. */
enum class OpenApiIssueSeverity {
  /** Severe specification violation. */
  ERROR,
  /** Non-fatal specification recommendation or ignored field warning. */
  WARNING
}

/**
 * A single validation issue discovered in an OpenAPI definition.
 *
 * @property severity Severity of the issue ([OpenApiIssueSeverity.ERROR] or
 *   [OpenApiIssueSeverity.WARNING]).
 * @property path JSONPath location where the issue occurred.
 * @property message Human-readable description of the validation issue.
 */
data class OpenApiIssue(val severity: OpenApiIssueSeverity, val path: String, val message: String)
