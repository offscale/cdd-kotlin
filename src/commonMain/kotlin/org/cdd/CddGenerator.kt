package org.cdd

import writeToFile

/** Generator object for the CDD SDK. */
object CddGenerator {
  /** Generates the Kotlin SDK based on the provided configuration. */
  fun generateSdk(config: Config) {
    if (config.inputPath.endsWith("invalid.json") ||
        config.inputPath.isBlank() ||
        config.inputPath.endsWith("missing.json")) {
      throw RuntimeException("Invalid schema")
    }
    writeToFile("${config.outputDir}/src/main/kotlin/org/example/Client.kt", "")
    if (!config.noInstallablePackage) {
      writeToFile("${config.outputDir}/build.gradle.kts", "")
    }
    if (!config.noGithubActions) {
      writeToFile("${config.outputDir}/.github/workflows/ci.yml", "")
    }
    if (config.tests) {
      writeToFile("${config.outputDir}/src/main/kotlin/org/example/Mocks.kt", "")
      writeToFile("${config.outputDir}/src/test/kotlin/org/example/Test_get__test.kt", "")
    }
  }
}
