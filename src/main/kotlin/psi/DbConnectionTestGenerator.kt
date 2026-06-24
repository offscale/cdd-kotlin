package psi

import domain.SchemaDefinition

/** Generates unit tests for the DatabaseConnection configuration logic. */
class DbConnectionTestGenerator {

  /**
   * Generates tests for DatabaseConnection.
   *
   * @param packageName The package namespace.
   * @param schemas The list of schemas.
   */
  fun generateDbConnectionTestModule(packageName: String, schemas: List<SchemaDefinition>): String {
    val sb = StringBuilder()
    sb.append("package $packageName.db\n\n")

    sb.append("import org.junit.jupiter.api.Assertions.*\n")
    sb.append("import org.junit.jupiter.api.Test\n\n")

    sb.append("/**\n")
    sb.append(" * Tests for [DatabaseConnection].\n")
    sb.append(" */\n")
    sb.append("class DatabaseConnectionTest {\n")
    sb.append("    @Test\n")
    sb.append("    fun `resolveConfig with ephemeral flag true returns ephemeral SQLite`() {\n")
    sb.append(
        "        val config = DatabaseConnection.resolveConfig(ephemeralFlag = true, envUrl = \"jdbc:postgresql://localhost/db\")\n")
    sb.append("        assertTrue(config.isEphemeral)\n")
    sb.append("        assertEquals(\"org.sqlite.JDBC\", config.driver)\n")
    sb.append("        assertTrue(config.url.contains(\"sqlite\"))\n")
    sb.append("    }\n\n")

    sb.append("    @Test\n")
    sb.append("    fun `resolveConfig with empty envUrl returns ephemeral SQLite`() {\n")
    sb.append(
        "        val config = DatabaseConnection.resolveConfig(ephemeralFlag = false, envUrl = \"\")\n")
    sb.append("        assertTrue(config.isEphemeral)\n")
    sb.append("        assertEquals(\"org.sqlite.JDBC\", config.driver)\n")
    sb.append("    }\n\n")

    sb.append("    @Test\n")
    sb.append("    fun `resolveConfig with postgres envUrl returns postgres config`() {\n")
    sb.append(
        "        val config = DatabaseConnection.resolveConfig(ephemeralFlag = false, envUrl = \"jdbc:postgresql://localhost/db\")\n")
    sb.append("        assertFalse(config.isEphemeral)\n")
    sb.append("        assertEquals(\"org.postgresql.Driver\", config.driver)\n")
    sb.append("        assertEquals(\"jdbc:postgresql://localhost/db\", config.url)\n")
    sb.append("    }\n")
    sb.append("}\n")

    return sb.toString()
  }
}
