package scaffold

import domain.Info

/**
 * Object containing string templates for the KMP project structure. These templates define the
 * Gradle configuration and initial Kotlin definitions.
 */
object ScaffoldTemplates {

  /**
   * Generates the content for gradle/libs.versions.toml. Defines versions for Kotlin, Compose,
   * Ktor, and Serialization.
   */
  fun createVersionCatalog(isServer: Boolean = false): String {
    val serverVersions =
        if (isServer)
            """
        exposed = "0.53.0"
        faker = "1.15.0"
        sqlite = "3.46.0.0"
        postgresql = "42.7.3"
        cli = "4.2.2"
"""
        else ""

    val serverLibs =
        if (isServer)
            """
        # Server dependencies
        ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
        ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
        ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
        ktor-server-cors = { module = "io.ktor:ktor-server-cors", version.ref = "ktor" }
        ktor-server-test-host = { module = "io.ktor:ktor-server-test-host", version.ref = "ktor" }
        
        # ORM & DB
        exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
        exposed-dao = { module = "org.jetbrains.exposed:exposed-dao", version.ref = "exposed" }
        exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
        sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqlite" }
        postgresql = { module = "org.postgresql:postgresql", version.ref = "postgresql" }
        
        # Faker & CLI
        faker = { module = "io.github.serpro69:kotlin-faker", version.ref = "faker" }
        clikt = { module = "com.github.ajalt.clikt:clikt", version.ref = "cli" }
"""
        else ""

    return """
        [versions]
        agp = "8.8.0"
        android-compileSdk = "34"
        android-minSdk = "24"
        android-targetSdk = "34"
        androidx-activityCompose = "1.9.0"
        androidx-core-ktx = "1.13.1"
        compose-plugin = "1.7.3"
        kotlin = "2.2.21"
        ktor = "2.3.12"
        coroutines = "1.8.1"
        serialization = "1.7.1"
        datetime = "0.6.0"
$serverVersions
        [libraries]
        androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "androidx-core-ktx" }
        androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activityCompose" }
        kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
        kotlinx-coroutines-swing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "coroutines" }
        kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
        
        # Ktor
        ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
        ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
        ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
        ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
        ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
        ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
        
        # Serialization
        kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
$serverLibs
        [plugins]
        androidApplication = { id = "com.android.application", version.ref = "agp" }
        androidLibrary = { id = "com.android.library", version.ref = "agp" }
        jetbrainsCompose = { id = "org.jetbrains.compose", version.ref = "compose-plugin" }
        composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
        kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
        kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
    """
        .trimIndent()
  }

  /**
   * Generates the root settings.gradle.kts file.
   *
   * @param projectName The name of the project to include.
   */
  fun createSettingsGradle(projectName: String, isServer: Boolean = false): String {
    val includes = if (isServer) "include(\":server\")" else "include(\":composeApp\")"
    return """
        pluginManagement {
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
                maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
            }
        }
        
        dependencyResolutionManagement {
            repositories {
                google()
                mavenCentral()
                maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
            }
        }

        rootProject.name = "$projectName"
        $includes
    """
        .trimIndent()
  }

  /**
   * Generates the root build.gradle.kts file. Sets up the plugins for the overall project context.
   *
   * @param info Optional Info metadata to populate version.
   */
  fun createRootBuildGradle(info: Info?): String {
    val versionLine =
        if (info != null) "version = \"${info.version}\"" else "version = \"1.0-SNAPSHOT\""

    return """
        plugins {
            // this is necessary to avoid the plugins to be loaded multiple times
            // in each subproject's classloader
            alias(libs.plugins.androidApplication) apply false
            alias(libs.plugins.androidLibrary) apply false
            alias(libs.plugins.jetbrainsCompose) apply false
            alias(libs.plugins.composeCompiler) apply false
            alias(libs.plugins.kotlinMultiplatform) apply false
            alias(libs.plugins.kotlinSerialization) apply false
        }

        group = "com.example"
        $versionLine
    """
        .trimIndent()
  }

  /** Generates the gradle.properties file used for JVM arguments. */
  fun createGradleProperties(): String =
      """
        org.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8
        kotlin.daemon.jvmargs=-Xmx6g
        kotlin.code.style=official
        android.useAndroidX=true
        kotlin.mpp.androidGradlePluginCompatibility.nowarn=true
        kotlin.mpp.applyDefaultHierarchyTemplate=false
    """
          .trimIndent()

  /**
   * Generates the module-level build.gradle.kts for the 'composeApp'. Configures SourceSets for
   * Android, iOS, and Desktop. Configures Ktor and Serialization dependencies.
   *
   * @param namespace The Android namespace (package name).
   * @param info Optional Info metadata to populate versionName.
   */
  fun createAppBuildGradle(namespace: String, info: Info?): String {
    val versionNameStr = if (info != null && info.version.isNotEmpty()) info.version else "1.0"

    return """
        plugins {
            alias(libs.plugins.kotlinMultiplatform)
            alias(libs.plugins.androidApplication)
            alias(libs.plugins.jetbrainsCompose)
            alias(libs.plugins.composeCompiler)
            alias(libs.plugins.kotlinSerialization)
        }

        kotlin {
            androidTarget {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                }
            }
            
            jvm("desktop")
            
            listOf(
                iosX64(),
                iosArm64(),
                iosSimulatorArm64()
            ).forEach { iosTarget ->
                iosTarget.binaries.framework {
                    baseName = "ComposeApp"
                    isStatic = true
                }
            }

            sourceSets {
                val commonMain by getting {
                    dependencies {
                        implementation(compose.runtime)
                        implementation(compose.foundation)
                        implementation(compose.material)
                        implementation(compose.ui)
                        implementation(compose.components.resources)
                        
                        implementation(libs.kotlinx.coroutines.core)
                        implementation(libs.kotlinx.serialization.json)
                        implementation(libs.kotlinx.datetime)
                        
                        // Ktor
                        implementation(libs.ktor.client.core)
                        implementation(libs.ktor.client.content.negotiation)
                        implementation(libs.ktor.serialization.json)
                    }
                }
                
                val commonTest by getting {
                    dependencies {
                        implementation(kotlin("test"))
                        implementation(kotlin("test-common"))
                        implementation(kotlin("test-annotations-common"))
                    }
                }
                
                val androidMain by getting {
                    dependencies {
                        implementation(libs.androidx.activity.compose)
                        implementation(libs.ktor.client.okhttp)
                        implementation(libs.kotlinx.coroutines.core)
                    }
                }
                
                val desktopMain by getting {
                    dependencies {
                        implementation(compose.desktop.currentOs)
                        implementation(libs.kotlinx.coroutines.swing)
                        implementation(libs.ktor.client.okhttp) // Use OkHttp or Apache for Desktop
                    }
                }
                
                val iosMain by creating {
                    dependsOn(commonMain)
                    dependencies {
                         implementation(libs.ktor.client.darwin)
                    }
                }
            }
        }

        android {
            namespace = "$namespace"
            compileSdk = libs.versions.android.compileSdk.get().toInt()

            defaultConfig {
                applicationId = "$namespace"
                minSdk = libs.versions.android.minSdk.get().toInt()
                targetSdk = libs.versions.android.targetSdk.get().toInt()
                versionCode = 1
                versionName = "$versionNameStr"
            }
            packaging {
                resources {
                    excludes += "/META-INF/{AL2.0,LGPL2.1}"
                }
            }
            buildTypes {
                getByName("release") {
                    isMinifyEnabled = false
                }
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
        }
    """
        .trimIndent()
  }

  /** Generates a basic AndroidManifest.xml required for the Android target. */
  fun createAndroidManifest(): String =
      """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application
                android:allowBackup="true"
                android:icon="@android:drawable/ic_menu_compass"
                android:label="ComposeApp"
                android:theme="@android:style/Theme.Material.Light.NoActionBar">
                <activity
                    android:name=".MainActivity"
                    android:exported="true"
                    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|mnc|colorMode|density|fontScale|layoutDirection|locale|smallestScreenSize|uiMode"
                    android:theme="@android:style/Theme.Material.Light.NoActionBar">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>
        </manifest>
    """
          .trimIndent()

  /**
   * Generates a placeholder App.kt file in commonMain to ensure compilation.
   *
   * @param packageName The package definition.
   */
  fun createCommonAppKt(packageName: String): String =
      """
        package $packageName

        import androidx.compose.material.MaterialTheme
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        import kotlinx.serialization.Serializable
        
        @Serializable
        data class ExampleDto(val name: String)

        @Composable
        fun App() {
            MaterialTheme {
                Text("Hello from KMP Auto-Admin Scaffold")
            }
        }
    """
          .trimIndent()

  /**
   * Generates the module-level build.gradle.kts for the 'server' module.
   *
   * @param namespace The package name.
   * @param info Optional Info metadata.
   */
  fun createServerBuildGradle(namespace: String, info: Info?, withTests: Boolean = false): String {
    val versionNameStr = if (info != null && info.version.isNotEmpty()) info.version else "1.0"

    val testBlock =
        if (withTests)
            """
                val jvmTest by getting {
                    kotlin.srcDir("src/test/kotlin")
                    dependencies {
                        implementation(kotlin("test"))
                        implementation(libs.ktor.server.test.host)
                    }
                }
    """
        else ""

    return """
        plugins {
            alias(libs.plugins.kotlinMultiplatform)
            alias(libs.plugins.kotlinSerialization)
        }

        kotlin {
            jvm {
                mainRun {
                    mainClass.set("$namespace.MainKt")
                }
            }

            sourceSets {
                val jvmMain by getting {
                    kotlin.srcDir("src/main/kotlin")
                    dependencies {
                        implementation(libs.kotlinx.coroutines.core)
                        implementation(libs.kotlinx.serialization.json)
                        implementation(libs.kotlinx.datetime)
                        
                        implementation(libs.ktor.server.core)
                        implementation(libs.ktor.server.netty)
                        implementation(libs.ktor.server.content.negotiation)
                        implementation(libs.ktor.server.cors)
                        implementation(libs.ktor.serialization.json)
                        implementation(libs.ktor.client.core)
                        implementation(libs.ktor.client.cio)
                        
                        implementation(libs.exposed.core)
                        implementation(libs.exposed.dao)
                        implementation(libs.exposed.jdbc)
                        implementation(libs.sqlite.jdbc)
                        implementation(libs.postgresql)
                        
                        implementation(libs.faker)
                        implementation(libs.clikt)
                    }
                }
                $testBlock
            }
        }
        
        tasks.withType<JavaExec> {
            maxHeapSize = "4g"
        }
    """
        .trimIndent()
  }
}
