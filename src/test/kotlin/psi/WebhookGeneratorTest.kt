package psi

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebhookGeneratorTest {
  @Test
  fun `generateWebhookModule produces administrative trigger route`() {
    val generator = WebhookGenerator()
    val result = generator.generateWebhookModule("com.example", emptyList())

    assertTrue(result.contains("fun Route.webhookRoutes()"), "Missing webhook routes")
    assertTrue(result.contains("post(\"/{webhookName}\")"), "Missing trigger path")
    assertTrue(result.contains("client.post(targetUrl)"), "Missing HTTP client dispatch")
    assertTrue(result.contains("Webhook dispatched"), "Missing accepted response")
  }
}
