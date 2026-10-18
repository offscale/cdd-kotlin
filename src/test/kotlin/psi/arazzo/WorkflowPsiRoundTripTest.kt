/**
 * Exhaustive unit tests for [psi.arazzo.ArazzoMetadataCodec], [psi.arazzo.WorkflowSdkGenerator],
 * [psi.arazzo.WorkflowSdkParser], [psi.arazzo.WorkflowSdkMerger],
 * [psi.arazzo.WorkflowCliGenerator], [psi.arazzo.WorkflowCliParser],
 * [psi.arazzo.WorkflowServerGenerator], [psi.arazzo.WorkflowServerParser], and
 * [psi.arazzo.ArazzoApiGenerator].
 */
package psi.arazzo

import domain.ArazzoComponents
import domain.ArazzoDefinition
import domain.ArazzoInfo
import domain.ArazzoParameter
import domain.ArazzoParameterLocation
import domain.ArazzoRequestBody
import domain.FailureActionObject
import domain.FailureActionType
import domain.OpenApiObject
import domain.OpenApiString
import domain.OutputValue
import domain.ReusableObject
import domain.ReusableOr
import domain.SchemaProperty
import domain.SelectorObject
import domain.SourceDescription
import domain.SourceDescriptionType
import domain.StepObject
import domain.SuccessActionObject
import domain.SuccessActionType
import domain.Workflow
import generateArazzoSdkCode
import generateArazzoServerCode
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit tests for all Arazzo PSI generators, parsers, mergers, and file round-trips. */
class WorkflowPsiRoundTripTest {

  private fun buildSampleArazzo(): ArazzoDefinition {
    return ArazzoDefinition(
        arazzo = "1.1.0",
        self = "https://example.com/arazzo.json",
        info =
            ArazzoInfo(
                title = "Order Orchestration",
                summary = "Order processing workflows",
                description = "Detailed order workflow description",
                version = "2.1.0"),
        sourceDescriptions =
            listOf(
                SourceDescription(
                    name = "orders",
                    url = "https://api.example.com/orders.json",
                    type = SourceDescriptionType.OPENAPI)),
        workflows =
            listOf(
                Workflow(
                    workflowId = "auth-workflow",
                    summary = "Authenticate user",
                    inputs = SchemaProperty(ref = "\$components.inputs.AuthInput"),
                    steps =
                        listOf(
                            StepObject(
                                stepId = "loginStep",
                                operationId = "loginUser",
                                parameters =
                                    listOf(
                                        ReusableOr.Inline(
                                            ArazzoParameter(
                                                name = "tenant",
                                                location = ArazzoParameterLocation.HEADER,
                                                value = OpenApiString("\$inputs.tenant"))),
                                        ReusableOr.Reference(
                                            ReusableObject(
                                                reference =
                                                    "\$components.parameters.traceHeader"))),
                                requestBody =
                                    ArazzoRequestBody(
                                        contentType = "application/json",
                                        payload =
                                            OpenApiObject(mapOf("user" to OpenApiString("alice")))),
                                onSuccess =
                                    listOf(
                                        ReusableOr.Inline(
                                            SuccessActionObject(
                                                name = "finishNow", type = SuccessActionType.END))),
                                onFailure =
                                    listOf(
                                        ReusableOr.Inline(
                                            FailureActionObject(
                                                name = "retryLogin",
                                                type = FailureActionType.RETRY,
                                                retryLimit = 2,
                                                retryAfter = 1.0))),
                                outputs =
                                    mapOf(
                                        "token" to OutputValue.Expression("\$response.body#/token"),
                                        "selRole" to
                                            OutputValue.Selector(
                                                SelectorObject(
                                                    context = "\$response.body",
                                                    selector = "\$.role"))))),
                    outputs =
                        mapOf(
                            "jwt" to OutputValue.Expression("\$steps.loginStep.outputs.token"),
                            "role" to
                                OutputValue.Selector(
                                    SelectorObject(
                                        context = "\$response.body", selector = "\$.role")))),
                Workflow(
                    workflowId = "create-order",
                    summary = "Create order after auth",
                    dependsOn = listOf("auth-workflow"),
                    inputs =
                        SchemaProperty(
                            types = setOf("object"),
                            properties =
                                mapOf(
                                    "quantity" to
                                        SchemaProperty(types = setOf("integer"), format = "int32"),
                                    "expedited" to SchemaProperty(types = setOf("boolean")),
                                    "price" to
                                        SchemaProperty(types = setOf("number"), format = "double"),
                                    "weight" to
                                        SchemaProperty(types = setOf("number"), format = "float"),
                                    "orderNum" to
                                        SchemaProperty(types = setOf("integer"), format = "int64"),
                                    "tags" to
                                        SchemaProperty(
                                            types = setOf("array"),
                                            items = SchemaProperty(types = setOf("string"))),
                                    "meta" to
                                        SchemaProperty(
                                            types = setOf("object"),
                                            additionalProperties =
                                                SchemaProperty(types = setOf("string")))),
                            required =
                                listOf(
                                    "quantity",
                                    "expedited",
                                    "price",
                                    "weight",
                                    "orderNum",
                                    "tags",
                                    "meta")),
                    steps =
                        listOf(
                            StepObject(
                                stepId = "submitOrder",
                                operationPath =
                                    "{\$sourceDescriptions.orders.url}#/paths/~1orders/post",
                                onSuccess =
                                    listOf(
                                        ReusableOr.Inline(
                                            SuccessActionObject(
                                                name = "gotoConfirm",
                                                type = SuccessActionType.GOTO,
                                                stepId = "confirmOrder"))),
                                onFailure =
                                    listOf(
                                        ReusableOr.Inline(
                                            FailureActionObject(
                                                name = "gotoFallback",
                                                type = FailureActionType.GOTO,
                                                stepId = "confirmOrder")))),
                            StepObject(
                                stepId = "confirmOrder",
                                channelPath = "/orders/confirmed",
                                dependsOn = listOf("submitOrder"))))),
        components =
            ArazzoComponents(
                inputs =
                    mapOf(
                        "AuthInput" to
                            SchemaProperty(
                                types = setOf("object"),
                                properties =
                                    mapOf("tenant" to SchemaProperty(types = setOf("string"))))),
                parameters =
                    mapOf(
                        "traceHeader" to
                            ArazzoParameter(
                                name = "X-Trace",
                                location = ArazzoParameterLocation.HEADER,
                                value = OpenApiString("1")))))
  }

  @Test
  fun `WorkflowSdkGenerator, WorkflowSdkParser, and WorkflowSdkMerger round-trip and merge cleanly`() {
    val spec = buildSampleArazzo()
    val sdkFiles =
        WorkflowSdkGenerator.generateWorkflowSdkSourcesResult("com.example", spec).getOrThrow()
    assertTrue(sdkFiles.containsKey("WorkflowModels.kt"))
    assertTrue(sdkFiles.containsKey("WorkflowOrchestrator.kt"))

    val ktFiles =
        WorkflowSdkGenerator.generateWorkflowSdkKtFilesResult("com.example", spec).getOrThrow()
    assertEquals(2, ktFiles.size)

    val roundTripped = WorkflowSdkParser.parseWorkflowSdkResult(sdkFiles).getOrThrow()
    assertEquals(spec.arazzo, roundTripped.arazzo)
    assertEquals(spec.self, roundTripped.self)
    assertEquals(spec.info.title, roundTripped.info.title)
    assertEquals(spec.workflows.size, roundTripped.workflows.size)
    assertEquals(spec.workflows[0].steps[0].outputs, roundTripped.workflows[0].steps[0].outputs)

    // Test pure PSI AST fallback without @arazzo-spec or @arazzo-workflow KDoc tags
    val handwrittenKotlin =
        """
        /** Handwritten orchestrator class. */
        package com.example.workflows

        /** Input DTO. */
        data class CheckoutInputs(val cartId: String = "", val promo: String? = null)

        /** Custom WorkflowOrchestrator. */
        class WorkflowOrchestrator(
            val sourceUrls: Map<String, String> = mapOf("shop" to "https://shop.example.com/openapi.json")
        ) {
            val arazzoVersion: String = "1.1.0"
            val selfUri: String? = "https://shop.example.com/arazzo.json"

            /** Runs checkout workflow. */
            suspend fun checkout(inputs: CheckoutInputs): Result<String> {
                val stepOutputs = LinkedHashMap<String, Map<String, String>>()
                val stepResult = stepExecutor("chargeCard", "chargeCardOp", mapOf("cartId" to "123"), null)
                val orderId = stepOutputs["chargeCard"]?.get("orderId")
                return Result.success(orderId ?: "")
            }
        }
        """
            .trimIndent()
    val parsedFromHandwritten =
        WorkflowSdkParser.parseWorkflowFileResult(handwrittenKotlin).getOrThrow()
    assertEquals("1.1.0", parsedFromHandwritten.arazzo)
    assertEquals("https://shop.example.com/arazzo.json", parsedFromHandwritten.self)
    assertEquals("shop", parsedFromHandwritten.sourceDescriptions.first().name)
    assertEquals("checkout", parsedFromHandwritten.workflows.first().workflowId)
    assertEquals("chargeCard", parsedFromHandwritten.workflows.first().steps.first().stepId)
    assertEquals(
        "\$steps.chargeCard.outputs.orderId",
        (parsedFromHandwritten.workflows.first().outputs["orderId"] as OutputValue.Expression)
            .expression)

    // Test WorkflowSdkMerger preserving custom helper functions
    val orchestratorWithCustomHelper =
        sdkFiles["WorkflowOrchestrator.kt"]!!.replaceLast(
            "}",
            """
                /** Custom helper method. */
                fun customHelper(): String = "preserved"
            }
            """
                .trimIndent())
    val merged =
        WorkflowSdkMerger.mergeWorkflowOrchestratorResult(
                orchestratorWithCustomHelper, spec, "com.example")
            .getOrThrow()
    assertTrue(merged.contains("""fun customHelper(): String = "preserved""""))

    assertTrue(WorkflowSdkParser.parseWorkflowSdkResult(emptyMap()).isFailure)
    assertTrue(WorkflowSdkParser.parseWorkflowFileResult("").isFailure)
    assertTrue(
        WorkflowSdkParser.parseWorkflowFileResult("""package p; class EmptyClass""").isFailure)
    assertTrue(WorkflowSdkMerger.mergeWorkflowOrchestratorResult("", spec).isFailure)
    assertTrue(
        WorkflowSdkMerger.mergeWorkflowOrchestratorResult("""package p; class Other""", spec)
            .isFailure)
    assertNull(ArazzoMetadataCodec.extractTagPayload(null, "@arazzo-spec"))
    assertTrue(ArazzoMetadataCodec.decodeWorkflowTagResult(""""not-obj"""").isFailure)
  }

  @Test
  fun `WorkflowCliGenerator and WorkflowCliParser round-trip and reuse SDK WorkflowOrchestrator`() {
    val spec = buildSampleArazzo()
    val cliSrc =
        WorkflowCliGenerator.generateWorkflowCliSourceResult("com.example", spec).getOrThrow()
    assertTrue(cliSrc.contains("import com.example.workflows.*"))
    assertTrue(cliSrc.contains("val orchestrator: WorkflowOrchestrator = WorkflowOrchestrator()"))
    assertTrue(cliSrc.contains("orchestrator.authWorkflow("))
    assertTrue(cliSrc.contains("orchestrator.createOrder("))

    val cliKtFile =
        WorkflowCliGenerator.generateWorkflowCliKtFileResult("com.example", spec).getOrThrow()
    val parsedFromCli = WorkflowCliParser.parseKtFileResult(cliKtFile).getOrThrow()
    assertEquals(spec.info.title, parsedFromCli.info.title)
    assertEquals(spec.workflows.size, parsedFromCli.workflows.size)

    // Test fallback PSI parsing of CLI without @arazzo-spec tag
    val simpleCli =
        """
        package com.example.cli
        class WorkflowCli {
            suspend fun run_syncUsers(flags: Map<String, String>): Result<String> = Result.success("ok")
        }
        """
            .trimIndent()
    val parsedSimpleCli = WorkflowCliParser.parseWorkflowCliResult(simpleCli).getOrThrow()
    assertEquals("syncUsers", parsedSimpleCli.workflows.first().workflowId)

    assertTrue(WorkflowCliParser.parseWorkflowCliResult("").isFailure)
    assertTrue(WorkflowCliParser.parseWorkflowCliResult("""package p; class Empty""").isFailure)
  }

  @Test
  fun `WorkflowServerGenerator, WorkflowServerParser, and ArazzoApiGenerator round-trip cleanly`() {
    val spec = buildSampleArazzo()
    val serverFiles =
        WorkflowServerGenerator.generateWorkflowServerSourcesResult("com.example", spec)
            .getOrThrow()
    assertTrue(serverFiles.containsKey("WorkflowRoutes.kt"))
    assertTrue(serverFiles.containsKey("StatefulWorkflowMockServer.kt"))
    assertTrue(serverFiles.containsKey("WorkflowE2ETest.kt"))

    val parsedFromRoutes =
        WorkflowServerParser.parseWorkflowServerFileResult(serverFiles["WorkflowRoutes.kt"]!!)
            .getOrThrow()
    assertEquals(spec.info.title, parsedFromRoutes.info.title)
    assertEquals(spec.workflows.size, parsedFromRoutes.workflows.size)

    val parsedFromServerMap =
        WorkflowServerParser.parseWorkflowServerResult(serverFiles).getOrThrow()
    assertEquals(spec.workflows.size, parsedFromServerMap.workflows.size)

    // Fallback E2E test parsing without @arazzo-spec tag
    val simpleTest =
        """
        package com.example.server
        class WorkflowE2ETest {
            fun test_onboardCustomer() {}
        }
        """
            .trimIndent()
    val parsedSimpleTest =
        WorkflowServerParser.parseWorkflowServerFileResult(simpleTest).getOrThrow()
    assertEquals("onboardCustomer", parsedSimpleTest.workflows.first().workflowId)

    assertTrue(WorkflowServerParser.parseWorkflowServerResult(emptyMap()).isFailure)
    assertTrue(WorkflowServerParser.parseWorkflowServerFileResult("").isFailure)
    assertTrue(
        WorkflowServerParser.parseWorkflowServerFileResult("""package p; class Empty""").isFailure)

    // Test ArazzoApiGenerator with temp directory and files
    val tmpDir = Files.createTempDirectory("cdd-arazzo-test").toFile()
    try {
      val orchestratorFile = java.io.File(tmpDir, "WorkflowOrchestrator.kt")
      orchestratorFile.writeText(serverFiles["WorkflowOrchestrator.kt"]!!)
      val outJsonFile = java.io.File(tmpDir, "out.arazzo.json")
      assertTrue(
          ArazzoApiGenerator.generateToArazzoResult(tmpDir.absolutePath, outJsonFile.absolutePath)
              .isSuccess)
      assertTrue(outJsonFile.exists())
      assertTrue(outJsonFile.readText().contains("Order Orchestration"))

      // Test single CLI file input
      val cliDir = java.io.File(tmpDir, "cliOnly").apply { mkdirs() }
      val cliFile = java.io.File(cliDir, "WorkflowCli.kt")
      cliFile.writeText(
          WorkflowCliGenerator.generateWorkflowCliSourceResult("com.example", spec).getOrThrow())
      val outYamlFile = java.io.File(tmpDir, "out.arazzo.yaml")
      assertTrue(
          ArazzoApiGenerator.generateToArazzoResult(cliFile.absolutePath, outYamlFile.absolutePath)
              .isSuccess)
      assertTrue(outYamlFile.exists())

      // Test single server file input
      val srvDir = java.io.File(tmpDir, "srvOnly").apply { mkdirs() }
      val srvFile = java.io.File(srvDir, "WorkflowRoutes.kt")
      srvFile.writeText(serverFiles["WorkflowRoutes.kt"]!!)
      val outSrvJson = java.io.File(tmpDir, "outSrv.json")
      assertTrue(
          ArazzoApiGenerator.generateToArazzoResult(srvDir.absolutePath, outSrvJson.absolutePath)
              .isSuccess)

      assertTrue(
          ArazzoApiGenerator.generateToArazzoResult(
                  "/nonexistent/arazzo/dir", outJsonFile.absolutePath)
              .isFailure)
      val nonKtFile = java.io.File(tmpDir, "notes.txt").apply { writeText("hi") }
      assertTrue(
          ArazzoApiGenerator.generateToArazzoResult(
                  nonKtFile.absolutePath, outJsonFile.absolutePath)
              .isFailure)
      val emptyDir = java.io.File(tmpDir, "emptyDir").apply { mkdirs() }
      assertTrue(
          ArazzoApiGenerator.generateToArazzoResult(emptyDir.absolutePath, outJsonFile.absolutePath)
              .isFailure)
    } finally {
      tmpDir.deleteRecursively()
    }
  }

  @Test
  fun `WorkflowSdkGenerator, WorkflowCliGenerator, WorkflowServerGenerator, andParsers cover all remaining edge branches`() {
    val nl = Char(10)
    val cr = Char(13)
    val bs = Char(92)
    val edgeSpec =
        ArazzoDefinition(
            arazzo = "1.1.0",
            self = null,
            info = ArazzoInfo(title = "Edge\"Title$bs$nl$cr\$1", version = "1.0.0"),
            sourceDescriptions = emptyList(),
            workflows =
                listOf(
                    Workflow(
                        workflowId = "123-flow",
                        summary = null,
                        description = "Flow desc",
                        inputs = SchemaProperty(ref = "#/components/inputs/RefInput"),
                        outputs =
                            mapOf("directOut" to OutputValue.Expression("\$inputs.customObj")),
                        steps =
                            listOf(
                                StepObject(
                                    stepId = "stepA",
                                    operationId = null,
                                    operationPath = null,
                                    channelPath = null,
                                    workflowId = "subWf",
                                    parameters =
                                        listOf(
                                            ReusableOr.Reference(
                                                ReusableObject(
                                                    reference =
                                                        "\$components.parameters.unresolvedParam")))),
                                StepObject(stepId = "stepFallbackId")))),
            components =
                ArazzoComponents(
                    inputs =
                        mapOf(
                            "RefInput" to
                                SchemaProperty(
                                    types = setOf("object"),
                                    properties =
                                        mapOf(
                                            "customObj" to
                                                SchemaProperty(
                                                    description = "Custom obj desc",
                                                    ref = "#/components/schemas/CustomObj"))),
                            "EmptyCompIn" to SchemaProperty(types = setOf("object")))))

    val sdkSrcs =
        WorkflowSdkGenerator.generateWorkflowSdkSourcesResult("com.example", edgeSpec).getOrThrow()
    assertTrue(sdkSrcs["WorkflowOrchestrator.kt"]!!.contains("Wf123Flow"))
    assertEquals("WorkflowItem", WorkflowSdkGenerator.toPascalIdentifier("---"))
    assertTrue(
        WorkflowSdkGenerator.generateOrchestratorSourceResult("com.example.workflows", edgeSpec)
            .isSuccess)
    assertTrue(ArazzoMetadataCodec.encodeDefinitionTagResult(edgeSpec).isSuccess)
    assertTrue(ArazzoMetadataCodec.encodeWorkflowTagResult(edgeSpec.workflows.first()).isSuccess)
    assertTrue(ArazzoMetadataCodec.decodeWorkflowTagResult("{bad").isFailure)

    val wfTagJson = ArazzoMetadataCodec.encodeWorkflowTag(edgeSpec.workflows.first())
    val fnWithWfTagCode =
        """
        package com.example
        class CustomHolder {
            val arazzoVersion: String
            val selfUri: String? = null
            /**
             * @tag ignored
             */
            fun untaggedHelper() {}
            /**
             * @arazzo-workflow $wfTagJson
             */
            fun taggedMethod() {}
            /**
             * @arazzo-workflow {bad json
             */
            fun badTaggedMethod() {}
        }
        data class NotEndingWithIn(val x: String)
        data class RawInputs(val raw: Map<String, String> = emptyMap())
        """
            .trimIndent()
    assertTrue(WorkflowSdkParser.parseWorkflowFileResult(fnWithWfTagCode).isSuccess)
    assertTrue(WorkflowCliParser.parseWorkflowCliResult(fnWithWfTagCode).isSuccess)
    assertTrue(WorkflowServerParser.parseWorkflowServerFileResult(fnWithWfTagCode).isSuccess)

    // Test channel stepExecutor in WorkflowSdkParser
    val channelAndPathStepCode =
        """
        package com.example
        /**
         * @only-tags
         */
        class NoSummaryOrchestrator {
            suspend fun flowWithChannelAndPath(): Result<String> {
                stepExecutor("sPath", "./oas.json#/paths/~1pets/get", emptyMap(), null)
                stepExecutor("sChan", "/events/stream", emptyMap(), null)
                return Result.success("ok")
            }
        }
        """
            .trimIndent()
    val parsedChanPath =
        WorkflowSdkParser.parseWorkflowFileResult(channelAndPathStepCode).getOrThrow()
    assertEquals("./oas.json#/paths/~1pets/get", parsedChanPath.workflows[0].steps[0].operationPath)
    assertEquals("/events/stream", parsedChanPath.workflows[0].steps[1].channelPath)

    // Test WorkflowSdkMerger with untagged function that matches spec workflow name
    val orchestratorWithMatchingUntagged =
        """
        package com.example.workflows
        class WorkflowOrchestrator {
            fun wf123Flow(): String = "sameName"
            fun customKeep(): String = "keep"
        }
        """
            .trimIndent()
    val mergedMatching =
        WorkflowSdkMerger.mergeWorkflowOrchestratorResult(
                orchestratorWithMatchingUntagged, edgeSpec, "com.example")
            .getOrThrow()
    assertTrue(mergedMatching.contains("customKeep"))

    val cliSrc =
        WorkflowCliGenerator.generateWorkflowCliSourceResult("com.example", edgeSpec).getOrThrow()
    assertTrue(cliSrc.contains("Wf123FlowInputs"))

    // Test WorkflowSdkMerger when no custom helpers exist
    val mergedUnchanged =
        WorkflowSdkMerger.mergeWorkflowOrchestratorResult(
                sdkSrcs["WorkflowOrchestrator.kt"]!!, edgeSpec, "com.example")
            .getOrThrow()
    assertTrue(mergedUnchanged.contains("class WorkflowOrchestrator"))

    // Test WorkflowSdkParser fallback branches (client calls, top-level function without class)
    val clientCallKotlin =
        """
        package com.example.workflows
        suspend fun topLevelFlow(): Result<String> {
            petClient.listPets()
            return Result.success("ok")
        }
        suspend fun noStepFlow(): Result<String> {
            return Result.success("ok")
        }
        """
            .trimIndent()
    val parsedTopLevel = WorkflowSdkParser.parseWorkflowFileResult(clientCallKotlin).getOrThrow()
    assertEquals(2, parsedTopLevel.workflows.size)
    assertEquals("listPets", parsedTopLevel.workflows[0].steps[0].operationId)
    assertEquals("noStepFlowOperation", parsedTopLevel.workflows[1].steps[0].operationId)

    // Test invalid @arazzo-spec tag in WorkflowSdkParser, WorkflowCliParser, WorkflowServerParser
    val badSpecTagCode =
        """
        package com.example
        /**
         * @arazzo-spec {invalid json
         */
        class BadSpecClass
        """
            .trimIndent()
    assertTrue(WorkflowSdkParser.parseWorkflowFileResult(badSpecTagCode).isFailure)
    assertTrue(WorkflowCliParser.parseWorkflowCliResult(badSpecTagCode).isFailure)
    assertTrue(WorkflowServerParser.parseWorkflowServerFileResult(badSpecTagCode).isFailure)

    // Test PlatformJvm and CddCli / CddGenerator Arazzo error and env branches
    val tmpDir = Files.createTempDirectory("cdd-arazzo-plat").toFile()
    try {
      assertTrue(generateArazzoSdkCode("{bad", tmpDir.absolutePath, "org.example", false).isFailure)
      assertTrue(
          generateArazzoServerCode("{bad", tmpDir.absolutePath, "org.example", false).isFailure)
      val specJson = arazzo.ArazzoWriter().writeJson(edgeSpec)
      assertTrue(
          generateArazzoServerCode(specJson, tmpDir.absolutePath, "org.example", false).isSuccess)

      val blockedSdkDir = java.io.File(tmpDir, "blocked_sdk")
      java.io
          .File(
              blockedSdkDir,
              "composeApp/src/commonMain/kotlin/org/example/workflows/WorkflowModels.kt")
          .mkdirs()
      assertTrue(
          generateArazzoSdkCode(specJson, blockedSdkDir.absolutePath, "org.example", true)
              .isFailure)

      val blockedCliDir = java.io.File(tmpDir, "blocked_cli")
      java.io
          .File(blockedCliDir, "composeApp/src/commonMain/kotlin/org/example/cli/WorkflowCli.kt")
          .mkdirs()
      assertTrue(
          generateArazzoSdkCode(specJson, blockedCliDir.absolutePath, "org.example", true)
              .isFailure)

      val blockedSrvDir = java.io.File(tmpDir, "blocked_srv")
      java.io
          .File(blockedSrvDir, "server/src/main/kotlin/org/example/workflows/WorkflowModels.kt")
          .mkdirs()
      assertTrue(
          generateArazzoServerCode(specJson, blockedSrvDir.absolutePath, "org.example", true)
              .isFailure)

      val specFile = java.io.File(tmpDir, "edge.arazzo.json").apply { writeText(specJson) }
      assertTrue(
          org.cdd.CddGenerator.generateArazzoSdk(
                  org.cdd.Config(inputPath = "", outputDir = tmpDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateArazzoSdk(
                  org.cdd.Config(inputPath = "invalid.json", outputDir = tmpDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateArazzoSdk(
                  org.cdd.Config(inputPath = "missing.json", outputDir = tmpDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateArazzoSdk(
                  org.cdd.Config(
                      inputPath = "/nonexistent/arazzo.json", outputDir = tmpDir.absolutePath))
              .isFailure)
      val badFile = java.io.File(tmpDir, "bad_arazzo.json").apply { writeText("{bad") }
      assertTrue(
          org.cdd.CddGenerator.generateArazzoSdk(
                  org.cdd.Config(inputPath = badFile.absolutePath, outputDir = tmpDir.absolutePath))
              .isFailure)

      assertTrue(
          org.cdd.CddGenerator.generateArazzoServer(
                  org.cdd.Config(inputPath = "", outputDir = tmpDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateArazzoServer(
                  org.cdd.Config(inputPath = "invalid.json", outputDir = tmpDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateArazzoServer(
                  org.cdd.Config(inputPath = "missing.json", outputDir = tmpDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateArazzoServer(
                  org.cdd.Config(
                      inputPath = "/nonexistent/arazzo.json", outputDir = tmpDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateArazzoServer(
                  org.cdd.Config(inputPath = badFile.absolutePath, outputDir = tmpDir.absolutePath))
              .isFailure)

      // CddGenerator scaffold / github actions / code write failures
      val blockedScaffoldOut = java.io.File(tmpDir, "blocked_scaffold")
      java.io.File(blockedScaffoldOut, "build.gradle.kts").mkdirs()
      assertTrue(
          org.cdd.CddGenerator.generateArazzoSdk(
                  org.cdd.Config(
                      inputPath = specFile.absolutePath,
                      outputDir = blockedScaffoldOut.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateArazzoServer(
                  org.cdd.Config(
                      inputPath = specFile.absolutePath,
                      outputDir = blockedScaffoldOut.absolutePath))
              .isFailure)

      val blockedGhOut = java.io.File(tmpDir, "blocked_gh")
      java.io.File(blockedGhOut, ".github/workflows/ci.yml").mkdirs()
      assertTrue(
          org.cdd.CddGenerator.generateArazzoSdk(
                  org.cdd.Config(
                      inputPath = specFile.absolutePath,
                      outputDir = blockedGhOut.absolutePath,
                      noInstallablePackage = true))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateArazzoServer(
                  org.cdd.Config(
                      inputPath = specFile.absolutePath,
                      outputDir = blockedGhOut.absolutePath,
                      noInstallablePackage = true))
              .isFailure)

      assertTrue(
          org.cdd.CddGenerator.generateArazzoSdk(
                  org.cdd.Config(
                      inputPath = specFile.absolutePath,
                      outputDir = blockedSdkDir.absolutePath,
                      noInstallablePackage = true,
                      noGithubActions = true))
              .isFailure)
      assertEquals(1, org.cdd.CddCli.generateFromArazzo(arrayOf("from_arazzo")))

      // CddCli env vars for generateFromArazzo and generateToArazzo
      System.setProperty("CDD_INPUT", specFile.absolutePath)
      System.setProperty("CDD_OUTPUT", java.io.File(tmpDir, "env_arazzo_out").absolutePath)
      System.setProperty("CDD_NO_GITHUB_ACTIONS", "1")
      System.setProperty("CDD_NO_INSTALLABLE_PACKAGE", "1")
      System.setProperty("CDD_TESTS", "1")
      try {
        val envOutDir = java.io.File(tmpDir, "env_arazzo_out")
        assertEquals(
            0,
            org.cdd.CddCli.generateFromArazzo(
                arrayOf(
                    "from_arazzo",
                    "--output",
                    envOutDir.absolutePath,
                    "--input",
                    specFile.absolutePath),
                "to_sdk"))
        assertEquals(0, org.cdd.CddCli.generateFromArazzo(arrayOf("from_arazzo", "-o"), "to_sdk"))
        assertEquals(0, org.cdd.CddCli.generateFromArazzo(arrayOf("from_arazzo", "-i"), "to_sdk"))
        System.setProperty("CDD_INPUT", envOutDir.absolutePath)
        val outArazzoSpec = java.io.File(tmpDir, "env_roundtrip.json")
        System.setProperty("CDD_OUTPUT", outArazzoSpec.absolutePath)
        assertEquals(
            0,
            org.cdd.CddCli.generateToArazzo(
                arrayOf(
                    "to_arazzo",
                    "--output",
                    outArazzoSpec.absolutePath,
                    "--input",
                    envOutDir.absolutePath)))
        assertEquals(0, org.cdd.CddCli.generateToArazzo(arrayOf("to_arazzo", "-o")))
        assertEquals(0, org.cdd.CddCli.generateToArazzo(arrayOf("to_arazzo", "-i")))

        // Also test CDD_NO_GITHUB_ACTIONS=true, CDD_NO_INSTALLABLE_PACKAGE=true, CDD_TESTS=true
        System.setProperty("CDD_NO_GITHUB_ACTIONS", "true")
        System.setProperty("CDD_NO_INSTALLABLE_PACKAGE", "true")
        System.setProperty("CDD_TESTS", "true")
        assertEquals(
            0,
            org.cdd.CddCli.generateFromArazzo(
                arrayOf("from_arazzo", "-i", specFile.absolutePath, "-o", envOutDir.absolutePath),
                "to_server"))

        // Test SyncGenerator with truth = "workflow"
        assertTrue(psi.SyncGenerator.synchronize(envOutDir.absolutePath, "workflow").isSuccess)

        // Test ArazzoApiGenerator with WorkflowE2ETest.kt only
        val e2eOnlyDir = java.io.File(tmpDir, "e2eOnly").apply { mkdirs() }
        val serverMap =
            WorkflowServerGenerator.generateWorkflowServerSourcesResult("com.example", edgeSpec)
                .getOrThrow()
        java.io.File(e2eOnlyDir, "WorkflowE2ETest.kt").writeText(serverMap["WorkflowE2ETest.kt"]!!)
        assertTrue(
            ArazzoApiGenerator.generateToArazzoResult(
                    e2eOnlyDir.absolutePath, java.io.File(tmpDir, "e2eOut.json").absolutePath)
                .isSuccess)
      } finally {
        System.clearProperty("CDD_INPUT")
        System.clearProperty("CDD_OUTPUT")
        System.clearProperty("CDD_NO_GITHUB_ACTIONS")
        System.clearProperty("CDD_NO_INSTALLABLE_PACKAGE")
        System.clearProperty("CDD_TESTS")
      }
    } finally {
      tmpDir.deleteRecursively()
    }
  }

  private fun String.replaceLast(oldValue: String, newValue: String): String {
    val lastIdx = lastIndexOf(oldValue)
    if (lastIdx < 0) return this
    return substring(0, lastIdx) + newValue + substring(lastIdx + oldValue.length)
  }
}
