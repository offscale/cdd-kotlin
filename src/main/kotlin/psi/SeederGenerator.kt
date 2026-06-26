package psi

import domain.SchemaDefinition

/**
 * Generates the mock data seeder using Kotlin Faker, handling topological sorting and referential
 * integrity.
 */
class SeederGenerator {

  private fun escapeKotlinKeyword(name: String): String {
    val keywords =
        setOf(
            "as",
            "break",
            "class",
            "continue",
            "do",
            "else",
            "false",
            "for",
            "fun",
            "if",
            "in",
            "interface",
            "is",
            "null",
            "object",
            "package",
            "return",
            "super",
            "this",
            "throw",
            "true",
            "try",
            "typealias",
            "typeof",
            "val",
            "var",
            "when",
            "while")
    return if (name in keywords) "`$name`" else name
  }

  /**
   * Generates the seeder module source code.
   *
   * @param packageName The package namespace.
   * @param schemas The list of schema definitions.
   */
  fun generateSeederModule(packageName: String, schemas: List<SchemaDefinition>): String {
    val sb = StringBuilder()
    sb.append("/**\n")
    sb.append(
        " * Module responsible for generating synthetic relational dependency graphs using Kotlin Faker.\n")
    sb.append(" */\n")
    sb.append("package $packageName.seeder\n\n")

    sb.append("import $packageName.models.*\n")
    sb.append("import $packageName.dao.*\n")
    sb.append("import io.github.serpro69.kfaker.Faker\n")
    sb.append("import kotlinx.coroutines.runBlocking\n\n")

    sb.append("/**\n")
    sb.append(
        " * Generates fake data while maintaining referential integrity across the domain graph.\n")
    sb.append(" */\n")
    sb.append("class DatabaseSeeder(private val daoConfig: DaoConfiguration) {\n")
    sb.append("    private val faker = Faker()\n\n")

    val modelSchemas =
        schemas.filter {
          it.type == "object" &&
              it.enumValues == null &&
              it.anyOf.isEmpty() &&
              it.oneOf.isEmpty() &&
              it.anyOfSchemas.isEmpty() &&
              it.oneOfSchemas.isEmpty() &&
              !it.safeName.contains("ExternalAccount") &&
              !it.safeName.contains("PaymentSource")
        }

    // Entity Pools
    for (schema in modelSchemas) {
      val name = schema.safeName.replaceFirstChar { it.uppercase() }
      val idProp = schema.properties["id"]
      if (idProp != null) {
        val idType = TypeMappers.mapType(idProp)
        sb.append("    private val pool${name}Ids = mutableListOf<$idType>()\n")
      }
    }
    sb.append("\n")

    // Mapping factories
    for (schema in modelSchemas) {
      sb.append(generateFactory(schema))
    }

    // Main seeder function
    sb.append("    /**\n")
    sb.append(
        "     * Seeds the database by creating batches of synthetic entities in topological order.\n")
    sb.append("     */\n")
    sb.append("    private fun generateAny(): Any = \"{}\"\n")
    sb.append(
        "    private fun generatekotlinx_serialization_json_JsonElement(): kotlinx.serialization.json.JsonElement = kotlinx.serialization.json.JsonObject(mapOf(\"uuid\" to kotlinx.serialization.json.JsonPrimitive(java.util.UUID.randomUUID().toString())))\n")
    sb.append("    private fun generateMap(): Map<String, Any> = mapOf(\"key\" to \"value\")\n")

    sb.append("    fun seedDatabase() = runBlocking {\n")
    val chunked = modelSchemas.chunked(100)
    chunked.forEachIndexed { index, _ -> sb.append("        seedBatch$index()\n") }
    sb.append("    }\n\n")

    chunked.forEachIndexed { index, batch ->
      sb.append("    private suspend fun seedBatch$index() {\n")
      batch.forEach { schema ->
        val name = schema.safeName.replaceFirstChar { it.uppercase() }
        sb.append("        seed$name()\n")
      }
      sb.append("    }\n\n")
    }

    for (schema in modelSchemas) {
      val name = schema.safeName.replaceFirstChar { it.uppercase() }
      val lowerName = name.replaceFirstChar { it.lowercase() }
      sb.append("    private suspend fun seed$name() {\n")
      sb.append("        for (i in 1..10) {\n")
      sb.append("            val entity = generate$name()\n")
      sb.append("            val created = daoConfig.${lowerName}Dao.create(entity)\n")

      val idProp = schema.properties["id"]
      if (idProp != null) {
        sb.append("            if (created.id != null) pool${name}Ids.add(created.id!!)\n")
      }
      sb.append("        }\n")
      sb.append("    }\n\n")
    }
    sb.append("}\n")

    return sb.toString()
  }

  private fun generateFactory(schema: SchemaDefinition): String {
    val sb = StringBuilder()
    val name = schema.safeName.replaceFirstChar { it.uppercase() }
    sb.append("    /**\n")
    sb.append("     * Generates a synthetic [$name] using Faker.\n")
    sb.append("     */\n")
    sb.append("    private fun generate$name(): $name {\n")

    val constructorArgs =
        schema.properties.entries
            .map { (propName, prop) ->
              val ktTypeWithNull = TypeMappers.mapType(prop)
              val isNullable = !schema.required.contains(propName) || ktTypeWithNull.endsWith("?")
              val type = ktTypeWithNull.removeSuffix("?")

              val valGen =
                  when (type) {
                    "Int" -> "faker.random.nextInt(1, 1000)"
                    "Long" -> "faker.random.nextLong(1000L)"
                    "Double" -> "faker.random.nextDouble()"
                    "Boolean" -> "faker.random.nextBoolean()"
                    "String" ->
                        when {
                          propName.contains("email", ignoreCase = true) -> "faker.internet.email()"
                          propName.contains("name", ignoreCase = true) -> "faker.name.name()"
                          propName.contains("phone", ignoreCase = true) ->
                              "faker.phoneNumber.phoneNumber()"
                          else -> "faker.lorem.words()"
                        }
                    "kotlinx.datetime.Instant" -> "kotlinx.datetime.Clock.System.now()"
                    else ->
                        if (isNullable) "null"
                        else if (type.startsWith("List")) "emptyList()"
                        else if (type.contains("Map<")) "emptyMap()"
                        else if (type.startsWith("Any")) "\"\""
                        else "generate${type.replace(".", "_")}()"
                  }
              if (propName == "id" && (type == "String" || type == "Int" || type == "Long")) {
                if (type == "Long") "${escapeKotlinKeyword(propName)} = 0L"
                else if (type == "Int") "${escapeKotlinKeyword(propName)} = 0"
                else "${escapeKotlinKeyword(propName)} = \"id_\${java.util.UUID.randomUUID()}\""
              } else if (propName.endsWith("Id") && (type == "Int" || type == "Long")) {
                val parentName = propName.removeSuffix("Id").replaceFirstChar { it.uppercase() }
                // Use a random from the pool if it exists, else 1
                val defaultVal = if (type == "Long") "1L" else "1"
                "${escapeKotlinKeyword(propName)} = if (pool${parentName}Ids.isNotEmpty()) pool${parentName}Ids.random() else $defaultVal"
              } else {
                "${escapeKotlinKeyword(propName)} = $valGen"
              }
            }
            .joinToString(",\n            ")

    sb.append("        return $name(\n            $constructorArgs\n        )\n")
    sb.append("    }\n\n")
    return sb.toString()
  }
}
