/**
 * Unified, strongly-typed error hierarchy for the CDD (Compiler Driven Development) toolchain.
 *
 * Replaces ad-hoc `RuntimeException`, `IllegalArgumentException`, and `IllegalStateException` usage
 * with a structured sealed hierarchy and `kotlin.Result` combinators.
 */
package domain

/**
 * Sealed hierarchy representing all domain, CLI, I/O, parsing, validation, and synchronization
 * errors in `cdd-kotlin`.
 */
sealed interface CddError {
  /** Human-readable error message describing the failure. */
  val message: String

  /**
   * Error indicating invalid command-line arguments or missing required flags.
   *
   * @property message Details of the CLI usage error.
   */
  data class CliUsageError(override val message: String) : CddError

  /**
   * Error indicating a CLI command or argument failure with an exit code.
   *
   * @property message Details of the CLI failure.
   * @property exitCode Recommended process exit code (defaults to `1`).
   */
  data class CliError(override val message: String, val exitCode: Int = 1) : CddError

  /**
   * Error indicating a file or directory read/write failure.
   *
   * @property path The file or directory path involved.
   * @property cause Description of the underlying I/O failure.
   */
  data class FileIoError(val path: String, val cause: String) : CddError {
    override val message: String
      get() = "File I/O error at '$path': $cause"
  }

  /**
   * Error indicating a syntax or structural error while parsing an OpenAPI or JSON Schema document.
   *
   * @property message Description of the parse failure.
   * @property location Optional JSON pointer or line/column location.
   */
  data class OpenApiParseError(override val message: String, val location: String? = null) :
      CddError

  /**
   * Error indicating that a standalone JSON Schema document or node is invalid.
   *
   * @property message Description of the schema parse failure.
   */
  data class SchemaParseError(override val message: String) : CddError

  /**
   * Error indicating a syntax error while parsing a JSON or YAML payload.
   *
   * @property message Description of the syntax error.
   */
  data class JsonParseError(override val message: String) : CddError

  /**
   * Error indicating that an OpenAPI specification failed semantic validation.
   *
   * @property issues List of validation error descriptions.
   */
  data class OpenApiValidationError(val issues: List<String>) : CddError {
    override val message: String
      get() = "OpenAPI validation failed with ${issues.size} issue(s): ${issues.joinToString("; ")}"
  }

  /**
   * Error indicating a failure while parsing Kotlin source code via PSI.
   *
   * @property file The file name or identifier being parsed.
   * @property reason Description of the PSI parse failure.
   */
  data class PsiParseError(val file: String, val reason: String) : CddError {
    override val message: String
      get() = "PSI parse error in '$file': $reason"
  }

  /**
   * Error indicating a structural conflict while merging an OpenAPI schema into an existing Kotlin
   * DTO via PSI.
   *
   * @property targetName Name of the target DTO class.
   * @property reason Description of the conflict.
   */
  data class MergeConflictError(val targetName: String, val reason: String) : CddError {
    override val message: String
      get() = "Merge conflict on '$targetName': $reason"
  }

  /**
   * Error indicating a required directory path does not exist or is not a directory.
   *
   * @property path The invalid directory path.
   * @property reason Explanation of why the directory is invalid.
   */
  data class InvalidDirectoryError(val path: String, val reason: String) : CddError {
    override val message: String
      get() = "$reason: $path"
  }

  /**
   * Error indicating that Kotlin PSI AST merging failed due to missing classes or constructors.
   *
   * @property targetClass The name of the target class being merged.
   * @property reason Explanation of why the PSI merge failed.
   */
  data class PsiMergeError(val targetClass: String, val reason: String) : CddError {
    override val message: String
      get() = reason
  }

  /**
   * Error indicating that an OpenAPI parameter style/location/type combination cannot be
   * serialized.
   *
   * @property parameterName The name of the parameter that failed serialization generation.
   * @property reason Detailed reason for the serialization failure.
   */
  data class ParameterSerializationError(val parameterName: String, val reason: String) : CddError {
    override val message: String
      get() = reason
  }

  /**
   * Error indicating that an operation is not supported on the current runtime platform (e.g.
   * WASM).
   *
   * @property operation Name of the unsupported operation.
   * @property platform Name of the runtime platform.
   */
  data class UnsupportedPlatformOperationError(val operation: String, val platform: String) :
      CddError {
    override val message: String
      get() = "$operation is not supported on $platform"
  }

  /**
   * Error representing a JSON-RPC 2.0 protocol or application error in the MCP layer.
   *
   * @property code JSON-RPC 2.0 integer error code.
   * @property message Human-readable error message.
   * @property data Optional structured error data.
   */
  data class JsonRpcError(
      val code: Int,
      override val message: String,
      val data: OpenApiValue? = null
  ) : CddError

  /**
   * Error raised when a mock webhook dispatch fails.
   *
   * @property targetUrl The target callback URL.
   * @property reason Description of the webhook dispatch failure.
   */
  data class WebhookDispatchError(val targetUrl: String, val reason: String) : CddError {
    override val message: String
      get() = "Failed to dispatch webhook to $targetUrl: $reason"
  }

  /**
   * Error indicating a syntax or structural error while parsing an Arazzo 1.1.0 document.
   *
   * @property message Description of the Arazzo parse failure.
   * @property location Optional JSON pointer or path location where the error occurred.
   */
  data class ArazzoParseError(override val message: String, val location: String? = null) :
      CddError

  /**
   * Error indicating that an Arazzo 1.1.0 specification failed semantic validation.
   *
   * @property issues List of validation error descriptions.
   */
  data class ArazzoValidationError(val issues: List<String>) : CddError {
    override val message: String
      get() = "Arazzo validation failed with ${issues.size} issue(s): ${issues.joinToString("; ")}"
  }

  /**
   * Error indicating a syntax or evaluation failure in an Arazzo runtime expression.
   *
   * @property expression The raw runtime expression string.
   * @property reason Explanation of why parsing or evaluation failed.
   */
  data class RuntimeExpressionError(val expression: String, val reason: String) : CddError {
    override val message: String
      get() = "Runtime expression error in '$expression': $reason"
  }

  /**
   * Error indicating a failure while evaluating an Arazzo criterion condition or selector.
   *
   * @property condition The criterion condition or selector expression.
   * @property reason Explanation of the evaluation failure.
   */
  data class CriterionEvaluationError(val condition: String, val reason: String) : CddError {
    override val message: String
      get() = "Criterion evaluation error in '$condition': $reason"
  }

  /**
   * Error indicating a failure during Arazzo workflow or step orchestration.
   *
   * @property workflowId Identifier of the workflow being executed.
   * @property stepId Optional identifier of the step where the failure occurred.
   * @property reason Explanation of the workflow execution failure.
   */
  data class WorkflowExecutionError(
      val workflowId: String,
      val stepId: String? = null,
      val reason: String
  ) : CddError {
    override val message: String
      get() =
          if (stepId != null) {
            "Workflow '$workflowId' failed at step '$stepId': $reason"
          } else {
            "Workflow '$workflowId' failed: $reason"
          }
  }
}

/**
 * Non-thrown [Throwable] carrier wrapping a structured [CddError] for use inside [kotlin.Result].
 *
 * @property error The structured [CddError] describing the failure.
 */
class CddException(val error: CddError) : RuntimeException(error.message)

/**
 * Wraps this [CddError] inside a failed [kotlin.Result] using [CddException] as the carrier.
 *
 * @param T The expected success value type of the [Result].
 * @return A failed [Result] containing [CddException] with this [CddError].
 */
fun <T> CddError.toFailure(): Result<T> = Result.failure(CddException(this))

/**
 * Extracts the structured [CddError] from a [Throwable], or converts an arbitrary [Throwable] into
 * a [CddError.CliError].
 *
 * @return The extracted or converted [CddError].
 */
fun Throwable.toCddError(): CddError =
    when (this) {
      is CddException -> this.error
      else -> CddError.CliError(this.message ?: "Unknown error")
    }

/**
 * Returns the structured [CddError] if this [Result] is a failure, or `null` if it succeeded.
 *
 * @param T The success value type of the [Result].
 * @return The [CddError] describing the failure, or `null` on success.
 */
fun <T> Result<T>.cddErrorOrNull(): CddError? = exceptionOrNull()?.toCddError()
