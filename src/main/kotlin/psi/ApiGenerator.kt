package psi

import domain.Info
import java.io.File
import openapi.OpenApiAssembler
import openapi.OpenApiWriter

/** Generator for producing OpenAPI specifications from Kotlin source. */
object ApiGenerator {
  /** Parses all Kotlin files in [inputDir] and generates an OpenAPI JSON to [outputFile]. */
  fun generateOpenApi(inputDir: String, outputFile: String) {
    val dir = File(inputDir)
    if (!dir.exists() || !dir.isDirectory) {
      throw IllegalArgumentException("Input must be a directory: $inputDir")
    }

    val ktFiles = dir.walk().filter { it.isFile && it.extension == "kt" }.toList()
    val allCode = ktFiles.joinToString("\n") { it.readText() }

    val dtoParser = DtoParser()
    val schemas = dtoParser.parse(allCode)

    val networkParser = NetworkParser()
    val parseResult = networkParser.parseWithMetadata(allCode)

    val assembler = OpenApiAssembler()
    val definition =
        assembler.assemble(
            info = parseResult.metadata.info ?: Info(title = "Generated API", version = "1.0.0"),
            schemas = schemas,
            endpoints = parseResult.endpoints,
            servers = parseResult.metadata.servers,
            webhooks = parseResult.webhooks,
            webhooksExtensions = parseResult.metadata.webhooksExtensions,
            webhooksExplicitEmpty = parseResult.metadata.webhooksExplicitEmpty,
            pathsExtensions = parseResult.metadata.pathsExtensions,
            pathsExplicitEmpty = parseResult.metadata.pathsExplicitEmpty,
            pathItems = parseResult.metadata.pathItems,
            security = parseResult.metadata.security,
            securityExplicitEmpty = parseResult.metadata.securityExplicitEmpty,
            tags = parseResult.metadata.tags,
            externalDocs = parseResult.metadata.externalDocs,
            extensions = parseResult.metadata.extensions,
            openapi = parseResult.metadata.openapi ?: "3.2.0",
            jsonSchemaDialect = parseResult.metadata.jsonSchemaDialect,
            self = parseResult.metadata.self,
            liftCommonPathMetadata = true,
            components =
                domain.Components(
                    schemas = emptyMap(),
                    responses = parseResult.metadata.componentResponses,
                    parameters = parseResult.metadata.componentParameters,
                    requestBodies = parseResult.metadata.componentRequestBodies,
                    headers = parseResult.metadata.componentHeaders,
                    securitySchemes = parseResult.metadata.securitySchemes,
                    examples = parseResult.metadata.componentExamples,
                    links = parseResult.metadata.componentLinks,
                    callbacks = parseResult.metadata.componentCallbacks,
                    pathItems = parseResult.metadata.componentPathItems,
                    mediaTypes = parseResult.metadata.componentMediaTypes,
                    extensions = parseResult.metadata.componentsExtensions))

    val writer = OpenApiWriter()
    val json = writer.writeJson(definition)
    File(outputFile).writeText(json)
  }
}
