/**
 * JVM-specific implementations of platform expect functions for file I/O, environment variables,
 * OpenAPI generation, SDK/server code generation, and synchronization.
 */
import domain.CddError
import domain.flatMapResult
import domain.toFailure
import java.io.File

/**
 * Retrieves an environment variable on the JVM platform.
 *
 * @param name The environment variable name (or `"PWD"` for current working directory).
 * @return The variable value, or `null` if not set.
 */
actual fun getEnvVar(name: String): String? =
    if (name == "PWD") System.getProperty("user.dir")
    else System.getenv(name) ?: System.getProperty(name)

/**
 * Reads a file's text contents from the local JVM filesystem wrapped in a [Result].
 *
 * @param path The path to the file.
 * @return A [Result] containing the text content of the file or a [CddError.FileIoError].
 */
actual fun readFile(path: String): Result<String> {
  val file = File(path)
  if (!file.isFile) {
    return CddError.FileIoError(path, "File does not exist or is not a regular file").toFailure()
  }
  return Result.success(file.readText())
}

/**
 * Writes text content to a file on the local JVM filesystem, creating parent directories if needed.
 *
 * @param path The target file path.
 * @param content The string content to write.
 * @return A [Result] indicating success or a [CddError.FileIoError].
 */
actual fun writeToFile(path: String, content: String): Result<Unit> {
  val file = File(path)
  val parent = file.parentFile
  if (parent != null) {
    if (parent.isFile) {
      return CddError.FileIoError(path, "Parent path is a file").toFailure()
    }
    parent.mkdirs()
  }
  if (file.isDirectory) {
    return CddError.FileIoError(path, "Target path is a directory").toFailure()
  }
  file.writeText(content)
  return Result.success(Unit)
}

/**
 * Parses Kotlin source files in [inputDir] and writes an OpenAPI specification to [outputFile].
 *
 * @param inputDir Directory containing Kotlin source files.
 * @param outputFile Output path for the generated OpenAPI specification.
 * @return A [Result] indicating success or a [CddError].
 */
actual fun generateToOpenApi(inputDir: String, outputFile: String): Result<Unit> {
  return psi.ApiGenerator.generateToOpenApi(inputDir, outputFile)
}

/**
 * Generates Kotlin Multiplatform SDK client and model source files from an OpenAPI JSON/YAML
 * string.
 *
 * @param inputJson Raw OpenAPI specification string.
 * @param outputDir Target directory for generated SDK files.
 * @param packageName Base Kotlin package name.
 * @return A [Result] indicating success or a [CddError].
 */
actual fun generateSdkCode(
    inputJson: String,
    outputDir: String,
    packageName: String
): Result<Unit> {
  val parser = openapi.OpenApiParser()
  val doc =
      parser.parseDocumentStringResult(inputJson).getOrElse {
        return Result.failure(it)
      }
  if (doc !is openapi.OpenApiDocument.OpenApi) return Result.success(Unit)
  val definition = doc.definition
  val components = definition.components
  val schemaMap = if (components != null) components.schemas else emptyMap()

  val filesToWrite = mutableListOf<Pair<String, String>>()
  val dtoGen = psi.DtoGenerator()
  val uiGen = psi.UiGenerator()
  val pkgPath = packageName.replace(".", "/")
  for ((name, schema) in schemaMap) {
    val file = dtoGen.generateDto("$packageName.models", schema)
    filesToWrite.add(
        "$outputDir/composeApp/src/commonMain/kotlin/$pkgPath/models/$name.kt" to file.text)
    if (schema.properties.isNotEmpty()) {
      val formFile = uiGen.generateForm("$packageName.ui", schema.copy(name = name))
      val gridFile = uiGen.generateGrid("$packageName.ui", schema.copy(name = name))
      filesToWrite.add(
          "$outputDir/composeApp/src/commonMain/kotlin/$pkgPath/ui/${schema.copy(name = name).safeName}Form.kt" to
              formFile.text)
      filesToWrite.add(
          "$outputDir/composeApp/src/commonMain/kotlin/$pkgPath/ui/${schema.copy(name = name).safeName}Grid.kt" to
              gridFile.text)
    }
  }

  val endpoints = domain.OpenApiPathFlattener.flattenPaths(definition.paths, definition.components)
  if (endpoints.isNotEmpty()) {
    val networkGen = psi.NetworkGenerator()
    val apiFile =
        networkGen.generateApi("$packageName.api", "ApiClient", endpoints).getOrElse {
          return Result.failure(it)
        }
    filesToWrite.add(
        "$outputDir/composeApp/src/commonMain/kotlin/${packageName.replace(".", "/")}/api/ApiClient.kt" to
            apiFile.text)
  }
  for ((path, content) in filesToWrite) {
    writeToFile(path, content).getOrElse {
      return Result.failure(it)
    }
  }
  return Result.success(Unit)
}

/**
 * Generates Ktor server, Exposed DAO, database connection, seeder, IdP, and webhook modules from an
 * OpenAPI specification string.
 *
 * @param inputJson Raw OpenAPI specification string.
 * @param outputDir Target directory for generated server files.
 * @param packageName Base Kotlin package name.
 * @param withTests Whether to generate companion test suites.
 * @return A [Result] indicating success or a [CddError].
 */
actual fun generateServerCode(
    inputJson: String,
    outputDir: String,
    packageName: String,
    withTests: Boolean
): Result<Unit> {
  val parser = openapi.OpenApiParser()
  val doc =
      parser.parseDocumentStringResult(inputJson).getOrElse {
        return Result.failure(it)
      }
  if (doc !is openapi.OpenApiDocument.OpenApi) return Result.success(Unit)
  val definition = doc.definition
  val components = definition.components
  val schemaMap = if (components != null) components.schemas else emptyMap()
  val pkgPath = packageName.replace(".", "/")

  val filesToWrite = mutableListOf<Pair<String, String>>()
  val dtoGen = psi.DtoGenerator()
  for ((name, schema) in schemaMap) {
    val file = dtoGen.generateDto("$packageName.models", schema)
    filesToWrite.add("$outputDir/server/src/main/kotlin/$pkgPath/models/$name.kt" to file.text)
  }

  val schemas = schemaMap.map { (key, value) -> value.copy(name = key) }
  val endpoints = domain.OpenApiPathFlattener.flattenPaths(definition.paths, definition.components)
  val securitySchemes = if (components != null) components.securitySchemes else emptyMap()

  val daoGen = psi.DaoGenerator()
  for ((path, content) in daoGen.generateDaos(packageName, schemas)) {
    filesToWrite.add("$outputDir/server/src/main/kotlin/$pkgPath/$path" to content)
  }

  val dbGen = psi.DbConnectionGenerator()
  filesToWrite.add(
      "$outputDir/server/src/main/kotlin/$pkgPath/db/Db.kt" to
          dbGen.generateDbConnectionModule(packageName, schemas))

  val seederGen = psi.SeederGenerator()
  filesToWrite.add(
      "$outputDir/server/src/main/kotlin/$pkgPath/seeder/Seeder.kt" to
          seederGen.generateSeederModule(packageName, schemas))

  val mainGen = psi.ServerMainGenerator()
  for ((path, content) in
      mainGen.generateServerMain(packageName, schemas, endpoints, securitySchemes)) {
    filesToWrite.add("$outputDir/server/src/main/kotlin/$pkgPath/$path" to content)
  }

  val idpGen = psi.IdpGenerator()
  filesToWrite.add(
      "$outputDir/server/src/main/kotlin/$pkgPath/auth/Idp.kt" to
          idpGen.generateIdpModule(packageName, schemas, securitySchemes))

  val webhookGen = psi.WebhookGenerator()
  filesToWrite.add(
      "$outputDir/server/src/main/kotlin/$pkgPath/webhooks/Webhooks.kt" to
          webhookGen.generateWebhookModule(packageName, schemas, definition.webhooks))

  if (withTests) {
    val daoTestGen = psi.DaoTestGenerator()
    for ((path, content) in daoTestGen.generateDaoTests(packageName, schemas)) {
      filesToWrite.add("$outputDir/server/src/test/kotlin/$pkgPath/$path" to content)
    }

    val dbTestGen = psi.DbConnectionTestGenerator()
    filesToWrite.add(
        "$outputDir/server/src/test/kotlin/$pkgPath/db/DbTest.kt" to
            dbTestGen.generateDbConnectionTestModule(packageName, schemas))

    val seederTestGen = psi.SeederTestGenerator()
    filesToWrite.add(
        "$outputDir/server/src/test/kotlin/$pkgPath/seeder/SeederTest.kt" to
            seederTestGen.generateSeederTestModule(packageName, schemas))

    val mainTestGen = psi.ServerMainTestGenerator()
    for ((path, content) in mainTestGen.generateServerMainTests(packageName, schemas, endpoints)) {
      filesToWrite.add("$outputDir/server/src/test/kotlin/$pkgPath/$path" to content)
    }
  }
  for ((path, content) in filesToWrite) {
    writeToFile(path, content).getOrElse {
      return Result.failure(it)
    }
  }
  return Result.success(Unit)
}

/**
 * Synchronizes project code definitions in [inputDir] against [truth] on the JVM platform.
 *
 * @param inputDir Target project directory.
 * @param truth Source of truth identifier (`class`, `sqlalchemy`, `function`, or `arazzo`).
 * @return A [Result] indicating success or a [CddError].
 */
actual fun performSync(inputDir: String, truth: String): Result<Unit> {
  return psi.SyncGenerator.synchronize(inputDir, truth)
}

/**
 * Parses Kotlin workflow source files in [inputDir] and writes an Arazzo 1.1.0 specification to
 * [outputFile].
 *
 * @param inputDir Directory or `.kt` file containing Kotlin workflow code.
 * @param outputFile Output path for the generated Arazzo 1.1.0 specification.
 * @return A [Result] indicating success or a [CddError].
 */
actual fun generateToArazzo(inputDir: String, outputFile: String): Result<Unit> {
  return psi.arazzo.ArazzoApiGenerator.generateToArazzoResult(inputDir, outputFile)
}

/**
 * Generates Kotlin Multiplatform Client SDK (and optional CLI) workflow source files from an Arazzo
 * 1.1.0 specification string.
 *
 * @param inputSpec Raw Arazzo 1.1.0 JSON or YAML specification content.
 * @param outputDir Target directory for generated SDK/CLI files.
 * @param packageName Base Kotlin package name.
 * @param includeCli Whether to also emit `WorkflowCli.kt` reusing `WorkflowOrchestrator`.
 * @return A [Result] indicating success or a [CddError].
 */
actual fun generateArazzoSdkCode(
    inputSpec: String,
    outputDir: String,
    packageName: String,
    includeCli: Boolean
): Result<Unit> {
  val definition =
      arazzo.ArazzoParser().parseStringResult(inputSpec).getOrElse {
        return Result.failure(it)
      }
  val pkgPath = packageName.replace(".", "/")
  return psi.arazzo.WorkflowSdkGenerator.generateWorkflowSdkSourcesResult(packageName, definition)
      .flatMapResult { sdkFiles ->
        for ((fileName, src) in sdkFiles) {
          writeToFile(
                  "$outputDir/composeApp/src/commonMain/kotlin/$pkgPath/workflows/$fileName", src)
              .getOrElse {
                return@flatMapResult Result.failure(it)
              }
        }
        if (includeCli) {
          psi.arazzo.WorkflowCliGenerator.generateWorkflowCliSourceResult(packageName, definition)
              .flatMapResult { cliSrc ->
                writeToFile(
                    "$outputDir/composeApp/src/commonMain/kotlin/$pkgPath/cli/WorkflowCli.kt",
                    cliSrc)
              }
        } else {
          Result.success(Unit)
        }
      }
}

/**
 * Generates Ktor server workflow routes, stateful mock server, and E2E integration tests from an
 * Arazzo 1.1.0 specification string.
 *
 * @param inputSpec Raw Arazzo 1.1.0 JSON or YAML specification content.
 * @param outputDir Target directory for generated server and test files.
 * @param packageName Base Kotlin package name.
 * @param withTests Whether to emit `WorkflowE2ETest.kt` into `server/src/test/kotlin`.
 * @return A [Result] indicating success or a [CddError].
 */
actual fun generateArazzoServerCode(
    inputSpec: String,
    outputDir: String,
    packageName: String,
    withTests: Boolean
): Result<Unit> {
  val definition =
      arazzo.ArazzoParser().parseStringResult(inputSpec).getOrElse {
        return Result.failure(it)
      }
  val pkgPath = packageName.replace(".", "/")
  return psi.arazzo.WorkflowServerGenerator.generateWorkflowServerSourcesResult(
          packageName, definition)
      .flatMapResult { serverFiles ->
        for ((fileName, src) in serverFiles) {
          val targetPath =
              when (fileName) {
                "WorkflowE2ETest.kt" ->
                    if (withTests) "$outputDir/server/src/test/kotlin/$pkgPath/server/$fileName"
                    else null
                "WorkflowModels.kt",
                "WorkflowOrchestrator.kt" ->
                    "$outputDir/server/src/main/kotlin/$pkgPath/workflows/$fileName"
                else -> "$outputDir/server/src/main/kotlin/$pkgPath/server/$fileName"
              }
          if (targetPath != null) {
            writeToFile(targetPath, src).getOrElse {
              return@flatMapResult Result.failure(it)
            }
          }
        }
        Result.success(Unit)
      }
}
