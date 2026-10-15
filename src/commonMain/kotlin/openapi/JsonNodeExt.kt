/**
 * Unified JSON AST traversal extensions over `kotlinx.serialization.json.JsonElement`.
 *
 * Provides a consistent, strongly-typed tree navigation API across `commonMain`, `jvmMain`, and
 * `wasmMain`.
 */
package openapi

import kotlinx.serialization.json.*

/** Multiplatform alias for [JsonElement] representing any JSON AST node. */
typealias JsonNode = JsonElement

/** Returns `true` if this [JsonNode] is a [JsonObject]. */
val JsonNode.isObject: Boolean
  get() = this is JsonObject

/** Returns `true` if this [JsonNode] is a [JsonArray]. */
val JsonNode.isArray: Boolean
  get() = this is JsonArray

/** Returns `true` if this [JsonNode] is a string [JsonPrimitive]. */
val JsonNode.isTextual: Boolean
  get() = this is JsonPrimitive && this.isString

/** Returns `true` if this [JsonNode] is a boolean [JsonPrimitive]. */
val JsonNode.isBoolean: Boolean
  get() = this is JsonPrimitive && !this.isString && this.booleanOrNull != null

/** Returns `true` if this [JsonNode] is a numeric [JsonPrimitive]. */
val JsonNode.isNumber: Boolean
  get() = this is JsonPrimitive && (!this.isString && this.doubleOrNull != null)

/** Returns `true` if this [JsonNode] is [JsonNull]. */
val JsonNode.isNullNode: Boolean
  get() = this is JsonNull

/**
 * Extracts the text content of a [JsonPrimitive], or returns [defaultValue] if not a primitive or
 * null.
 *
 * @param defaultValue Fallback string if this node is not a non-null primitive.
 * @return The string content or [defaultValue].
 */
fun JsonNode.asText(defaultValue: String? = null): String? =
    if (this is JsonPrimitive) this.contentOrNull ?: defaultValue else defaultValue

/**
 * Extracts the integer value of a [JsonPrimitive], or returns [defaultValue] if not an integer.
 *
 * @param defaultValue Fallback integer value.
 * @return The integer value or [defaultValue].
 */
fun JsonNode.asInt(defaultValue: Int = 0): Int =
    if (this is JsonPrimitive) this.intOrNull ?: defaultValue else defaultValue

/**
 * Extracts the double value of a [JsonPrimitive], or returns [defaultValue] if not a number.
 *
 * @param defaultValue Fallback double value.
 * @return The double value or [defaultValue].
 */
fun JsonNode.asDouble(defaultValue: Double = 0.0): Double =
    if (this is JsonPrimitive) this.doubleOrNull ?: defaultValue else defaultValue

/**
 * Extracts the boolean value of a [JsonPrimitive], or returns [defaultValue] if not a boolean.
 *
 * @param defaultValue Fallback boolean value.
 * @return The boolean value or [defaultValue].
 */
fun JsonNode.asBoolean(defaultValue: Boolean = false): Boolean =
    if (this is JsonPrimitive) this.booleanOrNull ?: defaultValue else defaultValue

/**
 * Returns the boolean value of this node (defaults to `false`).
 *
 * @return Boolean representation of this node.
 */
fun JsonNode.booleanValue(): Boolean = this.asBoolean()

/**
 * Returns the text content of this node, or `null` if not a primitive.
 *
 * @return Text content or `null`.
 */
fun JsonNode.textValue(): String? = this.asText()

/**
 * Returns the integer value of this node (defaults to `0`).
 *
 * @return Integer representation of this node.
 */
fun JsonNode.intValue(): Int = this.asInt()

/**
 * Returns the double value of this node (defaults to `0.0`).
 *
 * @return Double representation of this node.
 */
fun JsonNode.doubleValue(): Double = this.asDouble()

/**
 * Looks up a child property by [key] when this node is a [JsonObject].
 *
 * @param key The object property name.
 * @return The child [JsonNode], or `null` if absent or not an object.
 */
fun JsonNode.get(key: String): JsonNode? = if (this is JsonObject) this[key] else null

/**
 * Looks up an element by [index] when this node is a [JsonArray].
 *
 * @param index The 0-based array index.
 * @return The element [JsonNode], or `null` if out of bounds or not an array.
 */
fun JsonNode.get(index: Int): JsonNode? =
    if (this is JsonArray && index in 0 until this.size) this[index] else null

/**
 * Checks whether this [JsonObject] contains [key].
 *
 * @param key The property name to check.
 * @return `true` if this node is a [JsonObject] containing [key].
 */
fun JsonNode.has(key: String): Boolean = if (this is JsonObject) this.containsKey(key) else false

/**
 * Returns the list of key-value entries when this node is a [JsonObject], or an empty list.
 *
 * @return List of map entries.
 */
fun JsonNode.fields(): List<Map.Entry<String, JsonNode>> =
    if (this is JsonObject) this.entries.toList() else emptyList()

/**
 * Returns the list of elements when this node is a [JsonArray], or an empty list.
 *
 * @return List of array element [JsonNode]s.
 */
fun JsonNode.elements(): List<JsonNode> = if (this is JsonArray) this else emptyList()

/**
 * Returns the number of properties or elements in this node, or `0` for primitives/null.
 *
 * @return Size of the object or array.
 */
fun JsonNode.size(): Int =
    if (this is JsonObject) this.size else if (this is JsonArray) this.size else 0

/**
 * Returns this [JsonNode] if it is a [JsonObject], or `null` otherwise.
 *
 * @return This [JsonNode] when it is an object, or `null`.
 */
fun JsonNode?.asObject(): JsonNode? = if (this != null && this.isObject) this else null

/**
 * Returns the elements of this [JsonNode] if it is a [JsonArray], or `null` otherwise.
 *
 * @return List of element [JsonNode]s, or `null`.
 */
fun JsonNode?.asArray(): List<JsonNode>? =
    if (this != null && this.isArray) this.elements() else null

/**
 * Looks up a string property named [field] on this [JsonObject].
 *
 * @param field The property name.
 * @return The string value if [field] exists and is non-null, or `null`.
 */
fun JsonNode.text(field: String): String? = this.get(field)?.takeIf { !it.isNullNode }?.asText()

/**
 * Looks up a boolean property named [field] on this [JsonObject].
 *
 * @param field The property name.
 * @return The boolean value if [field] exists and is a boolean, or `null`.
 */
fun JsonNode.boolean(field: String): Boolean? =
    this.get(field)?.takeIf { it.isBoolean }?.booleanValue()

/**
 * Looks up an integer property named [field] on this [JsonObject].
 *
 * @param field The property name.
 * @return The integer value if [field] exists and is a number, or `null`.
 */
fun JsonNode.int(field: String): Int? = this.get(field)?.takeIf { it.isNumber }?.intValue()

/**
 * Looks up a double property named [field] on this [JsonObject].
 *
 * @param field The property name.
 * @return The double value if [field] exists and is a number, or `null`.
 */
fun JsonNode.double(field: String): Double? = this.get(field)?.takeIf { it.isNumber }?.doubleValue()

/**
 * Parses a JSON string into a [JsonElement] wrapped in a [Result] without throwing exceptions.
 *
 * @param source The raw JSON string to parse.
 * @return A [Result] containing the parsed [JsonElement] or a [domain.CddError.JsonParseError].
 */
fun parseJsonElementResult(source: String): Result<JsonElement> {
  val parser = SafeJsonReader(source)
  val element =
      parser.parseRoot()
          ?: return domain.CddError.JsonParseError("Invalid JSON syntax").let {
            Result.failure(domain.CddException(it))
          }
  return Result.success(element)
}

/**
 * Parses a JSON string into a [JsonElement], or returns `null` if [source] is not valid JSON.
 *
 * @param source The raw JSON string to parse.
 * @return The parsed [JsonElement], or `null` if malformed.
 */
fun parseJsonElementOrNull(source: String): JsonElement? =
    parseJsonElementResult(source).getOrNull()

private class SafeJsonReader(private val input: String) {
  private var pos: Int = 0

  /**
   * Parses the root JSON value and ensures no trailing non-whitespace tokens remain.
   *
   * @return The parsed [JsonElement], or `null` if syntax is invalid.
   */
  fun parseRoot(): JsonElement? {
    skipWhitespace()
    val value = parseValue() ?: return null
    skipWhitespace()
    return if (pos == input.length) value else null
  }

  private fun skipWhitespace() {
    while (pos < input.length && input[pos].isWhitespace()) {
      pos++
    }
  }

  private fun parseValue(): JsonElement? {
    skipWhitespace()
    if (pos >= input.length) return null
    return when (input[pos]) {
      '{' -> parseObject()
      '[' -> parseArray()
      '"' -> parseString()?.let { JsonPrimitive(it) }
      't' -> parseLiteral("true", JsonPrimitive(true))
      'f' -> parseLiteral("false", JsonPrimitive(false))
      'n' -> parseLiteral("null", JsonNull)
      '-',
      in '0'..'9' -> parseNumber()
      else -> null
    }
  }

  private fun parseLiteral(expected: String, result: JsonElement): JsonElement? {
    if (!input.startsWith(expected, pos)) return null
    pos += expected.length
    return result
  }

  private fun parseObject(): JsonObject? {
    pos++ // '{'
    skipWhitespace()
    if (pos < input.length && input[pos] == '}') {
      pos++
      return JsonObject(emptyMap())
    }
    val map = LinkedHashMap<String, JsonElement>()
    while (pos < input.length) {
      if (input[pos] != '"') return null
      val key = parseString() ?: return null
      skipWhitespace()
      if (pos >= input.length || input[pos] != ':') return null
      pos++ // ':'
      val value = parseValue() ?: return null
      map[key] = value
      skipWhitespace()
      if (pos >= input.length) return null
      when (input[pos]) {
        ',' -> {
          pos++
          skipWhitespace()
        }
        '}' -> {
          pos++
          return JsonObject(map)
        }
        else -> return null
      }
    }
    return null
  }

  private fun parseArray(): JsonArray? {
    pos++ // '['
    skipWhitespace()
    if (pos < input.length && input[pos] == ']') {
      pos++
      return JsonArray(emptyList())
    }
    val list = ArrayList<JsonElement>()
    while (pos < input.length) {
      val item = parseValue() ?: return null
      list.add(item)
      skipWhitespace()
      if (pos >= input.length) return null
      when (input[pos]) {
        ',' -> pos++
        ']' -> {
          pos++
          return JsonArray(list)
        }
        else -> return null
      }
    }
    return null
  }

  private fun parseString(): String? {
    pos++ // '"'
    val sb = StringBuilder()
    while (pos < input.length) {
      val ch = input[pos++]
      if (ch == '"') return sb.toString()
      if (ch == '\\') {
        if (pos >= input.length) return null
        when (val esc = input[pos++]) {
          '"',
          '\\',
          '/' -> sb.append(esc)
          'b' -> sb.append('\b')
          'f' -> sb.append('\u000C')
          'n' -> sb.append('\n')
          'r' -> sb.append('\r')
          't' -> sb.append('\t')
          'u' -> {
            if (pos + 4 > input.length) return null
            var code = 0
            for (i in 0 until 4) {
              val d = hexToInt(input[pos++])
              if (d < 0) return null
              code = (code shl 4) or d
            }
            sb.append(code.toChar())
          }
          else -> return null
        }
      } else {
        sb.append(ch)
      }
    }
    return null
  }

  @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
  private fun parseNumber(): JsonPrimitive? {
    val start = pos
    if (input[pos] == '-') pos++
    if (!scanDigits()) return null
    var isFloat = false
    if (pos < input.length && input[pos] == '.') {
      isFloat = true
      pos++
      if (!scanDigits()) return null
    }
    if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
      isFloat = true
      pos++
      if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
      if (!scanDigits()) return null
    }
    val raw = input.substring(start, pos)
    if (!isFloat) {
      val longVal = raw.toLongOrNull()
      if (longVal != null) return JsonPrimitive(longVal)
    }
    return if (raw.contains('e', ignoreCase = true)) {
      JsonUnquotedLiteral(raw)
    } else {
      JsonPrimitive(raw.toDouble())
    }
  }

  private fun scanDigits(): Boolean {
    val digitStart = pos
    while (pos < input.length && input[pos] in '0'..'9') {
      pos++
    }
    return pos > digitStart
  }
}
