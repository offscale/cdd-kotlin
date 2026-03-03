package cdd

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.InputStreamReader
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.PrintStream


import cdd.openapi.*
import cdd.classes.*
import cdd.routes.*
import cdd.docstrings.*
import cdd.shared.*
import cdd.scaffold.*


import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.options.versionOption
import java.io.File

/**
 * Root command for the cdd-kotlin CLI.
 */
class CddKotlin : CliktCommand(name = "cdd-kotlin", help = "OpenAPI ↔ Kotlin") {
    init {
        versionOption("0.0.1")
    }
    /** Run method for the base command. */
    override fun run() {}
}

/**
 * Subcommand to generate a KMP client from an OpenAPI spec.
 */
class FromOpenApi : CliktCommand(name = "from_openapi", help = "Generate from an OpenAPI specification") {
    override fun run() {}
}

/**
 * Subcommand to generate SDK.
 */
class ToSdk : CliktCommand(name = "to_sdk", help = "Generate Client SDK services") {
    val input by option("-i", "--input", envvar = "CDD_KOTLIN_INPUT", help = "Path or URL to the OpenAPI spec")
    val inputDir by option("--input-dir", envvar = "CDD_KOTLIN_INPUT_DIR", help = "Directory of OpenAPI specs")
    val output by option("-o", "--output", envvar = "CDD_KOTLIN_OUTPUT", help = "Output directory").default(".")

    val noGithubActions by option("--no-github-actions", envvar = "CDD_KOTLIN_NO_GITHUB_ACTIONS", help = "Do not generate GitHub Actions").flag()
    val noInstallablePackage by option("--no-installable-package", envvar = "CDD_KOTLIN_NO_INSTALLABLE_PACKAGE", help = "Do not generate installable package").flag()

    override fun run() {
        val outDir = java.io.File(output)
        val inputFile = input ?: inputDir?.let { java.io.File(it).listFiles()?.firstOrNull()?.absolutePath }
        if (inputFile != null) {
            val generator = ScaffoldGenerator()
            val appInfo = Info(title = "GeneratedApp", version = "1.0.0", summary = "Summary", contact = Contact("Sup", "s@s.s", "url"), license = License("Apache 2.0", "Apache-2.0"))
            try {
                cdd.openapi.ApiGenerator.generate(inputFile, java.io.File(outDir, "composeApp/src/commonMain/kotlin"), "com.example.auto")
                generator.generate(outputDirectory = outDir, projectName = "MyGeneratedApp", packageName = "com.example.auto", info = appInfo)
                println("Success! Generated project into ${outDir.absolutePath}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            println("No input provided.")
        }
    }
}

/**
 * Subcommand to generate SDK CLI.
 */
class ToSdkCli : CliktCommand(name = "to_sdk_cli", help = "Generate CLI for Client SDK") {
    val input by option("-i", "--input", envvar = "CDD_KOTLIN_INPUT", help = "Path or URL to the OpenAPI spec")
    val inputDir by option("--input-dir", envvar = "CDD_KOTLIN_INPUT_DIR", help = "Directory of OpenAPI specs")
    val output by option("-o", "--output", envvar = "CDD_KOTLIN_OUTPUT", help = "Output directory").default(".")

    val noGithubActions by option("--no-github-actions", envvar = "CDD_KOTLIN_NO_GITHUB_ACTIONS", help = "Do not generate GitHub Actions").flag()
    val noInstallablePackage by option("--no-installable-package", envvar = "CDD_KOTLIN_NO_INSTALLABLE_PACKAGE", help = "Do not generate installable package").flag()

    override fun run() {
        val outDir = java.io.File(output)
        val inputFile = input ?: inputDir?.let { java.io.File(it).listFiles()?.firstOrNull()?.absolutePath }
        if (inputFile != null) {
            val generator = ScaffoldGenerator()
            val appInfo = Info(title = "GeneratedAppCli", version = "1.0.0", summary = "Summary", contact = Contact("Sup", "s@s.s", "url"), license = License("Apache 2.0", "Apache-2.0"))
            try {
                cdd.openapi.ApiGenerator.generate(inputFile, java.io.File(outDir, "composeApp/src/commonMain/kotlin"), "com.example.cli")
                cdd.openapi.CliGenerator.generateCli(inputFile, java.io.File(outDir, "composeApp/src/commonMain/kotlin/com/example"), "com.example")
                generator.generate(outputDirectory = outDir, projectName = "MyGeneratedAppCli", packageName = "com.example.cli", info = appInfo)
                println("Success! Generated sdk cli into ${outDir.absolutePath}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            println("No input provided.")
        }
    }
}

/**
 * Subcommand to generate Server.
 */
class ToServer : CliktCommand(name = "to_server", help = "Generate Server services") {
    val input by option("-i", "--input", envvar = "CDD_KOTLIN_INPUT", help = "Path or URL to the OpenAPI spec")
    val inputDir by option("--input-dir", envvar = "CDD_KOTLIN_INPUT_DIR", help = "Directory of OpenAPI specs")
    val output by option("-o", "--output", envvar = "CDD_KOTLIN_OUTPUT", help = "Output directory").default(".")

    val noGithubActions by option("--no-github-actions", envvar = "CDD_KOTLIN_NO_GITHUB_ACTIONS", help = "Do not generate GitHub Actions").flag()
    val noInstallablePackage by option("--no-installable-package", envvar = "CDD_KOTLIN_NO_INSTALLABLE_PACKAGE", help = "Do not generate installable package").flag()

    override fun run() {
        val outDir = java.io.File(output)
        val inputFile = input ?: inputDir?.let { java.io.File(it).listFiles()?.firstOrNull()?.absolutePath }
        if (inputFile != null) {
            val generator = ScaffoldGenerator()
            val appInfo = Info(title = "GeneratedServer", version = "1.0.0", summary = "Summary", contact = Contact("Sup", "s@s.s", "url"), license = License("Apache 2.0", "Apache-2.0"))
            try {
                cdd.openapi.ApiGenerator.generate(inputFile, java.io.File(outDir, "src/main/kotlin"), "com.example.server")
                generator.generate(outputDirectory = outDir, projectName = "MyGeneratedServer", packageName = "com.example.server", info = appInfo)
                println("Success! Generated server into ${outDir.absolutePath}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            println("No input provided.")
        }
    }
}

/** Subcommand to generate an OpenAPI specification from Kotlin code */
class ToOpenApi : CliktCommand(name = "to_openapi", help = "Generate an OpenAPI specification from Kotlin code") {
    /** Path to a file or directory containing the generated project. */
    val file by option("-f", "--file", envvar = "CDD_KOTLIN_FILE", help = "Path to a snapshot file or a generated output directory").required()
    /** Format of the output OpenAPI specification. */
    val format by option("--format", envvar = "CDD_KOTLIN_FORMAT", help = "Output format for the OpenAPI spec").choice("json", "yaml").default("yaml")
    val output by option("-o", "--output", envvar = "CDD_KOTLIN_OUTPUT", help = "Output specification file")

    /** Executes the to_openapi parser logic. */
    override fun run() {
        val srcDir = File(file)
        if (!srcDir.exists()) {
            println("File or directory not found: ${srcDir.absolutePath}")
            return
        }

        try {
            val dtoParser = DtoParser()
            val networkParser = NetworkParser()
            val assembler = OpenApiAssembler()
            val writer = OpenApiWriter()
            
            val sourceCode = if (srcDir.isDirectory) {
                srcDir.walk().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
            } else {
                srcDir.readText()
            }

            val schemas = dtoParser.parse(sourceCode)
            val endpoints = networkParser.parse(sourceCode)
            val cliEndpoints = cdd.routes.CliParser().parse(sourceCode)
            
            // Deduplicate/merge by operationId
            val allEndpoints = (endpoints + cliEndpoints).associateBy { it.operationId }.values.toList()

            val definition = assembler.assemble(
                info = Info(title = "Generated API", version = "1.0.0"),
                schemas = schemas,
                endpoints = allEndpoints
            )

            val outText = if (format == "json") {
                writer.writeJson(definition)
            } else {
                writer.writeYaml(definition)
            }

            if (output != null) {
                File(output!!).writeText(outText)
                println("Saved to $output")
            } else {
                println(outText)
            }
        } catch (e: Exception) {
            println("❌ to_openapi failed: ${e.message}")
            e.printStackTrace()
        }
    }
}

/**
 * Subcommand to merge an OpenAPI spec into an existing Kotlin codebase.
 */
/** Subcommand to merge an OpenAPI specification into an existing Kotlin codebase */
class MergeOpenApi : CliktCommand(name = "merge_openapi", help = "Merge an OpenAPI specification into an existing Kotlin codebase") {
    /** Input OpenAPI specification file. */
    val spec by option("-s", "--spec", envvar = "CDD_KOTLIN_SPEC", help = "Path to the new OpenAPI specification file").required()
    /** Directory containing the existing Kotlin codebase. */
    val dir by option("-d", "--dir", envvar = "CDD_KOTLIN_DIR", help = "Directory containing the existing Kotlin codebase").required()

    /** Executes the merge_openapi logic. */
    override fun run() {
        val specFile = File(spec)
        val srcDir = File(dir)
        
        if (!specFile.exists()) {
            println("Spec file not found: ${specFile.absolutePath}")
            return
        }
        if (!srcDir.exists() || !srcDir.isDirectory) {
            println("Source directory not found or is not a directory: ${srcDir.absolutePath}")
            return
        }

        org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback()
        val disposable = org.jetbrains.kotlin.com.intellij.openapi.util.Disposer.newDisposable()

        try {
            cdd.shared.PsiInfrastructure.project // initialize
            val parser = cdd.openapi.OpenApiParser()
            val document = parser.parseFile(specFile)
            
            val dtoMerger = cdd.classes.DtoMerger()
            val networkMerger = cdd.routes.NetworkMerger()
            val networkGenerator = cdd.routes.NetworkGenerator()
                        val networkParser = cdd.routes.NetworkParser()
            val mocksEmit = cdd.mocks.MocksEmit()
            val mocksParse = cdd.mocks.MocksParse()
            val testsEmit = cdd.tests.TestsEmit()
            val testsParse = cdd.tests.TestsParse()
            val funcsEmit = cdd.functions.FunctionsEmit()
            val funcsParse = cdd.functions.FunctionsParse()

            mocksParse.parse("")
            testsParse.parse("")
            funcsParse.parse("")

            // 1. Merge DTOs
            println("Merging Models...")
            val dtoFiles = srcDir.walk().filter { it.isFile && it.extension == "kt" }.toList()
            document.components?.schemas?.forEach { (schemaName, schema) ->
                val enrichedSchema = schema.copy(name = schemaName)
                val targetFile = dtoFiles.find { it.name == "$schemaName.kt" }
                if (targetFile != null) {
                    val existingCode = targetFile.readText()
                    try {
                        val mergedCode = dtoMerger.mergeDto(existingCode, enrichedSchema)
                        if (mergedCode != existingCode) {
                            targetFile.writeText(mergedCode)
                            println("  Updated $schemaName in ${targetFile.name}")
                        }
                    } catch (e: Exception) {
                        println("  Failed to merge $schemaName: ${e.message}\nCODE WAS:\n$existingCode")
                    }
                }
            }

            // 2. Merge Network Clients
            println("Merging Network Clients...")
            val endpointsByTag = document.paths.flatMap { (path, item) ->
                val list = mutableListOf<cdd.openapi.EndpointDefinition>()
                item.get?.let { list.add(it) }
                item.post?.let { list.add(it) }
                item.put?.let { list.add(it) }
                item.delete?.let { list.add(it) }
                item.options?.let { list.add(it) }
                item.head?.let { list.add(it) }
                item.patch?.let { list.add(it) }
                item.trace?.let { list.add(it) }
                list
            }.groupBy { it.tags.firstOrNull() ?: "Default" }

            val apiFiles = srcDir.walk().filter { it.isFile && it.extension == "kt" && it.name.endsWith("Api.kt") }.toList()
            
            endpointsByTag.forEach { (tag, specEndpoints) ->
                val safeName = tag.replace(Regex("[^a-zA-Z0-9]"), "").replaceFirstChar { it.uppercase() } + "Api"
                val targetFile = apiFiles.find { it.name == "${safeName}.kt" }
                
                if (targetFile != null) {
                    val existingCode = targetFile.readText()
                    try {
                        val existingEndpoints = networkParser.parse(existingCode)
                        val mergedEndpoints = networkMerger.mergeEndpoints(existingEndpoints, specEndpoints)
                        
                        val packageName = existingCode.lines().find { it.startsWith("package ") }?.removePrefix("package ")?.trim() ?: "api"
                        
                        val ktFile = networkGenerator.generateApi(packageName, safeName, mergedEndpoints, document.servers)
                        targetFile.writeText(ktFile.text)
                        println("  Updated ${safeName}.kt")
                    } catch (e: Exception) {
                        println("  Failed to merge $safeName: ${e.message}")
                    }
                }
            }
            
            println("Merge complete.")
        } catch (e: Exception) {
            println("❌ merge_openapi failed: ${e.message}")
            e.printStackTrace()
        } finally {
            org.jetbrains.kotlin.com.intellij.openapi.util.Disposer.dispose(disposable)
        }
    }
}

/**
 * Main entry point for the cdd-kotlin executable.
 * @param args The command line arguments
 */
 
/**
 * Subcommand to generate API documentation code examples as JSON.
 */
/** Subcommand to generate API documentation code examples as JSON */

/** Subcommand to start JSON-RPC server */


/** Subcommand to start JSON-RPC server */
class ServerJsonRpc : CliktCommand(name = "serve_json_rpc", help = "Start JSON-RPC server") {
    val port by option("--port", envvar = "CDD_KOTLIN_PORT", help = "Port to listen on").default("8080")
    val listen by option("--listen", envvar = "CDD_KOTLIN_LISTEN", help = "Host to listen on").default("0.0.0.0")

    override fun run() {
        val server = HttpServer.create(InetSocketAddress(listen, port.toInt()), 0)
        server.createContext("/") { exchange ->
            if (exchange.requestMethod == "POST") {
                val reader = BufferedReader(InputStreamReader(exchange.requestBody))
                val requestBody = reader.readText()
                
                // Super minimal JSON-RPC parser
                val methodMatch = Regex(""""method"\s*:\s*"([^"]+)"""").find(requestBody)
                val paramsMatch = Regex(""""params"\s*:\s*\[(.*?)\]""").find(requestBody)
                val idMatch = Regex(""""id"\s*:\s*([^,\}]+)""").find(requestBody)
                
                val method = methodMatch?.groupValues?.get(1)
                val paramsStr = paramsMatch?.groupValues?.get(1) ?: ""
                val id = idMatch?.groupValues?.get(1) ?: "null"
                
                val paramsList = mutableListOf<String>()
                if (method != null) {
                    paramsList.addAll(method.split(" "))
                    if (paramsStr.isNotBlank()) {
                        val pMatches = Regex(""""([^"]+)"""").findAll(paramsStr)
                        paramsList.addAll(pMatches.map { it.groupValues[1] }.toList())
                    }
                    
                    val outStream = ByteArrayOutputStream()
                    val originalOut = System.out
                    val originalErr = System.err
                    System.setOut(PrintStream(outStream))
                    System.setErr(PrintStream(outStream))
                    
                    var exceptionMsg: String? = null
                    try {
                                                try {
                            val cmd = CddKotlin().subcommands(FromOpenApi().subcommands(ToSdk(), ToSdkCli(), ToServer()), ToOpenApi(), MergeOpenApi(), ToDocsJson(), ServerJsonRpc())
                            cmd.parse(paramsList.toTypedArray())
                        } catch (e: com.github.ajalt.clikt.core.PrintHelpMessage) {
                            println(e.message) // fallback
                        } catch (e: com.github.ajalt.clikt.core.PrintMessage) {
                            println(e.message)
                        } catch (e: com.github.ajalt.clikt.core.CliktError) {
                            println(e.message)
                        } catch (e: com.github.ajalt.clikt.core.CliktError) {
                            println(e.message)
                        }
                    } catch (e: Exception) {
                        exceptionMsg = e.message
                    } finally {
                        System.setOut(originalOut)
                        System.setErr(originalErr)
                    }
                    
                    val outputStr = outStream.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
                    
                    val response = if (exceptionMsg != null) {
                        """{"jsonrpc": "2.0", "error": {"code": -32603, "message": "Internal error", "data": "${exceptionMsg.replace("\"", "\\\"")}"}, "id": $id}"""
                    } else {
                        """{"jsonrpc": "2.0", "result": "$outputStr", "id": $id}"""
                    }
                    
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
                    exchange.responseBody.write(response.toByteArray())
                    exchange.responseBody.close()
                } else {
                    val errorResponse = """{"jsonrpc": "2.0", "error": {"code": -32600, "message": "Invalid Request"}, "id": null}"""
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(400, errorResponse.toByteArray().size.toLong())
                    exchange.responseBody.write(errorResponse.toByteArray())
                    exchange.responseBody.close()
                }
            } else {
                exchange.sendResponseHeaders(405, -1)
                exchange.close()
            }
        }
        server.executor = null
        println("Starting JSON-RPC server on $listen:$port...")
        server.start()
    }
}

/** Command to generate API documentation code examples as JSON. */
class ToDocsJson : CliktCommand(name = "to_docs_json", help = "Generate API documentation code examples as JSON") {
    /** Input OpenAPI specification file or URL. */
    val input by option("-i", "--input", envvar = "CDD_KOTLIN_INPUT", help = "Path or URL to the OpenAPI specification").required()
    /** Omit the imports field. */
    val noImports by option("--no-imports", envvar = "CDD_KOTLIN_NO_IMPORTS", help = "Omit the imports field").flag()
    /** Omit the wrapper_start and wrapper_end fields. */
    val noWrapping by option("--no-wrapping", envvar = "CDD_KOTLIN_NO_WRAPPING", help = "Omit the wrapper_start and wrapper_end fields").flag()
    /** Output JSON file. */
    val output by option("-o", "--output", envvar = "CDD_KOTLIN_OUTPUT", help = "Output JSON file")

    /** Executes the to_docs_json logic. */
    override fun run() {
        try {
            val parser = cdd.openapi.OpenApiParser()
            val file = File(input)
            val document = parser.parseFile(file)
            
            val operationsList = mutableListOf<Map<String, Any>>()
            
            document.paths.forEach { (path, item) ->
                val methods = mapOf(
                    "GET" to item.get,
                    "PUT" to item.put,
                    "POST" to item.post,
                    "DELETE" to item.delete,
                    "OPTIONS" to item.options,
                    "HEAD" to item.head,
                    "PATCH" to item.patch,
                    "TRACE" to item.trace
                )
                
                methods.forEach { (methodName, op) ->
                    if (op != null) {
                        val operationId = op.operationId
                        val tag = op.tags.firstOrNull() ?: "Default"
                        val apiClassName = tag.replace(Regex("[^a-zA-Z0-9]"), "").replaceFirstChar { it.uppercase() } + "Api"
                        
                        val methodNameCamel = if (operationId.isNotBlank()) {
                            operationId
                        } else {
                            methodName.lowercase() + path.replace(Regex("[^a-zA-Z0-9]"), "").replaceFirstChar { it.uppercase() }
                        }
                        
                        val code = mutableMapOf<String, String>()
                        
                        if (!noImports) {
                            code["imports"] = """import io.ktor.client.HttpClient
import com.example.auto.api.$apiClassName"""
                        }
                        if (!noWrapping) {
                            code["wrapper_start"] = """suspend fun main() {
    val client = HttpClient()
    val api = $apiClassName(client)"""
                            code["wrapper_end"] = "}"
                        }
                        
                        // construct snippet based on parameters
                        val params = op.parameters.map { it.name + " = TODO()" }
                        val requestBodyArg = if (op.requestBody != null) "body = TODO()" else null
                        val allArgs = (params + listOfNotNull(requestBodyArg)).joinToString(", ")
                        
                        code["snippet"] = "    val response = api.${methodNameCamel}($allArgs)"
                        
                        operationsList.add(mapOf(
                            "method" to methodName,
                            "path" to path,
                            "operationId" to operationId,
                            "code" to code
                        ))
                    }
                }
            }
            
            val outputJson = mapOf(
                "language" to "kotlin",
                "operations" to operationsList
            )
            
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val outText = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(listOf(outputJson))
            if (output != null) {
                File(output!!).writeText(outText)
            } else {
                println(outText)
            }
            
        } catch (e: Exception) {
            System.err.println("Error generating docs: ${e.message}")
            e.printStackTrace()
        }
    }
}

/**
 * Main entry point.
 */

/** Main entry point for the CLI */
fun main(args: Array<String>) {
    val fromOpenApi = FromOpenApi().subcommands(ToSdk(), ToSdkCli(), ToServer())
    CddKotlin().subcommands(fromOpenApi, ToOpenApi(), MergeOpenApi(), ToDocsJson(), ServerJsonRpc()).main(args)
}

/** Entry point for testing without exiting the JVM */
fun parse(args: Array<String>) {
    val fromOpenApi = FromOpenApi().subcommands(ToSdk(), ToSdkCli(), ToServer())
    CddKotlin().subcommands(fromOpenApi, ToOpenApi(), MergeOpenApi(), ToDocsJson(), ServerJsonRpc()).parse(args)
}
