plugins {
    kotlin("jvm") version "2.2.21"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

group = "org.cdd"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // The Kotlin Standard Library
    implementation(kotlin("stdlib"))

    // REQUIRED FOR NEXT STEPS: The Kotin Compiler PSI (Embeddable)
    // We will use this in Feature D-01/D-02 to parse code
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")

    // OpenAPI JSON/YAML parsing (tree model)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(23) // Use the locally installed JDK; 17+ required
}

// Ensure all public classes and functions have KDoc.
tasks.register("checkDocCoverage") {
    group = "verification"
    description = "Fails if public classes or functions lack KDoc."

    doLast {
        val sourceFiles = fileTree("src/main/kotlin") {
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
