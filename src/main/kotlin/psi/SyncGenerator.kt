/**
 * Bidirectional synchronization engine for Kotlin domain models, DAOs, and OpenAPI specifications.
 */
package psi

import domain.CddError
import domain.SchemaDefinition
import domain.SchemaProperty
import domain.toFailure
import java.io.File
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import writeToFile

/**
 * Handles bi-directional synchronization of domain models, DAOs/ORM constraints, and OpenAPI specs.
 */
object SyncGenerator {
  private fun extractBasePackage(allCode: String): String {
    val file = PsiInfrastructure.createPsiFactory().createFile("SyncSource.kt", allCode)
    val pkgFqName = file.packageDirective!!.text.removePrefix("package").trim()
    val stripped =
        pkgFqName
            .removeSuffix(".models")
            .removeSuffix(".dao")
            .removeSuffix(".network")
            .removeSuffix(".server")
            .removePrefix(".")
            .trim()
    return stripped.ifBlank { "org.example" }
  }

  private fun writeModelSchemas(
      dir: File,
      basePackage: String,
      schemas: Collection<SchemaDefinition>
  ): Result<Unit> {
    val dtoGen = DtoGenerator()
    val dtoMerger = DtoMerger()
    for (schema in schemas) {
      val modelFile = File(dir, "models/${schema.safeName}.kt")
      val dtoSource =
          if (modelFile.isFile) {
            dtoMerger.mergeDto(modelFile.readText(), schema).getOrElse {
              dtoGen.generateDto("$basePackage.models", schema).text
            }
          } else {
            dtoGen.generateDto("$basePackage.models", schema).text
          }
      writeToFile(modelFile.absolutePath, dtoSource).getOrElse {
        return Result.failure(it)
      }
    }
    return Result.success(Unit)
  }

  private fun parseDaoTableSchemas(allCode: String): List<SchemaDefinition> {
    val file = PsiInfrastructure.createPsiFactory().createFile("DaoTables.kt", allCode)
    val objects =
        file.collectDescendantsOfType<KtObjectDeclaration>().filter { !it.isObjectLiteral() }
    val schemas = mutableListOf<SchemaDefinition>()
    for (obj in objects) {
      val objName = obj.name!!
      if (!objName.endsWith("Table")) continue
      val modelName = objName.removeSuffix("Table")
      if (modelName.isBlank()) continue
      val props = LinkedHashMap<String, SchemaProperty>()
      val required = mutableListOf<String>()
      for (prop in obj.body?.properties.orEmpty()) {
        val parsedCol = parseColumnProperty(prop) ?: continue
        props[parsedCol.name] = parsedCol.schema
        if (!parsedCol.isNullable) {
          required.add(parsedCol.name)
        }
      }
      schemas.add(
          SchemaDefinition(
              name = modelName, type = "object", properties = props, required = required))
    }
    return schemas
  }

  private data class ParsedDaoColumn(
      val name: String,
      val schema: SchemaProperty,
      val isNullable: Boolean
  )

  private fun parseColumnProperty(prop: KtProperty): ParsedDaoColumn? {
    val propName = prop.name!!
    if (!propName.endsWith("Column")) return null
    val colName = propName.removeSuffix("Column")
    if (prop.initializer == null) return null
    val callChain =
        prop.collectDescendantsOfType<KtCallExpression>().map { it.calleeExpression!!.text }
    val colKind =
        callChain.firstOrNull {
          it in setOf("integer", "long", "double", "bool", "varchar", "text")
        } ?: return null
    val isNullable = callChain.contains("nullable")
    val oasType =
        when (colKind) {
          "integer",
          "long" -> "integer"
          "double" -> "number"
          "bool" -> "boolean"
          else -> "string"
        }
    val format = if (colKind == "long") "int64" else null
    val types = if (isNullable) setOf(oasType, "null") else setOf(oasType)
    return ParsedDaoColumn(
        name = colName,
        schema = SchemaProperty(types = types, format = format),
        isNullable = isNullable)
  }

  /**
   * Synchronizes the project in [inputDir] to align with [truth].
   *
   * @param inputDir The root directory of the source code.
   * @param truth The designated source of truth (`class` / `model`, `dao` / `sqlalchemy`, `arazzo`
   *   / `workflow`, or `openapi`).
   * @return A [Result] indicating success or a [CddError].
   */
  fun synchronize(inputDir: String, truth: String): Result<Unit> {
    val dir = File(inputDir)
    if (!dir.exists() || !dir.isDirectory) {
      return CddError.InvalidDirectoryError(inputDir, "Input must be a directory: $inputDir")
          .toFailure()
    }

    val ktFiles = dir.walk().toList().filter { it.isFile && it.extension == "kt" }
    val allCode = ktFiles.joinToString("\n".trimEnd() + '\n') { it.readText() }
    val basePackage = extractBasePackage(allCode)

    val dtoParser = DtoParser()
    val currentSchemas = dtoParser.parse(allCode)

    if (truth == "class" || truth == "model") {
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
