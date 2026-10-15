/**
 * PSI and KDoc parser for OpenAPI request bodies, responses, media-type content maps, headers, and
 * links on network operations.
 */
package psi.network

import domain.EndpointResponse
import domain.Header
import domain.Link
import domain.MediaTypeObject
import domain.OpenApiValue
import domain.ReferenceObject
import domain.RequestBody
import openapi.asObject
import openapi.asText
import openapi.boolean
import openapi.get
import openapi.has
import openapi.isTextual
import openapi.parser.PathAndOperationParser
import openapi.parser.SchemaNodeParser
import openapi.text
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import psi.TypeMappers

/**
 * Result of parsing an `@requestBody` KDoc tag.
 *
 * @property body Parsed [RequestBody].
 * @property requiredPresent Whether `required` was explicitly present in the JSON object.
 */
data class RequestBodyParseResult(val body: RequestBody, val requiredPresent: Boolean)

/**
 * Parses request bodies, responses, media-type content maps, headers, links, and Ktor `contentType`
 * calls from Kotlin PSI and KDoc tags.
 */
object RequestBodyPsiParser {
  /**
   * Extracts a Ktor `contentType(...)` call argument as a MIME type string.
   *
   * @param expression The `contentType(...)` call expression.
   * @return Resolved MIME type string (e.g., `application/json`) or `null`.
   */
  fun parseContentType(expression: KtCallExpression): String? {
    val firstArg = expression.valueArguments.firstOrNull() ?: return null
    val argExpr = firstArg.getArgumentExpression()!!
    val text = argExpr.text

    if (text.startsWith("ContentType.parse(")) {
      val raw = text.substringAfter("ContentType.parse(").substringBeforeLast(")")
      return raw.removeSurrounding("\"".toString())
    }

    if (text.startsWith("ContentType.")) {
      val parts = text.split(".")
      if (parts.size >= 3) {
        val type = parts[1].lowercase()
        val subType = parts[2].lowercase()
        return "$type/$subType"
      }
    }

    return null
  }

  /**
   * Extracts an `@requestBody` override from [element] KDoc.
   *
   * @param element The function declaration to inspect.
   * @return Parsed [RequestBodyParseResult] or `null`.
   */
  fun extractRequestBody(element: KtDeclaration): RequestBodyParseResult? {
    val docComment = element.docComment ?: return null
    val line =
        docComment.text
            .lines()
            .map { AuthPsiParser.cleanKDocLine(it) }
            .find { it.startsWith("@requestBody") } ?: return null
    val json = line.removePrefix("@requestBody").trim()
    if (json.isEmpty()) return null
    return parseRequestBodyJson(json)
  }

  /**
   * Merges an `@requestBody` KDoc override onto a signature-inferred [base] [RequestBody].
   *
   * @param base Signature-inferred [RequestBody] or `null`.
   * @param overrideResult KDoc `@requestBody` parse result.
   * @return Merged [RequestBody].
   */
  fun mergeRequestBody(base: RequestBody?, overrideResult: RequestBodyParseResult): RequestBody {
    val overrideBody = overrideResult.body
    if (overrideBody.reference != null) return overrideBody
    if (base == null) return overrideBody
    val mergedContent = if (overrideBody.contentPresent) overrideBody.content else base.content
    val mergedRequired =
        if (overrideResult.requiredPresent) overrideBody.required else base.required
    val mergedDescription = overrideBody.description ?: base.description
    val mergedExtensions =
        if (overrideBody.extensions.isNotEmpty()) overrideBody.extensions else base.extensions
    return base.copy(
        description = mergedDescription,
        content = mergedContent,
        contentPresent = true,
        required = mergedRequired,
        reference = null,
        extensions = mergedExtensions)
  }

  /**
   * Extracts all `@response*` KDoc tags from [element] into a status-code map of
   * [EndpointResponse]s.
   *
   * @param element The function declaration to inspect.
   * @return Map of status code to [EndpointResponse].
   */
  fun extractResponses(element: KtDeclaration): Map<String, EndpointResponse> {
    val docComment = element.docComment ?: return emptyMap()
    val responses = mutableMapOf<String, EndpointResponse>()
    val lines = docComment.text.lines().map { AuthPsiParser.cleanKDocLine(it) }
    lines
        .filter { it.startsWith("@response ") }
        .forEach { line ->
          val parts = line.removePrefix("@response").trim().split(" ", limit = 3)
          val code = parts[0]
          val type = if (parts.size > 1 && parts[1] != "Unit") parts[1] else null
          val desc = if (parts.size > 2) parts[2] else null
          responses[code] =
              EndpointResponse(
                  statusCode = code,
                  description = desc,
                  type = type,
                  content = contentForType(type))
        }

    lines
        .filter { it.startsWith("@responseHeaders") }
        .forEach { line ->
          val pair = parseResponsePayload(line, "@responseHeaders")
          if (pair != null) {
            val (code, json) = pair
            val headers = parseHeadersJson(json)
            val existing = responses[code] ?: EndpointResponse(statusCode = code)
            responses[code] = existing.copy(headers = headers)
          }
        }

    lines
        .filter { it.startsWith("@responseLinks") }
        .forEach { line ->
          val pair = parseResponsePayload(line, "@responseLinks")
          if (pair != null) {
            val (code, json) = pair
            val links = parseLinksJson(json)
            val existing = responses[code] ?: EndpointResponse(statusCode = code)
            responses[code] = existing.copy(links = links)
          }
        }

    lines
        .filter { it.startsWith("@responseContent") }
        .forEach { line ->
          val pair = parseResponsePayload(line, "@responseContent")
          if (pair != null) {
            val (code, json) = pair
            val content = parseContentJson(json)
            val existing = responses[code] ?: EndpointResponse(statusCode = code)
            val derivedType = existing.type ?: resolveTypeFromContent(content)
            responses[code] = existing.copy(content = content, type = derivedType)
          }
        }

    lines
        .filter { it.startsWith("@responseSummary") }
        .forEach { line ->
          val pair = parseResponsePayload(line, "@responseSummary")
          if (pair != null) {
            val (code, summary) = pair
            val existing = responses[code] ?: EndpointResponse(statusCode = code)
            responses[code] = existing.copy(summary = summary)
          }
        }

    extractResponseRefs(lines).forEach { (code, reference) ->
      val existing = responses[code] ?: EndpointResponse(statusCode = code)
      val summary = reference.summary ?: existing.summary
      val description = reference.description ?: existing.description
      responses[code] =
          existing.copy(summary = summary, description = description, reference = reference)
    }

    extractResponseExtensions(lines).forEach { (code, extensions) ->
      val existing = responses[code] ?: EndpointResponse(statusCode = code)
      val merged = existing.extensions + extensions
      responses[code] = existing.copy(extensions = merged)
    }
    return responses
  }

  /**
   * Builds a default `application/json` content map for a Kotlin return [type].
   *
   * @param type Kotlin type string or `null`.
   * @return Content map or empty map when [type] is null or `Unit`.
   */
  fun contentForType(type: String?): Map<String, MediaTypeObject> {
    if (type == null || type == "Unit") return emptyMap()
    val schema = TypeMappers.kotlinToSchemaProperty(type)
    return mapOf("application/json" to MediaTypeObject(schema = schema))
  }

  /**
   * Resolves a Kotlin type name from a media-type [content] map.
   *
   * @param content Media-type content map.
   * @return Resolved Kotlin type name or `null`.
   */
  fun resolveTypeFromContent(content: Map<String, MediaTypeObject>): String? {
    val schema = SchemaNodeParser.selectSchema(content, null) ?: return null
    return TypeMappers.mapType(schema)
  }

  /**
   * Parses a [ReferenceObject] from a raw string or JSON object literal.
   *
   * @param raw Raw reference string or JSON literal.
   * @return Parsed [ReferenceObject] or `null`.
   */
  fun parseReferenceObjectValue(raw: String): ReferenceObject? {
    val trimmed = raw.trim()
    val node = openapi.parseJsonElementOrNull(trimmed) ?: return ReferenceObject(ref = trimmed)
    if (node.isTextual) {
      return ReferenceObject(ref = node.asText()!!)
    }
    val obj = node.asObject() ?: return null
    val ref = obj.text("\$ref") ?: return null
    return ReferenceObject(
        ref = ref,
        summary = obj.text("summary"),
        description = obj.text("description"),
        extensions = SchemaNodeParser.parseExtensions(obj))
  }

  /**
   * Parses a JSON object string into a map of [Header] objects.
   *
   * @param json JSON string.
   * @return Map of header name to [Header].
   */
  fun parseHeadersJson(json: String): Map<String, Header> {
    val node = openapi.parseJsonElementOrNull(json)
    return PathAndOperationParser.parseHeaders(node, null)
  }

  /**
   * Parses a JSON object string into a map of [Link] objects.
   *
   * @param json JSON string.
   * @return Map of link name to [Link].
   */
  fun parseLinksJson(json: String): Map<String, Link> {
    val node = openapi.parseJsonElementOrNull(json)
    return PathAndOperationParser.parseLinks(node, null)
  }

  /**
   * Parses a JSON object string into a media-type content map.
   *
   * @param json JSON string.
   * @return Map of media type to [MediaTypeObject].
   */
  fun parseContentJson(json: String): Map<String, MediaTypeObject> {
    val node = openapi.parseJsonElementOrNull(json)
    return PathAndOperationParser.parseContentMap(node, null)
  }

  private fun parseRequestBodyJson(json: String): RequestBodyParseResult? {
    val node = openapi.parseJsonElementOrNull(json)
    val obj = node.asObject() ?: return null
    val ref = obj.text("\$ref")
    if (ref != null) {
      val reference =
          ReferenceObject(
              ref = ref,
              summary = obj.text("summary"),
              description = obj.text("description"),
              extensions = SchemaNodeParser.parseExtensions(obj))
      return RequestBodyParseResult(RequestBody(reference = reference), obj.has("required"))
    }
    val requiredPresent = obj.has("required")
    val hasContent = obj.has("content")
    return RequestBodyParseResult(
        body =
            RequestBody(
                description = obj.text("description"),
                content = PathAndOperationParser.parseContentMap(obj.get("content"), null),
                contentPresent = hasContent,
                required = obj.boolean("required") ?: false,
                reference = null,
                extensions = SchemaNodeParser.parseExtensions(obj)),
        requiredPresent = requiredPresent)
  }

  private fun extractResponseRefs(lines: List<String>): Map<String, ReferenceObject> {
    val refs = mutableMapOf<String, ReferenceObject>()
    lines
        .filter { it.startsWith("@responseRef") }
        .forEach { line ->
          val pair = parseResponsePayload(line, "@responseRef")
          if (pair != null) {
            val (code, json) = pair
            val refObj = parseReferenceObjectValue(json)
            if (refObj != null) refs[code] = refObj
          }
        }
    return refs
  }

  private fun extractResponseExtensions(
      lines: List<String>
  ): Map<String, Map<String, OpenApiValue>> {
    val extensions = mutableMapOf<String, Map<String, OpenApiValue>>()
    lines
        .filter { it.startsWith("@responseExtensions") }
        .forEach { line ->
          val pair = parseResponsePayload(line, "@responseExtensions")
          if (pair != null) {
            val (code, json) = pair
            val node = openapi.parseJsonElementOrNull(json) ?: return@forEach
            val parsed = SchemaNodeParser.parseExtensions(node)
            if (parsed.isNotEmpty()) {
              extensions[code] = (extensions[code] ?: emptyMap()) + parsed
            }
          }
        }
    return extensions
  }

  private fun parseResponsePayload(line: String, tag: String): Pair<String, String>? {
    val content = line.removePrefix(tag).trim()
    val parts = content.split(" ", limit = 2)
    if (parts.size < 2) return null
    val code = parts[0].trim()
    val json = parts[1].trim()
    return code to json
  }
}
