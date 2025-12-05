package scaffold

import java.io.File
import java.io.IOException

/**
 * Service responsible for creating the file structure of a Kotlin Multiplatform project.
 */
class ScaffoldGenerator {

    /**
     * Generates a complete KMP scaffold at the specified location.
     *
     * @param outputDirectory The root directory where the project will be created.
     * @param projectName The name of the gradle root project.
     * @param packageName The base package name (e.g. com.example.app).
     * @throws IOException If file writing fails.
     */
    @Throws(IOException::class)
    fun generate(outputDirectory: File, projectName: String, packageName: String) {
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }

        // 1. Root Gradle Files
        writeFile(outputDirectory, "build.gradle.kts", ScaffoldTemplates.createRootBuildGradle())
        writeFile(outputDirectory, "settings.gradle.kts", ScaffoldTemplates.createSettingsGradle(projectName))
        writeFile(outputDirectory, "gradle.properties", ScaffoldTemplates.createGradleProperties())

        // 2. Version Catalog (gradle/libs.versions.toml)
        val gradleFolder = File(outputDirectory, "gradle").apply { mkdirs() }
        writeFile(gradleFolder, "libs.versions.toml", ScaffoldTemplates.createVersionCatalog())

        // 3. Compose App Module
        val appDir = File(outputDirectory, "composeApp").apply { mkdirs() }
        writeFile(appDir, "build.gradle.kts", ScaffoldTemplates.createAppBuildGradle(packageName))

        // 4. Source Sets
        generateSourceSets(appDir, packageName)
    }

    /**
     * Helper to create source directories and initial source files.
     */
    private fun generateSourceSets(appModuleDir: File, packageName: String) {
        val packagePath = packageName.replace('.', '/')
        val srcDir = File(appModuleDir, "src")

        // commonMain
        val commonMainPath = File(srcDir, "commonMain/kotlin/$packagePath").apply { mkdirs() }
        writeFile(commonMainPath, "App.kt", ScaffoldTemplates.createCommonAppKt(packageName))

        // androidMain
        val androidMainDir = File(srcDir, "androidMain")
        val androidCodeDir = File(androidMainDir, "kotlin/$packagePath").apply { mkdirs() }
        // Create manifest strictly in src/androidMain
        writeFile(androidMainDir, "AndroidManifest.xml", ScaffoldTemplates.createAndroidManifest())
        // Create an empty MainActivity placeholders if needed, but App generator is enough is minimal

        // desktopMain
        File(srcDir, "desktopMain/kotlin/$packagePath").apply { mkdirs() }

        // iosMain
        File(srcDir, "iosMain/kotlin/$packagePath").apply { mkdirs() }
    }

    /**
     * Internal helper to write string content to a file.
     */
    private fun writeFile(directory: File, fileName: String, content: String) {
        File(directory, fileName).writeText(content)
    }
}
