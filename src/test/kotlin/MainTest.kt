import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `to_sdk handles CDD_INPUT env variable`(@TempDir tempDir: Path) {
        val specFile = tempDir.resolve("spec.json").toFile()
        specFile.writeText("{}")
        val original = System.getenv("CDD_INPUT") ?: System.getProperty("CDD_INPUT")
        try {
            System.setProperty("CDD_INPUT", specFile.absolutePath)
            val result = runCli(arrayOf("to_sdk"))
            assertEquals(1, result) // Fails due to invalid JSON, but proves file was picked up
        } finally {
            if (original != null) System.setProperty("CDD_INPUT", original) else System.clearProperty("CDD_INPUT")
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
            if (original != null) System.setProperty("CDD_INPUT", original) else System.clearProperty("CDD_INPUT")
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
            val outDir = tempDir.resolve("out").toFile()
            outDir.mkdirs()
            val result = runCli(arrayOf("to_sdk", "-i", specFile.absolutePath, "-o", outDir.absolutePath))
            assertEquals(0, result)
            assertTrue(File(outDir, "src/main/kotlin/org/example/Client.kt").exists())
            assertTrue(File(outDir, "build.gradle.kts").exists())
            assertTrue(File(outDir, ".github/workflows/ci.yml").exists())
        }
    }

    @Test
    fun `to_sdk skips github actions when no-github-actions is passed`(@TempDir tempDir: Path) {
        withUserDir(tempDir) {
            val specFile = tempDir.resolve("spec.json").toFile()
            specFile.writeText("""
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              }
            }
        """.trimIndent())
            val outDir = tempDir.resolve("out").toFile()
            outDir.mkdirs()
            val result = runCli(arrayOf("to_sdk", "-i", specFile.absolutePath, "-o", outDir.absolutePath, "--no-github-actions"))
            assertEquals(0, result)
            assertFalse(File(outDir, ".github/workflows/ci.yml").exists())
            assertTrue(File(outDir, "build.gradle.kts").exists())
        }
    }

    @Test
    fun `to_sdk skips build gradle when no-installable-package is passed`(@TempDir tempDir: Path) {
        withUserDir(tempDir) {
            val specFile = tempDir.resolve("spec.json").toFile()
            specFile.writeText("""
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              }
            }
        """.trimIndent())
            val outDir = tempDir.resolve("out").toFile()
            outDir.mkdirs()
            val result = runCli(arrayOf("to_sdk", "-i", specFile.absolutePath, "-o", outDir.absolutePath, "--no-installable-package"))
            assertEquals(0, result)
            assertTrue(File(outDir, ".github/workflows/ci.yml").exists())
            assertFalse(File(outDir, "build.gradle.kts").exists())
        }
    }

    @Test
    fun `to_sdk generates mocks and tests when create-composable-tests-mocks is passed`(@TempDir tempDir: Path) {
        withUserDir(tempDir) {
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
            val outDir = tempDir.resolve("out").toFile()
            outDir.mkdirs()
            val result = runCli(arrayOf("to_sdk", "-i", specFile.absolutePath, "-o", outDir.absolutePath, "--tests"))
            assertEquals(0, result)
            assertTrue(File(outDir, "src/main/kotlin/org/example/Mocks.kt").exists())
            assertTrue(File(outDir, "src/main/kotlin/org/example/Tests.kt").exists())
        }
    }

    @Test
    fun `from_openapi to_sdk delegates correctly`(@TempDir tempDir: Path) {
        withUserDir(tempDir) {
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
            val outDir = tempDir.resolve("out2").toFile()
            outDir.mkdirs()
            val result = runCli(arrayOf("from_openapi", "to_sdk", "-i", specFile.absolutePath, "-o", outDir.absolutePath))
            assertEquals(0, result)
            assertTrue(File(outDir, "src/main/kotlin/org/example/Client.kt").exists())
            assertTrue(File(outDir, "build.gradle.kts").exists())
        }
    }
}
