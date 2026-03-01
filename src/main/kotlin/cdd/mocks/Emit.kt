package cdd.mocks

import cdd.openapi.EndpointDefinition

/** Generates Mock implementations of API interfaces for local testing. */
class MocksEmit {
    /** Emits a mock implementation of the given endpoints. */
    fun emit(packageName: String, className: String, endpoints: List<EndpointDefinition>): String {
        val builder = StringBuilder()
        builder.appendLine("package $packageName\n")
        builder.appendLine("import io.ktor.client.engine.mock.*")
        builder.appendLine("import io.ktor.http.*\n")
        builder.appendLine("/** Mock class $className */")
        builder.appendLine("fun create${className}MockEngine(): MockEngine {")
        builder.appendLine("    return MockEngine { request ->")
        builder.appendLine("        val path = request.url.encodedPath")
        builder.appendLine("        val method = request.method")
        builder.appendLine("        when {")
        endpoints.forEach { ep ->
            val pathRegex = ep.path.replace(Regex("\\{[^}]+\\}"), ".+")
            builder.appendLine("            path.matches(Regex(\"^$pathRegex\$\")) && method == HttpMethod.${ep.methodName} -> {")
            builder.appendLine("                respond(\"{}\", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, \"application/json\"))")
            builder.appendLine("            }")
        }
        builder.appendLine("            else -> respondError(HttpStatusCode.NotFound)")
        builder.appendLine("        }")
        builder.appendLine("    }")
        builder.appendLine("}")
        return builder.toString()
    }
}
