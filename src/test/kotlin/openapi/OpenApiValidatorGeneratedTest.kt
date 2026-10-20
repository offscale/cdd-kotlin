/** Unit and integration tests in [OpenApiValidatorGeneratedTest]. */
package openapi

import domain.*
import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OpenApiValidatorGeneratedTest {
  @Test
  fun testGeneratedInvalid() {
    val json = File("src/test/resources/invalid_generated.json").readText()
    val doc = OpenApiParser().parseDocumentString(json) as OpenApiDocument.OpenApi
    val issues = OpenApiValidator().validate(doc.definition)
    assertTrue(issues.isNotEmpty())

    val paths = issues.map { it.path }.toSet()
    assertTrue(paths.contains("$.openapi"))
    assertTrue(paths.contains("$.info.contact.email"))
    assertTrue(paths.contains("$.info.license.name"))
    assertTrue(paths.contains("$.paths"))
    assertTrue(paths.contains("$.paths./items/{id}.get"))
    assertTrue(paths.contains("$.paths./items/{id}.post.responses"))
    assertTrue(paths.contains("$.paths./items/{id}.get.responses[0].links.MyLink"))
    assertTrue(paths.contains("$.paths./items/{id}.get.responses.200.links.MyLink"))
    assertTrue(paths.contains("$.components.schemas.BadRef.\$ref"))
    assertTrue(paths.contains("$.components.links.BadLink"))
    assertTrue(paths.contains("$.tags"))
    assertTrue(paths.contains("$.security[0]"))

    assertTrue(issues.any { it.message.contains("must be a valid email address") })
    assertTrue(issues.any { it.message.contains("License name must not be blank") })
    assertTrue(issues.any { it.message.contains("operationId 'getItems' must be unique") })
    assertTrue(issues.any { it.message.contains("Paths share the same templated structure") })
    assertTrue(
        issues.any {
          it.message.contains("Link must not define both operationId and operationRef")
        })
    assertTrue(
        issues.any {
          it.message.contains("Schema \$ref '#/components/schemas/Unknown' does not resolve")
        })
    assertTrue(issues.any { it.message.contains("Tag name 'A' must be unique") })
    assertTrue(issues.any { it.message.contains("Tag parent cycle detected") })
    assertTrue(issues.any { it.path == "$.security[0]" && it.message.contains("Unknown") })
  }
}
