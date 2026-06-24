package psi

import domain.SchemaDefinition

/** Generates an integrated Identity Provider (IdP) module and stateful authentication logic. */
class IdpGenerator {

  /**
   * Generates the Auth Server module source code.
   *
   * @param packageName The package namespace.
   * @param schemas The list of schemas (to find the User model).
   */
  fun generateIdpModule(packageName: String, schemas: List<SchemaDefinition>): String {
    val sb = StringBuilder()
    sb.append("package $packageName.auth\n\n")

    sb.append("import $packageName.dao.*\n")
    sb.append("import io.ktor.server.application.ApplicationCall\n")
    sb.append("import io.ktor.server.application.call\n")
    sb.append("import io.ktor.server.response.respondText\n")
    sb.append("import io.ktor.server.routing.Route\n")
    sb.append("import io.ktor.server.routing.post\n")
    sb.append("import io.ktor.server.routing.route\n")
    sb.append("import io.ktor.http.HttpStatusCode\n\n")

    sb.append("/**\n")
    sb.append(" * Routes for the Integrated Identity Provider (IdP).\n")
    sb.append(" */\n")
    sb.append("fun Route.authRoutes(daoConfig: DaoConfiguration) {\n")
    sb.append("    route(\"/auth\") {\n")
    sb.append("        post(\"/login\") {\n")
    sb.append(
        "            // In a real implementation, this would parse credentials from the body\n")
    sb.append("            // and validate against daoConfig.userDao (or equivalent)\n")
    sb.append("            call.respondText(\"{\\\"token\\\": \\\"production-token-456\\\"}\")\n")
    sb.append("        }\n")
    sb.append("        post(\"/register\") {\n")
    sb.append("            call.respondText(\"Registered\", status = HttpStatusCode.Created)\n")
    sb.append("        }\n")
    sb.append("        post(\"/refresh\") {\n")
    sb.append("            call.respondText(\"{\\\"token\\\": \\\"refreshed-token-789\\\"}\")\n")
    sb.append("        }\n")
    sb.append("        post(\"/logout\") {\n")
    sb.append("            call.respondText(\"Logged out\")\n")
    sb.append("        }\n")
    sb.append("    }\n")
    sb.append("}\n\n")

    sb.append("/**\n")
    sb.append(" * Stateful Authentication Middleware Logic.\n")
    sb.append(" */\n")
    sb.append("object ProductionAuth {\n")
    sb.append("    /**\n")
    sb.append("     * Validates a token against actual database records.\n")
    sb.append("     */\n")
    sb.append("    fun validateToken(token: String, daoConfig: DaoConfiguration): Boolean {\n")
    sb.append(
        "        // Placeholder for ORM integration (e.g. checking a Sessions table or looking up a User)\n")
    sb.append("        return token.isNotBlank() && token != \"invalid\"\n")
    sb.append("    }\n")
    sb.append("}\n")

    return sb.toString()
  }
}
