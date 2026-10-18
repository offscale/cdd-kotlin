/**
 * Extracts and writes Arazzo 1.1.0 specifications from Kotlin PSI source directories or files
 * (`WorkflowOrchestrator.kt`, `WorkflowCli.kt`, `WorkflowRoutes.kt`, `WorkflowE2ETest.kt`) with
 * full [kotlin.Result] percolation.
 */
package psi.arazzo

import arazzo.ArazzoWriter
import domain.ArazzoDefinition
import domain.CddError
import domain.toFailure
import java.io.File
import writeToFile

/**
 * Orchestrates reverse-engineering of Kotlin source directories or files into an Arazzo 1.1.0 JSON
 * or YAML document.
 */
object ArazzoApiGenerator {
  /**
   * Parses Kotlin source files in [inputPath] and writes the resulting Arazzo 1.1.0 specification
   * to [outputFile].
   *
   * @param inputPath Directory or `.kt` file containing Kotlin workflow code.
   * @param outputFile Destination `.json` or `.yaml` file path.
   * @return A [Result] indicating success or a [CddError].
   */
  fun generateToArazzoResult(inputPath: String, outputFile: String): Result<Unit> {
    val definition =
        parseArazzoFromPathResult(inputPath).getOrElse {
          return Result.failure(it)
        }
    val writer = ArazzoWriter()
    val serialized =
        writer.writeResult(definition, ArazzoWriter.Format.AUTO, outputFile).getOrThrow()
    return writeToFile(outputFile, serialized)
  }

  /**
   * Parses an [ArazzoDefinition] from a directory or `.kt` file path wrapped in a [Result].
   *
   * @param inputPath Directory or `.kt` file path.
   * @return A [Result] containing the extracted [ArazzoDefinition] or a [CddError].
   */
  fun parseArazzoFromPathResult(inputPath: String): Result<ArazzoDefinition> {
    val root = File(inputPath)
    if (!root.exists()) {
      return CddError.FileIoError(inputPath, "Path does not exist").toFailure()
    }
    val ktFiles =
        if (root.isFile) {
          if (root.extension == "kt") listOf(root) else emptyList()
        } else {
          root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
    if (ktFiles.isEmpty()) {
      return CddError.ArazzoParseError("No Kotlin (.kt) files found in '$inputPath'").toFailure()
    }
    val fileContents = ktFiles.associate { it.name to it.readText() }
    val cliFile = fileContents["WorkflowCli.kt"]
    if (cliFile != null && fileContents.size == 1) {
      return WorkflowCliParser.parseWorkflowCliResult(cliFile)
    }
    val serverFile = fileContents["WorkflowRoutes.kt"] ?: fileContents["WorkflowE2ETest.kt"]
    if (serverFile != null && !fileContents.containsKey("WorkflowOrchestrator.kt")) {
      return WorkflowServerParser.parseWorkflowServerFileResult(serverFile)
    }
    return WorkflowSdkParser.parseWorkflowSdkResult(fileContents)
  }
}
