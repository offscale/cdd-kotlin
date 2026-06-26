import org.cdd.CddCli
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CddCliErrorTest {
  @Test
  fun testGenerateFromOpenApiErrors() {
    // Missing input
    assertEquals(1, CddCli.generateFromOpenApi(arrayOf("-o", "out")))
    // Missing output (will default to out)
    assertEquals(1, CddCli.generateFromOpenApi(arrayOf("-i", "nonexistent.json")))
  }

  @Test
  fun testGenerateToOpenApiErrors() {
    assertEquals(1, CddCli.generateToOpenApi(arrayOf("-o", "out")))
  }

  @Test
  fun testGenerateDocsJsonErrors() {
    assertEquals(1, CddCli.generateDocsJson(arrayOf("-o", "out")))
  }
}
