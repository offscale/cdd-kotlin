package domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SchemaDynamicResolutionTest2 {

  @Test
  fun testPercentDecodeAllRanges() {
    val schema =
        SchemaProperty(schemaId = "root", dynamicAnchor = "test%30%61%41%7A%5A%39%66%46%00")
    val scope = buildDynamicAnchorScope(schema)
    assertNotNull(scope)
  }
}
