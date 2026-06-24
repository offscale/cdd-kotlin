package psi

import domain.SchemaDefinition

/**
 * Generates the mock data seeder using Kotlin Faker, handling topological sorting and referential
 * integrity.
 */
class SeederGenerator {

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
          it.type == "object" && it.enumValues == null && it.properties.isNotEmpty()
        }

    // Entity Pools
    for (schema in modelSchemas) {
      val name = schema.name.replaceFirstChar { it.uppercase() }
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
    sb.append("    fun seedDatabase() = runBlocking {\n")
    for (schema in modelSchemas) {
      val name = schema.name.replaceFirstChar { it.uppercase() }
      val lowerName = name.replaceFirstChar { it.lowercase() }
      sb.append("        for (i in 1..10) {\n")
      sb.append("            val entity = generate$name()\n")
      sb.append("            val created = daoConfig.${lowerName}Dao.create(entity)\n")

      val idProp = schema.properties["id"]
      if (idProp != null) {
        sb.append("            if (created.id != null) pool${name}Ids.add(created.id!!)\n")
      }
      sb.append("        }\n")
    }
    sb.append("    }\n")
    sb.append("}\n")

    return sb.toString()
  }

  private fun generateFactory(schema: SchemaDefinition): String {
    val sb = StringBuilder()
    val name = schema.name.replaceFirstChar { it.uppercase() }
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
                        else if (type.startsWith("List")) "emptyList()" else "generate$type()"
                  }
              if (propName == "id") {
                if (type == "Long") "$propName = 0L" else "$propName = 0"
              } else if (propName.endsWith("Id") && (type == "Int" || type == "Long")) {
                val parentName = propName.removeSuffix("Id").replaceFirstChar { it.uppercase() }
                // Use a random from the pool if it exists, else 1
                val defaultVal = if (type == "Long") "1L" else "1"
                "$propName = if (pool${parentName}Ids.isNotEmpty()) pool${parentName}Ids.random() else $defaultVal"
              } else {
                "$propName = $valGen"
              }
            }
            .joinToString(",\n            ")

    sb.append("        return $name(\n            $constructorArgs\n        )\n")
    sb.append("    }\n\n")
    return sb.toString()
  }
}
