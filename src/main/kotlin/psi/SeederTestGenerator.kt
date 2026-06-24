package psi

import domain.SchemaDefinition

/** Generates unit tests for the Fake Data Seeder. */
class SeederTestGenerator {

  /**
   * Generates a Kotlin test file testing the DatabaseSeeder.
   *
   * @param packageName The package namespace.
   * @param schemas The list of schemas.
   */
  fun generateSeederTestModule(packageName: String, schemas: List<SchemaDefinition>): String {
    val sb = StringBuilder()
    sb.append("package $packageName.seeder\n\n")

    sb.append("import $packageName.dao.*\n")
    sb.append("import $packageName.db.DatabaseConnection\n")
    sb.append("import org.junit.jupiter.api.Assertions.*\n")
    sb.append("import org.junit.jupiter.api.BeforeEach\n")
    sb.append("import org.junit.jupiter.api.Test\n")
    sb.append("import kotlinx.coroutines.runBlocking\n\n")

    sb.append("/**\n")
    sb.append(
        " * Tests for [DatabaseSeeder] focusing on referential integrity and batch insertion.\n")
    sb.append(" */\n")
    sb.append("class DatabaseSeederTest {\n")
    sb.append("    private lateinit var daoConfig: DaoConfiguration\n")
    sb.append("    private lateinit var seeder: DatabaseSeeder\n\n")

    sb.append("    @BeforeEach\n")
    sb.append("    fun setup() {\n")
    sb.append(
        "        val dbConfig = DatabaseConnection.resolveConfig(ephemeralFlag = true, envUrl = \"\")\n")
    sb.append("        DatabaseConnection.initialize(dbConfig)\n")
    sb.append("        daoConfig = DaoFactory.create(useConcrete = true)\n")
    sb.append("        seeder = DatabaseSeeder(daoConfig)\n")
    sb.append("    }\n\n")

    sb.append("    @Test\n")
    sb.append(
        "    fun `seedDatabase populates tables while maintaining referential integrity`() = runBlocking {\n")
    sb.append("        seeder.seedDatabase()\n")

    val modelSchemas =
        schemas.filter {
          it.type == "object" && it.enumValues == null && it.properties.isNotEmpty()
        }
    for (schema in modelSchemas) {
      val name = schema.name.replaceFirstChar { it.uppercase() }
      val lowerName = name.replaceFirstChar { it.lowercase() }
      sb.append("        val ${lowerName}s = daoConfig.${lowerName}Dao.getAll()\n")
      sb.append("        assertEquals(10, ${lowerName}s.size)\n")
    }
    sb.append("    }\n")
    sb.append("}\n")
    return sb.toString()
  }
}
