/** Unit and integration tests in [MegaSpecCoverageTest]. */
package openapi

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import psi.DtoParser
import psi.NetworkParser
import runCli

class MegaSpecCoverageTest {
  @Test
  fun testMegaSpec() {
    val tempDir = Files.createTempDirectory("mega-spec-test").toFile()
    val megaSpec = File("mega_spec.json")
    try {
      val outDir = File(tempDir, "out")
      outDir.mkdirs()
      val sdkExit =
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
      assertEquals(0, sdkExit)

      val sdkCliOutDir = File(tempDir, "out-cli")
      val sdkCliExit =
          runCli(
              arrayOf(
                  "from_openapi",
                  "to_sdk_cli",
                  "--input",
                  megaSpec.absolutePath,
                  "--output",
                  sdkCliOutDir.absolutePath))
      assertEquals(0, sdkCliExit)

      val serverOutDir = File(tempDir, "out-server")
      val serverExit =
          runCli(
              arrayOf(
                  "from_openapi",
                  "to_server",
                  "--input",
                  megaSpec.absolutePath,
                  "--output",
                  serverOutDir.absolutePath))
      assertEquals(0, serverExit)

      val docsFile = File(outDir, "docs.json")
      val docsExit =
          runCli(
              arrayOf(
                  "to_docs_json",
                  "--input",
                  megaSpec.absolutePath,
                  "--output",
                  docsFile.absolutePath))
      assertEquals(0, docsExit)
      assertTrue(docsFile.exists())

      val docsJson = Json.parseToJsonElement(docsFile.readText()).jsonObject
      assertTrue(docsJson.containsKey("endpoints"))
      assertTrue(docsJson["endpoints"]!!.jsonObject.isNotEmpty())

      val ktFiles = outDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
      assertTrue(ktFiles.isNotEmpty())
      val apiClientFile = ktFiles.firstOrNull { it.name == "ApiClient.kt" }
      assertTrue(apiClientFile != null && apiClientFile.exists())
      val parsedEndpoints = NetworkParser().parseWithMetadata(apiClientFile!!.readText()).endpoints
      assertTrue(parsedEndpoints.any { it.operationId == "getPetById" })
      assertTrue(parsedEndpoints.any { it.operationId == "updatePet" })

      val petDtoFile = ktFiles.firstOrNull { it.name == "Pet.kt" }
      assertTrue(petDtoFile != null && petDtoFile.exists())
      val parsedSchemas = DtoParser().parse(petDtoFile!!.readText())
      assertTrue(parsedSchemas.any { it.name == "Pet" })
    } finally {
      tempDir.deleteRecursively()
    }
  }
}
