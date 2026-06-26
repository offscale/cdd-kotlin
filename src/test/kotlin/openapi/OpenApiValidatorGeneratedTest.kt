package openapi

import domain.*
import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OpenApiValidatorGeneratedTest {
  @Test
  fun testGeneratedInvalid() {
    val json = File("src/test/resources/invalid_generated.json").readText()
    val doc = OpenApiParser().parseDocumentString(json) as OpenApiDocument.OpenApi
    val issues = OpenApiValidator().validate(doc.definition)
    assertTrue(issues.isNotEmpty())
  }
}
