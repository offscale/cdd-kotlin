package psi

import domain.SchemaDefinition

/** Generates the Database Connection and initialization routines. */
class DbConnectionGenerator {

  /**
   * Generates the DatabaseConnection configuration class and factory.
   *
   * @param packageName The package namespace.
   * @param schemas The schemas to register for migrations.
   */
  fun generateDbConnectionModule(packageName: String, schemas: List<SchemaDefinition>): String {
    val sb = StringBuilder()
    sb.append("package $packageName.db\n\n")

    sb.append("import $packageName.dao.*\n")
    sb.append("import org.jetbrains.exposed.sql.Database\n")
    sb.append("import org.jetbrains.exposed.sql.SchemaUtils\n")
    sb.append("import org.jetbrains.exposed.sql.transactions.transaction\n")
    sb.append("import java.sql.Connection\n\n")

    sb.append("/**\n")
    sb.append(" * Configuration for the Database connection.\n")
    sb.append(" * \n")
    sb.append(" * @property url The JDBC connection URL.\n")
    sb.append(" * @property driver The JDBC driver class name.\n")
    sb.append(" * @property isEphemeral Whether this is a throwaway database.\n")
    sb.append(" */\n")
    sb.append("data class DbConfig(\n")
    sb.append("    val url: String,\n")
    sb.append("    val driver: String,\n")
    sb.append("    val isEphemeral: Boolean\n")
    sb.append(")\n\n")

    sb.append("/**\n")
    sb.append(" * Connection factory for database initialization.\n")
    sb.append(" */\n")
    sb.append("object DatabaseConnection {\n")
    sb.append("    /**\n")
    sb.append(
        "     * Resolves the database configuration based on environment variables and flags.\n")
    sb.append("     * \n")
    sb.append("     * @param ephemeralFlag If true, forces an ephemeral SQLite connection.\n")
    sb.append("     * @param envUrl The DATABASE_URL environment variable.\n")
    sb.append("     */\n")
    sb.append("    fun resolveConfig(ephemeralFlag: Boolean, envUrl: String?): DbConfig {\n")
    sb.append("        if (ephemeralFlag || envUrl == null || envUrl.isBlank()) {\n")
    sb.append(
        "            return DbConfig(\"jdbc:sqlite:ephemeral.db\", \"org.sqlite.JDBC\", true)\n")
    sb.append("        }\n")
    sb.append("        // Extract driver from URL roughly\n")
    sb.append("        val driver = when {\n")
    sb.append("            envUrl.startsWith(\"jdbc:postgresql:\") -> \"org.postgresql.Driver\"\n")
    sb.append("            envUrl.startsWith(\"jdbc:sqlite:\") -> \"org.sqlite.JDBC\"\n")
    sb.append("            else -> \"org.postgresql.Driver\"\n")
    sb.append("        }\n")
    sb.append("        return DbConfig(envUrl, driver, false)\n")
    sb.append("    }\n\n")

    sb.append("    /**\n")
    sb.append("     * Connects to the database and runs schema migrations if necessary.\n")
    sb.append("     * \n")
    sb.append("     * @param config The database configuration to use.\n")
    sb.append("     */\n")
    sb.append("    fun initialize(config: DbConfig): Database {\n")
    sb.append("        val db = Database.connect(config.url, driver = config.driver)\n")

    val modelSchemas =
        schemas.filter {
          it.type == "object" && it.enumValues == null && it.properties.isNotEmpty()
        }
    if (modelSchemas.isNotEmpty()) {
      sb.append("        transaction(db) {\n")
      sb.append("            SchemaUtils.create(\n")
      val tables = modelSchemas.map { "${it.name.replaceFirstChar { c -> c.uppercase() }}Table" }
      for (i in tables.indices) {
        sb.append("                ${tables[i]}")
        if (i < tables.size - 1) sb.append(",")
        sb.append("\n")
      }
      sb.append("            )\n")
      sb.append("        }\n")
    }

    sb.append("        return db\n")
    sb.append("    }\n")
    sb.append("}\n")

    return sb.toString()
  }
}
