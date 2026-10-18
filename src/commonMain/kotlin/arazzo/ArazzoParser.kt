/**
 * Parses Arazzo 1.1.0 and 1.0.x specification documents from JSON or YAML into strongly-typed
 * [domain.ArazzoDefinition] models with full [kotlin.Result] percolation.
 */
package arazzo

import domain.ArazzoComponents
import domain.ArazzoDefinition
import domain.ArazzoInfo
import domain.ArazzoParameter
import domain.ArazzoParameterLocation
import domain.ArazzoRequestBody
import domain.CddError
import domain.CriterionEngineType
import domain.CriterionObject
import domain.CriterionTypeOrExpression
import domain.ExpressionTypeObject
import domain.FailureActionObject
import domain.FailureActionType
import domain.OpenApiValues
import domain.OutputValue
import domain.PayloadReplacementObject
import domain.ReusableObject
import domain.ReusableOr
import domain.SchemaProperty
import domain.SelectorObject
import domain.SourceDescription
import domain.SourceDescriptionType
import domain.StepActionType
import domain.StepObject
import domain.SuccessActionObject
import domain.SuccessActionType
import domain.Workflow
import domain.toFailure
import openapi.JsonNode
import openapi.asArray
import openapi.asObject
import openapi.double
import openapi.fields
import openapi.get
import openapi.has
import openapi.int
import openapi.isTextual
import openapi.parseJsonElementResult
import openapi.parser.SchemaNodeParser
import openapi.parser.YamlToJsonElementConverter
import openapi.text
import openapi.textValue

/**
 * Multiplatform parser for Arazzo 1.1.0 JSON and YAML specifications.
 *
 * All fallible parsing operations return [Result] and percolate [CddError.ArazzoParseError] without
 * throwing exceptions.
 */
class ArazzoParser {
  /** Supported input formats for Arazzo specifications. */
  enum class Format {
    /** Automatically detect JSON vs YAML based on leading token. */
    AUTO,

    /** Parse strictly as JSON. */
    JSON,

    /** Parse as YAML (or JSON as a YAML subset). */
    YAML
  }

  /**
   * Parses an Arazzo 1.1.0 specification from a raw JSON or YAML string into a [Result].
   *
   * @param source Raw JSON or YAML specification string.
   * @param format Optional format hint (defaults to [Format.AUTO]).
   * @return A [Result] containing the parsed [ArazzoDefinition] or a [CddError].
   */
  fun parseStringResult(source: String, format: Format = Format.AUTO): Result<ArazzoDefinition> {
    if (source.isBlank()) {
      return CddError.ArazzoParseError("Arazzo document content is blank").toFailure()
    }
    val resolvedFormat = resolveFormat(source, format)
    val root =
        readTreeResult(source, resolvedFormat).getOrElse {
          return CddError.ArazzoParseError("Failed to parse Arazzo syntax: ${it.message}")
              .toFailure()
        }
    return parseJsonObjectResult(root)
  }

  /**
   * Parses an Arazzo 1.1.0 specification from a parsed [JsonNode] root into a [Result].
   *
   * @param root The root [JsonNode] of the Arazzo document.
   * @return A [Result] containing the parsed [ArazzoDefinition] or a [CddError.ArazzoParseError].
   */
  fun parseJsonObjectResult(root: JsonNode): Result<ArazzoDefinition> {
    val obj =
        root.asObject()
            ?: return CddError.ArazzoParseError("Arazzo document root must be a JSON/YAML object")
                .toFailure()
    val arazzoVersion =
        obj.text("arazzo")
            ?: return CddError.ArazzoParseError("Missing required 'arazzo' version field", "arazzo")
                .toFailure()
    val selfUri = obj.text("\$self")
    val info =
        parseInfoResult(obj.get("info")).getOrElse {
          return Result.failure(it)
        }
    val sourceDescriptions =
        parseSourceDescriptionsResult(obj.get("sourceDescriptions")).getOrElse {
          return Result.failure(it)
        }
    val workflows =
        parseWorkflowsResult(obj.get("workflows")).getOrElse {
          return Result.failure(it)
        }
    val components =
        parseComponentsResult(obj.get("components")).getOrElse {
          return Result.failure(it)
        }
    val extensions = SchemaNodeParser.parseExtensions(obj)
    return Result.success(
        ArazzoDefinition(
            arazzo = arazzoVersion,
            self = selfUri,
            info = info,
            sourceDescriptions = sourceDescriptions,
            workflows = workflows,
            components = components,
            extensions = extensions))
  }

  /**
   * Parses the `info` object of an Arazzo document.
   *
   * @param node The `info` [JsonNode].
   * @return A [Result] containing [ArazzoInfo] or a [CddError.ArazzoParseError].
   */
  fun parseInfoResult(node: JsonNode?): Result<ArazzoInfo> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("Missing or invalid 'info' object", "info")
                .toFailure()
    val title =
        obj.text("title")
            ?: return CddError.ArazzoParseError("Missing required 'info.title'", "info.title")
                .toFailure()
    val version =
        obj.text("version")
            ?: return CddError.ArazzoParseError("Missing required 'info.version'", "info.version")
                .toFailure()
    return Result.success(
        ArazzoInfo(
            title = title,
            summary = obj.text("summary"),
            description = obj.text("description"),
            version = version,
            extensions = SchemaNodeParser.parseExtensions(obj)))
  }

  /**
   * Parses the `sourceDescriptions` array of an Arazzo document.
   *
   * @param node The `sourceDescriptions` [JsonNode].
   * @return A [Result] containing a list of [SourceDescription]s or a [CddError.ArazzoParseError].
   */
  fun parseSourceDescriptionsResult(node: JsonNode?): Result<List<SourceDescription>> {
    val arr =
        node.asArray()
            ?: return CddError.ArazzoParseError(
                    "Missing or invalid 'sourceDescriptions' array", "sourceDescriptions")
                .toFailure()
    val result = mutableListOf<SourceDescription>()
    for ((index, item) in arr.withIndex()) {
      val obj =
          item.asObject()
              ?: return CddError.ArazzoParseError(
                      "sourceDescriptions[$index] must be an object", "sourceDescriptions[$index]")
                  .toFailure()
      val name =
          obj.text("name")
              ?: return CddError.ArazzoParseError(
                      "Missing 'name' in sourceDescriptions[$index]",
                      "sourceDescriptions[$index].name")
                  .toFailure()
      val url =
          obj.text("url")
              ?: return CddError.ArazzoParseError(
                      "Missing 'url' in sourceDescriptions[$index]",
                      "sourceDescriptions[$index].url")
                  .toFailure()
      val typeRaw = obj.text("type")
      val type =
          if (typeRaw != null) {
            SourceDescriptionType.fromWireName(typeRaw).getOrElse {
              return Result.failure(it)
            }
          } else {
            null
          }
      result.add(
          SourceDescription(
              name = name,
              url = url,
              type = type,
              extensions = SchemaNodeParser.parseExtensions(obj)))
    }
    return Result.success(result)
  }

  /**
   * Parses the `workflows` array of an Arazzo document.
   *
   * @param node The `workflows` [JsonNode].
   * @return A [Result] containing a list of [Workflow]s or a [CddError.ArazzoParseError].
   */
  fun parseWorkflowsResult(node: JsonNode?): Result<List<Workflow>> {
    val arr =
        node.asArray()
            ?: return CddError.ArazzoParseError("Missing or invalid 'workflows' array", "workflows")
                .toFailure()
    val workflows = mutableListOf<Workflow>()
    for ((index, item) in arr.withIndex()) {
      val wf =
          parseWorkflowResult(item, index).getOrElse {
            return Result.failure(it)
          }
      workflows.add(wf)
    }
    return Result.success(workflows)
  }

  /**
   * Parses a single [Workflow] object at [index].
   *
   * @param node The workflow [JsonNode].
   * @param index The 0-based index of the workflow in `workflows`.
   * @return A [Result] containing the parsed [Workflow] or a [CddError.ArazzoParseError].
   */
  fun parseWorkflowResult(node: JsonNode, index: Int): Result<Workflow> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError(
                    "workflows[$index] must be an object", "workflows[$index]")
                .toFailure()
    val workflowId =
        obj.text("workflowId")
            ?: return CddError.ArazzoParseError(
                    "Missing 'workflowId' in workflows[$index]", "workflows[$index].workflowId")
                .toFailure()
    val inputsNode = obj.get("inputs")
    val inputs =
        if (inputsNode != null) {
          SchemaNodeParser.parseSchemaProperty(inputsNode)
        } else {
          null
        }
    val dependsOn = parseStringList(obj.get("dependsOn"))
    val stepsArr =
        obj.get("steps").asArray()
            ?: return CddError.ArazzoParseError(
                    "Missing or invalid 'steps' array in workflow '$workflowId'",
                    "workflows[$index].steps")
                .toFailure()
    val steps = mutableListOf<StepObject>()
    for ((stepIdx, stepNode) in stepsArr.withIndex()) {
      val step =
          parseStepResult(stepNode, stepIdx, workflowId).getOrElse {
            return Result.failure(it)
          }
      steps.add(step)
    }
    val successActions =
        parseSuccessActionListResult(obj.get("successActions")).getOrElse {
          return Result.failure(it)
        }
    val failureActions =
        parseFailureActionListResult(obj.get("failureActions")).getOrElse {
          return Result.failure(it)
        }
    val outputs =
        parseOutputsMapResult(obj.get("outputs")).getOrElse {
          return Result.failure(it)
        }
    val parameters =
        parseParameterListResult(obj.get("parameters")).getOrElse {
          return Result.failure(it)
        }
    return Result.success(
        Workflow(
            workflowId = workflowId,
            summary = obj.text("summary"),
            description = obj.text("description"),
            inputs = inputs,
            dependsOn = dependsOn,
            steps = steps,
            successActions = successActions,
            failureActions = failureActions,
            outputs = outputs,
            parameters = parameters,
            extensions = SchemaNodeParser.parseExtensions(obj)))
  }

  /**
   * Parses a single [StepObject] within [workflowId].
   *
   * @param node The step [JsonNode].
   * @param index The 0-based index of the step in `steps`.
   * @param workflowId The parent workflow's identifier.
   * @return A [Result] containing the parsed [StepObject] or a [CddError.ArazzoParseError].
   */
  fun parseStepResult(node: JsonNode, index: Int, workflowId: String): Result<StepObject> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError(
                    "Step[$index] in workflow '$workflowId' must be an object",
                    "workflows.$workflowId.steps[$index]")
                .toFailure()
    val stepId =
        obj.text("stepId")
            ?: return CddError.ArazzoParseError(
                    "Missing 'stepId' in workflow '$workflowId' step[$index]",
                    "workflows.$workflowId.steps[$index].stepId")
                .toFailure()
    val parameters =
        parseParameterListResult(obj.get("parameters")).getOrElse {
          return Result.failure(it)
        }
    val requestBody =
        if (obj.has("requestBody")) {
          parseRequestBodyResult(obj.get("requestBody")).getOrElse {
            return Result.failure(it)
          }
        } else {
          null
        }
    val successCriteria =
        parseCriteriaListResult(obj.get("successCriteria")).getOrElse {
          return Result.failure(it)
        }
    val onSuccess =
        parseSuccessActionListResult(obj.get("onSuccess")).getOrElse {
          return Result.failure(it)
        }
    val onFailure =
        parseFailureActionListResult(obj.get("onFailure")).getOrElse {
          return Result.failure(it)
        }
    val outputs =
        parseOutputsMapResult(obj.get("outputs")).getOrElse {
          return Result.failure(it)
        }
    val timeout = obj.int("timeout")?.toLong()
    val actionRaw = obj.text("action")
    val action =
        if (actionRaw != null) {
          StepActionType.fromWireName(actionRaw).getOrElse {
            return Result.failure(it)
          }
        } else {
          null
        }
    return Result.success(
        StepObject(
            stepId = stepId,
            description = obj.text("description"),
            operationId = obj.text("operationId"),
            operationPath = obj.text("operationPath"),
            channelPath = obj.text("channelPath"),
            workflowId = obj.text("workflowId"),
            parameters = parameters,
            requestBody = requestBody,
            successCriteria = successCriteria,
            onSuccess = onSuccess,
            onFailure = onFailure,
            outputs = outputs,
            timeout = timeout,
            correlationId = obj.text("correlationId"),
            action = action,
            dependsOn = parseStringList(obj.get("dependsOn")),
            extensions = SchemaNodeParser.parseExtensions(obj)))
  }

  /**
   * Parses a list of [ReusableOr] [ArazzoParameter] items.
   *
   * @param node Optional array [JsonNode].
   * @return A [Result] containing the parsed list or a [CddError.ArazzoParseError].
   */
  fun parseParameterListResult(node: JsonNode?): Result<List<ReusableOr<ArazzoParameter>>> {
    if (node == null) return Result.success(emptyList())
    val arr =
        node.asArray()
            ?: return CddError.ArazzoParseError("'parameters' must be an array").toFailure()
    val list = mutableListOf<ReusableOr<ArazzoParameter>>()
    for (item in arr) {
      val parsed =
          parseParameterOrReusableResult(item).getOrElse {
            return Result.failure(it)
          }
      list.add(parsed)
    }
    return Result.success(list)
  }

  /**
   * Parses either an inline [ArazzoParameter] or a [ReusableObject] reference.
   *
   * @param node The parameter [JsonNode].
   * @return A [Result] containing [ReusableOr] of [ArazzoParameter] or a
   *   [CddError.ArazzoParseError].
   */
  fun parseParameterOrReusableResult(node: JsonNode): Result<ReusableOr<ArazzoParameter>> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("Parameter entry must be an object").toFailure()
    val ref = obj.text("reference")
    if (ref != null) {
      val valueNode = obj.get("value")
      val value = valueNode?.let { OpenApiValues.fromJsonElement(it) }
      return Result.success(
          ReusableOr.Reference(
              ReusableObject(
                  reference = ref,
                  value = value,
                  extensions = SchemaNodeParser.parseExtensions(obj))))
    }
    val param =
        parseParameterResult(obj).getOrElse {
          return Result.failure(it)
        }
    return Result.success(ReusableOr.Inline(param))
  }

  /**
   * Parses an inline [ArazzoParameter] object.
   *
   * @param node The parameter [JsonNode].
   * @return A [Result] containing [ArazzoParameter] or a [CddError.ArazzoParseError].
   */
  fun parseParameterResult(node: JsonNode): Result<ArazzoParameter> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("Parameter must be an object").toFailure()
    val name =
        obj.text("name")
            ?: return CddError.ArazzoParseError("Parameter missing required 'name'").toFailure()
    val inRaw = obj.text("in")
    val location =
        if (inRaw != null) {
          ArazzoParameterLocation.fromWireName(inRaw).getOrElse {
            return Result.failure(it)
          }
        } else {
          null
        }
    val valueNode =
        obj.get("value")
            ?: return CddError.ArazzoParseError("Parameter '$name' missing required 'value'")
                .toFailure()
    return Result.success(
        ArazzoParameter(
            name = name,
            location = location,
            value = OpenApiValues.fromJsonElement(valueNode),
            extensions = SchemaNodeParser.parseExtensions(obj)))
  }

  /**
   * Parses a list of [ReusableOr] [SuccessActionObject] items.
   *
   * @param node Optional array [JsonNode].
   * @return A [Result] containing the parsed list or a [CddError.ArazzoParseError].
   */
  fun parseSuccessActionListResult(node: JsonNode?): Result<List<ReusableOr<SuccessActionObject>>> {
    if (node == null) return Result.success(emptyList())
    val arr =
        node.asArray()
            ?: return CddError.ArazzoParseError("Success actions must be an array").toFailure()
    val list = mutableListOf<ReusableOr<SuccessActionObject>>()
    for (item in arr) {
      val parsed =
          parseSuccessActionOrReusableResult(item).getOrElse {
            return Result.failure(it)
          }
      list.add(parsed)
    }
    return Result.success(list)
  }

  /**
   * Parses either an inline [SuccessActionObject] or a [ReusableObject] reference.
   *
   * @param node The success action [JsonNode].
   * @return A [Result] containing [ReusableOr] of [SuccessActionObject] or a
   *   [CddError.ArazzoParseError].
   */
  fun parseSuccessActionOrReusableResult(node: JsonNode): Result<ReusableOr<SuccessActionObject>> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("SuccessAction entry must be an object").toFailure()
    val ref = obj.text("reference")
    if (ref != null) {
      return Result.success(
          ReusableOr.Reference(
              ReusableObject(reference = ref, extensions = SchemaNodeParser.parseExtensions(obj))))
    }
    val action =
        parseSuccessActionResult(obj).getOrElse {
          return Result.failure(it)
        }
    return Result.success(ReusableOr.Inline(action))
  }

  /**
   * Parses an inline [SuccessActionObject].
   *
   * @param node The success action [JsonNode].
   * @return A [Result] containing [SuccessActionObject] or a [CddError.ArazzoParseError].
   */
  fun parseSuccessActionResult(node: JsonNode): Result<SuccessActionObject> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("SuccessAction must be an object").toFailure()
    val name =
        obj.text("name")
            ?: return CddError.ArazzoParseError("SuccessAction missing required 'name'").toFailure()
    val typeRaw =
        obj.text("type")
            ?: return CddError.ArazzoParseError("SuccessAction '$name' missing required 'type'")
                .toFailure()
    val type =
        SuccessActionType.fromWireName(typeRaw).getOrElse {
          return Result.failure(it)
        }
    val parameters =
        parseParameterListResult(obj.get("parameters")).getOrElse {
          return Result.failure(it)
        }
    val criteria =
        parseCriteriaListResult(obj.get("criteria")).getOrElse {
          return Result.failure(it)
        }
    return Result.success(
        SuccessActionObject(
            name = name,
            type = type,
            workflowId = obj.text("workflowId"),
            stepId = obj.text("stepId"),
            parameters = parameters,
            criteria = criteria,
            extensions = SchemaNodeParser.parseExtensions(obj)))
  }

  /**
   * Parses a list of [ReusableOr] [FailureActionObject] items.
   *
   * @param node Optional array [JsonNode].
   * @return A [Result] containing the parsed list or a [CddError.ArazzoParseError].
   */
  fun parseFailureActionListResult(node: JsonNode?): Result<List<ReusableOr<FailureActionObject>>> {
    if (node == null) return Result.success(emptyList())
    val arr =
        node.asArray()
            ?: return CddError.ArazzoParseError("Failure actions must be an array").toFailure()
    val list = mutableListOf<ReusableOr<FailureActionObject>>()
    for (item in arr) {
      val parsed =
          parseFailureActionOrReusableResult(item).getOrElse {
            return Result.failure(it)
          }
      list.add(parsed)
    }
    return Result.success(list)
  }

  /**
   * Parses either an inline [FailureActionObject] or a [ReusableObject] reference.
   *
   * @param node The failure action [JsonNode].
   * @return A [Result] containing [ReusableOr] of [FailureActionObject] or a
   *   [CddError.ArazzoParseError].
   */
  fun parseFailureActionOrReusableResult(node: JsonNode): Result<ReusableOr<FailureActionObject>> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("FailureAction entry must be an object").toFailure()
    val ref = obj.text("reference")
    if (ref != null) {
      return Result.success(
          ReusableOr.Reference(
              ReusableObject(reference = ref, extensions = SchemaNodeParser.parseExtensions(obj))))
    }
    val action =
        parseFailureActionResult(obj).getOrElse {
          return Result.failure(it)
        }
    return Result.success(ReusableOr.Inline(action))
  }

  /**
   * Parses an inline [FailureActionObject].
   *
   * @param node The failure action [JsonNode].
   * @return A [Result] containing [FailureActionObject] or a [CddError.ArazzoParseError].
   */
  fun parseFailureActionResult(node: JsonNode): Result<FailureActionObject> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("FailureAction must be an object").toFailure()
    val name =
        obj.text("name")
            ?: return CddError.ArazzoParseError("FailureAction missing required 'name'").toFailure()
    val typeRaw =
        obj.text("type")
            ?: return CddError.ArazzoParseError("FailureAction '$name' missing required 'type'")
                .toFailure()
    val type =
        FailureActionType.fromWireName(typeRaw).getOrElse {
          return Result.failure(it)
        }
    val parameters =
        parseParameterListResult(obj.get("parameters")).getOrElse {
          return Result.failure(it)
        }
    val criteria =
        parseCriteriaListResult(obj.get("criteria")).getOrElse {
          return Result.failure(it)
        }
    return Result.success(
        FailureActionObject(
            name = name,
            type = type,
            workflowId = obj.text("workflowId"),
            stepId = obj.text("stepId"),
            parameters = parameters,
            retryAfter = obj.double("retryAfter"),
            retryLimit = obj.int("retryLimit"),
            criteria = criteria,
            extensions = SchemaNodeParser.parseExtensions(obj)))
  }

  /**
   * Parses a list of [CriterionObject] items.
   *
   * @param node Optional array [JsonNode].
   * @return A [Result] containing the parsed list or a [CddError.ArazzoParseError].
   */
  fun parseCriteriaListResult(node: JsonNode?): Result<List<CriterionObject>> {
    if (node == null) return Result.success(emptyList())
    val arr =
        node.asArray() ?: return CddError.ArazzoParseError("Criteria must be an array").toFailure()
    val list = mutableListOf<CriterionObject>()
    for (item in arr) {
      val crit =
          parseCriterionResult(item).getOrElse {
            return Result.failure(it)
          }
      list.add(crit)
    }
    return Result.success(list)
  }

  /**
   * Parses a single [CriterionObject].
   *
   * @param node The criterion [JsonNode].
   * @return A [Result] containing [CriterionObject] or a [CddError.ArazzoParseError].
   */
  fun parseCriterionResult(node: JsonNode): Result<CriterionObject> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("Criterion must be an object").toFailure()
    val condition =
        obj.text("condition")
            ?: return CddError.ArazzoParseError("Criterion missing required 'condition'")
                .toFailure()
    val typeNode = obj.get("type")
    val type =
        if (typeNode != null) {
          parseCriterionTypeOrExpressionResult(typeNode).getOrElse {
            return Result.failure(it)
          }
        } else {
          null
        }
    return Result.success(
        CriterionObject(
            context = obj.text("context"),
            condition = condition,
            type = type,
            extensions = SchemaNodeParser.parseExtensions(obj)))
  }

  /**
   * Parses a [CriterionTypeOrExpression] from either a string primitive or an
   * [ExpressionTypeObject] node.
   *
   * @param node The `type` or `targetSelectorType` [JsonNode].
   * @return A [Result] containing [CriterionTypeOrExpression] or a [CddError.ArazzoParseError].
   */
  fun parseCriterionTypeOrExpressionResult(node: JsonNode): Result<CriterionTypeOrExpression> {
    if (node.isTextual) {
      val raw = (node as kotlinx.serialization.json.JsonPrimitive).content
      return CriterionEngineType.fromWireName(raw).map { CriterionTypeOrExpression.Engine(it) }
    }
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError(
                    "Criterion 'type' must be a string or ExpressionTypeObject")
                .toFailure()
    val rawType =
        obj.text("type")
            ?: return CddError.ArazzoParseError("ExpressionTypeObject missing required 'type'")
                .toFailure()
    val engine =
        CriterionEngineType.fromWireName(rawType).getOrElse {
          return Result.failure(it)
        }
    val version =
        obj.text("version")
            ?: return CddError.ArazzoParseError("ExpressionTypeObject missing required 'version'")
                .toFailure()
    return Result.success(
        CriterionTypeOrExpression.Versioned(
            ExpressionTypeObject(
                type = engine,
                version = version,
                extensions = SchemaNodeParser.parseExtensions(obj))))
  }

  /**
   * Parses an `outputs` map of output names to [OutputValue]s.
   *
   * @param node Optional object [JsonNode].
   * @return A [Result] containing the parsed map or a [CddError.ArazzoParseError].
   */
  fun parseOutputsMapResult(node: JsonNode?): Result<Map<String, OutputValue>> {
    if (node == null) return Result.success(emptyMap())
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("'outputs' must be an object").toFailure()
    val map = LinkedHashMap<String, OutputValue>()
    for ((key, valNode) in obj.fields()) {
      if (key.startsWith("x-")) continue
      val outVal =
          parseOutputValueResult(valNode).getOrElse {
            return Result.failure(it)
          }
      map[key] = outVal
    }
    return Result.success(map)
  }

  /**
   * Parses a single [OutputValue] from either a runtime expression string or a [SelectorObject].
   *
   * @param node The output value [JsonNode].
   * @return A [Result] containing [OutputValue] or a [CddError.ArazzoParseError].
   */
  fun parseOutputValueResult(node: JsonNode): Result<OutputValue> {
    if (node.isTextual) {
      return Result.success(OutputValue.Expression(node.textValue().toString()))
    }
    val selector =
        parseSelectorResult(node).getOrElse {
          return Result.failure(it)
        }
    return Result.success(OutputValue.Selector(selector))
  }

  /**
   * Parses a structured Arazzo 1.1.0 [SelectorObject].
   *
   * @param node The selector [JsonNode].
   * @return A [Result] containing [SelectorObject] or a [CddError.ArazzoParseError].
   */
  fun parseSelectorResult(node: JsonNode): Result<SelectorObject> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError(
                    "Output value must be a string expression or SelectorObject")
                .toFailure()
    val context =
        obj.text("context")
            ?: return CddError.ArazzoParseError("SelectorObject missing required 'context'")
                .toFailure()
    val selector =
        obj.text("selector")
            ?: return CddError.ArazzoParseError("SelectorObject missing required 'selector'")
                .toFailure()
    val typeNode = obj.get("type")
    val type =
        if (typeNode != null) {
          parseCriterionTypeOrExpressionResult(typeNode).getOrElse {
            return Result.failure(it)
          }
        } else {
          null
        }
    return Result.success(
        SelectorObject(
            context = context,
            selector = selector,
            type = type,
            extensions = SchemaNodeParser.parseExtensions(obj)))
  }

  /**
   * Parses a step's [ArazzoRequestBody].
   *
   * @param node The `requestBody` [JsonNode].
   * @return A [Result] containing [ArazzoRequestBody] or a [CddError.ArazzoParseError].
   */
  fun parseRequestBodyResult(node: JsonNode?): Result<ArazzoRequestBody> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("'requestBody' must be an object").toFailure()
    val payloadNode = obj.get("payload")
    val payload = payloadNode?.let { OpenApiValues.fromJsonElement(it) }
    val replacementsNode = obj.get("replacements")
    val replacements = mutableListOf<PayloadReplacementObject>()
    if (replacementsNode != null) {
      val arr =
          replacementsNode.asArray()
              ?: return CddError.ArazzoParseError("'requestBody.replacements' must be an array")
                  .toFailure()
      for (item in arr) {
        val rep =
            parsePayloadReplacementResult(item).getOrElse {
              return Result.failure(it)
            }
        replacements.add(rep)
      }
    }
    return Result.success(
        ArazzoRequestBody(
            contentType = obj.text("contentType"),
            payload = payload,
            replacements = replacements,
            extensions = SchemaNodeParser.parseExtensions(obj)))
  }

  /**
   * Parses a single [PayloadReplacementObject].
   *
   * @param node The replacement [JsonNode].
   * @return A [Result] containing [PayloadReplacementObject] or a [CddError.ArazzoParseError].
   */
  fun parsePayloadReplacementResult(node: JsonNode): Result<PayloadReplacementObject> {
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("PayloadReplacement must be an object").toFailure()
    val target =
        obj.text("target")
            ?: return CddError.ArazzoParseError("PayloadReplacement missing required 'target'")
                .toFailure()
    val valueNode =
        obj.get("value")
            ?: return CddError.ArazzoParseError("PayloadReplacement missing required 'value'")
                .toFailure()
    val selectorTypeNode = obj.get("targetSelectorType")
    val selectorType =
        if (selectorTypeNode != null) {
          parseCriterionTypeOrExpressionResult(selectorTypeNode).getOrElse {
            return Result.failure(it)
          }
        } else {
          null
        }
    return Result.success(
        PayloadReplacementObject(
            target = target,
            targetSelectorType = selectorType,
            value = OpenApiValues.fromJsonElement(valueNode),
            extensions = SchemaNodeParser.parseExtensions(obj)))
  }

  /**
   * Parses the reusable `components` object of an Arazzo document.
   *
   * @param node Optional `components` [JsonNode].
   * @return A [Result] containing [ArazzoComponents] (or `null` if absent) or a
   *   [CddError.ArazzoParseError].
   */
  fun parseComponentsResult(node: JsonNode?): Result<ArazzoComponents?> {
    if (node == null) return Result.success(null)
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("'components' must be an object", "components")
                .toFailure()
    val inputs = LinkedHashMap<String, SchemaProperty>()
    val inputsObj = obj.get("inputs")?.asObject()
    if (inputsObj != null) {
      for ((key, schemaNode) in inputsObj.fields()) {
        if (!key.startsWith("x-")) {
          inputs[key] = SchemaNodeParser.parseSchemaProperty(schemaNode)
        }
      }
    }
    val parameters = LinkedHashMap<String, ArazzoParameter>()
    val paramsObj = obj.get("parameters")?.asObject()
    if (paramsObj != null) {
      for ((key, paramNode) in paramsObj.fields()) {
        if (key.startsWith("x-")) continue
        val param =
            parseParameterResult(paramNode).getOrElse {
              return Result.failure(it)
            }
        parameters[key] = param
      }
    }
    val successActions = LinkedHashMap<String, SuccessActionObject>()
    val successObj = obj.get("successActions")?.asObject()
    if (successObj != null) {
      for ((key, actionNode) in successObj.fields()) {
        if (key.startsWith("x-")) continue
        val action =
            parseSuccessActionResult(actionNode).getOrElse {
              return Result.failure(it)
            }
        successActions[key] = action
      }
    }
    val failureActions = LinkedHashMap<String, FailureActionObject>()
    val failureObj = obj.get("failureActions")?.asObject()
    if (failureObj != null) {
      for ((key, actionNode) in failureObj.fields()) {
        if (key.startsWith("x-")) continue
        val action =
            parseFailureActionResult(actionNode).getOrElse {
              return Result.failure(it)
            }
        failureActions[key] = action
      }
    }
    return Result.success(
        ArazzoComponents(
            inputs = inputs,
            parameters = parameters,
            successActions = successActions,
            failureActions = failureActions,
            extensions = SchemaNodeParser.parseExtensions(obj)))
  }

  private fun parseStringList(node: JsonNode?): List<String> {
    val arr = node.asArray() ?: return emptyList()
    return arr.mapNotNull { it.textValue() }
  }

  private fun resolveFormat(source: String, hint: Format): Format {
    if (hint != Format.AUTO) return hint
    val trimmed = source.trimStart()
    return if (trimmed.startsWith("{") || trimmed.startsWith("[")) Format.JSON else Format.YAML
  }

  private fun readTreeResult(source: String, format: Format): Result<JsonNode> {
    return if (format == Format.YAML) {
      Result.success(YamlToJsonElementConverter.parseYaml(source))
    } else {
      parseJsonElementResult(source)
    }
  }
}
