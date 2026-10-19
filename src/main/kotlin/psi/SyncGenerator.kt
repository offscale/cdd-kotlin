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
  private val PACKAGE_REGEX = Regex("""^\s*package\s+([A-Za-z0-9_.]+)""", RegexOption.MULTILINE)
  private val TABLE_REGEX =
      Regex("""object\s+([A-Za-z0-9_]+)Table\s*:\s*Table\([^)]*\)\s*\{([^}]*)\}""")
  private val COL_REGEX =
      Regex("""val\s+([A-Za-z0-9_]+)Column\s*=\s*(integer|long|double|bool|varchar|text)\(""")

  private fun extractBasePackage(allCode: String): String {
    val match = PACKAGE_REGEX.find(allCode) ?: return "org.example"
    val stripped =
        match.groupValues[1]
            .removeSuffix(".models")
            .removeSuffix(".dao")
            .removeSuffix(".network")
            .removeSuffix(".server")
    return stripped.ifBlank { "org.example" }
  }

  private fun writeModelSchemas(
      dir: File,
      basePackage: String,
      schemas: Collection<domain.SchemaDefinition>
  ): Result<Unit> {
    val dtoGen = DtoGenerator()
    for (schema in schemas) {
      val dtoSource = dtoGen.generateDto("$basePackage.models", schema).text
      val modelFile = File(dir, "models/${schema.safeName}.kt")
      writeToFile(modelFile.absolutePath, dtoSource).getOrElse {
        return Result.failure(it)
      }
    }
    return Result.success(Unit)
  }

  private fun parseDaoTableSchemas(allCode: String): List<domain.SchemaDefinition> {
    return TABLE_REGEX.findAll(allCode)
        .map { match ->
          val modelName = match.groupValues[1]
          val body = match.groupValues[2]
          val props = LinkedHashMap<String, domain.SchemaProperty>()
          val required = mutableListOf<String>()
          for (colMatch in COL_REGEX.findAll(body)) {
            val colName = colMatch.groupValues[1]
            val colKind = colMatch.groupValues[2]
            val oasType =
                when (colKind) {
                  "integer",
                  "long" -> "integer"
                  "double" -> "number"
                  "bool" -> "boolean"
                  else -> "string"
                }
            val format = if (colKind == "long") "int64" else null
            props[colName] = domain.SchemaProperty(types = setOf(oasType), format = format)
            required.add(colName)
          }
          domain.SchemaDefinition(
              name = modelName, type = "object", properties = props, required = required)
        }
        .toList()
  }

  /**
   * Synchronizes the project in [inputDir] to align with [truth].
   *
   * @param inputDir The root directory of the source code.
   * @param truth The designated source of truth (`class`, `dao` / `sqlalchemy`, `arazzo` /
   *   `workflow`, or `openapi`).
   * @return A [Result] indicating success or a [CddError].
   */
  fun synchronize(inputDir: String, truth: String): Result<Unit> {
    val dir = File(inputDir)
    if (!dir.exists() || !dir.isDirectory) {
      return CddError.InvalidDirectoryError(inputDir, "Input must be a directory: $inputDir")
          .toFailure()
    }

    val ktFiles = dir.walk().toList().filter { it.isFile && it.extension == "kt" }
    val allCode = ktFiles.joinToString("\n") { it.readText() }
    val basePackage = extractBasePackage(allCode)

    val dtoParser = DtoParser()
    val currentSchemas = dtoParser.parse(allCode)

    if (truth == "class") {
      val daoGen = DaoGenerator()
      val newDaosMap = daoGen.generateDaos(basePackage, currentSchemas)
      for ((relativePath, content) in newDaosMap) {
        val daoFile = File(dir, relativePath)
        writeToFile(daoFile.absolutePath, content).getOrElse {
          return Result.failure(it)
        }
      }
    } else if (truth == "arazzo" || truth == "workflow") {
      val arazzoOut = File(dir, "arazzo_sync.json")
      return psi.arazzo.ArazzoApiGenerator.generateToArazzoResult(inputDir, arazzoOut.absolutePath)
    } else if (truth == "dao" || truth == "sqlalchemy") {
      writeModelSchemas(dir, basePackage, parseDaoTableSchemas(allCode)).getOrElse {
        return Result.failure(it)
      }
    } else if (truth == "openapi") {
      val specFile =
          listOf(File(dir, "openapi.json"), File(dir, "openapi_sync.json")).firstOrNull {
            it.exists() && it.isFile
          }
      if (specFile != null) {
        val definition =
            openapi.OpenApiParser().parseStringResult(specFile.readText()).getOrElse {
              return Result.failure(it)
            }
        val schemas =
            if (definition.components != null) definition.components.schemas.values else emptyList()
        writeModelSchemas(dir, basePackage, schemas).getOrElse {
          return Result.failure(it)
        }
      }
    } else {
      return CddError.CliUsageError("Unsupported --truth target: $truth").toFailure()
    }

    val openApiOut = File(dir, "openapi_sync.json")
    ApiGenerator.generateToOpenApi(inputDir, openApiOut.absolutePath).getOrElse {
      return Result.failure(it)
    }
    return Result.success(Unit)
  }
}
