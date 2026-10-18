/** Unit and integration tests in [UriUtilsTest]. */
package openapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [isAbsoluteUri], [resolveUri], [percentDecode], [unescapeJsonPointer], and
 * [hexToInt].
 */
class UriUtilsTest {

  @Test
  fun testIsAbsoluteUri() {
    assertTrue(isAbsoluteUri("http://example.com"))
    assertTrue(isAbsoluteUri("https://example.com"))
    assertTrue(isAbsoluteUri("file:///path/to/file"))
    assertTrue(isAbsoluteUri("urn:isbn:0451450523"))
    assertFalse(isAbsoluteUri(""))
    assertFalse(isAbsoluteUri("   "))
    assertFalse(isAbsoluteUri("http://example.com/with space"))
    assertFalse(isAbsoluteUri("#fragmentOnly"))
    assertFalse(isAbsoluteUri("/relative/path"))
    assertFalse(isAbsoluteUri("relative/path"))
  }

  @Test
  fun testResolveUri() {
    assertEquals(
        "http://example.com/path", resolveUri("http://example.com/base", "http://example.com/path"))
    assertEquals("relative", resolveUri("", "relative"))
    assertEquals("http://example.com/base#frag", resolveUri("http://example.com/base#old", "#frag"))

    assertEquals("http://example.com/root", resolveUri("http://example.com/base/sub", "/root"))
    assertEquals("http://example.com/root", resolveUri("http://example.com", "/root"))
    assertEquals("/root/a", resolveUri("/local/base/file.json", "/root/./a"))

    assertEquals(
        "http://example.com/base/relative", resolveUri("http://example.com/base/", "relative"))
    assertEquals("http://example.com/relative", resolveUri("http://example.com/base", "relative"))
    assertEquals("http://example.com/relative", resolveUri("http://example.com", "relative"))
    assertEquals(
        "http://example.com/a/c/#sec", resolveUri("http://example.com/a/b/file.json", "../c/#sec"))
    assertEquals("dir/sub/file.json", resolveUri("dir/base.json", "./sub/file.json"))
    assertEquals("sub/file.json", resolveUri("base.json", "a/../sub/file.json"))
    assertEquals("../outside.json", resolveUri("base.json", "../outside.json"))
    assertEquals("dir/sub/", resolveUri("dir/base.json", "sub/"))
    assertEquals("http://example.com/", resolveUri("http://example.com/a/", ".."))
    assertEquals("", resolveUri("base.json", "."))
  }

  @Test
  fun testPointerAndPercentDecode() {
    assertEquals("plain", percentDecode("plain"))
    assertEquals("a/b~c", decodeJsonPointerToken("a~1b~0c"))
    assertEquals("hello world", percentDecode("hello%20world"))
    assertEquals("hexAFaf09", percentDecode("hex%41%46%61%66%30%39"))
    assertEquals("bad%ZZ%1G%1", percentDecode("bad%ZZ%1G%1"))
    assertEquals(0, hexToInt('0'))
    assertEquals(9, hexToInt('9'))
    assertEquals(10, hexToInt('a'))
    assertEquals(15, hexToInt('f'))
    assertEquals(10, hexToInt('A'))
    assertEquals(15, hexToInt('F'))
    assertEquals(-1, hexToInt('G'))
    assertEquals(-1, hexToInt('/'))
    assertEquals(-1, hexToInt(':'))
    assertEquals(-1, hexToInt('@'))
    assertEquals(-1, hexToInt('`'))
    assertEquals(-1, hexToInt('g'))
  }

  @Test
  fun testParseUriSyntax() {
    val httpsUri = parseUriSyntax("https://[::1]:8080/api?x=1#frag")
    assertEquals(ParsedUri(scheme = "https", isAbsolute = true), httpsUri)
    assertEquals("https", httpsUri?.component1())
    assertEquals(true, httpsUri?.component2())
    assertEquals(httpsUri, httpsUri?.copy(scheme = "https", isAbsolute = true))

    val relUri = parseUriSyntax("/relative/path?x=1#frag")
    assertEquals(ParsedUri(scheme = null, isAbsolute = false), relUri)

    val relAuthority = parseUriSyntax("//user@[::1]/path")
    assertEquals(ParsedUri(scheme = null, isAbsolute = false), relAuthority)

    // Invalid URIs
    for (bad in
        listOf(
            "http://with space",
            "http://\u0001",
            "http://\u007F",
            "http://<bad>",
            "http://\"bad\"",
            "http://{bad}",
            "http://|bad|",
            "http://\\bad",
            "http://^bad",
            "http://`bad`",
            "http://example.com/%2",
            "http://example.com/%ZZ",
            "http://example.com/%2G",
            "http://example.com/a#b#c",
            "ht!tp://example.com",
            "://example.com",
            "http:",
            "http://example.com/path#[bad]",
            "http://example.com/path#bad]",
            "http://example.com/path?[bad]",
            "http://example.com/path?bad]",
            "relative/[bad]",
            "relative/bad]",
            "http://example.com/[bad]",
            "http://example.com/bad]",
            "http://[user]@example.com/path",
            "http://user]@example.com/path",
            "http://bad]/path",
            "http://[bad/path",
            "http://[]/path",
            "http://[a]/path",
            "http://[::1][::1]/path",
            "http://[[::1]/path")) {
      assertEquals(null, parseUriSyntax(bad), "Expected null for $bad")
    }
  }

  @Test
  fun testYamlToJsonElementConverter() {
    val converter = openapi.parser.YamlToJsonElementConverter
    assertEquals(kotlinx.serialization.json.JsonNull, converter.parseYaml(""))
    assertEquals(
        kotlinx.serialization.json.JsonNull, converter.parseYaml("   \n  # comment only\n---"))
    assertEquals(
        kotlinx.serialization.json.JsonPrimitive("top-scalar"), converter.parseYaml("top-scalar"))
    assertEquals(
        kotlinx.serialization.json.JsonObject(
            mapOf("a" to kotlinx.serialization.json.JsonPrimitive(1L))),
        converter.parseYaml("{a: 1}"))
    assertEquals(
        kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(1L))),
        converter.parseYaml("[1]"))

    val complexYaml =
        """
        ---
        # comment
        openapi: "3.2.0"
        'single:key': 'single''val'
        "escaped\"key:1": "escaped\"quote" # trailing comment
        flag: true
        disabled: false
        nothing: null
        tilde: ~
        emptyKey:
        count: 42
        ratio: 3.14
        emptyObj: {}
        emptyArr: []
        flowObj: {a: 1, 'b:c': 'two''s', "d\"e:f": "v\"1", nested: [1, 2], bare}
        flowArr: [true, false, "str,ing", 's,q', "esc\"x"]
        seq:
          -
            nestedKey: val
          -
          - inlineKey: 123
            secondKey: "val"
          - plainItem
        literalBlock: |
          line1
            line2
        foldedBlock: >-
          part1
          part2
        ...
        """
            .trimIndent()
    val doc = converter.parseYaml(complexYaml)
    assertTrue(doc is kotlinx.serialization.json.JsonObject)

    val parsed =
        converter.parseYaml(
            """
            openapi: "3.2.0"
            "true": "quotedKey"
            "123": "numericKey"
            flag: true
            disabled: false
            nothing: null
            count: 42
            ratio: 3.14
            emptyObj: {}
            emptyArr: []
            seq:
              -
                k: v
              -
                - innerScalar
              - scalar
            """
                .trimIndent())
    val yamlOut = converter.toYaml(parsed)
    assertTrue(yamlOut.contains("openapi: \"3.2.0\""))
    assertTrue(yamlOut.contains("\"true\": \"quotedKey\""))
    assertTrue(yamlOut.contains("\"123\": \"numericKey\""))
    val reparsed = converter.parseYaml(yamlOut)
    assertEquals(parsed, reparsed)
  }
}
