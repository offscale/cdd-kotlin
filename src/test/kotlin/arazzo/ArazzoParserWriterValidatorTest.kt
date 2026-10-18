/**
 * Exhaustive unit tests for [arazzo.ArazzoParser], [arazzo.ArazzoWriter], [arazzo.ArazzoValidator],
 * and [arazzo.ArazzoDocumentRegistry].
 */
package arazzo

import domain.ArazzoComponents
import domain.ArazzoDefinition
import domain.ArazzoInfo
import domain.ArazzoParameter
import domain.ArazzoParameterLocation
import domain.ArazzoRequestBody
import domain.CriterionEngineType
import domain.CriterionObject
import domain.CriterionTypeOrExpression
import domain.EndpointDefinition
import domain.ExpressionTypeObject
import domain.FailureActionObject
import domain.FailureActionType
import domain.HttpMethod
import domain.Info
import domain.OpenApiDefinition
import domain.OpenApiInt
import domain.OpenApiObject
import domain.OpenApiString
import domain.OutputValue
import domain.PathItem
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
import kotlinx.serialization.json.JsonPrimitive
import openapi.OpenApiDocument
import openapi.OpenApiParser
import openapi.OpenApiWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit tests for Arazzo 1.1.0 parsing, serialization, validation, and document linking. */
class ArazzoParserWriterValidatorTest {

  private fun buildComprehensiveSpec(): ArazzoDefinition {
    val ext = mapOf("x-ext" to OpenApiString("yes"))
    val paramInline =
        ReusableOr.Inline(
            ArazzoParameter(
                name = "petId",
                location = ArazzoParameterLocation.PATH,
                value = OpenApiString("\$inputs.petId"),
                extensions = ext))
    val paramRef =
        ReusableOr.Reference(
            ReusableObject(
                reference = "\$components.parameters.sharedHeader",
                value = OpenApiString("override-val"),
                extensions = ext))
    val critSimple =
        CriterionObject(
            context = "\$statusCode",
            condition = "\$statusCode == 200",
            type = CriterionTypeOrExpression.Engine(CriterionEngineType.SIMPLE),
            extensions = ext)
    val critVersioned =
        CriterionObject(
            context = "\$response.body",
            condition = "\$[?(@.status == 'available')]",
            type =
                CriterionTypeOrExpression.Versioned(
                    ExpressionTypeObject(
                        type = CriterionEngineType.JSONPATH,
                        version = "rfc9535",
                        extensions = ext)))
    val successInline =
        ReusableOr.Inline(
            SuccessActionObject(
                name = "goStep2",
                type = SuccessActionType.GOTO,
                stepId = "step2",
                parameters = listOf(paramInline),
                criteria = listOf(critSimple),
                extensions = ext))
    val successRef =
        ReusableOr.Reference(ReusableObject(reference = "\$components.successActions.endFlow"))
    val failureInline =
        ReusableOr.Inline(
            FailureActionObject(
                name = "retry transient",
                type = FailureActionType.RETRY,
                stepId = "step1",
                parameters = listOf(paramInline),
                retryAfter = 2.5,
                retryLimit = 3,
                criteria = listOf(critSimple),
                extensions = ext))
    val failureIntDelay =
        ReusableOr.Inline(
            FailureActionObject(
                name = "retry int",
                type = FailureActionType.RETRY,
                retryAfter = 2.0,
                retryLimit = 1))
    val failureRef =
        ReusableOr.Reference(ReusableObject(reference = "\$components.failureActions.abortFlow"))

    val step1 =
        StepObject(
            stepId = "step1",
            description = "Fetch pet",
            operationId = "getPetById",
            parameters = listOf(paramInline, paramRef),
            requestBody =
                ArazzoRequestBody(
                    contentType = "application/json",
                    payload =
                        OpenApiObject(
                            mapOf("pet" to OpenApiObject(mapOf("name" to OpenApiString(" Rex "))))),
                    replacements =
                        listOf(
                            PayloadReplacementObject(
                                target = "/pet/name",
                                targetSelectorType =
                                    CriterionTypeOrExpression.Engine(
                                        CriterionEngineType.JSONPOINTER),
                                value = OpenApiString("\$inputs.petName"),
                                extensions = ext)),
                    extensions = ext),
            successCriteria = listOf(critSimple, critVersioned),
            onSuccess = listOf(successInline, successRef),
            onFailure = listOf(failureInline, failureIntDelay, failureRef),
            outputs =
                mapOf(
                    "petName" to OutputValue.Expression("\$response.body#/name"),
                    "firstTag" to
                        OutputValue.Selector(
                            SelectorObject(
                                context = "\$response.body",
                                selector = "\$.tags[0].name",
                                type =
                                    CriterionTypeOrExpression.Engine(CriterionEngineType.JSONPATH),
                                extensions = ext))),
            timeout = 3000L,
            correlationId = "\$message.header.corr",
            action = StepActionType.SEND,
            extensions = ext)

    val step2 =
        StepObject(
            stepId = "step2",
            description = "Publish event",
            channelPath = "/pets/adopted",
            action = StepActionType.RECEIVE,
            dependsOn = listOf("step1"))

    val wf1 =
        Workflow(
            workflowId = "adoptPetWorkflow",
            summary = "Adopt a pet",
            description = "Multi-step adoption workflow",
            inputs =
                SchemaProperty(
                    types = setOf("object"),
                    properties =
                        mapOf(
                            "petId" to SchemaProperty(types = setOf("string")),
                            "petName" to SchemaProperty(types = setOf("string"))),
                    required = listOf("petId")),
            steps = listOf(step1, step2),
            successActions = listOf(successRef),
            failureActions = listOf(failureRef),
            outputs =
                mapOf("adoptedPetName" to OutputValue.Expression("\$steps.step1.outputs.petName")),
            parameters = listOf(paramRef),
            extensions = ext)

    val wf2 =
        Workflow(
            workflowId = "postAdoptWorkflow",
            dependsOn = listOf("adoptPetWorkflow"),
            steps = listOf(StepObject(stepId = "subCall", workflowId = "adoptPetWorkflow")))

    val comps =
        ArazzoComponents(
            inputs =
                mapOf(
                    "SharedInput" to
                        SchemaProperty(
                            types = setOf("object"),
                            properties = mapOf("id" to SchemaProperty(types = setOf("integer")))),
                    "BoolInput" to SchemaProperty(booleanSchema = true)),
            parameters =
                mapOf(
                    "sharedHeader" to
                        ArazzoParameter(
                            name = "X-Trace",
                            location = ArazzoParameterLocation.HEADER,
                            value = OpenApiString("trace-1"))),
            successActions =
                mapOf(
                    "endFlow" to
                        SuccessActionObject(name = "endFlow", type = SuccessActionType.END)),
            failureActions =
                mapOf(
                    "abortFlow" to
                        FailureActionObject(name = "abortFlow", type = FailureActionType.END)),
            extensions = ext)

    return ArazzoDefinition(
        arazzo = "1.1.0",
        self = "https://example.com/workflows/arazzo.json",
        info =
            ArazzoInfo(
                title = "Petstore Arazzo",
                summary = "Petstore workflows",
                description = "Full workflow suite",
                version = "1.1.0",
                extensions = ext),
        sourceDescriptions =
            listOf(
                SourceDescription(
                    name = "petstore",
                    url = "./petstore.json",
                    type = SourceDescriptionType.OPENAPI,
                    extensions = ext),
                SourceDescription(
                    name = "events", url = "./events.yaml", type = SourceDescriptionType.ASYNCAPI),
                SourceDescription(name = "untypedSource", url = "./other.json")),
        workflows = listOf(wf1, wf2),
        components = comps,
        extensions = ext)
  }

  @Test
  fun `ArazzoWriter and ArazzoParser round-trip JSON and YAML losslessly`() {
    val original = buildComprehensiveSpec()
    val writer = ArazzoWriter()
    val parser = ArazzoParser()

    val json = writer.writeJson(original, pretty = true)
    val compactJson = writer.writeJson(original, pretty = false)
    assertTrue(json.contains(""""arazzo""""))
    assertTrue(compactJson.contains(""""arazzo":"1.1.0""""))

    val parsedFromJson = parser.parseStringResult(json, ArazzoParser.Format.JSON).getOrThrow()
    assertEquals(original.arazzo, parsedFromJson.arazzo)
    assertEquals(original.self, parsedFromJson.self)
    assertEquals(original.info, parsedFromJson.info)
    assertEquals(original.sourceDescriptions, parsedFromJson.sourceDescriptions)
    assertEquals(original.workflows.size, parsedFromJson.workflows.size)
    assertEquals(original.workflows[0].steps.size, parsedFromJson.workflows[0].steps.size)
    assertEquals(
        original.workflows[0].steps[0].outputs, parsedFromJson.workflows[0].steps[0].outputs)
    assertEquals(
        original.workflows[0].steps[0].requestBody,
        parsedFromJson.workflows[0].steps[0].requestBody)

    val yaml = writer.writeYaml(original)
    val parsedFromYaml = parser.parseStringResult(yaml, ArazzoParser.Format.YAML).getOrThrow()
    assertEquals(original.info.title, parsedFromYaml.info.title)
    assertEquals(original.workflows.size, parsedFromYaml.workflows.size)

    val autoJson = writer.writeResult(original, ArazzoWriter.Format.AUTO, "spec.json").getOrThrow()
    val autoYaml = writer.writeResult(original, ArazzoWriter.Format.AUTO, "spec.yaml").getOrThrow()
    val autoYml = writer.writeResult(original, ArazzoWriter.Format.AUTO, "spec.yml").getOrThrow()
    assertEquals(original.info.title, parser.parseStringResult(autoJson).getOrThrow().info.title)
    assertEquals(original.info.title, parser.parseStringResult(autoYaml).getOrThrow().info.title)
    assertEquals(original.info.title, parser.parseStringResult(autoYml).getOrThrow().info.title)

    val openApiDoc = OpenApiParser().parseDocumentStringResult(json).getOrThrow()
    assertTrue(openApiDoc is OpenApiDocument.Arazzo)
    val openApiDocSync = OpenApiParser().parseDocumentString(json)
    assertTrue(openApiDocSync is OpenApiDocument.Arazzo)
    val writtenViaOpenApiWriter = OpenApiWriter().writeDocument(openApiDoc)
    assertTrue(writtenViaOpenApiWriter.contains(""""arazzo""""))

    // Additional ArazzoWriter branches
    assertTrue(writer.writeJsonResult(original, pretty = false).isSuccess)
    assertTrue(writer.writeYamlResult(original).isSuccess)
    assertTrue(writer.toOpenApiObjectResult(original).isSuccess)
    assertTrue(writer.writeResult(original, ArazzoWriter.Format.JSON, null).isSuccess)
    assertTrue(writer.writeResult(original, ArazzoWriter.Format.YAML, null).isSuccess)
    assertTrue(writer.writeResult(original, ArazzoWriter.Format.AUTO, null).isSuccess)

    // Minimal spec with empty components, null optional fields, non-null workflowId on
    // SuccessAction, null payload/selectorType, etc.
    val minimalWithVariants =
        ArazzoDefinition(
            info = ArazzoInfo(title = "Min", version = "1.0.0"),
            sourceDescriptions = listOf(SourceDescription(name = "s", url = "./s.json")),
            workflows =
                listOf(
                    Workflow(
                        workflowId = "w1",
                        steps =
                            listOf(
                                StepObject(
                                    stepId = "st1",
                                    operationId = "op1",
                                    requestBody =
                                        ArazzoRequestBody(
                                            contentType = null,
                                            payload = null,
                                            replacements =
                                                listOf(
                                                    PayloadReplacementObject(
                                                        target = "/a",
                                                        targetSelectorType = null,
                                                        value = OpenApiString("v")))),
                                    successCriteria =
                                        listOf(
                                            CriterionObject(
                                                context = null, condition = "true", type = null)),
                                    onSuccess =
                                        listOf(
                                            ReusableOr.Inline(
                                                SuccessActionObject(
                                                    name = "chainWf",
                                                    type = SuccessActionType.GOTO,
                                                    workflowId = "w1",
                                                    stepId = null))),
                                    onFailure =
                                        listOf(
                                            ReusableOr.Inline(
                                                FailureActionObject(
                                                    name = "endFail",
                                                    type = FailureActionType.END,
                                                    workflowId = null,
                                                    stepId = null,
                                                    retryAfter = null,
                                                    retryLimit = null))),
                                    outputs =
                                        mapOf(
                                            "selNoType" to
                                                OutputValue.Selector(
                                                    SelectorObject(
                                                        context = "\$response.body",
                                                        selector = "/id",
                                                        type = null))))))),
            components = ArazzoComponents())
    val minJson = writer.writeJson(minimalWithVariants)
    assertFalse(minJson.contains(""""components""""))
    val parsedMin = parser.parseStringResult(minJson).getOrThrow()
    assertNull(parsedMin.components)
    val nonEmptyCompsButEmptyMaps =
        minimalWithVariants.copy(
            components = ArazzoComponents(extensions = mapOf("x-comp" to OpenApiString("1"))))
    assertTrue(writer.writeJson(nonEmptyCompsButEmptyMaps).contains(""""x-comp""""))

    // Test parameter with null location and FailureAction with non-null workflowId in ArazzoWriter
    val writerBranchSpec =
        minimalWithVariants.copy(
            workflows =
                listOf(
                    Workflow(
                        workflowId = "w1",
                        parameters =
                            listOf(
                                ReusableOr.Inline(
                                    ArazzoParameter(
                                        name = "noLocParam",
                                        location = null,
                                        value = OpenApiString("v")))),
                        steps =
                            listOf(
                                StepObject(
                                    stepId = "st1",
                                    workflowId = "w1",
                                    onFailure =
                                        listOf(
                                            ReusableOr.Inline(
                                                FailureActionObject(
                                                    name = "gotoWfFail",
                                                    type = FailureActionType.GOTO,
                                                    workflowId = "w1"))))))))
    assertTrue(writer.writeJson(writerBranchSpec).contains(""""workflowId": "w1""""))
    // Test invalid string engine in parseCriterionTypeOrExpressionResult
    assertTrue(parser.parseCriterionTypeOrExpressionResult(JsonPrimitive("badEngine")).isFailure)
  }

  @Test
  fun `ArazzoParser handles all error branches cleanly via Result`() {
    val parser = ArazzoParser()
    assertTrue(parser.parseStringResult("").isFailure)
    assertTrue(parser.parseStringResult("{bad json", ArazzoParser.Format.JSON).isFailure)
    assertTrue(parser.parseJsonObjectResult(JsonPrimitive("not-obj")).isFailure)
    assertTrue(parser.parseStringResult("""{"info":{}}""").isFailure)
    assertTrue(parser.parseStringResult("""{"arazzo":"1.1.0"}""").isFailure)
    assertTrue(parser.parseStringResult("""{"arazzo":"1.1.0","info":{}}""").isFailure)
    assertTrue(parser.parseStringResult("""{"arazzo":"1.1.0","info":{"title":"T"}}""").isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":"bad"}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":["bad"]}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{}]}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s"}]}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u","type":"bad"}]}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}]}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":["bad"]}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{}]}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w"}]}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":["bad"]}]}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{}]}]}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","action":"bad"}]}]}""")
            .isFailure)

    assertTrue(parser.parseParameterListResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseParameterOrReusableResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseParameterResult(JsonPrimitive("bad")).isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","parameters":[{}]}]}]}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","parameters":[{"name":"p"}]}]}]}""")
            .isFailure)
    assertTrue(
        parser
            .parseStringResult(
                """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","parameters":[{"name":"p","in":"bad","value":"v"}]}]}]}""")
            .isFailure)

    assertTrue(parser.parseSuccessActionListResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseSuccessActionOrReusableResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseSuccessActionResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseFailureActionListResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseFailureActionOrReusableResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseFailureActionResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseCriteriaListResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseCriterionResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseCriterionTypeOrExpressionResult(JsonPrimitive(123)).isFailure)
    assertTrue(parser.parseOutputsMapResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseSelectorResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseRequestBodyResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parsePayloadReplacementResult(JsonPrimitive("bad")).isFailure)
    assertTrue(parser.parseComponentsResult(JsonPrimitive("bad")).isFailure)

    // Nested error propagation in ArazzoParser
    val badCases =
        listOf(
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1"}],"components":"bad"}],"components":"bad"}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1"}],"successActions":"bad"}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1"}],"failureActions":"bad"}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1"}],"outputs":"bad"}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1"}],"parameters":"bad"}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","requestBody":"bad"}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","requestBody":{"replacements":"bad"}}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","requestBody":{"replacements":[{}]}}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","requestBody":{"replacements":[{"target":"/a"}]}}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","requestBody":{"replacements":[{"target":"/a","value":"v","targetSelectorType":123}]}}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","successCriteria":"bad"}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","successCriteria":[{}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","successCriteria":[{"condition":"c","type":123}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","successCriteria":[{"condition":"c","type":{}}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","successCriteria":[{"condition":"c","type":{"type":"bad"}}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","successCriteria":[{"condition":"c","type":{"type":"jsonpath"}}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","onSuccess":["bad"]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","onSuccess":[{}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","onSuccess":[{"name":"a"}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","onSuccess":[{"name":"a","type":"bad"}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","onSuccess":[{"name":"a","type":"end","parameters":"bad"}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","onSuccess":[{"name":"a","type":"end","criteria":"bad"}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","onFailure":["bad"]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","onFailure":[{}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","onFailure":[{"name":"f"}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","onFailure":[{"name":"f","type":"bad"}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","onFailure":[{"name":"f","type":"end","parameters":"bad"}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","onFailure":[{"name":"f","type":"end","criteria":"bad"}]}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","outputs":{"o":{}}}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","outputs":{"o":{"context":"${'$'}url"}}}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","outputs":{"o":{"context":"${'$'}url","selector":"${'$'}.a","type":123}}}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1","outputs":{"x-ext":"ok","o":123}}]}]}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1"}]}],"components":{"inputs":{"x-ext":true},"parameters":{"x-ext":true,"p":{}}}}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1"}]}],"components":{"successActions":{"x-ext":true,"s":{}}}}""",
            """{"arazzo":"1.1.0","info":{"title":"T","version":"1"},"sourceDescriptions":[{"name":"s","url":"u"}],"workflows":[{"workflowId":"w","steps":[{"stepId":"s1"}]}],"components":{"failureActions":{"x-ext":true,"f":{}}}}""")
    for (badJson in badCases) {
      assertTrue(parser.parseStringResult(badJson).isFailure, "Expected failure for: $badJson")
    }
    // Also test JSON array root for Format.AUTO
    assertTrue(parser.parseStringResult("[1, 2, 3]").isFailure)
    // OpenApiParser fallback when "arazzo" root has invalid structure
    val invalidArazzoDoc = OpenApiParser().parseDocumentString("""{"arazzo":123}""")
    assertTrue(invalidArazzoDoc is OpenApiDocument.Schema)
  }

  @Test
  fun `ArazzoValidator validates valid specs and detects all semantic errors`() {
    val validator = ArazzoValidator()
    val valid = buildComprehensiveSpec()
    val validIssues = validator.validate(valid)
    assertTrue(validIssues.isEmpty(), "Expected 0 issues but got: $validIssues")
    assertTrue(validator.validateResult(valid).isSuccess)

    val invalidSpec =
        ArazzoDefinition(
            arazzo = "2.0.0",
            info = ArazzoInfo(title = "", version = ""),
            sourceDescriptions =
                listOf(
                    SourceDescription(name = "bad name!", url = ""),
                    SourceDescription(name = "dup", url = "http://a"),
                    SourceDescription(name = "dup", url = "http://b")),
            workflows =
                listOf(
                    Workflow(workflowId = "", steps = emptyList()),
                    Workflow(
                        workflowId = "wfA",
                        dependsOn = listOf("wfB", "unknownWf"),
                        parameters =
                            listOf(
                                ReusableOr.Inline(
                                    ArazzoParameter(
                                        name = "",
                                        location = ArazzoParameterLocation.QUERY,
                                        value = OpenApiString("\$badRoot.x"))),
                                ReusableOr.Inline(
                                    ArazzoParameter(
                                        name = "qs",
                                        location = ArazzoParameterLocation.QUERYSTRING,
                                        value = OpenApiString("a=1"))),
                                ReusableOr.Reference(ReusableObject(reference = "badRef")),
                                ReusableOr.Reference(
                                    ReusableObject(reference = "\$components.parameters.missing"))),
                        steps =
                            listOf(
                                StepObject(
                                    stepId = "",
                                    timeout = -5L,
                                    dependsOn = listOf("missingStep"),
                                    parameters =
                                        listOf(
                                            ReusableOr.Inline(
                                                ArazzoParameter(
                                                    name = "noIn", value = OpenApiString("v"))),
                                            ReusableOr.Inline(
                                                ArazzoParameter(
                                                    name = "p1",
                                                    location = ArazzoParameterLocation.PATH,
                                                    value = OpenApiString("v")))),
                                    operationId = "op1",
                                    operationPath = "#/paths/~1p/get",
                                    requestBody =
                                        ArazzoRequestBody(
                                            replacements =
                                                listOf(
                                                    PayloadReplacementObject(
                                                        target = "",
                                                        value = OpenApiString("\$badRoot.y")))),
                                    successCriteria =
                                        listOf(
                                            CriterionObject(
                                                context = "\$badRoot.z", condition = "")),
                                    onSuccess =
                                        listOf(
                                            ReusableOr.Inline(
                                                SuccessActionObject(
                                                    name = "dupAct",
                                                    type = SuccessActionType.END,
                                                    stepId = "s1")),
                                            ReusableOr.Inline(
                                                SuccessActionObject(
                                                    name = "dupAct",
                                                    type = SuccessActionType.GOTO)),
                                            ReusableOr.Inline(
                                                SuccessActionObject(
                                                    name = "badStepGoto",
                                                    type = SuccessActionType.GOTO,
                                                    stepId = "noSuchStep")),
                                            ReusableOr.Inline(
                                                SuccessActionObject(
                                                    name = "badWfGoto",
                                                    type = SuccessActionType.GOTO,
                                                    workflowId = "noSuchWf")),
                                            ReusableOr.Reference(
                                                ReusableObject(
                                                    reference =
                                                        "\$components.successActions.missing"))),
                                    onFailure =
                                        listOf(
                                            ReusableOr.Inline(
                                                FailureActionObject(
                                                    name = "dupFail",
                                                    type = FailureActionType.END,
                                                    stepId = "s1",
                                                    retryAfter = -1.0,
                                                    retryLimit = -2)),
                                            ReusableOr.Inline(
                                                FailureActionObject(
                                                    name = "dupFail",
                                                    type = FailureActionType.GOTO)),
                                            ReusableOr.Inline(
                                                FailureActionObject(
                                                    name = "badFailStep",
                                                    type = FailureActionType.GOTO,
                                                    stepId = "noSuchStep")),
                                            ReusableOr.Inline(
                                                FailureActionObject(
                                                    name = "badFailWf",
                                                    type = FailureActionType.GOTO,
                                                    workflowId = "noSuchWf")),
                                            ReusableOr.Inline(
                                                FailureActionObject(
                                                    name = "badRetryBoth",
                                                    type = FailureActionType.RETRY,
                                                    stepId = "s1",
                                                    workflowId = "wfA")),
                                            ReusableOr.Reference(
                                                ReusableObject(
                                                    reference =
                                                        "\$components.failureActions.missing"))),
                                    outputs =
                                        mapOf(
                                            "badOut" to OutputValue.Expression("\$badRoot.w"),
                                            "badSel" to
                                                OutputValue.Selector(
                                                    SelectorObject(
                                                        context = "\$badRoot.c", selector = "")))),
                                StepObject(
                                    stepId = "cyc1",
                                    operationId = "op1",
                                    dependsOn = listOf("cyc2")),
                                StepObject(
                                    stepId = "cyc2",
                                    operationId = "op2",
                                    dependsOn = listOf("cyc1")),
                                StepObject(stepId = "dupStep", operationId = "op2"),
                                StepObject(stepId = "dupStep", operationId = "op2"))),
                    Workflow(
                        workflowId = "wfDup",
                        steps = listOf(StepObject(stepId = "s1", operationId = "op"))),
                    Workflow(
                        workflowId = "wfDup",
                        steps = listOf(StepObject(stepId = "s1", operationId = "op"))),
                    Workflow(
                        workflowId = "wfB",
                        dependsOn = listOf("wfA"),
                        steps = listOf(StepObject(stepId = "s1", operationId = "op")))),
            components =
                ArazzoComponents(
                    successActions =
                        mapOf(
                            "badCompSucc" to
                                SuccessActionObject(
                                    name = "badCompSucc",
                                    type = SuccessActionType.END,
                                    stepId = "s1")),
                    failureActions =
                        mapOf(
                            "badCompFail" to
                                FailureActionObject(
                                    name = "badCompFail",
                                    type = FailureActionType.END,
                                    stepId = "s1"))))

    val issues = validator.validate(invalidSpec)
    assertFalse(issues.isEmpty())
    assertTrue(validator.validateResult(invalidSpec).isFailure)

    val emptyListsSpec =
        ArazzoDefinition(
            arazzo = "1.1.0",
            info = ArazzoInfo(title = "T", version = "1"),
            sourceDescriptions = emptyList(),
            workflows = emptyList())
    assertTrue(validator.validateResult(emptyListsSpec).isFailure)

    // Cover remaining ArazzoValidator branches:
    // - workflow dependsOn starting with $sourceDescriptions.
    // - non-string replacement/parameter value
    // - parameter query only vs querystring only
    // - successAction END with workflowId != null
    // - successAction GOTO with valid stepId and workflowId starting with $sourceDescriptions.
    // - failureAction END with workflowId != null
    // - failureAction GOTO with valid stepId and workflowId starting with $sourceDescriptions. and
    // valid local workflowId
    // - criterion with non-expression context
    // - reusable refs when components == null and when components has empty maps vs matching keys
    // - 3-node workflow/step cycle with shared visited node
    val extraValidatorSpec =
        ArazzoDefinition(
            arazzo = "1.1.0",
            info = ArazzoInfo(title = "Extra", version = "1.0.0"),
            sourceDescriptions = listOf(SourceDescription(name = "s1", url = "./s.json")),
            workflows =
                listOf(
                    Workflow(
                        workflowId = "wf1",
                        dependsOn = listOf("\$sourceDescriptions.s1.workflows.extWf", "wf2"),
                        parameters =
                            listOf(
                                ReusableOr.Inline(
                                    ArazzoParameter(
                                        name = "numParam",
                                        location = ArazzoParameterLocation.QUERY,
                                        value = OpenApiInt(1L))),
                                ReusableOr.Reference(
                                    ReusableObject(reference = "\$components.parameters.missing"))),
                        steps =
                            listOf(
                                StepObject(
                                    stepId = "sA",
                                    operationId = "opA",
                                    dependsOn = listOf("sB"),
                                    parameters =
                                        listOf(
                                            ReusableOr.Inline(
                                                ArazzoParameter(
                                                    name = "qsOnly",
                                                    location = ArazzoParameterLocation.QUERYSTRING,
                                                    value = OpenApiString("a=1")))),
                                    requestBody =
                                        ArazzoRequestBody(
                                            replacements =
                                                listOf(
                                                    PayloadReplacementObject(
                                                        target = "/x", value = OpenApiInt(5L)),
                                                    PayloadReplacementObject(
                                                        target = "/y",
                                                        value = OpenApiString("literal")))),
                                    successCriteria =
                                        listOf(
                                            CriterionObject(
                                                context = "literalCtx", condition = "true"),
                                            CriterionObject(context = null, condition = "true")),
                                    onSuccess =
                                        listOf(
                                            ReusableOr.Inline(
                                                SuccessActionObject(
                                                    name = "endWithWf",
                                                    type = SuccessActionType.END,
                                                    workflowId = "wf1")),
                                            ReusableOr.Inline(
                                                SuccessActionObject(
                                                    name = "gotoExtWf",
                                                    type = SuccessActionType.GOTO,
                                                    workflowId =
                                                        "\$sourceDescriptions.s1.workflows.ext")),
                                            ReusableOr.Inline(
                                                SuccessActionObject(
                                                    name = "gotoLocalWf",
                                                    type = SuccessActionType.GOTO,
                                                    workflowId = "wf1")),
                                            ReusableOr.Reference(
                                                ReusableObject(
                                                    reference =
                                                        "\$components.successActions.missing"))),
                                    onFailure =
                                        listOf(
                                            ReusableOr.Inline(
                                                FailureActionObject(
                                                    name = "endFailWithWf",
                                                    type = FailureActionType.END,
                                                    workflowId = "wf1")),
                                            ReusableOr.Inline(
                                                FailureActionObject(
                                                    name = "gotoValidStep",
                                                    type = FailureActionType.GOTO,
                                                    stepId = "sB")),
                                            ReusableOr.Inline(
                                                FailureActionObject(
                                                    name = "gotoExtFailWf",
                                                    type = FailureActionType.GOTO,
                                                    workflowId =
                                                        "\$sourceDescriptions.s1.workflows.ext")),
                                            ReusableOr.Inline(
                                                FailureActionObject(
                                                    name = "gotoLocalFailWf",
                                                    type = FailureActionType.GOTO,
                                                    workflowId = "wf1")),
                                            ReusableOr.Reference(
                                                ReusableObject(
                                                    reference =
                                                        "\$components.failureActions.missing")))),
                                StepObject(
                                    stepId = "sB", operationId = "opB", dependsOn = listOf("sC")),
                                StepObject(stepId = "sC", operationId = "opC"))),
                    Workflow(
                        workflowId = "wf2",
                        dependsOn = listOf("wf3"),
                        steps = listOf(StepObject(stepId = "s1", operationId = "op"))),
                    Workflow(
                        workflowId = "wf3",
                        steps = listOf(StepObject(stepId = "s1", operationId = "op")))),
            components = null)
    assertTrue(validator.validateResult(extraValidatorSpec).isFailure)

    // Also test validateComponents when components has Goto stepId on component-level action
    // (stepIds is empty)
    val compStepGotoSpec =
        extraValidatorSpec.copy(
            components =
                ArazzoComponents(
                    successActions =
                        mapOf(
                            "compStepGoto" to
                                SuccessActionObject(
                                    name = "compStepGoto",
                                    type = SuccessActionType.GOTO,
                                    stepId = "anyStep")),
                    failureActions =
                        mapOf(
                            "compFailStepGoto" to
                                FailureActionObject(
                                    name = "compFailStepGoto",
                                    type = FailureActionType.GOTO,
                                    stepId = "anyStep"))))
    assertTrue(validator.validateResult(compStepGotoSpec).isFailure)
  }

  @Test
  fun `ArazzoDocumentRegistry resolves operations, workflows, and reusable components`() {
    val registry = ArazzoDocumentRegistry()
    val petstoreOpenApi =
        OpenApiDefinition(
            info = Info(title = "Petstore", version = "1.0.0"),
            paths =
                mapOf(
                    "/pets/{petId}" to
                        PathItem(
                            get =
                                EndpointDefinition(
                                    path = "/pets/{petId}",
                                    method = HttpMethod.GET,
                                    operationId = "getPetById",
                                    summary = "Get pet by ID"))))
    registry.registerOpenApiSource("petstore", petstoreOpenApi, "./petstore.json")

    val extArazzo =
        ArazzoDefinition(
            info = ArazzoInfo(title = "Ext", version = "1.0.0"),
            sourceDescriptions =
                listOf(SourceDescription(name = "petstore", url = "./petstore.json")),
            workflows =
                listOf(
                    Workflow(
                        workflowId = "extLogin",
                        steps = listOf(StepObject(stepId = "s1", operationId = "getPetById")))))
    registry.registerArazzoSource("extArazzo", extArazzo, "./ext.arazzo.json")

    val arazzoDef =
        buildComprehensiveSpec()
            .copy(
                sourceDescriptions =
                    listOf(
                        SourceDescription(
                            name = "petstore",
                            url = "./petstore.json",
                            type = SourceDescriptionType.OPENAPI),
                        SourceDescription(
                            name = "extArazzo",
                            url = "./ext.arazzo.json",
                            type = SourceDescriptionType.ARAZZO)))

    // Unqualified operationId
    val stepById = StepObject(stepId = "s1", operationId = "getPetById")
    val resolved1 = registry.resolveStepOperationResult(arazzoDef, stepById).getOrThrow()
    assertEquals("petstore", resolved1.source.name)
    assertEquals("getPetById", resolved1.endpoint.operationId)

    // Qualified operationId
    val stepQualId =
        StepObject(stepId = "s2", operationId = "\$sourceDescriptions.petstore.getPetById")
    val resolved2 = registry.resolveStepOperationResult(arazzoDef, stepQualId).getOrThrow()
    assertEquals("getPetById", resolved2.endpoint.operationId)

    // operationPath with {$sourceDescriptions.petstore.url}
    val stepOpPath =
        StepObject(
            stepId = "s3",
            operationPath = "{\$sourceDescriptions.petstore.url}#/paths/~1pets~1{petId}/get")
    val resolved3 = registry.resolveStepOperationResult(arazzoDef, stepOpPath).getOrThrow()
    assertEquals("/pets/{petId}", resolved3.endpoint.path)

    // operationPath with literal URL
    val stepOpPathDirect =
        StepObject(stepId = "s4", operationPath = "./petstore.json#/paths/~1pets~1{petId}/get")
    assertEquals(
        "/pets/{petId}",
        registry.resolveStepOperationResult(arazzoDef, stepOpPathDirect).getOrThrow().endpoint.path)

    // Error branches for resolveStepOperationResult
    assertTrue(
        registry.resolveStepOperationResult(arazzoDef, StepObject(stepId = "none")).isFailure)
    assertTrue(
        registry
            .resolveStepOperationResult(
                arazzoDef, StepObject(stepId = "s", operationId = "\$sourceDescriptions.bad"))
            .isFailure)
    assertTrue(
        registry
            .resolveStepOperationResult(
                arazzoDef,
                StepObject(stepId = "s", operationId = "\$sourceDescriptions.unknown.op"))
            .isFailure)
    assertTrue(
        registry
            .resolveStepOperationResult(
                arazzoDef,
                StepObject(stepId = "s", operationId = "\$sourceDescriptions.petstore.missingOp"))
            .isFailure)
    assertTrue(
        registry
            .resolveStepOperationResult(
                arazzoDef, StepObject(stepId = "s", operationId = "missingUnqualified"))
            .isFailure)
    assertTrue(
        registry
            .resolveStepOperationResult(
                arazzoDef, StepObject(stepId = "s", operationPath = "no-hash"))
            .isFailure)
    assertTrue(
        registry
            .resolveStepOperationResult(
                arazzoDef, StepObject(stepId = "s", operationPath = "./petstore.json#/bad"))
            .isFailure)
    assertTrue(
        registry
            .resolveStepOperationResult(
                arazzoDef, StepObject(stepId = "s", operationPath = "./petstore.json#/paths/only"))
            .isFailure)
    assertTrue(
        registry
            .resolveStepOperationResult(
                arazzoDef,
                StepObject(stepId = "s", operationPath = "./petstore.json#/paths/~1missing/post"))
            .isFailure)

    // Workflow resolution (local and external)
    val localWf = registry.resolveStepWorkflowResult(arazzoDef, "adoptPetWorkflow").getOrThrow()
    assertNull(localWf.source)
    assertEquals("adoptPetWorkflow", localWf.workflow.workflowId)

    val extWf =
        registry
            .resolveStepWorkflowResult(
                arazzoDef, "\$sourceDescriptions.extArazzo.workflows.extLogin")
            .getOrThrow()
    assertEquals("extArazzo", extWf.source?.name)
    assertEquals("extLogin", extWf.workflow.workflowId)

    assertTrue(registry.resolveStepWorkflowResult(arazzoDef, "missingWf").isFailure)
    assertTrue(
        registry
            .resolveStepWorkflowResult(arazzoDef, "\$sourceDescriptions.unknown.workflows.extLogin")
            .isFailure)
    assertTrue(
        registry
            .resolveStepWorkflowResult(
                arazzoDef, "\$sourceDescriptions.extArazzo.workflows.missingWf")
            .isFailure)

    // Component dereferencing
    val comps = arazzoDef.components
    val resolvedParam =
        registry
            .resolveParameterResult(
                ReusableOr.Reference(
                    ReusableObject(
                        reference = "\$components.parameters.sharedHeader",
                        value = OpenApiInt(99L))),
                comps)
            .getOrThrow()
    assertEquals("X-Trace", resolvedParam.name)
    assertEquals(OpenApiInt(99L), resolvedParam.value)

    val resolvedParamNoOverride =
        registry
            .resolveParameterResult(
                ReusableOr.Reference(
                    ReusableObject(reference = "\$components.parameters.sharedHeader")),
                comps)
            .getOrThrow()
    assertEquals(OpenApiString("trace-1"), resolvedParamNoOverride.value)

    assertTrue(
        registry
            .resolveParameterResult(
                ReusableOr.Reference(ReusableObject(reference = "\$components.parameters.missing")),
                comps)
            .isFailure)

    val resolvedSucc =
        registry
            .resolveSuccessActionResult(
                ReusableOr.Reference(
                    ReusableObject(reference = "\$components.successActions.endFlow")),
                comps)
            .getOrThrow()
    assertEquals(SuccessActionType.END, resolvedSucc.type)
    assertTrue(
        registry
            .resolveSuccessActionResult(
                ReusableOr.Reference(
                    ReusableObject(reference = "\$components.successActions.missing")),
                comps)
            .isFailure)

    val resolvedFail =
        registry
            .resolveFailureActionResult(
                ReusableOr.Reference(
                    ReusableObject(reference = "\$components.failureActions.abortFlow")),
                comps)
            .getOrThrow()
    assertEquals(FailureActionType.END, resolvedFail.type)
    assertTrue(
        registry
            .resolveFailureActionResult(
                ReusableOr.Reference(
                    ReusableObject(reference = "\$components.failureActions.missing")),
                comps)
            .isFailure)

    // Workflow inputs resolution
    assertNull(registry.resolveWorkflowInputs(null, comps))
    val refInput1 = SchemaProperty(ref = "\$components.inputs.SharedInput")
    assertEquals(setOf("object"), registry.resolveWorkflowInputs(refInput1, comps)?.types)
    val refInput2 = SchemaProperty(ref = "#/components/inputs/SharedInput")
    assertEquals(setOf("object"), registry.resolveWorkflowInputs(refInput2, comps)?.types)
    val refInputMissing1 = SchemaProperty(ref = "\$components.inputs.Missing")
    assertEquals(refInputMissing1, registry.resolveWorkflowInputs(refInputMissing1, null))
    val refInputMissing2 = SchemaProperty(ref = "#/components/inputs/Missing")
    assertEquals(refInputMissing2, registry.resolveWorkflowInputs(refInputMissing2, null))
    val inlineInput = SchemaProperty(types = setOf("string"))
    assertEquals(inlineInput, registry.resolveWorkflowInputs(inlineInput, comps))
    assertEquals(
        ArazzoParameter(name = "inlineP", value = OpenApiString("v")),
        registry
            .resolveParameterResult(
                ReusableOr.Inline(ArazzoParameter(name = "inlineP", value = OpenApiString("v"))),
                comps)
            .getOrThrow())
    assertEquals(
        SuccessActionObject(name = "inS", type = SuccessActionType.END),
        registry
            .resolveSuccessActionResult(
                ReusableOr.Inline(SuccessActionObject(name = "inS", type = SuccessActionType.END)),
                comps)
            .getOrThrow())
    assertEquals(
        FailureActionObject(name = "inF", type = FailureActionType.END),
        registry
            .resolveFailureActionResult(
                ReusableOr.Inline(FailureActionObject(name = "inF", type = FailureActionType.END)),
                comps)
            .getOrThrow())

    // Test lookup via openApiRegistry with relative URI resolution against arazzo.self
    val reg2 = ArazzoDocumentRegistry()
    reg2.registerOpenApiSource("other", petstoreOpenApi, null)
    reg2.registerArazzoSource("otherAr", extArazzo, null)
    reg2.openApiRegistry.registerOpenApi(
        petstoreOpenApi, "https://example.com/workflows/petstore.json")
    reg2.openApiRegistry.registerArazzo(extArazzo, "https://example.com/workflows/ext.arazzo.json")
    assertEquals(
        extArazzo,
        reg2.openApiRegistry.resolveArazzo("https://example.com/workflows/ext.arazzo.json"))
    assertNull(reg2.openApiRegistry.resolveArazzo("https://example.com/workflows/petstore.json"))
    assertEquals(
        "getPetById",
        reg2
            .resolveStepOperationResult(
                arazzoDef, StepObject(stepId = "s1", operationId = "getPetById"))
            .getOrThrow()
            .endpoint
            .operationId)
    assertEquals(
        "extLogin",
        reg2
            .resolveStepWorkflowResult(
                arazzoDef, "\$sourceDescriptions.extArazzo.workflows.extLogin")
            .getOrThrow()
            .workflow
            .workflowId)

    // Unregistered source failures
    val emptyReg = ArazzoDocumentRegistry()
    assertTrue(
        emptyReg
            .resolveStepOperationResult(
                arazzoDef.copy(self = null),
                StepObject(stepId = "s", operationId = "\$sourceDescriptions.petstore.getPetById"))
            .isFailure)
    assertTrue(
        emptyReg
            .resolveStepOperationResult(
                arazzoDef,
                StepObject(
                    stepId = "s",
                    operationPath = "https://unknown.example.com/oas.json#/paths/~1pets/get"))
            .isFailure)
    assertTrue(
        emptyReg
            .resolveStepOperationResult(
                arazzoDef.copy(sourceDescriptions = emptyList()),
                StepObject(
                    stepId = "s",
                    operationPath = "https://unknown.example.com/oas.json#/paths/~1pets/get"))
            .isFailure)
    assertTrue(
        emptyReg
            .resolveStepWorkflowResult(
                arazzoDef.copy(self = null), "\$sourceDescriptions.extArazzo.workflows.extLogin")
            .isFailure)

    // Additional ArazzoDocumentRegistry branches:
    // - beforeHash starting with {$sourceDescriptions. but not ending with .url}
    // - stepWorkflowId starting with $sourceDescriptions. but fewer than 3 parts or parts[1] !=
    // "workflows"
    // - resolveParameterResult / resolveSuccessActionResult / resolveFailureActionResult when
    // components == null
    // - resolveWorkflowInputs when components != null but input key is missing
    // - lookupOpenApiForSource / lookupArazzoForSource hitting direct URL in openApiRegistry, and
    // when self is blank
    assertTrue(
        emptyReg
            .resolveStepOperationResult(
                arazzoDef.copy(self = ""),
                StepObject(
                    stepId = "s",
                    operationPath = "{\$sourceDescriptions.petstore.bad}#/paths/~1pets/get"))
            .isFailure)
    assertTrue(
        emptyReg
            .resolveStepWorkflowResult(arazzoDef.copy(self = ""), "\$sourceDescriptions.short")
            .isFailure)
    assertTrue(
        emptyReg
            .resolveStepWorkflowResult(
                arazzoDef.copy(self = ""), "\$sourceDescriptions.extArazzo.other.wf")
            .isFailure)
    assertTrue(
        emptyReg
            .resolveParameterResult(
                ReusableOr.Reference(ReusableObject(reference = "\$components.parameters.p")), null)
            .isFailure)
    assertTrue(
        emptyReg
            .resolveSuccessActionResult(
                ReusableOr.Reference(ReusableObject(reference = "\$components.successActions.s")),
                null)
            .isFailure)
    assertTrue(
        emptyReg
            .resolveFailureActionResult(
                ReusableOr.Reference(ReusableObject(reference = "\$components.failureActions.f")),
                null)
            .isFailure)
    assertEquals(
        refInputMissing1, emptyReg.resolveWorkflowInputs(refInputMissing1, ArazzoComponents()))

    val regDirectUrl = ArazzoDocumentRegistry()
    regDirectUrl.openApiRegistry.registerOpenApi(petstoreOpenApi, "./petstore.json")
    regDirectUrl.openApiRegistry.registerArazzo(extArazzo, "./ext.arazzo.json")
    assertEquals(
        "getPetById",
        regDirectUrl
            .resolveStepOperationResult(
                arazzoDef, StepObject(stepId = "s1", operationId = "getPetById"))
            .getOrThrow()
            .endpoint
            .operationId)
    assertEquals(
        "extLogin",
        regDirectUrl
            .resolveStepWorkflowResult(
                arazzoDef, "\$sourceDescriptions.extArazzo.workflows.extLogin")
            .getOrThrow()
            .workflow
            .workflowId)
  }
}
