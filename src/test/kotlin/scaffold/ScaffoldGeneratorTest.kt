/** Unit and integration tests in [ScaffoldGeneratorTest]. */
package scaffold

import domain.Components
import domain.Contact
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.HttpMethod
import domain.Info
import domain.OpenApiDefinition
import domain.OpenApiInt
import domain.OpenApiString
import domain.ParameterLocation
import domain.PathItem
import domain.SchemaDefinition
import domain.SchemaProperty
import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Tests for [ScaffoldGenerator] ensuring directory structure, critical dependencies, and metadata
 * injection are handled correctly.
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
    generator.generate(outputDir.absolutePath, projectName, packageName)

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
    assertTrue(
        File(appDir, "src/androidMain/AndroidManifest.xml").exists(), "AndroidManifest.xml missing")

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
    generator.generate(outputDir.absolutePath, "DepTest", "com.dep.test")

    // Assert - Version Catalog content
    val catalogContent = File(outputDir, "gradle/libs.versions.toml").readText()
    assertTrue(catalogContent.contains("ktor = \"2.3.12\""), "Ktor version missing")
    assertTrue(catalogContent.contains("compose-plugin = \"1.7.3\""), "Compose version missing")
    assertTrue(
        catalogContent.contains("serialization = \"1.7.1\""), "Serialization version missing")

    // Assert - App Build Gradle Dependencies
    val appBuildGradle = File(outputDir, "composeApp/build.gradle.kts").readText()
    assertTrue(
        appBuildGradle.contains("implementation(libs.ktor.client.core)"),
        "Ktor Client Core dependency missing")
    assertTrue(
        appBuildGradle.contains("implementation(libs.kotlinx.serialization.json)"),
        "Serialization JSON dependency missing")
    assertTrue(
        appBuildGradle.contains("alias(libs.plugins.kotlinSerialization)"),
        "Serialization plugin missing")
  }

  @Test
  fun `generated kotlin source has correct package and imports`(@TempDir tempDir: Path) {
    // Arrange
    val generator = ScaffoldGenerator()
    val outputDir = tempDir.toFile()
    val packageName = "com.custom.pkg"

    // Act
    generator.generate(outputDir.absolutePath, "SourceTest", packageName)

    // Assert
    val appKt = File(outputDir, "composeApp/src/commonMain/kotlin/com/custom/pkg/App.kt")
    val content = appKt.readText()

    assertTrue(content.contains("package com.custom.pkg"), "Wrong package definition")
    assertTrue(
        content.contains("import kotlinx.serialization.Serializable"),
        "Missing Serialization import")
    assertTrue(content.contains("@Serializable"), "Missing Serializable annotation")
  }

  @Test
  fun `generate creates directories idempotently or overwrites`(@TempDir tempDir: Path) {
    // Arrange
    val generator = ScaffoldGenerator()
    val outputDir = tempDir.toFile()

    // Act - Run twice
    generator.generate(outputDir.absolutePath, "IdempotentTest", "com.test")
    generator.generate(outputDir.absolutePath, "IdempotentTest", "com.test")

    // Assert
    assertTrue(File(outputDir, "settings.gradle.kts").exists())
    assertEquals(
        ScaffoldTemplates.createSettingsGradle("IdempotentTest"),
        File(outputDir, "settings.gradle.kts").readText())
  }

  @Test
  fun `generate populates gradle version from Info object`(@TempDir tempDir: Path) {
    // Arrange
    val generator = ScaffoldGenerator()
    val outputDir = tempDir.toFile()
    val info =
        Info(
            title = "Test App",
            version = "3.2.0-beta",
            contact = Contact(name = "Dev", email = "dev@test.com"))

    // Act
    generator.generate(outputDir.absolutePath, "VersionTest", "com.test.v", info)

    // Assert - Root build.gradle.kts
    val rootBuild = File(outputDir, "build.gradle.kts").readText()
    assertTrue(
        rootBuild.contains("version = \"3.2.0-beta\""), "Root project version not set correctly")

    // Assert - App build.gradle.kts (Android)
    val appBuild = File(outputDir, "composeApp/build.gradle.kts").readText()
    assertTrue(
        appBuild.contains("versionName = \"3.2.0-beta\""), "Android versionName not set correctly")
  }

  @Test
  fun `generate uses default version when Info is missing`(@TempDir tempDir: Path) {
    // Arrange
    val generator = ScaffoldGenerator()
    val outputDir = tempDir.toFile()

    // Act
    generator.generate(outputDir.absolutePath, "DefaultVersionTest", "com.test.d", null)

    // Assert
    val rootBuild = File(outputDir, "build.gradle.kts").readText()
    assertTrue(rootBuild.contains("version = \"1.0-SNAPSHOT\""), "Default root version missing")
    assertEquals(ScaffoldTemplates.createRootBuildGradle(null), rootBuild)

    val appBuild = File(outputDir, "composeApp/build.gradle.kts").readText()
    assertTrue(appBuild.contains("versionName = \"1.0\""), "Default Android versionName missing")
  }

  @Test
  fun `createAppBuildGradle gates Android on enableAndroid property and avoids test collisions`() {
    val appBuild = ScaffoldTemplates.createAppBuildGradle("org.example", Info("App", "1.0.0"))
    assertTrue(appBuild.contains("project.findProperty(\"enableAndroid\") == \"true\""))
    assertTrue(appBuild.contains("hasLocalSdkDir"))
    assertTrue(appBuild.contains("testDebugUnitTest"))
    assertTrue(appBuild.contains("testReleaseUnitTest"))
    assertTrue(appBuild.contains("dependsOn(\"desktopTest\")"))
  }

  @Test
  fun `createClientTest generates strict ContentNegotiation and live endpoint calls`() {
    val basic = ScaffoldTemplates.createClientTest("org.example")
    assertTrue(basic.contains("PETSTORE_BASE_URL"))
    assertTrue(basic.contains("PETSTORE_URL"))
    assertTrue(basic.contains("BASE_URL"))
    assertTrue(basic.contains("ignoreUnknownKeys = false"))
    assertTrue(basic.contains("isLenient = true"))
    assertTrue(basic.contains("encodeDefaults = true"))
    assertTrue(basic.contains("result.isSuccess"))
    assertFalse(basic.contains("McpServer"))

    val petstoreDef =
        OpenApiDefinition(
            info = Info(title = "Petstore", version = "1.0.0"),
            paths =
                mapOf(
                    "/pet/findByStatus" to
                        PathItem(
                            get =
                                EndpointDefinition(
                                    path = "/pet/findByStatus",
                                    method = HttpMethod.GET,
                                    operationId = "findPetsByStatus",
                                    parameters =
                                        listOf(
                                            EndpointParameter(
                                                name = "status",
                                                type = "String",
                                                location = ParameterLocation.QUERY,
                                                isRequired = false,
                                                schema =
                                                    SchemaProperty(
                                                        types = setOf("string"),
                                                        defaultValue = OpenApiString("available"))),
                                            EndpointParameter(
                                                name = "optLimit",
                                                type = "Int",
                                                location = ParameterLocation.QUERY,
                                                isRequired = false)),
                                    responses =
                                        mapOf(
                                            "200" to
                                                EndpointResponse(
                                                    statusCode = "200",
                                                    type = "List<Pet>",
                                                    description = "ok")))),
                    "/pet/{petId}" to
                        PathItem(
                            get =
                                EndpointDefinition(
                                    path = "/pet/{petId}",
                                    method = HttpMethod.GET,
                                    operationId = "getPetById",
                                    parameters =
                                        listOf(
                                            EndpointParameter(
                                                name = "petId",
                                                type = "Long",
                                                location = ParameterLocation.PATH,
                                                isRequired = true,
                                                schema =
                                                    SchemaProperty(
                                                        types = setOf("integer"),
                                                        format = "int64"))),
                                    responses =
                                        mapOf(
                                            "200" to
                                                EndpointResponse(
                                                    statusCode = "200",
                                                    type = null,
                                                    schema =
                                                        SchemaProperty(
                                                            ref = "#/components/schemas/Pet"),
                                                    description = "ok"))),
                            post =
                                EndpointDefinition(
                                    path = "/pet/{petId}",
                                    method = HttpMethod.POST,
                                    operationId = "updatePetWithForm",
                                    responses =
                                        mapOf("200" to EndpointResponse(statusCode = "200")))),
                    "/pet/findByTags" to
                        PathItem(
                            get =
                                EndpointDefinition(
                                    path = "/pet/findByTags",
                                    method = HttpMethod.GET,
                                    operationId = "findPetsByTags",
                                    deprecated = true,
                                    responses =
                                        mapOf("200" to EndpointResponse(statusCode = "200")))),
                    "/pet/errorOnly" to
                        PathItem(
                            get =
                                EndpointDefinition(
                                    path = "/pet/errorOnly",
                                    method = HttpMethod.GET,
                                    operationId = "errorOnly",
                                    responses =
                                        mapOf("400" to EndpointResponse(statusCode = "400")))),
                    "/pet/unsynth" to
                        PathItem(
                            get =
                                EndpointDefinition(
                                    path = "/pet/unsynth",
                                    method = HttpMethod.GET,
                                    operationId = "unsynthOp",
                                    parameters =
                                        listOf(
                                            EndpointParameter(
                                                name = "custom",
                                                type = "CustomFilter",
                                                location = ParameterLocation.QUERY,
                                                isRequired = true)),
                                    responses =
                                        mapOf("200" to EndpointResponse(statusCode = "200"))))),
            components =
                Components(
                    schemas = mapOf("Pet" to SchemaDefinition(name = "Pet", type = "object"))))

    val withDef = ScaffoldTemplates.createClientTest("org.example", petstoreDef, includeMcp = true)
    assertTrue(withDef.contains("import org.example.api.ApiClient"))
    assertTrue(withDef.contains("import org.example.mcp.McpServer"))
    assertTrue(withDef.contains("apiClient.findPetsByStatus(status = \"available\")"))
    assertTrue(withDef.contains("apiClient.getPetById(petId = 1L)"))
    assertTrue(withDef.contains("res0.isSuccess"))
    assertTrue(withDef.contains("res1.isSuccess"))

    val swaggerDef =
        OpenApiDefinition(
            info = Info(title = "SwaggerPetstore", version = "1.0.0"),
            paths =
                mapOf(
                    "/pet/findByStatus" to
                        PathItem(
                            get =
                                EndpointDefinition(
                                    path = "/pet/findByStatus",
                                    method = HttpMethod.GET,
                                    operationId = "findPetsByStatus",
                                    parameters =
                                        listOf(
                                            EndpointParameter(
                                                name = "status",
                                                type = "List<String>",
                                                location = ParameterLocation.QUERY,
                                                isRequired = true,
                                                schema =
                                                    SchemaProperty(
                                                        types = setOf("array"),
                                                        items =
                                                            SchemaProperty(
                                                                types = setOf("string"),
                                                                defaultValue =
                                                                    OpenApiString("available")))),
                                            EndpointParameter(
                                                name = "tags",
                                                type = "List<String>",
                                                location = ParameterLocation.QUERY,
                                                isRequired = true,
                                                schema =
                                                    SchemaProperty(
                                                        types = setOf("array"),
                                                        items =
                                                            SchemaProperty(
                                                                types = setOf("string"),
                                                                defaultValue = OpenApiInt(1L)))),
                                            EndpointParameter(
                                                name = "noItems",
                                                type = "List<String>",
                                                location = ParameterLocation.QUERY,
                                                isRequired = true,
                                                schema =
                                                    SchemaProperty(
                                                        types = setOf("array"),
                                                        items =
                                                            SchemaProperty(
                                                                types = setOf("string"),
                                                                defaultValue = null))),
                                            EndpointParameter(
                                                name = "noSchemaArr",
                                                type = "List<String>",
                                                location = ParameterLocation.QUERY,
                                                isRequired = true,
                                                schema = null),
                                            EndpointParameter(
                                                name = "page",
                                                type = "Int",
                                                location = ParameterLocation.QUERY,
                                                isRequired = true),
                                            EndpointParameter(
                                                name = "queryName",
                                                type = "String",
                                                location = ParameterLocation.QUERY,
                                                isRequired = true),
                                            EndpointParameter(
                                                name = "statusNoDef",
                                                type = "String",
                                                location = ParameterLocation.QUERY,
                                                isRequired = true,
                                                schema =
                                                    SchemaProperty(
                                                        types = setOf("string"),
                                                        defaultValue = OpenApiInt(1L)))),
                                    responses =
                                        mapOf(
                                            "200" to
                                                EndpointResponse(
                                                    statusCode = "200",
                                                    type = null,
                                                    schema = null,
                                                    description = "ok"))))),
            components = null)

    val swaggerTest =
        ScaffoldTemplates.createClientTestKt("org.example", swaggerDef, includeMcp = false)
    assertTrue(
        swaggerTest.contains(
            "apiClient.findPetsByStatus(status = listOf(\"available\"), tags = listOf(\"available\"), noItems = listOf(\"available\"), noSchemaArr = listOf(\"available\"), page = 1, queryName = \"1\", statusNoDef = \"available\")"),
        swaggerTest)
  }
}
