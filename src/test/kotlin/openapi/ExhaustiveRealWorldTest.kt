/** Unit and integration tests in [ExhaustiveRealWorldTest]. */
package openapi

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExhaustiveRealWorldTest {
  @Test
  fun parseStripe() {
    val file = File("src/test/resources/stripe.json")
    assertTrue(file.exists(), "stripe.json test resource must exist")

    val content = file.readText()
    val parser = OpenApiParser()
    val docResult = parser.parseDocumentStringResult(content)
    assertTrue(docResult.isSuccess)
    val doc = docResult.getOrThrow()
    assertTrue(doc is OpenApiDocument.OpenApi)
    val openApiDoc = doc as OpenApiDocument.OpenApi
    assertEquals("Stripe API", openApiDoc.definition.info.title)
    assertTrue(openApiDoc.definition.paths.size > 100)
    assertTrue(openApiDoc.definition.components?.schemas.orEmpty().size > 100)

    val validator = OpenApiValidator()
    val issues = validator.validate(openApiDoc.definition)
    assertNotNull(issues)
    assertTrue(issues.all { it.path.startsWith("$") && it.message.isNotBlank() })
  }

  @Test
  fun parseMonsterYamlAndCorruptedUsptoResource() {
    val monsterFile = File("src/test/resources/monster.yaml")
    assertTrue(monsterFile.exists(), "monster.yaml test resource must exist")
    val monsterContent = monsterFile.readText()
    val parser = OpenApiParser()
    val monsterDoc = parser.parseDocumentStringResult(monsterContent).getOrThrow()
    assertTrue(monsterDoc is OpenApiDocument.OpenApi)
    val monsterDef = (monsterDoc as OpenApiDocument.OpenApi).definition
    assertEquals("Monster", monsterDef.info.title)
    assertTrue(monsterDef.paths.containsKey("/test/{id}"))
    assertTrue(monsterDef.components?.schemas.orEmpty().containsKey("Monster"))

    val usptoFile = File("src/test/resources/uspto.yaml")
    assertTrue(usptoFile.exists(), "uspto.yaml test resource must exist")
    val usptoContent = usptoFile.readText()
    val usptoResult = parser.parseStringResult(usptoContent, OpenApiParser.Format.JSON)
    assertTrue(usptoResult.isFailure)
  }
}
