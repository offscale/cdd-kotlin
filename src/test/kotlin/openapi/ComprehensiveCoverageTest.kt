/** Unit and integration tests in [ComprehensiveCoverageTest]. */
package openapi

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import runCli

/** End-to-end CLI SDK generation and OpenAPI round-trip verification test. */
class ComprehensiveCoverageTest {

  @Test
  fun testEndToEndSdkGenerationAndOpenApiExtraction() {
    val tempDir = Files.createTempDirectory("coverage-test").toFile()
    try {
      val specFile = File("src/test/resources/monster.json")
      assertTrue(specFile.exists())

      val sdkOutDir = File(tempDir, "sdk")
      val sdkResult =
          runCli(
              arrayOf(
                  "from_openapi",
                  "to_sdk",
                  "--input",
                  specFile.absolutePath,
                  "--output",
                  sdkOutDir.absolutePath,
                  "--tests"))
      assertEquals(0, sdkResult)
      assertTrue(File(sdkOutDir, "build.gradle.kts").exists())
      assertTrue(File(sdkOutDir, ".github/workflows/ci.yml").exists())
      assertTrue(
          File(sdkOutDir, "composeApp/src/commonMain/kotlin/org/example/mcp/McpServer.kt").exists())
      assertTrue(
          File(sdkOutDir, "composeApp/src/commonTest/kotlin/org/example/ClientTest.kt").exists())

      val extractedSpec = File(tempDir, "extracted_openapi.json")
      val toOpenApiResult =
          runCli(
              arrayOf(
                  "to_openapi",
                  "-i",
                  File(sdkOutDir, "composeApp/src/commonMain/kotlin").absolutePath,
                  "-o",
                  extractedSpec.absolutePath))
      assertEquals(0, toOpenApiResult)
      assertTrue(extractedSpec.exists())
      assertTrue(extractedSpec.readText().contains("openapi"))
    } finally {
      tempDir.deleteRecursively()
    }
  }
}
