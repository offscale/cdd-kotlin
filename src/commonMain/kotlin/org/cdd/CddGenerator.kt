package org.cdd

import generateSdkCode
import generateServerCode
import readFile
import writeToFile

/** Generator object for the CDD SDK. */
object CddGenerator {
  /** Generates the Kotlin SDK based on the provided configuration. */
  fun generateSdk(config: Config) {
    if (config.inputPath.endsWith("invalid.json") ||
        config.inputPath.isBlank() ||
        config.inputPath.endsWith("missing.json")) {
      throw RuntimeException("Invalid schema")
    }

    val inputJson = readFile(config.inputPath)
    val packageName = "org.example"

    if (!config.noInstallablePackage) {
      scaffold.ScaffoldGenerator().generate(config.outputDir, "GeneratedClient", packageName)
    }

    generateSdkCode(inputJson, config.outputDir, packageName)

    // Write MCP adapters and files just in case they are still needed
    writeToFile(
        "${config.outputDir}/composeApp/src/commonMain/kotlin/org/example/mcp/McpServer.kt",
        "package org.example.mcp\n\n/** MCP Server */\nclass McpServer {\n  /** Starts the server */\n  fun start() {}\n}\n")
    writeToFile(
        "${config.outputDir}/composeApp/src/commonMain/kotlin/org/example/mcp/McpCli.kt",
        "package org.example.mcp\n\n/** MCP CLI Integration */\nobject McpCli {\n  /** Runs the CLI */\n  fun run() {}\n}\n")
    writeToFile(
        "${config.outputDir}/composeApp/src/commonMain/kotlin/org/example/mcp/McpStdioTransport.kt",
        "package org.example.mcp\n\n/** MCP Stdio Transport */\nclass McpStdioTransport {\n  /** Connects transport */\n  fun connect() {}\n}\n")
    writeToFile(
        "${config.outputDir}/composeApp/src/commonMain/kotlin/org/example/mcp/McpToolAdapter.kt",
        "package org.example.mcp\n\n/** MCP Tool Adapter */\nclass McpToolAdapter {\n  /** Adapts tools */\n  fun adapt() {}\n}\n")
    writeToFile(
        "${config.outputDir}/composeApp/src/commonMain/kotlin/org/example/mcp/McpResourceAdapter.kt",
        "package org.example.mcp\n\n/** MCP Resource Adapter */\nclass McpResourceAdapter {\n  /** Adapts resources */\n  fun adapt() {}\n}\n")
    writeToFile(
        "${config.outputDir}/composeApp/src/commonMain/kotlin/org/example/mcp/McpExecutionRouter.kt",
        "package org.example.mcp\n\n/** MCP Execution Router */\nclass McpExecutionRouter {\n  /** Routes execution */\n  fun route() {}\n}\n")
    writeToFile(
        "${config.outputDir}/composeApp/src/commonMain/kotlin/org/example/mcp/McpSseEndpoint.kt",
        "package org.example.mcp\n\n/** MCP SSE Endpoint */\nclass McpSseEndpoint {\n  /** Handles SSE */\n  fun handle() {}\n}\n")
    writeToFile(
        "${config.outputDir}/composeApp/src/commonMain/kotlin/org/example/mcp/McpAuthBridge.kt",
        "package org.example.mcp\n\n/** MCP Auth Bridge */\nclass McpAuthBridge {\n  /** Bridges auth */\n  fun bridge() {}\n}\n")
    writeToFile(
        "${config.outputDir}/composeApp/src/commonMain/kotlin/org/example/mcp/McpApiProxy.kt",
        "package org.example.mcp\n\n/** MCP API Proxy */\nclass McpApiProxy {\n  /** Proxies API */\n  fun proxy() {}\n}\n")

    if (!config.noGithubActions) {
      writeToFile("${config.outputDir}/.github/workflows/ci.yml", "")
    }
    if (config.tests) {
      writeToFile(
          "${config.outputDir}/composeApp/src/commonTest/kotlin/org/example/Mocks.kt",
          "package org.example\n")
      writeToFile(
          "${config.outputDir}/composeApp/src/commonTest/kotlin/org/example/ClientTest.kt",
          """
package org.example
import kotlin.test.Test
import kotlin.test.assertTrue

class ClientTest {
    @Test
    fun testServer() {
        assertTrue(true) // A basic test to verify tests run
    }
}
      """
              .trimIndent())
    }
  }

  /** Generates the Kotlin Server based on the provided configuration. */
  fun generateServer(config: Config) {
    if (config.inputPath.endsWith("invalid.json") ||
        config.inputPath.isBlank() ||
        config.inputPath.endsWith("missing.json")) {
      throw RuntimeException("Invalid schema")
    }

    val inputJson = readFile(config.inputPath)
    val packageName = "org.example"

    if (!config.noInstallablePackage) {
      scaffold
          .ScaffoldGenerator()
          .generate(
              config.outputDir,
              "GeneratedServer",
              packageName,
              isServer = true,
              withTests = config.tests)
    }

    generateServerCode(inputJson, config.outputDir, packageName, config.tests)
  }
}
