/** Unit and integration tests in [JsonNodeNavigationTest]. */
package openapi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Focused unit tests for [JsonNode] AST navigation extensions and RFC 8259 strict JSON parsing in
 * `src/commonMain/kotlin/openapi/JsonNodeExt.kt`.
 */
class JsonNodeNavigationTest {

  @Test
  fun `JsonNode type predicates and coercion helpers handle all node kinds`() {
    val arrNode = Json.parseToJsonElement("[10, 20]")
    val objNode =
        Json.parseToJsonElement("""{"a": 1, "b": true, "s": "txt", "n": null, "d": 1.5}""")
    val strNode = JsonPrimitive("hello")
    val boolNode = JsonPrimitive(true)
    val numNode = JsonPrimitive(42)
    val nullNode = JsonNull

    assertTrue(objNode.isObject)
    assertFalse(arrNode.isObject)
    assertTrue(arrNode.isArray)
    assertFalse(objNode.isArray)
    assertTrue(strNode.isTextual)
    assertFalse(numNode.isTextual)
    assertFalse(arrNode.isTextual)
    assertTrue(boolNode.isBoolean)
    assertFalse(strNode.isBoolean)
    assertFalse(numNode.isBoolean)
    assertFalse(arrNode.isBoolean)
    assertTrue(numNode.isNumber)
    assertFalse(strNode.isNumber)
    assertFalse(boolNode.isNumber)
    assertFalse(arrNode.isNumber)
    assertTrue(nullNode.isNullNode)
    assertFalse(strNode.isNullNode)

    assertEquals("hello", strNode.asText())
    assertEquals("fb", nullNode.asText("fb"))
    assertEquals("fb", arrNode.asText("fb"))
    assertEquals("hello", strNode.textValue())

    assertEquals(42, numNode.asInt())
    assertEquals(9, strNode.asInt(9))
    assertEquals(9, arrNode.asInt(9))
    assertEquals(0, strNode.intValue())

    assertEquals(42.0, numNode.asDouble())
    assertEquals(9.5, strNode.asDouble(9.5))
    assertEquals(9.5, arrNode.asDouble(9.5))
    assertEquals(42.0, numNode.doubleValue())
    assertEquals(0.0, strNode.doubleValue())

    assertEquals(true, boolNode.asBoolean())
    assertEquals(true, strNode.asBoolean(true))
    assertEquals(true, arrNode.asBoolean(true))
    assertEquals(false, strNode.booleanValue())

    assertEquals(10, arrNode.get(0)?.intValue())
    assertNull(arrNode.get(5))
    assertNull(arrNode.get(-1))
    assertNull(objNode.get(0))
    assertNull(arrNode.get("a"))
    assertTrue(objNode.has("a"))
    assertFalse(objNode.has("missing"))
    assertFalse(arrNode.has("a"))
    assertEquals(5, objNode.fields().size)
    assertTrue(arrNode.fields().isEmpty())
    assertEquals(2, arrNode.elements().size)
    assertTrue(objNode.elements().isEmpty())
    assertEquals(5, objNode.size())
    assertEquals(2, arrNode.size())
    assertEquals(0, strNode.size())

    assertEquals(objNode, objNode.asObject())
    assertNull(arrNode.asObject())
    assertNull((null as JsonNode?).asObject())
    assertEquals(2, arrNode.asArray()?.size)
    assertNull(objNode.asArray())
    assertNull((null as JsonNode?).asArray())

    assertEquals("txt", objNode.text("s"))
    assertNull(objNode.text("n"))
    assertNull(objNode.text("missing"))
    assertEquals(true, objNode.boolean("b"))
    assertNull(objNode.boolean("s"))
    assertNull(objNode.boolean("missing"))
    assertEquals(1, objNode.int("a"))
    assertNull(objNode.int("s"))
    assertNull(objNode.int("missing"))
    assertEquals(1.5, objNode.double("d"))
    assertNull(objNode.double("s"))
    assertNull(objNode.double("missing"))
  }

  @Test
  fun `parseJsonElementResult validates RFC 8259 syntax escapes and numbers`() {
    val bs = 92.toChar()
    val q = '"'
    val escJson = buildString {
      append('{').append(q).append("esc").append(q).append(':').append(q)
      append(bs).append(q)
      append(bs).append(bs)
      append(bs).append('/')
      append(bs).append('b')
      append(bs).append('f')
      append(bs).append('n')
      append(bs).append('r')
      append(bs).append('t')
      append(bs).append("u0041")
      append(bs).append("u00af")
      append(bs).append("u00AF")
      append(q).append('}')
    }
    val validJsonSamples =
        listOf(
            escJson,
            """[true, false, null, 0, -42, 3.14, -0.5, 1e5, 2E+3, 4e-2, 1.5e10, 999999999999999999999999999999]""",
            """{}""",
            """[]""")
    for (sample in validJsonSamples) {
      assertTrue(parseJsonElementResult(sample).isSuccess, "Expected valid: $sample")
    }
    val invalidJsonSamples =
        listOf(
            "",
            "   ",
            "true false",
            "@",
            "tru",
            "fals",
            "nul",
            "{",
            """{"a"""",
            """{"a" """,
            """{"a" 1}""",
            """{"a":""",
            """{"a":1""",
            """{"a":1;""",
            """{1:2}""",
            """{"${bs}x":1}""",
            "[",
            "[1",
            "[1;",
            "[#",
            """"unterminated""",
            "$q$bs",
            "$q${bs}x$q",
            "$q${bs}u12$q",
            "$q${bs}u123g$q",
            "$q${bs}u123$q",
            "-",
            "-a",
            "1.",
            "1.a",
            "1e",
            "1e+",
            "1e-",
            "1ea")
    for (sample in invalidJsonSamples) {
      assertTrue(parseJsonElementResult(sample).isFailure, "Expected invalid: $sample")
    }
  }
}
