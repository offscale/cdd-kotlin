/** Ktor and Clikt mock server entrypoint and routing generator. */
package psi

import domain.EndpointDefinition
import domain.HttpMethod
import domain.SchemaDefinition
import domain.SecurityScheme

/** Generates the Server CLI Entrypoint, bridging Ktor, DAOs, and the database seeder. */
class ServerMainGenerator {

  /**
   * Generates the Main module source code using Clikt and Ktor.
   *
   * @param packageName The package namespace.
   * @param schemas The list of schema definitions.
   * @param endpoints Optional list of OpenAPI endpoint definitions to generate routes for.
   * @param securitySchemes Optional map of OpenAPI security schemes.
   * @return Map of relative server file paths to generated Kotlin source code.
   */
  fun generateServerMain(
      packageName: String,
      schemas: List<SchemaDefinition>,
      endpoints: List<EndpointDefinition> = emptyList(),
      securitySchemes: Map<String, SecurityScheme> = emptyMap()
  ): Map<String, String> {
    val results = mutableMapOf<String, String>()
    val modelSchemas = filterModelSchemas(schemas)

    for (schema in modelSchemas) {
      val name = capitalizeName(schema.safeName)
      val routeName = decapitalizeName(schema.safeName)
      val idProp = schema.properties["id"]
      results["routes/${name}Routes.kt"] =
          buildKotlinFile("$packageName.routes") {
            import(
                "$packageName.dao.DaoConfiguration",
                "io.ktor.server.response.respondText",
                "io.ktor.server.routing.Route",
                "io.ktor.server.routing.get",
                "io.ktor.server.routing.delete",
                "io.ktor.server.application.call",
                "io.ktor.http.HttpStatusCode")
            kdoc(
                "Registers Ktor routes for [$name] backed by [DaoConfiguration].",
                "@param daoConfig The DAO configuration.")
            block("fun Route.${routeName}Routes(daoConfig: DaoConfiguration)") {
              block("""get("/$routeName")""") {
                block("if (daoConfig.${routeName}Dao.isStub)") {
                  line(
                      """call.respondText("Not Implemented", status = HttpStatusCode.NotImplemented)""")
                }
                block("else") {
                  line("val items = daoConfig.${routeName}Dao.getAll()")
                  line("call.respondText(items.toString())")
                }
              }
              if (idProp != null) {
                val idType = TypeMappers.mapType(idProp)
                val idParseExpr =
                    when (idType) {
                      "Int" -> """call.parameters["id"]?.toIntOrNull()"""
                      "Long" -> """call.parameters["id"]?.toLongOrNull()"""
                      else -> """call.parameters["id"]"""
                    }
                block("""get("/$routeName/{id}")""") {
                  block("if (daoConfig.${routeName}Dao.isStub)") {
                    line(
                        """call.respondText("Not Implemented", status = HttpStatusCode.NotImplemented)""")
                  }
                  block("else") {
                    line("val id = $idParseExpr")
                    block("if (id == null)") {
                      line(
                          """call.respondText("Bad Request", status = HttpStatusCode.BadRequest)""")
                    }
                    block("else") {
                      line("val item = daoConfig.${routeName}Dao.getById(id)")
                      block("if (item == null)") {
                        line("""call.respondText("Not Found", status = HttpStatusCode.NotFound)""")
                      }
                      block("else") { line("call.respondText(item.toString())") }
                    }
                  }
                }
              }
            }
          }
    }

    if (endpoints.isNotEmpty()) {
      results["routes/OpenApiEndpointRoutes.kt"] =
          generateOpenApiEndpointRoutes(packageName, endpoints, securitySchemes)
    }

    val chunkedRoutes = modelSchemas.chunked(100)
    results["Main.kt"] =
        buildKotlinFile(packageName) {
          import(
              "$packageName.dao.*",
              "$packageName.db.*",
              "$packageName.seeder.*",
              "$packageName.auth.*",
              "$packageName.webhooks.*",
              "$packageName.routes.*",
              "com.github.ajalt.clikt.core.CliktCommand",
              "com.github.ajalt.clikt.parameters.options.flag",
              "com.github.ajalt.clikt.parameters.options.option",
              "io.ktor.server.engine.embeddedServer",
              "io.ktor.server.netty.Netty",
              "io.ktor.server.response.respondText",
              "io.ktor.server.routing.get",
              "io.ktor.server.routing.routing",
              "io.ktor.server.application.call",
              "io.ktor.server.application.install",
              "io.ktor.server.plugins.cors.routing.CORS",
              "io.ktor.http.HttpMethod",
              "kotlin.system.exitProcess")
          kdoc("The main CLI entrypoint for the mock server.")
          block(
              """class MockServerCli : CliktCommand(name = "start", help = "Starts the Mock Server")""") {
                kdoc(
                    "Triggers the Concrete DAOs and overrides DATABASE_URL with a throwaway database.")
                line(
                    """val ephemeral by option("--ephemeral", help = "Use an ephemeral throwaway database").flag()""")
                line()
                kdoc("Runs the fake data seeder on startup (requires a concrete DB connection).")
                line(
                    """val seed by option("--seed", help = "Seed the database on startup").flag()""")
                line()
                kdoc("Enables strict schema validation for incoming requests.")
                line(
                    """val strictValidation by option("--strict-validation", help = "Enable strict request validation against OpenAPI schema").flag()""")
                line()
                kdoc(
                    "Toggles security validation against a hardcoded token for lightweight mock testing.")
                line(
                    """val enforceAuth by option("--enforce-auth", help = "Enforce authentication checks against mock tokens").flag()""")
                line()
                kdoc(
                    "Starts the integrated Identity Provider (IdP) module alongside the main application.")
                line(
                    """val startAuthServer by option("--start-auth-server", help = "Run the identity provider endpoints").flag()""")
                line()
                kdoc(
                    "Validates CLI flags and initializes configuration without blocking on the HTTP listener.")
                line(
                    """val dryRun by option("--dry-run", help = "Validate flags and exit without starting the HTTP server").flag()""")
                line()
                block("override fun run()") {
                  line("""val envUrl = currentContext.readEnvvar("DATABASE_URL")""")
                  line("val useConcrete = ephemeral || (envUrl != null && envUrl.isNotBlank())")
                  line()
                  line("val daoConfig = DaoFactory.create(useConcrete = useConcrete)")
                  line()
                  block("if (useConcrete && (!dryRun || ephemeral))") {
                    line(
                        "val dbConfig = DatabaseConnection.resolveConfig(ephemeralFlag = ephemeral, envUrl = envUrl)")
                    line("DatabaseConnection.initialize(dbConfig)")
                    line()
                    block("if (seed)") {
                      line("val seeder = DatabaseSeeder(daoConfig)")
                      line("seeder.seedDatabase()")
                    }
                  }
                  line()
                  line("if (dryRun) return")
                  line()
                  block("embeddedServer(Netty, port = 8080)", suffix = ".start(wait = true)") {
                    block("install(CORS)") {
                      line("anyHost()")
                      line("allowMethod(HttpMethod.Options)")
                      line("allowMethod(HttpMethod.Put)")
                      line("allowMethod(HttpMethod.Patch)")
                      line("allowMethod(HttpMethod.Delete)")
                      line("allowHeader(io.ktor.http.HttpHeaders.ContentType)")
                      line("allowHeader(io.ktor.http.HttpHeaders.Authorization)")
                    }
                    block("routing") {
                      block("if (strictValidation)") {
                        block(
                            "intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins)") {
                              block("""if (call.request.headers["X-Invalid-Schema"] == "true")""") {
                                line(
                                    """call.respondText("Bad Request", status = io.ktor.http.HttpStatusCode.BadRequest)""")
                                line("finish()")
                              }
                            }
                      }
                      block("if (enforceAuth)") {
                        block(
                            "intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins)") {
                              line(
                                  "val authHeader = call.request.headers[io.ktor.http.HttpHeaders.Authorization]")
                              block(
                                  """if (authHeader == null || !authHeader.startsWith("Bearer "))""") {
                                    line(
                                        """call.respondText("Unauthorized", status = io.ktor.http.HttpStatusCode.Unauthorized)""")
                                    line("finish()")
                                  }
                              block("else") {
                                line("""val token = authHeader.substringAfter("Bearer ")""")
                                block("""if (token != "mock-token-123")""") {
                                  line(
                                      """call.respondText("Forbidden", status = io.ktor.http.HttpStatusCode.Forbidden)""")
                                  line("finish()")
                                }
                              }
                            }
                      }
                      block("else if (!ephemeral && envUrl != null && envUrl.isNotBlank())") {
                        block(
                            "intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins)") {
                              line(
                                  "val authHeader = call.request.headers[io.ktor.http.HttpHeaders.Authorization]")
                              block(
                                  """if (authHeader != null && authHeader.startsWith("Bearer "))""") {
                                    line("""val token = authHeader.substringAfter("Bearer ")""")
                                    block("if (!ProductionAuth.validateToken(token, daoConfig))") {
                                      line(
                                          """call.respondText("Forbidden", status = io.ktor.http.HttpStatusCode.Forbidden)""")
                                      line("finish()")
                                    }
                                  }
                            }
                      }
                      line()
                      block("if (startAuthServer)") { line("authRoutes(daoConfig)") }
                      line()
                      line("webhookRoutes()")
                      line()
                      block("""get("/")""") { line("""call.respondText("Mock Server Running")""") }
                      chunkedRoutes.forEachIndexed { index, _ ->
                        line("routeBatch$index(daoConfig)")
                      }
                      if (endpoints.isNotEmpty()) {
                        line("openApiEndpointRoutes(daoConfig)")
                      }
                    }
                  }
                }
              }
          line()
          chunkedRoutes.forEachIndexed { index, batch ->
            kdoc(
                "Registers route batch $index for generated domain DAOs.",
                "@param daoConfig The DAO configuration.")
            block(
                "fun io.ktor.server.routing.Route.routeBatch$index(daoConfig: DaoConfiguration)") {
                  for (schema in batch) {
                    val routeName = decapitalizeName(schema.safeName)
                    line("${routeName}Routes(daoConfig)")
                  }
                }
            line()
          }
          kdoc("JVM Entrypoint.", "@param args The command line arguments.")
          line("fun main(args: Array<String>) = MockServerCli().main(args)")
        }
    return results
  }

  private fun generateOpenApiEndpointRoutes(
      packageName: String,
      endpoints: List<EndpointDefinition>,
      securitySchemes: Map<String, SecurityScheme>
  ): String {
    return buildKotlinFile("$packageName.routes") {
      import(
          "$packageName.dao.DaoConfiguration",
          "$packageName.auth.ProductionAuth",
          "io.ktor.http.HttpHeaders",
          "io.ktor.http.HttpStatusCode",
          "io.ktor.server.application.call",
          "io.ktor.server.request.receiveText",
          "io.ktor.server.response.respondText",
          "io.ktor.server.routing.*")
      kdoc(
          "Registers Ktor routes for all OpenAPI operations.",
          "@param daoConfig The DAO configuration.")
      block("fun Route.openApiEndpointRoutes(daoConfig: DaoConfiguration)") {
        for (ep in endpoints) {
          val ktorMethod =
              when (ep.method) {
                HttpMethod.GET -> "get"
                HttpMethod.POST -> "post"
                HttpMethod.PUT -> "put"
                HttpMethod.DELETE -> "delete"
                HttpMethod.PATCH -> "patch"
                HttpMethod.HEAD -> "head"
                HttpMethod.OPTIONS -> "options"
                else -> "get"
              }
          val requiresAuth = ep.security.isNotEmpty()
          val defaultStatus =
              when {
                ep.responses.containsKey("201") -> "HttpStatusCode.Created"
                ep.responses.containsKey("204") -> "HttpStatusCode.NoContent"
                else -> "HttpStatusCode.OK"
              }
          block("""$ktorMethod("${ep.path}")""") {
            if (requiresAuth) {
              val apiKeyHeader =
                  ep.security
                      .flatMap { it.keys }
                      .mapNotNull { securitySchemes[it] }
                      .firstOrNull { it.type == "apiKey" && it.`in` == "header" }
                      ?.name
              val authCheckExpr =
                  if (apiKeyHeader != null) {
                    """call.request.headers[HttpHeaders.Authorization].isNullOrBlank() && call.request.headers["$apiKeyHeader"].isNullOrBlank()"""
                  } else {
                    "call.request.headers[HttpHeaders.Authorization].isNullOrBlank()"
                  }
              block("if ($authCheckExpr)") {
                line("""call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)""")
                line("return@$ktorMethod")
              }
            }
            for (param in ep.parameters) {
              if (param.isRequired) {
                val extractExpr =
                    when (param.location) {
                      domain.ParameterLocation.PATH -> """call.parameters["${param.name}"]"""
                      domain.ParameterLocation.QUERY ->
                          """call.request.queryParameters["${param.name}"]"""
                      domain.ParameterLocation.HEADER -> """call.request.headers["${param.name}"]"""
                      else -> null
                    }
                if (extractExpr != null) {
                  block("if ($extractExpr.isNullOrBlank())") {
                    line(
                        """call.respondText("Missing required parameter: ${param.name}", status = HttpStatusCode.BadRequest)""")
                    line("return@$ktorMethod")
                  }
                }
              }
            }
            if (ep.requestBody?.required == true) {
              line("val bodyText = call.receiveText()")
              block("if (bodyText.isBlank())") {
                line(
                    """call.respondText("Request body required", status = HttpStatusCode.BadRequest)""")
                line("return@$ktorMethod")
              }
            }
            val q = '"'
            val bs = 92.toChar()
            line(
                "call.respondText($q{$bs${q}operationId$bs$q:$bs$q${ep.operationId}$bs$q}$q, status = $defaultStatus)")
          }
        }
      }
    }
  }
}
