actual fun getEnvVar(name: String): String? =
    if (name == "PWD") System.getProperty("user.dir") else System.getenv(name)

actual fun readFile(path: String): String = java.io.File(path).readText()

actual fun writeToFile(path: String, content: String) {
  val file = java.io.File(path)
  file.parentFile?.mkdirs()
  file.writeText(content)
}

actual fun generateToOpenApi(inputDir: String, outputFile: String) {
  psi.ApiGenerator.generateToOpenApi(inputDir, outputFile)
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

actual fun generateServerCode(
    inputJson: String,
    outputDir: String,
    packageName: String,
    withTests: Boolean
) {
  val parser = openapi.OpenApiParser()
  val doc = parser.parseDocumentString(inputJson)
  val definition = (doc as? openapi.OpenApiDocument.OpenApi)?.definition ?: return

  val dtoGen = psi.DtoGenerator()
  definition.components?.schemas?.forEach { (name, schema) ->
    val file = dtoGen.generateDto("$packageName.models", schema)
    writeToFile(
        "$outputDir/server/src/main/kotlin/${packageName.replace(".", "/")}/models/$name.kt",
        file.text)
  }

  val schemas =
      definition.components?.schemas?.map {
        domain.SchemaDefinition(
            name = it.key,
            type = it.value.type ?: "object",
            properties = it.value.properties ?: emptyMap(),
            required = it.value.required,
            enumValues = it.value.enumValues)
      } ?: emptyList()

  val daoGen = psi.DaoGenerator()
  daoGen.generateDaos(packageName, schemas).forEach { (path, content) ->
    writeToFile("$outputDir/server/src/main/kotlin/${packageName.replace(".", "/")}/$path", content)
  }

  val dbGen = psi.DbConnectionGenerator()
  writeToFile(
      "$outputDir/server/src/main/kotlin/${packageName.replace(".", "/")}/db/Db.kt",
      dbGen.generateDbConnectionModule(packageName, schemas))

  val seederGen = psi.SeederGenerator()
  writeToFile(
      "$outputDir/server/src/main/kotlin/${packageName.replace(".", "/")}/seeder/Seeder.kt",
      seederGen.generateSeederModule(packageName, schemas))

  val mainGen = psi.ServerMainGenerator()
  mainGen.generateServerMain(packageName, schemas).forEach { (path, content) ->
    writeToFile("$outputDir/server/src/main/kotlin/${packageName.replace(".", "/")}/$path", content)
  }

  val idpGen = psi.IdpGenerator()
  writeToFile(
      "$outputDir/server/src/main/kotlin/${packageName.replace(".", "/")}/auth/Idp.kt",
      idpGen.generateIdpModule(packageName, schemas))

  val webhookGen = psi.WebhookGenerator()
  writeToFile(
      "$outputDir/server/src/main/kotlin/${packageName.replace(".", "/")}/webhooks/Webhooks.kt",
      webhookGen.generateWebhookModule(packageName, schemas))

  if (withTests) {
    val daoTestGen = psi.DaoTestGenerator()
    daoTestGen.generateDaoTests(packageName, schemas).forEach { (path, content) ->
      writeToFile(
          "$outputDir/server/src/test/kotlin/${packageName.replace(".", "/")}/$path", content)
    }

    val dbTestGen = psi.DbConnectionTestGenerator()
    writeToFile(
        "$outputDir/server/src/test/kotlin/${packageName.replace(".", "/")}/db/DbTest.kt",
        dbTestGen.generateDbConnectionTestModule(packageName, schemas))

    val seederTestGen = psi.SeederTestGenerator()
    writeToFile(
        "$outputDir/server/src/test/kotlin/${packageName.replace(".", "/")}/seeder/SeederTest.kt",
        seederTestGen.generateSeederTestModule(packageName, schemas))

    val mainTestGen = psi.ServerMainTestGenerator()
    mainTestGen.generateServerMainTests(packageName, schemas).forEach { (path, content) ->
      writeToFile(
          "$outputDir/server/src/test/kotlin/${packageName.replace(".", "/")}/$path", content)
    }
  }
}

actual fun performSync(inputDir: String, truth: String) {
  psi.SyncGenerator.synchronize(inputDir, truth)
}
