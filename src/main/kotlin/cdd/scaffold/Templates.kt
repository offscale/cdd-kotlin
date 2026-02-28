package cdd.scaffold

import cdd.openapi.*
import cdd.classes.*
import cdd.routes.*
import cdd.docstrings.*
import cdd.shared.*
import cdd.scaffold.*



/**
 * Object containing string templates for the KMP project structure.
 * These templates define the Gradle configuration and initial Kotlin definitions.
 */
/** Auto generated docs */
object ScaffoldTemplates {

    /** Auto generated docs */

    fun createVersionCatalog(): String = """
        [versions]
        agp = "8.2.2"
        android-compileSdk = "34"
        android-minSdk = "24"
        android-targetSdk = "34"
        androidx-activityCompose = "1.9.0"
        androidx-core-ktx = "1.13.1"
        compose-plugin = "1.6.11"
        kotlin = "2.0.0"
        ktor = "2.3.12"
        coroutines = "1.8.1"
        serialization = "1.7.1"

        [libraries]
        androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "androidx-core-ktx" }
        androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activityCompose" }
        kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
        kotlinx-coroutines-swing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "coroutines" }
        
        # Ktor
        ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
        ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
        ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
        ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
        ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
        
        # Serialization
        kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }

        [plugins]
        androidApplication = { id = "com.android.application", version.ref = "agp" }
        androidLibrary = { id = "com.android.library", version.ref = "agp" }
        jetbrainsCompose = { id = "org.jetbrains.compose", version.ref = "compose-plugin" }
        composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
        kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
        kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
    """.trimIndent()

    /** Auto generated docs */

    fun createSettingsGradle(projectName: String): String = """
        pluginManagement {
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }
        dependencyResolutionManagement {
            repositories {
                google()
                mavenCentral()
            }
        }
        rootProject.name = "$projectName"
        include(":composeApp")
    """.trimIndent()

    /** Auto generated docs */

    fun createRootBuildGradle(info: Info? = null): String {
        val versionLine = if (info != null) "version = \"${info.version}\"" else "version = \"1.0-SNAPSHOT\""

        return """
        plugins {
            alias(libs.plugins.androidApplication) apply false
            alias(libs.plugins.androidLibrary) apply false
            alias(libs.plugins.jetbrainsCompose) apply false
            alias(libs.plugins.composeCompiler) apply false
            alias(libs.plugins.kotlinMultiplatform) apply false
            alias(libs.plugins.kotlinSerialization) apply false
        }

        group = "com.example"
        $versionLine
    """.trimIndent()
    }

    /** Auto generated docs */

    fun createGradleProperties(): String = """
        org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
        kotlin.code.style=official
        android.useAndroidX=true
    """.trimIndent()

    /** Auto generated docs */

    fun createAppBuildGradle(namespace: String, info: Info? = null): String {
        val versionNameStr = info?.version ?: "1.0"

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
                compilations.all {
                    kotlinOptions {
                        jvmTarget = "1.8"
                    }
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
                commonMain.dependencies {
                    implementation(compose.runtime)
                    implementation(compose.foundation)
                    implementation(compose.material)
                    implementation(compose.ui)
                    implementation(compose.components.resources)
                    
                    implementation(libs.kotlinx.coroutines.core)
                    implementation(libs.kotlinx.serialization.json)
                    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                    
                    implementation(libs.ktor.client.core)
                    implementation(libs.ktor.client.content.negotiation)
                    implementation(libs.ktor.serialization.json)
                }
                
                androidMain.dependencies {
                    implementation(libs.androidx.activity.compose)
                    implementation(libs.ktor.client.okhttp)
                    implementation(libs.kotlinx.coroutines.core)
                }
                
                jvm("desktop").compilations["main"].defaultSourceSet.dependencies {
                    implementation(compose.desktop.currentOs)
                    implementation(libs.kotlinx.coroutines.swing)
                    implementation(libs.ktor.client.okhttp)
                }
                
                iosMain.dependencies {
                    implementation(libs.ktor.client.darwin)
                }
                
                commonTest.dependencies {
                    implementation(kotlin("test"))
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
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
    """.trimIndent()
    }

    /** Auto generated docs */

    fun createAndroidManifest(): String = """
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
    """.trimIndent()

    /** Auto generated docs */

    fun createCommonAppKt(packageName: String): String = """
        package $packageName

        import androidx.compose.material.MaterialTheme
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        import kotlinx.serialization.Serializable
        
        @Serializable
        data class ExampleDto(val name: String)

        class ApiException(message: String) : Exception(message)

        @Composable
        /** Auto generated docs */
        fun App() {
            MaterialTheme {
                Text("Hello from KMP Auto-Admin Scaffold")
            }
        }
    """.trimIndent()
}
