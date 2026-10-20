/**
 * Strongly-typed JSON and OpenAPI value representations and extension key types.
 *
 * This module eliminates untyped `Any?` and `Map<String, Any?>` usages across the OpenAPI domain
 * models while providing lossless conversion to and from `kotlinx.serialization.json.JsonElement`.
 */
package domain

import kotlin.jvm.JvmInline
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * Sealed hierarchy representing any arbitrary JSON / YAML value embedded within an OpenAPI
 * specification (e.g., `example`, `default`, `const`, `enum` items, `x-*` specification extensions,
 * and custom JSON Schema vocabulary keywords).
 */
sealed interface OpenApiValue {
  /**
   * Converts this [OpenApiValue] into a `kotlinx.serialization.json.JsonElement`.
   *
   * @return The corresponding [JsonElement].
   */
  fun toJsonElement(): JsonElement
}

/**
 * Renders this [OpenApiValue] as a compact string representation suitable for KDoc tags and runtime
 * expression interpolation.
 *
 * @return String value for primitives or compact JSON for arrays/objects.
 */
fun OpenApiValue.toDisplayString(): String =
    when (this) {
      OpenApiNull -> "null"
      is OpenApiBoolean -> value.toString()
      is OpenApiInt -> value.toString()
      is OpenApiNumber -> raw
      is OpenApiString -> value
      is OpenApiArray,
      is OpenApiObject -> OpenApiValues.encodeToString(this)
    }

/**
 * Extracts the string value if this [OpenApiValue] is an [OpenApiString], or `null` otherwise.
 *
 * @return The string content or `null`.
 */
fun OpenApiValue?.stringOrNull(): String? = (this as? OpenApiString)?.value

/**
 * Extracts the boolean value if this [OpenApiValue] is an [OpenApiBoolean], or `null` otherwise.
 *
 * @return The boolean content or `null`.
 */
fun OpenApiValue?.booleanOrNull(): Boolean? = (this as? OpenApiBoolean)?.value

/**
 * Extracts a numeric `Double` value if this [OpenApiValue] is an [OpenApiNumber] or [OpenApiInt],
 * or `null` otherwise.
 *
 * @return The numeric content as a `Double`, or `null`.
 */
fun OpenApiValue?.doubleOrNull(): Double? =
    when (this) {
      is OpenApiNumber -> value
      is OpenApiInt -> value.toDouble()
      else -> null
    }

/**
 * Extracts a `Long` value if this [OpenApiValue] is an [OpenApiInt], or `null` otherwise.
 *
 * @return The integer content as a `Long`, or `null`.
 */
fun OpenApiValue?.longOrNull(): Long? = (this as? OpenApiInt)?.value

/** Represents a JSON `null` literal in an OpenAPI specification. */
data object OpenApiNull : OpenApiValue {
  override fun toJsonElement(): JsonElement = JsonNull
}

/**
 * Represents a JSON boolean value (`true` or `false`) in an OpenAPI specification.
 *
 * @property value The boolean value.
 */
data class OpenApiBoolean(val value: Boolean) : OpenApiValue {
  override fun toJsonElement(): JsonElement = JsonPrimitive(value)
}

/**
 * Represents an exact 64-bit integer value in an OpenAPI specification.
 *
 * @property value The 64-bit integer value.
 */
data class OpenApiInt(val value: Long) : OpenApiValue {
  override fun toJsonElement(): JsonElement = JsonPrimitive(value)
}

/**
 * Represents a floating-point or arbitrary-precision decimal number in an OpenAPI specification.
 *
 * @property value The numeric value as a [Double].
 * @property raw The original unquoted numeric literal string for lossless serialization.
 */
data class OpenApiNumber(val value: Double, val raw: String = value.toString()) : OpenApiValue {
  @OptIn(ExperimentalSerializationApi::class)
  override fun toJsonElement(): JsonElement = JsonUnquotedLiteral(raw)
}

/**
 * Represents a UTF-8 string value in an OpenAPI specification.
 *
 * @property value The string content.
 */
data class OpenApiString(val value: String) : OpenApiValue {
  override fun toJsonElement(): JsonElement = JsonPrimitive(value)
}

/**
 * Represents an ordered JSON array of [OpenApiValue] elements in an OpenAPI specification.
 *
 * @property elements The list of child [OpenApiValue] items.
 */
data class OpenApiArray(val elements: List<OpenApiValue>) : OpenApiValue {
  override fun toJsonElement(): JsonElement = JsonArray(elements.map { it.toJsonElement() })
}

/**
 * Represents an ordered JSON object mapping string keys to [OpenApiValue]s in an OpenAPI
 * specification.
 *
 * @property properties The map of property names to [OpenApiValue]s.
 */
data class OpenApiObject(val properties: Map<String, OpenApiValue>) : OpenApiValue {
  override fun toJsonElement(): JsonElement =
      JsonObject(properties.mapValues { (_, v) -> v.toJsonElement() })
}

/**
 * Strongly-typed OpenAPI specification extension key (guaranteed to start with `"x-"`).
 *
 * @property value The raw extension key string starting with `"x-"`.
 */
@JvmInline
value class ExtensionKey private constructor(val value: String) {
  override fun toString(): String = value

  /** Companion factory methods for validating and constructing [ExtensionKey] instances. */
  companion object {
    /**
     * Parses and validates that [raw] starts with `"x-"`.
     *
     * @param raw Candidate key string.
     * @return A [Result] containing the validated [ExtensionKey], or a
     *   [CddError.OpenApiParseError].
     */
    fun parse(raw: String): Result<ExtensionKey> {
      return if (raw.startsWith("x-")) {
        Result.success(ExtensionKey(raw))
      } else {
        CddError.OpenApiParseError("Specification extension key must start with 'x-': $raw")
            .toFailure()
      }
    }
  }
}

/**
 * Strongly-typed representation of a [Link] `requestBody` value, which may be either a runtime
 * expression string (e.g., `"\$request.body#/id"`) or a literal [OpenApiValue].
 */
sealed interface LinkRequestBody {
  /**
   * A runtime expression string evaluated at link invocation time.
   *
   * @property expression The OAS runtime expression string.
   */
  data class Expression(val expression: String) : LinkRequestBody

  /**
   * A literal JSON value passed as the link request body.
   *
   * @property value The literal [OpenApiValue].
   */
  data class Literal(val value: OpenApiValue) : LinkRequestBody

  /** Companion factory methods for [LinkRequestBody]. */
  companion object {
    /**
     * Classifies an [OpenApiValue] into either a [LinkRequestBody.Expression] (if it is a string
     * starting with `'$'` or containing `"{$"`) or a [LinkRequestBody.Literal].
     *
     * @param value The raw [OpenApiValue].
     * @return The classified [LinkRequestBody].
     */
    fun fromOpenApiValue(value: OpenApiValue): LinkRequestBody {
      if (value is OpenApiString && (value.value.startsWith("\$") || value.value.contains("{\$"))) {
        return Expression(value.value)
      }
      return Literal(value)
    }
  }
}

/**
 * Converts this [LinkRequestBody] to its underlying [OpenApiValue].
 *
 * @return The [OpenApiValue] representation.
 */
fun LinkRequestBody.toOpenApiValue(): OpenApiValue =
    when (this) {
      is LinkRequestBody.Expression -> OpenApiString(expression)
      is LinkRequestBody.Literal -> value
    }

/** Conversion utilities between [JsonElement] and [OpenApiValue]. */
object OpenApiValues {
  private val compactJson = Json { prettyPrint = false }

  /**
   * Converts a `kotlinx.serialization.json.JsonElement` into a strongly-typed [OpenApiValue].
   *
   * @param element The [JsonElement] to convert.
   * @return The corresponding [OpenApiValue].
   */
  fun fromJsonElement(element: JsonElement): OpenApiValue {
    return when (element) {
      is JsonNull -> OpenApiNull
      is JsonPrimitive -> {
        if (element.isString) {
          OpenApiString(element.content)
        } else {
          val raw = element.content
          when (raw) {
            "true" -> OpenApiBoolean(true)
            "false" -> OpenApiBoolean(false)
            else -> {
              val longVal = raw.toLongOrNull()
              if (longVal != null) {
                OpenApiInt(longVal)
              } else {
                val doubleVal = raw.toDoubleOrNull()
                if (doubleVal != null) {
                  OpenApiNumber(doubleVal, raw)
                } else {
                  OpenApiString(raw)
                }
              }
            }
          }
        }
      }
      is JsonArray -> OpenApiArray(element.map { fromJsonElement(it) })
      is JsonObject -> OpenApiObject(element.mapValues { (_, v) -> fromJsonElement(v) })
    }
  }

  /**
   * Encodes an [OpenApiValue] to a compact JSON string.
   *
   * @param value The [OpenApiValue] to encode.
   * @return Compact JSON string.
   */
  fun encodeToString(value: OpenApiValue): String =
      compactJson.encodeToString(JsonElement.serializer(), value.toJsonElement())

  /**
   * Encodes a [JsonElement] to a compact JSON string.
   *
   * @param element The [JsonElement] to encode.
   * @return Compact JSON string.
   */
  fun encodeJsonElementToString(element: JsonElement): String =
      compactJson.encodeToString(JsonElement.serializer(), element)

  /**
   * Decodes a JSON string into a strongly-typed [OpenApiValue] wrapped in a [Result].
   *
   * @param jsonStr The JSON string to parse.
   * @return A [Result] containing the decoded [OpenApiValue] or a [CddError.JsonParseError].
   */
  fun decodeFromStringResult(jsonStr: String): Result<OpenApiValue> =
      openapi.parseJsonElementResult(jsonStr).map { fromJsonElement(it) }

  /**
   * Decodes a JSON string into a strongly-typed [OpenApiValue], or `null` if parsing fails.
   *
   * @param jsonStr The JSON string to parse.
   * @return The decoded [OpenApiValue], or `null` if invalid JSON.
   */
  fun decodeFromStringOrNull(jsonStr: String): OpenApiValue? =
      decodeFromStringResult(jsonStr).getOrNull()
}
