import java.nio.file.Files
import org.cdd.CddCli
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CddCliErrorTest {
  @Test
  fun testGenerateFromOpenApiErrors() {
    // Missing input
    assertEquals(1, CddCli.generateFromOpenApi(arrayOf("to_sdk", "-o", "out")))
    // Nonexistent input file triggers exception catch block
    assertEquals(1, CddCli.generateFromOpenApi(arrayOf("to_sdk", "-i", "nonexistent.json")))
    // --input-dir with nonexistent input file triggers exception catch block
    val tempDir = Files.createTempDirectory("cdd_err_test").toFile()
    try {
      assertEquals(
          1,
          CddCli.generateFromOpenApi(
              arrayOf("to_sdk", "--input-dir", tempDir.absolutePath, "--tests")))
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testGenerateToOpenApiErrors() {
    assertEquals(1, CddCli.generateToOpenApi(arrayOf("to_openapi", "-o", "out")))
  }

  @Test
  fun testGenerateDocsJsonErrors() {
    assertEquals(1, CddCli.generateDocsJson(arrayOf("to_docs_json", "-o", "out")))
    val badFile = Files.createTempFile("bad_spec", ".json").toFile()
    try {
      badFile.writeText("{not valid json")
      assertEquals(1, CddCli.generateDocsJson(arrayOf("to_docs_json", "-i", badFile.absolutePath)))
    } finally {
      badFile.delete()
    }
  }
}
