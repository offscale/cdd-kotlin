package org.cdd

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CddGeneratorTest {

  @Test
  fun `generateSdk fails on invalid schema`(@TempDir tempDir: Path) {
    val outDir = tempDir.resolve("out").toFile()
    outDir.mkdirs()

    val config1 = Config(inputPath = "invalid.json", outputDir = outDir.absolutePath)
    assertThrows(RuntimeException::class.java) { CddGenerator.generateSdk(config1) }

    val config2 = Config(inputPath = "missing.json", outputDir = outDir.absolutePath)
    assertThrows(RuntimeException::class.java) { CddGenerator.generateSdk(config2) }

    val config3 = Config(inputPath = "   ", outputDir = outDir.absolutePath)
    assertThrows(RuntimeException::class.java) { CddGenerator.generateSdk(config3) }
  }
}
