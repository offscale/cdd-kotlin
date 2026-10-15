/** Exposed ORM table and Data Access Object (DAO) code generator. */
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
   * Generates the entire DAO module source code as a map of relative file paths to source strings.
   *
   * @param packageName The package namespace for the DAOs.
   * @param schemas The list of schema definitions to generate DAOs for.
   * @return Map of relative DAO file paths to generated Kotlin source strings.
   */
  fun generateDaos(packageName: String, schemas: List<SchemaDefinition>): Map<String, String> {
    val modelSchemas = filterModelSchemas(schemas)
    val results = mutableMapOf<String, String>()

    // 1. Generate individual DAO files
    for (schema in modelSchemas) {
      val name = capitalizeName(schema.safeName)
      results["dao/${name}Dao.kt"] =
          buildKotlinFile("$packageName.dao") {
            import(
                "$packageName.models.*",
                "org.jetbrains.exposed.sql.*",
                "org.jetbrains.exposed.sql.transactions.transaction",
                "kotlinx.coroutines.Dispatchers",
                "kotlinx.coroutines.withContext",
                "kotlinx.serialization.encodeToString",
                "kotlinx.serialization.json.Json")
            raw(generateAbstractInterface(schema))
            raw(generateStubDao(schema))
            raw(generateConcreteDao(schema))
          }
    }

    // 2. Generate Factory / DI routine
    results["dao/DaoFactory.kt"] =
        buildKotlinFile("$packageName.dao") {
          import("$packageName.models.*")
          raw(generateDaoFactory(modelSchemas))
        }

    return results
  }

  private fun generateAbstractInterface(schema: SchemaDefinition): String {
    val sb = StringBuilder()
    val name = capitalizeName(schema.safeName)
    sb.append("/**\n")
    sb.append(" * Data Access Object interface for [$name].\n")
    sb.append(" */\n")
    sb.append("interface ${name}Dao {\n")
    sb.append("    /** Whether this DAO is an unimplemented stub. */\n")
    sb.append("    val isStub: Boolean get() = false\n\n")
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
    val name = capitalizeName(schema.safeName)
    sb.append("/**\n")
    sb.append(" * Traditional Scaffold Stub DAO for [$name].\n")
    sb.append(" */\n")
    sb.append("class Stub${name}Dao : ${name}Dao {\n")
    sb.append("    override val isStub: Boolean = true\n")
    sb.append("    override suspend fun getAll(): List<$name> = emptyList()\n")

    val idProp = schema.properties["id"]
    if (idProp != null) {
      val idType = TypeMappers.mapType(idProp)
      sb.append("    override suspend fun getById(id: $idType): $name? = null\n")
      sb.append("    override suspend fun delete(id: $idType): Boolean = false\n")
    }

    sb.append("    override suspend fun create(entity: $name): $name = entity\n")
    sb.append("}\n\n")
    return sb.toString()
  }

  private fun generateConcreteDao(schema: SchemaDefinition): String {
    val sb = StringBuilder()
    val name = capitalizeName(schema.safeName)
    val tableName = "${name}Table"

    sb.append("/**\n")
    sb.append(" * Exposed Table definition for [$name].\n")
    sb.append(" */\n")
    sb.append("object $tableName : Table(\"${name.lowercase()}s\") {\n")

    if (schema.properties.isEmpty()) {
      sb.append("    val _dummy = integer(\"_dummy\").default(0)\n")
    }

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
        val ktTypeWithNullId = TypeMappers.mapType(schema.properties["id"]!!)
        val isAutoInc = ktTypeWithNullId == "Int" || ktTypeWithNullId == "Long"
        if (isAutoInc) {
          sb.append("    val idColumn = $dbType.autoIncrement()\n")
        } else {
          sb.append("    val idColumn = $dbType\n")
        }
        sb.append("    override val primaryKey = PrimaryKey(idColumn)\n")
      } else {
        sb.append("    val ${propName}Column = $dbType\n")
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
              "        ${escapeKotlinKeyword(propName)} = row[$tableName.${propName}Column]?.let { Json.decodeFromString(it) },\n")
        } else {
          sb.append(
              "        ${escapeKotlinKeyword(propName)} = Json.decodeFromString(row[$tableName.${propName}Column]),\n")
        }
      } else {
        sb.append("        ${escapeKotlinKeyword(propName)} = row[$tableName.${propName}Column],\n")
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
          "        transaction { $tableName.selectAll().where { $tableName.idColumn eq ${if (idType != "String" && idType != "Int" && idType != "Long") "Json.encodeToString(id)" else "id"} }.map { toEntity(it) }.singleOrNull() }\n")
      sb.append("    }\n\n")

      sb.append(
          "    override suspend fun delete(id: $idType): Boolean = withContext(Dispatchers.IO) {\n")
      sb.append(
          "        transaction { $tableName.deleteWhere { with(SqlExpressionBuilder) { $tableName.idColumn eq ${if (idType != "String" && idType != "Int" && idType != "Long") "Json.encodeToString(id)" else "id"} } } > 0 }\n")
      sb.append("    }\n\n")
    }

    sb.append(
        "    override suspend fun create(entity: $name): $name = withContext(Dispatchers.IO) {\n")
    sb.append("        transaction {\n")
    val isAutoInc =
        idProp != null &&
            (TypeMappers.mapType(idProp) == "Int" || TypeMappers.mapType(idProp) == "Long")
    if (isAutoInc) {
      sb.append("            val insertedId = $tableName.insert { \n")
    } else {
      sb.append("            $tableName.insert { \n")
    }
    if (schema.properties.isEmpty()) {
      sb.append("                it[$tableName._dummy] = 0\n")
    }
    for ((propName, prop) in schema.properties) {
      if (propName != "id" || !isAutoInc) {
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
            val fallback =
                if (propName == "id")
                    "\"{ \\\"uuid\\\": \\\"id_\${java.util.UUID.randomUUID()}\\\" }\""
                else "\"{}\""
            sb.append(
                "                it[$tableName.${propName}Column] = entity.${escapeKotlinKeyword(propName)}?.let { v -> Json.encodeToString(v) } ?: $fallback\n")
          } else {
            sb.append(
                "                it[$tableName.${propName}Column] = Json.encodeToString(entity.${escapeKotlinKeyword(propName)})\n")
          }
        } else {
          if (propName == "id" && isNullable) {
            sb.append(
                "                it[$tableName.${propName}Column] = entity.${escapeKotlinKeyword(propName)} ?: \"id_\${java.util.UUID.randomUUID()}\"\n")
          } else {
            sb.append(
                "                it[$tableName.${propName}Column] = entity.${escapeKotlinKeyword(propName)}\n")
          }
        }
      }
    }
    if (isAutoInc) {
      sb.append("            } [${tableName}.idColumn]\n")
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

  private fun generateDaoFactory(modelSchemas: List<SchemaDefinition>): String {
    val sb = StringBuilder()
    sb.append("/**\n")
    sb.append(" * Configuration object containing DAOs.\n")
    sb.append(" */\n")
    sb.append("class DaoConfiguration(private val daos: Map<String, Any>) {\n")
    for (schema in modelSchemas) {
      val name = capitalizeName(schema.safeName)
      val lowerName = decapitalizeName(name)
      sb.append(
          "    val ${lowerName}Dao: ${name}Dao get() = daos[\"${lowerName}Dao\"] as ${name}Dao\n")
    }
    sb.append("}\n\n")

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
    sb.append("        val map = mutableMapOf<String, Any>()\n")
    val chunkedDaos = modelSchemas.chunked(100)
    chunkedDaos.forEachIndexed { index, _ ->
      sb.append("        populateBatch$index(map, useConcrete)\n")
    }
    sb.append("        return DaoConfiguration(map)\n")
    sb.append("    }\n\n")

    chunkedDaos.forEachIndexed { index, batch ->
      sb.append(
          "    private fun populateBatch$index(map: MutableMap<String, Any>, useConcrete: Boolean) {\n")
      sb.append("        if (useConcrete) {\n")
      for (schema in batch) {
        val name = capitalizeName(schema.safeName)
        val lowerName = decapitalizeName(name)
        sb.append("            map[\"${lowerName}Dao\"] = Concrete${name}Dao()\n")
      }
      sb.append("        } else {\n")
      for (schema in batch) {
        val name = capitalizeName(schema.safeName)
        val lowerName = decapitalizeName(name)
        sb.append("            map[\"${lowerName}Dao\"] = Stub${name}Dao()\n")
      }
      sb.append("        }\n")
      sb.append("    }\n\n")
    }
    sb.append("}\n")
    return sb.toString()
  }
}
