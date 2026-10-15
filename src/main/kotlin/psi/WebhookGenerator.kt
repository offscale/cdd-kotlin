/** Mock webhook administrative trigger module generator for testing OpenAPI callbacks. */
package psi

import domain.SchemaDefinition

/** Generates the Mock Webhook Trigger API for testing callbacks. */
class WebhookGenerator {

  /**
   * Generates the Webhook administrative module.
   *
   * @param packageName The package namespace.
   * @param schemas The schemas (for type context if needed).
   */
  fun generateWebhookModule(packageName: String, schemas: List<SchemaDefinition>): String {
    return buildKotlinFile("$packageName.webhooks") {
      import(
          "io.ktor.client.HttpClient",
          "io.ktor.client.engine.cio.CIO",
          "io.ktor.client.request.post",
          "io.ktor.client.request.setBody",
          "io.ktor.http.ContentType",
          "io.ktor.http.contentType",
          "io.ktor.server.application.ApplicationCall",
          "io.ktor.server.application.call",
          "io.ktor.server.response.respondText",
          "io.ktor.server.routing.Route",
          "io.ktor.server.routing.post",
          "io.ktor.server.routing.route",
          "io.ktor.http.HttpStatusCode",
          "kotlinx.coroutines.launch")
      kdoc("Routes for the Administrative Webhook Triggers.")
      block("fun Route.webhookRoutes()") {
        line("val client = HttpClient(CIO)")
        line()
        block("route(\"/_mock/trigger-webhook\")") {
          block("post(\"/{webhookName}\")") {
            line("val webhookName = call.parameters[\"webhookName\"] ?: \"\"")
            line("val targetUrl = call.request.queryParameters[\"targetUrl\"]")
            block("if (targetUrl.isNullOrBlank())") {
              line(
                  "call.respondText(\"Missing targetUrl query parameter\", status = HttpStatusCode.BadRequest)")
              line("return@post")
            }
            line()
            line("// Fire and forget the webhook payload")
            block("launch") {
              block("client.post(targetUrl)") {
                line("contentType(ContentType.Application.Json)")
                line(
                    "setBody(\"\"\"{\\\"event\\\": \\\"\$webhookName\\\", \\\"timestamp\\\": 123456789}\"\"\")")
              }
            }
            line(
                "call.respondText(\"Webhook dispatched to \$targetUrl\", status = HttpStatusCode.Accepted)")
          }
        }
      }
    }
  }
}
