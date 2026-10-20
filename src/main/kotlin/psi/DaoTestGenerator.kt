/** Unit test generator for Exposed DAOs and DaoFactory implementations. */
package psi

import domain.SchemaDefinition

/** Generates unit tests for the Data Access Objects (DAOs) and DaoFactory. */
class DaoTestGenerator {

  /**
   * Generates a Kotlin test file testing the DaoFactory and DAOs.
   *
   * @param packageName The package namespace for the DAOs.
   * @param schemas The list of schema definitions to generate tests for.
   * @return Map of relative test file paths to generated Kotlin test source code.
   */
  fun generateDaoTests(packageName: String, schemas: List<SchemaDefinition>): Map<String, String> {
    val results = mutableMapOf<String, String>()
    val modelSchemas = filterModelSchemas(schemas)

    results["dao/DaoFactoryTest.kt"] =
        buildKotlinFile("$packageName.dao") {
          import("$packageName.models.*", "kotlin.test.*", "kotlin.test.Test")
          appendFactoryTest(modelSchemas)
        }

    for (schema in modelSchemas) {
      val name = capitalizeName(schema.safeName)
      results["dao/Concrete${name}DaoTest.kt"] =
          buildKotlinFile("$packageName.dao") {
            import(
                "$packageName.models.*",
                "org.jetbrains.exposed.sql.Database",
                "org.jetbrains.exposed.sql.SchemaUtils",
                "org.jetbrains.exposed.sql.transactions.transaction",
                "kotlin.test.*",
                "kotlin.test.BeforeTest",
                "kotlin.test.Test",
                "kotlinx.coroutines.runBlocking")
            appendConcreteDaoTest(schema)
          }
    }

    return results
  }

  private fun KotlinSourceBuilder.appendFactoryTest(schemas: List<SchemaDefinition>) {
    kdoc("Tests for [DaoFactory].")
    block("class DaoFactoryTest") {
      line("@Test")
      block("fun `create with true returns concrete DAOs`()") {
        line("val config = DaoFactory.create(useConcrete = true)")
        for (schema in schemas) {
          val name = capitalizeName(schema.safeName)
          val lowerName = decapitalizeName(name)
          line("assertTrue(config.${lowerName}Dao is Concrete${name}Dao)")
        }
      }
      line()
      line("@Test")
      block("fun `create with false returns stub DAOs`()") {
        line("val config = DaoFactory.create(useConcrete = false)")
        for (schema in schemas) {
          val name = capitalizeName(schema.safeName)
          val lowerName = decapitalizeName(name)
          line("assertTrue(config.${lowerName}Dao is Stub${name}Dao)")
        }
      }
    }
    line()
  }

  private fun KotlinSourceBuilder.appendConcreteDaoTest(schema: SchemaDefinition) {
    val name = capitalizeName(schema.safeName)
    val tableName = "${name}Table"

    kdoc("Tests for [Concrete${name}Dao] using an ephemeral SQLite database.")
    block("class Concrete${name}DaoTest") {
      line("private lateinit var dao: Concrete${name}Dao")
      line()
      line("@BeforeTest")
      block("fun setup()") {
        val d = '$'
        line(
            """val dbFile = java.io.File.createTempFile("${name.lowercase()}_dao_", ".db").apply { deleteOnExit() }""")
        line(
            """Database.connect("jdbc:sqlite:$d{dbFile.absolutePath}", driver = "org.sqlite.JDBC")""")
        block("transaction") { line("SchemaUtils.create($tableName)") }
        line("dao = Concrete${name}Dao()")
      }
      line()
      line("@Test")
      block("fun `getAll returns empty list initially`() = runBlocking") {
        line("val items = dao.getAll()")
        line("assertTrue(items.isEmpty())")
      }
      line()

      val constructorArgs =
          schema.properties.entries
              .map { (propName, prop) ->
                val typeWithNull = TypeMappers.mapType(prop)
                val isNullable = !schema.required.contains(propName) || typeWithNull.endsWith("?")
                val type = typeWithNull.removeSuffix("?")
                val isComplex =
                    type != "Int" &&
                        type != "Long" &&
                        type != "Double" &&
                        type != "Boolean" &&
                        type != "String"

                val dummyVal =
                    if (isComplex) {
                      if (isNullable) "null"
                      else if (type.startsWith("List")) "emptyList()" else "$type()"
                    } else {
                      when (type) {
                        "Int" -> "1"
                        "Long" -> "1L"
                        "Double" -> "1.0"
                        "Boolean" -> "true"
                        else -> """"test""""
                      }
                    }
                val safePropName = escapeKotlinKeyword(propName)
                if (propName == "id") {
                  val idZero = if (type == "Long") "0L" else if (type == "Int") "0" else dummyVal
                  "$safePropName = $idZero"
                } else {
                  "$safePropName = $dummyVal"
                }
              }
              .joinToString(", ")

      val idProp = schema.properties["id"]
      if (idProp != null) {
        val idType = TypeMappers.mapType(idProp)
        val idZero = if (idType == "Long") "0L" else "0"
        val idIsNullable = !schema.required.contains("id")
        val createdIdExpr = if (idIsNullable) "created.id!!" else "created.id"

        line("@Test")
        block("fun `create inserts record and getAll retrieves it`() = runBlocking") {
          line("val entity = $name($constructorArgs)")
          line("val created = dao.create(entity)")
          line("assertNotEquals($idZero, created.id)")
          line("val items = dao.getAll()")
          line("assertEquals(1, items.size)")
        }
        line()

        line("@Test")
        block("fun `getById retrieves correct record`() = runBlocking") {
          line("val entity = $name($constructorArgs)")
          line("val created = dao.create(entity)")
          line("val fetched = dao.getById($createdIdExpr)")
          line("assertNotNull(fetched)")
          line("assertEquals(created.id, fetched?.id)")
        }
        line()

        line("@Test")
        block("fun `delete removes record`() = runBlocking") {
          line("val entity = $name($constructorArgs)")
          line("val created = dao.create(entity)")
          line("val deleted = dao.delete($createdIdExpr)")
          line("assertTrue(deleted)")
          line("val fetched = dao.getById($createdIdExpr)")
          line("assertNull(fetched)")
        }
      } else {
        line("@Test")
        block("fun `create inserts record and getAll retrieves it`() = runBlocking") {
          line("val entity = $name($constructorArgs)")
          line("val created = dao.create(entity)")
          line("val items = dao.getAll()")
          line("assertEquals(1, items.size)")
        }
      }
    }
    line()
  }
}
