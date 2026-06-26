package psi

import domain.SchemaDefinition

/** Generates unit tests for the Data Access Objects (DAOs) and DaoFactory. */
class DaoTestGenerator {

  /**
   * Generates a Kotlin test file testing the DaoFactory and DAOs.
   *
   * @param packageName The package namespace for the DAOs.
   * @param schemas The list of schema definitions to generate tests for.
   */
  fun generateDaoTests(packageName: String, schemas: List<SchemaDefinition>): Map<String, String> {
    val results = mutableMapOf<String, String>()
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

    // Test DaoFactory
    val factorySb = StringBuilder()
    factorySb.append("package $packageName.dao\n\n")
    factorySb.append("import $packageName.models.*\n")
    factorySb.append("import kotlin.test.*\n")
    factorySb.append("import kotlin.test.Test\n\n")
    factorySb.append(generateFactoryTest(modelSchemas))
    results["dao/DaoFactoryTest.kt"] = factorySb.toString()

    // Test Concrete DAOs (Ephemeral SQLite)
    for (schema in modelSchemas) {
      val name = schema.safeName.replaceFirstChar { it.uppercase() }
      val sb = StringBuilder()
      sb.append("package $packageName.dao\n\n")
      sb.append("import $packageName.models.*\n")
      sb.append("import org.jetbrains.exposed.sql.Database\n")
      sb.append("import org.jetbrains.exposed.sql.SchemaUtils\n")
      sb.append("import org.jetbrains.exposed.sql.transactions.transaction\n")
      sb.append("import kotlin.test.*\n")
      sb.append("import kotlin.test.BeforeTest\n")
      sb.append("import kotlin.test.Test\n")
      sb.append("import kotlinx.coroutines.runBlocking\n\n")
      sb.append(generateConcreteDaoTest(schema))
      results["dao/Concrete${name}DaoTest.kt"] = sb.toString()
    }

    return results
  }

  private fun generateFactoryTest(schemas: List<SchemaDefinition>): String {
    val sb = StringBuilder()
    sb.append("/**\n")
    sb.append(" * Tests for [DaoFactory].\n")
    sb.append(" */\n")
    sb.append("class DaoFactoryTest {\n")
    sb.append("    @Test\n")
    sb.append("    fun `create with true returns concrete DAOs`() {\n")
    sb.append("        val config = DaoFactory.create(useConcrete = true)\n")
    for (schema in schemas) {
      val name = schema.safeName.replaceFirstChar { it.uppercase() }
      val lowerName = name.replaceFirstChar { it.lowercase() }
      sb.append("        assertTrue(config.${lowerName}Dao is Concrete${name}Dao)\n")
    }
    sb.append("    }\n\n")

    sb.append("    @Test\n")
    sb.append("    fun `create with false returns stub DAOs`() {\n")
    sb.append("        val config = DaoFactory.create(useConcrete = false)\n")
    for (schema in schemas) {
      val name = schema.safeName.replaceFirstChar { it.uppercase() }
      val lowerName = name.replaceFirstChar { it.lowercase() }
      sb.append("        assertTrue(config.${lowerName}Dao is Stub${name}Dao)\n")
    }
    sb.append("    }\n")
    sb.append("}\n\n")
    return sb.toString()
  }

  private fun generateConcreteDaoTest(schema: SchemaDefinition): String {
    val sb = StringBuilder()
    val name = schema.safeName.replaceFirstChar { it.uppercase() }
    val tableName = "${name}Table"

    sb.append("/**\n")
    sb.append(" * Tests for [Concrete${name}Dao] using an ephemeral SQLite database.\n")
    sb.append(" */\n")
    sb.append("class Concrete${name}DaoTest {\n")
    sb.append("    private lateinit var dao: Concrete${name}Dao\n\n")

    sb.append("    @BeforeTest\n")
    sb.append("    fun setup() {\n")
    sb.append(
        "        Database.connect(\"jdbc:sqlite:file:test?mode=memory&cache=shared\", driver = \"org.sqlite.JDBC\")\n")
    sb.append("        transaction {\n")
    sb.append("            SchemaUtils.create($tableName)\n")
    sb.append("        }\n")
    sb.append("        dao = Concrete${name}Dao()\n")
    sb.append("    }\n\n")

    sb.append("    @Test\n")
    sb.append("    fun `getAll returns empty list initially`() = runBlocking {\n")
    sb.append("        val items = dao.getAll()\n")
    sb.append("        assertTrue(items.isEmpty())\n")
    sb.append("    }\n\n")

    sb.append("    @Test\n")
    sb.append("    fun `create inserts record and getAll retrieves it`() = runBlocking {\n")

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
                      else -> "\"test\""
                    }
                  }
              if (propName == "id") {
                "$propName = 0"
              } else {
                "$propName = $dummyVal"
              }
            }
            .joinToString(", ")

    sb.append("        val entity = $name($constructorArgs)\n")
    sb.append("        val created = dao.create(entity)\n")
    val idProp = schema.properties["id"]
    if (idProp != null) {
      sb.append("        assertNotEquals(0, created.id)\n")
    }

    sb.append("        val items = dao.getAll()\n")
    sb.append("        assertEquals(1, items.size)\n")
    sb.append("    }\n")

    if (idProp != null) {
      sb.append("\n")
      sb.append("    @Test\n")
      sb.append("    fun `getById retrieves correct record`() = runBlocking {\n")
      sb.append("        val entity = $name($constructorArgs)\n")
      sb.append("        val created = dao.create(entity)\n")
      sb.append("        val fetched = dao.getById(created.id)\n")
      sb.append("        assertNotNull(fetched)\n")
      sb.append("        assertEquals(created.id, fetched!!.id)\n")
      sb.append("    }\n\n")

      sb.append("    @Test\n")
      sb.append("    fun `delete removes record`() = runBlocking {\n")
      sb.append("        val entity = $name($constructorArgs)\n")
      sb.append("        val created = dao.create(entity)\n")
      sb.append("        val deleted = dao.delete(created.id)\n")
      sb.append("        assertTrue(deleted)\n")
      sb.append("        val fetched = dao.getById(created.id)\n")
      sb.append("        assertNull(fetched)\n")
      sb.append("    }\n")
    }
    sb.append("}\n\n")
    return sb.toString()
  }
}
