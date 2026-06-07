package openapi

import java.io.File
import org.junit.jupiter.api.Test

class ExhaustiveRealWorldTest {
  @Test
  fun parseStripe() {
    val file = File("stripe.json")
    if (!file.exists()) return // Skip test if file is not present

    val content = file.readText()
    val parser = OpenApiParser()
    try {
      val doc = parser.parseDocumentString(content)
      if (doc is OpenApiDocument.OpenApi) {
        val validator = OpenApiValidator()
        validator.validate(doc.definition)
      }
    } catch (e: Exception) {
      e.printStackTrace()
      throw e
    }
  }
}
