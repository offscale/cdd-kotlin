rootProject.name = "cdd-kotlin"
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// Toolchain resolver plugin removed to keep compatibility with older Gradle runtimes.
// If you need automatic toolchain downloads, re-enable the foojay-resolver plugin.
