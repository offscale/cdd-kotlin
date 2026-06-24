package psi

import domain.SchemaDefinition

/**
 * Generates Data Access Objects (DAOs) for the mock server architecture.
 *
 * It produces:
 * 1. Abstract interfaces for each domain schema.
 * 2. Stub DAOs that return empty responses or NotImplementedError.
 * 3. Concrete DAOs that use an ORM (e.g., Exposed) to interact with a real or ephemeral database.
 * 4. A Dependency Injection/Factory routine to wire the appropriate DAO based on the environment.
 */
class DaoGenerator {

  /**
   * Generates the entire DAO module source code as a single string.
   *
   * @param packageName The package namespace for the DAOs.
   * @param schemas The list of schema definitions to generate DAOs for.
   */
  fun generateDaos(packageName: String, schemas: List<SchemaDefinition>): Map<String, String> {
    val modelSchemas =
        schemas.filter {
          it.type == "object" && it.enumValues == null && it.properties.isNotEmpty()
        }

    val results = mutableMapOf<String, String>()

    // 1. Generate individual DAO files
    for (schema in modelSchemas) {
      val sb = StringBuilder()
      sb.append("package $packageName.dao\n\n")

      sb.append("import $packageName.models.*\n")
      sb.append("import org.jetbrains.exposed.sql.*\n")
      sb.append("import org.jetbrains.exposed.sql.transactions.transaction\n")
      sb.append("import kotlinx.coroutines.Dispatchers\n")
      sb.append("import kotlinx.coroutines.withContext\n")
      sb.append("import kotlinx.serialization.encodeToString\n")
      sb.append("import kotlinx.serialization.json.Json\n\n")

      sb.append(generateAbstractInterface(schema))
      sb.append(generateStubDao(schema))
      sb.append(generateConcreteDao(schema))

      val name = schema.name.replaceFirstChar { it.uppercase() }
      results["dao/${name}Dao.kt"] = sb.toString()
    }

    // 2. Generate Factory / DI routine
    val factorySb = StringBuilder()
    factorySb.append("package $packageName.dao\n\n")
    factorySb.append("import $packageName.models.*\n\n")
    factorySb.append(generateDaoFactory(modelSchemas))
    results["dao/DaoFactory.kt"] = factorySb.toString()

    return results
  }

  private fun generateAbstractInterface(schema: SchemaDefinition): String {
    val sb = StringBuilder()
    val name = schema.name.replaceFirstChar { it.uppercase() }
    sb.append("/**\n")
    sb.append(" * Data Access Object interface for [$name].\n")
    sb.append(" */\n")
    sb.append("interface ${name}Dao {\n")
    sb.append("    /**\n")
    sb.append("     * Retrieves a list of [$name] objects.\n")
    sb.append("     */\n")
    sb.append("    suspend fun getAll(): List<$name>\n\n")

    val idProp = schema.properties["id"]
    if (idProp != null) {
      val idType = TypeMappers.mapType(idProp)
      sb.append("    /**\n")
      sb.append("     * Retrieves a single [$name] by its ID.\n")
      sb.append("     * @param id The ID to lookup.\n")
      sb.append("     */\n")
      sb.append("    suspend fun getById(id: $idType): $name?\n\n")

      sb.append("    /**\n")
      sb.append("     * Deletes a single [$name] by its ID.\n")
      sb.append("     * @param id The ID to delete.\n")
      sb.append("     */\n")
      sb.append("    suspend fun delete(id: $idType): Boolean\n\n")
    }

    sb.append("    /**\n")
    sb.append("     * Creates a new [$name].\n")
    sb.append("     * @param entity The entity to create.\n")
    sb.append("     */\n")
    sb.append("    suspend fun create(entity: $name): $name\n")
    sb.append("}\n\n")
    return sb.toString()
  }

  private fun generateStubDao(schema: SchemaDefinition): String {
    val sb = StringBuilder()
    val name = schema.name.replaceFirstChar { it.uppercase() }
    sb.append("/**\n")
    sb.append(" * Traditional Scaffold Stub DAO for [$name].\n")
    sb.append(" * Throws NotImplementedError for all operations.\n")
    sb.append(" */\n")
    sb.append("class Stub${name}Dao : ${name}Dao {\n")
    sb.append(
        "    override suspend fun getAll(): List<$name> = throw NotImplementedError(\"Stub DAO method not implemented.\")\n")

    val idProp = schema.properties["id"]
    if (idProp != null) {
      val idType = TypeMappers.mapType(idProp)
      sb.append(
          "    override suspend fun getById(id: $idType): $name? = throw NotImplementedError(\"Stub DAO method not implemented.\")\n")
      sb.append(
          "    override suspend fun delete(id: $idType): Boolean = throw NotImplementedError(\"Stub DAO method not implemented.\")\n")
    }

    sb.append(
        "    override suspend fun create(entity: $name): $name = throw NotImplementedError(\"Stub DAO method not implemented.\")\n")
    sb.append("}\n\n")
    return sb.toString()
  }

  private fun generateConcreteDao(schema: SchemaDefinition): String {
    val sb = StringBuilder()
    val name = schema.name.replaceFirstChar { it.uppercase() }
    val tableName = "${name}Table"

    sb.append("/**\n")
    sb.append(" * Exposed Table definition for [$name].\n")
    sb.append(" */\n")
    sb.append("object $tableName : Table(\"${name.lowercase()}s\") {\n")

    for ((propName, prop) in schema.properties) {
      val ktTypeWithNull = TypeMappers.mapType(prop)
      val isNullable = !schema.required.contains(propName) || ktTypeWithNull.endsWith("?")
      val ktType = ktTypeWithNull.removeSuffix("?")
      val dbTypeBase =
          when (ktType) {
            "Int" -> "integer(\"$propName\")"
            "Long" -> "long(\"$propName\")"
            "Double" -> "double(\"$propName\")"
            "Boolean" -> "bool(\"$propName\")"
            "String" -> "varchar(\"$propName\", 255)"
            else -> "text(\"$propName\")"
          }
      val dbType = if (isNullable && propName != "id") "$dbTypeBase.nullable()" else dbTypeBase
      if (propName == "id") {
        sb.append("    val id = $dbType.autoIncrement()\n")
        sb.append("    override val primaryKey = PrimaryKey(id)\n")
      } else {
        sb.append("    val $propName = $dbType\n")
      }
    }
    sb.append("}\n\n")

    sb.append("/**\n")
    sb.append(" * Concrete DB-backed DAO for [$name] using JetBrains Exposed.\n")
    sb.append(" */\n")
    sb.append("class Concrete${name}Dao : ${name}Dao {\n")

    // Row to Entity mapping
    sb.append("    private fun toEntity(row: ResultRow): $name = $name(\n")
    for ((propName, prop) in schema.properties) {
      val ktTypeWithNull = TypeMappers.mapType(prop)
      val isNullable = !schema.required.contains(propName) || ktTypeWithNull.endsWith("?")
      val ktType = ktTypeWithNull.removeSuffix("?")
      val isComplex =
          ktType != "Int" &&
              ktType != "Long" &&
              ktType != "Double" &&
              ktType != "Boolean" &&
              ktType != "String"
      if (isComplex) {
        if (isNullable) {
          sb.append(
              "        $propName = row[$tableName.$propName]?.let { Json.decodeFromString(it) },\n")
        } else {
          sb.append("        $propName = Json.decodeFromString(row[$tableName.$propName]),\n")
        }
      } else {
        sb.append("        $propName = row[$tableName.$propName],\n")
      }
    }
    sb.append("    )\n\n")

    sb.append("    override suspend fun getAll(): List<$name> = withContext(Dispatchers.IO) {\n")
    sb.append("        transaction { $tableName.selectAll().map { toEntity(it) } }\n")
    sb.append("    }\n\n")

    val idProp = schema.properties["id"]
    if (idProp != null) {
      val idType = TypeMappers.mapType(idProp)
      sb.append(
          "    override suspend fun getById(id: $idType): $name? = withContext(Dispatchers.IO) {\n")
      sb.append(
          "        transaction { $tableName.selectAll().where { $tableName.id eq id }.map { toEntity(it) }.singleOrNull() }\n")
      sb.append("    }\n\n")

      sb.append(
          "    override suspend fun delete(id: $idType): Boolean = withContext(Dispatchers.IO) {\n")
      sb.append(
          "        transaction { $tableName.deleteWhere { with(SqlExpressionBuilder) { $tableName.id eq id } } > 0 }\n")
      sb.append("    }\n\n")
    }

    sb.append(
        "    override suspend fun create(entity: $name): $name = withContext(Dispatchers.IO) {\n")
    sb.append("        transaction {\n")
    if (idProp != null) {
      sb.append("            val insertedId = $tableName.insert { \n")
    } else {
      sb.append("            $tableName.insert { \n")
    }
    for ((propName, prop) in schema.properties) {
      if (propName != "id") {
        val ktTypeWithNull = TypeMappers.mapType(prop)
        val isNullable = !schema.required.contains(propName) || ktTypeWithNull.endsWith("?")
        val ktType = ktTypeWithNull.removeSuffix("?")
        val isComplex =
            ktType != "Int" &&
                ktType != "Long" &&
                ktType != "Double" &&
                ktType != "Boolean" &&
                ktType != "String"
        if (isComplex) {
          if (isNullable) {
            sb.append(
                "                it[$tableName.$propName] = entity.$propName?.let { v -> Json.encodeToString(v) }\n")
          } else {
            sb.append(
                "                it[$tableName.$propName] = Json.encodeToString(entity.$propName)\n")
          }
        } else {
          sb.append("                it[$tableName.$propName] = entity.$propName\n")
        }
      }
    }
    if (idProp != null) {
      sb.append("            } [${tableName}.id]\n")
      sb.append("            entity.copy(id = insertedId)\n")
    } else {
      sb.append("            }\n")
      sb.append("            entity\n")
    }
    sb.append("        }\n")
    sb.append("    }\n")
    sb.append("}\n\n")
    return sb.toString()
  }

  private fun generateDaoFactory(schemas: List<SchemaDefinition>): String {
    val sb = StringBuilder()
    sb.append("/**\n")
    sb.append(" * Configuration object containing DAOs.\n")
    sb.append(" */\n")
    sb.append("data class DaoConfiguration(\n")
    for (schema in schemas) {
      val name = schema.name.replaceFirstChar { it.uppercase() }
      sb.append("    val ${name.replaceFirstChar { it.lowercase() }}Dao: ${name}Dao,\n")
    }
    sb.append(")\n\n")

    sb.append("/**\n")
    sb.append(" * Dependency Injection routing for DAOs.\n")
    sb.append(" */\n")
    sb.append("object DaoFactory {\n")
    sb.append("    /**\n")
    sb.append("     * Instantiates the correct DAOs based on configuration.\n")
    sb.append(
        "     * @param useConcrete If true, initializes concrete DAOs (and requires DB setup). Otherwise, initializes Stub DAOs.\n")
    sb.append("     */\n")
    sb.append("    fun create(useConcrete: Boolean): DaoConfiguration {\n")
    sb.append("        return if (useConcrete) {\n")
    sb.append("            DaoConfiguration(\n")
    for (schema in schemas) {
      val name = schema.name.replaceFirstChar { it.uppercase() }
      sb.append(
          "                ${name.replaceFirstChar { it.lowercase() }}Dao = Concrete${name}Dao(),\n")
    }
    sb.append("            )\n")
    sb.append("        } else {\n")
    sb.append("            DaoConfiguration(\n")
    for (schema in schemas) {
      val name = schema.name.replaceFirstChar { it.uppercase() }
      sb.append(
          "                ${name.replaceFirstChar { it.lowercase() }}Dao = Stub${name}Dao(),\n")
    }
    sb.append("            )\n")
    sb.append("        }\n")
    sb.append("    }\n")
    sb.append("}\n")
    return sb.toString()
  }
}
