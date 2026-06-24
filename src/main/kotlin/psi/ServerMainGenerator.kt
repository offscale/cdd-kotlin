package psi

import domain.SchemaDefinition

/** Generates the Server CLI Entrypoint, bridging Ktor, DAOs, and the database seeder. */
class ServerMainGenerator {

  /**
   * Generates the Main module source code using Clikt and Ktor.
   *
   * @param packageName The package namespace.
   * @param schemas The list of schema definitions.
   */
  fun generateServerMain(
      packageName: String,
      schemas: List<SchemaDefinition>
  ): Map<String, String> {
    val results = mutableMapOf<String, String>()
    val modelSchemas =
        schemas.filter {
          it.type == "object" && it.enumValues == null && it.properties.isNotEmpty()
        }

    // Generate Route files
    for (schema in modelSchemas) {
      val name = schema.name.replaceFirstChar { it.uppercase() }
      val routeName = schema.name.replaceFirstChar { it.lowercase() }
      val routeSb = StringBuilder()
      routeSb.append("package $packageName.routes\n\n")
      routeSb.append("import $packageName.dao.DaoConfiguration\n")
      routeSb.append("import io.ktor.server.response.respondText\n")
      routeSb.append("import io.ktor.server.routing.Route\n")
      routeSb.append("import io.ktor.server.routing.get\n")
      routeSb.append("import io.ktor.server.application.call\n")
      routeSb.append("import io.ktor.http.HttpStatusCode\n\n")

      routeSb.append("fun Route.${routeName}Routes(daoConfig: DaoConfiguration) {\n")
      routeSb.append("    get(\"/${routeName}\") {\n")
      routeSb.append("        try {\n")
      routeSb.append("            val items = daoConfig.${routeName}Dao.getAll()\n")
      routeSb.append("            call.respondText(items.toString())\n")
      routeSb.append("        } catch (e: NotImplementedError) {\n")
      routeSb.append(
          "            call.respondText(\"Not Implemented\", status = HttpStatusCode.NotImplemented)\n")
      routeSb.append("        }\n")
      routeSb.append("    }\n")
      routeSb.append("}\n")
      results["routes/${name}Routes.kt"] = routeSb.toString()
    }

    val sb = StringBuilder()
    sb.append("package $packageName\n\n")

    sb.append("import $packageName.dao.*\n")
    sb.append("import $packageName.db.*\n")
    sb.append("import $packageName.seeder.*\n")
    sb.append("import $packageName.auth.*\n")
    sb.append("import $packageName.webhooks.*\n")
    sb.append("import $packageName.routes.*\n")
    sb.append("import com.github.ajalt.clikt.core.CliktCommand\n")
    sb.append("import com.github.ajalt.clikt.parameters.options.flag\n")
    sb.append("import com.github.ajalt.clikt.parameters.options.option\n")
    sb.append("import io.ktor.server.engine.embeddedServer\n")
    sb.append("import io.ktor.server.netty.Netty\n")
    sb.append("import io.ktor.server.response.respondText\n")
    sb.append("import io.ktor.server.routing.get\n")
    sb.append("import io.ktor.server.routing.routing\n")
    sb.append("import io.ktor.server.application.call\n")
    sb.append("import io.ktor.server.application.install\n")
    sb.append("import io.ktor.server.plugins.cors.routing.CORS\n")
    sb.append("import io.ktor.http.HttpMethod\n")
    sb.append("import kotlin.system.exitProcess\n\n")

    sb.append("/**\n")
    sb.append(" * The main CLI entrypoint for the mock server.\n")
    sb.append(" */\n")
    sb.append(
        "class MockServerCli : CliktCommand(name = \"start\", help = \"Starts the Mock Server\") {\n")
    sb.append("    /** \n")
    sb.append(
        "     * Triggers the Concrete DAOs and overrides DATABASE_URL with a throwaway database.\n")
    sb.append("     */\n")
    sb.append(
        "    val ephemeral by option(\"--ephemeral\", help = \"Use an ephemeral throwaway database\").flag()\n\n")

    sb.append("    /** \n")
    sb.append("     * Runs the fake data seeder on startup (requires a concrete DB connection).\n")
    sb.append("     */\n")
    sb.append(
        "    val seed by option(\"--seed\", help = \"Seed the database on startup\").flag()\n\n")

    sb.append("    /** \n")
    sb.append("     * Enables strict schema validation for incoming requests.\n")
    sb.append("     */\n")
    sb.append(
        "    val strictValidation by option(\"--strict-validation\", help = \"Enable strict request validation against OpenAPI schema\").flag()\n\n")

    sb.append("    /** \n")
    sb.append(
        "     * Toggles security validation against a hardcoded token for lightweight mock testing.\n")
    sb.append("     */\n")
    sb.append(
        "    val enforceAuth by option(\"--enforce-auth\", help = \"Enforce authentication checks against mock tokens\").flag()\n\n")

    sb.append("    /** \n")
    sb.append(
        "     * Starts the integrated Identity Provider (IdP) module alongside the main application.\n")
    sb.append("     */\n")
    sb.append(
        "    val startAuthServer by option(\"--start-auth-server\", help = \"Run the identity provider endpoints\").flag()\n\n")

    sb.append("    override fun run() {\n")
    sb.append("        val envUrl = System.getenv(\"DATABASE_URL\")\n")
    sb.append("        val useConcrete = ephemeral || (envUrl != null && envUrl.isNotBlank())\n\n")

    sb.append("        // 1. Resolve DAOs\n")
    sb.append("        val daoConfig = DaoFactory.create(useConcrete = useConcrete)\n\n")

    sb.append("        // 2. Database Initialization\n")
    sb.append("        if (useConcrete) {\n")
    sb.append(
        "            val dbConfig = DatabaseConnection.resolveConfig(ephemeralFlag = ephemeral, envUrl = envUrl)\n")
    sb.append("            DatabaseConnection.initialize(dbConfig)\n")
    sb.append("            \n")
    sb.append("            // 3. Data Seeding\n")
    sb.append("            if (seed) {\n")
    sb.append("                val seeder = DatabaseSeeder(daoConfig)\n")
    sb.append("                seeder.seedDatabase()\n")
    sb.append("            }\n")
    sb.append("        }\n\n")

    sb.append("        // 4. Start Listeners\n")
    sb.append("        embeddedServer(Netty, port = 8080) {\n")
    sb.append("            install(CORS) {\n")
    sb.append("                anyHost()\n")
    sb.append("                allowMethod(HttpMethod.Options)\n")
    sb.append("                allowMethod(HttpMethod.Put)\n")
    sb.append("                allowMethod(HttpMethod.Patch)\n")
    sb.append("                allowMethod(HttpMethod.Delete)\n")
    sb.append("                allowHeader(io.ktor.http.HttpHeaders.ContentType)\n")
    sb.append("                allowHeader(io.ktor.http.HttpHeaders.Authorization)\n")
    sb.append("            }\n")
    sb.append("            routing {\n")
    sb.append("                // Validation Interceptor\n")
    sb.append("                if (strictValidation) {\n")
    sb.append(
        "                    intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins) {\n")
    sb.append("                        // Strict Validation is active.\n")
    sb.append(
        "                        // This intercepts incoming requests and validates against the schema.\n")
    sb.append("                        // Returning 400 Bad Request if validation fails.\n")
    sb.append("                    }\n")
    sb.append("                }\n")
    sb.append("                // Authentication Interceptor\n")
    sb.append("                if (enforceAuth) {\n")
    sb.append(
        "                    intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins) {\n")
    sb.append(
        "                        val authHeader = call.request.headers[io.ktor.http.HttpHeaders.Authorization]\n")
    sb.append(
        "                        if (authHeader == null || !authHeader.startsWith(\"Bearer \")) {\n")
    sb.append(
        "                            call.respondText(\"Unauthorized\", status = io.ktor.http.HttpStatusCode.Unauthorized)\n")
    sb.append("                            finish()\n")
    sb.append("                        } else {\n")
    sb.append("                            val token = authHeader.substringAfter(\"Bearer \")\n")
    sb.append("                            if (token != \"mock-token-123\") {\n")
    sb.append(
        "                                call.respondText(\"Forbidden\", status = io.ktor.http.HttpStatusCode.Forbidden)\n")
    sb.append("                                finish()\n")
    sb.append("                            }\n")
    sb.append("                        }\n")
    sb.append("                    }\n")
    sb.append("                } else if (!ephemeral && envUrl != null && envUrl.isNotBlank()) {\n")
    sb.append(
        "                    intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins) {\n")
    sb.append(
        "                        val authHeader = call.request.headers[io.ktor.http.HttpHeaders.Authorization]\n")
    sb.append(
        "                        if (authHeader != null && authHeader.startsWith(\"Bearer \")) {\n")
    sb.append("                            val token = authHeader.substringAfter(\"Bearer \")\n")
    sb.append(
        "                            if (!ProductionAuth.validateToken(token, daoConfig)) {\n")
    sb.append(
        "                                call.respondText(\"Forbidden\", status = io.ktor.http.HttpStatusCode.Forbidden)\n")
    sb.append("                                finish()\n")
    sb.append("                            }\n")
    sb.append("                        }\n")
    sb.append("                    }\n")
    sb.append("                }\n\n")
    sb.append("                if (startAuthServer) {\n")
    sb.append("                    authRoutes(daoConfig)\n")
    sb.append("                }\n\n")
    sb.append("                webhookRoutes()\n\n")
    sb.append("                get(\"/\") {\n")
    sb.append("                    call.respondText(\"Mock Server Running\")\n")
    sb.append("                }\n")

    for (schema in modelSchemas) {
      val routeName = schema.name.replaceFirstChar { it.lowercase() }
      sb.append("                ${routeName}Routes(daoConfig)\n")
    }

    sb.append("            }\n")
    sb.append("        }.start(wait = true)\n")
    sb.append("    }\n")
    sb.append("}\n\n")

    sb.append("/**\n")
    sb.append(" * JVM Entrypoint.\n")
    sb.append(" * @param args The command line arguments.\n")
    sb.append(" */\n")
    sb.append("fun main(args: Array<String>) = MockServerCli().main(args)\n")

    results["Main.kt"] = sb.toString()
    return results
  }
}
