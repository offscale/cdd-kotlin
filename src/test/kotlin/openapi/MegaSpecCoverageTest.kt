package openapi

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Test
import runCli

class MegaSpecCoverageTest {
  @Test
  fun testMegaSpec() {
    val tempDir = Files.createTempDirectory("mega-spec-test").toFile()
    val megaSpec = File("mega_spec.json")
    try {
      val outDir = File(tempDir, "out")
      outDir.mkdirs()
      runCli(
          arrayOf(
              "from_openapi",
              "to_sdk",
              "--input",
              megaSpec.absolutePath,
              "--output",
              outDir.absolutePath,
              "--language",
              "kotlin"))
      runCli(
          arrayOf(
              "to_docs_json",
              "--input",
              megaSpec.absolutePath,
              "--output",
              File(outDir, "docs.json").absolutePath))
    } finally {
      tempDir.deleteRecursively()
    }
  }
}
