package openapi

import kotlinx.serialization.json.*

typealias JsonNode = JsonElement

val JsonNode.isObject: Boolean
  get() = this is JsonObject
val JsonNode.isArray: Boolean
  get() = this is JsonArray
val JsonNode.isTextual: Boolean
  get() = this is JsonPrimitive && this.isString
val JsonNode.isBoolean: Boolean
  get() = this is JsonPrimitive && this.booleanOrNull != null
val JsonNode.isNumber: Boolean
  get() = this is JsonPrimitive && (!this.isString && this.doubleOrNull != null)
val JsonNode.isNullNode: Boolean
  get() = this is JsonNull

/** Documented asText. */
fun JsonNode.asText(defaultValue: String? = null): String? =
    if (this is JsonPrimitive) this.contentOrNull ?: defaultValue else defaultValue

/** Documented asInt. */
fun JsonNode.asInt(defaultValue: Int = 0): Int =
    if (this is JsonPrimitive) this.intOrNull ?: defaultValue else defaultValue

/** Documented asDouble. */
fun JsonNode.asDouble(defaultValue: Double = 0.0): Double =
    if (this is JsonPrimitive) this.doubleOrNull ?: defaultValue else defaultValue

/** Documented asBoolean. */
fun JsonNode.asBoolean(defaultValue: Boolean = false): Boolean =
    if (this is JsonPrimitive) this.booleanOrNull ?: defaultValue else defaultValue

/** Documented booleanValue. */
fun JsonNode.booleanValue(): Boolean = this.asBoolean()

/** Documented textValue. */
fun JsonNode.textValue(): String? = this.asText()

/** Documented numberValue. */
fun JsonNode.numberValue(): Number? =
    if (this is JsonPrimitive) this.intOrNull ?: this.longOrNull ?: this.doubleOrNull else null

/** Documented intValue. */
fun JsonNode.intValue(): Int = this.asInt()

/** Documented doubleValue. */
fun JsonNode.doubleValue(): Double = this.asDouble()

/** Documented get. */
fun JsonNode.get(key: String): JsonNode? = if (this is JsonObject) this[key] else null

/** Documented get. */
fun JsonNode.get(index: Int): JsonNode? = if (this is JsonArray) this[index] else null

/** Documented has. */
fun JsonNode.has(key: String): Boolean = if (this is JsonObject) this.containsKey(key) else false

/** Documented fields. */
fun JsonNode.fields(): List<Map.Entry<String, JsonNode>> =
    if (this is JsonObject) this.entries.toList() else emptyList<Map.Entry<String, JsonNode>>()

/** Documented elements. */
fun JsonNode.elements(): List<JsonNode> = if (this is JsonArray) this else emptyList<JsonNode>()

/** Documented size. */
fun JsonNode.size(): Int =
    if (this is JsonObject) this.size else if (this is JsonArray) this.size else 0
