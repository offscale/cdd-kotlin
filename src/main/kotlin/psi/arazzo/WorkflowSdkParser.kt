/**
 * Parses Kotlin Client SDK workflow orchestrators (`WorkflowOrchestrator.kt` and DTO models) via
 * Kotlin PSI into [domain.ArazzoDefinition] specifications with full [kotlin.Result] percolation.
 */
package psi.arazzo

import domain.ArazzoDefinition
import domain.ArazzoInfo
import domain.ArazzoParameter
import domain.ArazzoParameterLocation
import domain.CddError
import domain.OpenApiString
import domain.OutputValue
import domain.ReusableOr
import domain.SchemaProperty
import domain.SourceDescription
import domain.SourceDescriptionType
import domain.StepObject
import domain.Workflow
import domain.toFailure
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import psi.PsiInfrastructure
import psi.TypeMappers

/**
 * Reverse-engineers an [ArazzoDefinition] from Kotlin PSI source files (`WorkflowOrchestrator.kt`
 * and optional `WorkflowModels.kt`).
 */
object WorkflowSdkParser {
  /**
   * Parses a map of Kotlin file names to source code strings into an [ArazzoDefinition] wrapped in
   * a [Result].
   *
   * @param files Map of relative file names to Kotlin source code strings.
   * @return A [Result] containing the reconstructed [ArazzoDefinition] or a [CddError].
   */
  fun parseWorkflowSdkResult(files: Map<String, String>): Result<ArazzoDefinition> {
    if (files.isEmpty()) {
      return CddError.ArazzoParseError("No Kotlin source files provided to WorkflowSdkParser")
          .toFailure()
    }
    val sep = "${Char(10)}${Char(10)}"
    val combined = files.values.joinToString(sep)
    return parseWorkflowFileResult(combined)
  }

  /**
   * Parses a single Kotlin source file (or combined source string) into an [ArazzoDefinition]
   * wrapped in a [Result].
   *
   * @param sourceCode Kotlin source code containing a workflow orchestrator class or functions.
   * @return A [Result] containing the reconstructed [ArazzoDefinition] or a [CddError].
   */
  fun parseWorkflowFileResult(sourceCode: String): Result<ArazzoDefinition> {
    if (sourceCode.isBlank()) {
      return CddError.ArazzoParseError("Kotlin workflow source code is blank").toFailure()
    }
    val factory = PsiInfrastructure.createPsiFactory()
    val ktFile = factory.createFile("WorkflowOrchestrator.kt", sourceCode)
    return parseKtFileResult(ktFile)
  }

  /**
   * Extracts an [ArazzoDefinition] from a parsed [KtFile].
   *
   * @param ktFile The parsed Kotlin PSI [KtFile].
   * @return A [Result] containing the extracted [ArazzoDefinition] or a [CddError].
   */
  fun parseKtFileResult(ktFile: KtFile): Result<ArazzoDefinition> {
    val classes = ktFile.collectDescendantsOfType<KtClass>()
    for (ktClass in classes) {
      val docText = ktClass.docComment?.text
      val specTag = ArazzoMetadataCodec.extractTagPayload(docText, "@arazzo-spec")
      if (specTag != null) {
        return ArazzoMetadataCodec.decodeDefinitionTagResult(specTag)
      }
    }
    return parsePurePsiAstResult(ktFile)
  }

  private fun parsePurePsiAstResult(ktFile: KtFile): Result<ArazzoDefinition> {
    val classes = ktFile.collectDescendantsOfType<KtClass>()
    val inputSchemasByClass = LinkedHashMap<String, SchemaProperty>()
    for (cls in classes) {
      val name = cls.name.toString()
      if (cls.isData() && name.endsWith("Inputs")) {
        inputSchemasByClass[name] = parseDataClassToInputSchema(cls)
      }
    }

    val orchestratorClass = classes.firstOrNull { !it.isData() }
    val classDoc = orchestratorClass?.docComment
    val title =
        (if (classDoc != null) extractCleanSummary(classDoc.text) else null)
            ?: orchestratorClass?.name
            ?: "Generated Workflow Specification"

    val arazzoVersion = extractStringPropertyValue(orchestratorClass, "arazzoVersion") ?: "1.1.0"
    val selfUri = extractStringPropertyValue(orchestratorClass, "selfUri")
    val sourceDescriptions = extractSourceDescriptions(orchestratorClass)

    val functions = ktFile.collectDescendantsOfType<KtNamedFunction>()
    val workflows =
        functions.mapNotNull { fn ->
          val wfTag = ArazzoMetadataCodec.extractTagPayload(fn.docComment?.text, "@arazzo-workflow")
          if (wfTag != null) {
            ArazzoMetadataCodec.decodeWorkflowTagResult(wfTag).getOrNull()
          } else {
            parseFunctionAsWorkflow(fn, inputSchemasByClass)
          }
        }

    if (workflows.isEmpty()) {
      return CddError.ArazzoParseError("No workflow methods found in Kotlin source").toFailure()
    }

    val effectiveSources =
        if (sourceDescriptions.isNotEmpty()) {
          sourceDescriptions
        } else {
          listOf(
              SourceDescription(
                  name = "default", url = "./openapi.json", type = SourceDescriptionType.OPENAPI))
        }

    return Result.success(
        ArazzoDefinition(
            arazzo = arazzoVersion,
            self = selfUri,
            info = ArazzoInfo(title = title, version = "1.0.0"),
            sourceDescriptions = effectiveSources,
            workflows = workflows))
  }

  private fun parseDataClassToInputSchema(cls: KtClass): SchemaProperty {
    val props = LinkedHashMap<String, SchemaProperty>()
    val required = mutableListOf<String>()
    for (param in cls.primaryConstructorParameters) {
      val paramName = param.name.toString()
      if (paramName == "raw") continue
      val typeText = param.text.substringAfter(":").substringBefore("=").trim()
      val isNullable = typeText.endsWith("?")
      val mapped = TypeMappers.kotlinToSchemaProperty(typeText.removeSuffix("?"))
      props[paramName] = mapped
      if (!isNullable) {
        required.add(paramName)
      }
    }
    return SchemaProperty(types = setOf("object"), properties = props, required = required)
  }

  private fun parseFunctionAsWorkflow(
      fn: KtNamedFunction,
      inputSchemasByClass: Map<String, SchemaProperty>
  ): Workflow {
    val fnName = fn.name.toString()
    val fnDoc = fn.docComment
    val summary = if (fnDoc != null) extractCleanSummary(fnDoc.text) else null
    val firstParam = fn.valueParameters.firstOrNull()
    val inputs =
        if (firstParam != null) {
          val typeName =
              firstParam.text.substringAfter(":").substringBefore("=").trim().removeSuffix("?")
          inputSchemasByClass[typeName]
        } else {
          null
        }
    val bodyText = fn.text
    val steps = extractStepsFromBodyText(bodyText, fnName)
    val outputs = extractOutputsFromBodyText(bodyText)
    return Workflow(
        workflowId = fnName, summary = summary, inputs = inputs, steps = steps, outputs = outputs)
  }

  private fun extractStepsFromBodyText(
      bodyText: String,
      fallbackWorkflowId: String
  ): List<StepObject> {
    val steps = mutableListOf<StepObject>()
    val executorRegex =
        Regex(
            """stepExecutor\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*([^,]+?(?:\([^)]*\))?)\s*,\s*([^)]+)\)""")
    for (match in executorRegex.findAll(bodyText)) {
      val stepId = match.groupValues[1]
      val target = match.groupValues[2]
      val paramsRaw = match.groupValues[3]
      val params = extractInlineParameters(paramsRaw)
      val isPath = target.contains("#/paths/")
      val isChannel = target.startsWith("/") && !isPath
      steps.add(
          StepObject(
              stepId = stepId,
              operationId = if (!isPath && !isChannel) target else null,
              operationPath = if (isPath) target else null,
              channelPath = if (isChannel) target else null,
              parameters = params))
    }
    if (steps.isNotEmpty()) return steps

    val clientCallRegex = Regex("""([a-zA-Z0-9_]+Client)\.([a-zA-Z0-9_]+)\(""")
    for ((idx, match) in clientCallRegex.findAll(bodyText).withIndex()) {
      val opId = match.groupValues[2]
      steps.add(StepObject(stepId = "step_${idx + 1}_$opId", operationId = opId))
    }
    if (steps.isEmpty()) {
      steps.add(
          StepObject(
              stepId = "${fallbackWorkflowId}_step_1",
              operationId = "${fallbackWorkflowId}Operation"))
    }
    return steps
  }

  private fun extractInlineParameters(paramsRaw: String): List<ReusableOr<ArazzoParameter>> {
    val pairRegex = Regex(""""([^"]+)"\s+to\s+"([^"]*)"""")
    return pairRegex
        .findAll(paramsRaw)
        .map { m ->
          ReusableOr.Inline(
              ArazzoParameter(
                  name = m.groupValues[1],
                  location = ArazzoParameterLocation.QUERY,
                  value = OpenApiString(m.groupValues[2])))
        }
        .toList()
  }

  private fun extractOutputsFromBodyText(bodyText: String): Map<String, OutputValue> {
    val outputs = LinkedHashMap<String, OutputValue>()
    val outAssignRegex =
        Regex("""([a-zA-Z0-9_]+)\s*=\s*stepOutputs\["([^"]+)"\]\?\.\s*get\("([^"]+)"\)""")
    for (match in outAssignRegex.findAll(bodyText)) {
      val outKey = match.groupValues[1]
      val stepId = match.groupValues[2]
      val stepOut = match.groupValues[3]
      outputs[outKey] = OutputValue.Expression("\$steps.$stepId.outputs.$stepOut")
    }
    return outputs
  }

  private fun extractStringPropertyValue(ktClass: KtClass?, propName: String): String? {
    if (ktClass == null) return null
    val prop =
        ktClass.collectDescendantsOfType<KtProperty>().firstOrNull { it.name == propName }
            ?: return null
    val initExpr = prop.initializer ?: return null
    val initText = initExpr.text.trim()
    if (initText == "null") return null
    return initText.removeSurrounding('"'.toString())
  }

  private fun extractSourceDescriptions(ktClass: KtClass?): List<SourceDescription> {
    if (ktClass == null) return emptyList()
    val param =
        ktClass.primaryConstructorParameters.firstOrNull { it.name == "sourceUrls" }
            ?: return emptyList()
    val paramText = param.text
    val pairRegex = Regex(""""([^"]+)"\s+to\s+"([^"]+)"""")
    return pairRegex
        .findAll(paramText)
        .map {
          SourceDescription(
              name = it.groupValues[1],
              url = it.groupValues[2],
              type = SourceDescriptionType.OPENAPI)
        }
        .toList()
  }

  private fun extractCleanSummary(docText: String): String? {
    return docText
        .lines()
        .map { it.trim().removePrefix("/**").removeSuffix("*/").removePrefix("*").trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("@") }
  }
}
