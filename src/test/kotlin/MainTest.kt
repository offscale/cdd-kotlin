import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

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
    fun `main with unknown command throws RuntimeException`() {
        assertThrows(RuntimeException::class.java) {
            main(arrayOf("unknown_command"))
        }
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
    fun `to_docs_json fails if missing input file`() {
        val originalInput = System.getenv("CDD_INPUT")
        try {
            val result = runCli(arrayOf("to_docs_json"))
            if (originalInput.isNullOrEmpty()) {
                assertEquals(1, result)
            }
        } finally {
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
        specFile.writeText("""
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
        """.trimIndent())
        
        val result = runCli(arrayOf("to_docs_json", "--input", specFile.absolutePath))
        assertEquals(0, result)
    }

    @Test
    fun `to_docs_json processes valid openapi json without imports and wrapping`(@TempDir tempDir: Path) {
        val specFile = tempDir.resolve("spec.json").toFile()
        specFile.writeText("""
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
        """.trimIndent())
        
        val result = runCli(arrayOf("to_docs_json", "--input", specFile.absolutePath, "--no-imports", "--no-wrapping"))
        assertEquals(0, result)
    }

    @Test
    fun `to_sdk generates kotlin sdk`(@TempDir tempDir: Path) {
        withUserDir(tempDir) {
            val outDir = tempDir.resolve("out").toFile()
            outDir.mkdirs()
            val result = runCli(arrayOf("to_sdk", "-o", outDir.absolutePath))
            assertEquals(0, result)
            assertTrue(File(outDir, "ApiClient.kt").exists())
        }
    }

    @Test
    fun `from_openapi to_sdk delegates correctly`(@TempDir tempDir: Path) {
        withUserDir(tempDir) {
            val outDir = tempDir.resolve("out2").toFile()
            outDir.mkdirs()
            val result = runCli(arrayOf("from_openapi", "to_sdk", "-o", outDir.absolutePath))
            assertEquals(0, result)
            assertTrue(File(outDir, "ApiClient.kt").exists())
        }
    }
}
