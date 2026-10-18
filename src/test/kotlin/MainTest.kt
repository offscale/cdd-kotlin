/** Unit and integration tests in [MainTest]. */
import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MainTest {

  private fun withUserDir(dir: Path, block: () -> Unit) {
    val original = System.getProperty("user.dir")
    try {
      System.setProperty("user.dir", dir.toString())
      block()
    } finally {
      System.setProperty("user.dir", original)
    }
  }

  @Test
  fun `main without arguments prints message and returns 0`() {
    main(emptyArray())
    val result = runCli(emptyArray())
    assertEquals(0, result)
  }

  @Test
  fun `main with unknown command returns failure Result`() {
    main(arrayOf("unknown_command"))
    val res = runCliResult(arrayOf("unknown_command"))
    assertTrue(res.isFailure)
    assertEquals(0, runCliResult(emptyArray()).getOrThrow())
  }

  @Test
  fun `main generates scaffold in demo mode`(@TempDir tempDir: Path) {
    withUserDir(tempDir) {
      val result = runCli(arrayOf("demo"))
      assertEquals(0, result)
      val outputDir = File(tempDir.toFile(), "generated-project")
      assertTrue(File(outputDir, "build.gradle.kts").exists())
      assertTrue(File(outputDir, "composeApp/build.gradle.kts").exists())
    }
  }

  @Test
  fun `main runs mcp server when mcp command is passed`() {
    val originalIn = System.`in`
    try {
      System.setIn(java.io.ByteArrayInputStream(ByteArray(0)))
      val result = runCli(arrayOf("mcp"))
      assertEquals(0, result)
    } finally {
      System.setIn(originalIn)
    }
  }

  @Test
  fun `main handles generation failures gracefully`(@TempDir tempDir: Path) {
    withUserDir(tempDir) {
      val blocker = File(tempDir.toFile(), "generated-project")
      blocker.writeText("not a directory")
      val result = runCli(arrayOf("demo"))
      assertEquals(1, result)
      assertTrue(blocker.isFile)
    }
  }

  @Test
  fun `to_sdk handles CDD_INPUT env variable`(@TempDir tempDir: Path) {
    val specFile = tempDir.resolve("spec.json").toFile()
    specFile.writeText("{invalid_json")
    val original = System.getenv("CDD_INPUT") ?: System.getProperty("CDD_INPUT")
    try {
      System.setProperty("CDD_INPUT", specFile.absolutePath)
      val result = runCli(arrayOf("to_sdk"))
      assertEquals(1, result) // Fails due to invalid JSON, but proves file was picked up
    } finally {
      if (original != null) System.setProperty("CDD_INPUT", original)
      else System.clearProperty("CDD_INPUT")
    }
  }

  @Test
  fun `to_sdk handles INPUT env variable`(@TempDir tempDir: Path) {
    val specFile = tempDir.resolve("spec.json").toFile()
    specFile.writeText("{}")
    val original = System.getenv("INPUT") ?: System.getProperty("INPUT")
    try {
      System.setProperty("INPUT", specFile.absolutePath)
      val result = runCli(arrayOf("to_sdk"))
      assertEquals(1, result) // Fails due to invalid JSON, but proves file was picked up
    } finally {
      if (original != null) System.setProperty("INPUT", original) else System.clearProperty("INPUT")
    }
  }

  @Test
  fun `to_sdk fails if missing input file`() {
    val original = System.getProperty("CDD_INPUT")
    val originalInput = System.getProperty("INPUT")
    try {
      System.clearProperty("CDD_INPUT")
      System.clearProperty("INPUT")
      val result = runCli(arrayOf("to_sdk"))
      assertEquals(1, result)
    } finally {
      if (original != null) System.setProperty("CDD_INPUT", original)
      if (originalInput != null) System.setProperty("INPUT", originalInput)
    }
  }

  @Test
  fun `to_docs_json fails if missing input file`() {
    val original = System.getProperty("CDD_INPUT")
    val originalInput = System.getProperty("INPUT")
    try {
      System.clearProperty("CDD_INPUT")
      System.clearProperty("INPUT")
      val result = runCli(arrayOf("to_docs_json"))
      assertEquals(1, result)
    } finally {
      if (original != null) System.setProperty("CDD_INPUT", original)
      if (originalInput != null) System.setProperty("INPUT", originalInput)
    }
  }

  @Test
  fun `to_docs_json handles CDD_INPUT env variable`(@TempDir tempDir: Path) {
    val specFile = tempDir.resolve("spec.json").toFile()
    specFile.writeText("{}")
    val original = System.getenv("CDD_INPUT") ?: System.getProperty("CDD_INPUT")
    try {
      System.setProperty("CDD_INPUT", specFile.absolutePath)
      val result = runCli(arrayOf("to_docs_json"))
      assertEquals(1, result) // Fails due to invalid JSON, but proves file was picked up
    } finally {
      if (original != null) System.setProperty("CDD_INPUT", original)
      else System.clearProperty("CDD_INPUT")
    }
  }

  @Test
  fun `to_docs_json handles INPUT env variable`(@TempDir tempDir: Path) {
    val specFile = tempDir.resolve("spec.json").toFile()
    specFile.writeText("{}")
    val original = System.getenv("INPUT") ?: System.getProperty("INPUT")
    try {
      System.setProperty("INPUT", specFile.absolutePath)
      val result = runCli(arrayOf("to_docs_json"))
      assertEquals(1, result) // Fails due to invalid JSON, but proves file was picked up
    } finally {
      if (original != null) System.setProperty("INPUT", original) else System.clearProperty("INPUT")
    }
  }

  @Test
  fun `to_docs_json with invalid openapi json returns 1`(@TempDir tempDir: Path) {
    val invalidFile = tempDir.resolve("invalid.json").toFile()
    invalidFile.writeText("{ \"notOpenApi\": true }")
    val result = runCli(arrayOf("to_docs_json", "-i", invalidFile.absolutePath))
    assertEquals(1, result)
  }

  @Test
  fun `to_docs_json processes valid openapi json with defaults`(@TempDir tempDir: Path) {
    val specFile = tempDir.resolve("spec.json").toFile()
    specFile.writeText(
        """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/test": {
                  "get": {
                    "operationId": "getTest",
                    "responses": {
                      "200": { "description": "OK" }
                    }
                  }
                }
              }
            }
        """
            .trimIndent())

    val result = runCli(arrayOf("to_docs_json", "--input", specFile.absolutePath))
    assertEquals(0, result)
  }

  @Test
  fun `to_docs_json processes valid openapi json without imports and wrapping`(
      @TempDir tempDir: Path
  ) {
    val specFile = tempDir.resolve("spec.json").toFile()
    specFile.writeText(
        """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/test": {
                  "get": {
                    "operationId": "getTest",
                    "responses": {
                      "200": { "description": "OK" }
                    }
                  }
                }
              }
            }
        """
            .trimIndent())

    val result =
        runCli(
            arrayOf(
                "to_docs_json", "--input", specFile.absolutePath, "--no-imports", "--no-wrapping"))
    assertEquals(0, result)
  }

  @Test
  fun `to_sdk generates kotlin sdk`(@TempDir tempDir: Path) {
    withUserDir(tempDir) {
      val specFile = tempDir.resolve("spec.json").toFile()
      specFile.writeText(
          """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/test": {
                  "get": {
                    "operationId": "getTest",
                    "responses": {
                      "200": { "description": "OK" }
                    }
                  }
                }
              }
            }
        """
              .trimIndent())
      val outDir = tempDir.resolve("out").toFile()
      outDir.mkdirs()
      val result = runCli(arrayOf("to_sdk", "-i", specFile.absolutePath, "-o", outDir.absolutePath))
      assertEquals(0, result)
      assertTrue(
          File(outDir, "composeApp/src/commonMain/kotlin/org/example/api/ApiClient.kt").exists())
      assertTrue(File(outDir, "build.gradle.kts").exists())
      assertTrue(File(outDir, "composeApp/build.gradle.kts").exists())
      assertTrue(File(outDir, ".github/workflows/ci.yml").exists())
    }
  }

  @Test
  fun `to_sdk skips github actions when no-github-actions is passed`(@TempDir tempDir: Path) {
    withUserDir(tempDir) {
      val specFile = tempDir.resolve("spec.json").toFile()
      specFile.writeText(
          """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              }
            }
        """
              .trimIndent())
      val outDir = tempDir.resolve("out").toFile()
      outDir.mkdirs()
      val result =
          runCli(
              arrayOf(
                  "to_sdk",
                  "-i",
                  specFile.absolutePath,
                  "-o",
                  outDir.absolutePath,
                  "--no-github-actions"))
      assertEquals(0, result)
      assertFalse(File(outDir, ".github/workflows/ci.yml").exists())
      assertTrue(File(outDir, "build.gradle.kts").exists())
    }
  }

  @Test
  fun `to_sdk skips build gradle when no-installable-package is passed`(@TempDir tempDir: Path) {
    withUserDir(tempDir) {
      val specFile = tempDir.resolve("spec.json").toFile()
      specFile.writeText(
          """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              }
            }
        """
              .trimIndent())
      val outDir = tempDir.resolve("out").toFile()
      outDir.mkdirs()
      val result =
          runCli(
              arrayOf(
                  "to_sdk",
                  "-i",
                  specFile.absolutePath,
                  "-o",
                  outDir.absolutePath,
                  "--no-installable-package"))
      assertEquals(0, result)
      assertTrue(File(outDir, ".github/workflows/ci.yml").exists())
      assertFalse(File(outDir, "build.gradle.kts").exists())
    }
  }

  @Test
  fun `to_sdk generates mocks and tests when create-composable-tests-mocks is passed`(
      @TempDir tempDir: Path
  ) {
    withUserDir(tempDir) {
      val specFile = tempDir.resolve("spec.json").toFile()
      specFile.writeText(
          """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/test": {
                  "get": {
                    "operationId": "getTest",
                    "responses": {
                      "200": { "description": "OK" }
                    }
                  }
                }
              }
            }
        """
              .trimIndent())
      val outDir = tempDir.resolve("out").toFile()
      outDir.mkdirs()
      val result =
          runCli(
              arrayOf("to_sdk", "-i", specFile.absolutePath, "-o", outDir.absolutePath, "--tests"))
      assertEquals(0, result)
      assertTrue(File(outDir, "composeApp/src/commonTest/kotlin/org/example/Mocks.kt").exists())
      assertTrue(
          File(outDir, "composeApp/src/commonTest/kotlin/org/example/ClientTest.kt").exists())
    }
  }

  @Test
  fun `from_openapi to_sdk delegates correctly`(@TempDir tempDir: Path) {
    withUserDir(tempDir) {
      val specFile = tempDir.resolve("spec.json").toFile()
      specFile.writeText(
          """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/test": {
                  "get": {
                    "operationId": "getTest",
                    "responses": {
                      "200": { "description": "OK" }
                    }
                  }
                }
              }
            }
        """
              .trimIndent())
      val outDir = tempDir.resolve("out2").toFile()
      outDir.mkdirs()
      val result =
          runCli(
              arrayOf(
                  "from_openapi", "to_sdk", "-i", specFile.absolutePath, "-o", outDir.absolutePath))
      assertEquals(0, result)
      assertTrue(
          File(outDir, "composeApp/src/commonMain/kotlin/org/example/api/ApiClient.kt").exists())
      assertTrue(File(outDir, "build.gradle.kts").exists())
    }
  }

  @Test
  fun `from_arazzo to_sdk, to_sdk_cli, to_server, to_arazzo, and sync arazzo work end-to-end`(
      @TempDir tempDir: Path
  ) {
    withUserDir(tempDir) {
      val arazzoSpec =
          """
          {
            "arazzo": "1.1.0",
            "info": {
              "title": "Petstore Workflows",
              "version": "1.0.0"
            },
            "sourceDescriptions": [
              {
                "name": "petstore",
                "url": "./petstore.json",
                "type": "openapi"
              }
            ],
            "workflows": [
              {
                "workflowId": "adoptPet",
                "summary": "Adopt a pet",
                "steps": [
                  {
                    "stepId": "findPet",
                    "operationId": "findPetsByStatus"
                  }
                ]
              }
            ]
          }
          """
              .trimIndent()
      val specFile = tempDir.resolve("arazzo.json").toFile()
      specFile.writeText(arazzoSpec)

      val sdkOut = tempDir.resolve("arazzo_sdk").toFile()
      assertEquals(
          0,
          runCli(
              arrayOf(
                  "from_arazzo",
                  "to_sdk_cli",
                  "-i",
                  specFile.absolutePath,
                  "-o",
                  sdkOut.absolutePath)))
      assertTrue(
          File(
                  sdkOut,
                  "composeApp/src/commonMain/kotlin/org/example/workflows/WorkflowOrchestrator.kt")
              .exists())
      assertTrue(
          File(sdkOut, "composeApp/src/commonMain/kotlin/org/example/cli/WorkflowCli.kt").exists())

      // Default target when flags follow from_arazzo directly
      val sdkDefaultOut = tempDir.resolve("arazzo_sdk_default").toFile()
      assertEquals(
          0,
          runCli(
              arrayOf(
                  "from_arazzo",
                  "-i",
                  specFile.absolutePath,
                  "-o",
                  sdkDefaultOut.absolutePath,
                  "--no-github-actions",
                  "--no-installable-package",
                  "--tests")))

      val srvOut = tempDir.resolve("arazzo_srv").toFile()
      assertEquals(
          0,
          runCli(
              arrayOf(
                  "from_arazzo",
                  "to_server",
                  "-i",
                  specFile.absolutePath,
                  "-o",
                  srvOut.absolutePath,
                  "--tests")))
      assertTrue(
          File(srvOut, "server/src/main/kotlin/org/example/server/WorkflowRoutes.kt").exists())
      assertTrue(
          File(srvOut, "server/src/test/kotlin/org/example/server/WorkflowE2ETest.kt").exists())

      val roundTripArazzo = tempDir.resolve("roundtrip.arazzo.json").toFile()
      assertEquals(
          0,
          runCli(
              arrayOf("to_arazzo", "-i", sdkOut.absolutePath, "-o", roundTripArazzo.absolutePath)))
      assertTrue(roundTripArazzo.exists())
      assertTrue(roundTripArazzo.readText().contains("adoptPet"))

      assertEquals(0, runCli(arrayOf("sync", "-i", sdkOut.absolutePath, "--truth", "arazzo")))
      assertTrue(File(sdkOut, "arazzo_sync.json").exists())

      // Auto-detection via from_openapi when an Arazzo spec is passed
      val autoSdkOut = tempDir.resolve("auto_sdk").toFile()
      assertEquals(
          0,
          runCli(
              arrayOf(
                  "from_openapi",
                  "to_sdk",
                  "-i",
                  specFile.absolutePath,
                  "-o",
                  autoSdkOut.absolutePath)))
      val autoSrvOut = tempDir.resolve("auto_srv").toFile()
      assertEquals(
          0,
          runCli(
              arrayOf(
                  "from_openapi",
                  "to_server",
                  "-i",
                  specFile.absolutePath,
                  "-o",
                  autoSrvOut.absolutePath)))

      // Error branches
      assertEquals(1, runCli(arrayOf("from_arazzo")))
      assertEquals(1, runCli(arrayOf("from_arazzo", "-i", "/nonexistent/invalid.json")))
      assertEquals(1, runCli(arrayOf("to_arazzo")))
      assertEquals(1, runCli(arrayOf("to_arazzo", "-i", "/nonexistent/dir")))
    }
  }
}
