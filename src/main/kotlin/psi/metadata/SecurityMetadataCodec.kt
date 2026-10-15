/**
 * Encodes and decodes OpenAPI Security Requirements (`@security`, `@securityEmpty`) and Security
 * Schemes (`@securitySchemes`) for Kotlin KDoc round-tripping.
 */
package psi.metadata

import domain.Components
import domain.Info
import domain.OpenApiDefinition
import domain.OpenApiValues
import domain.SecurityRequirement
import domain.SecurityScheme
import kotlinx.serialization.json.Json
import openapi.OpenApiParser
import openapi.OpenApiWriter
import openapi.elements
import openapi.fields
import openapi.get
import openapi.isArray
import openapi.isObject
import openapi.textValue

/**
 * Result of parsing `@security` and `@securityEmpty` tags from KDoc lines.
 *
 * @property requirements Parsed list of security requirements.
 * @property explicitEmpty Whether an explicit empty security array (`security: []`) was specified.
 */
data class SecurityMetadataParseResult(
    val requirements: List<SecurityRequirement>,
    val explicitEmpty: Boolean
)

/**
 * Encodes and decodes OpenAPI [SecurityRequirement] and [SecurityScheme] metadata to and from KDoc
 * tags.
 */
object SecurityMetadataCodec {
  private val openApiWriter = OpenApiWriter()

  /**
   * Encodes a single [SecurityRequirement] map into a compact JSON string for `@security`.
   *
   * @param requirement Security requirement map (`schemeName -> scopes`).
   * @return Compact JSON string.
   */
  fun renderSecurityRequirement(requirement: Map<String, List<String>>): String {
    return OpenApiValues.encodeAnyToString(requirement)
  }

  /**
   * Encodes a map of [SecurityScheme]s into a compact JSON string for `@securitySchemes`.
   *
   * @param securitySchemes Security schemes map.
   * @return Compact JSON string.
   */
  fun renderSecuritySchemes(securitySchemes: Map<String, SecurityScheme>): String {
    if (securitySchemes.isEmpty()) return "{}"
    val tempDefinition =
        OpenApiDefinition(
            info = Info(title = "Security Schemes", version = "0.0.0"),
            components = Components(securitySchemes = securitySchemes))
    val json = openApiWriter.writeJson(tempDefinition)
    val node = Json.parseToJsonElement(json)
    val schemesNode = node.get("components")!!.get("securitySchemes")!!
    return OpenApiValues.encodeAnyToString(schemesNode)
  }

  /**
   * Extracts `@security` and `@securityEmpty` declarations from [lines].
   *
   * @param lines Normalized KDoc lines.
   * @return Parsed [SecurityMetadataParseResult].
   */
  fun extractSecurityLines(lines: List<String>): SecurityMetadataParseResult {
    val securityLines = lines.filter { it.startsWith("@security ") }
    val explicitEmpty =
        lines.any { it == "@securityEmpty" || it.startsWith("@securityEmpty ") } &&
            securityLines.isEmpty()

    val requirements =
        securityLines.mapNotNull { line ->
          val raw = line.removePrefix("@security").trim()
          if (raw.isEmpty()) return@mapNotNull null
          val node = openapi.parseJsonElementOrNull(raw) ?: return@mapNotNull null
          if (!node.isObject) return@mapNotNull null
          node.fields().asSequence().associate { (key, value) ->
            val scopes =
                if (value.isArray) {
                  value.elements().mapNotNull { it.textValue() }
                } else {
                  emptyList()
                }
            key to scopes
          }
        }
    return SecurityMetadataParseResult(requirements, explicitEmpty)
  }

  /**
   * Extracts a map of [SecurityScheme]s from `@securitySchemes` in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @param parser OpenAPI parser instance used to decode the wrapped component section.
   * @return Map of security scheme names to [SecurityScheme] objects.
   */
  fun extractSecuritySchemesLine(
      lines: List<String>,
      parser: OpenApiParser = OpenApiParser()
  ): Map<String, SecurityScheme> {
    val parsed =
        ComponentsMetadataCodec.parseWrappedComponentSection(
            lines, "@securitySchemes", "Security Schemes", "securitySchemes", parser)
    return if (parsed != null) parsed.securitySchemes else emptyMap()
  }
}
