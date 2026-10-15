/**
 * PSI and KDoc parser for OpenAPI root metadata, security requirements, and security schemes on
 * network interfaces.
 */
package psi.network

import domain.Callback
import domain.EndpointParameter
import domain.EndpointResponse
import domain.ExampleObject
import domain.Header
import domain.Link
import domain.MediaTypeObject
import domain.OpenApiValue
import domain.PathItem
import domain.RequestBody
import domain.SchemaDefinition
import domain.SecurityRequirement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import openapi.OpenApiParser
import openapi.fields
import openapi.isObject
import openapi.parser.PathAndOperationParser
import openapi.parser.SchemaNodeParser
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import psi.OpenApiMetadata
import psi.metadata.ComponentsMetadataCodec
import psi.metadata.InfoMetadataCodec
import psi.metadata.SecurityMetadataCodec
import psi.metadata.TagsMetadataCodec

/**
 * Result of parsing operation-level security KDoc tags (`@security`, `@securityEmpty`).
 *
 * @property requirements Parsed list of security requirement maps.
 * @property explicitEmpty Whether `security: []` was explicitly declared.
 */
data class OperationSecurityParseResult(
    val requirements: List<SecurityRequirement>,
    val explicitEmpty: Boolean
)

/**
 * Extracts root-level OpenAPI metadata (`@openapi`, `@info`, `@servers`, `@security`,
 * `@securitySchemes`, `@component*`, `@webhooks`, etc.) and operation-level security requirements
 * from Kotlin PSI files.
 */
object AuthPsiParser {
  private val openApiParser = OpenApiParser()

  private val rootTags =
      setOf(
          "@openapi",
          "@info",
          "@servers",
          "@security",
          "@securityEmpty",
          "@tags",
          "@externalDocs",
          "@see",
          "@extensions",
          "@pathsExtensions",
          "@pathsEmpty",
          "@pathItems",
          "@webhooksExtensions",
          "@webhooksEmpty",
          "@securitySchemes",
          "@componentExamples",
          "@componentLinks",
          "@componentCallbacks",
          "@componentParameters",
          "@componentResponses",
          "@componentRequestBodies",
          "@componentHeaders",
          "@componentPathItems",
          "@componentMediaTypes",
          "@componentSchemas",
          "@componentsExtensions",
          "@webhooks")

  /**
   * Extracts root-level [OpenApiMetadata] from the first interface or class KDoc in [file] that
   * contains OpenAPI root metadata tags.
   *
   * @param file The Kotlin PSI file to inspect.
   * @return Extracted [OpenApiMetadata].
   */
  fun extractRootMetadata(file: KtFile): OpenApiMetadata {
    val lines = extractRootDocLines(file)
    if (lines.isEmpty()) return OpenApiMetadata()

    val openapiMeta = InfoMetadataCodec.extractOpenApiLine(lines)
    val info = InfoMetadataCodec.extractInfoLine(lines)
    val servers = InfoMetadataCodec.extractServersLine(lines)
    val securityResult = SecurityMetadataCodec.extractSecurityLines(lines)
    val tags = TagsMetadataCodec.extractTagObjects(lines)
    val externalDocs = InfoMetadataCodec.extractExternalDocsLine(lines)
    val extensions = extractExtensionsLine(lines)
    val pathsExtensions = extractExtensionsTag(lines, "@pathsExtensions")
    val pathsExplicitEmpty = extractExplicitEmptyTag(lines, "@pathsEmpty")
    val pathItems = ComponentsMetadataCodec.extractPathItemsLine(lines, openApiParser)
    val webhooks = ComponentsMetadataCodec.extractWebhooksLine(lines, openApiParser)
    val webhooksExtensions = extractExtensionsTag(lines, "@webhooksExtensions")
    val webhooksExplicitEmpty = extractExplicitEmptyTag(lines, "@webhooksEmpty")
    val securitySchemes = SecurityMetadataCodec.extractSecuritySchemesLine(lines, openApiParser)
    val componentSchemas = extractComponentSchemasLine(lines)
    val componentExamples = extractComponentExamplesLine(lines)
    val componentLinks = extractComponentLinksLine(lines)
    val componentCallbacks = extractComponentCallbacksLine(lines)
    val componentParameters = extractComponentParametersLine(lines)
    val componentResponses = extractComponentResponsesLine(lines)
    val componentRequestBodies = extractComponentRequestBodiesLine(lines)
    val componentHeaders = extractComponentHeadersLine(lines)
    val componentPathItems = extractComponentPathItemsLine(lines)
    val componentMediaTypes = extractComponentMediaTypesLine(lines)
    val componentsExtensions = extractExtensionsTag(lines, "@componentsExtensions")

    return OpenApiMetadata(
        openapi = openapiMeta.openapi,
        jsonSchemaDialect = openapiMeta.jsonSchemaDialect,
        self = openapiMeta.self,
        info = info,
        servers = servers,
        security = securityResult.requirements,
        securityExplicitEmpty = securityResult.explicitEmpty,
        tags = tags,
        externalDocs = externalDocs,
        extensions = extensions,
        pathsExtensions = pathsExtensions,
        pathsExplicitEmpty = pathsExplicitEmpty,
        pathItems = pathItems,
        webhooks = webhooks,
        webhooksExtensions = webhooksExtensions,
        webhooksExplicitEmpty = webhooksExplicitEmpty,
        securitySchemes = securitySchemes,
        componentSchemas = componentSchemas,
        componentExamples = componentExamples,
        componentLinks = componentLinks,
        componentCallbacks = componentCallbacks,
        componentParameters = componentParameters,
        componentResponses = componentResponses,
        componentRequestBodies = componentRequestBodies,
        componentHeaders = componentHeaders,
        componentPathItems = componentPathItems,
        componentMediaTypes = componentMediaTypes,
        componentsExtensions = componentsExtensions)
  }

  /**
   * Extracts normalized root KDoc lines from the first class/interface in [file] containing root
   * tags.
   *
   * @param file The Kotlin PSI file.
   * @return List of cleaned KDoc lines, or empty list if none found.
   */
  fun extractRootDocLines(file: KtFile): List<String> {
    val match =
        file
            .collectDescendantsOfType<KtClass>()
            .asSequence()
            .mapNotNull { it.docComment?.text }
            .map { it.lines().map(::cleanKDocLine) }
            .firstOrNull { lines -> lines.any { line -> rootTags.any { line.startsWith(it) } } }
    return if (match != null) match.toList() else emptyList()
  }

  /**
   * Extracts operation-level `@security` and `@securityEmpty` declarations from [element].
   *
   * @param element The function or declaration to inspect.
   * @return Parsed [OperationSecurityParseResult].
   */
  fun extractSecurity(element: KtDeclaration): OperationSecurityParseResult {
    val docComment = element.docComment ?: return OperationSecurityParseResult(emptyList(), false)
    val lines = docComment.text.lines().map { cleanKDocLine(it) }
    val res = SecurityMetadataCodec.extractSecurityLines(lines)
    return OperationSecurityParseResult(res.requirements, res.explicitEmpty)
  }

  /**
   * Parses a JSON object string of callbacks into a map of [Callback] definitions.
   *
   * @param json Compact JSON string representing a callbacks map.
   * @return Parsed map of callback names to [Callback]s.
   */
  fun parseCallbacksJson(json: String): Map<String, Callback> {
    val callbacksNode = openapi.parseJsonElementOrNull(json) ?: return emptyMap()
    if (!callbacksNode.isObject) return emptyMap()

    val root = buildJsonObject {
      put("openapi", "3.2.0")
      putJsonObject("info") {
        put("title", "Callbacks")
        put("version", "0.0.0")
      }
      putJsonObject("paths") {
        putJsonObject("/_callbacks") {
          putJsonObject("post") {
            put("callbacks", callbacksNode)
            putJsonObject("responses") { putJsonObject("200") { put("description", "ok") } }
          }
        }
      }
    }

    val definition =
        openApiParser.parseString(
            Json.encodeToString(JsonElement.serializer(), root), OpenApiParser.Format.JSON)
    return definition.paths.getValue("/_callbacks").post!!.callbacks
  }

  private fun extractExtensionsLine(lines: List<String>): Map<String, OpenApiValue> {
    val extensionLines = lines.filter { it.startsWith("@extensions") }
    if (extensionLines.isEmpty()) return emptyMap()
    val merged = LinkedHashMap<String, OpenApiValue>()
    extensionLines.forEach { line ->
      val json = line.removePrefix("@extensions").trim()
      val node = openapi.parseJsonElementOrNull(json) ?: return@forEach
      if (!node.isObject) return@forEach
      node.fields().forEach { (key, value) -> merged[key] = SchemaNodeParser.nodeToValue(value) }
    }
    return merged
  }

  private fun extractExplicitEmptyTag(lines: List<String>, tag: String): Boolean {
    val line = lines.firstOrNull { it == tag || it.startsWith("$tag ") } ?: return false
    val raw = line.removePrefix(tag).trim()
    if (raw.isEmpty()) return true
    return parseBooleanTagValue(raw) == true
  }

  private fun extractExtensionsTag(lines: List<String>, tag: String): Map<String, OpenApiValue> {
    val line = lines.firstOrNull { it.startsWith(tag) } ?: return emptyMap()
    val json = line.removePrefix(tag).trim()
    val node = openapi.parseJsonElementOrNull(json) ?: return emptyMap()
    if (!node.isObject) return emptyMap()
    return node.fields().asSequence().associate { (key, value) ->
      key to SchemaNodeParser.nodeToValue(value)
    }
  }

  private fun extractComponentSchemasLine(lines: List<String>): Map<String, SchemaDefinition> {
    val parsed =
        ComponentsMetadataCodec.parseWrappedComponentSection(
            lines, "@componentSchemas", "Component Schemas", "schemas", openApiParser)
    return if (parsed != null) parsed.schemas else emptyMap()
  }

  private fun extractComponentExamplesLine(lines: List<String>): Map<String, ExampleObject> {
    val line = lines.firstOrNull { it.startsWith("@componentExamples") } ?: return emptyMap()
    val json = line.removePrefix("@componentExamples").trim()
    val node = openapi.parseJsonElementOrNull(json)
    return PathAndOperationParser.parseExampleMap(node, null)
  }

  private fun extractComponentLinksLine(lines: List<String>): Map<String, Link> {
    val line = lines.firstOrNull { it.startsWith("@componentLinks") } ?: return emptyMap()
    val json = line.removePrefix("@componentLinks").trim()
    return RequestBodyPsiParser.parseLinksJson(json)
  }

  private fun extractComponentCallbacksLine(lines: List<String>): Map<String, Callback> {
    val line = lines.firstOrNull { it.startsWith("@componentCallbacks") } ?: return emptyMap()
    val json = line.removePrefix("@componentCallbacks").trim()
    return parseCallbacksJson(json)
  }

  private fun extractComponentParametersLine(lines: List<String>): Map<String, EndpointParameter> {
    val parsed =
        ComponentsMetadataCodec.parseWrappedComponentSection(
            lines, "@componentParameters", "Component Parameters", "parameters", openApiParser)
    return if (parsed != null) parsed.parameters else emptyMap()
  }

  private fun extractComponentResponsesLine(lines: List<String>): Map<String, EndpointResponse> {
    val parsed =
        ComponentsMetadataCodec.parseWrappedComponentSection(
            lines, "@componentResponses", "Component Responses", "responses", openApiParser)
    return if (parsed != null) parsed.responses else emptyMap()
  }

  private fun extractComponentRequestBodiesLine(lines: List<String>): Map<String, RequestBody> {
    val parsed =
        ComponentsMetadataCodec.parseWrappedComponentSection(
            lines,
            "@componentRequestBodies",
            "Component Request Bodies",
            "requestBodies",
            openApiParser)
    return if (parsed != null) parsed.requestBodies else emptyMap()
  }

  private fun extractComponentHeadersLine(lines: List<String>): Map<String, Header> {
    val parsed =
        ComponentsMetadataCodec.parseWrappedComponentSection(
            lines, "@componentHeaders", "Component Headers", "headers", openApiParser)
    return if (parsed != null) parsed.headers else emptyMap()
  }

  private fun extractComponentPathItemsLine(lines: List<String>): Map<String, PathItem> {
    val parsed =
        ComponentsMetadataCodec.parseWrappedComponentSection(
            lines, "@componentPathItems", "Component Path Items", "pathItems", openApiParser)
    return if (parsed != null) parsed.pathItems else emptyMap()
  }

  private fun extractComponentMediaTypesLine(lines: List<String>): Map<String, MediaTypeObject> {
    val parsed =
        ComponentsMetadataCodec.parseWrappedComponentSection(
            lines, "@componentMediaTypes", "Component Media Types", "mediaTypes", openApiParser)
    return if (parsed != null) parsed.mediaTypes else emptyMap()
  }

  /**
   * Parses an optional boolean KDoc tag token (`true`, `false`, or `null` when omitted).
   *
   * @param value Raw token string or `null`.
   * @return Parsed boolean value or `null` if unrecognized.
   */
  fun parseBooleanTagValue(value: String?): Boolean? {
    return when (value?.lowercase()) {
      null -> true
      "true" -> true
      "false" -> false
      else -> null
    }
  }

  /**
   * Strips leading/trailing KDoc comment delimiters and asterisk prefixes from a single KDoc line.
   *
   * @param line Raw KDoc line.
   * @return Cleaned KDoc line text.
   */
  fun cleanKDocLine(line: String): String {
    return line.trim().replace("/**", "").replace("*/", "").replaceFirst("*", "").trim()
  }
}
