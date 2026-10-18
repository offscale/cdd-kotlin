/**
 * Exhaustive unit tests for [arazzo.RuntimeExpressionEngine] and [arazzo.ArazzoCriterionEvaluator]
 * covering all 17 runtime expression roots, string templates,
 * `simple`/`regex`/`jsonpath`/`jsonpointer`/`xpath` criteria, `SelectorObject` extraction, and
 * `PayloadReplacementObject` mutations.
 */
package arazzo

import domain.ArazzoComponents
import domain.ArazzoParameter
import domain.ArazzoParameterLocation
import domain.ArazzoRequestBody
import domain.CriterionEngineType
import domain.CriterionObject
import domain.CriterionTypeOrExpression
import domain.ExpressionTypeObject
import domain.FailureActionObject
import domain.FailureActionType
import domain.OpenApiArray
import domain.OpenApiBoolean
import domain.OpenApiInt
import domain.OpenApiNull
import domain.OpenApiNumber
import domain.OpenApiObject
import domain.OpenApiString
import domain.OutputValue
import domain.PayloadReplacementObject
import domain.SchemaProperty
import domain.SelectorObject
import domain.SuccessActionObject
import domain.SuccessActionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit tests for Arazzo 1.1.0 runtime expression and criterion evaluation. */
class RuntimeExpressionAndCriterionTest {

  private fun buildPopulatedContext(): WorkflowExecutionContext {
    return WorkflowExecutionContext(
        selfUri = "https://example.com/arazzo.yaml",
        inputs =
            mapOf(
                "userId" to OpenApiString("u-100"),
                "nestedIn" to OpenApiObject(mapOf("code" to OpenApiInt(42L)))),
        workflowOutputs =
            LinkedHashMap(
                mapOf(
                    "finalToken" to OpenApiString("tok-xyz"),
                    "nestedOut" to OpenApiObject(mapOf("ok" to OpenApiBoolean(true))))),
        stepOutputs =
            LinkedHashMap(
                mapOf(
                    "loginStep" to
                        mapOf(
                            "token" to OpenApiString("jwt-999"),
                            "profile" to OpenApiObject(mapOf("role" to OpenApiString("admin")))))),
        externalWorkflowOutputs =
            LinkedHashMap(mapOf("authWf" to mapOf("sessionId" to OpenApiString("sess-1")))),
        externalWorkflowInputs =
            LinkedHashMap(mapOf("authWf" to mapOf("tenant" to OpenApiString("acme")))),
        sourceUrls = mapOf("petstore" to "https://petstore.example.com/v2"),
        components =
            ArazzoComponents(
                inputs = mapOf("SharedIn" to SchemaProperty(types = setOf("object"))),
                parameters =
                    mapOf(
                        "apiKey" to
                            ArazzoParameter(
                                name = "X-Api-Key",
                                location = ArazzoParameterLocation.HEADER,
                                value = OpenApiString("key-123"))),
                successActions =
                    mapOf(
                        "finish" to
                            SuccessActionObject(name = "finish", type = SuccessActionType.END)),
                failureActions =
                    mapOf(
                        "abort" to
                            FailureActionObject(name = "abort", type = FailureActionType.END))),
        currentRequest =
            StepRequestSnapshot(
                url = "https://petstore.example.com/v2/pets/10",
                method = "POST",
                headers = mapOf("Authorization" to "Bearer jwt-999"),
                query = mapOf("filter" to "active"),
                path = mapOf("petId" to "10"),
                body =
                    OpenApiObject(
                        mapOf(
                            "item" to OpenApiObject(mapOf("id" to OpenApiInt(10L))),
                            "tags" to OpenApiArray(listOf(OpenApiString("dog")))))),
        currentResponse =
            StepResponseSnapshot(
                statusCode = 200,
                headers = mapOf("X-Rate-Limit" to "50"),
                body =
                    OpenApiObject(
                        mapOf(
                            "id" to OpenApiInt(10L),
                            "status" to OpenApiString("available"),
                            "price" to OpenApiNumber(19.99),
                            "items" to
                                OpenApiArray(
                                    listOf(
                                        OpenApiObject(
                                            mapOf(
                                                "name" to OpenApiString("bone"),
                                                "active" to OpenApiBoolean(true),
                                                "cost" to OpenApiInt(5L)))))))),
        currentMessage =
            StepMessageSnapshot(
                headers = mapOf("correlationId" to "corr-77"),
                payload =
                    OpenApiObject(
                        mapOf("event" to OpenApiObject(mapOf("type" to OpenApiString("created"))))),
                correlationId = "corr-77",
                action = "send"))
  }

  @Test
  fun `RuntimeExpressionEngine parses and evaluates all 17 roots and templates`() {
    val ctx = buildPopulatedContext()

    assertTrue(RuntimeExpressionEngine.isOrContainsExpression("\$statusCode"))
    assertTrue(
        RuntimeExpressionEngine.isOrContainsExpression("Bearer {\$steps.loginStep.outputs.token}"))
    assertFalse(RuntimeExpressionEngine.isOrContainsExpression("plain-literal"))
    assertEquals(
        OpenApiString("plain-literal"),
        RuntimeExpressionEngine.evaluate("plain-literal", ctx).getOrThrow())

    // 1. $url
    assertEquals(
        OpenApiString("https://petstore.example.com/v2/pets/10"),
        RuntimeExpressionEngine.evaluate("\$url", ctx).getOrThrow())
    // 2. $method
    assertEquals(
        OpenApiString("POST"), RuntimeExpressionEngine.evaluate("\$method", ctx).getOrThrow())
    // 3. $statusCode
    assertEquals(
        OpenApiInt(200L), RuntimeExpressionEngine.evaluate("\$statusCode", ctx).getOrThrow())
    // 4. $request.header.<name>
    assertEquals(
        OpenApiString("Bearer jwt-999"),
        RuntimeExpressionEngine.evaluate("\$request.header.authorization", ctx).getOrThrow())
    // 5. $request.query.<name>
    assertEquals(
        OpenApiString("active"),
        RuntimeExpressionEngine.evaluate("\$request.query.filter", ctx).getOrThrow())
    // 6. $request.path.<name>
    assertEquals(
        OpenApiString("10"),
        RuntimeExpressionEngine.evaluate("\$request.path.petId", ctx).getOrThrow())
    // 7. $request.body & pointer
    assertEquals(
        OpenApiInt(10L),
        RuntimeExpressionEngine.evaluate("\$request.body#/item/id", ctx).getOrThrow())
    assertEquals(
        OpenApiString("dog"),
        RuntimeExpressionEngine.evaluate("\$request.body#/tags/0", ctx).getOrThrow())
    assertTrue(
        RuntimeExpressionEngine.evaluate("\$request.body", ctx).getOrThrow() is OpenApiObject)
    // 8. $response.header.<name>
    assertEquals(
        OpenApiString("50"),
        RuntimeExpressionEngine.evaluate("\$response.header.x-rate-limit", ctx).getOrThrow())
    // 9. $response.body & pointer
    assertEquals(
        OpenApiString("available"),
        RuntimeExpressionEngine.evaluate("\$response.body#/status", ctx).getOrThrow())
    assertTrue(
        RuntimeExpressionEngine.evaluate("\$response.body", ctx).getOrThrow() is OpenApiObject)
    // 10. $message.header.<name>
    assertEquals(
        OpenApiString("corr-77"),
        RuntimeExpressionEngine.evaluate("\$message.header.correlationId", ctx).getOrThrow())
    // 11. $message.payload & pointer / dot
    assertEquals(
        OpenApiString("created"),
        RuntimeExpressionEngine.evaluate("\$message.payload#/event/type", ctx).getOrThrow())
    assertEquals(
        OpenApiString("created"),
        RuntimeExpressionEngine.evaluate("\$message.payload.event.type", ctx).getOrThrow())
    assertTrue(
        RuntimeExpressionEngine.evaluate("\$message.payload", ctx).getOrThrow() is OpenApiObject)
    // 12. $inputs.<name>
    assertEquals(
        OpenApiString("u-100"),
        RuntimeExpressionEngine.evaluate("\$inputs.userId", ctx).getOrThrow())
    assertEquals(
        OpenApiInt(42L),
        RuntimeExpressionEngine.evaluate("\$inputs.nestedIn#/code", ctx).getOrThrow())
    assertEquals(
        OpenApiInt(42L),
        RuntimeExpressionEngine.evaluate("\$inputs.nestedIn.code", ctx).getOrThrow())
    // 13. $outputs.<name>
    assertEquals(
        OpenApiString("tok-xyz"),
        RuntimeExpressionEngine.evaluate("\$outputs.finalToken", ctx).getOrThrow())
    assertEquals(
        OpenApiBoolean(true),
        RuntimeExpressionEngine.evaluate("\$outputs.nestedOut#/ok", ctx).getOrThrow())
    // 14. $steps.<stepId>.outputs.<name>
    assertEquals(
        OpenApiString("jwt-999"),
        RuntimeExpressionEngine.evaluate("\$steps.loginStep.outputs.token", ctx).getOrThrow())
    assertEquals(
        OpenApiString("admin"),
        RuntimeExpressionEngine.evaluate("\$steps.loginStep.outputs.profile#/role", ctx)
            .getOrThrow())
    // 15. $workflows.<workflowId>.outputs.<name> & inputs
    assertEquals(
        OpenApiString("sess-1"),
        RuntimeExpressionEngine.evaluate("\$workflows.authWf.outputs.sessionId", ctx).getOrThrow())
    assertEquals(
        OpenApiString("acme"),
        RuntimeExpressionEngine.evaluate("\$workflows.authWf.inputs.tenant", ctx).getOrThrow())
    // 16. $sourceDescriptions.<name>.url & target
    assertEquals(
        OpenApiString("https://petstore.example.com/v2"),
        RuntimeExpressionEngine.evaluate("\$sourceDescriptions.petstore.url", ctx).getOrThrow())
    assertEquals(
        OpenApiString("https://petstore.example.com/v2#getPetById"),
        RuntimeExpressionEngine.evaluate("\$sourceDescriptions.petstore.getPetById", ctx)
            .getOrThrow())
    // 17. $components.<category>.<name>
    assertEquals(
        OpenApiString("key-123"),
        RuntimeExpressionEngine.evaluate("\$components.parameters.apiKey", ctx).getOrThrow())
    assertEquals(
        OpenApiString("\$components.inputs.SharedIn"),
        RuntimeExpressionEngine.evaluate("\$components.inputs.SharedIn", ctx).getOrThrow())
    assertEquals(
        OpenApiString("finish"),
        RuntimeExpressionEngine.evaluate("\$components.successActions.finish", ctx).getOrThrow())
    assertEquals(
        OpenApiString("abort"),
        RuntimeExpressionEngine.evaluate("\$components.failureActions.abort", ctx).getOrThrow())
    // 18. $self
    assertEquals(
        OpenApiString("https://example.com/arazzo.yaml"),
        RuntimeExpressionEngine.evaluate("\$self", ctx).getOrThrow())

    // Embedded Template
    assertEquals(
        OpenApiString("Bearer jwt-999 for user u-100 (status 200)"),
        RuntimeExpressionEngine.evaluate(
                "Bearer {\$steps.loginStep.outputs.token} for user {\$inputs.userId} (status {\$statusCode})",
                ctx)
            .getOrThrow())

    // evaluateValue recursive
    val complexVal =
        OpenApiObject(
            mapOf(
                "auth" to OpenApiString("Bearer {\$steps.loginStep.outputs.token}"),
                "arr" to OpenApiArray(listOf(OpenApiString("\$inputs.userId"), OpenApiInt(5L))),
                "plain" to OpenApiString("static")))
    val evaluatedComplex = RuntimeExpressionEngine.evaluateValue(complexVal, ctx).getOrThrow()
    val objMap = (evaluatedComplex as OpenApiObject).properties
    assertEquals(OpenApiString("Bearer jwt-999"), objMap["auth"])
    assertEquals(OpenApiArray(listOf(OpenApiString("u-100"), OpenApiInt(5L))), objMap["arr"])
    assertEquals(OpenApiString("static"), objMap["plain"])
  }

  @Test
  fun `RuntimeExpressionEngine handles all syntax and missing context error branches`() {
    val emptyCtx = WorkflowExecutionContext()
    assertTrue(RuntimeExpressionEngine.parseExpression("").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("no-dollar").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("{not_dollar}").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("prefix {\$url").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$unknownRoot").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$request.header.").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$request.query.").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$request.path.").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$request.body#bad").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$request.other").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$response.header.").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$response.body#bad").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$response.other").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$message.header.").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$message.payload#bad").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$message.other").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$inputs.").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$inputs.#/a").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$outputs.").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$outputs.#/a").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$steps.noDot").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$steps.s1.noSecondDot").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$steps.s1.badCat.x").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$workflows.noDot").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$workflows.w1.noSecondDot").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$workflows.w1.badCat.x").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$sourceDescriptions.noDot").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$sourceDescriptions.s1.").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$components.noDot").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$components.badCat.x").isFailure)

    // Missing context evaluations
    assertTrue(RuntimeExpressionEngine.evaluate("\$url", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$method", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$statusCode", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$self", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$request.header.h", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$request.query.q", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$request.path.p", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$request.body", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$response.header.h", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$response.body", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$message.header.h", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$message.payload", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$inputs.missing", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$outputs.missing", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$steps.s1.outputs.o", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$workflows.w1.outputs.o", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$sourceDescriptions.s1.url", emptyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$components.parameters.p", emptyCtx).isFailure)

    // Pointer errors
    val popCtx = buildPopulatedContext()
    assertTrue(RuntimeExpressionEngine.evaluate("\$request.header.missing", popCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$request.query.missing", popCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$request.path.missing", popCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$response.header.missing", popCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$message.header.missing", popCtx).isFailure)
    assertTrue(
        RuntimeExpressionEngine.evaluate("\$steps.loginStep.outputs.missing", popCtx).isFailure)
    assertTrue(
        RuntimeExpressionEngine.evaluate("\$workflows.authWf.outputs.missing", popCtx).isFailure)
    assertTrue(
        RuntimeExpressionEngine.evaluate("\$components.parameters.missing", popCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$components.inputs.missing", popCtx).isFailure)
    assertTrue(
        RuntimeExpressionEngine.evaluate("\$components.successActions.missing", popCtx).isFailure)
    assertTrue(
        RuntimeExpressionEngine.evaluate("\$components.failureActions.missing", popCtx).isFailure)
    assertTrue(
        RuntimeExpressionEngine.resolveJsonPointer(OpenApiObject(emptyMap()), "noSlash").isFailure)
    assertTrue(
        RuntimeExpressionEngine.resolveJsonPointer(OpenApiObject(emptyMap()), "/missing").isFailure)
    assertTrue(
        RuntimeExpressionEngine.resolveJsonPointer(OpenApiArray(listOf(OpenApiInt(1L))), "/badIdx")
            .isFailure)
    assertTrue(
        RuntimeExpressionEngine.resolveJsonPointer(OpenApiArray(listOf(OpenApiInt(1L))), "/5")
            .isFailure)
    assertTrue(RuntimeExpressionEngine.resolveJsonPointer(OpenApiInt(1L), "/child").isFailure)
    assertEquals(
        OpenApiInt(1L),
        RuntimeExpressionEngine.resolveJsonPointer(OpenApiInt(1L), "#").getOrThrow())
  }

  @Test
  fun `ArazzoCriterionEvaluator evaluates simple, regex, jsonpath, jsonpointer, xpath, selectors, and replacements`() {
    val ctx = buildPopulatedContext()

    // Simple conditions
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$statusCode",
                    condition =
                        "(\$statusCode == 200 && \$response.body#/status == 'available') || \$statusCode == 201"),
                ctx)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$statusCode >= 200 && \$statusCode < 300 && !false"),
                ctx)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    condition =
                        "\$response.body#/price <= 25.0 && \$response.body#/price > 10.0 && \$statusCode != 500"),
                ctx)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$response.body", condition = """/status == "available""""),
                ctx)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$statusCode == 404 || \$statusCode == 500"), ctx)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = ""), ctx).isFailure)

    // Regex condition
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$response.body#/status",
                    condition = "^avail.*",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.REGEX)),
                ctx)
            .getOrThrow())

    // JSONPath condition
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$response.body",
                    condition = "\$.items[0].name == 'bone'",
                    type =
                        CriterionTypeOrExpression.Versioned(
                            ExpressionTypeObject(
                                type = CriterionEngineType.JSONPATH, version = "rfc9535"))),
                ctx)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$response.body",
                    condition = "\$.items[?(@.active == true)]",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.JSONPATH)),
                ctx)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$response.body",
                    condition = "\$.items[*].name",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.JSONPATH)),
                ctx)
            .getOrThrow())

    // JSONPointer condition
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$response.body",
                    condition = "/items/0/active",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.JSONPOINTER)),
                ctx)
            .getOrThrow())

    // XPath condition on object and XML string
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$response.body",
                    condition = "/status",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.XPATH)),
                ctx)
            .getOrThrow())
    ctx.currentResponse =
        StepResponseSnapshot(
            statusCode = 200, body = OpenApiString("<order><id>ORD-9</id></order>"))
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$response.body",
                    condition = "//order/id",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.XPATH)),
                ctx)
            .getOrThrow())

    // evaluateAllCriteria
    val ctx2 = buildPopulatedContext()
    assertTrue(
        ArazzoCriterionEvaluator.evaluateAllCriteria(
                listOf(
                    CriterionObject(condition = "\$statusCode == 200"),
                    CriterionObject(condition = "\$inputs.userId == 'u-100'")),
                ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateAllCriteria(
                listOf(
                    CriterionObject(condition = "\$statusCode == 200"),
                    CriterionObject(condition = "\$statusCode == 404")),
                ctx2)
            .getOrThrow())

    // Selectors & OutputValue
    assertEquals(
        OpenApiString("bone"),
        ArazzoCriterionEvaluator.evaluateOutput(
                OutputValue.Selector(
                    SelectorObject(context = "\$response.body", selector = "\$.items[0].name")),
                ctx2)
            .getOrThrow())
    assertEquals(
        OpenApiInt(10L),
        ArazzoCriterionEvaluator.evaluateOutput(
                OutputValue.Selector(SelectorObject(context = "\$response.body", selector = "/id")),
                ctx2)
            .getOrThrow())
    assertEquals(
        OpenApiString("available"),
        ArazzoCriterionEvaluator.evaluateOutput(
                OutputValue.Selector(
                    SelectorObject(context = "\$response.body", selector = "//status")),
                ctx2)
            .getOrThrow())
    assertEquals(
        OpenApiString("avail"),
        ArazzoCriterionEvaluator.evaluateOutput(
                OutputValue.Selector(
                    SelectorObject(
                        context = "\$response.body#/status",
                        selector = "avail",
                        type = CriterionTypeOrExpression.Engine(CriterionEngineType.REGEX))),
                ctx2)
            .getOrThrow())
    assertEquals(
        OpenApiString("u-100"),
        ArazzoCriterionEvaluator.evaluateOutput(OutputValue.Expression("\$inputs.userId"), ctx2)
            .getOrThrow())

    // Payload replacements (JSON Pointer, JSONPath, XPath, Array index)
    val reqBody =
        ArazzoRequestBody(
            payload =
                OpenApiObject(
                    mapOf(
                        "user" to OpenApiObject(mapOf("id" to OpenApiString("old"))),
                        "items" to OpenApiArray(listOf(OpenApiString("first"))))),
            replacements =
                listOf(
                    PayloadReplacementObject(
                        target = "/user/id", value = OpenApiString("\$inputs.userId")),
                    PayloadReplacementObject(
                        target = "\$.user.role",
                        value = OpenApiString("\$steps.loginStep.outputs.profile#/role")),
                    PayloadReplacementObject(
                        target = "//items/0", value = OpenApiString("updated-first"))))
    val mutated = ArazzoCriterionEvaluator.applyPayloadReplacements(reqBody, ctx2).getOrThrow()
    val mutatedObj = mutated as OpenApiObject
    val userObj = mutatedObj.properties["user"] as OpenApiObject
    assertEquals(OpenApiString("u-100"), userObj.properties["id"])
    assertEquals(OpenApiString("admin"), userObj.properties["role"])
    val itemsArr = mutatedObj.properties["items"] as OpenApiArray
    assertEquals(OpenApiString("updated-first"), itemsArr.elements[0])

    assertTrue(
        ArazzoCriterionEvaluator.applyPayloadReplacements(
                ArazzoRequestBody(
                    payload = OpenApiObject(emptyMap()),
                    replacements =
                        listOf(PayloadReplacementObject(target = "", value = OpenApiNull))),
                ctx2)
            .isFailure)

    // Additional RuntimeExpressionEngine and ArazzoCriterionEvaluator edge branches
    assertTrue(RuntimeExpressionEngine.parseExpression("\$url{unclosed").isFailure)
    assertTrue(
        RuntimeExpressionEngine.evaluateValue(OpenApiArray(listOf(OpenApiString("\$bad"))), ctx2)
            .isFailure)
    assertTrue(
        RuntimeExpressionEngine.evaluateValue(
                OpenApiObject(mapOf("k" to OpenApiString("\$bad"))), ctx2)
            .isFailure)
    assertEquals(
        OpenApiString("true"),
        RuntimeExpressionEngine.evaluate("{\$outputs.nestedOut#/ok}", ctx2).getOrThrow())
    assertEquals(
        OpenApiString("19.99"),
        RuntimeExpressionEngine.evaluate("{\$response.body#/price}", ctx2).getOrThrow())
    ctx2.workflowOutputs["nullVal"] = OpenApiNull
    ctx2.workflowOutputs["objVal"] = OpenApiObject(mapOf("a" to OpenApiInt(1L)))
    assertEquals(
        OpenApiString("null"),
        RuntimeExpressionEngine.evaluate("{\$outputs.nullVal}", ctx2).getOrThrow())
    assertTrue(
        RuntimeExpressionEngine.evaluate("{\$outputs.objVal}", ctx2)
            .getOrThrow()
            .toString()
            .contains("a"))
    assertTrue(
        RuntimeExpressionEngine.evaluateAst(
                RuntimeExpressionAst.Components("badCat", "x"), ctx2, "\$components.badCat.x")
            .isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("{\$badRoot}", ctx2).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("{\$inputs.missing}", ctx2).isFailure)
    assertEquals(
        OpenApiInt(1L), RuntimeExpressionEngine.resolveJsonPointer(OpenApiInt(1L), "").getOrThrow())

    // Additional ArazzoCriterionEvaluator branches
    assertTrue(
        ArazzoCriterionEvaluator.evaluateAllCriteria(listOf(CriterionObject(condition = "")), ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.evaluateSelector(
                SelectorObject(context = "\$badRoot", selector = "/id"), ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.evaluateSelector(
                SelectorObject(
                    context = "\$response.body",
                    selector = "/missing",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.JSONPOINTER)),
                ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.evaluateSelector(
                SelectorObject(
                    context = "\$response.body#/status",
                    selector = "nomatch_zzz",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.REGEX)),
                ctx2)
            .isFailure)
    assertEquals(
        OpenApiString("u-100"),
        ArazzoCriterionEvaluator.evaluateSelector(
                SelectorObject(
                    context = "\$response.body",
                    selector = "\$inputs.userId",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.SIMPLE)),
                ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateSelector(
                SelectorObject(context = "\$response.body", selector = "status"), ctx2)
            .isFailure)

    // applyPayloadReplacements edge cases
    assertEquals(
        OpenApiString("replacedRoot"),
        ArazzoCriterionEvaluator.applyPayloadReplacements(
                ArazzoRequestBody(
                    payload = null,
                    replacements =
                        listOf(
                            PayloadReplacementObject(
                                target = "/", value = OpenApiString("replacedRoot")))),
                ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.applyPayloadReplacements(
                ArazzoRequestBody(payload = OpenApiString("\$badRoot"), replacements = emptyList()),
                ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.applyPayloadReplacements(
                ArazzoRequestBody(
                    payload = OpenApiObject(emptyMap()),
                    replacements =
                        listOf(
                            PayloadReplacementObject(
                                target = "/a", value = OpenApiString("\$badRoot")))),
                ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.applyPayloadReplacements(
                ArazzoRequestBody(
                    payload = OpenApiArray(listOf(OpenApiObject(mapOf("x" to OpenApiInt(1L))))),
                    replacements =
                        listOf(PayloadReplacementObject(target = "/0/x", value = OpenApiInt(2L)))),
                ctx2)
            .isSuccess)
    assertTrue(
        ArazzoCriterionEvaluator.applyPayloadReplacements(
                ArazzoRequestBody(
                    payload = OpenApiArray(listOf(OpenApiInt(1L))),
                    replacements =
                        listOf(
                            PayloadReplacementObject(target = "/badIdx", value = OpenApiInt(2L)))),
                ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.applyPayloadReplacements(
                ArazzoRequestBody(
                    payload = OpenApiArray(listOf(OpenApiInt(1L))),
                    replacements =
                        listOf(PayloadReplacementObject(target = "/9", value = OpenApiInt(2L)))),
                ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.applyPayloadReplacements(
                ArazzoRequestBody(
                    payload = OpenApiInt(1L),
                    replacements =
                        listOf(PayloadReplacementObject(target = "/a", value = OpenApiInt(2L)))),
                ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.applyPayloadReplacements(
                ArazzoRequestBody(
                    payload = OpenApiObject(mapOf("p" to OpenApiInt(1L))),
                    replacements =
                        listOf(
                            PayloadReplacementObject(target = "/p/sub", value = OpenApiInt(2L)))),
                ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.applyPayloadReplacements(
                ArazzoRequestBody(
                    payload = OpenApiArray(listOf(OpenApiInt(1L))),
                    replacements =
                        listOf(
                            PayloadReplacementObject(target = "/0/sub", value = OpenApiInt(2L)))),
                ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.applyPayloadReplacements(
                ArazzoRequestBody(
                    payload = OpenApiObject(emptyMap()),
                    replacements =
                        listOf(
                            PayloadReplacementObject(
                                target = "relativeKey", value = OpenApiInt(2L)))),
                ctx2)
            .isSuccess)

    // Simple condition string comparisons and error branches
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    condition =
                        "'apple' < 'banana' && 'b' <= 'b' && 'z' > 'a' && 'z' >= 'z' && 'a' != 'b'"),
                ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "true"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "null"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "0"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "0.0"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$badRoot == 1"), ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "1 == \$badRoot"), ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$badRoot || true"), ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$badRoot && true"), ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$badRoot",
                    condition = ".*",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.REGEX)),
                ctx2)
            .isFailure)

    // JSONPath and XPath error/edge branches
    assertTrue(ArazzoCriterionEvaluator.queryJsonPath(OpenApiInt(1L), "noDollar").isFailure)
    assertEquals(
        OpenApiInt(1L), ArazzoCriterionEvaluator.queryJsonPath(OpenApiInt(1L), "\$").getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.queryJsonPath(
                OpenApiArray(listOf(OpenApiObject(mapOf("a" to OpenApiInt(1L))))), "\$[?(@.a)]")
            .isSuccess)
    assertTrue(
        ArazzoCriterionEvaluator.queryJsonPath(OpenApiObject(mapOf("a" to OpenApiInt(1L))), "\$.*")
            .isSuccess)
    assertTrue(ArazzoCriterionEvaluator.queryJsonPath(OpenApiInt(1L), "\$.*").isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.queryJsonPath(OpenApiArray(listOf(OpenApiInt(1L))), "\$[5]")
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.queryJsonPath(
                OpenApiArray(listOf(OpenApiObject(mapOf("a" to OpenApiInt(1L))))), "\$.missingProp")
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.queryJsonPath(
                OpenApiObject(mapOf("a" to OpenApiInt(1L))), "\$.missing")
            .isFailure)
    assertTrue(ArazzoCriterionEvaluator.queryJsonPath(OpenApiInt(1L), "\$.child").isFailure)
    assertTrue(ArazzoCriterionEvaluator.queryJsonPath(OpenApiInt(1L), "\$[?(@.a == 1)]").isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.queryJsonPath(
                OpenApiArray(listOf(OpenApiInt(1L))), "\$[?(@.a == 1)]")
            .isFailure)

    assertTrue(ArazzoCriterionEvaluator.queryXPath(OpenApiInt(1L), "noSlash").isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.queryXPath(OpenApiString("<root></root>"), "//missingTag")
            .isFailure)
    assertTrue(ArazzoCriterionEvaluator.queryXPath(OpenApiObject(emptyMap()), "/missing").isFailure)

    // Cover remaining ArazzoCriterionEvaluator branches:
    // balanced vs unbalanced parens, quotes inside logical & comparison operators,
    // valueToString/isTruthy on all types,
    // evaluateJsonPathCondition / evaluateJsonPointerCondition / evaluateXPathCondition error/false
    // branches,
    // tokenizeJsonPath bracketed subpaths (`a[0].b`, unclosed `[`), filter failure inside path
    // (`items[?(@.missing)]`),
    // multiple matches in array mapping, filter on single object, and pointer error in simple
    // condition.
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "(1 == 1) && (\"a||b\" != 'c&&d')"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "true && false"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "1 != 2 && 1 < 2 && 2 <= 2 && 3 > 2 && 3 >= 3"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "1 == 2"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "1 != 1"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "2 < 1"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "2 <= 1"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "1 > 2"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "1 >= 2"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "'b' < 'a'"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "'b' <= 'a'"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "'a' > 'b'"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "'a' >= 'b'"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "'a' != 'a'"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(context = "\$badRoot", condition = "/status == 'ok'"), ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$badRoot",
                    condition = "\$.id",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.JSONPATH)),
                ctx2)
            .isFailure)
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = null,
                    condition = "\$.missing == 'x'",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.JSONPATH)),
                ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = null,
                    condition = "\$.id == \$badRoot",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.JSONPATH)),
                ctx2)
            .isFailure)
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = null,
                    condition = "\$.missing",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.JSONPATH)),
                ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$badRoot",
                    condition = "/id",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.JSONPOINTER)),
                ctx2)
            .isFailure)
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = null,
                    condition = "/missing",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.JSONPOINTER)),
                ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$badRoot",
                    condition = "/id",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.XPATH)),
                ctx2)
            .isFailure)
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = null,
                    condition = "/missing",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.XPATH)),
                ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = null,
                    condition = "/status",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.XPATH)),
                ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = null,
                    condition = "10",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.REGEX)),
                ctx2)
            .getOrThrow())

    // JSONPath nested filter, multi-element array property projection, object filter, unclosed
    // bracket
    val multiArr =
        OpenApiObject(
            mapOf(
                "list" to
                    OpenApiArray(
                        listOf(
                            OpenApiObject(mapOf("k" to OpenApiInt(1L))),
                            OpenApiObject(mapOf("k" to OpenApiInt(2L))),
                            OpenApiInt(3L))),
                "obj" to OpenApiObject(mapOf("k" to OpenApiInt(1L)))))
    assertTrue(ArazzoCriterionEvaluator.queryJsonPath(multiArr, "\$.list.k").isSuccess)
    assertTrue(ArazzoCriterionEvaluator.queryJsonPath(multiArr, "\$.obj[?(@.k == 1)]").isSuccess)
    assertTrue(ArazzoCriterionEvaluator.queryJsonPath(multiArr, "\$.list[?(@.k == 99)]").isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.queryJsonPath(multiArr, "\$.list[?(@.k == \$badRoot)]").isFailure)
    assertTrue(ArazzoCriterionEvaluator.queryJsonPath(multiArr, "\$.list[0].k").isSuccess)
    assertTrue(ArazzoCriterionEvaluator.queryJsonPath(multiArr, "\$.list[unclosed").isFailure)
    assertTrue(ArazzoCriterionEvaluator.queryJsonPath(multiArr, "\$.list[]").isSuccess)

    // isTruthy & valueToString on all OpenApiValue variants
    ctx2.workflowOutputs["emptyStr"] = OpenApiString("")
    ctx2.workflowOutputs["falseStr"] = OpenApiString("false")
    ctx2.workflowOutputs["emptyArr"] = OpenApiArray(emptyList())
    ctx2.workflowOutputs["nonEmptyArr"] = OpenApiArray(listOf(OpenApiInt(1L)))
    ctx2.workflowOutputs["emptyObj"] = OpenApiObject(emptyMap())
    ctx2.workflowOutputs["nonEmptyObj"] = OpenApiObject(mapOf("a" to OpenApiInt(1L)))
    ctx2.workflowOutputs["numVal"] = OpenApiNumber(3.14)
    ctx2.workflowOutputs["boolVal"] = OpenApiBoolean(true)
    ctx2.workflowOutputs["intVal"] = OpenApiInt(7L)
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.emptyStr"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.falseStr"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.emptyArr"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.nonEmptyArr"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.emptyObj"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.nonEmptyObj"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.numVal == '3.14'"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.boolVal == 'true'"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.intVal == '7'"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.nullVal == 'null'"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.nonEmptyObj != 'other'"), ctx2)
            .getOrThrow())

    // Remaining RuntimeExpressionEngine & ArazzoCriterionEvaluator branches:
    // - $steps.s1.outputs. (blank nameAndPointer)
    // - $workflows.w1.outputs. and $workflows.w1.inputs. (blank nameAndPointer)
    // - $components.parameters. (blank name)
    // - currentRequest non-null with body == null, currentResponse non-null with body == null,
    // currentMessage non-null with payload == null
    // - replaceAtSegments when intermediate property already exists in object
    // - evaluateSimpleCondition !<error>
    // - resolveSimpleOperand with mismatched quotes ('a" or "a') and contextExpr == null with
    // '/pointer'
    // - compareValues where leftNum != null and rightNum == null
    // - evaluateJsonPathFilter where propName is missing on an object element
    // - queryXPath when root is not OpenApiString
    // - stripOuterParens with nested parens ((a))
    // - valueToString / isTruthy on OpenApiInt(0) and OpenApiNumber(0.0) via direct criterion
    assertTrue(RuntimeExpressionEngine.parseExpression("\$steps.s1.outputs.").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$workflows.w1.outputs.").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$workflows.w1.inputs.").isFailure)
    assertTrue(RuntimeExpressionEngine.parseExpression("\$components.parameters.").isFailure)

    val nullBodyCtx =
        WorkflowExecutionContext(
            currentRequest = StepRequestSnapshot(body = null),
            currentResponse = StepResponseSnapshot(body = null),
            currentMessage = StepMessageSnapshot(payload = null))
    assertTrue(RuntimeExpressionEngine.evaluate("\$request.body", nullBodyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$response.body", nullBodyCtx).isFailure)
    assertTrue(RuntimeExpressionEngine.evaluate("\$message.payload", nullBodyCtx).isFailure)

    assertTrue(
        ArazzoCriterionEvaluator.applyPayloadReplacements(
                ArazzoRequestBody(
                    payload = OpenApiObject(emptyMap()),
                    replacements =
                        listOf(
                            PayloadReplacementObject(
                                target = "/newParent/child", value = OpenApiInt(1L)))),
                ctx2)
            .isSuccess)
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "!\$badRoot"), ctx2)
            .isFailure)
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "'unmatchedQuote"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\"unmatchedDouble"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(context = null, condition = "/status == 'available'"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "10 != 'ten'"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(CriterionObject(condition = "((true))"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.queryJsonPath(
                OpenApiArray(
                    listOf(
                        OpenApiObject(mapOf("other" to OpenApiInt(1L))),
                        OpenApiObject(mapOf("k" to OpenApiInt(1L))))),
                "\$[?(@.k == 1)]")
            .isSuccess)
    assertEquals(
        OpenApiString("5"),
        ArazzoCriterionEvaluator.evaluateSelector(
                SelectorObject(
                    context = "\$response.body#/items/0/cost",
                    selector = "5",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.REGEX)),
                ctx2)
            .getOrThrow())
    assertEquals(
        OpenApiString("19.99"),
        ArazzoCriterionEvaluator.evaluateSelector(
                SelectorObject(
                    context = "\$response.body#/price",
                    selector = "19\\.99",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.REGEX)),
                ctx2)
            .getOrThrow())
    ctx2.workflowOutputs["zeroInt"] = OpenApiInt(0L)
    ctx2.workflowOutputs["zeroNum"] = OpenApiNumber(0.0)
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.zeroInt"), ctx2)
            .getOrThrow())
    assertFalse(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(condition = "\$outputs.zeroNum"), ctx2)
            .getOrThrow())
    assertTrue(
        ArazzoCriterionEvaluator.evaluateCriterion(
                CriterionObject(
                    context = "\$outputs.zeroInt",
                    condition = "//a",
                    type = CriterionTypeOrExpression.Engine(CriterionEngineType.XPATH)),
                ctx2)
            .isSuccess)
  }
}
