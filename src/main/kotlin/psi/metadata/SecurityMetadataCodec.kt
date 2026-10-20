/**
 * Encodes and decodes OpenAPI Security Requirements (`@security`, `@securityEmpty`) and Security
 * Schemes (`@securitySchemes`) for Kotlin KDoc round-tripping.
 */
package psi.metadata

import domain.Components
import domain.Info
import domain.OpenApiArray
import domain.OpenApiDefinition
import domain.OpenApiObject
import domain.OpenApiString
import domain.OpenApiValues
import domain.SecurityRequirement
import domain.SecurityScheme
import kotlinx.serialization.json.Json
import openapi.OpenApiParser
import openapi.OpenApiWriter
import openapi.asArray
import openapi.asObject
import openapi.fields
import openapi.get
import openapi.textValue

/**
 * Result of extracting security requirements from KDoc lines.
 *
 * @property requirements List of parsed [SecurityRequirement] maps.
 * @property explicitEmpty Whether `@securityEmpty` was explicitly specified.
 */
data class SecurityExtractResult(
    val requirements: List<SecurityRequirement>,
    val explicitEmpty: Boolean
)

/** Encodes and decodes OpenAPI security requirements and security schemes in KDoc blocks. */
object SecurityMetadataCodec {
  private val openApiWriter = OpenApiWriter()

  /**
   * Serializes a single [SecurityRequirement] map into a compact JSON string.
   *
   * @param requirement Security requirement map.
   * @return Compact JSON string.
   */
  fun renderSecurityRequirement(requirement: SecurityRequirement): String {
    val obj =
        OpenApiObject(
            requirement.mapValues { (_, scopes) -> OpenApiArray(scopes.map { OpenApiString(it) }) })
    return OpenApiValues.encodeToString(obj)
  }

  /**
   * Serializes a map of [SecurityScheme]s into a compact JSON string for `@securitySchemes`.
   *
   * @param schemes Map of security schemes.
   * @return Compact JSON string.
   */
  fun renderSecuritySchemes(schemes: Map<String, SecurityScheme>): String {
    if (schemes.isEmpty()) return "{}"
    val tempDefinition =
        OpenApiDefinition(
            info = Info(title = "Security Schemes", version = "0.0.0"),
            components = Components(securitySchemes = schemes))
    val json = openApiWriter.writeJson(tempDefinition)
    val node = Json.parseToJsonElement(json)
    val schemesNode = node.get("components")!!.get("securitySchemes")!!
    return OpenApiValues.encodeJsonElementToString(schemesNode)
  }

  /**
   * Extracts [SecurityExtractResult] from `@security` and `@securityEmpty` tags in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @return Extracted [SecurityExtractResult].
   */
  fun extractSecurityLines(lines: List<String>): SecurityExtractResult {
    val hasEmptyTag = lines.any { it == "@securityEmpty" || it.startsWith("@securityEmpty ") }
    val securityLines =
        lines
            .filter { it.startsWith("@security") && !it.startsWith("@securitySchemes") }
            .filterNot { it.startsWith("@securityEmpty") }
            .map { it.removePrefix("@security").trim() }
            .filter { it.isNotEmpty() }

    val requirements =
        securityLines.mapNotNull { json ->
          val node = openapi.parseJsonElementOrNull(json) ?: return@mapNotNull null
          val obj = node.asObject() ?: return@mapNotNull null
          val map = LinkedHashMap<String, List<String>>()
          obj.fields().forEach { (scheme, scopesNode) ->
            val arr = scopesNode.asArray()
            val scopes = if (arr != null) arr.mapNotNull { it.textValue() } else emptyList()
            map[scheme] = scopes
          }
          map
        }

    return SecurityExtractResult(requirements, hasEmptyTag && requirements.isEmpty())
  }

  /**
   * Extracts a map of [SecurityScheme]s from `@securitySchemes` in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @param parser OpenAPI parser instance.
   * @return Map of scheme names to [SecurityScheme]s.
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
