/**
 * Parses Kotlin Client SDK CLI workflow runner files (`WorkflowCli.kt`) via Kotlin PSI into
 * [domain.ArazzoDefinition] specifications with full [kotlin.Result] percolation.
 */
package psi.arazzo

import domain.ArazzoDefinition
import domain.ArazzoInfo
import domain.CddError
import domain.SourceDescription
import domain.SourceDescriptionType
import domain.StepObject
import domain.Workflow
import domain.toFailure
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import psi.PsiInfrastructure

/** Reverse-engineers an [ArazzoDefinition] from a `WorkflowCli.kt` source file or PSI [KtFile]. */
object WorkflowCliParser {
  /**
   * Parses `WorkflowCli.kt` source code into an [ArazzoDefinition] wrapped in a [Result].
   *
   * @param sourceCode Kotlin source code of `WorkflowCli.kt`.
   * @return A [Result] containing the extracted [ArazzoDefinition] or a [CddError].
   */
  fun parseWorkflowCliResult(sourceCode: String): Result<ArazzoDefinition> {
    if (sourceCode.isBlank()) {
      return CddError.ArazzoParseError("WorkflowCli source code is blank").toFailure()
    }
    val ktFile = PsiInfrastructure.createPsiFactory().createFile("WorkflowCli.kt", sourceCode)
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
    for (cls in classes) {
      val specTag = ArazzoMetadataCodec.extractTagPayload(cls.docComment?.text, "@arazzo-spec")
      if (specTag != null) {
        return ArazzoMetadataCodec.decodeDefinitionTagResult(specTag)
      }
    }

    val functions = ktFile.collectDescendantsOfType<KtNamedFunction>()
    val workflows =
        functions.mapNotNull { fn ->
          val wfTag = ArazzoMetadataCodec.extractTagPayload(fn.docComment?.text, "@arazzo-workflow")
          if (wfTag != null) {
            ArazzoMetadataCodec.decodeWorkflowTagResult(wfTag).getOrNull()
          } else {
            val name = fn.name.toString()
            if (!name.startsWith("run_")) return@mapNotNull null
            val wfId = name.removePrefix("run_")
            Workflow(
                workflowId = wfId,
                summary = "CLI Workflow $wfId",
                steps = listOf(StepObject(stepId = "${wfId}_step_1", operationId = wfId)))
          }
        }
    if (workflows.isEmpty()) {
      return CddError.ArazzoParseError("No CLI workflow commands found in source").toFailure()
    }
    return Result.success(
        ArazzoDefinition(
            arazzo = "1.1.0",
            info = ArazzoInfo(title = "CLI Workflows", version = "1.0.0"),
            sourceDescriptions =
                listOf(
                    SourceDescription(
                        name = "default",
                        url = "./openapi.json",
                        type = SourceDescriptionType.OPENAPI)),
            workflows = workflows))
  }
}
