/** Unit and integration tests in [CddCliCommandTest]. */
package org.cdd

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.cdd.mcp.DummyTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import runCli

/**
 * Focused unit tests for [CddCli], [SyncCli], [CddGenerator], and Meta-MCP JSON-RPC command
 * handlers.
 */
class CddCliCommandTest {

  @Test
  fun `CddCli and SyncCli handle environment variable fallbacks trailing flags and Meta-MCP requests`() {
    val outDir = Files.createTempDirectory("test_cdd_cli_cmd").toFile()
    try {
      val standaloneSchemaFile = File(outDir, "schema_only.json")
      standaloneSchemaFile.writeText(
          """{"${'$'}schema":"https://json-schema.org/draft/2020-12/schema","type":"string"}""")
      val sdkSchemaDir = File(outDir, "sdk_schema")
      assertTrue(
          CddGenerator.generateSdk(
                  Config(
                      inputPath = standaloneSchemaFile.absolutePath,
                      outputDir = sdkSchemaDir.absolutePath),
                  includeCli = true)
              .isSuccess)
      assertTrue(File(sdkSchemaDir, "build.gradle.kts").exists())

      assertEquals(
          0,
          CddCli.generateFromOpenApi(
              arrayOf(
                  "to_sdk",
                  "-i",
                  standaloneSchemaFile.absolutePath,
                  "-o",
                  File(outDir, "sdk_def").absolutePath)))
      assertEquals(
          1,
          CddCli.generateDocsJson(arrayOf("to_docs_json", "-i", standaloneSchemaFile.absolutePath)))

      val dummyTransport = DummyTransport()
      CddCli.createConfiguredMcpPeer(dummyTransport)
      val oasSpec =
          """{"openapi":"3.2.0","info":{"title":"T","version":"1"},"components":{"schemas":{"U":{"type":"object"}}}}"""
      val schemaOnlySpec =
          """{"${'$'}schema":"https://json-schema.org/draft/2020-12/schema","type":"string"}"""

      for (msg in
          listOf(
              """{"jsonrpc":"2.0","id":"1","method":"tools/cdd_generate","params":{"spec":${JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"2","method":"tools/cdd_generate","params":{"spec":${JsonPrimitive(schemaOnlySpec)}}}""",
              """{"jsonrpc":"2.0","id":"3","method":"tools/cdd_generate","params":{}}""",
              """{"jsonrpc":"2.0","id":"3b","method":"tools/cdd_generate","params":[1]}""",
              """{"jsonrpc":"2.0","id":"3c","method":"tools/cdd_generate","params":{"spec":"   "}}""",
              """{"jsonrpc":"2.0","id":"4","method":"tools/cdd_inspect_schema","params":{"spec":${JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"4b","method":"tools/cdd_inspect_schema","params":{"spec":${JsonPrimitive("""{"openapi":"3.2.0","info":{"title":"T","version":"1"}}""")}}}""",
              """{"jsonrpc":"2.0","id":"5","method":"tools/cdd_inspect_schema","params":{"spec":${JsonPrimitive(schemaOnlySpec)}}}""",
              """{"jsonrpc":"2.0","id":"6","method":"tools/cdd_inspect_schema","params":{}}""",
              """{"jsonrpc":"2.0","id":"6b","method":"tools/cdd_inspect_schema","params":[1]}""",
              """{"jsonrpc":"2.0","id":"6c","method":"tools/cdd_inspect_schema","params":{"spec":"   "}}""",
              """{"jsonrpc":"2.0","id":"7","method":"tools/cdd_sync_schema","params":{"inputDir":${JsonPrimitive(sdkSchemaDir.absolutePath)},"truth":"class"}}""",
              """{"jsonrpc":"2.0","id":"8","method":"tools/cdd_sync_schema","params":{}}""",
              """{"jsonrpc":"2.0","id":"8b","method":"tools/cdd_sync_schema","params":[1]}""",
              """{"jsonrpc":"2.0","id":"8c","method":"tools/cdd_sync_schema","params":{"inputDir":"   "}}""",
              """{"jsonrpc":"2.0","id":"9","method":"resources/ast_query","params":{"query":"U","spec":${JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"9b","method":"resources/ast_query","params":{"query":"*","spec":${JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"9c","method":"resources/ast_query","params":{"query":"Other","spec":${JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"9d","method":"resources/ast_query","params":{"query":"U","spec":${JsonPrimitive("""{"openapi":"3.2.0","info":{"title":"T","version":"1"}}""")}}}""",
              """{"jsonrpc":"2.0","id":"9e","method":"resources/ast_query","params":{"query":"U","spec":${JsonPrimitive(schemaOnlySpec)}}}""",
              """{"jsonrpc":"2.0","id":"10","method":"resources/ast_query","params":{}}""",
              """{"jsonrpc":"2.0","id":"10b","method":"resources/ast_query","params":[1]}""",
              """{"jsonrpc":"2.0","id":"10c","method":"resources/ast_query","params":{"spec":"   "}}""",
              """{"jsonrpc":"2.0","id":"11","method":"router/in_memory_generation","params":{"spec":${JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"11b","method":"router/in_memory_generation","params":{"spec":${JsonPrimitive(schemaOnlySpec)}}}""",
              """{"jsonrpc":"2.0","id":"12","method":"router/in_memory_generation","params":{}}""",
              """{"jsonrpc":"2.0","id":"12b","method":"router/in_memory_generation","params":[1]}""",
              """{"jsonrpc":"2.0","id":"12c","method":"router/in_memory_generation","params":{"spec":"   "}}""",
              """{"jsonrpc":"2.0","id":"13","method":"tools/list","params":{}}""",
              """{"jsonrpc":"2.0","id":"14","method":"resources/list","params":{}}""")) {
        dummyTransport.simulateReceive(Json.parseToJsonElement(msg))
      }
      assertEquals(30, dummyTransport.sentMessages.size)

      assertEquals(0, runCli(arrayOf("to_sdk", "--help")))
      assertEquals(0, runCli(arrayOf("to_sdk", "-h")))
      assertEquals(1, runCli(arrayOf("from_openapi")))
      assertEquals(
          0,
          runCli(
              arrayOf(
                  "from_openapi",
                  "-i",
                  standaloneSchemaFile.absolutePath,
                  "-o",
                  File(outDir, "sdk_from_flag").absolutePath)))
      assertEquals(
          0,
          runCli(
              arrayOf(
                  "to_sdk_cli",
                  "-i",
                  standaloneSchemaFile.absolutePath,
                  "-o",
                  File(outDir, "sdk_cli_dir").absolutePath)))
    } finally {
      outDir.deleteRecursively()
    }
  }
}
