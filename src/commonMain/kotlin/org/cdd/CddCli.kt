/** Programmatic and CLI command dispatcher for the CDD (Compiler Driven Development) toolchain. */
package org.cdd

import generateToArazzo
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
    val flags = CliArgumentParser.parse(args, defaultOutput = "out")
    if (flags.input.isEmpty() && flags.inputDir.isEmpty()) {
      println("Missing -i <spec.json> or --input-dir <specs_dir>")
      return 1
    }

    val config =
        Config(
            inputPath = flags.input,
            outputDir = flags.output,
            noGithubActions = flags.noGithubActions,
            noInstallablePackage = flags.noInstallablePackage,
            tests = flags.tests,
            inputDir = flags.inputDir)
    val generationResult =
        if (target == "to_server") {
          CddGenerator.generateServer(config)
        } else {
          CddGenerator.generateSdk(config, includeCli = target == "to_sdk_cli")
        }
    return generationResult.fold(
        onSuccess = { 0 },
        onFailure = { e ->
          println("Failed to generate code: ${e.message}")
          1
        })
  }

  /**
   * Generates Client SDK, Client SDK CLI, or Server code from an Arazzo 1.1.0 specification.
   *
   * @param args Command-line arguments.
   * @param target The generation target (`to_sdk`, `to_sdk_cli`, or `to_server`).
   * @return Exit code (0 for success, 1 for error).
   */
  fun generateFromArazzo(args: Array<String>, target: String = "to_sdk"): Int {
    val flags = CliArgumentParser.parse(args, defaultOutput = "out")
    if (flags.input.isEmpty()) {
      println("Missing -i <arazzo.json|yaml>")
      return 1
    }
    val config =
        Config(
            inputPath = flags.input,
            outputDir = flags.output,
            noGithubActions = flags.noGithubActions,
            noInstallablePackage = flags.noInstallablePackage,
            tests = flags.tests)
    val generationResult =
        if (target == "to_server") {
          CddGenerator.generateArazzoServer(config)
        } else {
          CddGenerator.generateArazzoSdk(config, includeCli = target == "to_sdk_cli")
        }
    return generationResult.fold(
        onSuccess = { 0 },
        onFailure = { e ->
          println("Failed to generate from Arazzo: ${e.message}")
          1
        })
  }

  /**
   * Generates an Arazzo 1.1.0 specification from Kotlin workflow source code.
   *
   * @param args Command-line arguments.
   * @return Exit code (0 for success, 1 for error).
   */
  fun generateToArazzo(args: Array<String>): Int {
    val flags = CliArgumentParser.parse(args, defaultOutput = "arazzo.json")
    if (flags.input.isEmpty()) {
      println("Missing -i <src>")
      return 1
    }
    return generateToArazzo(flags.input, flags.output)
        .fold(
            onSuccess = { 0 },
            onFailure = { e ->
              println("Failed to generate Arazzo: ${e.message}")
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
    val flags = CliArgumentParser.parse(args, defaultOutput = "out.json")
    if (flags.input.isEmpty()) {
      println("Missing -i <src>")
      return 1
    }

    return generateToOpenApi(flags.input, flags.output)
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
    val flags = CliArgumentParser.parse(args, defaultOutput = "")
    val inputFile = flags.input.ifEmpty { getEnvVar("INPUT") ?: "" }
    val outputFile = flags.output
    val noImports = flags.noImports
    val noWrapping = flags.noWrapping

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
    val cwd = getEnvVar("user.dir")
    val defaultOutput =
        getEnvVar("CDD_OUTPUT")
            ?: if (cwd != null && cwd.isNotEmpty()) "$cwd/generated-project"
            else "generated-project"
    val outputDir = CliArgumentParser.parse(args, defaultOutput = defaultOutput).output
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
      val generatedFiles =
          if (!spec.isNullOrBlank()) {
            val parsed = OpenApiParser().parseDocumentString(spec)
            if (parsed is OpenApiDocument.OpenApi) {
              org.cdd.mcp.McpClientGenerator.generateMcpFiles("org.example", parsed.definition)
            } else {
              emptyMap()
            }
          } else {
            emptyMap()
          }
      val status =
          when {
            spec.isNullOrBlank() -> "ready"
            generatedFiles.isNotEmpty() -> "generated"
            else -> "schema_only"
          }
      buildJsonObject {
        put("status", JsonPrimitive(status))
        put("fileCount", JsonPrimitive(generatedFiles.size))
      }
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
      val spec = params.text("spec")
      val matches =
          if (!spec.isNullOrBlank()) {
            val parsed = OpenApiParser().parseDocumentString(spec)
            val schemaNames =
                if (parsed is OpenApiDocument.OpenApi) {
                  val components = parsed.definition.components
                  if (components != null) components.schemas.keys else emptySet()
                } else {
                  emptySet()
                }
            schemaNames.filter { query == "*" || it.contains(query, ignoreCase = true) }
          } else {
            emptyList()
          }
      buildJsonObject {
        put("query", JsonPrimitive(query))
        put("matches", buildJsonArray { matches.forEach { add(JsonPrimitive(it)) } })
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

    peer.onRequest("tools/list") { _ ->
      buildJsonObject {
        put(
            "tools",
            buildJsonArray {
              listOf("cdd_generate", "cdd_inspect_schema", "cdd_sync_schema").forEach { name ->
                add(buildJsonObject { put("name", JsonPrimitive(name)) })
              }
            })
      }
    }

    peer.onRequest("resources/list") { _ ->
      buildJsonObject {
        put(
            "resources",
            buildJsonArray {
              add(
                  buildJsonObject {
                    put("uri", JsonPrimitive("openapi://spec/schemas"))
                    put("name", JsonPrimitive("OpenAPI Schemas"))
                  })
            })
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
    createConfiguredMcpPeer(transport)
    transport.startReading()
    return 0
  }
}
