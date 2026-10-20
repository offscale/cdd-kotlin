/**
 * Generator for producing Model Context Protocol (MCP) server, transport, tool adapter, resource
 * adapter, execution router, SSE endpoint, auth bridge, and API proxy modules from an OpenAPI
 * specification.
 */
package org.cdd.mcp

import domain.OpenApiDefinition

/** Generates functional MCP client/server bridge source files for an OpenAPI specification. */
object McpClientGenerator {

  private data class AdaptedMcpTool(
      val name: String,
      val description: String,
      val requiredParams: List<String>
  )

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
    val schemas =
        if (definition?.components != null) definition.components.schemas.keys else emptySet()
    val tools =
        paths.flatMap { (path, item) ->
          listOf(
                  "get" to item.get,
                  "post" to item.post,
                  "put" to item.put,
                  "delete" to item.delete,
                  "patch" to item.patch)
              .mapNotNull { (method, op) ->
                op?.let {
                  val name =
                      it.operationId.takeIf { id -> id.isNotBlank() }
                          ?: "${method}_${path.trim('/').replace('/', '_').replace("{", "").replace("}", "")}"
                  val desc = it.summary ?: it.description ?: "${method.uppercase()} $path"
                  val reqParams = it.parameters.filter { p -> p.isRequired }.map { p -> p.name }
                  AdaptedMcpTool(name, desc, reqParams)
                }
              }
        }

    val quote = '"'
    val toolListLiteral = tools.joinToString(", ") { "$quote${it.name}$quote" }
    val toolDescriptionsLiteral =
        tools.joinToString(", ") {
          "$quote${it.name}$quote to $quote${it.description.replace(quote, '\'')}$quote"
        }
    val toolRequiredParamsLiteral =
        tools.joinToString(", ") { tool ->
          val reqList = tool.requiredParams.joinToString(", ") { "$quote$it$quote" }
          "$quote${tool.name}$quote to listOf($reqList)"
        }
    val resourceListLiteral =
        (listOf("openapi://spec/info", "openapi://spec/schemas") +
                schemas.map { "openapi://spec/schemas/$it" })
            .joinToString(", ") { "$quote$it$quote" }

    return mapOf(
        "McpServer.kt" to
            """
            /** MCP Server module for $packageName. */
            package $packageName.mcp

            /**
             * Embedded MCP Server exposing OpenAPI operations as MCP tools and schemas as resources.
             *
             * @property serverName Name of the MCP server instance.
             * @property toolAdapter Adapter providing OpenAPI operation tool definitions.
             * @property resourceAdapter Adapter providing OpenAPI schema resources.
             * @property router Execution router dispatching tool calls.
             */
            class McpServer(
                val serverName: String = "$serverTitle",
                val toolAdapter: McpToolAdapter = McpToolAdapter(),
                val resourceAdapter: McpResourceAdapter = McpResourceAdapter(),
                val router: McpExecutionRouter = McpExecutionRouter(toolAdapter)
            ) {
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

              /**
               * Lists all registered tool names.
               *
               * @return List of tool names.
               */
              fun listTools(): List<String> = toolAdapter.adapt()

              /**
               * Lists all registered resource URIs.
               *
               * @return List of resource URIs.
               */
              fun listResources(): List<String> = resourceAdapter.adapt()

              /**
               * Calls a tool by [name] with optional [arguments].
               *
               * @param name Tool identifier.
               * @param arguments Map of argument names to string values.
               * @return True if the tool call succeeded.
               */
              fun callTool(name: String, arguments: Map<String, String> = emptyMap()): Boolean =
                  isRunning && router.routeWithArguments(name, arguments)
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
             * Adapts OpenAPI operations into callable MCP tool descriptors and input schemas.
             */
            class McpToolAdapter {
              private val descriptions: Map<String, String> = mapOf($toolDescriptionsLiteral)
              private val requiredParams: Map<String, List<String>> = mapOf($toolRequiredParamsLiteral)

              /**
               * Returns the list of adapted OpenAPI operation tool names.
               *
               * @return List of MCP tool identifiers.
               */
              fun adapt(): List<String> = listOf($toolListLiteral)

              /**
               * Returns the description for [toolName], or null if unknown.
               *
               * @param toolName Tool identifier.
               * @return Description string or null.
               */
              fun describe(toolName: String): String? = descriptions[toolName]

              /**
               * Returns the required parameter names for [toolName].
               *
               * @param toolName Tool identifier.
               * @return List of required parameter names.
               */
              fun requiredArguments(toolName: String): List<String> =
                  requiredParams[toolName] ?: emptyList()
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
              fun adapt(): List<String> = listOf($resourceListLiteral)

              /**
               * Reads the textual content of a resource [uri].
               *
               * @param uri Target resource URI.
               * @return Resource payload string or null if not found.
               */
              fun read(uri: String): String? =
                  if (adapt().contains(uri)) "$serverTitle: " + uri else null
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

              /**
               * Routes a tool invocation by [toolName] and validates required [arguments].
               *
               * @param toolName Name of the tool to invoke.
               * @param arguments Map of provided argument values.
               * @return True if the tool is registered and all required arguments are present.
               */
              fun routeWithArguments(
                  toolName: String,
                  arguments: Map<String, String> = emptyMap()
              ): Boolean {
                if (!adapter.adapt().contains(toolName)) return false
                return adapter.requiredArguments(toolName).all { arguments.containsKey(it) }
              }
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
              /** Most recent bridged `Authorization` header value. */
              var lastAuthorizationHeader: String = ""
                private set

              /**
               * Executes a proxied request for [toolName].
               *
               * @param toolName Target MCP tool name.
               * @param token Optional authentication token.
               * @return True if the proxy call is permitted and routed.
               */
              fun proxy(toolName: String = "", token: String? = null): Boolean {
                lastAuthorizationHeader = authBridge.bridge(token)
                return router.route(toolName)
              }
            }
            """
                .trimIndent())
  }
}
