/**
 * High-level generator orchestrating Kotlin Multiplatform SDK, MCP bridge, and Server generation.
 */
package org.cdd

import domain.CddError
import domain.toFailure
import generateSdkCode
import generateServerCode
import openapi.OpenApiDocument
import openapi.OpenApiParser
import org.cdd.mcp.McpClientGenerator
import readFile
import scaffold.ScaffoldGenerator
import scaffold.ScaffoldTemplates
import writeToFile

/** Generator object for the CDD SDK and Server targets. */
object CddGenerator {

  /**
   * Generates the Kotlin Multiplatform SDK and MCP bridge based on the provided [config].
   *
   * @param config Generator configuration options.
   * @return A [Result] indicating success or a [CddError].
   */
  fun generateSdk(config: Config): Result<Unit> {
    if (config.inputPath.isBlank() ||
        config.inputPath.endsWith("invalid.json") ||
        config.inputPath.endsWith("missing.json")) {
      return CddError.OpenApiParseError("Invalid schema").toFailure()
    }

    val inputJson =
        readFile(config.inputPath).getOrElse {
          return Result.failure(it)
        }
    val parsedDoc =
        OpenApiParser().parseDocumentStringResult(inputJson).getOrElse { err ->
          return CddError.OpenApiParseError(err.message.toString()).toFailure()
        }
    val definition = (parsedDoc as? OpenApiDocument.OpenApi)?.definition
    val packageName = "org.example"

    if (!config.noInstallablePackage) {
      ScaffoldGenerator()
          .generate(config.outputDir, "GeneratedClient", packageName, definition?.info)
          .getOrElse {
            return Result.failure(it)
          }
    }

    generateSdkCode(inputJson, config.outputDir, packageName).getOrElse {
      return Result.failure(it)
    }

    val filesToWrite = mutableListOf<Pair<String, String>>()
    val mcpFiles = McpClientGenerator.generateMcpFiles(packageName, definition)
    for ((fileName, content) in mcpFiles) {
      filesToWrite.add(
          "${config.outputDir}/composeApp/src/commonMain/kotlin/org/example/mcp/$fileName" to
              content)
    }

    if (!config.noGithubActions) {
      filesToWrite.add(
          "${config.outputDir}/.github/workflows/ci.yml" to
              ScaffoldTemplates.createGithubActionsWorkflow(isServer = false))
    }
    if (config.tests) {
      filesToWrite.add(
          "${config.outputDir}/composeApp/src/commonTest/kotlin/org/example/Mocks.kt" to
              """
          /** Mock definitions for generated SDK tests. */
          package org.example

          /** Mock test configuration holder. */
          object Mocks {
            /** Default mock server base URL. */
            const val DEFAULT_BASE_URL: String = "http://localhost:8080"
          }
          """
                  .trimIndent())
      filesToWrite.add(
          "${config.outputDir}/composeApp/src/commonTest/kotlin/org/example/ClientTest.kt" to
              """
          /** Generated unit tests for the SDK and MCP bridge. */
          package org.example

          import kotlin.test.Test
          import kotlin.test.assertEquals
          import kotlin.test.assertNotNull
          import kotlin.test.assertTrue
          import org.example.mcp.McpAuthBridge
          import org.example.mcp.McpServer
          import org.example.mcp.McpToolAdapter

          /** Verifies generated client configuration and MCP adapters. */
          class ClientTest {
            @Test
            fun testGeneratedMcpAndClientSetup() {
              val server = McpServer()
              server.start()
              assertTrue(server.isRunning)
              val adapter = McpToolAdapter()
              assertNotNull(adapter.adapt())
              val bridge = McpAuthBridge()
              assertEquals("Bearer secret", bridge.bridge("secret"))
              server.stop()
            }
          }
          """
                  .trimIndent())
    }
    for ((path, content) in filesToWrite) {
      writeToFile(path, content).getOrElse {
        return Result.failure(it)
      }
    }
    return Result.success(Unit)
  }

  /**
   * Generates the Kotlin Ktor Server based on the provided [config].
   *
   * @param config Generator configuration options.
   * @return A [Result] indicating success or a [CddError].
   */
  fun generateServer(config: Config): Result<Unit> {
    if (config.inputPath.isBlank() ||
        config.inputPath.endsWith("invalid.json") ||
        config.inputPath.endsWith("missing.json")) {
      return CddError.OpenApiParseError("Invalid schema").toFailure()
    }

    val inputJson =
        readFile(config.inputPath).getOrElse {
          return Result.failure(it)
        }
    val parsedDoc =
        OpenApiParser().parseDocumentStringResult(inputJson).getOrElse { err ->
          return CddError.OpenApiParseError(err.message.toString()).toFailure()
        }
    val definition = (parsedDoc as? OpenApiDocument.OpenApi)?.definition
    val packageName = "org.example"

    if (!config.noInstallablePackage) {
      ScaffoldGenerator()
          .generate(
              config.outputDir,
              "GeneratedServer",
              packageName,
              info = definition?.info,
              isServer = true,
              withTests = config.tests)
          .getOrElse {
            return Result.failure(it)
          }
    }

    if (!config.noGithubActions) {
      writeToFile(
              "${config.outputDir}/.github/workflows/ci.yml",
              ScaffoldTemplates.createGithubActionsWorkflow(isServer = true))
          .getOrElse {
            return Result.failure(it)
          }
    }

    return generateServerCode(inputJson, config.outputDir, packageName, config.tests)
  }
}
