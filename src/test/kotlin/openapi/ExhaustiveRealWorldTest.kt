/** Unit and integration tests in [ExhaustiveRealWorldTest]. */
package openapi

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExhaustiveRealWorldTest {
  @Test
  fun parseStripe() {
    val stream = this::class.java.classLoader.getResourceAsStream("stripe.json")
    if (stream == null) return // Skip test if file is not present

    val content = stream.bufferedReader().use { it.readText() }
    val parser = OpenApiParser()
    val docResult = parser.parseDocumentStringResult(content)
    assertTrue(docResult.isSuccess)
    val doc = docResult.getOrThrow()
    if (doc is OpenApiDocument.OpenApi) {
      val validator = OpenApiValidator()
      validator.validate(doc.definition)
    }
  }
}
