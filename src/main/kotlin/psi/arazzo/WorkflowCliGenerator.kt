/**
 * Generates Kotlin Client SDK CLI workflow runner code (`WorkflowCli.kt`) from Arazzo 1.1.0
 * [domain.ArazzoDefinition]s, directly reusing the generated Client SDK `WorkflowOrchestrator` and
 * `<Workflow>Inputs` / `<Workflow>Outputs` models.
 */
package psi.arazzo

import domain.ArazzoComponents
import domain.ArazzoDefinition
import domain.SchemaProperty
import domain.Workflow
import org.jetbrains.kotlin.psi.KtFile
import psi.PsiInfrastructure
import psi.arazzo.WorkflowSdkGenerator.quote
import psi.buildKotlinFile
import psi.escapeKotlinKeyword

/**
 * Generates a Clikt-compatible CLI workflow runner (`WorkflowCli.kt`) that delegates directly to
 * `WorkflowOrchestrator` from the Client SDK.
 */
object WorkflowCliGenerator {
  private val NL = Char(10)
  private const val DL = '$'

  /**
   * Generates `WorkflowCli.kt` source code wrapped in a [Result].
   *
   * @param packageName Base package name (e.g. `"com.example"`).
   * @param definition The [ArazzoDefinition] to compile.
   * @return A [Result] containing the generated Kotlin source string for `WorkflowCli.kt`.
   */
  fun generateWorkflowCliSourceResult(
      packageName: String,
      definition: ArazzoDefinition
  ): Result<String> {
    val specJson = ArazzoMetadataCodec.encodeDefinitionTag(definition)
    val cliPkg = "$packageName.cli"
    val workflowsPkg = "$packageName.workflows"

    val src =
        buildKotlinFile(cliPkg) {
          import("$workflowsPkg.*")
          import("kotlinx.coroutines.runBlocking")

          val classDoc = mutableListOf<String>()
          classDoc.add(
              "CLI Workflow Runner for ${definition.info.title} (${definition.info.version}).")
          definition.info.summary?.let { classDoc.add(it) }
          classDoc.add(
              "Directly reuses the Client SDK [WorkflowOrchestrator] for all workflow executions.")
          classDoc.add("@arazzo-spec $specJson")
          classDoc.add(
              "@property orchestrator Underlying Client SDK [WorkflowOrchestrator] instance.")
          kdoc(*classDoc.toTypedArray())

          block(
              "class WorkflowCli(val orchestrator: WorkflowOrchestrator = WorkflowOrchestrator())") {
                kdoc(
                    "Dispatches CLI arguments to the target workflow subcommand or utility command.",
                    "@param args Command-line arguments (`<workflowId> [--flag value]`).",
                    "@return A [Result] containing formatted CLI output or an error.")
                block("fun executeResult(args: Array<String>): Result<String>") {
                  line(
                      "if (args.isEmpty() || args[0] == ${quote("--help")} || args[0] == ${quote("-h")}) return Result.success(renderHelp())")
                  line(
                      "if (args[0] == ${quote("--version")}) return Result.success(${quote(definition.info.version)})")
                  line("if (args[0] == ${quote("list")}) return Result.success(listWorkflows())")
                  line("val command = args[0]")
                  line("val flags = parseFlags(args.drop(1))")
                  block("return runBlocking") {
                    block("when (command)") {
                      for (wf in definition.workflows) {
                        val camel = WorkflowSdkGenerator.toCamelIdentifier(wf.workflowId)
                        line("${quote(wf.workflowId)} -> run_$camel(flags)")
                      }
                      line(
                          "else -> Result.failure(IllegalArgumentException(${quote("Unknown workflow command")}))")
                    }
                  }
                }
                line()

                for (wf in definition.workflows) {
                  emitCliWorkflowRunnerMethod(
                      wf, ArazzoMetadataCodec.encodeWorkflowTag(wf), definition.components)
                  line()
                }

                kdoc(
                    "Lists all registered Arazzo workflows and their summaries.",
                    "@return Formatted workflow table string.")
                block("fun listWorkflows(): String") {
                  val summaryText =
                      definition.workflows.joinToString("; ") {
                        "${it.workflowId}: ${it.summary ?: "Workflow"}"
                      }
                  line("return ${quote(summaryText)}")
                }
                line()

                kdoc(
                    "Renders CLI usage and available workflow subcommands.",
                    "@return Help text string.")
                block("fun renderHelp(): String") {
                  val wfNames = definition.workflows.joinToString(", ") { it.workflowId }
                  line(
                      "return ${quote("Usage: workflow <subcommand> [options] | Workflows: $wfNames")}")
                }
                line()

                kdoc(
                    "Parses `--key value` CLI option pairs into a map.",
                    "@param rawArgs Remaining CLI tokens after the subcommand.",
                    "@return Map of option names to string values.")
                block("private fun parseFlags(rawArgs: List<String>): Map<String, String>") {
                  line("val out = LinkedHashMap<String, String>()")
                  line("var i = 0")
                  block("while (i < rawArgs.size)") {
                    line("val token = rawArgs[i]")
                    block("if (token.startsWith(${quote("--")}))") {
                      line("val key = token.removePrefix(${quote("--")})")
                      line(
                          "val value = if (i + 1 < rawArgs.size && !rawArgs[i + 1].startsWith(${quote("--")})) rawArgs[++i] else ${quote("true")}")
                      line("out[key] = value")
                    }
                    line("i++")
                  }
                  line("return out")
                }
              }
        }
    return Result.success("/** Generated Arazzo 1.1.0 Client SDK CLI Runner. */$NL$src")
  }

  /**
   * Generates a parsed PSI [KtFile] for `WorkflowCli.kt` wrapped in a [Result].
   *
   * @param packageName Base package name.
   * @param definition The [ArazzoDefinition] to compile.
   * @return A [Result] containing the parsed [KtFile].
   */
  fun generateWorkflowCliKtFileResult(
      packageName: String,
      definition: ArazzoDefinition
  ): Result<KtFile> {
    val src = generateWorkflowCliSourceResult(packageName, definition).getOrThrow()
    return Result.success(PsiInfrastructure.createPsiFactory().createFile("WorkflowCli.kt", src))
  }

  private fun psi.KotlinSourceBuilder.emitCliWorkflowRunnerMethod(
      workflow: Workflow,
      wfTagJson: String,
      components: ArazzoComponents?
  ) {
    val camel = WorkflowSdkGenerator.toCamelIdentifier(workflow.workflowId)
    val pascal = WorkflowSdkGenerator.toPascalIdentifier(workflow.workflowId)
    val sdkMethod = escapeKotlinKeyword(camel)
    val resolvedInputs = resolveInputSchema(workflow.inputs, components)
    val props = resolvedInputs?.properties.orEmpty()

    kdoc(
        "Executes CLI subcommand for workflow '${workflow.workflowId}' via [WorkflowOrchestrator.$sdkMethod].",
        "@arazzo-workflow $wfTagJson",
        "@param flags Parsed CLI option flags.",
        "@return A [Result] containing formatted [${pascal}Outputs].")
    block("suspend fun run_$camel(flags: Map<String, String>): Result<String>") {
      if (props.isEmpty()) {
        line("val inputs = ${pascal}Inputs(raw = flags)")
      } else {
        val argsList =
            props.entries.joinToString(", ") { (propName, propSchema) ->
              val safeProp = escapeKotlinKeyword(WorkflowSdkGenerator.toCamelIdentifier(propName))
              val conv = buildFlagConversionExpr(propName, propSchema)
              "$safeProp = $conv"
            }
        line("val inputs = ${pascal}Inputs($argsList)")
      }
      line("return orchestrator.$sdkMethod(inputs).map { it.toString() }")
    }
  }

  private fun buildFlagConversionExpr(propName: String, schema: SchemaProperty): String {
    val qKey = quote(propName)
    val primary = schema.types.firstOrNull { it != "null" } ?: "string"
    return when (primary) {
      "integer" ->
          if (schema.format == "int64") "flags[$qKey]?.toLongOrNull() ?: 0L"
          else "flags[$qKey]?.toIntOrNull() ?: 0"
      "number" ->
          if (schema.format == "float") "flags[$qKey]?.toFloatOrNull() ?: 0.0f"
          else "flags[$qKey]?.toDoubleOrNull() ?: 0.0"
      "boolean" -> "flags[$qKey]?.toBooleanStrictOrNull() ?: false"
      else -> "flags[$qKey] ?: ${quote("")}"
    }
  }

  private fun resolveInputSchema(
      inputs: SchemaProperty?,
      components: ArazzoComponents?
  ): SchemaProperty? = WorkflowSdkGenerator.resolveInputSchema(inputs, components)
}
