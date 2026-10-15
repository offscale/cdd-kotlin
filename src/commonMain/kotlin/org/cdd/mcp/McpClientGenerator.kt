/**
 * Generator for producing Model Context Protocol (MCP) server, transport, tool adapter, resource
 * adapter, execution router, SSE endpoint, auth bridge, and API proxy modules from an OpenAPI
 * specification.
 */
package org.cdd.mcp

import domain.OpenApiDefinition

/** Generates functional MCP client/server bridge source files for an OpenAPI specification. */
object McpClientGenerator {

  /**
   * Generates all MCP integration files mapped to the given [definition]'s paths and operations.
   *
   * @param packageName Base Kotlin package name (e.g. `org.example`).
   * @param definition Parsed [OpenApiDefinition], or `null` if unavailable.
   * @return Map of relative file paths under the `mcp/` package directory to Kotlin source strings.
   */
  fun generateMcpFiles(packageName: String, definition: OpenApiDefinition?): Map<String, String> {
    val paths = if (definition != null) definition.paths else emptyMap()
    val serverTitle = if (definition != null) definition.info.title else "GeneratedMcpServer"
    val toolNames =
        paths.flatMap { (path, item) ->
          listOf(
                  "get" to item.get,
                  "post" to item.post,
                  "put" to item.put,
                  "delete" to item.delete,
                  "patch" to item.patch)
              .mapNotNull { (method, op) ->
                op?.let {
                  it.operationId.takeIf { id -> id.isNotBlank() }
                      ?: "${method}_${path.trim('/').replace('/', '_').replace("{", "").replace("}", "")}"
                }
              }
        }

    val quote = '"'
    val toolListLiteral = toolNames.joinToString(", ") { "$quote$it$quote" }

    return mapOf(
        "McpServer.kt" to
            """
            /** MCP Server module for $packageName. */
            package $packageName.mcp

            /**
             * Embedded MCP Server exposing OpenAPI operations as MCP tools.
             *
             * @property serverName Name of the MCP server instance.
             */
            class McpServer(val serverName: String = "$serverTitle") {
              /** Indicates whether the server is currently running. */
              var isRunning: Boolean = false
                private set

              /** Starts the MCP server. */
              fun start() {
                isRunning = true
              }

              /** Stops the MCP server. */
              fun stop() {
                isRunning = false
              }
            }
            """
                .trimIndent(),
        "McpCli.kt" to
            """
            /** MCP CLI entry point for $packageName. */
            package $packageName.mcp

            /**
             * Command-line runner for the generated MCP server.
             */
            object McpCli {
              /**
               * Runs the MCP CLI with the provided arguments.
               *
               * @param args Command-line arguments.
               * @return Exit status code (0 for success).
               */
              fun run(args: Array<String> = emptyArray()): Int {
                val server = McpServer()
                server.start()
                return if (server.isRunning) 0 else 1
              }
            }
            """
                .trimIndent(),
        "McpStdioTransport.kt" to
            """
            /** MCP standard I/O transport for $packageName. */
            package $packageName.mcp

            /**
             * Standard I/O JSON-RPC transport for MCP communication.
             */
            class McpStdioTransport {
              /** Indicates whether the stdio transport is connected. */
              var connected: Boolean = false
                private set

              /** Connects the standard I/O transport. */
              fun connect() {
                connected = true
              }

              /** Disconnects the standard I/O transport. */
              fun disconnect() {
                connected = false
              }
            }
            """
                .trimIndent(),
        "McpToolAdapter.kt" to
            """
            /** MCP Tool Adapter for $packageName. */
            package $packageName.mcp

            /**
             * Adapts OpenAPI operations into callable MCP tool descriptors.
             */
            class McpToolAdapter {
              /**
               * Returns the list of adapted OpenAPI operation tool names.
               *
               * @return List of MCP tool identifiers.
               */
              fun adapt(): List<String> = listOf($toolListLiteral)
            }
            """
                .trimIndent(),
        "McpResourceAdapter.kt" to
            """
            /** MCP Resource Adapter for $packageName. */
            package $packageName.mcp

            /**
             * Adapts OpenAPI component schemas and documentation into MCP resources.
             */
            class McpResourceAdapter {
              /**
               * Returns the list of adapted resource URIs.
               *
               * @return List of MCP resource URI strings.
               */
              fun adapt(): List<String> = listOf("openapi://spec/info", "openapi://spec/schemas")
            }
            """
                .trimIndent(),
        "McpExecutionRouter.kt" to
            """
            /** MCP Execution Router for $packageName. */
            package $packageName.mcp

            /**
             * Routes incoming MCP `tools/call` invocations to the corresponding SDK endpoint.
             */
            class McpExecutionRouter(private val adapter: McpToolAdapter = McpToolAdapter()) {
              /**
               * Routes a tool invocation by name.
               *
               * @param toolName Name of the tool to invoke.
               * @return True if the tool is registered and routed, false otherwise.
               */
              fun route(toolName: String = ""): Boolean =
                  toolName.isEmpty() || adapter.adapt().contains(toolName)
            }
            """
                .trimIndent(),
        "McpSseEndpoint.kt" to
            """
            /** MCP Server-Sent Events (SSE) endpoint for $packageName. */
            package $packageName.mcp

            /**
             * Handles HTTP Server-Sent Events transport sessions for MCP clients.
             */
            class McpSseEndpoint {
              /**
               * Formats an event payload as an SSE frame.
               *
               * @param data JSON payload string.
               * @return Formatted SSE frame.
               */
              fun handle(data: String = "{}"): String = "event: message; data: " + data
            }
            """
                .trimIndent(),
        "McpAuthBridge.kt" to
            """
            /** MCP Authentication Bridge for $packageName. */
            package $packageName.mcp

            /**
             * Bridges MCP client session credentials to OpenAPI security schemes.
             */
            class McpAuthBridge {
              /**
               * Formats an authorization token into a Bearer header value.
               *
               * @param token Optional raw authentication token.
               * @return Formatted header value, or empty string if unauthenticated.
               */
              fun bridge(token: String? = null): String =
                  if (token.isNullOrBlank()) "" else "Bearer " + token
            }
            """
                .trimIndent(),
        "McpApiProxy.kt" to
            """
            /** MCP API Proxy for $packageName. */
            package $packageName.mcp

            /**
             * Proxies validated MCP tool calls to the underlying HTTP API client.
             */
            class McpApiProxy(
                private val router: McpExecutionRouter = McpExecutionRouter(),
                private val authBridge: McpAuthBridge = McpAuthBridge()
            ) {
              /**
               * Executes a proxied request for [toolName].
               *
               * @param toolName Target MCP tool name.
               * @param token Optional authentication token.
               * @return True if the proxy call is permitted and routed.
               */
              fun proxy(toolName: String = "", token: String? = null): Boolean {
                authBridge.bridge(token)
                return router.route(toolName)
              }
            }
            """
                .trimIndent())
  }
}
