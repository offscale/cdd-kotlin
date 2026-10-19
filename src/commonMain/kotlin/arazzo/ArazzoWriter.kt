/**
 * Multiplatform Arazzo 1.1.0 JSON and YAML document serializer.
 *
 * Serializes [domain.ArazzoDefinition] instances into strongly-typed [domain.OpenApiObject] ASTs
 * and formats them as JSON or YAML strings with full [kotlin.Result] percolation.
 */
package arazzo

import domain.ArazzoComponents
import domain.ArazzoDefinition
import domain.ArazzoInfo
import domain.ArazzoParameter
import domain.ArazzoRequestBody
import domain.CriterionObject
import domain.CriterionTypeOrExpression
import domain.ExpressionTypeObject
import domain.FailureActionObject
import domain.OpenApiArray
import domain.OpenApiInt
import domain.OpenApiNumber
import domain.OpenApiObject
import domain.OpenApiString
import domain.OpenApiValue
import domain.OutputValue
import domain.PayloadReplacementObject
import domain.ReusableObject
import domain.ReusableOr
import domain.SchemaProperty
import domain.SelectorObject
import domain.SourceDescription
import domain.StepObject
import domain.SuccessActionObject
import domain.Workflow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import openapi.OpenApiWriter
import openapi.parser.YamlToJsonElementConverter

/**
 * Serializes [ArazzoDefinition] domain models into JSON or YAML strings without using untyped `Any`
 * maps.
 */
class ArazzoWriter {
  private val prettyJson = Json { prettyPrint = true }
  private val compactJson = Json { prettyPrint = false }
  private val openApiWriter = OpenApiWriter()

  /** Supported output formats for Arazzo serialization. */
  enum class Format {
    /** Auto-detect format from file extension or default to JSON. */
    AUTO,

    /** JSON output format. */
    JSON,

    /** YAML output format. */
    YAML
  }

  /**
   * Serializes an [ArazzoDefinition] into a JSON or YAML string wrapped in a [Result].
   *
   * @param definition The [ArazzoDefinition] to serialize.
   * @param format Target output [Format] (defaults to [Format.JSON]).
   * @param fileName Optional file name used when [format] is [Format.AUTO].
   * @return A [Result] containing the serialized specification string.
   */
  fun writeResult(definition: ArazzoDefinition, format: Format, fileName: String?): Result<String> {
    val resolved = resolveFormat(format, fileName)
    return if (resolved == Format.YAML) {
      writeYamlResult(definition)
    } else {
      writeJsonResult(definition, pretty = true)
    }
  }

  /**
   * Serializes an [ArazzoDefinition] into a JSON string wrapped in a [Result].
   *
   * @param definition The [ArazzoDefinition] to serialize.
   * @param pretty Whether to pretty-print the JSON output.
   * @return A [Result] containing the serialized JSON string.
   */
  fun writeJsonResult(definition: ArazzoDefinition, pretty: Boolean): Result<String> {
    return Result.success(writeJson(definition, pretty))
  }

  /**
   * Serializes an [ArazzoDefinition] into a JSON string.
   *
   * @param definition The [ArazzoDefinition] to serialize.
   * @param pretty Whether to pretty-print the JSON output.
   * @return The serialized JSON string.
   */
  fun writeJson(definition: ArazzoDefinition, pretty: Boolean = true): String {
    val obj = toOpenApiObject(definition)
    val jsonSerializer = if (pretty) prettyJson else compactJson
    return jsonSerializer.encodeToString(JsonElement.serializer(), obj.toJsonElement())
  }

  /**
   * Serializes an [ArazzoDefinition] into a YAML string wrapped in a [Result].
   *
   * @param definition The [ArazzoDefinition] to serialize.
   * @return A [Result] containing the serialized YAML string.
   */
  fun writeYamlResult(definition: ArazzoDefinition): Result<String> {
    return Result.success(writeYaml(definition))
  }

  /**
   * Serializes an [ArazzoDefinition] into a YAML string.
   *
   * @param definition The [ArazzoDefinition] to serialize.
   * @return The serialized YAML string.
   */
  fun writeYaml(definition: ArazzoDefinition): String {
    val obj = toOpenApiObject(definition)
    return YamlToJsonElementConverter.toYaml(obj.toJsonElement())
  }

  /**
   * Converts an [ArazzoDefinition] into a strongly-typed [OpenApiObject] wrapped in a [Result].
   *
   * @param definition The [ArazzoDefinition] to convert.
   * @return A [Result] containing the root [OpenApiObject].
   */
  fun toOpenApiObjectResult(definition: ArazzoDefinition): Result<OpenApiObject> {
    return Result.success(toOpenApiObject(definition))
  }

  /**
   * Converts an [ArazzoDefinition] into a strongly-typed [OpenApiObject].
   *
   * @param definition The [ArazzoDefinition] to convert.
   * @return The root [OpenApiObject].
   */
  fun toOpenApiObject(definition: ArazzoDefinition): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["arazzo"] = OpenApiString(definition.arazzo)
    if (definition.self != null) {
      map["\$self"] = OpenApiString(definition.self)
    }
    map["info"] = infoToObject(definition.info)
    map["sourceDescriptions"] =
        OpenApiArray(definition.sourceDescriptions.map { sourceDescriptionToObject(it) })
    map["workflows"] = OpenApiArray(definition.workflows.map { workflowToObject(it) })
    if (definition.components != null) {
      val compsObj = componentsToObject(definition.components)
      if (compsObj.properties.isNotEmpty()) {
        map["components"] = compsObj
      }
    }
    map.putAll(definition.extensions)
    return OpenApiObject(map)
  }

  private fun infoToObject(info: ArazzoInfo): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["title"] = OpenApiString(info.title)
    if (info.summary != null) map["summary"] = OpenApiString(info.summary)
    if (info.description != null) map["description"] = OpenApiString(info.description)
    map["version"] = OpenApiString(info.version)
    map.putAll(info.extensions)
    return OpenApiObject(map)
  }

  private fun sourceDescriptionToObject(source: SourceDescription): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["name"] = OpenApiString(source.name)
    map["url"] = OpenApiString(source.url)
    if (source.type != null) {
      map["type"] = OpenApiString(source.type.wireName)
    }
    map.putAll(source.extensions)
    return OpenApiObject(map)
  }

  private fun workflowToObject(workflow: Workflow): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["workflowId"] = OpenApiString(workflow.workflowId)
    if (workflow.summary != null) map["summary"] = OpenApiString(workflow.summary)
    if (workflow.description != null) map["description"] = OpenApiString(workflow.description)
    if (workflow.inputs != null) {
      map["inputs"] = schemaPropertyToOpenApiValue(workflow.inputs)
    }
    if (workflow.dependsOn.isNotEmpty()) {
      map["dependsOn"] = OpenApiArray(workflow.dependsOn.map { OpenApiString(it) })
    }
    map["steps"] = OpenApiArray(workflow.steps.map { stepToObject(it) })
    if (workflow.successActions.isNotEmpty()) {
      map["successActions"] =
          OpenApiArray(workflow.successActions.map { successActionOrReusableToObject(it) })
    }
    if (workflow.failureActions.isNotEmpty()) {
      map["failureActions"] =
          OpenApiArray(workflow.failureActions.map { failureActionOrReusableToObject(it) })
    }
    if (workflow.outputs.isNotEmpty()) {
      map["outputs"] = outputsToObject(workflow.outputs)
    }
    if (workflow.parameters.isNotEmpty()) {
      map["parameters"] = OpenApiArray(workflow.parameters.map { parameterOrReusableToObject(it) })
    }
    map.putAll(workflow.extensions)
    return OpenApiObject(map)
  }

  private fun stepToObject(step: StepObject): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["stepId"] = OpenApiString(step.stepId)
    if (step.description != null) map["description"] = OpenApiString(step.description)
    if (step.operationId != null) map["operationId"] = OpenApiString(step.operationId)
    if (step.operationPath != null) map["operationPath"] = OpenApiString(step.operationPath)
    if (step.channelPath != null) map["channelPath"] = OpenApiString(step.channelPath)
    if (step.workflowId != null) map["workflowId"] = OpenApiString(step.workflowId)
    if (step.parameters.isNotEmpty()) {
      map["parameters"] = OpenApiArray(step.parameters.map { parameterOrReusableToObject(it) })
    }
    if (step.requestBody != null) {
      map["requestBody"] = requestBodyToObject(step.requestBody)
    }
    if (step.successCriteria.isNotEmpty()) {
      map["successCriteria"] = OpenApiArray(step.successCriteria.map { criterionToObject(it) })
    }
    if (step.onSuccess.isNotEmpty()) {
      map["onSuccess"] = OpenApiArray(step.onSuccess.map { successActionOrReusableToObject(it) })
    }
    if (step.onFailure.isNotEmpty()) {
      map["onFailure"] = OpenApiArray(step.onFailure.map { failureActionOrReusableToObject(it) })
    }
    if (step.outputs.isNotEmpty()) {
      map["outputs"] = outputsToObject(step.outputs)
    }
    if (step.timeout != null) {
      map["timeout"] = OpenApiInt(step.timeout)
    }
    if (step.correlationId != null) {
      map["correlationId"] = OpenApiString(step.correlationId)
    }
    if (step.action != null) {
      map["action"] = OpenApiString(step.action.wireName)
    }
    if (step.dependsOn.isNotEmpty()) {
      map["dependsOn"] = OpenApiArray(step.dependsOn.map { OpenApiString(it) })
    }
    map.putAll(step.extensions)
    return OpenApiObject(map)
  }

  private fun parameterOrReusableToObject(item: ReusableOr<ArazzoParameter>): OpenApiObject =
      when (item) {
        is ReusableOr.Inline -> parameterToObject(item.value)
        is ReusableOr.Reference -> reusableToObject(item.reusable)
      }

  private fun parameterToObject(param: ArazzoParameter): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["name"] = OpenApiString(param.name)
    if (param.location != null) {
      map["in"] = OpenApiString(param.location.wireName)
    }
    map["value"] = param.value
    map.putAll(param.extensions)
    return OpenApiObject(map)
  }

  private fun reusableToObject(reusable: ReusableObject): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["reference"] = OpenApiString(reusable.reference)
    if (reusable.value != null) {
      map["value"] = reusable.value
    }
    map.putAll(reusable.extensions)
    return OpenApiObject(map)
  }

  private fun successActionOrReusableToObject(
      item: ReusableOr<SuccessActionObject>
  ): OpenApiObject =
      when (item) {
        is ReusableOr.Inline -> successActionToObject(item.value)
        is ReusableOr.Reference -> reusableToObject(item.reusable)
      }

  private fun successActionToObject(action: SuccessActionObject): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["name"] = OpenApiString(action.name)
    map["type"] = OpenApiString(action.type.wireName)
    if (action.workflowId != null) map["workflowId"] = OpenApiString(action.workflowId)
    if (action.stepId != null) map["stepId"] = OpenApiString(action.stepId)
    if (action.parameters.isNotEmpty()) {
      map["parameters"] = OpenApiArray(action.parameters.map { parameterOrReusableToObject(it) })
    }
    if (action.criteria.isNotEmpty()) {
      map["criteria"] = OpenApiArray(action.criteria.map { criterionToObject(it) })
    }
    map.putAll(action.extensions)
    return OpenApiObject(map)
  }

  private fun failureActionOrReusableToObject(
      item: ReusableOr<FailureActionObject>
  ): OpenApiObject =
      when (item) {
        is ReusableOr.Inline -> failureActionToObject(item.value)
        is ReusableOr.Reference -> reusableToObject(item.reusable)
      }

  private fun failureActionToObject(action: FailureActionObject): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["name"] = OpenApiString(action.name)
    map["type"] = OpenApiString(action.type.wireName)
    if (action.workflowId != null) map["workflowId"] = OpenApiString(action.workflowId)
    if (action.stepId != null) map["stepId"] = OpenApiString(action.stepId)
    if (action.parameters.isNotEmpty()) {
      map["parameters"] = OpenApiArray(action.parameters.map { parameterOrReusableToObject(it) })
    }
    if (action.retryAfter != null) {
      val asLong = action.retryAfter.toLong()
      map["retryAfter"] =
          if (action.retryAfter == asLong.toDouble()) OpenApiInt(asLong)
          else OpenApiNumber(action.retryAfter)
    }
    if (action.retryLimit != null) {
      map["retryLimit"] = OpenApiInt(action.retryLimit.toLong())
    }
    if (action.criteria.isNotEmpty()) {
      map["criteria"] = OpenApiArray(action.criteria.map { criterionToObject(it) })
    }
    map.putAll(action.extensions)
    return OpenApiObject(map)
  }

  private fun criterionToObject(criterion: CriterionObject): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    if (criterion.context != null) map["context"] = OpenApiString(criterion.context)
    map["condition"] = OpenApiString(criterion.condition)
    if (criterion.type != null) {
      map["type"] = criterionTypeOrExpressionToValue(criterion.type)
    }
    map.putAll(criterion.extensions)
    return OpenApiObject(map)
  }

  private fun criterionTypeOrExpressionToValue(type: CriterionTypeOrExpression): OpenApiValue =
      when (type) {
        is CriterionTypeOrExpression.Engine -> OpenApiString(type.type.wireName)
        is CriterionTypeOrExpression.Versioned -> expressionTypeObjectToObject(type.expressionType)
      }

  private fun expressionTypeObjectToObject(expr: ExpressionTypeObject): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["type"] = OpenApiString(expr.type.wireName)
    map["version"] = OpenApiString(expr.version)
    map.putAll(expr.extensions)
    return OpenApiObject(map)
  }

  private fun outputsToObject(outputs: Map<String, OutputValue>): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    for ((key, value) in outputs) {
      map[key] =
          when (value) {
            is OutputValue.Expression -> OpenApiString(value.expression)
            is OutputValue.Selector -> selectorToObject(value.selector)
          }
    }
    return OpenApiObject(map)
  }

  private fun selectorToObject(selector: SelectorObject): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["context"] = OpenApiString(selector.context)
    map["selector"] = OpenApiString(selector.selector)
    if (selector.type != null) {
      map["type"] = criterionTypeOrExpressionToValue(selector.type)
    }
    map.putAll(selector.extensions)
    return OpenApiObject(map)
  }

  private fun requestBodyToObject(body: ArazzoRequestBody): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    if (body.contentType != null) map["contentType"] = OpenApiString(body.contentType)
    if (body.payload != null) map["payload"] = body.payload
    if (body.replacements.isNotEmpty()) {
      map["replacements"] = OpenApiArray(body.replacements.map { payloadReplacementToObject(it) })
    }
    map.putAll(body.extensions)
    return OpenApiObject(map)
  }

  private fun payloadReplacementToObject(rep: PayloadReplacementObject): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["target"] = OpenApiString(rep.target)
    if (rep.targetSelectorType != null) {
      map["targetSelectorType"] = criterionTypeOrExpressionToValue(rep.targetSelectorType)
    }
    map["value"] = rep.value
    map.putAll(rep.extensions)
    return OpenApiObject(map)
  }

  private fun componentsToObject(components: ArazzoComponents): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    if (components.inputs.isNotEmpty()) {
      val inputsMap = LinkedHashMap<String, OpenApiValue>()
      for ((name, schema) in components.inputs) {
        inputsMap[name] = schemaPropertyToOpenApiValue(schema)
      }
      map["inputs"] = OpenApiObject(inputsMap)
    }
    if (components.parameters.isNotEmpty()) {
      map["parameters"] =
          OpenApiObject(components.parameters.mapValues { (_, v) -> parameterToObject(v) })
    }
    if (components.successActions.isNotEmpty()) {
      map["successActions"] =
          OpenApiObject(components.successActions.mapValues { (_, v) -> successActionToObject(v) })
    }
    if (components.failureActions.isNotEmpty()) {
      map["failureActions"] =
          OpenApiObject(components.failureActions.mapValues { (_, v) -> failureActionToObject(v) })
    }
    map.putAll(components.extensions)
    return OpenApiObject(map)
  }

  private fun schemaPropertyToOpenApiValue(schema: SchemaProperty): OpenApiValue =
      openApiWriter.schemaToOpenApiValue(schema)

  private fun resolveFormat(format: Format, fileName: String?): Format {
    if (format != Format.AUTO) return format
    val lower = fileName?.lowercase().orEmpty()
    return if (lower.endsWith(".yaml") || lower.endsWith(".yml")) Format.YAML else Format.JSON
  }
}
