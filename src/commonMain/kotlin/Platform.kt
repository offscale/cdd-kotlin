/**
 * Multiplatform expect declarations for platform-specific file I/O, environment variables, OpenAPI
 * specification extraction, SDK/server code generation, and bi-directional synchronization.
 */

/**
 * Retrieves the value of an environment variable by name.
 *
 * @param name The name of the environment variable.
 * @return The value of the environment variable, or `null` if not defined.
 */
expect fun getEnvVar(name: String): String?

/**
 * Reads the entire contents of a file at the given path as a UTF-8 string wrapped in a [Result].
 *
 * @param path The filesystem path of the file to read.
 * @return A [Result] containing the text content of the file or a [domain.CddError.FileIoError].
 */
expect fun readFile(path: String): Result<String>

/**
 * Writes the given string content to a file at the specified path, creating parent directories if
 * necessary.
 *
 * @param path The filesystem path where the file should be written.
 * @param content The text content to write to the file.
 * @return A [Result] indicating success or a [domain.CddError.FileIoError].
 */
expect fun writeToFile(path: String, content: String): Result<Unit>

/**
 * Parses source files in [inputDir] and generates an OpenAPI specification file at [outputFile].
 *
 * @param inputDir The directory containing Kotlin source code.
 * @param outputFile The destination path for the generated OpenAPI specification.
 * @return A [Result] indicating success or a [domain.CddError].
 */
expect fun generateToOpenApi(inputDir: String, outputFile: String): Result<Unit>

/**
 * Generates Kotlin Multiplatform client SDK code from an OpenAPI specification string.
 *
 * @param inputJson The raw OpenAPI JSON or YAML specification content.
 * @param outputDir The root directory where SDK files should be generated.
 * @param packageName The target Kotlin package name for generated models and client.
 * @return A [Result] indicating success or a [domain.CddError].
 */
expect fun generateSdkCode(inputJson: String, outputDir: String, packageName: String): Result<Unit>

/**
 * Generates Ktor server, Exposed DAO, database, seeder, IdP, and webhook code from an OpenAPI
 * specification string.
 *
 * @param inputJson The raw OpenAPI JSON or YAML specification content.
 * @param outputDir The root directory where server files should be generated.
 * @param packageName The target Kotlin package name for generated server modules.
 * @param withTests Whether to generate unit and integration test suites for the server.
 * @return A [Result] indicating success or a [domain.CddError].
 */
expect fun generateServerCode(
    inputJson: String,
    outputDir: String,
    packageName: String,
    withTests: Boolean
): Result<Unit>

/**
 * Performs bi-directional synchronization of code definitions in [inputDir] against [truth].
 *
 * @param inputDir The project directory to synchronize.
 * @param truth The source of truth (`class`, `sqlalchemy`, or `function`).
 * @return A [Result] indicating success or a [domain.CddError].
 */
expect fun performSync(inputDir: String, truth: String): Result<Unit>
