package org.cdd.mcp

import kotlinx.serialization.json.JsonElement

/** Base interface for MCP Transports. */
interface Transport {
  /** Sends a message. */
  fun send(message: JsonElement)
  /** Sets a listener for received messages. */
  fun onReceive(listener: (JsonElement) -> Unit)
  /** Sets a listener for errors. */
  fun onError(listener: (Exception) -> Unit)
  /** Sets a listener for closing. */
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
    try {
      println(JsonRpcParser.serializeMessage(message))
    } catch (e: Exception) {
      errorListener?.invoke(e)
    }
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

  /** Expose method for testing to simulate incoming messages. */
  fun simulateReceive(message: JsonElement) {
    receiveListener?.invoke(message)
  }

  /**
   * Starts reading from standard input. This is a blocking call that will continuously read lines
   * until EOF.
   */
  fun startReading() {
    try {
      while (true) {
        val line = readlnOrNull() ?: break
        if (line.isBlank()) continue
        try {
          val message = JsonRpcParser.parseMessage(line)
          receiveListener?.invoke(message)
        } catch (e: Exception) {
          errorListener?.invoke(e)
        }
      }
    } catch (e: Exception) {
      errorListener?.invoke(e)
    } finally {
      close()
    }
  }
}
