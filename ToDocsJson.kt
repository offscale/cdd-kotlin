/**
 * Subcommand to generate API documentation code examples as JSON.
 */
class ToDocsJson : CliktCommand(name = "to_docs_json", help = "Generate API documentation code examples as JSON") {
    /** Input OpenAPI specification file or URL. */
    val input by option("-i", "--input", help = "Path or URL to the OpenAPI specification").required()
    /** Omit the imports field. */
    val noImports by option("--no-imports", help = "Omit the imports field").flag()
    /** Omit the wrapper_start and wrapper_end fields. */
    val noWrapping by option("--no-wrapping", help = "Omit the wrapper_start and wrapper_end fields").flag()

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
                            code["imports"] = "import io.ktor.client.HttpClient
import com.example.auto.api.$apiClassName"
                        }
                        if (!noWrapping) {
                            code["wrapper_start"] = "suspend fun main() {
    val client = HttpClient()
    val api = $apiClassName(client)"
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
            println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(listOf(outputJson)))
            
        } catch (e: Exception) {
            System.err.println("Error generating docs: ${e.message}")
            e.printStackTrace()
        }
    }
}
