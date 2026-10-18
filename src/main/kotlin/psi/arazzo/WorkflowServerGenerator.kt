/**
 * Generates Ktor server workflow orchestration routes (`WorkflowRoutes.kt`), stateful multi-step
 * mock workflow engines (`StatefulWorkflowMockServer.kt`), and JUnit / `kotlin.test` E2E test
 * suites (`WorkflowE2ETest.kt`) from Arazzo 1.1.0 [domain.ArazzoDefinition]s.
 */
package psi.arazzo

import domain.ArazzoDefinition
import domain.FailureActionType
import domain.ReusableOr
import psi.arazzo.WorkflowSdkGenerator.quote
import psi.buildKotlinFile
import psi.escapeKotlinKeyword

/**
 * Generates Backend Server, Stateful Mock Server, and E2E Integration Test suites from an
 * [ArazzoDefinition] with full [Result] percolation.
 */
object WorkflowServerGenerator {
  private val NL = Char(10)

  /**
   * Generates all backend workflow server and E2E test Kotlin source files wrapped in a [Result].
   *
   * @param packageName Base package name (e.g. `"com.example"`).
   * @param definition The [ArazzoDefinition] to compile.
   * @return A [Result] containing a map of relative file names to Kotlin source code strings.
   */
  fun generateWorkflowServerSourcesResult(
      packageName: String,
      definition: ArazzoDefinition
  ): Result<Map<String, String>> {
    val files = LinkedHashMap<String, String>()
    val sdkFiles =
        WorkflowSdkGenerator.generateWorkflowSdkSourcesResult(packageName, definition).getOrThrow()
    files.putAll(sdkFiles)
    files["WorkflowRoutes.kt"] =
        generateWorkflowRoutesSourceResult(packageName, definition).getOrThrow()
    files["StatefulWorkflowMockServer.kt"] =
        generateStatefulMockServerSourceResult(packageName, definition).getOrThrow()
    files["WorkflowE2ETest.kt"] =
        generateWorkflowE2ETestSourceResult(packageName, definition).getOrThrow()
    return Result.success(files)
  }

  /**
   * Generates `WorkflowRoutes.kt` containing server-side workflow execution endpoints.
   *
   * @param packageName Base package name.
   * @param definition The [ArazzoDefinition] to compile.
   * @return A [Result] containing the Kotlin source code for `WorkflowRoutes.kt`.
   */
  fun generateWorkflowRoutesSourceResult(
      packageName: String,
      definition: ArazzoDefinition
  ): Result<String> {
    val specJson = ArazzoMetadataCodec.encodeDefinitionTag(definition)
    val serverPkg = "$packageName.server"
    val workflowsPkg = "$packageName.workflows"
    val src =
        buildKotlinFile(serverPkg) {
          import("$workflowsPkg.*")

          kdoc(
              "Server-side workflow route dispatcher for ${definition.info.title} (${definition.info.version}).",
              "@arazzo-spec $specJson",
              "@property orchestrator Backing [WorkflowOrchestrator] executing workflow steps.")
          block(
              "class WorkflowRoutes(val orchestrator: WorkflowOrchestrator = WorkflowOrchestrator())") {
                kdoc(
                    "Dispatches a server-side workflow execution request by `workflowId`.",
                    "@param workflowId Identifier of the workflow to execute.",
                    "@param params Request parameters map.",
                    "@return A [Result] containing the serialized workflow execution output.")
                block(
                    "suspend fun handleExecuteWorkflow(workflowId: String, params: Map<String, String> = emptyMap()): Result<String>") {
                      block("return when (workflowId)") {
                        for (wf in definition.workflows) {
                          val camel =
                              escapeKotlinKeyword(
                                  WorkflowSdkGenerator.toCamelIdentifier(wf.workflowId))
                          val pascal = WorkflowSdkGenerator.toPascalIdentifier(wf.workflowId)
                          line(
                              "${quote(wf.workflowId)} -> orchestrator.$camel(${pascal}Inputs()).map { it.toString() }")
                        }
                        line(
                            "else -> Result.failure(IllegalArgumentException(${quote("Unknown workflowId")}))")
                      }
                    }
              }
        }
    return Result.success("/** Generated Arazzo 1.1.0 Server WorkflowRoutes. */$NL$src")
  }

  /**
   * Generates `StatefulWorkflowMockServer.kt` containing a stateful multi-step mock engine that
   * tracks step invocation counts, simulates `retryLimit` / `retryAfter` transient failures, and
   * returns synthetic step outputs.
   *
   * @param packageName Base package name.
   * @param definition The [ArazzoDefinition] to compile.
   * @return A [Result] containing the Kotlin source code for `StatefulWorkflowMockServer.kt`.
   */
  fun generateStatefulMockServerSourceResult(
      packageName: String,
      definition: ArazzoDefinition
  ): Result<String> {
    val specJson = ArazzoMetadataCodec.encodeDefinitionTag(definition)
    val serverPkg = "$packageName.server"
    val src =
        buildKotlinFile(serverPkg) {
          kdoc(
              "Stateful Arazzo 1.1.0 mock workflow engine for ${definition.info.title}.",
              "Tracks step attempt counts, simulates FailureAction retries, and produces synthetic step outputs.",
              "@arazzo-spec $specJson")
          block("class StatefulWorkflowMockServer") {
            kdoc("Recorded invocation counts per stepId.")
            line("val stepAttempts: MutableMap<String, Int> = LinkedHashMap()")
            line()
            kdoc(
                "Executes a mocked workflow step, simulating configured retries and returning synthetic outputs.",
                "@param stepId Identifier of the step being executed.",
                "@param target Operation ID, operation path, channel path, or sub-workflow ID.",
                "@param parameters Step parameters map.",
                "@param payload Optional serialized request body.",
                "@return A [Result] containing mocked step output key-value pairs.")
            block(
                "fun executeMockStep(stepId: String, target: String, parameters: Map<String, String>, payload: String?): Result<Map<String, String>>") {
                  line("val currentAttempt = (stepAttempts[stepId] ?: 0) + 1")
                  line("stepAttempts[stepId] = currentAttempt")
                  block("return when (stepId)") {
                    for (wf in definition.workflows) {
                      for (step in wf.steps) {
                        val retryAction =
                            step.onFailure
                                .filterIsInstance<ReusableOr.Inline<domain.FailureActionObject>>()
                                .map { it.value }
                                .firstOrNull { it.type == FailureActionType.RETRY }
                        val outEntries =
                            step.outputs.keys.joinToString(", ") { k ->
                              "${quote(k)} to ${quote("mock_${step.stepId}_$k")}"
                            }
                        val outMapExpr =
                            if (outEntries.isEmpty()) {
                              "mapOf(${quote("status")} to ${quote("200")}, ${quote("target")} to target)"
                            } else {
                              "mapOf($outEntries)"
                            }
                        if (retryAction != null) {
                          line(
                              "${quote(step.stepId)} -> if (currentAttempt == 1) Result.failure(IllegalStateException(${quote("Simulated transient failure before retry")})) else Result.success($outMapExpr)")
                        } else {
                          line("${quote(step.stepId)} -> Result.success($outMapExpr)")
                        }
                      }
                    }
                    line("else -> Result.success(mapOf(${quote("target")} to target))")
                  }
                }
          }
        }
    return Result.success("/** Generated Arazzo 1.1.0 StatefulWorkflowMockServer. */$NL$src")
  }

  /**
   * Generates `WorkflowE2ETest.kt` containing automated end-to-end integration tests for every
   * [domain.Workflow] wired against `StatefulWorkflowMockServer` and `WorkflowOrchestrator`.
   *
   * @param packageName Base package name.
   * @param definition The [ArazzoDefinition] to compile.
   * @return A [Result] containing the Kotlin source code for `WorkflowE2ETest.kt`.
   */
  fun generateWorkflowE2ETestSourceResult(
      packageName: String,
      definition: ArazzoDefinition
  ): Result<String> {
    val specJson = ArazzoMetadataCodec.encodeDefinitionTag(definition)
    val workflowsPkg = "$packageName.workflows"
    val serverPkg = "$packageName.server"

    val src =
        buildKotlinFile(serverPkg) {
          import("$workflowsPkg.*")
          import("kotlin.test.Test")
          import("kotlin.test.assertTrue")
          import("kotlinx.coroutines.runBlocking")

          kdoc(
              "End-to-end integration test suite for ${definition.info.title} (${definition.info.version}).",
              "@arazzo-spec $specJson")
          block("class WorkflowE2ETest") {
            for (wf in definition.workflows) {
              val camel = WorkflowSdkGenerator.toCamelIdentifier(wf.workflowId)
              val sdkMethod = escapeKotlinKeyword(camel)
              val pascal = WorkflowSdkGenerator.toPascalIdentifier(wf.workflowId)
              val wfTag = ArazzoMetadataCodec.encodeWorkflowTag(wf)
              kdoc(
                  "Verifies end-to-end execution of workflow '${wf.workflowId}'.",
                  "@arazzo-workflow $wfTag")
              line("@Test")
              block("fun test_$camel() = runBlocking") {
                line("val mockServer = StatefulWorkflowMockServer()")
                line(
                    "val orchestrator = WorkflowOrchestrator(stepExecutor = { stepId, target, params, payload -> mockServer.executeMockStep(stepId, target, params, payload) })")
                line("val result = orchestrator.$sdkMethod(${pascal}Inputs())")
                line("assertTrue(result.isSuccess)")
              }
              line()
            }
          }
        }
    return Result.success("/** Generated Arazzo 1.1.0 E2E Workflow Integration Tests. */$NL$src")
  }
}
