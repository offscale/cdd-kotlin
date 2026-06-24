package scaffold

import domain.Info
import writeToFile

/** Generator for scaffold project. */
class ScaffoldGenerator {
  /** Generates the scaffold project in the given directory. */
  fun generate(
      outputDirectory: String,
      projectName: String,
      packageName: String,
      info: Info? = null,
      isServer: Boolean = false,
      withTests: Boolean = false
  ) {
    writeFile(outputDirectory, "build.gradle.kts", ScaffoldTemplates.createRootBuildGradle(info))
    writeFile(
        outputDirectory,
        "settings.gradle.kts",
        ScaffoldTemplates.createSettingsGradle(projectName, isServer))
    writeFile(outputDirectory, "gradle.properties", ScaffoldTemplates.createGradleProperties())

    val gradleFolder = "$outputDirectory/gradle"
    writeFile(gradleFolder, "libs.versions.toml", ScaffoldTemplates.createVersionCatalog(isServer))

    if (isServer) {
      val serverDir = "$outputDirectory/server"
      writeFile(
          serverDir,
          "build.gradle.kts",
          ScaffoldTemplates.createServerBuildGradle(packageName, info, withTests))
    } else {
      val appDir = "$outputDirectory/composeApp"
      writeFile(
          appDir, "build.gradle.kts", ScaffoldTemplates.createAppBuildGradle(packageName, info))
      generateSourceSets(appDir, packageName)
    }
  }

  private fun generateSourceSets(appModuleDir: String, packageName: String) {
    val packagePath = packageName.replace('.', '/')
    val srcDir = "$appModuleDir/src"

    val commonMainPath = "$srcDir/commonMain/kotlin/$packagePath"
    writeFile(commonMainPath, "App.kt", ScaffoldTemplates.createCommonAppKt(packageName))

    val androidMainDir = "$srcDir/androidMain"
    writeFile(androidMainDir, "AndroidManifest.xml", ScaffoldTemplates.createAndroidManifest())
  }

  private fun writeFile(directory: String, fileName: String, content: String) {
    writeToFile("$directory/$fileName", content)
  }
}
