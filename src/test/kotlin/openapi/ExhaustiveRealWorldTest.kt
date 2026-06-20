package openapi

import org.junit.jupiter.api.Test

class ExhaustiveRealWorldTest {
  @Test
  fun parseStripe() {
    val stream = this::class.java.classLoader.getResourceAsStream("stripe.json")
    if (stream == null) return // Skip test if file is not present

    val content = stream.bufferedReader().use { it.readText() }
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
