import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verifies CLI commands across real-world and exhaustive OpenAPI test specifications. */
class MonsterCliCoverageTest {

  @Test
  fun testValidJsonSpecsAcrossCommands() {
    val validJsonSpecs =
        listOf("monster.json", "invalid_generated.json", "invalid_openapi_exhaustive.json")
    for (spec in validJsonSpecs) {
      val specFile = File("src/test/resources/$spec")
      assertTrue(specFile.exists(), "Expected test resource $spec to exist")

      for (cmd in listOf("to_sdk", "to_server")) {
        val outDir = Files.createTempDirectory("test_monster").toFile()
        try {
          val code =
              runCli(
                  arrayOf(
                      "from_openapi", cmd, "-i", specFile.absolutePath, "-o", outDir.absolutePath))
          assertEquals(0, code, "Expected $cmd to succeed on $spec")
          assertTrue(outDir.list()?.isNotEmpty() == true, "Expected files generated for $spec")
        } finally {
          outDir.deleteRecursively()
        }
      }

      val docsOut = Files.createTempFile("docs", ".json").toFile()
      try {
        val docsCode =
            runCli(arrayOf("to_docs_json", "-i", specFile.absolutePath, "-o", docsOut.absolutePath))
        assertEquals(0, docsCode, "Expected to_docs_json to succeed on $spec")
        assertTrue(docsOut.readText().contains("endpoints"))
      } finally {
        docsOut.delete()
      }
    }
  }

  @Test
  fun testUnknownSubcommandsFailGracefully() {
    val outDir = Files.createTempDirectory("test_unknown").toFile()
    try {
      val code =
          runCli(
              arrayOf(
                  "from_openapi",
                  "unknown_subcmd",
                  "-i",
                  "src/test/resources/monster.json",
                  "-o",
                  outDir.absolutePath))
      assertEquals(1, code)
    } finally {
      outDir.deleteRecursively()
    }
  }
}
