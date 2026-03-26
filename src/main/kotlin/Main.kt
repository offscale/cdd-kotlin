import java.io.File
import kotlin.system.exitProcess
import openapi.OpenApiParser
import openapi.OpenApiDocument
import domain.EndpointDefinition
import domain.HttpMethod

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("cdd-kotlin CLI")
        return
    }

    val command = args[0]
    if (command == "to_docs_json") {
        var inputFile = ""
        var noImports = false
        var noWrapping = false

        var i = 1
        while (i < args.size) {
            when (args[i]) {
                "-i", "--input" -> if (i + 1 < args.size) inputFile = args[++i]
                "--no-imports" -> noImports = true
                "--no-wrapping" -> noWrapping = true
            }
            i++
        }

        if (inputFile.isEmpty()) {
            inputFile = System.getenv("CDD_INPUT") ?: ""
        }

        if (inputFile.isEmpty()) {
            System.err.println("Missing -i <spec.json>")
            exitProcess(1)
        }

        val jsonStr = File(inputFile).readText()
        val parser = OpenApiParser()
        val result = parser.parseDocumentString(jsonStr)

        val doc = when (result) {
            is OpenApiDocument.OpenApi -> result.definition
            else -> {
                System.err.println("Not an OpenAPI document")
                exitProcess(1)
            }
        }

        val endpoints = mutableMapOf<String, Map<String, String>>()

        if (doc.paths != null) {
            for ((path, pathItem) in doc.paths) {
                val methods = mapOf(
                    "get" to pathItem.get,
                    "put" to pathItem.put,
                    "post" to pathItem.post,
                    "delete" to pathItem.delete,
                    "options" to pathItem.options,
                    "head" to pathItem.head,
                    "patch" to pathItem.patch,
                    "trace" to pathItem.trace
                )

                val methodMap = mutableMapOf<String, String>()

                for ((methodName, operation) in methods) {
                    if (operation == null) continue

                    val opId = operation.operationId ?: "${methodName}${path.replace("/", "").replace("{", "").replace("}", "")}"

                    val sb = StringBuilder()

                    if (!noImports) {
                        sb.append("import io.ktor.client.*\nimport io.ktor.client.request.*\n\n")
                    }

                    if (!noWrapping) {
                        sb.append("suspend fun main() {\n    val client = HttpClient()\n")
                    }

                    val indent = if (noWrapping) "" else "    "
                    sb.append("${indent}val response = client.${methodName}(\"https://api.example.com$path\") {\n")
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
    }
}
