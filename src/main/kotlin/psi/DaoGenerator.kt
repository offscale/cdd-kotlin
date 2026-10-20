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
            appendAbstractInterface(schema)
            appendStubDao(schema)
            appendConcreteDao(schema)
          }
    }

    // 2. Generate Factory / DI routine
    results["dao/DaoFactory.kt"] =
        buildKotlinFile("$packageName.dao") {
          import("$packageName.models.*")
          appendDaoFactory(modelSchemas)
        }

    return results
  }

  private fun KotlinSourceBuilder.appendAbstractInterface(schema: SchemaDefinition) {
    val name = capitalizeName(schema.safeName)
    kdoc("Data Access Object interface for [$name].")
    block("interface ${name}Dao") {
      line("/** Whether this DAO is an unimplemented stub. */")
      line("val isStub: Boolean get() = false")
      line()
      kdoc("Retrieves a list of [$name] objects.")
      line("suspend fun getAll(): List<$name>")
      line()

      val idProp = schema.properties["id"]
      if (idProp != null) {
        val idType = TypeMappers.mapType(idProp)
        kdoc("Retrieves a single [$name] by its ID.", "@param id The ID to lookup.")
        line("suspend fun getById(id: $idType): $name?")
        line()
        kdoc("Deletes a single [$name] by its ID.", "@param id The ID to delete.")
        line("suspend fun delete(id: $idType): Boolean")
        line()
      }

      kdoc("Creates a new [$name].", "@param entity The entity to create.")
      line("suspend fun create(entity: $name): $name")
    }
    line()
  }

  private fun KotlinSourceBuilder.appendStubDao(schema: SchemaDefinition) {
    val name = capitalizeName(schema.safeName)
    kdoc("Traditional Scaffold Stub DAO for [$name].")
    block("class Stub${name}Dao : ${name}Dao") {
      line("override val isStub: Boolean = true")
      line("override suspend fun getAll(): List<$name> = emptyList()")

      val idProp = schema.properties["id"]
      if (idProp != null) {
        val idType = TypeMappers.mapType(idProp)
        line("override suspend fun getById(id: $idType): $name? = null")
        line("override suspend fun delete(id: $idType): Boolean = false")
      }

      line("override suspend fun create(entity: $name): $name = entity")
    }
    line()
  }

  private fun KotlinSourceBuilder.appendConcreteDao(schema: SchemaDefinition) {
    val name = capitalizeName(schema.safeName)
    val tableName = "${name}Table"

    kdoc("Exposed Table definition for [$name].")
    block("""object $tableName : Table("${name.lowercase()}s")""") {
      if (schema.properties.isEmpty()) {
        line("""val _dummy = integer("_dummy").default(0)""")
      }
      for ((propName, prop) in schema.properties) {
        val ktTypeWithNull = TypeMappers.mapType(prop)
        val isNullable = !schema.required.contains(propName) || ktTypeWithNull.endsWith("?")
        val ktType = ktTypeWithNull.removeSuffix("?")
        val dbTypeBase =
            when (ktType) {
              "Int" -> """integer("$propName")"""
              "Long" -> """long("$propName")"""
              "Double" -> """double("$propName")"""
              "Boolean" -> """bool("$propName")"""
              "String" -> """varchar("$propName", 255)"""
              else -> """text("$propName")"""
            }
        val dbType = if (isNullable && propName != "id") "$dbTypeBase.nullable()" else dbTypeBase
        if (propName == "id") {
          val isAutoInc = ktTypeWithNull == "Int" || ktTypeWithNull == "Long"
          if (isAutoInc) {
            line("val idColumn = $dbType.autoIncrement()")
          } else {
            line("val idColumn = $dbType")
          }
          line("override val primaryKey = PrimaryKey(idColumn)")
        } else {
          line("val ${propName}Column = $dbType")
        }
      }
    }
    line()

    kdoc("Concrete DB-backed DAO for [$name] using JetBrains Exposed.")
    block("class Concrete${name}Dao : ${name}Dao") {
      line("private fun toEntity(row: ResultRow): $name = $name(")
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
            line(
                "    ${escapeKotlinKeyword(propName)} = row[$tableName.${propName}Column]?.let { Json.decodeFromString(it) },")
          } else {
            line(
                "    ${escapeKotlinKeyword(propName)} = Json.decodeFromString(row[$tableName.${propName}Column]),")
          }
        } else {
          line("    ${escapeKotlinKeyword(propName)} = row[$tableName.${propName}Column],")
        }
      }
      line(")")
      line()

      block("override suspend fun getAll(): List<$name> = withContext(Dispatchers.IO)") {
        line("transaction { $tableName.selectAll().map { toEntity(it) } }")
      }
      line()

      val idProp = schema.properties["id"]
      if (idProp != null) {
        val idType = TypeMappers.mapType(idProp)
        val eqExpr =
            if (idType != "String" && idType != "Int" && idType != "Long") "Json.encodeToString(id)"
            else "id"
        block("override suspend fun getById(id: $idType): $name? = withContext(Dispatchers.IO)") {
          line(
              "transaction { $tableName.selectAll().where { $tableName.idColumn eq $eqExpr }.map { toEntity(it) }.singleOrNull() }")
        }
        line()

        block("override suspend fun delete(id: $idType): Boolean = withContext(Dispatchers.IO)") {
          line(
              "transaction { $tableName.deleteWhere { with(SqlExpressionBuilder) { $tableName.idColumn eq $eqExpr } } > 0 }")
        }
        line()
      }

      block("override suspend fun create(entity: $name): $name = withContext(Dispatchers.IO)") {
        block("transaction") {
          val isAutoInc =
              idProp != null &&
                  (TypeMappers.mapType(idProp) == "Int" || TypeMappers.mapType(idProp) == "Long")
          val insertHeader =
              if (isAutoInc) "val insertedId = $tableName.insert" else "$tableName.insert"
          val insertSuffix = if (isAutoInc) " [$tableName.idColumn]" else ""
          block(insertHeader, suffix = insertSuffix) {
            if (schema.properties.isEmpty()) {
              line("it[$tableName._dummy] = 0")
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
                    val q = '"'
                    val bs = 92.toChar()
                    val d = '$'
                    val fallback =
                        if (propName == "id")
                            "$q{ $bs${q}uuid$bs$q: $bs${q}id_$d{java.util.UUID.randomUUID()}$bs$q }$q"
                        else "null"
                    line(
                        "it[$tableName.${propName}Column] = entity.${escapeKotlinKeyword(propName)}?.let { v -> Json.encodeToString(v) } ?: $fallback")
                  } else {
                    line(
                        "it[$tableName.${propName}Column] = Json.encodeToString(entity.${escapeKotlinKeyword(propName)})")
                  }
                } else {
                  if (propName == "id" && isNullable) {
                    val q = '"'
                    val d = '$'
                    line(
                        "it[$tableName.${propName}Column] = entity.${escapeKotlinKeyword(propName)} ?: ${q}id_$d{java.util.UUID.randomUUID()}$q")
                  } else {
                    line(
                        "it[$tableName.${propName}Column] = entity.${escapeKotlinKeyword(propName)}")
                  }
                }
              }
            }
          }
          if (isAutoInc) {
            line("entity.copy(id = insertedId)")
          } else {
            line("entity")
          }
        }
      }
    }
    line()
  }

  private fun KotlinSourceBuilder.appendDaoFactory(modelSchemas: List<SchemaDefinition>) {
    kdoc(
        "Configuration object containing strongly-typed DAOs.",
        "",
        "@property useConcrete Whether concrete database-backed DAOs are enabled.")
    block("class DaoConfiguration(val useConcrete: Boolean)") {
      for (schema in modelSchemas) {
        val name = capitalizeName(schema.safeName)
        val lowerName = decapitalizeName(name)
        line(
            "val ${lowerName}Dao: ${name}Dao get() = if (useConcrete) Concrete${name}Dao() else Stub${name}Dao()")
      }
    }
    line()

    kdoc("Dependency Injection routing for DAOs.")
    block("object DaoFactory") {
      kdoc(
          "Instantiates the correct DAOs based on configuration.",
          "@param useConcrete If true, initializes concrete DAOs (and requires DB setup). Otherwise, initializes Stub DAOs.",
          "@return The configured [DaoConfiguration].")
      line("fun create(useConcrete: Boolean): DaoConfiguration = DaoConfiguration(useConcrete)")
    }
  }
}
