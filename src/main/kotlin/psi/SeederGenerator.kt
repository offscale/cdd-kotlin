/** Referentially-consistent mock database seeder generator using Kotlin Faker. */
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
   * @return The generated DatabaseSeeder Kotlin source code.
   */
  fun generateSeederModule(packageName: String, schemas: List<SchemaDefinition>): String {
    val modelSchemas = filterModelSchemas(schemas)
    val chunked = modelSchemas.chunked(100)
    return buildKotlinFile {
      kdoc(
          "Module responsible for generating synthetic relational dependency graphs using Kotlin Faker.")
      line("package $packageName.seeder")
      line()
      line("import $packageName.models.*")
      line("import $packageName.dao.*")
      line("import io.github.serpro69.kfaker.Faker")
      line("import kotlinx.coroutines.runBlocking")
      line()
      kdoc("Generates fake data while maintaining referential integrity across the domain graph.")
      block("class DatabaseSeeder(private val daoConfig: DaoConfiguration)") {
        line("private val faker = Faker()")
        line()
        for (schema in modelSchemas) {
          val name = capitalizeName(schema.safeName)
          val idProp = schema.properties["id"]
          if (idProp != null) {
            val idType = TypeMappers.mapType(idProp)
            line("private val pool${name}Ids = mutableListOf<$idType>()")
          }
        }
        line()
        for (schema in modelSchemas) {
          appendFactory(schema)
        }
        kdoc("Seeds the database by creating batches of synthetic entities in topological order.")
        line(
            """private fun generatekotlinx_serialization_json_JsonElement(): kotlinx.serialization.json.JsonElement = kotlinx.serialization.json.JsonObject(mapOf("uuid" to kotlinx.serialization.json.JsonPrimitive(java.util.UUID.randomUUID().toString())))""")
        line("""private fun generateMap(): Map<String, Any> = mapOf("key" to "value")""")
        block("fun seedDatabase() = runBlocking") {
          chunked.forEachIndexed { index, _ -> line("seedBatch$index()") }
        }
        line()
        chunked.forEachIndexed { index, batch ->
          block("private suspend fun seedBatch$index()") {
            batch.forEach { schema ->
              val name = capitalizeName(schema.safeName)
              line("seed$name()")
            }
          }
          line()
        }
        for (schema in modelSchemas) {
          val name = capitalizeName(schema.safeName)
          val lowerName = decapitalizeName(name)
          block("private suspend fun seed$name()") {
            block("for (i in 1..10)") {
              line("val entity = generate$name()")
              line("val created = daoConfig.${lowerName}Dao.create(entity)")
              if (schema.properties["id"] != null) {
                line("val createdId = created.id")
                line("if (createdId != null) pool${name}Ids.add(createdId)")
              }
            }
          }
          line()
        }
      }
    }
  }

  private fun KotlinSourceBuilder.appendFactory(schema: SchemaDefinition) {
    val name = capitalizeName(schema.safeName)
    val q = '"'
    val d = '$'
    val nl = '\n'
    kdoc("Generates a synthetic [$name] using Faker.")
    block("private fun generate$name(): $name") {
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
                            propName.contains("email", ignoreCase = true) ->
                                "faker.internet.email()"
                            propName.contains("name", ignoreCase = true) -> "faker.name.name()"
                            propName.contains("phone", ignoreCase = true) ->
                                "faker.phoneNumber.phoneNumber()"
                            else -> "faker.lorem.words()"
                          }
                      else ->
                          if (isNullable) "null"
                          else if (type.startsWith("List")) "emptyList()"
                          else if (type.contains("Map<")) "emptyMap()"
                          else "generate${type.replace(".", "_")}()"
                    }
                if (propName == "id" && (type == "String" || type == "Int" || type == "Long")) {
                  if (type == "Long") "${escapeKotlinKeyword(propName)} = 0L"
                  else if (type == "Int") "${escapeKotlinKeyword(propName)} = 0"
                  else "${escapeKotlinKeyword(propName)} = ${q}id_$d{java.util.UUID.randomUUID()}$q"
                } else if (propName.endsWith("Id") && (type == "Int" || type == "Long")) {
                  val parentName = capitalizeName(propName.removeSuffix("Id"))
                  val defaultVal = if (type == "Long") "1L" else "1"
                  "${escapeKotlinKeyword(propName)} = if (pool${parentName}Ids.isNotEmpty()) pool${parentName}Ids.random() else $defaultVal"
                } else {
                  "${escapeKotlinKeyword(propName)} = $valGen"
                }
              }
              .joinToString(",$nl            ")
      line("return $name($nl            $constructorArgs$nl        )")
    }
    line()
  }
}
