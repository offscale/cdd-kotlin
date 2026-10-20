/** Encodes and decodes OpenAPI Tag metadata (`@tags` and `@tag`) for Kotlin KDoc round-tripping. */
package psi.metadata

import domain.OpenApiArray
import domain.OpenApiObject
import domain.OpenApiString
import domain.OpenApiValue
import domain.OpenApiValues
import domain.Tag
import openapi.JsonNode
import openapi.asObject
import openapi.elements
import openapi.get
import openapi.isArray
import openapi.isObject
import openapi.text

/** Encodes and decodes OpenAPI [Tag] definitions in KDoc blocks. */
object TagsMetadataCodec {
  /**
   * Serializes a list of [Tag]s into a compact JSON array string for `@tags`.
   *
   * @param tags List of tags.
   * @return Compact JSON string.
   */
  fun renderTags(tags: List<Tag>): String {
    val mapped = tags.map { tagToDocValue(it) }
    return OpenApiValues.encodeToString(OpenApiArray(mapped))
  }

  /**
   * Converts a [Tag] into an [OpenApiObject] suitable for JSON serialization.
   *
   * @param tag Tag to convert.
   * @return [OpenApiObject] representation.
   */
  fun tagToDocValue(tag: Tag): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["name"] = OpenApiString(tag.name)
    tag.summary?.let { map["summary"] = OpenApiString(it) }
    tag.description?.let { map["description"] = OpenApiString(it) }
    tag.externalDocs?.let { map["externalDocs"] = InfoMetadataCodec.externalDocsToDocValue(it) }
    tag.parent?.let { map["parent"] = OpenApiString(it) }
    tag.kind?.let { map["kind"] = OpenApiString(it) }
    tag.extensions.forEach { (key, value) ->
      if (key.startsWith("x-")) {
        map[key] = value
      }
    }
    return OpenApiObject(map)
  }

  /**
   * Extracts structured [Tag] objects from `@tags` JSON lines in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @return List of parsed [Tag]s.
   */
  fun extractTagObjects(lines: List<String>): List<Tag> {
    val tagsLine = lines.firstOrNull { it.startsWith("@tags") } ?: return emptyList()
    val json = tagsLine.removePrefix("@tags").trim()
    if (json.isEmpty()) return emptyList()
    val node = openapi.parseJsonElementOrNull(json) ?: return emptyList()
    val items =
        when {
          node.isArray -> node.elements()
          node.isObject -> listOf(node)
          else -> emptyList()
        }
    return items.mapNotNull { parseTagNode(it) }
  }

  /**
   * Parses a [JsonNode] into a [Tag].
   *
   * @param node JSON node.
   * @return Parsed [Tag] or `null`.
   */
  fun parseTagNode(node: JsonNode?): Tag? {
    val obj = node.asObject() ?: return null
    val name = obj.text("name") ?: return null
    return Tag(
        name = name,
        summary = obj.text("summary"),
        description = obj.text("description"),
        externalDocs = InfoMetadataCodec.parseExternalDocsNode(obj.get("externalDocs")),
        parent = obj.text("parent"),
        kind = obj.text("kind"),
        extensions = InfoMetadataCodec.parseExtensions(obj))
  }
}
