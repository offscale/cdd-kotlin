actual fun getEnvVar(name: String): String? =
    if (name == "PWD") System.getProperty("user.dir") else System.getenv(name)

actual fun readFile(path: String): String = java.io.File(path).readText()

actual fun writeToFile(path: String, content: String) {
  val file = java.io.File(path)
  file.parentFile?.mkdirs()
  file.writeText(content)
}

actual fun generateOpenApi(inputDir: String, outputFile: String) {
  psi.ApiGenerator.generateOpenApi(inputDir, outputFile)
}

actual fun generateSdkCode(inputJson: String, outputDir: String, packageName: String) {
  val parser = openapi.OpenApiParser()
  val doc = parser.parseDocumentString(inputJson)
  val definition = (doc as? openapi.OpenApiDocument.OpenApi)?.definition ?: return

  val dtoGen = psi.DtoGenerator()
  definition.components?.schemas?.forEach { (name, schema) ->
    val file = dtoGen.generateDto("$packageName.models", schema)
    writeToFile(
        "$outputDir/composeApp/src/commonMain/kotlin/${packageName.replace(".", "/")}/models/$name.kt",
        file.text)
  }

  val endpoints = domain.OpenApiPathFlattener.flattenPaths(definition.paths, definition.components)
  if (endpoints.isNotEmpty()) {
    val networkGen = psi.NetworkGenerator()
    val apiFile = networkGen.generateApi("$packageName.api", "ApiClient", endpoints)
    writeToFile(
        "$outputDir/composeApp/src/commonMain/kotlin/${packageName.replace(".", "/")}/api/ApiClient.kt",
        apiFile.text)
  }
}
