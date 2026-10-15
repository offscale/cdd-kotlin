/** Database connection configuration and initialization generator. */
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
    val modelSchemas = filterModelSchemas(schemas)
    return buildKotlinFile("$packageName.db") {
      import(
          "$packageName.dao.*",
          "org.jetbrains.exposed.sql.Database",
          "org.jetbrains.exposed.sql.SchemaUtils",
          "org.jetbrains.exposed.sql.transactions.transaction",
          "java.sql.Connection")
      kdoc(
          "Configuration for the Database connection.",
          "",
          "@property url The JDBC connection URL.",
          "@property driver The JDBC driver class name.",
          "@property isEphemeral Whether this is a throwaway database.")
      line("data class DbConfig(")
      line("    val url: String,")
      line("    val driver: String,")
      line("    val isEphemeral: Boolean")
      line(")")
      line()
      kdoc("Connection factory for database initialization.")
      block("object DatabaseConnection") {
        kdoc(
            "Resolves the database configuration based on environment variables and flags.",
            "",
            "@param ephemeralFlag If true, forces an ephemeral SQLite connection.",
            "@param envUrl The DATABASE_URL environment variable.")
        block("fun resolveConfig(ephemeralFlag: Boolean, envUrl: String?): DbConfig") {
          block("if (ephemeralFlag || envUrl == null || envUrl.isBlank())") {
            line("return DbConfig(\"jdbc:sqlite:ephemeral.db\", \"org.sqlite.JDBC\", true)")
          }
          line("// Extract driver from URL roughly")
          block("val driver = when") {
            line("envUrl.startsWith(\"jdbc:postgresql:\") -> \"org.postgresql.Driver\"")
            line("envUrl.startsWith(\"jdbc:sqlite:\") -> \"org.sqlite.JDBC\"")
            line("else -> \"org.postgresql.Driver\"")
          }
          line("return DbConfig(envUrl, driver, false)")
        }
        line()
        kdoc(
            "Connects to the database and runs schema migrations if necessary.",
            "",
            "@param config The database configuration to use.")
        block("fun initialize(config: DbConfig): Database") {
          line("val db = Database.connect(config.url, driver = config.driver)")
          if (modelSchemas.isNotEmpty()) {
            block("transaction(db)") {
              val tables = modelSchemas.map { "${capitalizeName(it.safeName)}Table" }
              for (chunk in tables.chunked(100)) {
                line("SchemaUtils.create(")
                for (i in chunk.indices) {
                  val suffix = if (i < chunk.size - 1) "," else ""
                  line("    ${chunk[i]}$suffix")
                }
                line(")")
              }
            }
          }
          line("return db")
        }
      }
    }
  }
}
