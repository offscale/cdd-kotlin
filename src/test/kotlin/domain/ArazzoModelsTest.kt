/**
 * Exhaustive unit tests for [domain.ArazzoDefinition] and all Arazzo 1.1.0 domain models, enums,
 * and sealed hierarchies in `src/commonMain/kotlin/domain/ArazzoModels.kt`.
 */
package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit tests for all Arazzo 1.1.0 domain models and enum wire name parsers. */
class ArazzoModelsTest {

  @Test
  fun `enum fromWireName parses all valid values and fails on unknown values`() {
    assertEquals(
        SourceDescriptionType.OPENAPI, SourceDescriptionType.fromWireName("openapi").getOrThrow())
    assertEquals(
        SourceDescriptionType.ASYNCAPI, SourceDescriptionType.fromWireName("ASYNCAPI").getOrThrow())
    assertEquals(
        SourceDescriptionType.ARAZZO, SourceDescriptionType.fromWireName("arazzo").getOrThrow())
    assertEquals("openapi", SourceDescriptionType.OPENAPI.wireName)
    assertTrue(SourceDescriptionType.fromWireName("unknown").isFailure)

    assertEquals(
        ArazzoParameterLocation.PATH, ArazzoParameterLocation.fromWireName("path").getOrThrow())
    assertEquals(
        ArazzoParameterLocation.QUERY, ArazzoParameterLocation.fromWireName("query").getOrThrow())
    assertEquals(
        ArazzoParameterLocation.QUERYSTRING,
        ArazzoParameterLocation.fromWireName("querystring").getOrThrow())
    assertEquals(
        ArazzoParameterLocation.HEADER, ArazzoParameterLocation.fromWireName("header").getOrThrow())
    assertEquals(
        ArazzoParameterLocation.COOKIE, ArazzoParameterLocation.fromWireName("cookie").getOrThrow())
    assertEquals("querystring", ArazzoParameterLocation.QUERYSTRING.wireName)
    assertTrue(ArazzoParameterLocation.fromWireName("body").isFailure)

    assertEquals(StepActionType.SEND, StepActionType.fromWireName("send").getOrThrow())
    assertEquals(StepActionType.RECEIVE, StepActionType.fromWireName("RECEIVE").getOrThrow())
    assertEquals("send", StepActionType.SEND.wireName)
    assertTrue(StepActionType.fromWireName("invalid").isFailure)

    assertEquals(
        CriterionEngineType.SIMPLE, CriterionEngineType.fromWireName("simple").getOrThrow())
    assertEquals(CriterionEngineType.REGEX, CriterionEngineType.fromWireName("regex").getOrThrow())
    assertEquals(
        CriterionEngineType.JSONPATH, CriterionEngineType.fromWireName("jsonpath").getOrThrow())
    assertEquals(CriterionEngineType.XPATH, CriterionEngineType.fromWireName("xpath").getOrThrow())
    assertEquals(
        CriterionEngineType.JSONPOINTER,
        CriterionEngineType.fromWireName("jsonpointer").getOrThrow())
    assertEquals("jsonpointer", CriterionEngineType.JSONPOINTER.wireName)
    assertTrue(CriterionEngineType.fromWireName("sql").isFailure)

    assertEquals(SuccessActionType.END, SuccessActionType.fromWireName("end").getOrThrow())
    assertEquals(SuccessActionType.GOTO, SuccessActionType.fromWireName("goto").getOrThrow())
    assertEquals("goto", SuccessActionType.GOTO.wireName)
    assertTrue(SuccessActionType.fromWireName("retry").isFailure)

    assertEquals(FailureActionType.END, FailureActionType.fromWireName("end").getOrThrow())
    assertEquals(FailureActionType.RETRY, FailureActionType.fromWireName("retry").getOrThrow())
    assertEquals(FailureActionType.GOTO, FailureActionType.fromWireName("goto").getOrThrow())
    assertEquals("retry", FailureActionType.RETRY.wireName)
    assertTrue(FailureActionType.fromWireName("ignore").isFailure)
  }

  @Test
  fun `Arazzo domain models construct and preserve all properties`() {
    val ext = mapOf("x-custom" to OpenApiString("v"))
    val info =
        ArazzoInfo(
            title = "Petstore Workflows",
            summary = "Summary",
            description = "Desc",
            version = "1.0.0",
            extensions = ext)
    assertEquals("Petstore Workflows", info.title)
    assertEquals("Summary", info.summary)
    assertEquals("Desc", info.description)
    assertEquals("1.0.0", info.version)
    assertEquals(ext, info.extensions)
    assertNull(ArazzoInfo(title = "T", version = "1").summary)

    val src =
        SourceDescription(
            name = "petstore",
            url = "./petstore.json",
            type = SourceDescriptionType.OPENAPI,
            extensions = ext)
    assertEquals("petstore", src.name)
    assertEquals("./petstore.json", src.url)
    assertEquals(SourceDescriptionType.OPENAPI, src.type)
    assertEquals(ext, src.extensions)
    assertNull(SourceDescription(name = "s", url = "u").type)

    val reusable =
        ReusableObject(
            reference = "\$components.parameters.page", value = OpenApiInt(2L), extensions = ext)
    assertEquals("\$components.parameters.page", reusable.reference)
    assertEquals(OpenApiInt(2L), reusable.value)
    assertEquals(ext, reusable.extensions)
    assertNull(ReusableObject(reference = "\$components.parameters.p").value)

    val param =
        ArazzoParameter(
            name = "limit",
            location = ArazzoParameterLocation.QUERY,
            value = OpenApiInt(10L),
            extensions = ext)
    assertEquals("limit", param.name)
    assertEquals(ArazzoParameterLocation.QUERY, param.location)
    assertEquals(OpenApiInt(10L), param.value)
    assertEquals(ext, param.extensions)
    assertNull(ArazzoParameter(name = "p", value = OpenApiNull).location)

    val inlineParam: ReusableOr<ArazzoParameter> = ReusableOr.Inline(param)
    val refParam: ReusableOr<ArazzoParameter> = ReusableOr.Reference(reusable)
    assertEquals(param, (inlineParam as ReusableOr.Inline).value)
    assertEquals(reusable, (refParam as ReusableOr.Reference).reusable)

    val exprType =
        ExpressionTypeObject(
            type = CriterionEngineType.JSONPATH, version = "rfc9535", extensions = ext)
    assertEquals(CriterionEngineType.JSONPATH, exprType.type)
    assertEquals("rfc9535", exprType.version)
    assertEquals(ext, exprType.extensions)

    val engineUnion = CriterionTypeOrExpression.Engine(CriterionEngineType.SIMPLE)
    val versionedUnion = CriterionTypeOrExpression.Versioned(exprType)
    assertEquals(CriterionEngineType.SIMPLE, engineUnion.type)
    assertEquals(exprType, versionedUnion.expressionType)

    val crit =
        CriterionObject(
            context = "\$statusCode",
            condition = "\$statusCode == 200",
            type = engineUnion,
            extensions = ext)
    assertEquals("\$statusCode", crit.context)
    assertEquals("\$statusCode == 200", crit.condition)
    assertEquals(engineUnion, crit.type)
    assertEquals(ext, crit.extensions)
    assertNull(CriterionObject(condition = "true").context)

    val sel =
        SelectorObject(
            context = "\$response.body",
            selector = "\$.id",
            type = versionedUnion,
            extensions = ext)
    assertEquals("\$response.body", sel.context)
    assertEquals("\$.id", sel.selector)
    assertEquals(versionedUnion, sel.type)
    assertEquals(ext, sel.extensions)
    assertNull(SelectorObject(context = "\$response.body", selector = "/id").type)

    val outExpr = OutputValue.Expression("\$response.body#/id")
    val outSel = OutputValue.Selector(sel)
    assertEquals("\$response.body#/id", outExpr.expression)
    assertEquals(sel, outSel.selector)

    val replacement =
        PayloadReplacementObject(
            target = "/user/id",
            targetSelectorType = engineUnion,
            value = OpenApiString("\$inputs.userId"),
            extensions = ext)
    assertEquals("/user/id", replacement.target)
    assertEquals(engineUnion, replacement.targetSelectorType)
    assertEquals(OpenApiString("\$inputs.userId"), replacement.value)
    assertEquals(ext, replacement.extensions)
    assertNull(PayloadReplacementObject(target = "/a", value = OpenApiNull).targetSelectorType)

    val reqBody =
        ArazzoRequestBody(
            contentType = "application/json",
            payload = OpenApiObject(mapOf("user" to OpenApiObject(emptyMap()))),
            replacements = listOf(replacement),
            extensions = ext)
    assertEquals("application/json", reqBody.contentType)
    assertEquals(1, reqBody.replacements.size)
    assertEquals(ext, reqBody.extensions)
    assertNull(ArazzoRequestBody().contentType)

    val successAction =
        SuccessActionObject(
            name = "goNext",
            type = SuccessActionType.GOTO,
            workflowId = null,
            stepId = "step2",
            parameters = listOf(inlineParam),
            criteria = listOf(crit),
            extensions = ext)
    assertEquals("goNext", successAction.name)
    assertEquals(SuccessActionType.GOTO, successAction.type)
    assertNull(successAction.workflowId)
    assertEquals("step2", successAction.stepId)
    assertEquals(1, successAction.parameters.size)
    assertEquals(1, successAction.criteria.size)
    assertEquals(ext, successAction.extensions)

    val failureAction =
        FailureActionObject(
            name = "retryStep",
            type = FailureActionType.RETRY,
            workflowId = "recoverWf",
            stepId = "recoverStep",
            parameters = listOf(refParam),
            retryAfter = 1.5,
            retryLimit = 3,
            criteria = listOf(crit),
            extensions = ext)
    assertEquals("retryStep", failureAction.name)
    assertEquals(FailureActionType.RETRY, failureAction.type)
    assertEquals("recoverWf", failureAction.workflowId)
    assertEquals("recoverStep", failureAction.stepId)
    assertEquals(1.5, failureAction.retryAfter)
    assertEquals(3, failureAction.retryLimit)
    assertEquals(ext, failureAction.extensions)

    val step =
        StepObject(
            stepId = "step1",
            description = "First step",
            operationId = "listPets",
            operationPath = null,
            channelPath = "/pets/events",
            workflowId = null,
            parameters = listOf(inlineParam, refParam),
            requestBody = reqBody,
            successCriteria = listOf(crit),
            onSuccess = listOf(ReusableOr.Inline(successAction)),
            onFailure = listOf(ReusableOr.Inline(failureAction)),
            outputs = mapOf("id" to outExpr, "selId" to outSel),
            timeout = 5000L,
            correlationId = "\$message.header.corrId",
            action = StepActionType.SEND,
            dependsOn = listOf("step0"),
            extensions = ext)
    assertEquals("step1", step.stepId)
    assertEquals("First step", step.description)
    assertEquals("listPets", step.operationId)
    assertEquals("/pets/events", step.channelPath)
    assertEquals(5000L, step.timeout)
    assertEquals("\$message.header.corrId", step.correlationId)
    assertEquals(StepActionType.SEND, step.action)
    assertEquals(listOf("step0"), step.dependsOn)
    assertEquals(ext, step.extensions)

    val wf =
        Workflow(
            workflowId = "adoptPet",
            summary = "Adopt a pet",
            description = "Full adoption flow",
            inputs = SchemaProperty(types = setOf("object")),
            dependsOn = listOf("authWf"),
            steps = listOf(step),
            successActions = listOf(ReusableOr.Inline(successAction)),
            failureActions = listOf(ReusableOr.Inline(failureAction)),
            outputs = mapOf("petId" to outExpr),
            parameters = listOf(inlineParam),
            extensions = ext)
    assertEquals("adoptPet", wf.workflowId)
    assertEquals("Adopt a pet", wf.summary)
    assertEquals("Full adoption flow", wf.description)
    assertEquals(listOf("authWf"), wf.dependsOn)
    assertEquals(1, wf.steps.size)
    assertEquals(ext, wf.extensions)

    val comps =
        ArazzoComponents(
            inputs = mapOf("UserIn" to SchemaProperty(types = setOf("object"))),
            parameters = mapOf("page" to param),
            successActions = mapOf("done" to successAction),
            failureActions = mapOf("retry" to failureAction),
            extensions = ext)
    assertEquals(1, comps.inputs.size)
    assertEquals(1, comps.parameters.size)
    assertEquals(1, comps.successActions.size)
    assertEquals(1, comps.failureActions.size)
    assertEquals(ext, comps.extensions)

    val def =
        ArazzoDefinition(
            arazzo = "1.1.0",
            self = "https://example.com/arazzo.yaml",
            info = info,
            sourceDescriptions = listOf(src),
            workflows = listOf(wf),
            components = comps,
            extensions = ext)
    assertEquals("1.1.0", def.arazzo)
    assertEquals("https://example.com/arazzo.yaml", def.self)
    assertEquals(info, def.info)
    assertEquals(1, def.sourceDescriptions.size)
    assertEquals(1, def.workflows.size)
    assertEquals(comps, def.components)
    assertEquals(ext, def.extensions)
  }
}
