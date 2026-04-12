package scaffold

import domain.Info
import writeToFile

class ScaffoldGenerator {
    fun generate(
        outputDirectory: String,
        projectName: String,
        packageName: String,
        info: Info? = null
    ) {
        writeFile(outputDirectory, "build.gradle.kts", ScaffoldTemplates.createRootBuildGradle(info))
        writeFile(outputDirectory, "settings.gradle.kts", ScaffoldTemplates.createSettingsGradle(projectName))
        writeFile(outputDirectory, "gradle.properties", ScaffoldTemplates.createGradleProperties())

        val gradleFolder = "$outputDirectory/gradle"
        writeFile(gradleFolder, "libs.versions.toml", ScaffoldTemplates.createVersionCatalog())

        val appDir = "$outputDirectory/composeApp"
        writeFile(appDir, "build.gradle.kts", ScaffoldTemplates.createAppBuildGradle(packageName, info))

        generateSourceSets(appDir, packageName)
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
