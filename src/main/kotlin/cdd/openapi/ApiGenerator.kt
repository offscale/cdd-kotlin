package cdd.openapi

import cdd.openapi.*
import cdd.classes.*
import cdd.routes.*
import cdd.docstrings.*
import cdd.shared.*
import cdd.scaffold.*


import java.io.File
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer

/** Auto generated docs */

object ApiGenerator {
    /** Auto generated docs */
    fun generate(inputPath: String, outputDirectory: File, packageName: String) {
        val parser = OpenApiParser()
        val document = parser.parseFile(File(inputPath))
        
        val validator = OpenApiValidator()
        validator.validate(document)
        
        // This is necessary to use Kotlin Compiler embeddable APIs correctly
        setIdeaIoUseFallback()
        val disposable = Disposer.newDisposable()
        
        try {
            val project = PsiInfrastructure.project
            
            // 1. Generate Models (DTOs)
            println("Generating Models...")
            val dtoGenerator = DtoGenerator()
            val dtoDir = File(outputDirectory, "dto")
            dtoDir.mkdirs()
            
            document.components?.schemas?.forEach { (name, schema) ->
                val enrichedSchema = schema.copy(name = name)
                val ktFile = dtoGenerator.generateDto("$packageName.dto", enrichedSchema)
                val cleanName = name.replace(Regex("[^a-zA-Z0-9]"), "").replaceFirstChar { it.uppercase() }
                File(dtoDir, "${cleanName}.kt").writeText(ktFile.text)
            }
            
            // 2. Generate Network clients (Ktor routes)
            println("Generating Network Client...")
            val networkGenerator = NetworkGenerator()
            val apiDir = File(outputDirectory, "api")
            apiDir.mkdirs()
            
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
            
            endpointsByTag.forEach { (tag, endpoints) ->
                val safeName = tag.replace(Regex("[^a-zA-Z0-9]"), "").replaceFirstChar { it.uppercase() } + "Api"
                val ktFile = networkGenerator.generateApi("$packageName.api", safeName, endpoints, document.servers)
                File(apiDir, "${safeName}.kt").writeText(ktFile.text)
            }
            
        } finally {
            Disposer.dispose(disposable)
        }
    }
}
