/**
 * Evaluates Arazzo 1.1.0 [domain.CriterionObject] assertions, [domain.SelectorObject] queries,
 * [domain.OutputValue] mappings, and [domain.PayloadReplacementObject] payload mutations with full
 * [kotlin.Result] percolation.
 */
package arazzo

import domain.ArazzoRequestBody
import domain.CddError
import domain.CriterionEngineType
import domain.CriterionObject
import domain.CriterionTypeOrExpression
import domain.OpenApiArray
import domain.OpenApiBoolean
import domain.OpenApiInt
import domain.OpenApiNull
import domain.OpenApiNumber
import domain.OpenApiObject
import domain.OpenApiString
import domain.OpenApiValue
import domain.OutputValue
import domain.PayloadReplacementObject
import domain.SelectorObject
import domain.toFailure

/**
 * Multiplatform evaluator for Arazzo 1.1.0 criteria (`simple`, `regex`, `jsonpath`, `jsonpointer`,
 * `xpath`), structured selectors, and request body payload replacements.
 */
object ArazzoCriterionEvaluator {
  /**
   * Evaluates a single [CriterionObject] against [context], returning `true` or `false` wrapped in
   * a [Result].
   *
   * @param criterion The [CriterionObject] assertion to evaluate.
   * @param context Active [WorkflowExecutionContext].
   * @return A [Result] containing `true` if the condition holds, `false` otherwise, or a
   *   [CddError].
   */
  fun evaluateCriterion(
      criterion: CriterionObject,
      context: WorkflowExecutionContext
  ): Result<Boolean> {
    if (criterion.condition.isBlank()) {
      return CddError.CriterionEvaluationError(criterion.condition, "Condition cannot be blank")
          .toFailure()
    }
    val engine = resolveEngineType(criterion.type)
    return when (engine) {
      CriterionEngineType.SIMPLE ->
          evaluateSimpleCondition(criterion.condition, criterion.context, context)
      CriterionEngineType.REGEX ->
          evaluateRegexCondition(criterion.condition, criterion.context, context)
      CriterionEngineType.JSONPATH ->
          evaluateJsonPathCondition(criterion.condition, criterion.context, context)
      CriterionEngineType.JSONPOINTER ->
          evaluateJsonPointerCondition(criterion.condition, criterion.context, context)
      CriterionEngineType.XPATH ->
          evaluateXPathCondition(criterion.condition, criterion.context, context)
    }
  }

  /**
   * Evaluates a list of [CriterionObject]s; all must evaluate to `true` for the result to be
   * `true`.
   *
   * @param criteria List of [CriterionObject]s to evaluate.
   * @param context Active [WorkflowExecutionContext].
   * @return A [Result] containing `true` if all criteria pass, `false` if any fail, or a
   *   [CddError].
   */
  fun evaluateAllCriteria(
      criteria: List<CriterionObject>,
      context: WorkflowExecutionContext
  ): Result<Boolean> {
    for (criterion in criteria) {
      val passed =
          evaluateCriterion(criterion, context).getOrElse {
            return Result.failure(it)
          }
      if (!passed) return Result.success(false)
    }
    return Result.success(true)
  }

  /**
   * Evaluates an [OutputValue] (either a runtime expression or a [SelectorObject]) against
   * [context].
   *
   * @param output The [OutputValue] to evaluate.
   * @param context Active [WorkflowExecutionContext].
   * @return A [Result] containing the extracted [OpenApiValue] or a [CddError].
   */
  fun evaluateOutput(output: OutputValue, context: WorkflowExecutionContext): Result<OpenApiValue> =
      when (output) {
        is OutputValue.Expression -> RuntimeExpressionEngine.evaluate(output.expression, context)
        is OutputValue.Selector -> evaluateSelector(output.selector, context)
      }

  /**
   * Evaluates a structured Arazzo 1.1.0 [SelectorObject] against [context].
   *
   * @param selector The [SelectorObject] query.
   * @param context Active [WorkflowExecutionContext].
   * @return A [Result] containing the selected [OpenApiValue] or a
   *   [CddError.CriterionEvaluationError].
   */
  fun evaluateSelector(
      selector: SelectorObject,
      context: WorkflowExecutionContext
  ): Result<OpenApiValue> {
    val root =
        RuntimeExpressionEngine.evaluate(selector.context, context).getOrElse {
          return CddError.CriterionEvaluationError(
                  selector.selector, "Failed to resolve selector context '${selector.context}'")
              .toFailure()
        }
    val engine = resolveSelectorEngine(selector.type, selector.selector)
    return when (engine) {
      CriterionEngineType.JSONPOINTER ->
          RuntimeExpressionEngine.resolveJsonPointer(root, selector.selector)
              .fold(
                  onSuccess = { Result.success(it) },
                  onFailure = {
                    CddError.CriterionEvaluationError(selector.selector, it.message.toString())
                        .toFailure()
                  })
      CriterionEngineType.JSONPATH -> queryJsonPath(root, selector.selector)
      CriterionEngineType.XPATH -> queryXPath(root, selector.selector)
      CriterionEngineType.REGEX -> {
        val text = valueToString(root)
        val match = Regex(selector.selector).find(text)?.value
        if (match != null) {
          Result.success(OpenApiString(match))
        } else {
          CddError.CriterionEvaluationError(selector.selector, "Regex selector matched nothing")
              .toFailure()
        }
      }
      CriterionEngineType.SIMPLE -> RuntimeExpressionEngine.evaluate(selector.selector, context)
    }
  }

  /**
   * Evaluates a step's [ArazzoRequestBody], resolving dynamic expressions in `payload` and applying
   * all [PayloadReplacementObject] mutations in order.
   *
   * @param requestBody The [ArazzoRequestBody] definition.
   * @param context Active [WorkflowExecutionContext].
   * @return A [Result] containing the final [OpenApiValue] payload or a [CddError].
   */
  fun applyPayloadReplacements(
      requestBody: ArazzoRequestBody,
      context: WorkflowExecutionContext
  ): Result<OpenApiValue> {
    val basePayload = requestBody.payload ?: OpenApiObject(emptyMap())
    var current =
        RuntimeExpressionEngine.evaluateValue(basePayload, context).getOrElse {
          return Result.failure(it)
        }
    for (replacement in requestBody.replacements) {
      val resolvedReplacementVal =
          RuntimeExpressionEngine.evaluateValue(replacement.value, context).getOrElse {
            return Result.failure(it)
          }
      current =
          applySingleReplacement(current, replacement, resolvedReplacementVal).getOrElse {
            return Result.failure(it)
          }
    }
    return Result.success(current)
  }

  private fun applySingleReplacement(
      targetRoot: OpenApiValue,
      replacement: PayloadReplacementObject,
      newValue: OpenApiValue
  ): Result<OpenApiValue> {
    val rawTarget = replacement.target.trim()
    if (rawTarget.isEmpty()) {
      return CddError.CriterionEvaluationError(rawTarget, "PayloadReplacement target is empty")
          .toFailure()
    }
    val pointer =
        when {
          rawTarget.startsWith("/") -> rawTarget
          rawTarget.startsWith("\$.") ->
              "/" +
                  rawTarget.removePrefix("\$.").replace("[", "/").replace("]", "").replace(".", "/")
          else -> "/$rawTarget"
        }
    val segments =
        pointer
            .removePrefix("/")
            .split("/")
            .filter { it.isNotEmpty() }
            .map { it.replace("~1", "/").replace("~0", "~") }
    if (segments.isEmpty()) {
      return Result.success(newValue)
    }
    return replaceAtSegments(targetRoot, segments, 0, newValue, rawTarget)
  }

  private fun replaceAtSegments(
      current: OpenApiValue,
      segments: List<String>,
      index: Int,
      newValue: OpenApiValue,
      rawTarget: String
  ): Result<OpenApiValue> {
    val token = segments[index]
    val isLast = index == segments.size - 1
    return when (current) {
      is OpenApiObject -> {
        val mutable = LinkedHashMap(current.properties)
        if (isLast) {
          mutable[token] = newValue
          Result.success(OpenApiObject(mutable))
        } else {
          val child = mutable[token] ?: OpenApiObject(emptyMap())
          val updatedChild =
              replaceAtSegments(child, segments, index + 1, newValue, rawTarget).getOrElse {
                return Result.failure(it)
              }
          mutable[token] = updatedChild
          Result.success(OpenApiObject(mutable))
        }
      }
      is OpenApiArray -> {
        val arrIdx =
            token.toIntOrNull()
                ?: return CddError.CriterionEvaluationError(
                        rawTarget, "Expected integer array index at '$token' in '$rawTarget'")
                    .toFailure()
        val existingElem =
            current.elements.getOrNull(arrIdx)
                ?: return CddError.CriterionEvaluationError(
                        rawTarget, "Array index $arrIdx out of bounds in '$rawTarget'")
                    .toFailure()
        val mutable = current.elements.toMutableList()
        if (isLast) {
          mutable[arrIdx] = newValue
          Result.success(OpenApiArray(mutable))
        } else {
          val updatedChild =
              replaceAtSegments(existingElem, segments, index + 1, newValue, rawTarget).getOrElse {
                return Result.failure(it)
              }
          mutable[arrIdx] = updatedChild
          Result.success(OpenApiArray(mutable))
        }
      }
      else ->
          CddError.CriterionEvaluationError(
                  rawTarget, "Cannot apply replacement inside primitive at '$token'")
              .toFailure()
    }
  }

  private fun resolveEngineType(type: CriterionTypeOrExpression?): CriterionEngineType =
      when (type) {
        null -> CriterionEngineType.SIMPLE
        is CriterionTypeOrExpression.Engine -> type.type
        is CriterionTypeOrExpression.Versioned -> type.expressionType.type
      }

  private fun resolveSelectorEngine(
      type: CriterionTypeOrExpression?,
      selector: String
  ): CriterionEngineType {
    if (type != null) return resolveEngineType(type)
    return when {
      selector.startsWith("\$") -> CriterionEngineType.JSONPATH
      selector.startsWith("//") -> CriterionEngineType.XPATH
      selector.startsWith("/") -> CriterionEngineType.JSONPOINTER
      else -> CriterionEngineType.JSONPATH
    }
  }

  private fun evaluateSimpleCondition(
      condition: String,
      contextExpr: String?,
      context: WorkflowExecutionContext
  ): Result<Boolean> {
    val trimmed = stripOuterParens(condition.trim())
    val orParts = splitTopLevelLogical(trimmed, "||")
    if (orParts.size > 1) {
      for (part in orParts) {
        val res =
            evaluateSimpleCondition(part, contextExpr, context).getOrElse {
              return Result.failure(it)
            }
        if (res) return Result.success(true)
      }
      return Result.success(false)
    }
    val andParts = splitTopLevelLogical(trimmed, "&&")
    if (andParts.size > 1) {
      for (part in andParts) {
        val res =
            evaluateSimpleCondition(part, contextExpr, context).getOrElse {
              return Result.failure(it)
            }
        if (!res) return Result.success(false)
      }
      return Result.success(true)
    }
    if (trimmed.startsWith("!")) {
      val inner =
          evaluateSimpleCondition(trimmed.substring(1), contextExpr, context).getOrElse {
            return Result.failure(it)
          }
      return Result.success(!inner)
    }
    val operators = listOf("==", "!=", "<=", ">=", "<", ">")
    for (op in operators) {
      val opIdx = findTopLevelOperator(trimmed, op)
      if (opIdx >= 0) {
        val leftRaw = trimmed.substring(0, opIdx).trim()
        val rightRaw = trimmed.substring(opIdx + op.length).trim()
        val leftVal =
            resolveSimpleOperand(leftRaw, contextExpr, context).getOrElse {
              return Result.failure(it)
            }
        val rightVal =
            resolveSimpleOperand(rightRaw, contextExpr, context).getOrElse {
              return Result.failure(it)
            }
        return compareValues(leftVal, rightVal, op)
      }
    }
    val singleVal =
        resolveSimpleOperand(trimmed, contextExpr, context).getOrElse {
          return Result.failure(it)
        }
    return Result.success(isTruthy(singleVal))
  }

  private fun resolveSimpleOperand(
      raw: String,
      contextExpr: String?,
      context: WorkflowExecutionContext
  ): Result<OpenApiValue> {
    val trimmed = raw.trim()
    if (trimmed.length >= 2 &&
        ((trimmed.first() == Char(39) && trimmed.last() == Char(39)) ||
            (trimmed.first() == '"' && trimmed.last() == '"'))) {
      return Result.success(OpenApiString(trimmed.substring(1, trimmed.length - 1)))
    }
    if (trimmed == "true") return Result.success(OpenApiBoolean(true))
    if (trimmed == "false") return Result.success(OpenApiBoolean(false))
    if (trimmed == "null") return Result.success(OpenApiNull)
    val asLong = trimmed.toLongOrNull()
    if (asLong != null) return Result.success(OpenApiInt(asLong))
    val asDouble = trimmed.toDoubleOrNull()
    if (asDouble != null) return Result.success(OpenApiNumber(asDouble, trimmed))
    if (trimmed.startsWith("/")) {
      val root =
          RuntimeExpressionEngine.evaluate(contextExpr ?: "\$response.body", context).getOrElse {
            return Result.failure(it)
          }
      return RuntimeExpressionEngine.resolveJsonPointer(root, trimmed)
    }
    return RuntimeExpressionEngine.evaluate(trimmed, context)
  }

  private fun compareValues(left: OpenApiValue, right: OpenApiValue, op: String): Result<Boolean> =
      Result.success(compareValuesBoolean(left, right, op))

  private fun compareValuesBoolean(left: OpenApiValue, right: OpenApiValue, op: String): Boolean {
    val leftNum = toDoubleOrNull(left)
    val rightNum = toDoubleOrNull(right)
    if (leftNum != null && rightNum != null) {
      return when (op) {
        "==" -> leftNum == rightNum
        "!=" -> leftNum != rightNum
        "<" -> leftNum < rightNum
        "<=" -> leftNum <= rightNum
        ">" -> leftNum > rightNum
        else -> leftNum >= rightNum
      }
    }
    val leftStr = valueToString(left)
    val rightStr = valueToString(right)
    return when (op) {
      "==" -> leftStr == rightStr
      "!=" -> leftStr != rightStr
      "<" -> leftStr < rightStr
      "<=" -> leftStr <= rightStr
      ">" -> leftStr > rightStr
      else -> leftStr >= rightStr
    }
  }

  private fun evaluateRegexCondition(
      pattern: String,
      contextExpr: String?,
      context: WorkflowExecutionContext
  ): Result<Boolean> {
    val targetExpr = contextExpr ?: "\$response.body"
    val targetVal =
        RuntimeExpressionEngine.evaluate(targetExpr, context).getOrElse {
          return Result.failure(it)
        }
    val targetStr = valueToString(targetVal)
    return Result.success(Regex(pattern).containsMatchIn(targetStr))
  }

  private fun evaluateJsonPathCondition(
      condition: String,
      contextExpr: String?,
      context: WorkflowExecutionContext
  ): Result<Boolean> {
    val targetExpr = contextExpr ?: "\$response.body"
    val root =
        RuntimeExpressionEngine.evaluate(targetExpr, context).getOrElse {
          return Result.failure(it)
        }
    val operators = listOf("==", "!=", "<=", ">=", "<", ">")
    for (op in operators) {
      val opIdx = findTopLevelOperator(condition, op)
      if (opIdx >= 0) {
        val pathPart = condition.substring(0, opIdx).trim()
        val expectedRaw = condition.substring(opIdx + op.length).trim()
        val selected =
            queryJsonPath(root, pathPart).getOrElse {
              return Result.success(false)
            }
        val expectedVal =
            resolveSimpleOperand(expectedRaw, null, context).getOrElse {
              return Result.failure(it)
            }
        return compareValues(selected, expectedVal, op)
      }
    }
    val matched = queryJsonPath(root, condition).getOrNull() ?: return Result.success(false)
    return Result.success(isTruthy(matched))
  }

  private fun evaluateJsonPointerCondition(
      condition: String,
      contextExpr: String?,
      context: WorkflowExecutionContext
  ): Result<Boolean> {
    val targetExpr = contextExpr ?: "\$response.body"
    val root =
        RuntimeExpressionEngine.evaluate(targetExpr, context).getOrElse {
          return Result.failure(it)
        }
    val matched =
        RuntimeExpressionEngine.resolveJsonPointer(root, condition).getOrNull()
            ?: return Result.success(false)
    return Result.success(isTruthy(matched))
  }

  private fun evaluateXPathCondition(
      condition: String,
      contextExpr: String?,
      context: WorkflowExecutionContext
  ): Result<Boolean> {
    val targetExpr = contextExpr ?: "\$response.body"
    val root =
        RuntimeExpressionEngine.evaluate(targetExpr, context).getOrElse {
          return Result.failure(it)
        }
    val matched = queryXPath(root, condition).getOrNull() ?: return Result.success(false)
    return Result.success(isTruthy(matched))
  }

  /**
   * Evaluates a JSONPath query (`$.a.b[0]`, `$[?(@.status == 'ok')]`) against an [OpenApiValue]
   * tree.
   *
   * @param root The root [OpenApiValue] to query.
   * @param query The JSONPath query string.
   * @return A [Result] containing the matched [OpenApiValue] or a
   *   [CddError.CriterionEvaluationError].
   */
  fun queryJsonPath(root: OpenApiValue, query: String): Result<OpenApiValue> {
    val trimmed = query.trim()
    if (!trimmed.startsWith("\$")) {
      return CddError.CriterionEvaluationError(query, "JSONPath must start with '\$'").toFailure()
    }
    var rest = trimmed.substring(1)
    if (rest.startsWith(".")) rest = rest.substring(1)
    if (rest.isEmpty()) return Result.success(root)
    val tokens = tokenizeJsonPath(rest)
    var current: OpenApiValue = root
    for (token in tokens) {
      if (token.startsWith("?(")) {
        current =
            evaluateJsonPathFilter(current, token.removePrefix("?(").removeSuffix(")"), query)
                .getOrElse {
                  return Result.failure(it)
                }
        continue
      }
      if (token == "*") {
        current =
            when (val node = current) {
              is OpenApiArray -> node
              is OpenApiObject -> OpenApiArray(node.properties.values.toList())
              else ->
                  return CddError.CriterionEvaluationError(query, "Wildcard '*' on primitive")
                      .toFailure()
            }
        continue
      }
      val idx = token.toIntOrNull()
      current =
          when (val node = current) {
            is OpenApiArray -> {
              if (idx != null) {
                node.elements.getOrNull(idx)
                    ?: return CddError.CriterionEvaluationError(
                            query, "Array index $idx out of bounds in '$query'")
                        .toFailure()
              } else {
                val mapped = mutableListOf<OpenApiValue>()
                for (elem in node.elements) {
                  if (elem is OpenApiObject) {
                    val prop = elem.properties[token]
                    if (prop != null) mapped.add(prop)
                  }
                }
                if (mapped.isEmpty()) {
                  return CddError.CriterionEvaluationError(
                          query, "Property '$token' not found in array elements")
                      .toFailure()
                }
                if (mapped.size == 1) mapped.first() else OpenApiArray(mapped)
              }
            }
            is OpenApiObject ->
                node.properties[token]
                    ?: return CddError.CriterionEvaluationError(
                            query, "Property '$token' not found in '$query'")
                        .toFailure()
            else ->
                return CddError.CriterionEvaluationError(
                        query, "Cannot traverse primitive at '$token'")
                    .toFailure()
          }
    }
    return Result.success(current)
  }

  private fun evaluateJsonPathFilter(
      node: OpenApiValue,
      filterExpr: String,
      query: String
  ): Result<OpenApiValue> {
    val candidates =
        when (node) {
          is OpenApiArray -> node.elements
          is OpenApiObject -> listOf(node)
          else ->
              return CddError.CriterionEvaluationError(query, "Filter requires array or object")
                  .toFailure()
        }
    val operators = listOf("==", "!=", "<=", ">=", "<", ">")
    var matchedOp: String? = null
    var opIdx = -1
    for (op in operators) {
      val idx = filterExpr.indexOf(op)
      if (idx >= 0) {
        matchedOp = op
        opIdx = idx
        break
      }
    }
    val matches =
        candidates.filter { item ->
          if (item !is OpenApiObject) return@filter false
          if (matchedOp == null) {
            val propName = filterExpr.removePrefix("@.").trim()
            item.properties.containsKey(propName)
          } else {
            val propName = filterExpr.substring(0, opIdx).trim().removePrefix("@.")
            val expectedRaw = filterExpr.substring(opIdx + matchedOp.length).trim()
            val propVal = item.properties[propName] ?: return@filter false
            val expectedVal =
                resolveSimpleOperand(expectedRaw, null, WorkflowExecutionContext()).getOrNull()
                    ?: return@filter false
            compareValuesBoolean(propVal, expectedVal, matchedOp)
          }
        }
    if (matches.isEmpty()) {
      return CddError.CriterionEvaluationError(query, "No elements matched filter '$filterExpr'")
          .toFailure()
    }
    return Result.success(OpenApiArray(matches))
  }

  private fun tokenizeJsonPath(path: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    while (i < path.length) {
      when (val ch = path[i]) {
        '.' -> {
          if (current.isNotEmpty()) {
            tokens.add(current.toString())
            current.clear()
          }
          i++
        }
        '[' -> {
          if (current.isNotEmpty()) {
            tokens.add(current.toString())
            current.clear()
          }
          val close = path.indexOf(']', i + 1)
          if (close > i) {
            val inside =
                path
                    .substring(i + 1, close)
                    .trim()
                    .removeSurrounding("'")
                    .removeSurrounding('"'.toString())
            if (inside.isNotEmpty()) tokens.add(inside)
            i = close + 1
          } else {
            i++
          }
        }
        else -> {
          current.append(ch)
          i++
        }
      }
    }
    if (current.isNotEmpty()) tokens.add(current.toString())
    return tokens
  }

  /**
   * Evaluates an XPath query (`/order/id`, `//item/name`) against an [OpenApiValue] or XML string.
   *
   * @param root The root [OpenApiValue] to query.
   * @param xpath The XPath query string.
   * @return A [Result] containing the matched [OpenApiValue] or a
   *   [CddError.CriterionEvaluationError].
   */
  fun queryXPath(root: OpenApiValue, xpath: String): Result<OpenApiValue> {
    val trimmed = xpath.trim()
    if (!trimmed.startsWith("/")) {
      return CddError.CriterionEvaluationError(xpath, "XPath must start with '/'").toFailure()
    }
    if (root is OpenApiString) {
      val lastTag = trimmed.substringAfterLast("/").substringBefore("[")
      val regex = Regex("<$lastTag[^>]*>([^<]*)</$lastTag>")
      val matchResult = regex.find(root.value)
      if (matchResult != null) return Result.success(OpenApiString(matchResult.groupValues[1]))
      return CddError.CriterionEvaluationError(xpath, "XPath '$xpath' matched no XML nodes")
          .toFailure()
    }
    val pointer = "/" + trimmed.removePrefix("//").removePrefix("/").substringBefore("[")
    return RuntimeExpressionEngine.resolveJsonPointer(root, pointer)
        .fold(
            onSuccess = { Result.success(it) },
            onFailure = {
              CddError.CriterionEvaluationError(xpath, "XPath '$xpath' did not match node")
                  .toFailure()
            })
  }

  private fun stripOuterParens(input: String): String {
    var s = input
    while (s.startsWith("(") && s.endsWith(")")) {
      var depth = 0
      var balanced = true
      for (i in 0 until s.length - 1) {
        if (s[i] == '(') depth++
        else if (s[i] == ')') {
          depth--
          if (depth == 0) {
            balanced = false
            break
          }
        }
      }
      if (balanced) s = s.substring(1, s.length - 1).trim() else break
    }
    return s
  }

  private fun splitTopLevelLogical(input: String, op: String): List<String> {
    val parts = mutableListOf<String>()
    var depth = 0
    var inQuote = false
    var lastStart = 0
    var i = 0
    while (i < input.length) {
      val ch = input[i]
      when {
        ch == Char(39) || ch == '"' -> inQuote = !inQuote
        !inQuote && ch == '(' -> depth++
        !inQuote && ch == ')' -> depth--
        !inQuote && depth == 0 && input.startsWith(op, i) -> {
          parts.add(input.substring(lastStart, i).trim())
          i += op.length
          lastStart = i
          continue
        }
      }
      i++
    }
    if (parts.isNotEmpty()) {
      parts.add(input.substring(lastStart).trim())
    }
    return parts
  }

  private fun findTopLevelOperator(input: String, op: String): Int {
    var depth = 0
    var inQuote = false
    var i = 0
    while (i <= input.length - op.length) {
      val ch = input[i]
      when {
        ch == Char(39) || ch == '"' -> inQuote = !inQuote
        !inQuote && ch == '(' -> depth++
        !inQuote && ch == ')' -> depth--
        !inQuote && depth == 0 && input.startsWith(op, i) -> {
          return i
        }
      }
      i++
    }
    return -1
  }

  private fun toDoubleOrNull(value: OpenApiValue): Double? =
      when (value) {
        is OpenApiInt -> value.value.toDouble()
        is OpenApiNumber -> value.value
        is OpenApiString -> value.value.toDoubleOrNull()
        else -> null
      }

  private fun valueToString(value: OpenApiValue): String =
      when (value) {
        is OpenApiString -> value.value
        is OpenApiInt -> value.value.toString()
        is OpenApiNumber -> value.raw
        is OpenApiBoolean -> value.value.toString()
        is OpenApiNull -> "null"
        else -> value.toJsonElement().toString()
      }

  private fun isTruthy(value: OpenApiValue): Boolean =
      when (value) {
        is OpenApiNull -> false
        is OpenApiBoolean -> value.value
        is OpenApiArray -> value.elements.isNotEmpty()
        is OpenApiObject -> value.properties.isNotEmpty()
        else -> {
          val s = valueToString(value)
          s.isNotEmpty() && s != "false" && s != "0" && s != "0.0"
        }
      }
}
