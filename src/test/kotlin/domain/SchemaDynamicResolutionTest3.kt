/** Unit and integration tests in [SchemaDynamicResolutionTest3]. */
package domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SchemaDynamicResolutionTest3 {

  @Test
  fun testPercentDecodeEdgesHex() {
    val schema =
        SchemaProperty(
            schemaId = "root",
            properties =
                mapOf(
                    "p0" to SchemaProperty(dynamicAnchor = "0%00%09%aA%fF%Aa%Ff"),
                    "p1" to SchemaProperty(dynamicAnchor = "1%1X%X1%2%1"),
                    "p2" to SchemaProperty(dynamicAnchor = "2%")))
    val scope = buildDynamicAnchorScope(schema)
    assertNotNull(scope)

    val k1 = IdentityKey(schema)
    val k2 = IdentityKey(schema)
    val k3 = IdentityKey(SchemaProperty(schemaId = "root"))
    assertTrue(k1 == k2)
    assertFalse(k1 == k3)
    assertFalse(k1.equals("notAnIdentityKey"))
    assertFalse(k1.equals(null))
    assertEquals(schema.hashCode(), k1.hashCode())

    // Test resourceCache hit when the same SchemaProperty instance with a schemaId is referenced
    // twice
    val sharedResource =
        SchemaProperty(schemaId = "https://example.com/shared", dynamicAnchor = "a")
    val parentWithShared =
        SchemaProperty(
            schemaId = "https://example.com/root",
            properties = mapOf("first" to sharedResource, "second" to sharedResource))
    val cachedScope = buildDynamicAnchorScope(parentWithShared)
    assertEquals(sharedResource, cachedScope.resolveDynamicRef(sharedResource, "#a"))
  }
}
