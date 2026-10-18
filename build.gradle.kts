@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  kotlin("multiplatform") version "2.2.21"
  kotlin("plugin.serialization") version "2.2.21"
  id("org.jetbrains.kotlinx.kover") version "0.9.8"
  id("com.diffplug.spotless") version "7.0.4"
}

group = "org.cdd"

version = "0.0.4"

repositories { mavenCentral() }

kotlin {
  jvmToolchain(21)
  compilerOptions {
    allWarningsAsErrors.set(true)
    freeCompilerArgs.add("-Xexpect-actual-classes")
  }
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
        implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.21")
      }
    }
    val wasmWasiMain by getting { kotlin.srcDir("src/wasmMain/kotlin") }
    val wasmWasiTest by getting {
      kotlin.srcDir("src/wasmTest/kotlin")
      dependencies { implementation(kotlin("test")) }
    }
  }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

tasks.register("checkDocCoverage") {
  group = "verification"
  description =
      "Fails if files, public/expect/actual classes, interfaces, objects, enums, typealiases, functions, or function arguments lack KDoc."

  doLast {
    val allKotlinFiles =
        fileTree("src/commonMain/kotlin") { include("**/*.kt") } +
            fileTree("src/main/kotlin") { include("**/*.kt") } +
            fileTree("src/wasmMain/kotlin") { include("**/*.kt") } +
            fileTree("src/test/kotlin") { include("**/*.kt") } +
            fileTree("src/wasmTest/kotlin") { include("**/*.kt") }

    val sourceFiles =
        fileTree("src/commonMain/kotlin") { include("**/*.kt") } +
            fileTree("src/main/kotlin") { include("**/*.kt") } +
            fileTree("src/wasmMain/kotlin") { include("**/*.kt") }

    val missing = mutableListOf<String>()
    var coveredCount = 0

    allKotlinFiles.forEach { file ->
      val lines = file.readLines()
      val firstContentLine =
          lines.firstOrNull { it.trim().isNotEmpty() && !it.trim().startsWith("@file:") }?.trim()
      if (firstContentLine != null && firstContentLine.startsWith("/**")) {
        coveredCount++
      } else {
        missing.add("${file.path}:1: missing file-level KDoc header")
      }
    }

    sourceFiles.forEach { file ->
      val lines = file.readLines()
      var inRawString = false
      var inKDoc = false
      var lastKDocEnd = -1
      var lastKDocText = ""
      val currentKDocLines = mutableListOf<String>()

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
          currentKDocLines.clear()
        }
        if (inKDoc) {
          currentKDocLines.add(line)
        }
        if (inKDoc && line.contains("*/")) {
          inKDoc = false
          lastKDocEnd = index
          lastKDocText = currentKDocLines.joinToString("\n")
        }

        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEachIndexed

        val isPublicFunction =
            Regex("""^\s*(public\s+)?((expect|actual|suspend|inline|operator)\s+)*fun\s+""")
                .containsMatchIn(line) &&
                !Regex("""^\s*(private|internal|protected|override)\s+""").containsMatchIn(line)

        val isPublicClassOrTypealias =
            Regex(
                    """^\s*(public\s+)?((expect|actual|data|sealed|value|enum|annotation)\s+)*(class|interface|object|typealias)\s+""")
                .containsMatchIn(line) &&
                !Regex("""^\s*(private|internal|protected)\s+""").containsMatchIn(line)

        if (isPublicFunction || isPublicClassOrTypealias) {
          var hasKDoc = false
          var kdocBlock = lastKDocText
          if (line.contains("/**")) {
            hasKDoc = true
            kdocBlock = line
          } else {
            var j = index - 1
            while (j >= 0) {
              val back = lines[j].trim()
              if (back.isEmpty() || isAnnotation(back)) {
                j--
                continue
              }
              if (j == lastKDocEnd) {
                hasKDoc = true
              }
              break
            }
          }

          if (!hasKDoc) {
            missing.add("${file.path}:${index + 1}: ${trimmed}")
            return@forEachIndexed
          }
          coveredCount++

          if (isPublicFunction && !trimmed.startsWith("fun interface ")) {
            val sigBuilder = StringBuilder(line)
            var k = index + 1
            while (!sigBuilder.toString().contains(")") && k < lines.size) {
              sigBuilder.append(" ").append(lines[k].trim())
              k++
            }
            val sig = sigBuilder.toString()
            val paramMatch =
                Regex(
                        """fun\s+(?:<[^>]+>\s+)?(?:[A-Za-z0-9_.<>?]+\.)?[A-Za-z0-9_]+\s*\(([^)]*)\)""")
                    .find(sig)
            if (paramMatch != null) {
              val paramsStr = paramMatch.groupValues[1].trim()
              if (paramsStr.isNotEmpty()) {
                var depth = 0
                val parts = mutableListOf<String>()
                val cur = StringBuilder()
                for (ch in paramsStr) {
                  when (ch) {
                    '<',
                    '(' -> {
                      depth++
                      cur.append(ch)
                    }
                    '>',
                    ')' -> {
                      depth--
                      cur.append(ch)
                    }
                    ',' ->
                        if (depth == 0) {
                          parts.add(cur.toString().trim())
                          cur.clear()
                        } else {
                          cur.append(ch)
                        }
                    else -> cur.append(ch)
                  }
                }
                if (cur.isNotEmpty()) parts.add(cur.toString().trim())
                for (part in parts) {
                  val pm =
                      Regex(
                              """^(?:(?:vararg|noinline|crossinline|val|var)\s+)*([A-Za-z0-9_]+)\s*:""")
                          .find(part)
                  if (pm != null) {
                    val paramName = pm.groupValues[1]
                    if (!kdocBlock.contains("@param $paramName") &&
                        !kdocBlock.contains("[$paramName]")) {
                      missing.add(
                          "${file.path}:${index + 1}: missing @param $paramName on ${trimmed}")
                    } else {
                      coveredCount++
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    val total = coveredCount + missing.size
    val pct = if (total > 0) (coveredCount * 100) / total else 100
    val reportDir = file("build/reports/doc-coverage")
    reportDir.mkdirs()
    file("build/reports/doc-coverage/summary.txt")
        .writeText("covered=$coveredCount\nmissed=${missing.size}\npercentage=$pct\n")

    if (missing.isNotEmpty()) {
      throw GradleException(
          "KDoc coverage is below 100% ($pct%). Missing documentation:\n" +
              missing.joinToString("\n"))
    }
  }
}

kover {
  reports {
    verify {
      rule {
        minBound(100, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
        minBound(100, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.INSTRUCTION)
        minBound(100, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
      }
    }
  }
}

tasks.check {
  dependsOn("checkDocCoverage")
  dependsOn("koverVerify")
}

tasks.register<JavaExec>("run") {
  group = "application"
  mainClass.set("MainKt")
  classpath =
      files(
          kotlin.targets.getByName("jvm").compilations.getByName("main").output.allOutputs,
          kotlin.targets.getByName("jvm").compilations.getByName("main").compileDependencyFiles,
          kotlin.targets.getByName("jvm").compilations.getByName("main").runtimeDependencyFiles)

  if (project.hasProperty("args")) {
    args((project.property("args") as String).split(" "))
  }
}

spotless {
  kotlin {
    target("src/**/*.kt")
    targetExclude("build/**", "out/**", "bin/**")
    ktfmt()
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt()
  }
}
