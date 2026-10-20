/** Unit and integration tests in [YamlConverterEdgeCasesTest]. */
package openapi

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import openapi.parser.YamlToJsonElementConverter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Focused unit tests for [YamlToJsonElementConverter] covering YAML 1.2 block scalars, flow
 * collections, quoting, comments, and round-trip serialization.
 */
class YamlConverterEdgeCasesTest {

  @Test
  fun `parseYaml handles quoted keys block scalars flow collections and round-trips`() {
    val nonStringQuotedYaml = YamlToJsonElementConverter.parseYaml("""  "[1]": val""")
    assertTrue(nonStringQuotedYaml is JsonObject)
    assertEquals("val", (nonStringQuotedYaml as JsonObject)["[1]"]?.textValue())

    val yamlDoc =
        """
        # Document comment
        title: 'Single ''quoted'' string'
        doubleQuoted: "Line 1"
        literalBlock: |
          alpha
          beta
        foldedBlock: >
          hello
          world
        flowList: [1, true, "three", null]
        flowMap: {k1: v1, k2: 2}
        nested:
          items:
            - name: item1
              active: true
            - name: item2
              active: false
        """
            .trimIndent()

    val parsed = YamlToJsonElementConverter.parseYaml(yamlDoc) as JsonObject
    assertEquals("Single 'quoted' string", parsed["title"]?.textValue())
    assertEquals("Line 1", parsed["doubleQuoted"]?.textValue())
    assertTrue(parsed["literalBlock"]?.textValue()?.contains("alpha") == true)
    assertTrue(parsed["foldedBlock"]?.textValue()?.contains("hello") == true)

    val flowList = parsed["flowList"] as JsonArray
    assertEquals(4, flowList.size)
    assertEquals(1, flowList[0].intValue())
    assertEquals(true, flowList[1].booleanValue())
    assertEquals("three", flowList[2].textValue())
    assertEquals(JsonNull, flowList[3])

    val serialized = YamlToJsonElementConverter.toYaml(parsed)
    val reparsed = YamlToJsonElementConverter.parseYaml(serialized) as JsonObject
    assertEquals(parsed["title"], reparsed["title"])
    assertEquals(parsed["flowList"], reparsed["flowList"])
    assertEquals(parsed["nested"], reparsed["nested"])
  }

  @Test
  fun `toYaml serializes primitives arrays and objects deterministically`() {
    assertEquals("null", YamlToJsonElementConverter.toYaml(JsonNull).trim())
    assertEquals("true", YamlToJsonElementConverter.toYaml(JsonPrimitive(true)).trim())
    assertEquals("42", YamlToJsonElementConverter.toYaml(JsonPrimitive(42)).trim())

    assertTrue(YamlToJsonElementConverter.parseYamlResult("").isSuccess)
    assertTrue(YamlToJsonElementConverter.parseYamlResult("key: !!str 123").isSuccess)
    assertTrue(YamlToJsonElementConverter.parseYamlResult("key: !!null").isSuccess)
    assertTrue(YamlToJsonElementConverter.parseYamlResult("keepLit: |+\n  line1").isSuccess)
    assertTrue(YamlToJsonElementConverter.parseYamlResult("keepFold: >+\n  line1").isSuccess)
    assertTrue(YamlToJsonElementConverter.parseYamlResult("{unclosed").isFailure)
    assertTrue(YamlToJsonElementConverter.parseYamlResult("[unclosed").isFailure)
    assertTrue(YamlToJsonElementConverter.parseYamlResult("{closed: 1}").isSuccess)
    assertTrue(YamlToJsonElementConverter.parseYamlResult("[1, 2]").isSuccess)
  }
}
