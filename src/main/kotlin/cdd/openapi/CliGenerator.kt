package cdd.openapi

import cdd.classes.*
import cdd.routes.*
import cdd.docstrings.*
import cdd.shared.*
import cdd.scaffold.*

import java.io.File

/** Generates CLI for the SDK */
object CliGenerator {
    /** Generate CLI classes */
    fun generateCli(inputPath: String, outputDirectory: File, packageName: String) {
        val parser = OpenApiParser()
        val document = parser.parseFile(File(inputPath))
        
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
        
        val cliDir = File(outputDirectory, "cli")
        cliDir.mkdirs()
        
        val stringBuilder = java.lang.StringBuilder()
        stringBuilder.appendLine("package $packageName.cli\n")
        stringBuilder.appendLine("import com.github.ajalt.clikt.core.CliktCommand")
        stringBuilder.appendLine("import com.github.ajalt.clikt.core.subcommands")
        stringBuilder.appendLine("import com.github.ajalt.clikt.parameters.options.option")
        stringBuilder.appendLine("import com.github.ajalt.clikt.parameters.options.required")
        stringBuilder.appendLine("import io.ktor.client.HttpClient")
        stringBuilder.appendLine("import $packageName.api.*\n")
        
        stringBuilder.appendLine("class MainCommand : CliktCommand(name = \"api-cli\", help = \"Auto-generated API CLI\") {")
        stringBuilder.appendLine("    override fun run() {}")
        stringBuilder.appendLine("}\n")
        
        val tagCommands = mutableListOf<String>()
        
        endpointsByTag.forEach { (tag, endpoints) ->
            val safeTag = tag.replace(Regex("[^a-zA-Z0-9]"), "").replaceFirstChar { it.lowercase() }
            val tagCommandName = safeTag.replaceFirstChar { it.uppercase() } + "Command"
            
            stringBuilder.appendLine("class $tagCommandName : CliktCommand(name = \"$safeTag\", help = \"$tag operations\") {")
            stringBuilder.appendLine("    override fun run() {}")
            stringBuilder.appendLine("}\n")
            
            tagCommands.add(tagCommandName)
            
            val operationCommands = mutableListOf<String>()
            
            endpoints.forEach { endpoint ->
                val opId = if (endpoint.operationId.isNotBlank()) endpoint.operationId else endpoint.method.name + endpoint.path.replace(Regex("[^a-zA-Z0-9]"), "")
                val safeOp = opId.replaceFirstChar { it.uppercase() }
                val opCommandName = "${safeOp}Command"
                val apiClassName = tag.replace(Regex("[^a-zA-Z0-9]"), "").replaceFirstChar { it.uppercase() } + "Api"
                
                stringBuilder.appendLine("class $opCommandName : CliktCommand(name = \"${opId.lowercase()}\", help = \"${endpoint.summary ?: endpoint.description ?: opId}\") {")
                
                endpoint.parameters.forEach { param ->
                    val paramType = when(param.schema?.type) {
                        "integer" -> "Int"
                        "number" -> "Double"
                        "boolean" -> "Boolean"
                        else -> "String"
                    }
                    val req = if (param.isRequired) ".required()" else ""
                    stringBuilder.appendLine("    val ${param.name} by option(\"--${param.name}\", help=\"${param.description ?: param.name}\")\$req")
                }
                
                stringBuilder.appendLine("    override fun run() {")
                stringBuilder.appendLine("        val client = HttpClient()")
                stringBuilder.appendLine("        val api = $apiClassName(client)")
                stringBuilder.appendLine("        println(\"Called $opId\")")
                stringBuilder.appendLine("    }")
                stringBuilder.appendLine("}\n")
                
                operationCommands.add(opCommandName)
            }
            
            stringBuilder.appendLine("fun build$tagCommandName(): $tagCommandName {")
            stringBuilder.appendLine("    return $tagCommandName().subcommands(${operationCommands.joinToString(", ") { "\"$it()\"" }})")
            stringBuilder.appendLine("}\n")
        }
        
        stringBuilder.appendLine("fun main(args: Array<String>) {")
        stringBuilder.appendLine("    MainCommand().subcommands(${tagCommands.joinToString(", ") { "\"build$it()\"" }}).main(args)")
        stringBuilder.appendLine("}")
        
        File(cliDir, "Cli.kt").writeText(stringBuilder.toString().replace("\"build", "build").replace("()\"", "()"))
    }
}
