/** Unit test generator for DatabaseSeeder mock data seeding routines. */
package psi

import domain.SchemaDefinition

/** Generates unit tests for the Fake Data Seeder. */
class SeederTestGenerator {

  /**
   * Generates a Kotlin test file testing the DatabaseSeeder.
   *
   * @param packageName The package namespace.
   * @param schemas The list of schemas.
   * @return The generated DatabaseSeederTest Kotlin source code.
   */
  fun generateSeederTestModule(packageName: String, schemas: List<SchemaDefinition>): String {
    val modelSchemas = filterModelSchemas(schemas)
    val chunkedAsserts = modelSchemas.chunked(100)
    return buildKotlinFile("$packageName.seeder") {
      import(
          "$packageName.dao.*",
          "$packageName.db.DatabaseConnection",
          "kotlin.test.*",
          "kotlin.test.BeforeTest",
          "kotlin.test.Test",
          "kotlinx.coroutines.runBlocking")
      kdoc("Tests for [DatabaseSeeder] focusing on referential integrity and batch insertion.")
      block("class DatabaseSeederTest") {
        line("private lateinit var daoConfig: DaoConfiguration")
        line("private lateinit var seeder: DatabaseSeeder")
        line()
        line("@BeforeTest")
        block("fun setup()") {
          line(
              "val dbFile = java.io.File.createTempFile(\"seeder_test_\", \".db\").apply { deleteOnExit() }")
          line(
              "val dbConfig = DatabaseConnection.resolveConfig(ephemeralFlag = false, envUrl = \"jdbc:sqlite:\${dbFile.absolutePath}\")")
          line("DatabaseConnection.initialize(dbConfig)")
          line("daoConfig = DaoFactory.create(useConcrete = true)")
          line("seeder = DatabaseSeeder(daoConfig)")
        }
        line()
        line("@Test")
        block(
            "fun `seedDatabase populates tables while maintaining referential integrity`() = runBlocking") {
              line("seeder.seedDatabase()")
              chunkedAsserts.forEachIndexed { index, _ -> line("assertBatch$index()") }
            }
        line()
        chunkedAsserts.forEachIndexed { index, batch ->
          block("private suspend fun assertBatch$index()") {
            for (schema in batch) {
              val name = capitalizeName(schema.safeName)
              val lowerName = decapitalizeName(name)
              line("val ${lowerName}s = daoConfig.${lowerName}Dao.getAll()")
              line("assertEquals(10, ${lowerName}s.size)")
            }
          }
          line()
        }
      }
    }
  }
}
