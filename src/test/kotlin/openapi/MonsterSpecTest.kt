/** Unit and integration tests in [MonsterSpecTest]. */
package openapi

import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MonsterSpecTest {
  @Test
  fun testParseMonster() {
    val file = File("src/test/resources/monster.json")
    val content = file.readText()
    val parser = OpenApiParser()
    val doc = parser.parseDocumentString(content)
    assertTrue(doc is OpenApiDocument.OpenApi)
    val definition = (doc as OpenApiDocument.OpenApi).definition
    val validator = OpenApiValidator()
    val issues = validator.validate(definition)
    assertTrue(issues.isNotEmpty())
    assertTrue(issues.any { it.path == "$.openapi" && it.severity == OpenApiIssueSeverity.WARNING })
    assertTrue(
        issues.any {
          it.path == "$.components.schemas.Monster.discriminator" &&
              it.severity == OpenApiIssueSeverity.ERROR
        })

    val writer = OpenApiWriter()
    val outStr = writer.writeYaml(definition)
    assertTrue(outStr.contains("Monster"))

    val reparsed = parser.parseString(outStr, OpenApiParser.Format.YAML)
    assertEquals(definition.info, reparsed.info)
    assertEquals(definition.servers, reparsed.servers)
    assertEquals(definition.paths.keys, reparsed.paths.keys)
    assertEquals(definition.components?.schemas?.keys, reparsed.components?.schemas?.keys)
    assertEquals(
        definition.components?.schemas?.get("Monster")?.properties,
        reparsed.components?.schemas?.get("Monster")?.properties)
  }
}
