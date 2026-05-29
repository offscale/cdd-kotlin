package org.cdd

import generateOpenApi
import getEnvVar
import openapi.OpenApiDocument
import openapi.OpenApiParser
import readFile

/** Provides a programmatic API for the CDD (Compiler Driven Development) toolchain. */
object CddCli {

  /**
   * Generates code from an OpenAPI specification.
   *
   * @param args Command-line arguments.
   * @return Exit code (0 for success).
   */
  fun generateFromOpenApi(args: Array<String>): Int {
    var outputDir = getEnvVar("CDD_OUTPUT") ?: "out"
    var inputFile = getEnvVar("CDD_INPUT") ?: ""
    var inputDir = getEnvVar("CDD_INPUT_DIR") ?: ""
    var noGithubActions =
        getEnvVar("CDD_NO_GITHUB_ACTIONS") == "true" || getEnvVar("CDD_NO_GITHUB_ACTIONS") == "1"
    var noInstallablePackage =
        getEnvVar("CDD_NO_INSTALLABLE_PACKAGE") == "true" ||
            getEnvVar("CDD_NO_INSTALLABLE_PACKAGE") == "1"
    var tests = getEnvVar("CDD_TESTS") == "true" || getEnvVar("CDD_TESTS") == "1"
    var i = 1
    while (i < args.size) {
      val arg = args[i]
      if (arg == "-o" || arg == "--output") {
        if (i + 1 < args.size) outputDir = args[++i]
      } else if (arg == "-i" || arg == "--input") {
        if (i + 1 < args.size) inputFile = args[++i]
      } else if (arg == "--input-dir") {
        if (i + 1 < args.size) inputDir = args[++i]
      } else if (arg == "--no-github-actions") {
        noGithubActions = true
      } else if (arg == "--no-installable-package") {
        noInstallablePackage = true
      } else if (arg == "--tests") {
        tests = true
      }
      i++
    }

    if (inputFile.isEmpty() && inputDir.isEmpty()) {
      println("Missing -i <spec.json> or --input-dir <specs_dir>")
      return 1
    }

    try {
      val config =
          Config(
              inputPath = inputFile,
              outputDir = outputDir,
              noGithubActions = noGithubActions,
              noInstallablePackage = noInstallablePackage,
              tests = tests,
              inputDir = inputDir)
      CddGenerator.generateSdk(config)
    } catch (e: Exception) {
      println("Failed to generate SDK:")
      e.printStackTrace()
      return 1
    }
    return 0
  }

  /**
   * Generates an OpenAPI specification from source code.
   *
   * @param args Command-line arguments.
   * @return Exit code (0 for success).
   */
  fun generateToOpenApi(args: Array<String>): Int {
    var outputDir = getEnvVar("CDD_OUTPUT") ?: "out.json"
    var inputFile = getEnvVar("CDD_INPUT") ?: ""
    var i = 1
    while (i < args.size) {
      val arg = args[i]
      if (arg == "-o" || arg == "--output") {
        if (i + 1 < args.size) outputDir = args[++i]
      } else if (arg == "-i" || arg == "--input") {
        if (i + 1 < args.size) inputFile = args[++i]
      }
      i++
    }

    if (inputFile.isEmpty()) {
      println("Missing -i <src>")
      return 1
    }

    try {
      generateOpenApi(inputFile, outputDir)
    } catch (e: Exception) {
      println("Failed to generate OpenAPI:")
      e.printStackTrace()
      return 1
    }
    return 0
  }

  /**
   * Generates JSON documentation with code snippets for an OpenAPI specification.
   *
   * @param args Command-line arguments.
   * @return Exit code (0 for success).
   */
  fun generateDocsJson(args: Array<String>): Int {
    var inputFile = getEnvVar("CDD_INPUT") ?: ""
    var noImports = getEnvVar("CDD_NO_IMPORTS") == "true" || getEnvVar("CDD_NO_IMPORTS") == "1"
    var noWrapping = getEnvVar("CDD_NO_WRAPPING") == "true" || getEnvVar("CDD_NO_WRAPPING") == "1"

    var i = 1
    while (i < args.size) {
      when (args[i]) {
        "-i",
        "--input" -> if (i + 1 < args.size) inputFile = args[++i]
        "--no-imports" -> noImports = true
        "--no-wrapping" -> noWrapping = true
      }
      i++
    }

    if (inputFile.isEmpty()) {
      println("Missing -i <spec.json>")
      return 1
    }

    val jsonStr = readFile(inputFile)
    val parser = OpenApiParser()

    val result =
        try {
          parser.parseDocumentString(jsonStr)
        } catch (e: Throwable) {
          println("PARSE ERROR: " + e.message)
          throw e
        }

    val doc =
        when (result) {
          is OpenApiDocument.OpenApi -> result.definition
          else -> {
            println("Not an OpenAPI document")
            return 1
          }
        }

    val endpoints = mutableMapOf<String, Map<String, String>>()

    for ((path, pathItem) in doc.paths) {
      val methods =
          mapOf(
              "get" to pathItem.get,
              "put" to pathItem.put,
              "post" to pathItem.post,
              "delete" to pathItem.delete,
              "options" to pathItem.options,
              "head" to pathItem.head,
              "patch" to pathItem.patch,
              "trace" to pathItem.trace)

      val methodMap = mutableMapOf<String, String>()

      for ((methodName, operation) in methods) {
        if (operation == null) continue

        val opId =
            if (!operation.operationIdExplicit) {
              "${methodName}${path.replace("/", "").replace("{", "").replace("}", "")}"
            } else {
              operation.operationId
            }

        val sb = StringBuilder()

        if (!noImports) {
          sb.append("import io.ktor.client.*\nimport io.ktor.client.request.*\n\n")
        }

        if (!noWrapping) {
          sb.append("suspend fun main() {\n    val client = HttpClient()\n")
        }

        val indent = if (noWrapping) "" else "    "
        sb.append(
            "${indent}val response = client.${methodName}(\"https://api.example.com$path\") {\n")
        sb.append("$indent    // Add parameters and body here\n")
        sb.append("$indent}\n")
        sb.append("${indent}println(response)\n")

        if (!noWrapping) {
          sb.append("}\n")
        }

        methodMap[methodName] = sb.toString()
      }

      if (methodMap.isNotEmpty()) {
        endpoints[path] = methodMap
      }
    }

    val sb = StringBuilder()
    sb.append("{\n  \"endpoints\": {\n")
    var pathIndex = 0
    for ((path, methods) in endpoints) {
      sb.append("    \"$path\": {\n")
      var methodIndex = 0
      for ((methodName, code) in methods) {
        val escapedCode = code.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        sb.append("      \"$methodName\": \"$escapedCode\"")
        if (methodIndex < methods.size - 1) sb.append(",")
        sb.append("\n")
        methodIndex++
      }
      sb.append("    }")
      if (pathIndex < endpoints.size - 1) sb.append(",")
      sb.append("\n")
      pathIndex++
    }
    sb.append("  }\n}")
    println(sb.toString())
    return 0
  }

  /**
   * Generates a demo scaffolding project.
   *
   * @param args Command-line arguments.
   * @return Exit code (0 for success).
   */
  fun generateDemo(args: Array<String>): Int {
    return try {
      val outputDir = getEnvVar("PWD")?.let { "$it/generated-project" } ?: "generated-project"
      scaffold.ScaffoldGenerator().generate(outputDir, "demo", "org.example")
      0
    } catch (e: Exception) {
      1
    }
  }
}
