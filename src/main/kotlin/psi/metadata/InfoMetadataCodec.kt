/**
 * Encodes and decodes OpenAPI root Info, Contact, License, Server, and ExternalDocumentation KDoc
 * metadata.
 */
package psi.metadata

import domain.Contact
import domain.ExternalDocumentation
import domain.Info
import domain.License
import domain.OpenApiArray
import domain.OpenApiObject
import domain.OpenApiString
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

/**
 * Result of extracting the `@openapi` header KDoc tag.
 *
 * @property openapi OpenAPI version string.
 * @property jsonSchemaDialect Optional JSON Schema dialect URI.
 * @property self Optional `$self` URI.
 */
data class OpenApiHeaderResult(
    val openapi: String? = null,
    val jsonSchemaDialect: String? = null,
    val self: String? = null
)

/**
 * Encodes and decodes OpenAPI root [Info], [Contact], [License], [Server], and
 * [ExternalDocumentation] KDoc metadata.
 */
object InfoMetadataCodec {
  /**
   * Serializes root OpenAPI version, dialect, and `$self` from [metadata] into a compact JSON
   * string for `@openapi`.
   *
   * @param metadata The [psi.OpenApiMetadata] to render.
   * @return Compact JSON string.
   */
  fun renderOpenApiMeta(metadata: psi.OpenApiMetadata): String {
    val map = LinkedHashMap<String, OpenApiValue>()
    metadata.openapi?.let { map["openapi"] = OpenApiString(it) }
    metadata.jsonSchemaDialect?.let { map["jsonSchemaDialect"] = OpenApiString(it) }
    metadata.self?.let { map["\$self"] = OpenApiString(it) }
    return OpenApiValues.encodeToString(OpenApiObject(map))
  }

  /**
   * Extracts [OpenApiHeaderResult] from `@openapi` in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @return Extracted [OpenApiHeaderResult].
   */
  fun extractOpenApiLine(lines: List<String>): OpenApiHeaderResult {
    val line = lines.firstOrNull { it.startsWith("@openapi") } ?: return OpenApiHeaderResult()
    val json = line.removePrefix("@openapi").trim()
    if (json.isEmpty()) return OpenApiHeaderResult()
    val node = openapi.parseJsonElementOrNull(json) ?: return OpenApiHeaderResult()
    val obj = node.asObject() ?: return OpenApiHeaderResult()
    return OpenApiHeaderResult(
        openapi = obj.text("openapi"),
        jsonSchemaDialect = obj.text("jsonSchemaDialect"),
        self = obj.text("\$self"))
  }

  /**
   * Serializes an [Info] object into a compact JSON string for `@info`.
   *
   * @param info Info object.
   * @return Compact JSON string.
   */
  fun renderInfo(info: Info): String {
    return OpenApiValues.encodeToString(infoToDocValue(info))
  }

  /**
   * Converts an [Info] object into an [OpenApiObject].
   *
   * @param info Info object.
   * @return [OpenApiObject] representation.
   */
  fun infoToDocValue(info: Info): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["title"] = OpenApiString(info.title)
    map["version"] = OpenApiString(info.version)
    info.summary?.let { map["summary"] = OpenApiString(it) }
    info.description?.let { map["description"] = OpenApiString(it) }
    info.termsOfService?.let { map["termsOfService"] = OpenApiString(it) }
    info.contact?.let { map["contact"] = contactToDocValue(it) }
    info.license?.let { map["license"] = licenseToDocValue(it) }
    info.extensions.forEach { (key, value) ->
      if (key.startsWith("x-")) {
        map[key] = value
      }
    }
    return OpenApiObject(map)
  }

  private fun contactToDocValue(contact: Contact): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    contact.name?.let { map["name"] = OpenApiString(it) }
    contact.url?.let { map["url"] = OpenApiString(it) }
    contact.email?.let { map["email"] = OpenApiString(it) }
    contact.extensions.forEach { (key, value) ->
      if (key.startsWith("x-")) {
        map[key] = value
      }
    }
    return OpenApiObject(map)
  }

  private fun licenseToDocValue(license: License): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["name"] = OpenApiString(license.name)
    license.identifier?.let { map["identifier"] = OpenApiString(it) }
    license.url?.let { map["url"] = OpenApiString(it) }
    license.extensions.forEach { (key, value) ->
      if (key.startsWith("x-")) {
        map[key] = value
      }
    }
    return OpenApiObject(map)
  }

  /**
   * Extracts an [Info] object from `@info` in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @return Parsed [Info] or `null`.
   */
  fun extractInfoLine(lines: List<String>): Info? {
    val line = lines.firstOrNull { it.startsWith("@info") } ?: return null
    val json = line.removePrefix("@info").trim()
    if (json.isEmpty()) return null
    val node = openapi.parseJsonElementOrNull(json) ?: return null
    return parseInfoNode(node)
  }

  /**
   * Parses a [JsonNode] into an [Info].
   *
   * @param node JSON node.
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

  private fun parseContactNode(node: JsonNode?): Contact? {
    val obj = node.asObject() ?: return null
    return Contact(
        name = obj.text("name"),
        url = obj.text("url"),
        email = obj.text("email"),
        extensions = parseExtensions(obj))
  }

  /**
   * Parses a [JsonNode] into a [License].
   *
   * @param node JSON node.
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
   * Serializes an [ExternalDocumentation] object into a compact JSON string for `@externalDocs`.
   *
   * @param docs ExternalDocumentation object.
   * @return Compact JSON string.
   */
  fun renderExternalDocs(docs: ExternalDocumentation): String {
    return OpenApiValues.encodeToString(externalDocsToDocValue(docs))
  }

  /**
   * Converts an [ExternalDocumentation] object into an [OpenApiObject].
   *
   * @param docs ExternalDocumentation object.
   * @return [OpenApiObject] representation.
   */
  fun externalDocsToDocValue(docs: ExternalDocumentation): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["url"] = OpenApiString(docs.url)
    docs.description?.let { map["description"] = OpenApiString(it) }
    docs.extensions.forEach { (key, value) ->
      if (key.startsWith("x-")) {
        map[key] = value
      }
    }
    return OpenApiObject(map)
  }

  /**
   * Extracts an [ExternalDocumentation] object from `@externalDocs` or `@see` in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @return Parsed [ExternalDocumentation] or `null`.
   */
  fun extractExternalDocsLine(lines: List<String>): ExternalDocumentation? {
    val line = lines.firstOrNull { it.startsWith("@externalDocs") }
    if (line != null) {
      val json = line.removePrefix("@externalDocs").trim()
      if (json.isEmpty()) return null
      val node = openapi.parseJsonElementOrNull(json) ?: return null
      return parseExternalDocsNode(node)
    }

    val seeLine = lines.firstOrNull { it.startsWith("@see") } ?: return null
    val content = seeLine.removePrefix("@see").trim()
    if (content.isEmpty()) return null
    val parts = content.split(" ", limit = 2)
    val url = parts[0]
    val desc = parts.getOrNull(1)
    return ExternalDocumentation(description = desc, url = url)
  }

  /**
   * Parses a [JsonNode] into an [ExternalDocumentation].
   *
   * @param node JSON node.
   * @return Parsed [ExternalDocumentation] or `null`.
   */
  fun parseExternalDocsNode(node: JsonNode?): ExternalDocumentation? {
    val obj = node.asObject() ?: return null
    val url = obj.text("url") ?: return null
    return ExternalDocumentation(
        description = obj.text("description"), url = url, extensions = parseExtensions(obj))
  }

  /**
   * Converts a [Server] into an [OpenApiObject].
   *
   * @param server Server to convert.
   * @return [OpenApiObject] representation.
   */
  fun serverToDocValue(server: Server): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["url"] = OpenApiString(server.url)
    server.description?.let { map["description"] = OpenApiString(it) }
    server.name?.let { map["name"] = OpenApiString(it) }
    if (!server.variables.isNullOrEmpty()) {
      map["variables"] =
          OpenApiObject(
              server.variables.mapValues { (_, variable) ->
                val varMap = LinkedHashMap<String, OpenApiValue>()
                varMap["default"] = OpenApiString(variable.default)
                if (!variable.enum.isNullOrEmpty()) {
                  varMap["enum"] = OpenApiArray(variable.enum.map { OpenApiString(it) })
                }
                variable.description?.let { varMap["description"] = OpenApiString(it) }
                variable.extensions.forEach { (key, value) ->
                  if (key.startsWith("x-")) {
                    varMap[key] = value
                  }
                }
                OpenApiObject(varMap)
              })
    }
    server.extensions.forEach { (key, value) ->
      if (key.startsWith("x-")) {
        map[key] = value
      }
    }
    return OpenApiObject(map)
  }

  /**
   * Extracts a list of [Server]s from `@servers` in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @return List of parsed [Server]s.
   */
  fun extractServersLine(lines: List<String>): List<Server> {
    val line = lines.firstOrNull { it.startsWith("@servers") } ?: return emptyList()
    val json = line.removePrefix("@servers").trim()
    if (json.isEmpty()) return emptyList()
    val node = openapi.parseJsonElementOrNull(json) ?: return emptyList()
    val items =
        when {
          node.isArray -> node.elements()
          node.isObject -> listOf(node)
          else -> emptyList()
        }
    return items.mapNotNull { parseServerNode(it) }
  }

  /**
   * Parses a [JsonNode] into a [Server].
   *
   * @param node JSON node.
   * @return Parsed [Server] or `null`.
   */
  fun parseServerNode(node: JsonNode?): Server? {
    val obj = node.asObject() ?: return null
    val url = obj.text("url") ?: "/"
    return Server(
        url = url,
        description = obj.text("description"),
        name = obj.text("name"),
        variables = parseServerVariables(obj.get("variables")),
        extensions = parseExtensions(obj))
  }

  private fun parseServerVariables(node: JsonNode?): Map<String, ServerVariable>? {
    val obj = node.asObject() ?: return null
    return obj.fields().asSequence().associate { (name, raw) ->
      val varObj = raw.asObject()
      val default = varObj?.text("default") ?: "default"
      val enumNode = varObj?.get("enum").asArray()
      val enumValues = enumNode?.mapNotNull { it.textValue() }
      val desc = varObj?.text("description")
      val extensions = if (varObj != null) parseExtensions(varObj) else emptyMap()
      name to
          ServerVariable(
              default = default, enum = enumValues, description = desc, extensions = extensions)
    }
  }

  /**
   * Parses all `x-` specification extensions from [node].
   *
   * @param node JSON object node.
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
