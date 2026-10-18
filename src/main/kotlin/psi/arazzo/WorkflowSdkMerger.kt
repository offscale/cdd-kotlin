/**
 * Non-destructive PSI merger for reconciling existing Kotlin `WorkflowOrchestrator` source code
 * with updated Arazzo 1.1.0 [domain.ArazzoDefinition] specifications.
 */
package psi.arazzo

import domain.ArazzoDefinition
import domain.CddError
import domain.toFailure
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import psi.PsiInfrastructure

/**
 * Merges updated [ArazzoDefinition] workflows and metadata into an existing Kotlin
 * `WorkflowOrchestrator.kt` source file while preserving user-defined helper methods.
 */
object WorkflowSdkMerger {
  /**
   * Merges [spec] into [existingSource], returning the updated Kotlin source code wrapped in a
   * [Result].
   *
   * @param existingSource Current Kotlin source code of `WorkflowOrchestrator.kt`.
   * @param spec Updated [ArazzoDefinition] specification.
   * @param packageName Target package name (defaults to `"com.example"`).
   * @return A [Result] containing the merged Kotlin source string or a [CddError].
   */
  fun mergeWorkflowOrchestratorResult(
      existingSource: String,
      spec: ArazzoDefinition,
      packageName: String = "com.example"
  ): Result<String> {
    if (existingSource.isBlank()) {
      return CddError.PsiMergeError("WorkflowOrchestrator", "Existing source code cannot be blank")
          .toFailure()
    }
    val factory = PsiInfrastructure.createPsiFactory()
    val existingFile = factory.createFile("ExistingWorkflowOrchestrator.kt", existingSource)
    val existingClass =
        existingFile.collectDescendantsOfType<KtClass>().firstOrNull {
          it.name == "WorkflowOrchestrator"
        }
            ?: return CddError.PsiMergeError(
                    "WorkflowOrchestrator", "Class 'WorkflowOrchestrator' not found in source")
                .toFailure()

    val specWorkflowNames =
        spec.workflows.map { WorkflowSdkGenerator.toCamelIdentifier(it.workflowId) }.toSet()
    val customHelperFunctions =
        existingClass
            .collectDescendantsOfType<org.jetbrains.kotlin.psi.KtNamedFunction>()
            .filter { fn ->
              val name = fn.name.toString()
              val hasArazzoTag =
                  ArazzoMetadataCodec.extractTagPayload(fn.docComment?.text, "@arazzo-workflow") !=
                      null
              !hasArazzoTag && name !in specWorkflowNames
            }
            .map { it.text }

    val freshSource =
        WorkflowSdkGenerator.generateOrchestratorSource("$packageName.workflows", spec)
    if (customHelperFunctions.isEmpty()) {
      return Result.success(freshSource)
    }

    val lastBraceIdx = freshSource.lastIndexOf('}')
    val sb = StringBuilder(freshSource.substring(0, lastBraceIdx))
    for (customFn in customHelperFunctions) {
      sb.appendLine()
      sb.appendLine("    $customFn")
    }
    sb.appendLine("}")
    return Result.success(sb.toString())
  }
}
