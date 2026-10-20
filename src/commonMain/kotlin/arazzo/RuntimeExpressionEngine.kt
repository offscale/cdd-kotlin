/**
 * ABNF Runtime Expression parser, AST, and evaluator for Arazzo 1.1.0 and OpenAPI 3.2.0.
 *
 * Supports all 17 Arazzo 1.1.0 runtime expression roots (`$url`, `$method`, `$statusCode`,
 * `$request.header.*`, `$request.query.*`, `$request.path.*`, `$request.body#*`,
 * `$response.header.*`, `$response.body#*`, `$message.header.*`, `$message.payload#*`, `$inputs.*`,
 * `$outputs.*`, `$steps.*`, `$workflows.*`, `$sourceDescriptions.*`, `$components.*`, `$self`) and
 * `{...}` string template interpolations with full [kotlin.Result] percolation.
 */
package arazzo

import domain.ArazzoComponents
import domain.CddError
import domain.OpenApiArray
import domain.OpenApiBoolean
import domain.OpenApiInt
import domain.OpenApiNull
import domain.OpenApiNumber
import domain.OpenApiObject
import domain.OpenApiString
import domain.OpenApiValue
import domain.toFailure

/**
 * Strongly-typed AST representing a parsed Arazzo 1.1.0 runtime expression or embedded template.
 */
sealed interface RuntimeExpressionAst {
  /** Represents `$url`. */
  data object Url : RuntimeExpressionAst

  /** Represents `$method`. */
  data object Method : RuntimeExpressionAst

  /** Represents `$statusCode`. */
  data object StatusCode : RuntimeExpressionAst

  /** Represents `$self`. */
  data object Self : RuntimeExpressionAst

  /**
   * Represents `$request.header.<name>`.
   *
   * @property name Case-insensitive HTTP request header name.
   */
  data class RequestHeader(val name: String) : RuntimeExpressionAst

  /**
   * Represents `$request.query.<name>`.
   *
   * @property name HTTP query parameter name.
   */
  data class RequestQuery(val name: String) : RuntimeExpressionAst

  /**
   * Represents `$request.path.<name>`.
   *
   * @property name URL path parameter name.
   */
  data class RequestPath(val name: String) : RuntimeExpressionAst

  /**
   * Represents `$request.body` or `$request.body#<json-pointer>`.
   *
   * @property pointer Optional RFC 6901 JSON Pointer.
   */
  data class RequestBody(val pointer: String? = null) : RuntimeExpressionAst

  /**
   * Represents `$response.header.<name>`.
   *
   * @property name Case-insensitive HTTP response header name.
   */
  data class ResponseHeader(val name: String) : RuntimeExpressionAst

  /**
   * Represents `$response.query.<name>` (OpenAPI 3.2 runtime expression).
   *
   * @property name HTTP query parameter name.
   */
  data class ResponseQuery(val name: String) : RuntimeExpressionAst

  /**
   * Represents `$response.path.<name>` (OpenAPI 3.2 runtime expression).
   *
   * @property name URL path parameter name.
   */
  data class ResponsePath(val name: String) : RuntimeExpressionAst

  /**
   * Represents `$response.body` or `$response.body#<json-pointer>`.
   *
   * @property pointer Optional RFC 6901 JSON Pointer.
   */
  data class ResponseBody(val pointer: String? = null) : RuntimeExpressionAst

  /**
   * Represents `$message.header.<name>` (Arazzo 1.1.0 AsyncAPI message header).
   *
   * @property name Message header name.
   */
  data class MessageHeader(val name: String) : RuntimeExpressionAst

  /**
   * Represents `$message.payload` or `$message.payload#<json-pointer>` (Arazzo 1.1.0 AsyncAPI
   * message payload).
   *
   * @property pointer Optional RFC 6901 JSON Pointer.
   */
  data class MessagePayload(val pointer: String? = null) : RuntimeExpressionAst

  /**
   * Represents `$inputs.<name>` with an optional JSON Pointer or dotted sub-path.
   *
   * @property name Workflow input parameter name.
   * @property pointer Optional JSON Pointer or property path.
   */
  data class Inputs(val name: String, val pointer: String? = null) : RuntimeExpressionAst

  /**
   * Represents `$outputs.<name>` with an optional JSON Pointer or dotted sub-path.
   *
   * @property name Workflow output parameter name.
   * @property pointer Optional JSON Pointer or property path.
   */
  data class Outputs(val name: String, val pointer: String? = null) : RuntimeExpressionAst

  /**
   * Represents `$steps.<stepId>.outputs.<name>` (or step request/response property access).
   *
   * @property stepId Identifier of the step in the current workflow.
   * @property category Step sub-scope (typically `"outputs"`).
   * @property name Output key name within the step.
   * @property pointer Optional JSON Pointer or dotted path into the step output value.
   */
  data class Steps(
      val stepId: String,
      val category: String,
      val name: String,
      val pointer: String? = null
  ) : RuntimeExpressionAst

  /**
   * Represents `$workflows.<workflowId>.outputs.<name>` (or workflow inputs access).
   *
   * @property workflowId Identifier of the referenced workflow.
   * @property category Workflow sub-scope (`"outputs"` or `"inputs"`).
   * @property name Output or input key name within the referenced workflow.
   * @property pointer Optional JSON Pointer or dotted path into the value.
   */
  data class Workflows(
      val workflowId: String,
      val category: String,
      val name: String,
      val pointer: String? = null
  ) : RuntimeExpressionAst

  /**
   * Represents `$sourceDescriptions.<name>.url` or `$sourceDescriptions.<name>.<operationId>`.
   *
   * @property sourceName Identifier of the [domain.SourceDescription].
   * @property target Target member (e.g. `"url"`, `"workflows.<id>"`, or an `operationId`).
   */
  data class SourceDescriptions(val sourceName: String, val target: String) : RuntimeExpressionAst

  /**
   * Represents `$components.<category>.<name>`.
   *
   * @property category Component category (`"inputs"`, `"parameters"`, `"successActions"`,
   *   `"failureActions"`).
   * @property name Component key name.
   */
  data class Components(val category: String, val name: String) : RuntimeExpressionAst

  /**
   * Represents a string template containing literal text and `{...}` embedded runtime expressions.
   *
   * @property segments Ordered list of [TemplateSegment]s.
   */
  data class Template(val segments: List<TemplateSegment>) : RuntimeExpressionAst
}

/** Segment within a [RuntimeExpressionAst.Template]. */
sealed interface TemplateSegment {
  /**
   * Static literal string segment.
   *
   * @property text Literal characters.
   */
  data class Literal(val text: String) : TemplateSegment

  /**
   * Embedded `{...}` runtime expression segment.
   *
   * @property expression Parsed [RuntimeExpressionAst] inside `{...}`.
   */
  data class Expression(val expression: RuntimeExpressionAst) : TemplateSegment
}

/**
 * Snapshot of an HTTP request executed during a workflow step.
 *
 * @property url Request URL string.
 * @property method HTTP method verb (e.g. `"GET"`, `"POST"`).
 * @property headers Request headers keyed by header name.
 * @property query Query parameters keyed by parameter name.
 * @property path Path parameters keyed by parameter name.
 * @property body Optional serialized request body as an [OpenApiValue].
 */
data class StepRequestSnapshot(
    val url: String = "",
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    val path: Map<String, String> = emptyMap(),
    val body: OpenApiValue? = null
)

/**
 * Snapshot of an HTTP response received during a workflow step.
 *
 * @property statusCode HTTP status code (e.g. `200`, `201`, `404`).
 * @property headers Response headers keyed by header name.
 * @property body Optional parsed response body as an [OpenApiValue].
 */
data class StepResponseSnapshot(
    val statusCode: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: OpenApiValue? = null
)

/**
 * Snapshot of an AsyncAPI event message sent or received during a workflow step.
 *
 * @property headers Message headers keyed by header name.
 * @property payload Optional message payload as an [OpenApiValue].
 * @property correlationId Optional message correlation identifier.
 * @property action Optional message action (`"send"` or `"receive"`).
 */
data class StepMessageSnapshot(
    val headers: Map<String, String> = emptyMap(),
    val payload: OpenApiValue? = null,
    val correlationId: String? = null,
    val action: String? = null
)

/**
 * Mutable runtime state container used while evaluating Arazzo workflow steps, criteria, selectors,
 * and outputs.
 *
 * @property selfUri Optional `$self` URI of the active Arazzo document.
 * @property inputs Workflow input values keyed by input property name.
 * @property workflowOutputs Accumulated workflow output values keyed by output name.
 * @property stepOutputs Map of `stepId` to that step's extracted output map.
 * @property externalWorkflowOutputs Map of `workflowId` to that workflow's output map.
 * @property externalWorkflowInputs Map of `workflowId` to that workflow's input map.
 * @property sourceUrls Map of `sourceDescription.name` to its resolved `url`.
 * @property components Optional reusable [ArazzoComponents] registry.
 * @property currentRequest Most recent [StepRequestSnapshot] for `$request.*`, `$url`, `$method`.
 * @property currentResponse Most recent [StepResponseSnapshot] for `$response.*` and `$statusCode`.
 * @property currentMessage Most recent [StepMessageSnapshot] for `$message.*`.
 */
data class WorkflowExecutionContext(
    val selfUri: String? = null,
    val inputs: Map<String, OpenApiValue> = emptyMap(),
    val workflowOutputs: MutableMap<String, OpenApiValue> = LinkedHashMap(),
    val stepOutputs: MutableMap<String, Map<String, OpenApiValue>> = LinkedHashMap(),
    val externalWorkflowOutputs: MutableMap<String, Map<String, OpenApiValue>> = LinkedHashMap(),
    val externalWorkflowInputs: MutableMap<String, Map<String, OpenApiValue>> = LinkedHashMap(),
    val sourceUrls: Map<String, String> = emptyMap(),
    val components: ArazzoComponents? = null,
    var currentRequest: StepRequestSnapshot? = null,
    var currentResponse: StepResponseSnapshot? = null,
    var currentMessage: StepMessageSnapshot? = null
)

/** Parser and evaluator for Arazzo 1.1.0 runtime expressions and RFC 6901 JSON Pointers. */
object RuntimeExpressionEngine {
  private val validCategories = setOf("inputs", "parameters", "successActions", "failureActions")

  /**
   * Returns `true` if [raw] is a standalone `$` runtime expression or contains `{...}` expression
   * interpolations.
   *
   * @param raw Candidate string to inspect.
   * @return `true` if [raw] is or contains a runtime expression.
   */
  fun isOrContainsExpression(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.startsWith("\$")) return true
    val openIdx = trimmed.indexOf("{\$")
    return openIdx >= 0 && trimmed.contains('}')
  }

  /**
   * Validates whether [pointer] conforms to RFC 6901 JSON Pointer syntax (`""` or `"/..."` with
   * valid `~0` and `~1` escape sequences).
   *
   * @param pointer Candidate JSON Pointer string (without leading `#`).
   * @return `true` if [pointer] is syntactically valid per RFC 6901.
   */
  fun isValidJsonPointerSyntax(pointer: String): Boolean {
    if (pointer.isEmpty()) return true
    if (!pointer.startsWith("/")) return false
    var i = 1
    while (i < pointer.length) {
      if (pointer[i] == '~') {
        val next = pointer.getOrNull(i + 1)
        if (next != '0' && next != '1') return false
        i += 2
      } else {
        i += 1
      }
    }
    return true
  }

  /**
   * Validates whether [expression] is a valid OpenAPI 3.2 runtime expression (`$url`, `$method`,
   * `$statusCode`, `$request.header.<token>`, `$request.query.<name>`, `$request.path.<name>`,
   * `$request.body[#<pointer>]`, `$response.header.<token>`, `$response.query.<name>`,
   * `$response.path.<name>`, or `$response.body[#<pointer>]`) using the unified
   * [RuntimeExpressionAst] parser.
   *
   * @param expression Raw runtime expression string.
   * @param headerTokenValidator Predicate validating HTTP header token characters.
   * @return `true` if [expression] is a valid OpenAPI 3.2 runtime expression.
   */
  fun isValidOpenApiRuntimeExpression(
      expression: String,
      headerTokenValidator: (String) -> Boolean
  ): Boolean {
    val ast = parseSingleDollarExpression(expression).getOrNull() ?: return false
    return when (ast) {
      RuntimeExpressionAst.Url,
      RuntimeExpressionAst.Method,
      RuntimeExpressionAst.StatusCode,
      is RuntimeExpressionAst.RequestQuery,
      is RuntimeExpressionAst.RequestPath,
      is RuntimeExpressionAst.ResponseQuery,
      is RuntimeExpressionAst.ResponsePath,
      is RuntimeExpressionAst.RequestBody,
      is RuntimeExpressionAst.ResponseBody -> true
      is RuntimeExpressionAst.RequestHeader -> headerTokenValidator(ast.name)
      is RuntimeExpressionAst.ResponseHeader -> headerTokenValidator(ast.name)
      else -> false
    }
  }

  /**
   * Parses a raw Arazzo runtime expression or embedded `{...}` string template into a
   * [RuntimeExpressionAst] wrapped in a [Result].
   *
   * @param expression The raw expression or template string.
   * @return A [Result] containing the parsed [RuntimeExpressionAst] or a
   *   [CddError.RuntimeExpressionError].
   */
  fun parseExpression(expression: String): Result<RuntimeExpressionAst> {
    val trimmed = expression.trim()
    if (trimmed.isEmpty()) {
      return CddError.RuntimeExpressionError(expression, "Expression cannot be empty").toFailure()
    }
    if (!trimmed.startsWith("\$")) {
      if (trimmed.contains("{") && trimmed.contains("}")) {
        return parseTemplate(expression)
      }
      return CddError.RuntimeExpressionError(
              expression, "Runtime expression must start with '\$' or contain '{...}' template")
          .toFailure()
    }
    if (trimmed.contains("{")) {
      return parseTemplate(expression)
    }
    return parseSingleDollarExpression(trimmed)
  }

  private fun parseTemplate(raw: String): Result<RuntimeExpressionAst> {
    val segments = mutableListOf<TemplateSegment>()
    var i = 0
    val literalBuf = StringBuilder()
    while (i < raw.length) {
      val ch = raw[i]
      if (ch == '{') {
        val closeIdx = raw.indexOf('}', i + 1)
        if (closeIdx < 0) {
          return CddError.RuntimeExpressionError(raw, "Unclosed '{' in template expression")
              .toFailure()
        }
        val inner = raw.substring(i + 1, closeIdx).trim()
        if (inner.startsWith("\$")) {
          if (literalBuf.isNotEmpty()) {
            segments.add(TemplateSegment.Literal(literalBuf.toString()))
            literalBuf.clear()
          }
          val innerAst =
              parseSingleDollarExpression(inner).getOrElse {
                return Result.failure(it)
              }
          segments.add(TemplateSegment.Expression(innerAst))
          i = closeIdx + 1
          continue
        }
      }
      literalBuf.append(ch)
      i++
    }
    if (literalBuf.isNotEmpty()) {
      segments.add(TemplateSegment.Literal(literalBuf.toString()))
    }
    if (segments.none { it is TemplateSegment.Expression }) {
      return CddError.RuntimeExpressionError(raw, "Template contains no valid {\$...} expressions")
          .toFailure()
    }
    return Result.success(RuntimeExpressionAst.Template(segments))
  }

  private fun parseSingleDollarExpression(expr: String): Result<RuntimeExpressionAst> {
    return when {
      expr == "\$url" -> Result.success(RuntimeExpressionAst.Url)
      expr == "\$method" -> Result.success(RuntimeExpressionAst.Method)
      expr == "\$statusCode" -> Result.success(RuntimeExpressionAst.StatusCode)
      expr == "\$self" -> Result.success(RuntimeExpressionAst.Self)
      expr.startsWith("\$request.") -> parseRequestExpression(expr)
      expr.startsWith("\$response.") -> parseResponseExpression(expr)
      expr.startsWith("\$message.") -> parseMessageExpression(expr)
      expr.startsWith("\$inputs.") -> parseInputsExpression(expr)
      expr.startsWith("\$outputs.") -> parseOutputsExpression(expr)
      expr.startsWith("\$steps.") -> parseStepsExpression(expr)
      expr.startsWith("\$workflows.") -> parseWorkflowsExpression(expr)
      expr.startsWith("\$sourceDescriptions.") -> parseSourceDescriptionsExpression(expr)
      expr.startsWith("\$components.") -> parseComponentsExpression(expr)
      else ->
          CddError.RuntimeExpressionError(expr, "Unrecognized runtime expression root").toFailure()
    }
  }

  private fun parseRequestExpression(expr: String): Result<RuntimeExpressionAst> {
    val rest = expr.removePrefix("\$request.")
    return when {
      rest.startsWith("header.") -> {
        val name = rest.removePrefix("header.")
        if (name.isBlank()) {
          CddError.RuntimeExpressionError(expr, "Missing header name in \$request.header")
              .toFailure()
        } else {
          Result.success(RuntimeExpressionAst.RequestHeader(name))
        }
      }
      rest.startsWith("query.") -> {
        val name = rest.removePrefix("query.")
        if (name.isBlank()) {
          CddError.RuntimeExpressionError(expr, "Missing query parameter name in \$request.query")
              .toFailure()
        } else {
          Result.success(RuntimeExpressionAst.RequestQuery(name))
        }
      }
      rest.startsWith("path.") -> {
        val name = rest.removePrefix("path.")
        if (name.isBlank()) {
          CddError.RuntimeExpressionError(expr, "Missing path parameter name in \$request.path")
              .toFailure()
        } else {
          Result.success(RuntimeExpressionAst.RequestPath(name))
        }
      }
      rest == "body" || rest == "body#" -> Result.success(RuntimeExpressionAst.RequestBody(null))
      rest.startsWith("body#") -> {
        val pointer = rest.removePrefix("body#")
        if (!isValidJsonPointerSyntax(pointer)) {
          CddError.RuntimeExpressionError(expr, "Invalid RFC 6901 JSON Pointer after #").toFailure()
        } else {
          Result.success(RuntimeExpressionAst.RequestBody(pointer))
        }
      }
      else ->
          CddError.RuntimeExpressionError(
                  expr, "Invalid \$request source (expected header, query, path, or body)")
              .toFailure()
    }
  }

  private fun parseResponseExpression(expr: String): Result<RuntimeExpressionAst> {
    val rest = expr.removePrefix("\$response.")
    return when {
      rest.startsWith("header.") -> {
        val name = rest.removePrefix("header.")
        if (name.isBlank()) {
          CddError.RuntimeExpressionError(expr, "Missing header name in \$response.header")
              .toFailure()
        } else {
          Result.success(RuntimeExpressionAst.ResponseHeader(name))
        }
      }
      rest.startsWith("query.") -> {
        val name = rest.removePrefix("query.")
        if (name.isBlank()) {
          CddError.RuntimeExpressionError(expr, "Missing query parameter name in \$response.query")
              .toFailure()
        } else {
          Result.success(RuntimeExpressionAst.ResponseQuery(name))
        }
      }
      rest.startsWith("path.") -> {
        val name = rest.removePrefix("path.")
        if (name.isBlank()) {
          CddError.RuntimeExpressionError(expr, "Missing path parameter name in \$response.path")
              .toFailure()
        } else {
          Result.success(RuntimeExpressionAst.ResponsePath(name))
        }
      }
      rest == "body" || rest == "body#" -> Result.success(RuntimeExpressionAst.ResponseBody(null))
      rest.startsWith("body#") -> {
        val pointer = rest.removePrefix("body#")
        if (!isValidJsonPointerSyntax(pointer)) {
          CddError.RuntimeExpressionError(expr, "Invalid RFC 6901 JSON Pointer after #").toFailure()
        } else {
          Result.success(RuntimeExpressionAst.ResponseBody(pointer))
        }
      }
      else ->
          CddError.RuntimeExpressionError(
                  expr, "Invalid \$response source (expected header, query, path, or body)")
              .toFailure()
    }
  }

  private fun parseMessageExpression(expr: String): Result<RuntimeExpressionAst> {
    val rest = expr.removePrefix("\$message.")
    return when {
      rest.startsWith("header.") -> {
        val name = rest.removePrefix("header.")
        if (name.isBlank()) {
          CddError.RuntimeExpressionError(expr, "Missing header name in \$message.header")
              .toFailure()
        } else {
          Result.success(RuntimeExpressionAst.MessageHeader(name))
        }
      }
      rest == "payload" -> Result.success(RuntimeExpressionAst.MessagePayload(null))
      rest.startsWith("payload#") -> {
        val pointer = rest.removePrefix("payload#")
        if (!pointer.startsWith("/")) {
          CddError.RuntimeExpressionError(expr, "JSON Pointer after # must start with '/'")
              .toFailure()
        } else {
          Result.success(RuntimeExpressionAst.MessagePayload(pointer))
        }
      }
      rest.startsWith("payload.") -> {
        val subPath = "/" + rest.removePrefix("payload.").replace('.', '/')
        Result.success(RuntimeExpressionAst.MessagePayload(subPath))
      }
      else ->
          CddError.RuntimeExpressionError(
                  expr, "Invalid \$message source (expected header or payload)")
              .toFailure()
    }
  }

  private fun parseInputsExpression(expr: String): Result<RuntimeExpressionAst> {
    val rest = expr.removePrefix("\$inputs.")
    if (rest.isBlank()) {
      return CddError.RuntimeExpressionError(expr, "Missing input name in \$inputs").toFailure()
    }
    val (name, pointer) = splitNameAndPointer(rest)
    if (name.isBlank()) {
      return CddError.RuntimeExpressionError(expr, "Blank input name in \$inputs").toFailure()
    }
    return Result.success(RuntimeExpressionAst.Inputs(name, pointer))
  }

  private fun parseOutputsExpression(expr: String): Result<RuntimeExpressionAst> {
    val rest = expr.removePrefix("\$outputs.")
    if (rest.isBlank()) {
      return CddError.RuntimeExpressionError(expr, "Missing output name in \$outputs").toFailure()
    }
    val (name, pointer) = splitNameAndPointer(rest)
    if (name.isBlank()) {
      return CddError.RuntimeExpressionError(expr, "Blank output name in \$outputs").toFailure()
    }
    return Result.success(RuntimeExpressionAst.Outputs(name, pointer))
  }

  private fun parseStepsExpression(expr: String): Result<RuntimeExpressionAst> {
    val rest = expr.removePrefix("\$steps.")
    val firstDot = rest.indexOf('.')
    if (firstDot <= 0) {
      return CddError.RuntimeExpressionError(expr, "Expected \$steps.<stepId>.outputs.<name>")
          .toFailure()
    }
    val stepId = rest.substring(0, firstDot)
    val afterStep = rest.substring(firstDot + 1)
    val secondDot = afterStep.indexOf('.')
    if (secondDot <= 0) {
      return CddError.RuntimeExpressionError(expr, "Expected \$steps.<stepId>.outputs.<name>")
          .toFailure()
    }
    val category = afterStep.substring(0, secondDot)
    val nameAndPointer = afterStep.substring(secondDot + 1)
    if (category != "outputs" || nameAndPointer.isBlank()) {
      return CddError.RuntimeExpressionError(
              expr, "Step expression must reference 'outputs.<name>'")
          .toFailure()
    }
    val (name, pointer) = splitNameAndPointer(nameAndPointer)
    return Result.success(RuntimeExpressionAst.Steps(stepId, category, name, pointer))
  }

  private fun parseWorkflowsExpression(expr: String): Result<RuntimeExpressionAst> {
    val rest = expr.removePrefix("\$workflows.")
    val firstDot = rest.indexOf('.')
    if (firstDot <= 0) {
      return CddError.RuntimeExpressionError(
              expr, "Expected \$workflows.<workflowId>.outputs.<name>")
          .toFailure()
    }
    val workflowId = rest.substring(0, firstDot)
    val afterWf = rest.substring(firstDot + 1)
    val secondDot = afterWf.indexOf('.')
    if (secondDot <= 0) {
      return CddError.RuntimeExpressionError(
              expr, "Expected \$workflows.<workflowId>.(outputs|inputs).<name>")
          .toFailure()
    }
    val category = afterWf.substring(0, secondDot)
    val nameAndPointer = afterWf.substring(secondDot + 1)
    if ((category != "outputs" && category != "inputs") || nameAndPointer.isBlank()) {
      return CddError.RuntimeExpressionError(
              expr, "Workflow expression must reference 'outputs.<name>' or 'inputs.<name>'")
          .toFailure()
    }
    val (name, pointer) = splitNameAndPointer(nameAndPointer)
    return Result.success(RuntimeExpressionAst.Workflows(workflowId, category, name, pointer))
  }

  private fun parseSourceDescriptionsExpression(expr: String): Result<RuntimeExpressionAst> {
    val rest = expr.removePrefix("\$sourceDescriptions.")
    val firstDot = rest.indexOf('.')
    if (firstDot <= 0) {
      return CddError.RuntimeExpressionError(expr, "Expected \$sourceDescriptions.<name>.<target>")
          .toFailure()
    }
    val sourceName = rest.substring(0, firstDot)
    val target = rest.substring(firstDot + 1)
    if (target.isBlank()) {
      return CddError.RuntimeExpressionError(
              expr, "Missing target in \$sourceDescriptions expression")
          .toFailure()
    }
    return Result.success(RuntimeExpressionAst.SourceDescriptions(sourceName, target))
  }

  private fun parseComponentsExpression(expr: String): Result<RuntimeExpressionAst> {
    val rest = expr.removePrefix("\$components.")
    val firstDot = rest.indexOf('.')
    if (firstDot <= 0) {
      return CddError.RuntimeExpressionError(expr, "Expected \$components.<category>.<name>")
          .toFailure()
    }
    val category = rest.substring(0, firstDot)
    val name = rest.substring(firstDot + 1)
    if (category !in validCategories || name.isBlank()) {
      return CddError.RuntimeExpressionError(
              expr, "Invalid component reference '$expr' (allowed categories: $validCategories)")
          .toFailure()
    }
    return Result.success(RuntimeExpressionAst.Components(category, name))
  }

  private fun splitNameAndPointer(raw: String): Pair<String, String?> {
    val hashIdx = raw.indexOf('#')
    if (hashIdx >= 0) {
      val name = raw.substring(0, hashIdx)
      val pointer = raw.substring(hashIdx + 1)
      return name to pointer
    }
    val dotIdx = raw.indexOf('.')
    if (dotIdx >= 0) {
      val name = raw.substring(0, dotIdx)
      val pointer = "/" + raw.substring(dotIdx + 1).replace('.', '/')
      return name to pointer
    }
    return raw to null
  }

  /**
   * Evaluates a runtime expression or template string against [context], returning the resolved
   * [OpenApiValue] wrapped in a [Result].
   *
   * @param expression Raw runtime expression or template string.
   * @param context Active [WorkflowExecutionContext].
   * @return A [Result] containing the evaluated [OpenApiValue] or a
   *   [CddError.RuntimeExpressionError].
   */
  fun evaluate(expression: String, context: WorkflowExecutionContext): Result<OpenApiValue> {
    if (!isOrContainsExpression(expression)) {
      return Result.success(OpenApiString(expression))
    }
    val ast =
        parseExpression(expression).getOrElse {
          return Result.failure(it)
        }
    return evaluateAst(ast, context, expression)
  }

  /**
   * Evaluates an arbitrary [OpenApiValue], recursively resolving any [OpenApiString] values that
   * contain runtime expressions or `{...}` templates.
   *
   * @param value The [OpenApiValue] to evaluate.
   * @param context Active [WorkflowExecutionContext].
   * @return A [Result] containing the resolved [OpenApiValue].
   */
  fun evaluateValue(value: OpenApiValue, context: WorkflowExecutionContext): Result<OpenApiValue> {
    return when (value) {
      is OpenApiString ->
          if (isOrContainsExpression(value.value)) {
            evaluate(value.value, context)
          } else {
            Result.success(value)
          }
      is OpenApiArray -> {
        val resolved = mutableListOf<OpenApiValue>()
        for (elem in value.elements) {
          val item =
              evaluateValue(elem, context).getOrElse {
                return Result.failure(it)
              }
          resolved.add(item)
        }
        Result.success(OpenApiArray(resolved))
      }
      is OpenApiObject -> {
        val resolved = LinkedHashMap<String, OpenApiValue>()
        for ((k, v) in value.properties) {
          val item =
              evaluateValue(v, context).getOrElse {
                return Result.failure(it)
              }
          resolved[k] = item
        }
        Result.success(OpenApiObject(resolved))
      }
      else -> Result.success(value)
    }
  }

  /**
   * Evaluates a pre-parsed [RuntimeExpressionAst] against [context].
   *
   * @param ast The parsed [RuntimeExpressionAst].
   * @param context Active [WorkflowExecutionContext].
   * @param rawExpr Optional raw expression string for error reporting.
   * @return A [Result] containing the resolved [OpenApiValue] or a
   *   [CddError.RuntimeExpressionError].
   */
  fun evaluateAst(
      ast: RuntimeExpressionAst,
      context: WorkflowExecutionContext,
      rawExpr: String
  ): Result<OpenApiValue> {
    return when (ast) {
      is RuntimeExpressionAst.Url -> {
        val req =
            context.currentRequest
                ?: return CddError.RuntimeExpressionError(rawExpr, "No active request for \$url")
                    .toFailure()
        Result.success(OpenApiString(req.url))
      }
      is RuntimeExpressionAst.Method -> {
        val req =
            context.currentRequest
                ?: return CddError.RuntimeExpressionError(rawExpr, "No active request for \$method")
                    .toFailure()
        Result.success(OpenApiString(req.method))
      }
      is RuntimeExpressionAst.StatusCode -> {
        val resp =
            context.currentResponse
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "No active response for \$statusCode")
                    .toFailure()
        Result.success(OpenApiInt(resp.statusCode.toLong()))
      }
      is RuntimeExpressionAst.Self -> {
        val selfVal =
            context.selfUri
                ?: return CddError.RuntimeExpressionError(rawExpr, "\$self is not defined")
                    .toFailure()
        Result.success(OpenApiString(selfVal))
      }
      is RuntimeExpressionAst.RequestHeader -> {
        val req =
            context.currentRequest
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "No active request for \$request.header.${ast.name}")
                    .toFailure()
        val match =
            findHeaderIgnoreCase(req.headers, ast.name)
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "Request header '${ast.name}' not found")
                    .toFailure()
        Result.success(OpenApiString(match))
      }
      is RuntimeExpressionAst.RequestQuery -> {
        val req =
            context.currentRequest
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "No active request for \$request.query.${ast.name}")
                    .toFailure()
        val match =
            req.query[ast.name]
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "Request query parameter '${ast.name}' not found")
                    .toFailure()
        Result.success(OpenApiString(match))
      }
      is RuntimeExpressionAst.RequestPath -> {
        val req =
            context.currentRequest
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "No active request for \$request.path.${ast.name}")
                    .toFailure()
        val match =
            req.path[ast.name]
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "Request path parameter '${ast.name}' not found")
                    .toFailure()
        Result.success(OpenApiString(match))
      }
      is RuntimeExpressionAst.RequestBody -> {
        val body =
            context.currentRequest?.body
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "No active request body for \$request.body")
                    .toFailure()
        resolveOptionalPointer(body, ast.pointer, rawExpr)
      }
      is RuntimeExpressionAst.ResponseHeader -> {
        val resp =
            context.currentResponse
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "No active response for \$response.header.${ast.name}")
                    .toFailure()
        val match =
            findHeaderIgnoreCase(resp.headers, ast.name)
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "Response header '${ast.name}' not found")
                    .toFailure()
        Result.success(OpenApiString(match))
      }
      is RuntimeExpressionAst.ResponseQuery -> {
        val req =
            context.currentRequest
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "No active request for \$response.query.${ast.name}")
                    .toFailure()
        val match =
            req.query[ast.name]
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "Response query parameter '${ast.name}' not found")
                    .toFailure()
        Result.success(OpenApiString(match))
      }
      is RuntimeExpressionAst.ResponsePath -> {
        val req =
            context.currentRequest
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "No active request for \$response.path.${ast.name}")
                    .toFailure()
        val match =
            req.path[ast.name]
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "Response path parameter '${ast.name}' not found")
                    .toFailure()
        Result.success(OpenApiString(match))
      }
      is RuntimeExpressionAst.ResponseBody -> {
        val body =
            context.currentResponse?.body
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "No active response body for \$response.body")
                    .toFailure()
        resolveOptionalPointer(body, ast.pointer, rawExpr)
      }
      is RuntimeExpressionAst.MessageHeader -> {
        val msg =
            context.currentMessage
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "No active message for \$message.header.${ast.name}")
                    .toFailure()
        val match =
            findHeaderIgnoreCase(msg.headers, ast.name)
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "Message header '${ast.name}' not found")
                    .toFailure()
        Result.success(OpenApiString(match))
      }
      is RuntimeExpressionAst.MessagePayload -> {
        val payload =
            context.currentMessage?.payload
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "No active message payload for \$message.payload")
                    .toFailure()
        resolveOptionalPointer(payload, ast.pointer, rawExpr)
      }
      is RuntimeExpressionAst.Inputs -> {
        val base =
            context.inputs[ast.name]
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "Input '${ast.name}' not found in workflow context")
                    .toFailure()
        resolveOptionalPointer(base, ast.pointer, rawExpr)
      }
      is RuntimeExpressionAst.Outputs -> {
        val base =
            context.workflowOutputs[ast.name]
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "Output '${ast.name}' not found in workflow context")
                    .toFailure()
        resolveOptionalPointer(base, ast.pointer, rawExpr)
      }
      is RuntimeExpressionAst.Steps -> {
        val stepMap =
            context.stepOutputs[ast.stepId]
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "Step '${ast.stepId}' has no recorded outputs")
                    .toFailure()
        val base =
            stepMap[ast.name]
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "Output '${ast.name}' not found in step '${ast.stepId}'")
                    .toFailure()
        resolveOptionalPointer(base, ast.pointer, rawExpr)
      }
      is RuntimeExpressionAst.Workflows -> {
        val map =
            if (ast.category == "inputs") {
              context.externalWorkflowInputs[ast.workflowId]
            } else {
              context.externalWorkflowOutputs[ast.workflowId]
            }
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "Workflow '${ast.workflowId}' has no recorded ${ast.category}")
                    .toFailure()
        val base =
            map[ast.name]
                ?: return CddError.RuntimeExpressionError(
                        rawExpr,
                        "Entry '${ast.name}' not found in workflow '${ast.workflowId}' ${ast.category}")
                    .toFailure()
        resolveOptionalPointer(base, ast.pointer, rawExpr)
      }
      is RuntimeExpressionAst.SourceDescriptions -> {
        val url =
            context.sourceUrls[ast.sourceName]
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "SourceDescription '${ast.sourceName}' not found")
                    .toFailure()
        if (ast.target == "url") {
          Result.success(OpenApiString(url))
        } else {
          Result.success(OpenApiString("$url#${ast.target}"))
        }
      }
      is RuntimeExpressionAst.Components -> {
        val comps =
            context.components
                ?: return CddError.RuntimeExpressionError(
                        rawExpr, "No components defined in Arazzo document")
                    .toFailure()
        when (ast.category) {
          "parameters" -> {
            val param =
                comps.parameters[ast.name]
                    ?: return CddError.RuntimeExpressionError(
                            rawExpr, "Component parameter '${ast.name}' not found")
                        .toFailure()
            evaluateValue(param.value, context)
          }
          "inputs" -> {
            if (!comps.inputs.containsKey(ast.name)) {
              return CddError.RuntimeExpressionError(
                      rawExpr, "Component input '${ast.name}' not found")
                  .toFailure()
            }
            Result.success(OpenApiString("\$components.inputs.${ast.name}"))
          }
          "successActions" -> {
            val action =
                comps.successActions[ast.name]
                    ?: return CddError.RuntimeExpressionError(
                            rawExpr, "Component successAction '${ast.name}' not found")
                        .toFailure()
            Result.success(OpenApiString(action.name))
          }
          "failureActions" -> {
            val action =
                comps.failureActions[ast.name]
                    ?: return CddError.RuntimeExpressionError(
                            rawExpr, "Component failureAction '${ast.name}' not found")
                        .toFailure()
            Result.success(OpenApiString(action.name))
          }
          else ->
              CddError.RuntimeExpressionError(
                      rawExpr, "Unsupported component category '${ast.category}'")
                  .toFailure()
        }
      }
      is RuntimeExpressionAst.Template -> {
        val sb = StringBuilder()
        for (segment in ast.segments) {
          when (segment) {
            is TemplateSegment.Literal -> sb.append(segment.text)
            is TemplateSegment.Expression -> {
              val resolved =
                  evaluateAst(segment.expression, context, rawExpr).getOrElse {
                    return Result.failure(it)
                  }
              sb.append(openApiValueToTemplateString(resolved))
            }
          }
        }
        Result.success(OpenApiString(sb.toString()))
      }
    }
  }

  /**
   * Navigates an [OpenApiValue] tree using an RFC 6901 JSON Pointer (`""` or `"/a/b/0"`).
   *
   * @param root The root [OpenApiValue] to traverse.
   * @param pointer The RFC 6901 JSON Pointer string.
   * @return A [Result] containing the target [OpenApiValue] or a [CddError.RuntimeExpressionError].
   */
  fun resolveJsonPointer(root: OpenApiValue, pointer: String): Result<OpenApiValue> {
    val normalized = pointer.removePrefix("#")
    if (normalized.isEmpty()) return Result.success(root)
    if (!normalized.startsWith("/")) {
      return CddError.RuntimeExpressionError(
              pointer, "RFC 6901 JSON Pointer must start with '/': '$pointer'")
          .toFailure()
    }
    val tokens = normalized.substring(1).split("/").map { it.replace("~1", "/").replace("~0", "~") }
    var current: OpenApiValue = root
    for (token in tokens) {
      current =
          when (val node = current) {
            is OpenApiObject ->
                node.properties[token]
                    ?: return CddError.RuntimeExpressionError(
                            pointer, "Property '$token' not found at pointer '$pointer'")
                        .toFailure()
            is OpenApiArray -> {
              val index =
                  token.toIntOrNull()
                      ?: return CddError.RuntimeExpressionError(
                              pointer, "Invalid array index '$token' in pointer '$pointer'")
                          .toFailure()
              node.elements.getOrNull(index)
                  ?: return CddError.RuntimeExpressionError(
                          pointer, "Array index $index out of bounds in pointer '$pointer'")
                      .toFailure()
            }
            else ->
                return CddError.RuntimeExpressionError(
                        pointer, "Cannot traverse into primitive at token '$token'")
                    .toFailure()
          }
    }
    return Result.success(current)
  }

  private fun findHeaderIgnoreCase(headers: Map<String, String>, name: String): String? {
    for ((k, v) in headers) {
      if (k.equals(name, ignoreCase = true)) return v
    }
    return null
  }

  private fun resolveOptionalPointer(
      base: OpenApiValue,
      pointer: String?,
      rawExpr: String
  ): Result<OpenApiValue> {
    if (pointer.isNullOrEmpty()) return Result.success(base)
    return resolveJsonPointer(base, pointer)
  }

  private fun openApiValueToTemplateString(value: OpenApiValue): String =
      when (value) {
        is OpenApiString -> value.value
        is OpenApiInt -> value.value.toString()
        is OpenApiNumber -> value.raw
        is OpenApiBoolean -> value.value.toString()
        is OpenApiNull -> "null"
        else -> value.toJsonElement().toString()
      }
}
