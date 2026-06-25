package openapi

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Test
import runCli

class ComprehensiveCoverageTest {
  @Test
  fun testEverythingOnPetstore() {
    val tempDir = Files.createTempDirectory("coverage-test").toFile()

    val specs = listOf("../petstore_oas3.json", "../petstore.json", "../petstore.yaml")

    for (spec in specs) {
      val file = File(spec)
      if (file.exists()) {
        val outDir = File(tempDir, file.name)
        outDir.mkdirs()
        println("Running for ${file.name}")
        val result =
            runCli(
                arrayOf(
                    "from_openapi",
                    "to_sdk",
                    "--input",
                    file.absolutePath,
                    "--output",
                    outDir.absolutePath))
        println("Result for ${file.name}: $result")
      }
    }

    tempDir.deleteRecursively()
  }
}
