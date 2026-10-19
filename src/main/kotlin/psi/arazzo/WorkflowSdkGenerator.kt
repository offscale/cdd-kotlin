/**
 * Generates Kotlin Multiplatform Client SDK workflow orchestrators (`WorkflowOrchestrator.kt`) and
 * `@Serializable` workflow input/output DTOs from Arazzo 1.1.0 [domain.ArazzoDefinition]s with full
 * [kotlin.Result] percolation.
 */
package psi.arazzo

import domain.ArazzoComponents
import domain.ArazzoDefinition
import domain.FailureActionType
import domain.OutputValue
import domain.ReusableOr
import domain.SchemaProperty
import domain.StepObject
import domain.SuccessActionType
import domain.Workflow
import domain.mapResult
import org.jetbrains.kotlin.psi.KtFile
import psi.PsiInfrastructure
import psi.TypeMappers
import psi.buildKotlinFile
import psi.capitalizeName
import psi.decapitalizeName
import psi.escapeKotlinKeyword

/**
 * Generates strongly-typed Client SDK workflow orchestration code and DTOs from an
 * [ArazzoDefinition].
 */
object WorkflowSdkGenerator {
  private const val Q = '"'
  private val BS = Char(92)
  private const val DL = '$'
  private val NL = Char(10)
  private val CR = Char(13)

  /**
   * Generates all Client SDK workflow Kotlin source files (keyed by relative file name) wrapped in
   * a [Result].
   *
   * @param packageName Base package name (e.g. `"com.example"`).
   * @param definition The [ArazzoDefinition] to compile.
   * @return A [Result] containing a map of file names to generated Kotlin source strings.
   */
  fun generateWorkflowSdkSourcesResult(
      packageName: String,
      definition: ArazzoDefinition
  ): Result<Map<String, String>> {
    val files = LinkedHashMap<String, String>()
    val workflowsPkg = "$packageName.workflows"
    files["WorkflowModels.kt"] = generateWorkflowModelsSource(workflowsPkg, definition)
    files["WorkflowOrchestrator.kt"] = generateOrchestratorSource(workflowsPkg, definition)
    return Result.success(files)
  }

  /**
   * Generates parsed PSI [KtFile]s for the Client SDK workflow orchestrator and models wrapped in a
   * [Result].
   *
   * @param packageName Base package name (e.g. `"com.example"`).
   * @param definition The [ArazzoDefinition] to compile.
   * @return A [Result] containing a map of file names to [KtFile] instances.
   */
  fun generateWorkflowSdkKtFilesResult(
      packageName: String,
      definition: ArazzoDefinition
  ): Result<Map<String, KtFile>> =
      generateWorkflowSdkSourcesResult(packageName, definition).mapResult { sources ->
        val factory = PsiInfrastructure.createPsiFactory()
        val ktFiles = LinkedHashMap<String, KtFile>()
        for ((name, src) in sources) {
          ktFiles[name] = factory.createFile(name, src)
        }
        ktFiles
      }

  /**
   * Generates `WorkflowModels.kt` containing `@Serializable` input and output data classes for
   * every [Workflow] and reusable [ArazzoComponents.inputs].
   *
   * @param workflowsPkg Target package name (`<base>.workflows`).
   * @param definition The [ArazzoDefinition].
   * @return The Kotlin source string for `WorkflowModels.kt`.
   */
  fun generateWorkflowModelsSource(workflowsPkg: String, definition: ArazzoDefinition): String {
    val src =
        buildKotlinFile(workflowsPkg) {
          kdoc(
              "Generated Arazzo 1.1.0 workflow input and output DTO models for ${definition.info.title}.",
              "Version: ${definition.info.version}")
          import("kotlinx.serialization.Serializable")
          import("kotlinx.serialization.json.JsonElement")

          val compInputs = definition.components?.inputs.orEmpty()
          for ((compName, schema) in compInputs) {
            val className = "${toPascalIdentifier(compName)}ComponentInput"
            emitInputDataClass(
                className, "Reusable component input '$compName'.", schema, definition.components)
            line()
          }

          for (wf in definition.workflows) {
            val pascal = toPascalIdentifier(wf.workflowId)
            val resolvedInputs = resolveInputSchema(wf.inputs, definition.components)
            emitInputDataClass(
                "${pascal}Inputs",
                wf.summary ?: "Inputs for workflow '${wf.workflowId}'.",
                resolvedInputs,
                definition.components)
            line()
            emitOutputDataClass(
                "${pascal}Outputs", "Outputs produced by workflow '${wf.workflowId}'.", wf)
            line()
          }
        }
    return "/** Generated Arazzo 1.1.0 workflow DTO models. */$NL$src"
  }

  private fun psi.KotlinSourceBuilder.emitInputDataClass(
      className: String,
      summary: String,
      schema: SchemaProperty?,
      components: ArazzoComponents?
  ) {
    val resolved = resolveInputSchema(schema, components)
    val props = resolved?.properties.orEmpty()
    val requiredSet = resolved?.required.orEmpty().toSet()
    val docLines = mutableListOf(summary)
    if (props.isEmpty()) {
      docLines.add("@property raw Optional raw input map when no properties are declared.")
    } else {
      for ((propName, propSchema) in props) {
        val desc = propSchema.description ?: "Input parameter '$propName'."
        docLines.add("@property ${escapeKotlinKeyword(toCamelIdentifier(propName))} $desc")
      }
    }
    kdoc(*docLines.toTypedArray())
    line("@Serializable")
    if (props.isEmpty()) {
      line("data class $className(val raw: Map<String, String> = emptyMap())")
    } else {
      line("data class $className(")
      val entries = props.entries.toList()
      for ((idx, entry) in entries.withIndex()) {
        val propName = entry.key
        val propSchema = entry.value
        val isReq = propName in requiredSet
        val (ktType, defaultLit) = mapSimpleInputTypeAndDefault(propSchema)
        val safeIdent = escapeKotlinKeyword(toCamelIdentifier(propName))
        val comma = if (idx < entries.size - 1) "," else ""
        if (isReq) {
          line("    val $safeIdent: $ktType = $defaultLit$comma")
        } else {
          line("    val $safeIdent: $ktType? = null$comma")
        }
      }
      line(")")
    }
  }

  private fun psi.KotlinSourceBuilder.emitOutputDataClass(
      className: String,
      summary: String,
      workflow: Workflow
  ) {
    val outputs = workflow.outputs
    val docLines = mutableListOf(summary)
    if (outputs.isEmpty()) {
      docLines.add("@property stepResults Recorded step output maps keyed by stepId.")
    } else {
      for ((outName, outVal) in outputs) {
        val exprDesc =
            when (outVal) {
              is OutputValue.Expression -> outVal.expression
              is OutputValue.Selector -> "${outVal.selector.context} -> ${outVal.selector.selector}"
            }
        docLines.add(
            "@property ${escapeKotlinKeyword(toCamelIdentifier(outName))} Extracted from `$exprDesc`.")
      }
    }
    kdoc(*docLines.toTypedArray())
    line("@Serializable")
    if (outputs.isEmpty()) {
      line("data class $className(val stepResults: Map<String, Map<String, String>> = emptyMap())")
    } else {
      line("data class $className(")
      val entries = outputs.entries.toList()
      for ((idx, entry) in entries.withIndex()) {
        val safeIdent = escapeKotlinKeyword(toCamelIdentifier(entry.key))
        val comma = if (idx < entries.size - 1) "," else ""
        line("    val $safeIdent: String? = null$comma")
      }
      line(")")
    }
  }

  /**
   * Generates `WorkflowOrchestrator.kt` containing the multi-step Arazzo 1.1.0 execution engine and
   * one suspend method per [Workflow] returning `Result<<Workflow>Outputs>`.
   *
   * @param workflowsPkg Target package name (`<base>.workflows`).
   * @param definition The [ArazzoDefinition] to compile.
   * @return A [Result] containing the Kotlin source string for `WorkflowOrchestrator.kt`.
   */
  fun generateOrchestratorSourceResult(
      workflowsPkg: String,
      definition: ArazzoDefinition
  ): Result<String> {
    return Result.success(generateOrchestratorSource(workflowsPkg, definition))
  }

  /**
   * Generates `WorkflowOrchestrator.kt` containing the multi-step Arazzo 1.1.0 execution engine and
   * one suspend method per [Workflow] returning `Result<<Workflow>Outputs>`.
   *
   * @param workflowsPkg Target package name (`<base>.workflows`).
   * @param definition The [ArazzoDefinition] to compile.
   * @return The Kotlin source string for `WorkflowOrchestrator.kt`.
   */
  fun generateOrchestratorSource(workflowsPkg: String, definition: ArazzoDefinition): String {
    val specJson = ArazzoMetadataCodec.encodeDefinitionTag(definition)

    val src =
        buildKotlinFile(workflowsPkg) {
          val classDoc = mutableListOf<String>()
          classDoc.add(definition.info.title)
          definition.info.summary?.let { classDoc.add(it) }
          definition.info.description?.let { classDoc.add(it) }
          classDoc.add("@arazzo-spec $specJson")
          classDoc.add("@property sourceUrls Resolved base URLs keyed by SourceDescription name.")
          classDoc.add(
              "@property stepExecutor Callback executing an operation or channel step and returning a Result of step output values.")
          kdoc(*classDoc.toTypedArray())

          val defaultUrlsLiteral =
              if (definition.sourceDescriptions.isEmpty()) {
                "emptyMap()"
              } else {
                "mapOf(" +
                    definition.sourceDescriptions.joinToString(", ") {
                      "${quote(it.name)} to ${quote(it.url)}"
                    } +
                    ")"
              }

          block(
              "class WorkflowOrchestrator(val sourceUrls: Map<String, String> = $defaultUrlsLiteral, val stepExecutor: suspend (stepId: String, target: String, parameters: Map<String, String>, payload: String?) -> Result<Map<String, String>> = { _, _, _, _ -> Result.success(emptyMap()) })") {
                kdoc("Arazzo specification version string.")
                line("val arazzoVersion: String = ${quote(definition.arazzo)}")
                line()
                kdoc("Optional canonical document URI (`${DL}self`).")
                val selfLit = if (definition.self != null) quote(definition.self) else "null"
                line("val selfUri: String? = $selfLit")
                line()

                for (wf in definition.workflows) {
                  emitWorkflowMethod(
                      wf, ArazzoMetadataCodec.encodeWorkflowTag(wf), definition.components)
                  line()
                }
              }
        }
    return "/** Generated Arazzo 1.1.0 Client SDK WorkflowOrchestrator. */$NL$src"
  }

  private fun psi.KotlinSourceBuilder.emitWorkflowMethod(
      workflow: Workflow,
      wfTagJson: String,
      components: ArazzoComponents?
  ) {
    val pascal = toPascalIdentifier(workflow.workflowId)
    val methodName = escapeKotlinKeyword(toCamelIdentifier(workflow.workflowId))
    val docLines = mutableListOf<String>()
    docLines.add(workflow.summary ?: "Executes workflow '${workflow.workflowId}'.")
    workflow.description?.let { docLines.add(it) }
    docLines.add("@arazzo-workflow $wfTagJson")
    docLines.add("@param inputs Strongly-typed [${pascal}Inputs] for this workflow.")
    docLines.add("@return A [Result] containing [${pascal}Outputs] on success.")
    kdoc(*docLines.toTypedArray())

    block(
        "suspend fun $methodName(inputs: ${pascal}Inputs = ${pascal}Inputs()): Result<${pascal}Outputs>") {
          for (depId in workflow.dependsOn) {
            val depMethod = escapeKotlinKeyword(toCamelIdentifier(depId))
            val depPascal = toPascalIdentifier(depId)
            line("$depMethod(${depPascal}Inputs()).getOrElse { return Result.failure(it) }")
          }
          line("val stepOutputs = LinkedHashMap<String, Map<String, String>>()")
          line("val completedSteps = LinkedHashSet<String>()")
          val firstStepId = workflow.steps.first().stepId
          line("var currentStepId: String? = ${quote(firstStepId)}")
          line("var transitions = 0")
          block("while (currentStepId != null && transitions < 256)") {
            line("transitions++")
            block("when (currentStepId)") {
              for ((idx, step) in workflow.steps.withIndex()) {
                val nextStepId = workflow.steps.getOrNull(idx + 1)?.stepId
                emitStepBranch(step, nextStepId, components)
              }
              line("else -> currentStepId = null")
            }
          }
          if (workflow.outputs.isEmpty()) {
            line("return Result.success(${pascal}Outputs(stepResults = stepOutputs))")
          } else {
            val outArgs =
                workflow.outputs.entries.joinToString(", ") { (outKey, outVal) ->
                  val safeProp = escapeKotlinKeyword(toCamelIdentifier(outKey))
                  val lookupExpr = buildOutputLookupExpr(outVal)
                  "$safeProp = $lookupExpr"
                }
            line("return Result.success(${pascal}Outputs($outArgs))")
          }
        }
  }

  private fun psi.KotlinSourceBuilder.emitStepBranch(
      step: StepObject,
      nextStepId: String?,
      components: ArazzoComponents?
  ) {
    val target =
        step.operationId ?: step.operationPath ?: step.channelPath ?: step.workflowId ?: step.stepId
    val compParams = components?.parameters.orEmpty()
    val paramsEntries =
        step.parameters.map { item ->
          when (item) {
            is ReusableOr.Inline -> item.value.name to item.value.value.toString()
            is ReusableOr.Reference -> {
              val key = item.reusable.reference.removePrefix("${DL}components.parameters.")
              val resolved = compParams[key]
              if (resolved != null) {
                resolved.name to resolved.value.toString()
              } else {
                key to item.reusable.reference
              }
            }
          }
        }
    val paramsMapLit =
        if (paramsEntries.isEmpty()) {
          "emptyMap()"
        } else {
          "mapOf(" +
              paramsEntries.joinToString(", ") { (k, v) -> "${quote(k)} to ${quote(v)}" } +
              ")"
        }
    val payloadLit =
        if (step.requestBody?.payload != null) {
          quote(step.requestBody.payload.toJsonElement().toString())
        } else {
          "null"
        }
    val nextLit = if (nextStepId != null) quote(nextStepId) else "null"
    val inlineFailures =
        step.onFailure.filterIsInstance<ReusableOr.Inline<domain.FailureActionObject>>().map {
          it.value
        }
    val inlineSuccesses =
        step.onSuccess.filterIsInstance<ReusableOr.Inline<domain.SuccessActionObject>>().map {
          it.value
        }
    val retryAction = inlineFailures.firstOrNull { it.type == FailureActionType.RETRY }
    val gotoOnFailure = inlineFailures.firstOrNull { it.type == FailureActionType.GOTO }
    val gotoOnSuccess = inlineSuccesses.firstOrNull { it.type == SuccessActionType.GOTO }
    val endOnSuccess = inlineSuccesses.any { it.type == SuccessActionType.END }

    val maxRetries = if (retryAction != null) 2 else 0

    block("${quote(step.stepId)} ->") {
      for (depStep in step.dependsOn) {
        line(
            "if (!completedSteps.contains(${quote(depStep)})) return Result.failure(IllegalStateException(${quote("Missing prerequisite step $depStep")}))")
      }
      line("var attempt = 0")
      line("var stepResult: Result<Map<String, String>>")
      block("do") {
        line(
            "stepResult = stepExecutor(${quote(step.stepId)}, ${quote(target)}, $paramsMapLit, $payloadLit)")
        line("attempt++")
      }
      line("while (stepResult.isFailure && attempt <= $maxRetries)")
      val failStepId = gotoOnFailure?.stepId
      val failHandler =
          if (failStepId != null) {
            "currentStepId = ${quote(failStepId)}; continue"
          } else {
            "return Result.failure(it)"
          }
      line("val outMap = stepResult.getOrElse { $failHandler }")
      line("stepOutputs[${quote(step.stepId)}] = outMap")
      line("completedSteps.add(${quote(step.stepId)})")
      val succStepId = gotoOnSuccess?.stepId
      when {
        endOnSuccess -> line("currentStepId = null")
        succStepId != null -> line("currentStepId = ${quote(succStepId)}")
        else -> line("currentStepId = $nextLit")
      }
    }
  }

  private fun buildOutputLookupExpr(outVal: OutputValue): String {
    return when (outVal) {
      is OutputValue.Expression -> {
        val expr = outVal.expression
        if (expr.startsWith("${DL}steps.")) {
          val parts = expr.removePrefix("${DL}steps.").split(".")
          val stepId = parts[0]
          val outName = parts.last().substringBefore('#')
          return "stepOutputs[${quote(stepId)}]?.get(${quote(outName)}) ?: ${quote(expr)}"
        }
        quote(expr)
      }
      is OutputValue.Selector -> quote(outVal.selector.selector)
    }
  }

  internal fun resolveInputSchema(
      inputs: SchemaProperty?,
      components: ArazzoComponents?
  ): SchemaProperty? {
    val registry = arazzo.ArazzoDocumentRegistry()
    return registry.resolveWorkflowInputs(inputs, components)
  }

  private fun mapSimpleInputTypeAndDefault(prop: SchemaProperty): Pair<String, String> {
    val mapped = TypeMappers.mapType(prop)
    return when {
      mapped == "Int" -> "Int" to "0"
      mapped == "Long" -> "Long" to "0L"
      mapped == "Double" -> "Double" to "0.0"
      mapped == "Float" -> "Float" to "0.0f"
      mapped == "Boolean" -> "Boolean" to "false"
      mapped.startsWith("List<") -> "List<String>" to "emptyList()"
      mapped.startsWith("Map<") -> "Map<String, String>" to "emptyMap()"
      else -> "String" to quote("")
    }
  }

  /**
   * Converts an arbitrary Arazzo identifier (`login-user_workflow`) into PascalCase
   * (`LoginUserWorkflow`).
   *
   * @param raw Raw workflow, step, or component identifier.
   * @return PascalCase Kotlin identifier.
   */
  fun toPascalIdentifier(raw: String): String {
    val parts = raw.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "WorkflowItem"
    val joined = parts.joinToString("") { capitalizeName(it) }
    return if (joined.first().isDigit()) "Wf$joined" else joined
  }

  /**
   * Converts an arbitrary Arazzo identifier (`login-user_workflow`) into camelCase
   * (`loginUserWorkflow`).
   *
   * @param raw Raw workflow, step, or property identifier.
   * @return camelCase Kotlin identifier.
   */
  fun toCamelIdentifier(raw: String): String {
    val pascal = toPascalIdentifier(raw)
    return decapitalizeName(pascal)
  }

  private val escapedChars = setOf(BS, Q, DL)

  /**
   * Wraps [raw] in double quotes with Kotlin string escaping applied.
   *
   * @param raw Unescaped string value.
   * @return Quoted Kotlin string literal.
   */
  fun quote(raw: String): String {
    val sb = StringBuilder()
    sb.append(Q)
    for (ch in raw) {
      if (ch in escapedChars) {
        sb.append(BS)
      }
      sb.append(ch)
    }
    sb.append(Q)
    return sb.toString()
  }
}
