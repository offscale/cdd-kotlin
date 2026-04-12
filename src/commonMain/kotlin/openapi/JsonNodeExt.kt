package openapi

import kotlinx.serialization.json.*

typealias JsonNode = JsonElement

val JsonNode.isObject: Boolean get() = this is JsonObject
val JsonNode.isArray: Boolean get() = this is JsonArray
val JsonNode.isTextual: Boolean get() = this is JsonPrimitive && this.isString
val JsonNode.isBoolean: Boolean get() = this is JsonPrimitive && this.booleanOrNull != null
val JsonNode.isNumber: Boolean get() = this is JsonPrimitive && (this.intOrNull != null || this.doubleOrNull != null || this.longOrNull != null || this.floatOrNull != null)
val JsonNode.isNullNode: Boolean get() = this is JsonNull

fun JsonNode.asText(defaultValue: String? = null): String? = if (this is JsonPrimitive) this.contentOrNull ?: defaultValue else defaultValue
fun JsonNode.asInt(defaultValue: Int = 0): Int = if (this is JsonPrimitive) this.intOrNull ?: defaultValue else defaultValue
fun JsonNode.asDouble(defaultValue: Double = 0.0): Double = if (this is JsonPrimitive) this.doubleOrNull ?: defaultValue else defaultValue
fun JsonNode.asBoolean(defaultValue: Boolean = false): Boolean = if (this is JsonPrimitive) this.booleanOrNull ?: defaultValue else defaultValue
fun JsonNode.booleanValue(): Boolean = this.asBoolean()
fun JsonNode.textValue(): String? = this.asText()
fun JsonNode.numberValue(): Number? = if (this is JsonPrimitive) this.doubleOrNull else null
fun JsonNode.intValue(): Int = this.asInt()
fun JsonNode.doubleValue(): Double = this.asDouble()

fun JsonNode.get(key: String): JsonNode? = if (this is JsonObject) this[key] else null
fun JsonNode.get(index: Int): JsonNode? = if (this is JsonArray) this[index] else null
fun JsonNode.has(key: String): Boolean = if (this is JsonObject) this.containsKey(key) else false

fun JsonNode.fields(): List<Map.Entry<String, JsonNode>> = if (this is JsonObject) this.entries.toList() else emptyList<Map.Entry<String, JsonNode>>()
fun JsonNode.elements(): List<JsonNode> = if (this is JsonArray) this else emptyList<JsonNode>()
fun JsonNode.size(): Int = if (this is JsonObject) this.size else if (this is JsonArray) this.size else 0
