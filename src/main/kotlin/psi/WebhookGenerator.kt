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
    val sb = StringBuilder()
    sb.append("package $packageName.webhooks\n\n")

    sb.append("import io.ktor.client.HttpClient\n")
    sb.append("import io.ktor.client.engine.cio.CIO\n")
    sb.append("import io.ktor.client.request.post\n")
    sb.append("import io.ktor.client.request.setBody\n")
    sb.append("import io.ktor.http.ContentType\n")
    sb.append("import io.ktor.http.contentType\n")
    sb.append("import io.ktor.server.application.ApplicationCall\n")
    sb.append("import io.ktor.server.application.call\n")
    sb.append("import io.ktor.server.response.respondText\n")
    sb.append("import io.ktor.server.routing.Route\n")
    sb.append("import io.ktor.server.routing.post\n")
    sb.append("import io.ktor.server.routing.route\n")
    sb.append("import io.ktor.http.HttpStatusCode\n")
    sb.append("import kotlinx.coroutines.launch\n\n")

    sb.append("/**\n")
    sb.append(" * Routes for the Administrative Webhook Triggers.\n")
    sb.append(" */\n")
    sb.append("fun Route.webhookRoutes() {\n")
    sb.append("    val client = HttpClient(CIO)\n\n")
    sb.append("    route(\"/_mock/trigger-webhook\") {\n")
    sb.append("        post(\"/{webhookName}\") {\n")
    sb.append("            val webhookName = call.parameters[\"webhookName\"] ?: \"\"\n")
    sb.append("            val targetUrl = call.request.queryParameters[\"targetUrl\"]\n")
    sb.append("            if (targetUrl.isNullOrBlank()) {\n")
    sb.append(
        "                call.respondText(\"Missing targetUrl query parameter\", status = HttpStatusCode.BadRequest)\n")
    sb.append("                return@post\n")
    sb.append("            }\n\n")
    sb.append("            // Fire and forget the webhook payload\n")
    sb.append("            launch {\n")
    sb.append("                try {\n")
    sb.append("                    client.post(targetUrl) {\n")
    sb.append("                        contentType(ContentType.Application.Json)\n")
    sb.append(
        "                        setBody(\"\"\"{\\\"event\\\": \\\"\$webhookName\\\", \\\"timestamp\\\": 123456789}\"\"\")\n")
    sb.append("                    }\n")
    sb.append("                } catch (e: Exception) {\n")
    sb.append(
        "                    println(\"Failed to dispatch webhook to \$targetUrl: \${e.message}\")\n")
    sb.append("                }\n")
    sb.append("            }\n")
    sb.append(
        "            call.respondText(\"Webhook dispatched to \$targetUrl\", status = HttpStatusCode.Accepted)\n")
    sb.append("        }\n")
    sb.append("    }\n")
    sb.append("}\n")

    return sb.toString()
  }
}
