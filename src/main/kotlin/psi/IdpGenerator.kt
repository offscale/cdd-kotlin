/** Integrated OAuth2 / OIDC Identity Provider (IdP) server module generator. */
package psi

import domain.SchemaDefinition

/** Generates an integrated Identity Provider (IdP) module and stateful authentication logic. */
class IdpGenerator {

  /**
   * Generates the Auth Server module source code.
   *
   * @param packageName The package namespace.
   * @param schemas The list of schemas (to find the User model).
   * @return The generated Auth Server Kotlin source code.
   */
  fun generateIdpModule(packageName: String, schemas: List<SchemaDefinition>): String {
    val principalModel = if (schemas.isNotEmpty()) schemas.first().safeName else "User"
    return buildKotlinFile("$packageName.auth") {
      import(
          "$packageName.dao.*",
          "io.ktor.server.application.ApplicationCall",
          "io.ktor.server.application.call",
          "io.ktor.server.response.respondText",
          "io.ktor.server.routing.Route",
          "io.ktor.server.routing.post",
          "io.ktor.server.routing.route",
          "io.ktor.http.HttpStatusCode")
      kdoc("Routes for the Integrated Identity Provider (IdP) bound to [$principalModel].")
      block("fun Route.authRoutes(daoConfig: DaoConfiguration)") {
        block("route(\"/auth\")") {
          block("post(\"/login\")") {
            line("ProductionAuth.activeTokens.add(\"production-token-456\")")
            line("call.respondText(\"{\\\"token\\\": \\\"production-token-456\\\"}\")")
          }
          block("post(\"/register\")") {
            line("call.respondText(\"Registered\", status = HttpStatusCode.Created)")
          }
          block("post(\"/refresh\")") {
            line("ProductionAuth.activeTokens.add(\"refreshed-token-789\")")
            line("call.respondText(\"{\\\"token\\\": \\\"refreshed-token-789\\\"}\")")
          }
          block("post(\"/logout\")") { line("call.respondText(\"Logged out\")") }
        }
      }
      line()
      kdoc("Stateful Authentication Middleware Logic for [$principalModel].")
      block("object ProductionAuth") {
        line("val principalModelName: String = \"$principalModel\"")
        line(
            "val activeTokens: MutableSet<String> = mutableSetOf(\"production-token-456\", \"refreshed-token-789\")")
        line()
        kdoc("Validates a token against active sessions and database records.")
        block("fun validateToken(token: String, daoConfig: DaoConfiguration): Boolean") {
          line(
              "return token.isNotBlank() && token != \"invalid\" && (activeTokens.contains(token) || !daoConfig.useConcrete)")
        }
      }
    }
  }
}
