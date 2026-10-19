/** Unit and integration tests in [JsonValueAndErrorTest]. */
package domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit tests for [OpenApiValue], [ExtensionKey], [LinkRequestBody], and [CddError]. */
class JsonValueAndErrorTest {

  @Test
  fun `ExtensionKey validates x- prefix and formats string`() {
    val key = ExtensionKey.parse("x-custom-ext").getOrThrow()
    assertEquals("x-custom-ext", key.value)
    assertEquals("x-custom-ext", key.toString())

    assertTrue(ExtensionKey.parse("invalid-key").isFailure)
  }

  @Test
  fun `OpenApiValue conversions between JsonElement and untyped`() {
    assertEquals(JsonNull, OpenApiNull.toJsonElement())
    assertNull(OpenApiNull.toUntyped())

    val boolVal = OpenApiBoolean(true)
    assertEquals(JsonPrimitive(true), boolVal.toJsonElement())
    assertEquals(true, boolVal.toUntyped())

    val intVal = OpenApiInt(42L)
    assertEquals(JsonPrimitive(42L), intVal.toJsonElement())
    assertEquals(42, intVal.toUntyped())

    val bigIntVal = OpenApiInt(Long.MAX_VALUE)
    assertEquals(Long.MAX_VALUE, bigIntVal.toUntyped())

    val smallIntVal = OpenApiInt(Long.MIN_VALUE)
    assertEquals(Long.MIN_VALUE, smallIntVal.toUntyped())

    val numVal = OpenApiNumber(3.14)
    assertEquals(JsonPrimitive(3.14), numVal.toJsonElement())
    assertEquals(3.14, numVal.toUntyped())
    assertEquals("3.14", numVal.raw)

    val strVal = OpenApiString("hello")
    assertEquals(JsonPrimitive("hello"), strVal.toJsonElement())
    assertEquals("hello", strVal.toUntyped())

    val arrVal = OpenApiArray(listOf(strVal, intVal))
    assertEquals(
        JsonArray(listOf(JsonPrimitive("hello"), JsonPrimitive(42L))), arrVal.toJsonElement())
    assertEquals(listOf("hello", 42), arrVal.toUntyped())

    val objVal = OpenApiObject(mapOf("k" to boolVal))
    assertEquals(JsonObject(mapOf("k" to JsonPrimitive(true))), objVal.toJsonElement())
    assertEquals(mapOf("k" to true), objVal.toUntyped())
  }

  @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
  @Test
  fun `OpenApiValues fromJsonElement handles all JsonElement variants`() {
    assertEquals(OpenApiNull, OpenApiValues.fromJsonElement(JsonNull))
    assertEquals(OpenApiString("true"), OpenApiValues.fromJsonElement(JsonPrimitive("true")))
    assertEquals(OpenApiBoolean(false), OpenApiValues.fromJsonElement(JsonPrimitive(false)))
    assertEquals(OpenApiInt(100L), OpenApiValues.fromJsonElement(JsonPrimitive(100)))
    assertEquals(OpenApiNumber(1.5, "1.5"), OpenApiValues.fromJsonElement(JsonPrimitive(1.5)))
    assertEquals(
        OpenApiNumber(100.0, "1e2"),
        OpenApiValues.fromJsonElement(kotlinx.serialization.json.JsonUnquotedLiteral("1e2")))
    assertEquals(
        OpenApiString("unquoted_symbol"),
        OpenApiValues.fromJsonElement(
            kotlinx.serialization.json.JsonUnquotedLiteral("unquoted_symbol")))
    assertEquals(
        OpenApiArray(listOf(OpenApiInt(1L))),
        OpenApiValues.fromJsonElement(JsonArray(listOf(JsonPrimitive(1)))))
    assertEquals(
        OpenApiObject(mapOf("a" to OpenApiString("b"))),
        OpenApiValues.fromJsonElement(JsonObject(mapOf("a" to JsonPrimitive("b")))))
  }

  @Test
  fun `OpenApiValues fromAny handles all Kotlin types`() {
    assertEquals(OpenApiNull, OpenApiValues.fromAny(null))
    val existing = OpenApiString("existing")
    assertSame(existing, OpenApiValues.fromAny(existing))
    assertEquals(OpenApiBoolean(true), OpenApiValues.fromAny(JsonPrimitive(true)))
    assertEquals(OpenApiBoolean(false), OpenApiValues.fromAny(false))
    assertEquals(OpenApiInt(7L), OpenApiValues.fromAny(7))
    assertEquals(OpenApiInt(8L), OpenApiValues.fromAny(8L))
    assertEquals(OpenApiInt(9L), OpenApiValues.fromAny(9.toShort()))
    assertEquals(OpenApiInt(10L), OpenApiValues.fromAny(10.toByte()))
    assertEquals(OpenApiNumber(2.5, "2.5"), OpenApiValues.fromAny(2.5f))
    assertEquals(OpenApiNumber(3.5, "3.5"), OpenApiValues.fromAny(3.5))
    assertEquals(OpenApiNumber(42.0, "42"), OpenApiValues.fromAny(java.math.BigDecimal("42")))
    assertEquals(OpenApiString("str"), OpenApiValues.fromAny("str"))
    assertEquals(OpenApiArray(listOf(OpenApiInt(1L))), OpenApiValues.fromAny(listOf(1)))
    assertEquals(OpenApiArray(listOf(OpenApiString("x"))), OpenApiValues.fromAny(arrayOf("x")))
    assertEquals(
        OpenApiObject(mapOf("key" to OpenApiInt(2L))), OpenApiValues.fromAny(mapOf("key" to 2)))
    val customObj =
        object {
          override fun toString(): String = "custom"
        }
    assertEquals(OpenApiString("custom"), OpenApiValues.fromAny(customObj))

    val rawMap = mapOf("x-flag" to true, "x-count" to 5)
    val typedMap = OpenApiValues.fromMap(rawMap)
    assertEquals(mapOf("x-flag" to OpenApiBoolean(true), "x-count" to OpenApiInt(5L)), typedMap)
    assertEquals(rawMap, OpenApiValues.toUntypedMap(typedMap))
    assertEquals("{\"x-flag\":true,\"x-count\":5}", OpenApiValues.encodeAnyToString(rawMap))
    assertEquals(rawMap, OpenApiValues.decodeStringToAny("{\"x-flag\":true,\"x-count\":5}"))
  }

  @Test
  fun `typed domain model accessors work accurately`() {
    val ex =
        ExampleObject(
            dataValue = OpenApiObject(mapOf("id" to OpenApiInt(1L))),
            value = OpenApiString("sample"),
            extensions = mapOf("x-ex" to OpenApiBoolean(true)))
    assertEquals(OpenApiObject(mapOf("id" to OpenApiInt(1L))), ex.dataValue)
    assertEquals(OpenApiString("sample"), ex.value)
    assertEquals(mapOf("x-ex" to OpenApiBoolean(true)), ex.extensions)
    assertNull(ExampleObject().dataValue)
    assertNull(ExampleObject().value)

    val linkExpr =
        Link(
            requestBody = OpenApiString("\$request.body#/id"),
            parameters = mapOf("id" to OpenApiString("\$response.body#/id")),
            extensions = mapOf("x-l" to OpenApiInt(1L)))
    assertEquals(LinkRequestBody.Expression("\$request.body#/id"), linkExpr.typedRequestBody)
    assertEquals(mapOf("id" to OpenApiString("\$response.body#/id")), linkExpr.parameters)
    assertEquals(mapOf("x-l" to OpenApiInt(1L)), linkExpr.extensions)

    val linkEmbeddedExpr = Link(requestBody = OpenApiString("prefix{\$request.query.x}"))
    assertEquals(
        LinkRequestBody.Expression("prefix{\$request.query.x}"), linkEmbeddedExpr.typedRequestBody)

    val linkStrLiteral = Link(requestBody = OpenApiString("plain-text"))
    assertEquals(
        LinkRequestBody.Literal(OpenApiString("plain-text")), linkStrLiteral.typedRequestBody)

    val linkObjLiteral = Link(requestBody = OpenApiInt(99L))
    assertEquals(LinkRequestBody.Literal(OpenApiInt(99L)), linkObjLiteral.typedRequestBody)
    assertNull(Link().typedRequestBody)

    assertEquals("hello", OpenApiString("hello").unwrapOrNull())
    assertNull((null as OpenApiValue?).unwrapOrNull())
    assertEquals("hello", OpenApiString("hello").stringOrNull())
    assertNull(OpenApiInt(1L).stringOrNull())
    assertEquals(true, OpenApiBoolean(true).booleanOrNull())
    assertNull(OpenApiString("true").booleanOrNull())
    assertEquals(42.0, OpenApiInt(42L).doubleOrNull())
    assertEquals(3.5, OpenApiNumber(3.5).doubleOrNull())
    assertNull(OpenApiString("3.5").doubleOrNull())
    assertEquals(42L, OpenApiInt(42L).longOrNull())
    assertNull(OpenApiNumber(3.5).longOrNull())

    val prop =
        SchemaProperty(
            types = setOf("string", "null", "unknown"),
            defaultValue = OpenApiString("def"),
            constValue = OpenApiString("c"),
            example = OpenApiString("ex"),
            customKeywords = mapOf("x-vocab" to OpenApiInt(1L)),
            extensions = mapOf("x-ext" to OpenApiString("v")),
            xml = Xml(nodeType = "attribute"))
    assertEquals(setOf(SchemaType.STRING, SchemaType.NULL), prop.parsedTypes)
    assertEquals(OpenApiString("def"), prop.defaultValue)
    assertEquals(OpenApiString("c"), prop.constValue)
    assertEquals(OpenApiString("ex"), prop.example)
    assertEquals(mapOf("x-vocab" to OpenApiInt(1L)), prop.customKeywords)
    assertEquals(mapOf("x-ext" to OpenApiString("v")), prop.extensions)
    assertEquals(XmlNodeType.ATTRIBUTE, prop.xml?.parsedNodeType)
    assertNull(Xml(nodeType = "invalid").parsedNodeType)
    assertNull(Xml(nodeType = null).parsedNodeType)
    assertNull(SchemaProperty().defaultValue)
    assertNull(SchemaProperty().constValue)
    assertNull(SchemaProperty().example)

    val sec = SecurityScheme(type = "oauth2", `in` = "header")
    assertEquals(SecuritySchemeType.OAUTH2, sec.parsedType)
    assertEquals(ApiKeyLocation.HEADER, sec.parsedLocation)
    assertNull(SecurityScheme(type = "custom", `in` = "invalid").parsedType)
    assertNull(SecurityScheme(type = "custom", `in` = "invalid").parsedLocation)
    assertNull(SecurityScheme(`in` = null).parsedLocation)

    val convertedDef = prop.toSchemaDefinition("MyProp")
    assertEquals("MyProp", convertedDef.name)
    assertEquals(prop.types, convertedDef.types)
  }

  @Test
  fun `CddError and CddException format messages accurately`() {
    val cliErr = CddError.CliUsageError("Missing -i")
    assertEquals("Missing -i", cliErr.message)

    val ioErr = CddError.FileIoError("/tmp/a.json", "Not found")
    assertEquals("File I/O error at '/tmp/a.json': Not found", ioErr.message)

    val parseErr = CddError.OpenApiParseError("Bad token", "#/paths")
    assertEquals("Bad token", parseErr.message)
    assertEquals("#/paths", parseErr.location)

    val valErr = CddError.OpenApiValidationError(listOf("Missing title", "Missing version"))
    assertEquals(
        "OpenAPI validation failed with 2 issue(s): Missing title; Missing version", valErr.message)

    val psiErr = CddError.PsiParseError("User.kt", "Unclosed brace")
    assertEquals("PSI parse error in 'User.kt': Unclosed brace", psiErr.message)

    val mergeErr = CddError.MergeConflictError("UserDto", "No primary constructor")
    assertEquals("Merge conflict on 'UserDto': No primary constructor", mergeErr.message)

    val platErr = CddError.UnsupportedPlatformOperationError("generateServerCode", "WASM")
    assertEquals("generateServerCode is not supported on WASM", platErr.message)

    val rpcErr = CddError.JsonRpcError(-32600, "Invalid Request", OpenApiString("detail"))
    assertEquals(-32600, rpcErr.code)
    assertEquals("Invalid Request", rpcErr.message)
    assertEquals(OpenApiString("detail"), rpcErr.data)

    val ex = CddException(platErr)
    assertEquals(platErr, ex.error)
    assertEquals("generateServerCode is not supported on WASM", ex.message)

    val cliCodeErr = CddError.CliError("Bad command", 2)
    assertEquals("Bad command", cliCodeErr.message)
    assertEquals(2, cliCodeErr.exitCode)
    assertEquals(1, CddError.CliError("Default exit").exitCode)

    val schemaErr = CddError.SchemaParseError("Not a schema")
    assertEquals("Not a schema", schemaErr.message)

    val jsonErr = CddError.JsonParseError("Invalid JSON")
    assertEquals("Invalid JSON", jsonErr.message)

    val dirErr = CddError.InvalidDirectoryError("/bad/dir", "Not a directory")
    assertEquals("/bad/dir", dirErr.path)
    assertEquals("Not a directory", dirErr.reason)
    assertEquals("Not a directory: /bad/dir", dirErr.message)

    val psiMergeErr = CddError.PsiMergeError("User", "Missing class")
    assertEquals("User", psiMergeErr.targetClass)
    assertEquals("Missing class", psiMergeErr.reason)
    assertEquals("Missing class", psiMergeErr.message)

    val paramErr = CddError.ParameterSerializationError("q", "Invalid style")
    assertEquals("q", paramErr.parameterName)
    assertEquals("Invalid style", paramErr.reason)
    assertEquals("Invalid style", paramErr.message)

    val webhookErr = CddError.WebhookDispatchError("http://cb", "Timeout")
    assertEquals("http://cb", webhookErr.targetUrl)
    assertEquals("Timeout", webhookErr.reason)
    assertEquals("Failed to dispatch webhook to http://cb: Timeout", webhookErr.message)

    val arazzoParseErr = CddError.ArazzoParseError("Bad arazzo", "workflows[0]")
    assertEquals("Bad arazzo", arazzoParseErr.message)
    assertEquals("workflows[0]", arazzoParseErr.location)
    assertNull(CddError.ArazzoParseError("Default loc").location)

    val arazzoValErr = CddError.ArazzoValidationError(listOf("Missing workflows", "Cycle"))
    assertEquals(listOf("Missing workflows", "Cycle"), arazzoValErr.issues)
    assertEquals(
        "Arazzo validation failed with 2 issue(s): Missing workflows; Cycle", arazzoValErr.message)

    val rtExprErr = CddError.RuntimeExpressionError("\$bad", "Unknown root")
    assertEquals("\$bad", rtExprErr.expression)
    assertEquals("Unknown root", rtExprErr.reason)
    assertEquals("Runtime expression error in '\$bad': Unknown root", rtExprErr.message)

    val critErr = CddError.CriterionEvaluationError("a == b", "Failed compare")
    assertEquals("a == b", critErr.condition)
    assertEquals("Failed compare", critErr.reason)
    assertEquals("Criterion evaluation error in 'a == b': Failed compare", critErr.message)

    val wfStepErr = CddError.WorkflowExecutionError("wf1", "step1", "Step failed")
    assertEquals("wf1", wfStepErr.workflowId)
    assertEquals("step1", wfStepErr.stepId)
    assertEquals("Step failed", wfStepErr.reason)
    assertEquals("Workflow 'wf1' failed at step 'step1': Step failed", wfStepErr.message)

    val wfNoStepErr = CddError.WorkflowExecutionError(workflowId = "wf1", reason = "Aborted")
    assertNull(wfNoStepErr.stepId)
    assertEquals("Workflow 'wf1' failed: Aborted", wfNoStepErr.message)

    val failedResult: Result<String> = platErr.toFailure()
    assertTrue(failedResult.isFailure)
    assertEquals(platErr, failedResult.cddErrorOrNull())
    assertNull(Result.success("ok").cddErrorOrNull())

    assertEquals(Result.success(4), Result.success("abcd").mapResult { it.length })
    assertTrue(failedResult.mapResult { it.length }.isFailure)
    assertEquals(
        Result.success("ABCD"),
        Result.success("abcd").flatMapResult { Result.success(it.uppercase()) })
    assertTrue(failedResult.flatMapResult { Result.success(it.uppercase()) }.isFailure)

    val genericThrowable = RuntimeException("boom")
    assertEquals(CddError.CliError("boom"), genericThrowable.toCddError())
    val nullMsgThrowable = RuntimeException()
    assertEquals(CddError.CliError("Unknown error"), nullMsgThrowable.toCddError())

    assertEquals(mapOf("a" to 1), OpenApiValues.decodeStringToAnyResult("""{"a":1}""").getOrThrow())
    assertTrue(OpenApiValues.decodeStringToAnyResult("{bad").isFailure)
    assertNull(OpenApiValues.decodeStringToAny("{bad"))
  }
}
