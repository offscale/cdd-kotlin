/**
 * Encodes and decodes OpenAPI root Info, Contact, License, Server, and ExternalDocumentation KDoc
 * metadata.
 */
package psi.metadata

import domain.Contact
import domain.ExternalDocumentation
import domain.Info
import domain.License
import domain.OpenApiValue
import domain.OpenApiValues
import domain.Server
import domain.ServerVariable
import openapi.JsonNode
import openapi.asArray
import openapi.asObject
import openapi.elements
import openapi.fields
import openapi.get
import openapi.isArray
import openapi.isObject
import openapi.text
import openapi.textValue
import psi.OpenApiMetadata

/**
 * Result of parsing the `@openapi` root metadata tag.
 *
 * @property openapi OpenAPI version string.
 * @property jsonSchemaDialect JSON Schema dialect URI.
 * @property self Document `$self` URI.
 */
data class OpenApiHeaderLine(
    val openapi: String?,
    val jsonSchemaDialect: String?,
    val self: String?
)

/**
 * Encodes and decodes OpenAPI `info`, `contact`, `license`, `servers`, `externalDocs`, and root
 * `@openapi` KDoc tags.
 */
object InfoMetadataCodec {
  /**
   * Encodes the root `@openapi` metadata JSON string from [metadata].
   *
   * @param metadata The OpenAPI metadata source.
   * @return Compact JSON string representing `openapi`, `jsonSchemaDialect`, and `$self`.
   */
  fun renderOpenApiMeta(metadata: OpenApiMetadata): String {
    val map = linkedMapOf<String, Any?>()
    if (metadata.openapi != null) map["openapi"] = metadata.openapi
    if (metadata.jsonSchemaDialect != null) map["jsonSchemaDialect"] = metadata.jsonSchemaDialect
    if (metadata.self != null) map["\$self"] = metadata.self
    return OpenApiValues.encodeAnyToString(map)
  }

  /**
   * Decodes the `@openapi` KDoc tag line from [lines].
   *
   * @param lines Normalized KDoc lines.
   * @return Parsed [OpenApiHeaderLine].
   */
  fun extractOpenApiLine(lines: List<String>): OpenApiHeaderLine {
    val line =
        lines.firstOrNull { it.startsWith("@openapi") }
            ?: return OpenApiHeaderLine(null, null, null)
    val json = line.removePrefix("@openapi").trim()
    if (json.isEmpty()) return OpenApiHeaderLine(null, null, null)
    val node = openapi.parseJsonElementOrNull(json) ?: return OpenApiHeaderLine(null, null, null)
    if (!node.isObject) return OpenApiHeaderLine(null, null, null)
    return OpenApiHeaderLine(
        openapi = node.text("openapi"),
        jsonSchemaDialect = node.text("jsonSchemaDialect"),
        self = node.text("\$self"))
  }

  /**
   * Encodes an [Info] object into a compact JSON string for `@info`.
   *
   * @param info The Info object to encode.
   * @return Compact JSON string.
   */
  fun renderInfo(info: Info): String {
    return OpenApiValues.encodeAnyToString(infoToDocValue(info))
  }

  /**
   * Converts an [Info] object into a serializable map.
   *
   * @param info The Info object.
   * @return Map representation.
   */
  fun infoToDocValue(info: Info): Map<String, Any?> {
    val map = linkedMapOf<String, Any?>()
    map["title"] = info.title
    map["version"] = info.version
    if (info.summary != null) map["summary"] = info.summary
    if (info.description != null) map["description"] = info.description
    if (info.termsOfService != null) map["termsOfService"] = info.termsOfService
    info.contact?.let { map["contact"] = contactToDocValue(it) }
    info.license?.let { map["license"] = licenseToDocValue(it) }
    info.extensions.forEach { (k, v) -> if (k.startsWith("x-")) map[k] = v }
    return map
  }

  /**
   * Converts a [Contact] object into a serializable map.
   *
   * @param contact The Contact object.
   * @return Map representation.
   */
  fun contactToDocValue(contact: Contact): Map<String, Any?> {
    val map = linkedMapOf<String, Any?>()
    if (contact.name != null) map["name"] = contact.name
    if (contact.url != null) map["url"] = contact.url
    if (contact.email != null) map["email"] = contact.email
    contact.extensions.forEach { (k, v) -> if (k.startsWith("x-")) map[k] = v }
    return map
  }

  /**
   * Converts a [License] object into a serializable map.
   *
   * @param license The License object.
   * @return Map representation.
   */
  fun licenseToDocValue(license: License): Map<String, Any?> {
    val map = linkedMapOf<String, Any?>()
    map["name"] = license.name
    if (license.identifier != null) map["identifier"] = license.identifier
    if (license.url != null) map["url"] = license.url
    license.extensions.forEach { (k, v) -> if (k.startsWith("x-")) map[k] = v }
    return map
  }

  /**
   * Decodes an [Info] object from the `@info` KDoc tag in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @return Parsed [Info] or `null`.
   */
  fun extractInfoLine(lines: List<String>): Info? {
    val line = lines.firstOrNull { it.startsWith("@info") } ?: return null
    val json = line.removePrefix("@info").trim()
    if (json.isEmpty()) return null
    val node = openapi.parseJsonElementOrNull(json) ?: return null
    if (!node.isObject) return null
    return parseInfoNode(node)
  }

  /**
   * Parses an [Info] object from a [JsonNode].
   *
   * @param node The JSON node.
   * @return Parsed [Info] or `null`.
   */
  fun parseInfoNode(node: JsonNode?): Info? {
    val obj = node.asObject() ?: return null
    val title = obj.text("title") ?: return null
    val version = obj.text("version") ?: return null
    return Info(
        title = title,
        version = version,
        summary = obj.text("summary"),
        description = obj.text("description"),
        termsOfService = obj.text("termsOfService"),
        contact = parseContactNode(obj.get("contact")),
        license = parseLicenseNode(obj.get("license")),
        extensions = parseExtensions(obj))
  }

  /**
   * Parses a [Contact] object from a [JsonNode].
   *
   * @param node The JSON node.
   * @return Parsed [Contact] or `null`.
   */
  fun parseContactNode(node: JsonNode?): Contact? {
    val obj = node.asObject() ?: return null
    return Contact(
        name = obj.text("name"),
        url = obj.text("url"),
        email = obj.text("email"),
        extensions = parseExtensions(obj))
  }

  /**
   * Parses a [License] object from a [JsonNode].
   *
   * @param node The JSON node.
   * @return Parsed [License] or `null`.
   */
  fun parseLicenseNode(node: JsonNode?): License? {
    val obj = node.asObject() ?: return null
    val name = obj.text("name") ?: return null
    return License(
        name = name,
        identifier = obj.text("identifier"),
        url = obj.text("url"),
        extensions = parseExtensions(obj))
  }

  /**
   * Encodes an [ExternalDocumentation] object into a compact JSON string.
   *
   * @param docs The ExternalDocumentation object.
   * @return Compact JSON string.
   */
  fun renderExternalDocs(docs: ExternalDocumentation): String {
    return OpenApiValues.encodeAnyToString(externalDocsToDocValue(docs))
  }

  /**
   * Converts an [ExternalDocumentation] object into a serializable map.
   *
   * @param docs The ExternalDocumentation object.
   * @return Map representation.
   */
  fun externalDocsToDocValue(docs: ExternalDocumentation): Map<String, Any?> {
    val map = linkedMapOf<String, Any?>()
    map["url"] = docs.url
    if (docs.description != null) map["description"] = docs.description
    docs.extensions.forEach { (k, v) -> if (k.startsWith("x-")) map[k] = v }
    return map
  }

  /**
   * Extracts an [ExternalDocumentation] object from `@externalDocs` or `@see` in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @return Parsed [ExternalDocumentation] or `null`.
   */
  fun extractExternalDocsLine(lines: List<String>): ExternalDocumentation? {
    val explicit = lines.firstOrNull { it.startsWith("@externalDocs") }
    if (explicit != null) {
      val json = explicit.removePrefix("@externalDocs").trim()
      if (json.isEmpty()) return null
      val node = openapi.parseJsonElementOrNull(json) ?: return null
      if (!node.isObject) return null
      return parseExternalDocsNode(node)
    }

    val seeLine = lines.firstOrNull { it.startsWith("@see") } ?: return null
    val content = seeLine.removePrefix("@see").trim()
    if (content.isEmpty()) return null
    val parts = content.split(" ", limit = 2)
    val url = parts[0]
    val desc = parts.getOrNull(1)
    return ExternalDocumentation(url = url, description = desc)
  }

  /**
   * Parses an [ExternalDocumentation] object from a [JsonNode].
   *
   * @param node The JSON node.
   * @return Parsed [ExternalDocumentation] or `null`.
   */
  fun parseExternalDocsNode(node: JsonNode?): ExternalDocumentation? {
    val obj = node.asObject() ?: return null
    val url = obj.text("url") ?: return null
    return ExternalDocumentation(
        description = obj.text("description"), url = url, extensions = parseExtensions(obj))
  }

  /**
   * Converts a [Server] object into a serializable map.
   *
   * @param server The Server object.
   * @return Map representation.
   */
  fun serverToDocValue(server: Server): Map<String, Any?> {
    val map = linkedMapOf<String, Any?>()
    map["url"] = server.url
    if (server.description != null) map["description"] = server.description
    if (server.name != null) map["name"] = server.name
    server.variables?.let { vars ->
      if (vars.isNotEmpty()) {
        map["variables"] =
            vars.mapValues { variable ->
              val variableMap = linkedMapOf<String, Any?>()
              variableMap["default"] = variable.value.default
              if (!variable.value.enum.isNullOrEmpty()) variableMap["enum"] = variable.value.enum
              if (variable.value.description != null) {
                variableMap["description"] = variable.value.description
              }
              variable.value.extensions.forEach { (k, v) ->
                if (k.startsWith("x-")) variableMap[k] = v
              }
              variableMap
            }
      }
    }
    server.extensions.forEach { (k, v) -> if (k.startsWith("x-")) map[k] = v }
    return map
  }

  /**
   * Extracts a list of [Server] objects from `@servers` in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @return List of parsed [Server]s.
   */
  fun extractServersLine(lines: List<String>): List<Server> {
    val line = lines.firstOrNull { it.startsWith("@servers") } ?: return emptyList()
    val content = line.removePrefix("@servers").trim()
    if (content.isEmpty()) return emptyList()
    val node = openapi.parseJsonElementOrNull(content) ?: return emptyList()
    return when {
      node.isArray -> node.elements().mapNotNull { parseServerNode(it) }
      node.isObject -> listOfNotNull(parseServerNode(node))
      else -> emptyList()
    }
  }

  /**
   * Parses a [Server] object from a [JsonNode].
   *
   * @param node The JSON node.
   * @return Parsed [Server] or `null`.
   */
  fun parseServerNode(node: JsonNode): Server? {
    val obj = node.asObject() ?: return null
    return Server(
        url = obj.text("url") ?: "/",
        description = obj.text("description"),
        variables = parseServerVariables(obj.get("variables")),
        name = obj.text("name"),
        extensions = parseExtensions(obj))
  }

  private fun parseServerVariables(node: JsonNode?): Map<String, ServerVariable> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      val value = raw.asObject() ?: return@associate name to ServerVariable(default = "")
      val enumArray = value.get("enum").asArray()
      val enumValues = enumArray?.mapNotNull { it.textValue() }
      name to
          ServerVariable(
              default = value.text("default") ?: "",
              enum = enumValues,
              description = value.text("description"),
              extensions = parseExtensions(value))
    }
  }

  /**
   * Parses specification extensions (`x-*` keys) from a [JsonNode].
   *
   * @param node The JSON node.
   * @return Map of extension keys to [OpenApiValue]s.
   */
  fun parseExtensions(node: JsonNode): Map<String, OpenApiValue> {
    return node
        .fields()
        .asSequence()
        .filter { (key, _) -> key.startsWith("x-") }
        .associate { (key, value) -> key to OpenApiValues.fromJsonElement(value) }
  }
}
