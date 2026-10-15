/** Encodes and decodes OpenAPI Tag metadata (`@tags` and `@tag`) for Kotlin KDoc round-tripping. */
package psi.metadata

import domain.OpenApiValues
import domain.Tag
import openapi.JsonNode
import openapi.asObject
import openapi.elements
import openapi.get
import openapi.isArray
import openapi.text

/** Encodes and decodes OpenAPI [Tag] metadata to and from KDoc tag lines. */
object TagsMetadataCodec {
  /**
   * Encodes a list of [Tag] objects into a compact JSON array string for `@tags`.
   *
   * @param tags List of Tag objects.
   * @return Compact JSON string.
   */
  fun renderTags(tags: List<Tag>): String {
    val tagValues = tags.map { tagToDocValue(it) }
    return OpenApiValues.encodeAnyToString(tagValues)
  }

  /**
   * Converts a [Tag] object into a serializable map.
   *
   * @param tag The Tag object.
   * @return Map representation.
   */
  fun tagToDocValue(tag: Tag): Map<String, Any?> {
    val map = linkedMapOf<String, Any?>()
    map["name"] = tag.name
    if (tag.summary != null) map["summary"] = tag.summary
    if (tag.description != null) map["description"] = tag.description
    tag.externalDocs?.let { map["externalDocs"] = InfoMetadataCodec.externalDocsToDocValue(it) }
    if (tag.parent != null) map["parent"] = tag.parent
    if (tag.kind != null) map["kind"] = tag.kind
    tag.extensions.forEach { (k, v) -> if (k.startsWith("x-")) map[k] = v }
    return map
  }

  /**
   * Decodes a list of [Tag] objects from the `@tags` KDoc tag in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @return List of parsed [Tag]s.
   */
  fun extractTagObjects(lines: List<String>): List<Tag> {
    val line = lines.firstOrNull { it.startsWith("@tags") } ?: return emptyList()
    val json = line.removePrefix("@tags").trim()
    if (json.isEmpty()) return emptyList()
    val node = openapi.parseJsonElementOrNull(json) ?: return emptyList()
    if (!node.isArray) return emptyList()
    return node.elements().mapNotNull { parseTagNode(it) }
  }

  /**
   * Parses a [Tag] object from a [JsonNode].
   *
   * @param node The JSON node.
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
