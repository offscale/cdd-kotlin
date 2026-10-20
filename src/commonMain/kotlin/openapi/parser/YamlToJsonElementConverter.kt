/**
 * Pure Kotlin Multiplatform converter between YAML 1.2 documents and
 * `kotlinx.serialization.json.JsonElement`.
 *
 * Enables YAML parsing and serialization across all targets (JVM and WASM) without requiring
 * platform-specific YAML libraries.
 */
package openapi.parser

import domain.CddError
import domain.toFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Converts between YAML strings and [JsonElement] AST nodes. */
object YamlToJsonElementConverter {
  private val prettyJson = Json { prettyPrint = true }
  private val NL = Char(10)
  private val BS = Char(92)
  private val SQ = Char(39)
  private val DQ = Char(34)

  /**
   * Serializes a [JsonElement] into a formatted YAML 1.2 document string.
   *
   * @param element The [JsonElement] to serialize.
   * @return A valid YAML 1.2 string representation of [element].
   */
  fun toYaml(element: JsonElement): String {
    return serializeBlock(element, "") + NL
  }

  /**
   * Parses a YAML 1.2 or JSON string into a [JsonElement] wrapped in a [Result].
   *
   * @param source The raw YAML or JSON string.
   * @return A [Result] containing the parsed [JsonElement] or a [CddError.YamlParseError].
   */
  fun parseYamlResult(source: String): Result<JsonElement> {
    val trimmed = source.trim()
    if (trimmed.isEmpty()) return Result.success(JsonNull)
    if ((trimmed.startsWith("{") && !trimmed.endsWith("}")) ||
        (trimmed.startsWith("[") && !trimmed.endsWith("]"))) {
      return CddError.JsonParseError("Unclosed flow collection in YAML input").toFailure()
    }
    return Result.success(parseYaml(source))
  }

  /**
   * Parses a YAML 1.2 or JSON string into a [JsonElement].
   *
   * @param source The raw YAML or JSON string.
   * @return The parsed [JsonElement].
   */
  fun parseYaml(source: String): JsonElement {
    val trimmed = source.trim()
    if (trimmed.isEmpty()) return JsonNull
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      return openapi.parseJsonElementOrNull(trimmed) ?: parseScalarOrFlow(trimmed)
    }
    val rawLines = source.lines()
    val lines = preprocessLines(rawLines)
    if (lines.isEmpty()) return JsonNull
    val (element, _) = parseNode(lines, 0)
    return element
  }

  /**
   * Serializes a [JsonElement] at the given [indent] level.
   *
   * @param element The element to serialize.
   * @param indent Current indentation prefix.
   * @return The serialized YAML fragment.
   */
  private fun serializeBlock(element: JsonElement, indent: String): String {
    return when (element) {
      is JsonNull -> "null"
      is JsonPrimitive -> serializePrimitive(element)
      is JsonArray -> {
        if (element.isEmpty()) {
          "[]"
        } else {
          element.joinToString(NL.toString()) { item ->
            if (isNonEmptyContainer(item)) {
              "$indent-$NL${serializeBlock(item, "$indent  ")}"
            } else {
              "$indent- ${serializeBlock(item, "")}"
            }
          }
        }
      }
      is JsonObject -> {
        if (element.isEmpty()) {
          "{}"
        } else {
          element.entries.joinToString(NL.toString()) { (key, value) ->
            val formattedKey = formatKey(key)
            if (isNonEmptyContainer(value)) {
              "$indent$formattedKey:$NL${serializeBlock(value, "$indent  ")}"
            } else {
              "$indent$formattedKey: ${serializeBlock(value, "")}"
            }
          }
        }
      }
    }
  }

  private fun isNonEmptyContainer(element: JsonElement): Boolean {
    return when (element) {
      is JsonObject -> element.isNotEmpty()
      is JsonArray -> element.isNotEmpty()
      else -> false
    }
  }

  /**
   * Serializes a [JsonPrimitive] into a YAML scalar.
   *
   * @param primitive The primitive value.
   * @return The serialized scalar string.
   */
  private fun serializePrimitive(primitive: JsonPrimitive): String {
    if (!primitive.isString) return primitive.content
    return prettyJson.encodeToString(JsonPrimitive.serializer(), primitive)
  }

  /**
   * Formats a mapping key for YAML output, quoting keys that contain special characters or match
   * reserved YAML literals.
   *
   * @param key The map key string.
   * @return The formatted YAML key.
   */
  private fun formatKey(key: String): String {
    val reserved = setOf("true", "false", "null", "yes", "no", "on", "off")
    val isIdentifier = Regex("^[a-zA-Z_-][a-zA-Z0-9_-]*$").matches(key)
    val safeBare = isIdentifier && key.lowercase() !in reserved
    return if (safeBare) {
      key
    } else {
      prettyJson.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(key))
    }
  }

  /**
   * Represents a preprocessed non-empty YAML line with its leading space indentation.
   *
   * @property indent Number of leading spaces.
   * @property text Trimmed line content with trailing comments removed.
   * @property rawText Original line content after leading indentation.
   */
  private data class YamlLine(
      /** Number of leading spaces. */
      val indent: Int,
      /** Trimmed line content with trailing comments removed. */
      val text: String,
      /** Original line content after leading indentation. */
      val rawText: String
  )

  /**
   * Strips empty lines, document markers (`---`, `...`), and full-line comments from raw lines.
   *
   * @param rawLines The input lines from a YAML document.
   * @return Preprocessed [YamlLine] list.
   */
  private fun preprocessLines(rawLines: List<String>): List<YamlLine> {
    val result = mutableListOf<YamlLine>()
    for (raw in rawLines) {
      val indent = raw.takeWhile { it == ' ' }.length
      val afterIndent = raw.substring(indent)
      val stripped = stripInlineComment(afterIndent).trimEnd()
      if (stripped.isEmpty() || stripped == "---" || stripped == "...") continue
      result.add(YamlLine(indent = indent, text = stripped, rawText = afterIndent.trimEnd()))
    }
    return result
  }

  /**
   * Removes unquoted `#` comments from a single YAML line.
   *
   * @param line The line text after indentation.
   * @return The line text without trailing comment.
   */
  private fun stripInlineComment(line: String): String {
    val idx = findUnquotedChar(line, '#') { i -> line.getOrNull(i - 1)?.isWhitespace() != false }
    return if (idx != -1) line.substring(0, idx) else line
  }

  private fun findUnquotedChar(text: String, target: Char, isBoundary: (Int) -> Boolean): Int {
    var activeQuote = Char(0)
    var escaped = false
    for (i in text.indices) {
      val ch = text[i]
      if (escaped) {
        escaped = false
      } else if ((ch == BS) and (activeQuote == DQ)) {
        escaped = true
      } else if (activeQuote != Char(0)) {
        if (ch == activeQuote) activeQuote = Char(0)
      } else if ((ch == SQ) or (ch == DQ)) {
        activeQuote = ch
      } else if ((ch == target) and isBoundary(i)) {
        return i
      }
    }
    return -1
  }

  /**
   * Parses a YAML node starting at [index].
   *
   * @param lines Preprocessed YAML lines.
   * @param index Current line index.
   * @return Pair of parsed [JsonElement] and the next line index.
   */
  private fun parseNode(lines: List<YamlLine>, index: Int): Pair<JsonElement, Int> {
    val line = lines[index]
    return when {
      isSequenceItemText(line.text) -> parseSequence(lines, index, line.indent)
      isMappingLine(line.text) -> parseMapping(lines, index, line.indent)
      else -> parseScalarOrFlow(line.text) to (index + 1)
    }
  }

  private fun isSequenceItemText(text: String): Boolean {
    return (text == "-") or text.startsWith("- ")
  }

  private fun indentAt(lines: List<YamlLine>, index: Int): Int {
    return if (index < lines.size) lines[index].indent else -1
  }

  /**
   * Parses a YAML block sequence (`- ...`) at [seqIndent].
   *
   * @param lines Preprocessed YAML lines.
   * @param startIndex First sequence item line index.
   * @param seqIndent Indentation of the `-` marker.
   * @return Pair of parsed [JsonArray] and the next line index after the sequence.
   */
  private fun parseSequence(
      lines: List<YamlLine>,
      startIndex: Int,
      seqIndent: Int
  ): Pair<JsonArray, Int> {
    val items = mutableListOf<JsonElement>()
    var i = startIndex
    while (i < lines.size) {
      val line = lines[i]
      if ((line.indent != seqIndent) or !isSequenceItemText(line.text)) break
      val rest = line.text.removePrefix("-").trimStart()
      if (rest.isEmpty()) {
        i++
        if (indentAt(lines, i) > seqIndent) {
          val (child, nextIdx) = parseNode(lines, i)
          items.add(child)
          i = nextIdx
        } else {
          items.add(JsonNull)
        }
      } else if (isMappingLine(rest)) {
        val syntheticIndent = line.indent + 2
        val modifiedFirst = YamlLine(indent = syntheticIndent, text = rest, rawText = rest)
        val subLines = mutableListOf(modifiedFirst)
        var j = i + 1
        while (indentAt(lines, j) > seqIndent) {
          subLines.add(lines[j])
          j++
        }
        val (child, _) = parseMapping(subLines, 0, syntheticIndent)
        items.add(child)
        i = j
      } else {
        items.add(parseScalarOrFlow(rest))
        i++
      }
    }
    return JsonArray(items) to i
  }

  /**
   * Parses a YAML block mapping (`key: value`) at [mapIndent].
   *
   * @param lines Preprocessed YAML lines.
   * @param startIndex First mapping entry line index.
   * @param mapIndent Indentation of the mapping keys.
   * @return Pair of parsed [JsonObject] and the next line index after the mapping.
   */
  private fun parseMapping(
      lines: List<YamlLine>,
      startIndex: Int,
      mapIndent: Int
  ): Pair<JsonObject, Int> {
    val map = LinkedHashMap<String, JsonElement>()
    var i = startIndex
    while (i < lines.size) {
      val line = lines[i]
      if ((line.indent != mapIndent) or !isMappingLine(line.text)) break
      val colonIdx = findMappingColon(line.text)
      val rawKey = line.text.substring(0, colonIdx).trim()
      val key = unquoteString(rawKey)
      val rest = line.text.substring(colonIdx + 1).trim()
      if (rest.isEmpty()) {
        i++
        if (indentAt(lines, i) > mapIndent) {
          val (child, nextIdx) = parseNode(lines, i)
          map[key] = child
          i = nextIdx
        } else {
          map[key] = JsonNull
        }
      } else if (rest in setOf("|", "|-", "|+", ">", ">-", ">+")) {
        val fold = rest.startsWith(">")
        val stripTrailing = rest.endsWith("-")
        i++
        val blockLines = mutableListOf<String>()
        val nextIndent = indentAt(lines, i)
        val blockIndent = if (nextIndent > mapIndent) nextIndent else Int.MAX_VALUE
        while (indentAt(lines, i) >= blockIndent) {
          val prefixSpaces = " ".repeat(lines[i].indent - blockIndent)
          blockLines.add(prefixSpaces + lines[i].rawText)
          i++
        }
        val joined =
            if (fold) {
              blockLines.joinToString(" ")
            } else {
              blockLines.joinToString(NL.toString())
            }
        val finalStr = if (stripTrailing) joined else "$joined$NL"
        map[key] = JsonPrimitive(finalStr)
      } else {
        map[key] = parseScalarOrFlow(rest)
        i++
      }
    }
    return JsonObject(map) to i
  }

  /**
   * Checks whether [text] contains an unquoted mapping colon (`:`).
   *
   * @param text Trimmed line content.
   * @return `true` if [text] is a YAML mapping entry.
   */
  private fun isMappingLine(text: String): Boolean {
    if (text.firstOrNull() in setOf('{', '[')) return false
    return findMappingColon(text) != -1
  }

  /**
   * Finds the index of the mapping colon (`:`) in [text], ignoring colons inside quotes.
   *
   * @param text Trimmed line content.
   * @return Index of `:`, or `-1` if not a mapping line.
   */
  private fun findMappingColon(text: String): Int {
    return findUnquotedChar(text, ':') { i -> text.getOrNull(i + 1)?.isWhitespace() != false }
  }

  /**
   * Parses a scalar value or inline flow collection (`{...}` or `[...]`).
   *
   * @param raw Trimmed scalar or flow collection text.
   * @return Corresponding [JsonElement].
   */
  private fun parseScalarOrFlow(raw: String): JsonElement {
    val trimmed = raw.trim()
    if (trimmed in setOf("", "null", "~", "!!null")) return JsonNull
    if (trimmed.startsWith("!!str ")) {
      return JsonPrimitive(unquoteString(trimmed.removePrefix("!!str ").trim()))
    }
    if (trimmed == "{}") return JsonObject(emptyMap())
    if (trimmed == "[]") return JsonArray(emptyList())
    if (trimmed.startsWith("{") and trimmed.endsWith("}")) {
      return parseFlowMapping(trimmed.substring(1, trimmed.length - 1))
    }
    if (trimmed.startsWith("[") and trimmed.endsWith("]")) {
      return parseFlowSequence(trimmed.substring(1, trimmed.length - 1))
    }
    if (isQuoted(trimmed, DQ) or isQuoted(trimmed, SQ)) {
      return JsonPrimitive(unquoteString(trimmed))
    }
    if (trimmed.equals("true", ignoreCase = true)) return JsonPrimitive(true)
    if (trimmed.equals("false", ignoreCase = true)) return JsonPrimitive(false)
    trimmed.toLongOrNull()?.let {
      return JsonPrimitive(it)
    }
    trimmed.toDoubleOrNull()?.let {
      return JsonPrimitive(it)
    }
    return JsonPrimitive(trimmed)
  }

  /**
   * Parses an inline YAML flow mapping (`a: 1, b: 2`).
   *
   * @param inner Content between `{` and `}`.
   * @return Parsed [JsonObject].
   */
  private fun parseFlowMapping(inner: String): JsonObject {
    val parts = splitFlowItems(inner)
    val map = LinkedHashMap<String, JsonElement>()
    for (part in parts) {
      val colonIdx = findMappingColon(part)
      if (colonIdx != -1) {
        val key = unquoteString(part.substring(0, colonIdx).trim())
        val value = parseScalarOrFlow(part.substring(colonIdx + 1).trim())
        map[key] = value
      }
    }
    return JsonObject(map)
  }

  /**
   * Parses an inline YAML flow sequence (`a, b, c`).
   *
   * @param inner Content between `[` and `]`.
   * @return Parsed [JsonArray].
   */
  private fun parseFlowSequence(inner: String): JsonArray {
    val parts = splitFlowItems(inner)
    return JsonArray(parts.map { parseScalarOrFlow(it) })
  }

  /**
   * Splits comma-separated items in a YAML flow collection while respecting nested brackets and
   * quotes.
   *
   * @param inner Raw content inside `{...}` or `[...]`.
   * @return List of trimmed item strings.
   */
  private fun splitFlowItems(inner: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    var activeQuote = Char(0)
    var escaped = false
    for (ch in inner) {
      if (escaped) {
        escaped = false
      } else if ((ch == BS) and (activeQuote == DQ)) {
        escaped = true
      } else if (activeQuote != Char(0)) {
        if (ch == activeQuote) activeQuote = Char(0)
      } else if ((ch == SQ) or (ch == DQ)) {
        activeQuote = ch
      } else if (ch in setOf('{', '[')) {
        depth++
      } else if (ch in setOf('}', ']')) {
        depth--
      } else if ((ch == ',') and (depth == 0)) {
        result.add(current.toString().trim())
        current.clear()
        continue
      }
      current.append(ch)
    }
    val tail = current.toString().trim()
    if (tail.isNotEmpty()) result.add(tail)
    return result
  }

  private fun isQuoted(raw: String, quote: Char): Boolean {
    return (raw.length >= 2) and raw.startsWith(quote) and raw.endsWith(quote)
  }

  /**
   * Unquotes a single-quoted or double-quoted YAML string.
   *
   * @param raw Possibly quoted string.
   * @return Unquoted string content.
   */
  private fun unquoteString(raw: String): String {
    if (isQuoted(raw, DQ)) {
      val parsed = openapi.parseJsonElementOrNull(raw)
      return if (parsed != null) {
        (parsed as JsonPrimitive).content
      } else {
        raw.substring(1, raw.length - 1)
      }
    }
    if (isQuoted(raw, SQ)) {
      return raw.substring(1, raw.length - 1).replace("''", "'")
    }
    return raw
  }
}
