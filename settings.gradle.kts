rootProject.name = "cdd-kotlin"
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // This plugin fixes the "toolchain download repositories have not been configured" error
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
