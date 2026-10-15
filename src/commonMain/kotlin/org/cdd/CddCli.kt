/** Programmatic and CLI command dispatcher for the CDD (Compiler Driven Development) toolchain. */
package org.cdd

import generateToOpenApi
import getEnvVar
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import openapi.OpenApiDocument
import openapi.OpenApiParser
import openapi.text
import performSync
import readFile
import writeToFile

/** Provides a programmatic API for the CDD (Compiler Driven Development) toolchain. */
object CddCli {

  private val prettyJson = Json { prettyPrint = true }

  /**
   * Generates code from an OpenAPI specification.
   *
   * @param args Command-line arguments.
   * @param target The generation target (`to_sdk`, `to_sdk_cli`, or `to_server`).
   * @return Exit code (0 for success, 1 for error).
   */
  fun generateFromOpenApi(args: Array<String>, target: String = "to_sdk"): Int {
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

    val config =
        Config(
            inputPath = inputFile,
            outputDir = outputDir,
            noGithubActions = noGithubActions,
            noInstallablePackage = noInstallablePackage,
            tests = tests,
            inputDir = inputDir)
    val generationResult =
        if (target == "to_server") {
          CddGenerator.generateServer(config)
        } else {
          CddGenerator.generateSdk(config)
        }
    return generationResult.fold(
        onSuccess = { 0 },
        onFailure = { e ->
          println("Failed to generate code: ${e.message}")
          1
        })
  }

  /**
   * Generates an OpenAPI specification from Kotlin source code.
   *
   * @param args Command-line arguments.
   * @return Exit code (0 for success, 1 for error).
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

    return generateToOpenApi(inputFile, outputDir)
        .fold(
            onSuccess = { 0 },
            onFailure = { e ->
              println("Failed to generate OpenAPI: ${e.message}")
              1
            })
  }

  /**
   * Generates JSON documentation with Ktor client code snippets for an OpenAPI specification.
   *
   * @param args Command-line arguments.
   * @return Exit code (0 for success, 1 for error).
   */
  fun generateDocsJson(args: Array<String>): Int {
    var inputFile = getEnvVar("CDD_INPUT") ?: getEnvVar("INPUT") ?: ""
    var outputFile = getEnvVar("CDD_OUTPUT") ?: ""
    var noImports = getEnvVar("CDD_NO_IMPORTS") == "true" || getEnvVar("CDD_NO_IMPORTS") == "1"
    var noWrapping = getEnvVar("CDD_NO_WRAPPING") == "true" || getEnvVar("CDD_NO_WRAPPING") == "1"

    var i = 1
    while (i < args.size) {
      when (args[i]) {
        "-i",
        "--input" -> if (i + 1 < args.size) inputFile = args[++i]
        "-o",
        "--output" -> if (i + 1 < args.size) outputFile = args[++i]
        "--no-imports" -> noImports = true
        "--no-wrapping" -> noWrapping = true
      }
      i++
    }

    if (inputFile.isEmpty()) {
      println("Missing -i <spec.json>")
      return 1
    }

    val jsonStr =
        readFile(inputFile).getOrElse { e ->
          println("Failed to read file: ${e.message}")
          return 1
        }
    val parser = OpenApiParser()

    val result =
        parser.parseDocumentStringResult(jsonStr).getOrElse { e ->
          println("PARSE ERROR: " + e.message)
          return 1
        }

    val doc =
        when (result) {
          is OpenApiDocument.OpenApi -> result.definition
          else -> {
            println("Not an OpenAPI document")
            return 1
          }
        }

    val endpointsJson = buildJsonObject {
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

        val methodEntries = mutableMapOf<String, JsonPrimitive>()
        for ((methodName, operation) in methods) {
          if (operation == null) continue

          val sb = StringBuilder()
          if (!noImports) {
            sb.appendLine("import io.ktor.client.*")
            sb.appendLine("import io.ktor.client.request.*")
            sb.appendLine()
          }
          if (!noWrapping) {
            sb.appendLine("suspend fun main() {")
            sb.appendLine("    val client = HttpClient()")
          }

          val indent = if (noWrapping) "" else "    "
          sb.appendLine(
              """${indent}val response = client.${methodName}("https://api.example.com$path") {""")
          sb.appendLine("$indent    // Add parameters and body here")
          sb.appendLine("$indent}")
          sb.appendLine("${indent}println(response)")

          if (!noWrapping) {
            sb.appendLine("}")
          }

          methodEntries[methodName] = JsonPrimitive(sb.toString())
        }

        if (methodEntries.isNotEmpty()) {
          put(path, JsonObject(methodEntries))
        }
      }
    }

    val rootJson = buildJsonObject { put("endpoints", endpointsJson) }
    val formatted = prettyJson.encodeToString(JsonObject.serializer(), rootJson)
    if (outputFile.isNotEmpty()) {
      writeToFile(outputFile, formatted).getOrElse {
        return 1
      }
    }
    println(formatted)
    return 0
  }

  /**
   * Generates a demo scaffolding project.
   *
   * @param args Command-line arguments.
   * @return Exit code (0 for success, 1 for error).
   */
  fun generateDemo(args: Array<String>): Int {
    val pwd = getEnvVar("PWD")
    val outputDir =
        if (pwd != null && pwd.isNotEmpty()) "$pwd/generated-project" else "generated-project"
    return scaffold
        .ScaffoldGenerator()
        .generate(outputDir, "demo", "org.example")
        .fold(onSuccess = { 0 }, onFailure = { 1 })
  }

  /**
   * Configures and runs the CDD MCP Server via stdio.
   *
   * @param args Command-line arguments.
   * @return Exit code (0 for success).
   */
  fun runMcpServer(args: Array<String>): Int {
    val transport = org.cdd.mcp.StdioTransportImpl()
    createConfiguredMcpPeer(transport)
    transport.startReading()
    return 0
  }

  /**
   * Creates an [org.cdd.mcp.McpPeer] pre-registered with CDD tool, resource, and router handlers.
   *
   * @param transport The underlying MCP [org.cdd.mcp.Transport].
   * @return The configured [org.cdd.mcp.McpPeer].
   */
  fun createConfiguredMcpPeer(transport: org.cdd.mcp.Transport): org.cdd.mcp.McpPeer {
    val peer = org.cdd.mcp.McpPeer(transport)

    peer.onRequest("tools/cdd_generate") { params ->
      val spec = params.text("spec")
      val status =
          if (!spec.isNullOrBlank()) {
            val parsed = OpenApiParser().parseDocumentString(spec)
            if (parsed is OpenApiDocument.OpenApi) "generated" else "schema_only"
          } else {
            "ready"
          }
      buildJsonObject { put("status", JsonPrimitive(status)) }
    }

    peer.onRequest("tools/cdd_inspect_schema") { params ->
      val spec = params.text("spec")
      if (!spec.isNullOrBlank()) {
        val parsed = OpenApiParser().parseDocumentString(spec)
        if (parsed is OpenApiDocument.OpenApi) {
          val components = parsed.definition.components
          val schemas = if (components != null) components.schemas.keys else emptySet()
          buildJsonObject {
            put("title", JsonPrimitive(parsed.definition.info.title))
            put("schemas", buildJsonArray { schemas.forEach { add(JsonPrimitive(it)) } })
          }
        } else {
          buildJsonObject { put("type", JsonPrimitive("schema")) }
        }
      } else {
        buildJsonObject { put("schemas", buildJsonArray {}) }
      }
    }

    peer.onRequestResult("tools/cdd_sync_schema") { params ->
      val inputDir = params.text("inputDir")
      val truth = params.text("truth") ?: "class"
      if (!inputDir.isNullOrBlank()) {
        performSync(inputDir, truth).getOrElse {
          return@onRequestResult kotlin.Result.failure(it)
        }
      }
      kotlin.Result.success(
          buildJsonObject {
            put("synced", JsonPrimitive(true))
            put("truth", JsonPrimitive(truth))
          })
    }

    peer.onRequest("resources/ast_query") { params ->
      val query = params.text("query") ?: "*"
      buildJsonObject {
        put("query", JsonPrimitive(query))
        put("matches", buildJsonArray {})
      }
    }

    peer.onRequest("router/in_memory_generation") { params ->
      val spec = params.text("spec")
      val files =
          if (!spec.isNullOrBlank()) {
            val doc =
                (OpenApiParser().parseDocumentString(spec) as? OpenApiDocument.OpenApi)?.definition
            org.cdd.mcp.McpClientGenerator.generateMcpFiles("org.example", doc)
          } else {
            emptyMap()
          }
      buildJsonObject {
        put("files", buildJsonObject { files.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
      }
    }

    return peer
  }

  /**
   * Serves JSON-RPC via stdio.
   *
   * @param args Command-line arguments.
   * @return Exit code (0 for success).
   */
  fun serveJsonRpc(args: Array<String>): Int {
    val transport = org.cdd.mcp.StdioTransportImpl()
    org.cdd.mcp.McpPeer(transport)
    transport.startReading()
    return 0
  }
}
