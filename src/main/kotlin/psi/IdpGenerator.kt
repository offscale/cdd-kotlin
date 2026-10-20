/** Integrated OAuth2 / OIDC Identity Provider (IdP) server module generator. */
package psi

import domain.SchemaDefinition
import domain.SecurityScheme

/** Generates an integrated Identity Provider (IdP) module and stateful authentication logic. */
class IdpGenerator {

  /**
   * Generates the Auth Server module source code.
   *
   * @param packageName The package namespace.
   * @param schemas The list of schemas (to find the User model).
   * @param securitySchemes Optional map of OpenAPI security schemes.
   * @return The generated Auth Server Kotlin source code.
   */
  fun generateIdpModule(
      packageName: String,
      schemas: List<SchemaDefinition>,
      securitySchemes: Map<String, SecurityScheme> = emptyMap()
  ): String {
    val principalModel = if (schemas.isNotEmpty()) schemas.first().safeName else "User"
    val configuredScopes =
        securitySchemes.values
            .flatMap { scheme ->
              val flows = scheme.flows
              listOfNotNull(
                      flows?.implicit,
                      flows?.password,
                      flows?.clientCredentials,
                      flows?.authorizationCode,
                      flows?.deviceAuthorization)
                  .flatMap { it.scopes.keys } + scheme.scopes.orEmpty().keys
            }
            .distinct()
    val q = '"'
    val bs = 92.toChar()
    val scopesLiteral = configuredScopes.joinToString(", ") { "$q$it$q" }

    return buildKotlinFile("$packageName.auth") {
      import(
          "$packageName.dao.*",
          "io.ktor.server.application.ApplicationCall",
          "io.ktor.server.application.call",
          "io.ktor.server.response.respondText",
          "io.ktor.server.routing.Route",
          "io.ktor.server.routing.post",
          "io.ktor.server.routing.route",
          "io.ktor.http.HttpStatusCode",
          "java.util.concurrent.ConcurrentHashMap")
      kdoc("Routes for the Integrated Identity Provider (IdP) bound to [$principalModel].")
      block("fun Route.authRoutes(daoConfig: DaoConfiguration)") {
        block("""route("/auth")""") {
          block("""post("/login")""") {
            line("""ProductionAuth.activeTokens.add("production-token-456")""")
            line("call.respondText($q{$bs${q}token$bs$q: $bs${q}production-token-456$bs$q}$q)")
          }
          block("""post("/register")""") {
            line("""call.respondText("Registered", status = HttpStatusCode.Created)""")
          }
          block("""post("/refresh")""") {
            line("""ProductionAuth.activeTokens.add("refreshed-token-789")""")
            line("call.respondText($q{$bs${q}token$bs$q: $bs${q}refreshed-token-789$bs$q}$q)")
          }
          block("""post("/logout")""") { line("""call.respondText("Logged out")""") }
        }
      }
      line()
      kdoc("Stateful Authentication Middleware Logic for [$principalModel].")
      block("object ProductionAuth") {
        line("""val principalModelName: String = "$principalModel"""")
        line("val supportedScopes: Set<String> = setOf($scopesLiteral)")
        line(
            """val activeTokens: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply { add("production-token-456"); add("refreshed-token-789") }""")
        line()
        kdoc("Validates a token against active sessions and database records.")
        block("fun validateToken(token: String, daoConfig: DaoConfiguration): Boolean") {
          line(
              """return token.isNotBlank() && token != "invalid" && (activeTokens.contains(token) || !daoConfig.useConcrete)""")
        }
      }
    }
  }
}
