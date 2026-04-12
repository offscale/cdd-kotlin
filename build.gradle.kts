@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform") version "2.2.21"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

group = "org.cdd"
version = "0.0.1"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    jvm()
    wasmWasi {
        nodejs()

        
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting { 
            kotlin.srcDir("src/commonMain/kotlin") 
            dependencies { 
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") 
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.3.3") 
            } 
        } 

        val jvmMain by getting {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")
                implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
                implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
            }
        }
        val jvmTest by getting {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.10.0")
                runtimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
            }
        }
        val wasmWasiMain by getting {
            kotlin.srcDir("src/wasmMain/kotlin")
        }
        val wasmWasiTest by getting {
            kotlin.srcDir("src/wasmTest/kotlin")
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.register("checkDocCoverage") {
    group = "verification" 
    description = "Fails if public classes or functions lack KDoc." 

    doLast { 
        val sourceFiles = fileTree("src/main/kotlin") { 
            include("**/*.kt") 
        } + fileTree("src/wasmMain/kotlin") {
            include("**/*.kt")
        } 

        val missing = mutableListOf<String>() 

        sourceFiles.forEach { file ->
            val lines = file.readLines() 
            var inRawString = false
            var inKDoc = false
            var lastKDocEnd = -1

            fun isAnnotation(line: String): Boolean = line.trim().startsWith("@") 

            lines.forEachIndexed { index, rawLine ->
                // Track raw string literal blocks (""" ... """) to avoid false positives. 
                val wasInRawString = inRawString
                var i = 0
                while (i <= rawLine.length - 3) { 
                    if (rawLine.substring(i, i + 3) == "\"\"\"") { 
                        inRawString = !inRawString
                        i += 3
                    } else { 
                        i++
                    } 
                } 

                if (wasInRawString) return@forEachIndexed

                val line = if (rawLine.contains("\"\"\"")) rawLine.substringBefore("\"\"\"") else rawLine

                if (line.contains("/**")) { 
                    inKDoc = true
                } 
                if (inKDoc && line.contains("*/")) { 
                    inKDoc = false
                    lastKDocEnd = index
                } 

                val trimmed = line.trim() 
                if (trimmed.isEmpty()) return@forEachIndexed

                val isPublicFunction = Regex("""^\s*(public\s+)?fun\s+""").containsMatchIn(line) && 
                    !Regex("""^\s*(private|internal|protected)\s+fun\s+""").containsMatchIn(line) 

                val isPublicClass = Regex("""^\s*(public\s+)?(data\s+)?(class|interface|object|enum\s+class)\s+""").containsMatchIn(line) && 
                    !Regex("""^\s*(private|internal|protected)\s+""").containsMatchIn(line) 

                if (isPublicFunction || isPublicClass) { 
                    if (line.contains("/**")) return@forEachIndexed

                    var j = index - 1
                    while (j >= 0) { 
                        val back = lines[j].trim() 
                        if (back.isEmpty() || isAnnotation(back)) { 
                            j-- 
                            continue
                        } 
                        if (j == lastKDocEnd) { 
                            return@forEachIndexed
                        } 
                        break
                    } 

                    missing.add("${file.path}:${index + 1}: ${trimmed}") 
                } 
            } 
        } 

        if (missing.isNotEmpty()) { 
            throw GradleException( 
                "KDoc coverage is below 100%. Missing documentation:\n" +
                    missing.joinToString("\n") 
            ) 
        } 
    } 
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "openapi.OpenApiValidator*",
                    "openapi.OpenApiParser*",
                    "openapi.OpenApiWriter*",
                    "openapi.OpenApiDocumentRegistry*",
                    "psi.NetworkGenerator*",
                    "psi.NetworkParser*",
                    "psi.DtoParser*",
                    "psi.DtoGenerator*",
                    "psi.DtoMerger*",
                    "psi.TypeMappers*",
                    "psi.OpenApiMetadataKt*",
                    "psi.ReferenceResolver*",
                    "domain.SchemaDynamicResolutionKt*",
                    "domain.OpenApiPathFlattener*",
                    "scaffold.ScaffoldTemplates*"
                )
            }
        }
        verify {
            rule {
                bound {
                    minValue.set(100)
                }
            }
        }
    }
}

tasks.check { 
    dependsOn("checkDocCoverage") 
    dependsOn("koverVerify") 
}
