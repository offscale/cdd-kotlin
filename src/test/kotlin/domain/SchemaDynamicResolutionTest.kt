package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SchemaDynamicResolutionTest {

  @Test
  fun resolveDynamicRef_invalid_ref() {
    val root = SchemaProperty(schemaId = "root", dynamicAnchor = "rootAnchor")
    val scope = buildDynamicAnchorScope(root)
    val result = scope.resolveDynamicRef(root, "#/path")
    assertNull(result)
  }

  @Test
  fun resolveDynamicRef_empty_ref() {
    val root = SchemaProperty(schemaId = "root", dynamicAnchor = "rootAnchor")
    val scope = buildDynamicAnchorScope(root)
    val result = scope.resolveDynamicRef(root, "#")
    assertNull(result)
  }

  @Test
  fun extractDynamicAnchorName_handles_percent_decode() {
    assertEquals("foo bar", extractDynamicAnchorName("#foo%20bar"))
  }

  @Test
  fun extractDynamicAnchorName_percent_decode_invalid() {
    assertEquals("foo%2Z", extractDynamicAnchorName("#foo%2Z"))
  }
}
