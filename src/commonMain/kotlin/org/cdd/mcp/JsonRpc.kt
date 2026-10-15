/** JSON-RPC 2.0 error codes and message parsing/formatting utilities for MCP. */
package org.cdd.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import openapi.parseJsonElementResult

/** Standard JSON-RPC 2.0 error codes. */
object JsonRpcErrorCodes {
  /** Parse error */
  const val PARSE_ERROR = -32700
  /** Invalid request */
  const val INVALID_REQUEST = -32600
  /** Method not found */
  const val METHOD_NOT_FOUND = -32601
  /** Invalid params */
  const val INVALID_PARAMS = -32602
  /** Internal error */
  const val INTERNAL_ERROR = -32603
}

/**
 * Non-thrown [Exception] carrier representing a JSON-RPC 2.0 error for use inside [kotlin.Result].
 *
 * @property code The JSON-RPC 2.0 error code.
 * @property message The human-readable error message.
 * @property data Optional structured error data.
 */
class JsonRpcException(
    /** The error code */
    val code: Int,
    /** The error message */
    override val message: String,
    /** The error data */
    val data: JsonElement? = null
) : RuntimeException(message)

/** Message parsing and serialization utility. */
object JsonRpcParser {
  /** The JSON configuration */
  val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  /**
   * Parses a message string into a [JsonElement] wrapped in a [kotlin.Result].
   *
   * @param string Raw JSON-RPC message string.
   * @return A [kotlin.Result] containing the parsed [JsonElement] or a [JsonRpcException] failure.
   */
  fun parseMessage(string: String): kotlin.Result<JsonElement> {
    return parseJsonElementResult(string)
        .fold(
            onSuccess = { kotlin.Result.success(it) },
            onFailure = {
              kotlin.Result.failure(JsonRpcException(JsonRpcErrorCodes.PARSE_ERROR, "Parse error"))
            })
  }

  /**
   * Serializes a [JsonElement] into a JSON string.
   *
   * @param element The [JsonElement] to serialize.
   * @return Compact JSON string.
   */
  fun serializeMessage(element: JsonElement): String {
    return json.encodeToString(JsonElement.serializer(), element)
  }
}
