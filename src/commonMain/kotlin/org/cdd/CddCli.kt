/** Programmatic and CLI command dispatcher for the CDD (Compiler Driven Development) toolchain. */
package org.cdd

import domain.ArazzoDefinition
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.OpenApiDefinition
import domain.PathItem
import domain.SchemaProperty
import domain.Workflow
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
import psi.TypeMappers
import psi.capitalizeName
import psi.decapitalizeName
import psi.escapeKotlinKeyword
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
    val effectiveInput = flags.input.ifEmpty { flags.inputDir }
    if (effectiveInput.isEmpty()) {
      println("Missing -i <src>")
      return 1
    }

    return generateToOpenApi(effectiveInput, flags.output)
        .fold(
            onSuccess = { 0 },
            onFailure = { e ->
              println("Failed to generate OpenAPI: ${e.message}")
              1
            })
  }

  /**
   * Generates JSON documentation with Kotlin SDK client code snippets for an OpenAPI or Arazzo
   * specification.
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

    val rootJson =
        when (result) {
          is OpenApiDocument.OpenApi ->
              buildOpenApiDocsJson(result.definition, noImports, noWrapping)
          is OpenApiDocument.Arazzo -> buildArazzoDocsJson(result.definition, noImports, noWrapping)
          else -> {
            println("Not an OpenAPI document")
            return 1
          }
        }

    val formatted = prettyJson.encodeToString(JsonObject.serializer(), rootJson)
    if (outputFile.isNotEmpty()) {
      writeDocsJsonOutput(outputFile, formatted).getOrElse {
        return 1
      }
    }
    println(formatted)
    return 0
  }

  private fun writeDocsJsonOutput(outputFile: String, content: String): Result<Unit> {
    val firstAttempt = writeToFile(outputFile, content)
    if (firstAttempt.isSuccess) return firstAttempt
    val fallbackPath = "${outputFile.trimEnd('/', '\\')}/docs.json"
    return writeToFile(fallbackPath, content)
  }

  private fun buildOpenApiDocsJson(
      doc: OpenApiDefinition,
      noImports: Boolean,
      noWrapping: Boolean
  ): JsonObject {
    val firstUrl = doc.servers.firstOrNull()?.url
    val baseUrl = if (!firstUrl.isNullOrBlank()) firstUrl else "https://api.example.com"
    val endpointsJson = buildPathItemsDocsJson(doc.paths, baseUrl, noImports, noWrapping)
    val webhooksJson = buildPathItemsDocsJson(doc.webhooks, baseUrl, noImports, noWrapping)
    return buildJsonObject {
      put("endpoints", endpointsJson)
      if (doc.webhooks.isNotEmpty()) {
        put("webhooks", webhooksJson)
      }
    }
  }

  private fun buildPathItemsDocsJson(
      items: Map<String, PathItem>,
      baseUrl: String,
      noImports: Boolean,
      noWrapping: Boolean
  ): JsonObject = buildJsonObject {
    for ((path, pathItem) in items) {
      val methods = LinkedHashMap<String, EndpointDefinition?>()
      methods["get"] = pathItem.get
      methods["put"] = pathItem.put
      methods["post"] = pathItem.post
      methods["delete"] = pathItem.delete
      methods["options"] = pathItem.options
      methods["head"] = pathItem.head
      methods["patch"] = pathItem.patch
      methods["trace"] = pathItem.trace
      methods["query"] = pathItem.query
      for ((customKey, customOp) in pathItem.additionalOperations) {
        methods[customKey.lowercase()] = customOp
      }

      val methodEntries = LinkedHashMap<String, JsonObject>()
      for ((methodName, operation) in methods) {
        if (operation == null) continue
        val mergedParams = mergeDocsParameters(pathItem.parameters, operation.parameters)
        val mergedOperation = operation.copy(parameters = mergedParams)
        methodEntries[methodName] =
            buildOperationDocsEntry(
                path, methodName, mergedOperation, baseUrl, noImports, noWrapping)
      }

      if (methodEntries.isNotEmpty()) {
        put(path, JsonObject(methodEntries))
      }
    }
  }

  private fun mergeDocsParameters(
      pathParams: List<EndpointParameter>,
      opParams: List<EndpointParameter>
  ): List<EndpointParameter> {
    val merged = LinkedHashMap<String, EndpointParameter>()
    for (p in pathParams) {
      merged["${p.location}:${p.name}"] = p
    }
    for (p in opParams) {
      merged["${p.location}:${p.name}"] = p
    }
    return merged.values.toList()
  }

  private fun buildOperationDocsEntry(
      path: String,
      methodName: String,
      operation: EndpointDefinition,
      baseUrl: String,
      noImports: Boolean,
      noWrapping: Boolean
  ): JsonObject {
    val sdkMethodName = deriveOperationMethodName(operation, methodName, path)
    val argsWithTypes = buildOperationSampleArguments(operation)
    val needsModelsImport = argsWithTypes.any { it.second }
    val importsText =
        if (needsModelsImport) {
          "import org.example.api.ApiClient\nimport org.example.models.*"
        } else {
          "import org.example.api.ApiClient"
        }
    val wrapperStartText = "suspend fun main() {\n    val api = ApiClient(baseUrl = \"$baseUrl\")"
    val argsJoined = argsWithTypes.joinToString(", ") { it.first }
    val indent = if (noWrapping) "" else "    "
    val rawSnippetText = "    val response = api.$sdkMethodName($argsJoined)\n    println(response)"
    val formattedSnippetText =
        "${indent}val response = api.$sdkMethodName($argsJoined)\n${indent}println(response)"
    val wrapperEndText = "}"

    val fullCode =
        buildFullDocsCode(
            importsText = importsText,
            wrapperStartText = wrapperStartText,
            snippetText = formattedSnippetText,
            wrapperEndText = wrapperEndText,
            noImports = noImports,
            noWrapping = noWrapping)

    return buildJsonObject {
      put("imports", JsonPrimitive(importsText))
      put("wrapper_start", JsonPrimitive(wrapperStartText))
      put("snippet", JsonPrimitive(rawSnippetText))
      put("wrapper_end", JsonPrimitive(wrapperEndText))
      put("code", JsonPrimitive(fullCode))
    }
  }

  private fun buildArazzoDocsJson(
      doc: ArazzoDefinition,
      noImports: Boolean,
      noWrapping: Boolean
  ): JsonObject {
    val workflowsJson = buildJsonObject {
      for (wf in doc.workflows) {
        put(wf.workflowId, buildWorkflowDocsEntry(wf, noImports, noWrapping))
      }
    }
    return buildJsonObject { put("workflows", workflowsJson) }
  }

  private fun buildWorkflowDocsEntry(
      workflow: Workflow,
      noImports: Boolean,
      noWrapping: Boolean
  ): JsonObject {
    val pascal = toDocsPascalIdentifier(workflow.workflowId)
    val methodName = escapeKotlinKeyword(decapitalizeName(pascal))
    val importsText =
        "import org.example.workflows.WorkflowOrchestrator\nimport org.example.workflows.${pascal}Inputs"
    val wrapperStartText = "suspend fun main() {\n    val orchestrator = WorkflowOrchestrator()"
    val indent = if (noWrapping) "" else "    "
    val rawSnippetText =
        "    val response = orchestrator.$methodName(${pascal}Inputs())\n    println(response)"
    val formattedSnippetText =
        "${indent}val response = orchestrator.$methodName(${pascal}Inputs())\n${indent}println(response)"
    val wrapperEndText = "}"

    val fullCode =
        buildFullDocsCode(
            importsText = importsText,
            wrapperStartText = wrapperStartText,
            snippetText = formattedSnippetText,
            wrapperEndText = wrapperEndText,
            noImports = noImports,
            noWrapping = noWrapping)

    return buildJsonObject {
      put("imports", JsonPrimitive(importsText))
      put("wrapper_start", JsonPrimitive(wrapperStartText))
      put("snippet", JsonPrimitive(rawSnippetText))
      put("wrapper_end", JsonPrimitive(wrapperEndText))
      put("code", JsonPrimitive(fullCode))
    }
  }

  private fun buildFullDocsCode(
      importsText: String,
      wrapperStartText: String,
      snippetText: String,
      wrapperEndText: String,
      noImports: Boolean,
      noWrapping: Boolean
  ): String {
    val sb = StringBuilder()
    if (!noImports) {
      sb.appendLine(importsText)
      sb.appendLine()
    }
    if (!noWrapping) {
      sb.appendLine(wrapperStartText)
    }
    sb.appendLine(snippetText)
    if (!noWrapping) {
      sb.appendLine(wrapperEndText)
    }
    return sb.toString()
  }

  private fun deriveOperationMethodName(
      operation: EndpointDefinition,
      methodName: String,
      path: String
  ): String {
    val raw =
        if (operation.operationIdExplicit && operation.operationId.isNotBlank()) {
          operation.operationId
        } else {
          "${methodName}_${path}"
        }
    val camel = toDocsCamelIdentifier(raw)
    return escapeKotlinKeyword(camel)
  }

  private fun toDocsPascalIdentifier(raw: String): String {
    val parts = raw.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "Operation"
    return parts.joinToString("") { capitalizeName(it) }
  }

  private fun toDocsCamelIdentifier(raw: String): String {
    val pascal = toDocsPascalIdentifier(raw)
    return decapitalizeName(pascal)
  }

  private fun buildOperationSampleArguments(
      operation: EndpointDefinition
  ): List<Pair<String, Boolean>> {
    val args = mutableListOf<Pair<String, Boolean>>()
    for (param in operation.parameters) {
      val ktType = resolveDocsParamKotlinType(param)
      val (literal, usesModel) = sampleLiteralForKotlinType(ktType)
      args.add("${param.safeName} = $literal" to usesModel)
    }
    val bodyType = resolveDocsBodyKotlinType(operation)
    if (bodyType != null) {
      val (literal, usesModel) = sampleLiteralForKotlinType(bodyType)
      args.add("body = $literal" to usesModel)
    }
    return args
  }

  private fun resolveDocsParamKotlinType(param: EndpointParameter): String {
    val schema = param.schema ?: selectDocsSchema(param.content)
    return if (schema != null) {
      TypeMappers.mapType(schema)
    } else {
      param.type
    }
  }

  private fun resolveDocsBodyKotlinType(operation: EndpointDefinition): String? {
    val rb = operation.requestBody ?: return null
    val schema = selectDocsSchema(rb.content) ?: return null
    return TypeMappers.mapType(schema)
  }

  private fun selectDocsSchema(content: Map<String, domain.MediaTypeObject>): SchemaProperty? {
    if (content.isEmpty()) return null
    val jsonEntry =
        content.entries.firstOrNull { (k, _) ->
          val norm = k.substringBefore(";").trim().lowercase()
          norm == "application/json" || norm.endsWith("+json")
        } ?: content.entries.first()
    val media = jsonEntry.value
    if (media.schema != null) return media.schema
    val item = media.itemSchema ?: return null
    return SchemaProperty(types = setOf("array"), items = item)
  }

  private fun sampleLiteralForKotlinType(kotlinType: String): Pair<String, Boolean> {
    val clean = kotlinType.trim().removeSuffix("?")
    return when {
      clean == "String" || clean == "LocalDate" || clean == "Instant" -> "\"example\"" to false
      clean == "Int" -> "1" to false
      clean == "Long" -> "1L" to false
      clean == "Double" -> "1.0" to false
      clean == "Float" -> "1.0f" to false
      clean == "Boolean" -> "true" to false
      clean == "ByteArray" -> "byteArrayOf()" to false
      clean.startsWith("List<") -> "emptyList()" to false
      clean.startsWith("Map<") -> "emptyMap()" to false
      clean.endsWith("JsonElement") || clean == "Nothing" -> "null" to false
      else -> "$clean()" to true
    }
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
