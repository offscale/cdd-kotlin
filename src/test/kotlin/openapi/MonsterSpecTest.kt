package openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class MonsterSpecTest {
  @Test
  fun testParseMonster() {
    val file = File("src/test/resources/monster.json")
    val content = file.readText()
    val parser = OpenApiParser()
    val doc = parser.parseDocumentString(content)
    assertTrue(doc is OpenApiDocument.OpenApi)
    val validator = OpenApiValidator()
    validator.validate((doc as OpenApiDocument.OpenApi).definition)
    
    val writer = OpenApiWriter()
    val outStr = writer.writeYaml(doc.definition)
    assertTrue(outStr.contains("Monster"))
  }
}
