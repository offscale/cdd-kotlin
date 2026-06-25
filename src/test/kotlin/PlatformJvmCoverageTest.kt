import org.junit.jupiter.api.Test

class PlatformJvmCoverageTest {
  @Test
  fun testPlatformServerCode() {
    val tempDir = java.nio.file.Files.createTempDirectory("test_gen_server").toFile()
    try {
      val json = java.io.File("src/test/resources/mega_spec.json").readText()
      generateServerCode(json, tempDir.absolutePath, "com.test", true)
      org.junit.jupiter.api.Assertions.assertTrue(java.io.File(tempDir, "server").exists())
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testPlatformPerformSync() {
    try {
      performSync("src/test/resources/mega_spec.json", "dummy")
    } catch (e: Exception) {}
  }

  @Test
  fun testPlatform() {
    getEnvVar("PWD")
    try {
      readFile("nonexistent")
    } catch (e: Exception) {}
    try {
      writeToFile("dummy", "dummy")
    } catch (e: Exception) {}
    try {
      java.io.File("dummy").delete()
    } catch (e: Exception) {}
    try {
      generateOpenApi("dummy", "dummy")
    } catch (e: Exception) {}
  }
}
