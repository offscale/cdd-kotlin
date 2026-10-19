/** Unit test generator for DatabaseConnection configuration and initialization logic. */
package psi

import domain.SchemaDefinition

/** Generates unit tests for the DatabaseConnection configuration logic. */
class DbConnectionTestGenerator {

  /**
   * Generates tests for DatabaseConnection.
   *
   * @param packageName The package namespace.
   * @param schemas The list of schemas.
   * @return The generated DatabaseConnectionTest Kotlin source code.
   */
  fun generateDbConnectionTestModule(packageName: String, schemas: List<SchemaDefinition>): String {
    return buildKotlinFile("$packageName.db") {
      import("kotlin.test.*", "kotlin.test.Test")
      kdoc("Tests for [DatabaseConnection].")
      block("class DatabaseConnectionTest") {
        line("@Test")
        block("fun `resolveConfig with ephemeral flag true returns ephemeral SQLite`()") {
          line(
              "val config = DatabaseConnection.resolveConfig(ephemeralFlag = true, envUrl = \"jdbc:postgresql://localhost/db\")")
          line("assertTrue(config.isEphemeral)")
          line("assertEquals(\"org.sqlite.JDBC\", config.driver)")
          line("assertTrue(config.url.contains(\"sqlite\"))")
        }
        line()
        line("@Test")
        block("fun `resolveConfig with empty envUrl returns ephemeral SQLite`()") {
          line(
              "val config = DatabaseConnection.resolveConfig(ephemeralFlag = false, envUrl = \"\")")
          line("assertTrue(config.isEphemeral)")
          line("assertEquals(\"org.sqlite.JDBC\", config.driver)")
        }
        line()
        line("@Test")
        block("fun `resolveConfig with postgres envUrl returns postgres config`()") {
          line(
              "val config = DatabaseConnection.resolveConfig(ephemeralFlag = false, envUrl = \"jdbc:postgresql://localhost/db\")")
          line("assertFalse(config.isEphemeral)")
          line("assertEquals(\"org.postgresql.Driver\", config.driver)")
          line("assertEquals(\"jdbc:postgresql://localhost/db\", config.url)")
        }
      }
    }
  }
}
