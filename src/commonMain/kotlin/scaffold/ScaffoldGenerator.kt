/** Multiplatform project scaffold generator for Ktor and Gradle builds. */
package scaffold

import domain.Info
import writeGradleWrapperToDirectory
import writeToFile

/** Generator for scaffold project. */
class ScaffoldGenerator {
  /**
   * Generates the scaffold project in the given directory.
   *
   * @param outputDirectory Target root directory.
   * @param projectName Gradle project name.
   * @param packageName Base Kotlin package name.
   * @param info Optional OpenAPI [Info] metadata.
   * @param isServer Whether to scaffold a Ktor server module instead of a client module.
   * @param withTests Whether to include test dependencies and tasks.
   * @return A [Result] indicating success or a [domain.CddError.FileIoError].
   */
  fun generate(
      outputDirectory: String,
      projectName: String,
      packageName: String,
      info: Info? = null,
      isServer: Boolean = false,
      withTests: Boolean = false
  ): Result<Unit> {
    val files =
        mutableListOf(
            "$outputDirectory/build.gradle.kts" to
                ScaffoldTemplates.createRootBuildGradle(info, isServer),
            "$outputDirectory/settings.gradle.kts" to
                ScaffoldTemplates.createSettingsGradle(projectName, isServer),
            "$outputDirectory/gradle.properties" to ScaffoldTemplates.createGradleProperties(),
            "$outputDirectory/gradle/libs.versions.toml" to
                ScaffoldTemplates.createVersionCatalog(isServer))

    if (isServer) {
      files.add(
          "$outputDirectory/server/build.gradle.kts" to
              ScaffoldTemplates.createServerBuildGradle(packageName, info, withTests))
    } else {
      val appDir = "$outputDirectory/composeApp"
      val packagePath = packageName.replace('.', '/')
      val srcDir = "$appDir/src"
      files.add(
          "$appDir/build.gradle.kts" to ScaffoldTemplates.createAppBuildGradle(packageName, info))
      files.add(
          "$srcDir/commonMain/kotlin/$packagePath/App.kt" to
              ScaffoldTemplates.createCommonAppKt(packageName))
      files.add(
          "$srcDir/commonTest/kotlin/$packagePath/ClientTest.kt" to
              ScaffoldTemplates.createClientTestKt(packageName))
      files.add(
          "$srcDir/androidMain/AndroidManifest.xml" to ScaffoldTemplates.createAndroidManifest())
    }

    files.add("$outputDirectory/gradlew" to ScaffoldTemplates.createGradlewScript())
    files.add("$outputDirectory/gradlew.bat" to ScaffoldTemplates.createGradlewBatScript())
    files.add(
        "$outputDirectory/gradle/wrapper/gradle-wrapper.properties" to
            ScaffoldTemplates.createGradleWrapperProperties())

    for ((path, content) in files) {
      writeToFile(path, content).getOrElse {
        return Result.failure(it)
      }
    }
    return writeGradleWrapperToDirectory(outputDirectory)
  }
}
