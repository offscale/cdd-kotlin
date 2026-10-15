/** Transport abstractions and standard I/O transport implementation for MCP peers. */
package org.cdd.mcp

import kotlinx.serialization.json.JsonElement

/** Base interface for MCP Transports. */
interface Transport {
  /**
   * Sends a message over the transport.
   *
   * @param message The [JsonElement] payload to send.
   */
  fun send(message: JsonElement)

  /**
   * Sets a listener for received messages.
   *
   * @param listener Callback invoked with each incoming [JsonElement].
   */
  fun onReceive(listener: (JsonElement) -> Unit)

  /**
   * Sets a listener for transport or parsing errors.
   *
   * @param listener Callback invoked with the failure [Exception].
   */
  fun onError(listener: (Exception) -> Unit)

  /**
   * Sets a listener invoked when the transport closes.
   *
   * @param listener Callback invoked on close.
   */
  fun onClose(listener: () -> Unit)

  /** Closes the transport. */
  fun close()
}

/** Standard I/O (stdio) transport interface. */
interface StdioTransport : Transport

/** Server-Sent Events (sse) transport interface. */
interface SseTransport : Transport

/** Custom transport interface. */
interface CustomTransport : Transport

/** Implementation of StdioTransport. */
class StdioTransportImpl : StdioTransport {
  private var receiveListener: ((JsonElement) -> Unit)? = null
  private var errorListener: ((Exception) -> Unit)? = null
  private var closeListener: (() -> Unit)? = null

  override fun send(message: JsonElement) {
    println(JsonRpcParser.serializeMessage(message))
  }

  override fun onReceive(listener: (JsonElement) -> Unit) {
    receiveListener = listener
  }

  override fun onError(listener: (Exception) -> Unit) {
    errorListener = listener
  }

  override fun onClose(listener: () -> Unit) {
    closeListener = listener
  }

  override fun close() {
    closeListener?.invoke()
  }

  /**
   * Exposes a helper for testing to simulate incoming messages.
   *
   * @param message The simulated incoming [JsonElement].
   */
  fun simulateReceive(message: JsonElement) {
    receiveListener?.invoke(message)
  }

  /**
   * Starts reading from standard input (or [lineReader]). Continuously reads lines until EOF
   * (`null`).
   *
   * @param lineReader Function returning the next line of input, or `null` on EOF.
   */
  fun startReading(lineReader: () -> String? = ::readlnOrNull) {
    startReadingResult { kotlin.Result.success(lineReader()) }
  }

  /**
   * Starts reading from a [kotlin.Result]-producing line reader until EOF (`null`) or reader
   * failure.
   *
   * @param lineReader Function returning a [kotlin.Result] with the next line of input, or `null`
   *   on EOF.
   */
  fun startReadingResult(lineReader: () -> kotlin.Result<String?>) {
    while (true) {
      val lineResult = lineReader()
      val shouldBreak =
          lineResult.fold(
              onSuccess = { line ->
                if (line == null) {
                  true
                } else {
                  if (line.isNotBlank()) {
                    JsonRpcParser.parseMessage(line)
                        .fold(
                            onSuccess = { message -> receiveListener?.invoke(message) },
                            onFailure = { err -> errorListener?.invoke(err as Exception) })
                  }
                  false
                }
              },
              onFailure = { err ->
                errorListener?.invoke(err as Exception)
                true
              })
      if (shouldBreak) break
    }
    close()
  }
}
