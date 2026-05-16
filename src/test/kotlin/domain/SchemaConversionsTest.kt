package domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SchemaConversionsTest {

  @Test
  fun testToSchemaProperty() {
    val def =
        SchemaDefinition(
            name = "TestSchema",
            type = "string",
            description = "A test schema",
            maxLength = 10,
            types = setOf("string"))
    val prop = def.toSchemaProperty()
    assertEquals(setOf("string"), prop.types)
    assertEquals("A test schema", prop.description)
    assertEquals(10, prop.maxLength)
  }
}
