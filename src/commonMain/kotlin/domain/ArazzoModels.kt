/**
 * Strongly-typed Arazzo 1.1.0 specification domain models.
 *
 * Defines [ArazzoDefinition], [ArazzoInfo], [SourceDescription], [SourceDescriptionType],
 * [Workflow], [StepObject], [StepActionType], [ArazzoParameter], [ArazzoParameterLocation],
 * [ReusableObject], [ReusableOr], [SuccessActionObject], [SuccessActionType],
 * [FailureActionObject], [FailureActionType], [CriterionObject], [CriterionEngineType],
 * [ExpressionTypeObject], [CriterionTypeOrExpression], [SelectorObject], [OutputValue],
 * [ArazzoRequestBody], [PayloadReplacementObject], and [ArazzoComponents].
 */
package domain

/**
 * Root object of an Arazzo 1.1.0 Specification document.
 *
 * @property arazzo **REQUIRED**. The semantic version number of the Arazzo Specification (e.g.
 *   `"1.1.0"`).
 * @property self Optional canonical base URI (`$self`) for resolving relative references.
 * @property info **REQUIRED**. Metadata about the Arazzo workflows contained in the document.
 * @property sourceDescriptions **REQUIRED**. Non-empty list of source descriptions (OpenAPI,
 *   AsyncAPI, or Arazzo specs) referenced by workflows.
 * @property workflows **REQUIRED**. Non-empty list of workflows defined in the Arazzo document.
 * @property components Optional container for reusable inputs, parameters, success actions, and
 *   failure actions.
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class ArazzoDefinition(
    val arazzo: String = "1.1.0",
    val self: String? = null,
    val info: ArazzoInfo,
    val sourceDescriptions: List<SourceDescription> = emptyList(),
    val workflows: List<Workflow> = emptyList(),
    val components: ArazzoComponents? = null,
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Metadata about the Arazzo specification and its workflows.
 *
 * @property title **REQUIRED**. A human-readable title of the Arazzo document.
 * @property summary A short summary of the Arazzo document.
 * @property description A description of the purpose of the workflows (CommonMark allowed).
 * @property version **REQUIRED**. The version of the Arazzo document.
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class ArazzoInfo(
    val title: String,
    val summary: String? = null,
    val description: String? = null,
    val version: String,
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Supported specification protocol types for a [SourceDescription].
 *
 * @property wireName The canonical lowercase wire representation (`openapi`, `asyncapi`, `arazzo`).
 */
enum class SourceDescriptionType(val wireName: String) {
  /** An OpenAPI 3.x or Swagger 2.0 specification document. */
  OPENAPI("openapi"),

  /** An AsyncAPI specification document (added in Arazzo 1.1.0). */
  ASYNCAPI("asyncapi"),

  /** Another Arazzo specification document. */
  ARAZZO("arazzo");

  /** Companion lookup helpers for [SourceDescriptionType]. */
  companion object {
    /**
     * Parses a wire name into a [SourceDescriptionType] wrapped in a [Result].
     *
     * @param raw The wire string to parse.
     * @return A [Result] containing the matching [SourceDescriptionType] or a
     *   [CddError.ArazzoParseError].
     */
    fun fromWireName(raw: String): Result<SourceDescriptionType> =
        when (raw.lowercase()) {
          "openapi" -> Result.success(OPENAPI)
          "asyncapi" -> Result.success(ASYNCAPI)
          "arazzo" -> Result.success(ARAZZO)
          else -> CddError.ArazzoParseError("Unknown SourceDescription type: '$raw'").toFailure()
        }
  }
}

/**
 * Describes a source specification (OpenAPI, AsyncAPI, or Arazzo) referenced by one or more
 * workflows.
 *
 * @property name **REQUIRED**. Unique name identifying the source description (`[A-Za-z0-9_\-]+`).
 * @property url **REQUIRED**. URI or relative reference locating the source description document.
 * @property type Optional specification type (`openapi`, `asyncapi`, or `arazzo`).
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class SourceDescription(
    val name: String,
    val url: String,
    val type: SourceDescriptionType? = null,
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Reference object pointing to a reusable component in [ArazzoComponents].
 *
 * @property reference **REQUIRED**. Runtime expression identifying the component (e.g.
 *   `"$components.parameters.page"`).
 * @property value Optional overridden value when referencing a reusable parameter.
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class ReusableObject(
    val reference: String,
    val value: OpenApiValue? = null,
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Discriminated union representing either an inline domain object [T] or a [ReusableObject]
 * reference.
 *
 * @param T The inline object type ([ArazzoParameter], [SuccessActionObject], or
 *   [FailureActionObject]).
 */
sealed interface ReusableOr<out T> {
  /**
   * An inline definition of [T].
   *
   * @param T The inline value type.
   * @property value The inline domain object.
   */
  data class Inline<out T>(val value: T) : ReusableOr<T>

  /**
   * A reference to a component in [ArazzoComponents].
   *
   * @property reusable The [ReusableObject] pointer.
   */
  data class Reference(val reusable: ReusableObject) : ReusableOr<Nothing>
}

/**
 * Supported parameter locations in an Arazzo [ArazzoParameter] object.
 *
 * @property wireName The canonical lowercase wire representation (`path`, `query`, `querystring`,
 *   `header`, `cookie`).
 */
enum class ArazzoParameterLocation(val wireName: String) {
  /** URL path template parameter. */
  PATH("path"),

  /** Individual URL query parameter. */
  QUERY("query"),

  /** Entire raw query string parameter (added in Arazzo 1.1.0 / OpenAPI 3.2.0). */
  QUERYSTRING("querystring"),

  /** HTTP request header parameter. */
  HEADER("header"),

  /** HTTP Cookie parameter. */
  COOKIE("cookie");

  /** Companion lookup helpers for [ArazzoParameterLocation]. */
  companion object {
    /**
     * Parses a wire string into an [ArazzoParameterLocation] wrapped in a [Result].
     *
     * @param raw The wire string to parse.
     * @return A [Result] containing the matching [ArazzoParameterLocation] or a
     *   [CddError.ArazzoParseError].
     */
    fun fromWireName(raw: String): Result<ArazzoParameterLocation> =
        when (raw.lowercase()) {
          "path" -> Result.success(PATH)
          "query" -> Result.success(QUERY)
          "querystring" -> Result.success(QUERYSTRING)
          "header" -> Result.success(HEADER)
          "cookie" -> Result.success(COOKIE)
          else -> CddError.ArazzoParseError("Invalid parameter location: '$raw'").toFailure()
        }
  }
}

/**
 * Describes a single operation or sub-workflow parameter binding.
 *
 * @property name **REQUIRED**. The name of the parameter.
 * @property location The parameter location (`in` field); required for operation steps, omitted for
 *   workflow steps.
 * @property value **REQUIRED**. Constant literal or runtime expression string passed as the
 *   parameter value.
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class ArazzoParameter(
    val name: String,
    val location: ArazzoParameterLocation? = null,
    val value: OpenApiValue,
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Supported AsyncAPI message action intents for a [StepObject] in Arazzo 1.1.0.
 *
 * @property wireName The canonical lowercase wire representation (`send` or `receive`).
 */
enum class StepActionType(val wireName: String) {
  /** Publish / send a message to the target AsyncAPI channel. */
  SEND("send"),

  /** Subscribe / receive a message from the target AsyncAPI channel. */
  RECEIVE("receive");

  /** Companion lookup helpers for [StepActionType]. */
  companion object {
    /**
     * Parses a wire string into a [StepActionType] wrapped in a [Result].
     *
     * @param raw The wire string to parse.
     * @return A [Result] containing the matching [StepActionType] or a [CddError.ArazzoParseError].
     */
    fun fromWireName(raw: String): Result<StepActionType> =
        when (raw.lowercase()) {
          "send" -> Result.success(SEND)
          "receive" -> Result.success(RECEIVE)
          else -> CddError.ArazzoParseError("Invalid step action: '$raw'").toFailure()
        }
  }
}

/**
 * Supported condition and selector evaluation engines in Arazzo 1.1.0.
 *
 * @property wireName The canonical lowercase wire representation (`simple`, `regex`, `jsonpath`,
 *   `xpath`, `jsonpointer`).
 */
enum class CriterionEngineType(val wireName: String) {
  /** Simple comparison and logical operators (`==`, `!=`, `<`, `<=`, `>`, `>=`, `&&`, `||`). */
  SIMPLE("simple"),

  /** Regular expression matching. */
  REGEX("regex"),

  /** JSONPath query evaluation (`rfc9535` or `draft-goessner-dispatch-jsonpath-00`). */
  JSONPATH("jsonpath"),

  /** XPath query evaluation (`xpath-31`, `xpath-20`, or `xpath-10`). */
  XPATH("xpath"),

  /** RFC 6901 JSON Pointer evaluation (`rfc6901`). */
  JSONPOINTER("jsonpointer");

  /** Companion lookup helpers for [CriterionEngineType]. */
  companion object {
    /**
     * Parses a wire string into a [CriterionEngineType] wrapped in a [Result].
     *
     * @param raw The wire string to parse.
     * @return A [Result] containing the matching [CriterionEngineType] or a
     *   [CddError.ArazzoParseError].
     */
    fun fromWireName(raw: String): Result<CriterionEngineType> =
        when (raw.lowercase()) {
          "simple" -> Result.success(SIMPLE)
          "regex" -> Result.success(REGEX)
          "jsonpath" -> Result.success(JSONPATH)
          "xpath" -> Result.success(XPATH)
          "jsonpointer" -> Result.success(JSONPOINTER)
          else -> CddError.ArazzoParseError("Invalid criterion engine type: '$raw'").toFailure()
        }
  }
}

/**
 * Explicit expression dialect and standard version specification (Criterion Expression Type
 * Object).
 *
 * @property type **REQUIRED**. The expression engine type (`jsonpath`, `xpath`, or `jsonpointer`).
 * @property version **REQUIRED**. The dialect version string (e.g. `"rfc9535"`, `"xpath-31"`,
 *   `"rfc6901"`).
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class ExpressionTypeObject(
    val type: CriterionEngineType,
    val version: String,
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Union representing either a simple [CriterionEngineType] string or a versioned
 * [ExpressionTypeObject].
 */
sealed interface CriterionTypeOrExpression {
  /**
   * Unversioned engine identifier (e.g. `"simple"`, `"regex"`, `"jsonpath"`).
   *
   * @property type The [CriterionEngineType].
   */
  data class Engine(val type: CriterionEngineType) : CriterionTypeOrExpression

  /**
   * Versioned expression dialect specification.
   *
   * @property expressionType The [ExpressionTypeObject].
   */
  data class Versioned(val expressionType: ExpressionTypeObject) : CriterionTypeOrExpression
}

/**
 * Assertion condition evaluated against step execution state.
 *
 * @property context Optional runtime expression defining the evaluation root (e.g. `"$statusCode"`,
 *   `"$response.body"`).
 * @property condition **REQUIRED**. Condition predicate or query expression to evaluate.
 * @property type Optional engine type or versioned [ExpressionTypeObject] (defaults to `simple`).
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class CriterionObject(
    val context: String? = null,
    val condition: String,
    val type: CriterionTypeOrExpression? = null,
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Arazzo 1.1.0 Selector Object for extracting structured data into step or workflow outputs.
 *
 * @property context **REQUIRED**. Source data context runtime expression (e.g. `"$response.body"`,
 *   `"$message.payload"`).
 * @property selector **REQUIRED**. Query expression string (e.g. `"$.items[0].id"`, `"/id"`).
 * @property type Optional selector dialect (`jsonpath`, `xpath`, `jsonpointer`) or versioned
 *   [ExpressionTypeObject].
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class SelectorObject(
    val context: String,
    val selector: String,
    val type: CriterionTypeOrExpression? = null,
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Union representing a step or workflow output definition: either a runtime expression string or a
 * [SelectorObject].
 */
sealed interface OutputValue {
  /**
   * Direct runtime expression string (e.g. `"$response.body#/id"`).
   *
   * @property expression The runtime expression string.
   */
  data class Expression(val expression: String) : OutputValue

  /**
   * Structured Arazzo 1.1.0 [SelectorObject] query.
   *
   * @property selector The [SelectorObject] specification.
   */
  data class Selector(val selector: SelectorObject) : OutputValue
}

/**
 * Describes an in-place replacement applied to a [ArazzoRequestBody] payload prior to transmission.
 *
 * @property target **REQUIRED**. Location pointer (JSON Pointer, JSONPath, or XPath) within the
 *   payload.
 * @property targetSelectorType Optional Arazzo 1.1.0 dialect specification for [target].
 * @property value **REQUIRED**. Runtime expression or literal value to inject at [target].
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class PayloadReplacementObject(
    val target: String,
    val targetSelectorType: CriterionTypeOrExpression? = null,
    val value: OpenApiValue,
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Dynamic request body definition for a workflow [StepObject].
 *
 * @property contentType Optional MIME Content-Type override (e.g. `"application/json"`).
 * @property payload Base payload template ([OpenApiObject], [OpenApiArray], primitive, or runtime
 *   expression string).
 * @property replacements Ordered list of targeted in-place replacements applied to [payload].
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class ArazzoRequestBody(
    val contentType: String? = null,
    val payload: OpenApiValue? = null,
    val replacements: List<PayloadReplacementObject> = emptyList(),
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Flow transition types for a [SuccessActionObject].
 *
 * @property wireName The canonical lowercase wire representation (`end` or `goto`).
 */
enum class SuccessActionType(val wireName: String) {
  /** Terminate the workflow immediately and return current outputs. */
  END("end"),

  /** Branch to another step in the current workflow or chain to another workflow. */
  GOTO("goto");

  /** Companion lookup helpers for [SuccessActionType]. */
  companion object {
    /**
     * Parses a wire string into a [SuccessActionType] wrapped in a [Result].
     *
     * @param raw The wire string to parse.
     * @return A [Result] containing the matching [SuccessActionType] or a
     *   [CddError.ArazzoParseError].
     */
    fun fromWireName(raw: String): Result<SuccessActionType> =
        when (raw.lowercase()) {
          "end" -> Result.success(END)
          "goto" -> Result.success(GOTO)
          else -> CddError.ArazzoParseError("Invalid success action type: '$raw'").toFailure()
        }
  }
}

/**
 * Defines a state transition taken when a step's `successCriteria` pass.
 *
 * @property name **REQUIRED**. Unique name of the success action within its enclosing scope.
 * @property type **REQUIRED**. Transition type (`end` or `goto`).
 * @property workflowId Target workflow identifier when [type] is [SuccessActionType.GOTO] and
 *   [stepId] is null.
 * @property stepId Target step identifier within the current workflow when [type] is
 *   [SuccessActionType.GOTO] and [workflowId] is null.
 * @property parameters Parameters forwarded to the target workflow or step.
 * @property criteria Guard conditions that must hold for this action to trigger.
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class SuccessActionObject(
    val name: String,
    val type: SuccessActionType,
    val workflowId: String? = null,
    val stepId: String? = null,
    val parameters: List<ReusableOr<ArazzoParameter>> = emptyList(),
    val criteria: List<CriterionObject> = emptyList(),
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Flow transition and recovery types for a [FailureActionObject].
 *
 * @property wireName The canonical lowercase wire representation (`end`, `retry`, or `goto`).
 */
enum class FailureActionType(val wireName: String) {
  /** Terminate the workflow with an error. */
  END("end"),

  /** Retry the failed step (optionally after executing a recovery step/workflow and waiting). */
  RETRY("retry"),

  /** Branch to a fallback step or recovery workflow without retrying the current step. */
  GOTO("goto");

  /** Companion lookup helpers for [FailureActionType]. */
  companion object {
    /**
     * Parses a wire string into a [FailureActionType] wrapped in a [Result].
     *
     * @param raw The wire string to parse.
     * @return A [Result] containing the matching [FailureActionType] or a
     *   [CddError.ArazzoParseError].
     */
    fun fromWireName(raw: String): Result<FailureActionType> =
        when (raw.lowercase()) {
          "end" -> Result.success(END)
          "retry" -> Result.success(RETRY)
          "goto" -> Result.success(GOTO)
          else -> CddError.ArazzoParseError("Invalid failure action type: '$raw'").toFailure()
        }
  }
}

/**
 * Defines an error recovery, retry, or fallback transition when a step fails.
 *
 * @property name **REQUIRED**. Unique name of the failure action within its enclosing scope.
 * @property type **REQUIRED**. Recovery action type (`end`, `retry`, or `goto`).
 * @property workflowId Target workflow identifier for `goto` or pre-retry recovery workflow.
 * @property stepId Target step identifier for `goto` or pre-retry recovery step.
 * @property parameters Parameters forwarded to the target recovery workflow or step.
 * @property retryAfter Non-negative delay in seconds before retrying the step.
 * @property retryLimit Non-negative maximum number of retry attempts allowed.
 * @property criteria Guard conditions matching specific failure modes (e.g. `$statusCode == 429`).
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class FailureActionObject(
    val name: String,
    val type: FailureActionType,
    val workflowId: String? = null,
    val stepId: String? = null,
    val parameters: List<ReusableOr<ArazzoParameter>> = emptyList(),
    val retryAfter: Double? = null,
    val retryLimit: Int? = null,
    val criteria: List<CriterionObject> = emptyList(),
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Describes a single workflow execution step invoking an OpenAPI operation, AsyncAPI channel, or
 * nested workflow.
 *
 * @property stepId **REQUIRED**. Unique identifier for the step within its enclosing [Workflow].
 * @property description Optional description of what the step accomplishes.
 * @property operationId Target OpenAPI `operationId` (e.g. `"createPet"` or
 *   `"$sourceDescriptions.petstore.createPet"`).
 * @property operationPath Target OpenAPI operation JSON Pointer path (e.g.
 *   `"{$sourceDescriptions.petstore.url}#/paths/~1pets/post"`).
 * @property channelPath Target AsyncAPI channel path (added in Arazzo 1.1.0).
 * @property workflowId Target sub-workflow identifier (e.g. `"loginWorkflow"`).
 * @property parameters Parameters bound to this step's invocation.
 * @property requestBody Request body payload and replacements for this step.
 * @property successCriteria Assertions evaluated to determine whether the step succeeded.
 * @property onSuccess Step-specific success transition actions overriding workflow defaults.
 * @property onFailure Step-specific failure/retry actions overriding workflow defaults.
 * @property outputs Named values extracted from the step's execution into workflow context.
 * @property timeout Optional per-step execution timeout in milliseconds (Arazzo 1.1.0).
 * @property correlationId Optional AsyncAPI message correlation expression/token (Arazzo 1.1.0).
 * @property action Optional AsyncAPI message action intent (`send` or `receive`, Arazzo 1.1.0).
 * @property dependsOn Optional list of prerequisite `stepId`s in the same workflow (Arazzo 1.1.0).
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class StepObject(
    val stepId: String,
    val description: String? = null,
    val operationId: String? = null,
    val operationPath: String? = null,
    val channelPath: String? = null,
    val workflowId: String? = null,
    val parameters: List<ReusableOr<ArazzoParameter>> = emptyList(),
    val requestBody: ArazzoRequestBody? = null,
    val successCriteria: List<CriterionObject> = emptyList(),
    val onSuccess: List<ReusableOr<SuccessActionObject>> = emptyList(),
    val onFailure: List<ReusableOr<FailureActionObject>> = emptyList(),
    val outputs: Map<String, OutputValue> = emptyMap(),
    val timeout: Long? = null,
    val correlationId: String? = null,
    val action: StepActionType? = null,
    val dependsOn: List<String> = emptyList(),
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Describes an orchestrated sequence or DAG of API steps achieving a business objective.
 *
 * @property workflowId **REQUIRED**. Unique identifier for the workflow across the Arazzo document.
 * @property summary Short summary of what the workflow does.
 * @property description Detailed CommonMark description of the workflow.
 * @property inputs Optional JSON Schema 2020-12 object defining the workflow's input parameters.
 * @property dependsOn Optional list of prerequisite `workflowId`s that must execute before this
 *   workflow.
 * @property steps **REQUIRED**. Non-empty ordered list of [StepObject]s executed by the workflow.
 * @property successActions Default success actions applied to steps in this workflow.
 * @property failureActions Default failure actions applied to steps in this workflow.
 * @property outputs Named output values produced by the workflow upon completion.
 * @property parameters Default parameters injected into all steps in this workflow.
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class Workflow(
    val workflowId: String,
    val summary: String? = null,
    val description: String? = null,
    val inputs: SchemaProperty? = null,
    val dependsOn: List<String> = emptyList(),
    val steps: List<StepObject> = emptyList(),
    val successActions: List<ReusableOr<SuccessActionObject>> = emptyList(),
    val failureActions: List<ReusableOr<FailureActionObject>> = emptyList(),
    val outputs: Map<String, OutputValue> = emptyMap(),
    val parameters: List<ReusableOr<ArazzoParameter>> = emptyList(),
    val extensions: Map<String, OpenApiValue> = emptyMap()
)

/**
 * Container for reusable schemas, parameters, and action handlers within an [ArazzoDefinition].
 *
 * @property inputs Reusable JSON Schema 2020-12 input definitions keyed by component name.
 * @property parameters Reusable [ArazzoParameter] definitions keyed by component name.
 * @property successActions Reusable [SuccessActionObject] definitions keyed by component name.
 * @property failureActions Reusable [FailureActionObject] definitions keyed by component name.
 * @property extensions Specification extensions (keys starting with `x-`).
 */
data class ArazzoComponents(
    val inputs: Map<String, SchemaProperty> = emptyMap(),
    val parameters: Map<String, ArazzoParameter> = emptyMap(),
    val successActions: Map<String, SuccessActionObject> = emptyMap(),
    val failureActions: Map<String, FailureActionObject> = emptyMap(),
    val extensions: Map<String, OpenApiValue> = emptyMap()
)
