/**
 * Strongly-typed JSON and OpenAPI value representations and extension key types.
 *
 * This module eliminates untyped `Any?` and `Map<String, Any?>` usages across the OpenAPI domain
 * models while providing lossless conversion to and from `kotlinx.serialization.json.JsonElement`
 * and standard Kotlin collections/primitives.
 */
package domain

import kotlin.jvm.JvmInline
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Strongly-typed wrapper for OpenAPI specification extension keys (must start with `x-`).
 *
 * @property value The raw extension key string starting with `x-`.
 */
@JvmInline
value class ExtensionKey(val value: String) {
  /**
   * Returns the raw string representation of the extension key.
   *
   * @return The underlying key string.
   */
  override fun toString(): String = value

  /** Companion factory methods for validating [ExtensionKey] values without exceptions. */
  companion object {
    /**
     * Validates that [value] starts with `x-` and returns a [Result] wrapping [ExtensionKey].
     *
     * @param value The raw extension key string to validate.
     * @return A [Result] containing [ExtensionKey] or a [CddError.OpenApiParseError] failure.
     */
    fun parse(value: String): Result<ExtensionKey> =
        if (value.startsWith("x-")) {
          Result.success(ExtensionKey(value))
        } else {
          CddError.OpenApiParseError(
                  "OpenAPI specification extension key must start with 'x-': $value")
              .toFailure()
        }
  }
}

/** Typealias for a map of OpenAPI specification extensions (`x-*` -> [OpenApiValue]). */
typealias SpecificationExtensions = Map<String, OpenApiValue>

/**
 * Strongly-typed sealed hierarchy representing any JSON / OpenAPI literal, array, or object value.
 */
sealed interface OpenApiValue {
  /**
   * Converts this [OpenApiValue] into a [JsonElement].
   *
   * @return The corresponding [JsonElement].
   */
  fun toJsonElement(): JsonElement

  /**
   * Converts this [OpenApiValue] into standard Kotlin primitives, lists, and maps (`Any?`) for
   * interoperability with legacy serializers.
   *
   * @return The unwrapped Kotlin value (`null`, `Boolean`, `Long`, `Double`, `String`, `List`, or
   *   `Map`).
   */
  fun toUntyped(): Any?
}

/**
 * Unwraps this nullable [OpenApiValue] to its raw Kotlin value (`null` when receiver is `null` or
 * [OpenApiNull]).
 *
 * @return The unwrapped Kotlin value or `null`.
 */
fun OpenApiValue?.unwrapOrNull(): Any? = this?.toUntyped()

/**
 * Returns the string content if this [OpenApiValue] is an [OpenApiString], or `null` otherwise.
 *
 * @return The string value or `null`.
 */
fun OpenApiValue?.stringOrNull(): String? = (this as? OpenApiString)?.value

/**
 * Returns the boolean value if this [OpenApiValue] is an [OpenApiBoolean], or `null` otherwise.
 *
 * @return The boolean value or `null`.
 */
fun OpenApiValue?.booleanOrNull(): Boolean? = (this as? OpenApiBoolean)?.value

/**
 * Returns the numeric double value if this [OpenApiValue] is an [OpenApiInt] or [OpenApiNumber], or
 * `null` otherwise.
 *
 * @return The numeric value as a `Double`, or `null`.
 */
fun OpenApiValue?.doubleOrNull(): Double? =
    when (this) {
      is OpenApiInt -> value.toDouble()
      is OpenApiNumber -> value
      else -> null
    }

/**
 * Returns the integer long value if this [OpenApiValue] is an [OpenApiInt], or `null` otherwise.
 *
 * @return The integer value as a `Long`, or `null`.
 */
fun OpenApiValue?.longOrNull(): Long? = (this as? OpenApiInt)?.value

/** Represents a JSON `null` value in an OpenAPI document. */
data object OpenApiNull : OpenApiValue {
  override fun toJsonElement(): JsonElement = JsonNull

  override fun toUntyped(): Any? = null
}

/**
 * Represents a JSON boolean value in an OpenAPI document.
 *
 * @property value The boolean value.
 */
data class OpenApiBoolean(val value: Boolean) : OpenApiValue {
  override fun toJsonElement(): JsonElement = JsonPrimitive(value)

  override fun toUntyped(): Any = value
}

/**
 * Represents a JSON integer (64-bit signed) value in an OpenAPI document.
 *
 * @property value The integer value.
 */
data class OpenApiInt(val value: Long) : OpenApiValue {
  override fun toJsonElement(): JsonElement = JsonPrimitive(value)

  override fun toUntyped(): Any =
      if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else value
}

/**
 * Represents a JSON floating-point number value in an OpenAPI document.
 *
 * @property value The double-precision numeric value.
 * @property raw Optional raw string representation to preserve exact formatting.
 */
data class OpenApiNumber(val value: Double, val raw: String = value.toString()) : OpenApiValue {
  override fun toJsonElement(): JsonElement = JsonPrimitive(value)

  override fun toUntyped(): Any = value
}

/**
 * Represents a JSON string value in an OpenAPI document.
 *
 * @property value The string content.
 */
data class OpenApiString(val value: String) : OpenApiValue {
  override fun toJsonElement(): JsonElement = JsonPrimitive(value)

  override fun toUntyped(): Any = value
}

/**
 * Represents a JSON array value in an OpenAPI document.
 *
 * @property elements The ordered list of [OpenApiValue] elements.
 */
data class OpenApiArray(val elements: List<OpenApiValue>) : OpenApiValue {
  override fun toJsonElement(): JsonElement = JsonArray(elements.map { it.toJsonElement() })

  override fun toUntyped(): Any = elements.map { it.toUntyped() }
}

/**
 * Represents a JSON object value in an OpenAPI document.
 *
 * @property properties The map of property names to [OpenApiValue] instances.
 */
data class OpenApiObject(val properties: Map<String, OpenApiValue>) : OpenApiValue {
  override fun toJsonElement(): JsonElement =
      JsonObject(properties.mapValues { (_, v) -> v.toJsonElement() })

  override fun toUntyped(): Any = properties.mapValues { (_, v) -> v.toUntyped() }
}

/**
 * Represents a Link Object's `requestBody`, which can be either a runtime expression or a literal
 * [OpenApiValue].
 */
sealed interface LinkRequestBody {
  /**
   * A runtime expression string (e.g. `$request.body#/id`).
   *
   * @property runtimeExpression The OAS runtime expression string.
   */
  data class Expression(val runtimeExpression: String) : LinkRequestBody

  /**
   * A literal JSON value to send as the request body.
   *
   * @property value The literal [OpenApiValue].
   */
  data class Literal(val value: OpenApiValue) : LinkRequestBody
}

/**
 * Utility object for converting between [JsonElement], untyped Kotlin objects, and [OpenApiValue].
 */
object OpenApiValues {
  /**
   * Converts a [JsonElement] into a strongly-typed [OpenApiValue].
   *
   * @param element The [JsonElement] to convert.
   * @return The corresponding [OpenApiValue].
   */
  fun fromJsonElement(element: JsonElement): OpenApiValue =
      when (element) {
        is JsonNull -> OpenApiNull
        is JsonPrimitive -> {
          if (element.isString) {
            OpenApiString(element.content)
          } else {
            val boolVal = element.booleanOrNull
            if (boolVal != null) {
              OpenApiBoolean(boolVal)
            } else {
              val longVal = element.longOrNull
              if (longVal != null && !element.content.contains('e', ignoreCase = true)) {
                OpenApiInt(longVal)
              } else {
                val doubleVal = element.doubleOrNull
                if (doubleVal != null) {
                  OpenApiNumber(doubleVal, element.content)
                } else {
                  OpenApiString(element.content)
                }
              }
            }
          }
        }
        is JsonArray -> OpenApiArray(element.map { fromJsonElement(it) })
        is JsonObject -> OpenApiObject(element.mapValues { (_, v) -> fromJsonElement(v) })
      }

  /**
   * Converts an arbitrary Kotlin value into a strongly-typed [OpenApiValue].
   *
   * @param value The Kotlin value to convert.
   * @return The corresponding [OpenApiValue].
   */
  fun fromAny(value: Any?): OpenApiValue =
      when (value) {
        null -> OpenApiNull
        is OpenApiValue -> value
        is JsonElement -> fromJsonElement(value)
        is Boolean -> OpenApiBoolean(value)
        is Int -> OpenApiInt(value.toLong())
        is Long -> OpenApiInt(value)
        is Short -> OpenApiInt(value.toLong())
        is Byte -> OpenApiInt(value.toLong())
        is Float -> OpenApiNumber(value.toDouble(), value.toString())
        is Double -> OpenApiNumber(value, value.toString())
        is Number -> OpenApiNumber(value.toDouble(), value.toString())
        is String -> OpenApiString(value)
        is Iterable<*> -> OpenApiArray(value.map { fromAny(it) })
        is Array<*> -> OpenApiArray(value.map { fromAny(it) })
        is Map<*, *> ->
            OpenApiObject(value.entries.associate { (k, v) -> k.toString() to fromAny(v) })
        else -> OpenApiString(value.toString())
      }

  /**
   * Converts a map with untyped values into a map of [OpenApiValue]s.
   *
   * @param map The input map.
   * @return A new map with [OpenApiValue] values.
   */
  fun fromMap(map: Map<String, Any?>): Map<String, OpenApiValue> =
      map.mapValues { (_, v) -> fromAny(v) }

  /**
   * Converts a map of [OpenApiValue]s back into an untyped map for legacy interoperability.
   *
   * @param map The input map of [OpenApiValue]s.
   * @return A map with unwrapped Kotlin values.
   */
  fun toUntypedMap(map: Map<String, OpenApiValue>): Map<String, Any?> =
      map.mapValues { (_, v) -> v.toUntyped() }

  /**
   * Encodes an arbitrary Kotlin value (`Any?`) into a compact JSON string via [OpenApiValue] and
   * `kotlinx.serialization.json.Json`.
   *
   * @param value The Kotlin value to encode.
   * @return Compact JSON string representation.
   */
  fun encodeAnyToString(value: Any?): String =
      kotlinx.serialization.json.Json.encodeToString(
          JsonElement.serializer(), fromAny(value).toJsonElement())

  /**
   * Decodes a JSON string into an unwrapped Kotlin value (`null`, `Boolean`, `Int`, `Long`,
   * `Double`, `String`, `List`, or `Map`), or `null` if invalid JSON.
   *
   * @param jsonStr The JSON string to parse.
   * @return The decoded Kotlin value.
   */
  fun decodeStringToAny(jsonStr: String): Any? =
      openapi.parseJsonElementOrNull(jsonStr)?.let { fromJsonElement(it).toUntyped() }

  /**
   * Decodes a JSON string into an unwrapped Kotlin value wrapped in a [Result].
   *
   * @param jsonStr The JSON string to parse.
   * @return A [Result] containing the decoded Kotlin value or a [CddError.JsonParseError].
   */
  fun decodeStringToAnyResult(jsonStr: String): Result<Any?> =
      openapi.parseJsonElementResult(jsonStr).map { fromJsonElement(it).toUntyped() }
}
