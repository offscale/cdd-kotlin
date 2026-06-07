package org.cdd.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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

/** Exception for JSON-RPC errors. */
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

  /** Parses a message string into a JsonElement. */
  fun parseMessage(string: String): JsonElement {
    return try {
      json.parseToJsonElement(string)
    } catch (e: Exception) {
      throw JsonRpcException(JsonRpcErrorCodes.PARSE_ERROR, "Parse error")
    }
  }

  /** Serializes a JsonElement into a string. */
  fun serializeMessage(element: JsonElement): String {
    return json.encodeToString(JsonElement.serializer(), element)
  }
}
