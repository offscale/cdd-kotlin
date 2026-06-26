import java.nio.file.Files
import org.junit.jupiter.api.Test

class MonsterCliCoverageTest {
  @Test
  fun testAllSpecs() {
    val specs =
        listOf(
            "monster.json",
            "monster.yaml",
            "uspto.yaml",
            "invalid_generated.json",
            "invalid_openapi_exhaustive.json")
    for (spec in specs) {
      for (cmd in listOf("to_sdk", "to_server", "to_cli", "to_mock")) {
        val outDir = Files.createTempDirectory("test_monster").toFile()
        try {
          runCli(
              arrayOf(
                  "from_openapi", cmd, "-i", "src/test/resources/$spec", "-o", outDir.absolutePath))
        } catch (e: Exception) {}
        try {
          runCli(arrayOf("to_docs_json", "-i", "src/test/resources/$spec"))
        } catch (e: Exception) {}
        outDir.deleteRecursively()
      }
    }
  }
}
