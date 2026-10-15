package org.cdd

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CddGeneratorTest {

  @Test
  fun `generateSdk fails on invalid schema`(@TempDir tempDir: Path) {
    val outDir = tempDir.resolve("out").toFile()
    outDir.mkdirs()

    val config1 = Config(inputPath = "invalid.json", outputDir = outDir.absolutePath)
    assertTrue(CddGenerator.generateSdk(config1).isFailure)

    val config2 = Config(inputPath = "missing.json", outputDir = outDir.absolutePath)
    assertTrue(CddGenerator.generateSdk(config2).isFailure)

    val config3 = Config(inputPath = "   ", outputDir = outDir.absolutePath)
    assertTrue(CddGenerator.generateSdk(config3).isFailure)
  }
}
