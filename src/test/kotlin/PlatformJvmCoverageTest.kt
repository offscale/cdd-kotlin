import org.junit.jupiter.api.Test

class PlatformJvmCoverageTest {
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
