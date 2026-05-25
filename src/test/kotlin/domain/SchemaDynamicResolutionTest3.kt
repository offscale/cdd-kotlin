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
  }
}
