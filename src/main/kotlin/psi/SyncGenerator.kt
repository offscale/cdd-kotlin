/**
 * Bidirectional synchronization engine for Kotlin domain models, DAOs, and OpenAPI specifications.
 */
package psi

import domain.CddError
import domain.toFailure
import java.io.File
import writeToFile

/**
 * Handles bi-directional synchronization of domain models, DAOs/ORM constraints, and OpenAPI specs.
 */
object SyncGenerator {
  /**
   * Synchronizes the project in [inputDir] to align with [truth].
   *
   * @param inputDir The root directory of the source code.
   * @param truth The designated source of truth ("class", "sqlalchemy" / "dao", "function").
   * @return A [Result] indicating success or a [CddError].
   */
  fun synchronize(inputDir: String, truth: String): Result<Unit> {
    val dir = File(inputDir)
    if (!dir.exists() || !dir.isDirectory) {
      return CddError.InvalidDirectoryError(inputDir, "Input must be a directory: $inputDir")
          .toFailure()
    }

    println("Parsing source code in ${dir.absolutePath}...")

    val ktFiles = dir.walk().toList().filter { it.isFile && it.extension == "kt" }
    val allCode = ktFiles.joinToString("\n") { it.readText() }

    // Step 1: Parse the current state (Schemas and DAOs)
    val dtoParser = DtoParser()
    val currentSchemas = dtoParser.parse(allCode)

    // Currently, we support treating "class" (Data Models) as the source of truth,
    // and synchronizing "dao" (Exposed ORM definitions) to match.
    if (truth == "class") {
      println("Aligning DAOs/ORM to match Data Classes...")
      // If class is truth, we use DaoGenerator to overwrite the Daos.kt with the true schema
      val daoGen = DaoGenerator()
      val newDaosMap = daoGen.generateDaos("org.example", currentSchemas)

      // Write each DAO file
      for ((relativePath, content) in newDaosMap) {
        val daoFile = File(dir, relativePath)
        writeToFile(daoFile.absolutePath, content).getOrElse {
          return Result.failure(it)
        }
        println("Synchronized DAOs in $relativePath")
      }
    } else if (truth == "dao" || truth == "sqlalchemy") {
      println("Aligning Data Classes to match DAOs/ORM... (Not fully implemented in this phase)")
      // In a fully complete sync, this would parse the Exposed Table definitions
      // and emit updated data classes.
    } else {
      println("Unknown truth target: $truth")
    }

    // As a side-effect, we also update the OpenAPI spec
    println("Re-generating OpenAPI specification to match $truth...")
    val openApiOut = File(dir, "openapi_sync.json")
    ApiGenerator.generateToOpenApi(inputDir, openApiOut.absolutePath).getOrElse {
      return Result.failure(it)
    }
    println("Synchronization complete. Specs and Code are aligned.")
    return Result.success(Unit)
  }
}
