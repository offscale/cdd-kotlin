import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit tests verifying JVM platform functions (`PlatformJvm.kt`). */
class PlatformJvmCoverageTest {

  @Test
  fun testPlatformServerCodeWithAndWithoutTests() {
    val tempDir = Files.createTempDirectory("test_gen_server").toFile()
    try {
      val json = File("src/test/resources/mega_spec.json").readText()
      generateServerCode(json, tempDir.absolutePath, "com.test", true)
      assertTrue(File(tempDir, "server/src/main/kotlin/com/test/Main.kt").exists())
      assertTrue(File(tempDir, "server/src/test/kotlin/com/test/MainTest.kt").exists())

      val tempDirNoTests = Files.createTempDirectory("test_gen_server_notests").toFile()
      try {
        generateServerCode(json, tempDirNoTests.absolutePath, "com.test", false)
        assertTrue(File(tempDirNoTests, "server/src/main/kotlin/com/test/Main.kt").exists())
      } finally {
        tempDirNoTests.deleteRecursively()
      }
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testPlatformPerformSync() {
    val tempDir = Files.createTempDirectory("test_sync_jvm").toFile()
    try {
      File(tempDir, "User.kt")
          .writeText("package com.test; data class User(val id: Int, val name: String)")
      performSync(tempDir.absolutePath, "class")
      assertTrue(File(tempDir, "openapi_sync.json").exists())
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testPlatformFileIoAndEnv() {
    assertNotNull(getEnvVar("PWD"))

    assertTrue(readFile("/nonexistent_dir_12345/nonexistent_file.txt").isFailure)

    val tempDir = Files.createTempDirectory("test_platform_io").toFile()
    try {
      val target = File(tempDir, "nested/file.txt")
      assertTrue(writeToFile(target.absolutePath, "hello world").isSuccess)
      assertEquals("hello world", readFile(target.absolutePath).getOrThrow())

      val outSpec = File(tempDir, "openapi.json")
      assertTrue(generateToOpenApi(tempDir.absolutePath, outSpec.absolutePath).isSuccess)
      assertTrue(outSpec.exists())
    } finally {
      tempDir.deleteRecursively()
    }
  }
}
