/**
 * JVM-specific implementations of platform expect functions for file I/O, environment variables,
 * OpenAPI generation, SDK/server code generation, and synchronization.
 */
import domain.CddError
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
  for ((name, schema) in schemaMap) {
    val file = dtoGen.generateDto("$packageName.models", schema)
    filesToWrite.add(
        "$outputDir/composeApp/src/commonMain/kotlin/${packageName.replace(".", "/")}/models/$name.kt" to
            file.text)
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
  for ((path, content) in mainGen.generateServerMain(packageName, schemas)) {
    filesToWrite.add("$outputDir/server/src/main/kotlin/$pkgPath/$path" to content)
  }

  val idpGen = psi.IdpGenerator()
  filesToWrite.add(
      "$outputDir/server/src/main/kotlin/$pkgPath/auth/Idp.kt" to
          idpGen.generateIdpModule(packageName, schemas))

  val webhookGen = psi.WebhookGenerator()
  filesToWrite.add(
      "$outputDir/server/src/main/kotlin/$pkgPath/webhooks/Webhooks.kt" to
          webhookGen.generateWebhookModule(packageName, schemas))

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
    for ((path, content) in mainTestGen.generateServerMainTests(packageName, schemas)) {
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
 * @param truth Source of truth identifier (`class`, `sqlalchemy`, or `function`).
 * @return A [Result] indicating success or a [CddError].
 */
actual fun performSync(inputDir: String, truth: String): Result<Unit> {
  return psi.SyncGenerator.synchronize(inputDir, truth)
}
