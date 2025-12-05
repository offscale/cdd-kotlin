package scaffold

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Tests for [ScaffoldGenerator] ensuring directory structure and critical dependencies
 * are generated correctly.
 */
class ScaffoldGeneratorTest {

    @Test
    fun `generate creates all critical infrastructure files`(@TempDir tempDir: Path) {
        // Arrange
        val generator = ScaffoldGenerator()
        val projectName = "MyKmpApp"
        val packageName = "com.test.app"
        val outputDir = tempDir.toFile()

        // Act
        generator.generate(outputDir, projectName, packageName)

        // Assert - Root files
        assertTrue(File(outputDir, "build.gradle.kts").exists(), "Root build.gradle.kts missing")
        assertTrue(File(outputDir, "settings.gradle.kts").exists(), "settings.gradle.kts missing")
        assertTrue(File(outputDir, "gradle.properties").exists(), "gradle.properties missing")

        // Assert - Gradle Version Catalog
        val versionCatalog = File(outputDir, "gradle/libs.versions.toml")
        assertTrue(versionCatalog.exists(), "libs.versions.toml missing")

        // Assert - Module files
        val appDir = File(outputDir, "composeApp")
        assertTrue(File(appDir, "build.gradle.kts").exists(), "App build.gradle.kts missing")
        assertTrue(File(appDir, "src/androidMain/AndroidManifest.xml").exists(), "AndroidManifest.xml missing")

        // Assert - Source Code
        val commonMainParams = File(appDir, "src/commonMain/kotlin/com/test/app/App.kt")
        assertTrue(commonMainParams.exists(), "CommonMain App.kt missing")
    }

    @Test
    fun `generated build scripts contain correct dependencies`(@TempDir tempDir: Path) {
        // Arrange
        val generator = ScaffoldGenerator()
        val outputDir = tempDir.toFile()

        // Act
        generator.generate(outputDir, "DepTest", "com.dep.test")

        // Assert - Version Catalog content
        val catalogContent = File(outputDir, "gradle/libs.versions.toml").readText()
        assertTrue(catalogContent.contains("ktor = \"2.3.12\""), "Ktor version missing")
        assertTrue(catalogContent.contains("compose-plugin = \"1.6.11\""), "Compose version missing")
        assertTrue(catalogContent.contains("serialization = \"1.7.1\""), "Serialization version missing")

        // Assert - App Build Gradle Dependencies
        val appBuildGradle = File(outputDir, "composeApp/build.gradle.kts").readText()
        assertTrue(appBuildGradle.contains("implementation(libs.ktor.client.core)"), "Ktor Client Core dependency missing")
        assertTrue(appBuildGradle.contains("implementation(libs.kotlinx.serialization.json)"), "Serialization JSON dependency missing")
        assertTrue(appBuildGradle.contains("alias(libs.plugins.kotlinSerialization)"), "Serialization plugin missing")
    }

    @Test
    fun `generated kotlin source has correct package and imports`(@TempDir tempDir: Path) {
        // Arrange
        val generator = ScaffoldGenerator()
        val outputDir = tempDir.toFile()
        val packageName = "com.custom.pkg"

        // Act
        generator.generate(outputDir, "SourceTest", packageName)

        // Assert
        val appKt = File(outputDir, "composeApp/src/commonMain/kotlin/com/custom/pkg/App.kt")
        val content = appKt.readText()

        assertTrue(content.contains("package com.custom.pkg"), "Wrong package definition")
        assertTrue(content.contains("import kotlinx.serialization.Serializable"), "Missing Serialization import")
        assertTrue(content.contains("@Serializable"), "Missing Serializable annotation")
    }

    @Test
    fun `generate creates directories idempotently or overwrites`(@TempDir tempDir: Path) {
        // Arrange
        val generator = ScaffoldGenerator()
        val outputDir = tempDir.toFile()

        // Act - Run twice
        generator.generate(outputDir, "IdempotentTest", "com.test")
        generator.generate(outputDir, "IdempotentTest", "com.test")

        // Assert
        assertTrue(File(outputDir, "settings.gradle.kts").exists())
        assertEquals(
            "rootProject.name = \"IdempotentTest\"\ninclude(\":composeApp\")",
            File(outputDir, "settings.gradle.kts").readText()
        )
    }
}