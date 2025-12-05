package psi

import domain.EndpointDefinition
import domain.EndpointParameter
import domain.HttpMethod
import domain.ParameterLocation
import org.jetbrains.kotlin.psi.KtFile

/**
 * Generates Ktor Network Interface and Implementation code from Endpoint Definitions.
 */
class NetworkGenerator {

    private val psiFactory = PsiInfrastructure.createPsiFactory()

    /**
     * Generates a complete API file with Interface, Implementation, and Exception classes.
     */
    fun generateApi(packageName: String, apiName: String, endpoints: List<EndpointDefinition>): KtFile {
        val imports = """
            package $packageName
            
            import io.ktor.client.*
            import io.ktor.client.call.*
            import io.ktor.client.request.*
            import io.ktor.http.*
        """.trimIndent()

        val interfaceName = "I$apiName"
        val implName = apiName

        // Generate Interface Methods
        val interfaceMethods = endpoints.joinToString("\n\n") { ep ->
            val signature = generateMethodSignature(ep)
            val doc = generateKDoc(ep)
            "$doc    $signature"
        }

        // Generate Implementation Methods
        val implMethods = endpoints.joinToString("\n\n") { ep ->
            val doc = generateKDoc(ep)
            val impl = generateMethodImpl(ep)
            "$doc    $impl"
        }

        val content = """
            $imports
            
            interface $interfaceName {
            $interfaceMethods
            }
            
            class $implName(private val client: HttpClient) : $interfaceName {
            $implMethods
            }
            
            class ApiException(message: String) : Exception(message)
        """.trimIndent()

        return psiFactory.createFile("$apiName.kt", content)
    }

    private fun generateKDoc(ep: EndpointDefinition): String {
        return if (ep.summary != null) {
            "    /**\n     * ${ep.summary}\n     */\n"
        } else {
            ""
        }
    }

    /**
     * Generates the Kotlin function signature for an endpoint.
     */
    fun generateMethodSignature(ep: EndpointDefinition): String {
        // Collect standard parameters
        val params = ep.parameters.map { param ->
            val type = param.type ?: "String"
            "${param.name}: $type"
        }.toMutableList()

        // Append Body parameter if exists
        if (ep.requestBodyType != null) {
            params.add("body: ${ep.requestBodyType}")
        }

        val paramString = params.joinToString(", ")
        val returnType = ep.responseType ?: "Unit"

        return "suspend fun ${ep.operationId}($paramString): $returnType"
    }

    /**
     * Generates the Ktor implementation block for an endpoint.
     */
    fun generateMethodImpl(ep: EndpointDefinition): String {
        val returnType = ep.responseType ?: "Unit"
        val signature = generateMethodSignature(ep)

        // Convert OpenAPI style path {id} to Kotlin String template style $id
        val path = ep.path.replace("{", "\$").replace("}", "")

        // Convert parameters to Ktor DSL calls
        val paramLines = ep.parameters.map { param ->
            when (param.location) {
                ParameterLocation.QUERY -> "parameter(\"${param.name}\", ${param.name})"
                ParameterLocation.HEADER -> "header(\"${param.name}\", ${param.name})"
                ParameterLocation.PATH -> "" // Handled in URL interpolation
            }
        }.filter { it.isNotEmpty() }

        val paramConfig = if (paramLines.isNotEmpty()) {
            "\n            " + paramLines.joinToString("\n            ")
        } else {
            ""
        }

        // Handle Body Set
        val bodyConfig = if (ep.requestBodyType != null) {
            "\n            setBody(body)"
        } else {
            ""
        }

        // Ensure format "HttpMethod.Get"
        val methodEnumName = ep.method.name.lowercase().replaceFirstChar { it.uppercase() }
        val methodStr = "HttpMethod.$methodEnumName"

        return """
    override $signature {
        return try {
            client.request("$path") {
                method = $methodStr$paramConfig$bodyConfig
            }.body<$returnType>()
        } catch (e: Exception) {
            throw ApiException(e.message ?: "Error")
        }
    }
        """.trimIndent()
    }
}
