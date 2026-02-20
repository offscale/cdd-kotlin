rootProject.name = "cdd-kotlin" 
pluginManagement { 
    repositories { 
        mavenCentral() 
        gradlePluginPortal() 
    } 
} 

plugins { 
    // Re-enabled Toolchain resolver to automatically download requested JDK 21 on runners
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" 
}