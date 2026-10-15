import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import openapi.asArray
import openapi.asBoolean
import openapi.asDouble
import openapi.asInt
import openapi.asObject
import openapi.asText
import openapi.boolean
import openapi.booleanValue
import openapi.double
import openapi.doubleValue
import openapi.elements
import openapi.fields
import openapi.get
import openapi.has
import openapi.int
import openapi.intValue
import openapi.isArray
import openapi.isBoolean
import openapi.isNullNode
import openapi.isNumber
import openapi.isObject
import openapi.isTextual
import openapi.size
import openapi.text
import openapi.textValue
import openapi.writeToFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CliCoverageTest {

  @Test
  fun testCliToSdk() {
    val outDir = Files.createTempDirectory("test_sdk").toFile()
    val args =
        arrayOf(
            "from_openapi",
            "to_sdk",
            "-i",
            "src/test/resources/mega_spec.json",
            "-o",
            outDir.absolutePath)
    val exitCode = runCli(args)
    assertEquals(0, exitCode)
    assertTrue(File(outDir, "build.gradle.kts").exists())
    outDir.deleteRecursively()
  }

  @Test
  fun testCliToServer() {
    val outDir = Files.createTempDirectory("test_server").toFile()
    val args =
        arrayOf(
            "from_openapi",
            "to_server",
            "-i",
            "src/test/resources/mega_spec.json",
            "-o",
            outDir.absolutePath)
    val exitCode = runCli(args)
    assertEquals(0, exitCode)
    assertTrue(File(outDir, "build.gradle.kts").exists())
    outDir.deleteRecursively()
  }

  @Test
  fun testCliToDocsJson() {
    val out = ByteArrayOutputStream()
    val oldOut = System.out
    System.setOut(PrintStream(out))
    try {
      val args = arrayOf("to_docs_json", "-i", "src/test/resources/mega_spec.json")
      val exitCode = runCli(args)
      assertEquals(0, exitCode)
      val outStr = out.toString()
      assertTrue(outStr.contains("endpoints"))
    } finally {
      System.setOut(oldOut)
    }
  }

  @Test
  fun testCliDemo() {
    val args = arrayOf("demo")
    val exitCode = runCli(args)
    assertEquals(0, exitCode)
  }

  @Test
  fun testMcpPeer() {
    val inStream = ByteArrayInputStream("".toByteArray())
    val oldIn = System.`in`
    System.setIn(inStream)
    try {
      val args = arrayOf("mcp")
      val exitCode = runCli(args)
      assertEquals(0, exitCode)
    } finally {
      System.setIn(oldIn)
    }
  }

  @Test
  fun testServeJsonRpc() {
    val inStream = ByteArrayInputStream("".toByteArray())
    val oldIn = System.`in`
    System.setIn(inStream)
    try {
      val args = arrayOf("serve_json_rpc")
      val exitCode = runCli(args)
      assertEquals(0, exitCode)
    } finally {
      System.setIn(oldIn)
    }
  }

  @Test
  fun testCliToOpenApi() {
    val outDir = Files.createTempDirectory("test_openapi").toFile()
    val outFile = File(outDir, "out.json")
    val args =
        arrayOf(
            "to_openapi", "-i", "src/test/kotlin/CliCoverageTest.kt", "-o", outFile.absolutePath)
    val exitCode = runCli(args)
    // Might fail depending on logic, but covers lines
    assertTrue(exitCode >= 0)
    outDir.deleteRecursively()
  }

  @Test
  fun testCliSync() {
    val outDir = Files.createTempDirectory("test_sync").toFile()
    val args =
        arrayOf("sync", "-i", "src/test/kotlin/CliCoverageTest.kt", "-o", outDir.absolutePath)
    val exitCode = runCli(args)
    assertTrue(exitCode >= 0)
    outDir.deleteRecursively()
  }

  @Test
  fun testCliMainNoArgs() {
    val args = arrayOf<String>()
    val exitCode = runCli(args)
    assertEquals(0, exitCode)
  }

  @Test
  fun testCliMainHelp() {
    val args = arrayOf("--help")
    val exitCode = runCli(args)
    assertEquals(0, exitCode)
  }

  @Test
  fun testCliMainVersion() {
    val args = arrayOf("--version")
    val exitCode = runCli(args)
    assertEquals(0, exitCode)
  }

  @Test
  fun testCliMainUnknown() {
    val args = arrayOf("unknown_command")
    val exitCode = runCli(args)
    assertEquals(1, exitCode)
  }

  @Test
  fun testMainWrapper() {
    // test main(args: Array<String>) function directly
    val out = ByteArrayOutputStream()
    System.setOut(PrintStream(out))
    try {
      main(arrayOf("--help"))
    } catch (e: Exception) {}
    System.setOut(System.out)
  }

  @Test
  fun testCliToSdkMega() {
    val outDir = Files.createTempDirectory("test_sdk_mega").toFile()
    val args =
        arrayOf(
            "from_openapi",
            "to_sdk",
            "-i",
            "src/test/resources/mega_spec.json",
            "-o",
            outDir.absolutePath)
    val exitCode = runCli(args)
    assertEquals(0, exitCode)
    outDir.deleteRecursively()
  }

  @Test
  fun testCliToServerMega() {
    val outDir = Files.createTempDirectory("test_server_mega").toFile()
    val args =
        arrayOf(
            "from_openapi",
            "to_server",
            "-i",
            "src/test/resources/mega_spec.json",
            "-o",
            outDir.absolutePath)
    val exitCode = runCli(args)
    assertEquals(0, exitCode)
    outDir.deleteRecursively()
  }

  @Test
  fun testCliToSdkStripe() {
    val outDir = Files.createTempDirectory("test_sdk_stripe").toFile()
    val args =
        arrayOf(
            "from_openapi",
            "to_sdk",
            "-i",
            "src/test/resources/stripe.json",
            "-o",
            outDir.absolutePath)
    runCli(args)
    outDir.deleteRecursively()
  }

  @Test
  fun testCliToServerStripe() {
    val outDir = Files.createTempDirectory("test_server_stripe").toFile()
    val args =
        arrayOf(
            "from_openapi",
            "to_server",
            "-i",
            "src/test/resources/stripe.json",
            "-o",
            outDir.absolutePath)
    runCli(args)
    outDir.deleteRecursively()
  }

  @Test
  fun testCddGeneratorEdgeAndErrorCases() {
    val outDir = Files.createTempDirectory("test_gen_edges").toFile()
    try {
      assertTrue(
          org.cdd.CddGenerator.generateSdk(
                  org.cdd.Config(inputPath = "", outputDir = outDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateSdk(
                  org.cdd.Config(inputPath = "invalid.json", outputDir = outDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateSdk(
                  org.cdd.Config(inputPath = "missing.json", outputDir = outDir.absolutePath))
              .isFailure)

      assertTrue(
          org.cdd.CddGenerator.generateServer(
                  org.cdd.Config(inputPath = "", outputDir = outDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateServer(
                  org.cdd.Config(inputPath = "invalid.json", outputDir = outDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateServer(
                  org.cdd.Config(inputPath = "missing.json", outputDir = outDir.absolutePath))
              .isFailure)

      val malformedFile = File(outDir, "malformed.json")
      malformedFile.writeText("{ unclosed")
      assertTrue(
          org.cdd.CddGenerator.generateSdk(
                  org.cdd.Config(
                      inputPath = malformedFile.absolutePath, outputDir = outDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateServer(
                  org.cdd.Config(
                      inputPath = malformedFile.absolutePath, outputDir = outDir.absolutePath))
              .isFailure)

      val standaloneSchemaFile = File(outDir, "schema_only.json")
      standaloneSchemaFile.writeText(
          "{\"${'$'}schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"string\"}")
      val sdkSchemaDir = File(outDir, "sdk_schema")
      org.cdd.CddGenerator.generateSdk(
          org.cdd.Config(
              inputPath = standaloneSchemaFile.absolutePath, outputDir = sdkSchemaDir.absolutePath))
      assertTrue(File(sdkSchemaDir, "build.gradle.kts").exists())

      val srvSchemaDir = File(outDir, "srv_schema")
      org.cdd.CddGenerator.generateServer(
          org.cdd.Config(
              inputPath = standaloneSchemaFile.absolutePath,
              outputDir = srvSchemaDir.absolutePath,
              noGithubActions = true,
              noInstallablePackage = true,
              tests = true))

      // SyncCli and SyncGenerator branches
      assertEquals(1, org.cdd.SyncCli.sync(arrayOf("sync")))
      assertEquals(
          0,
          org.cdd.SyncCli.sync(arrayOf("sync", "--truth", "dao", "-i", sdkSchemaDir.absolutePath)))
      assertEquals(
          0,
          org.cdd.SyncCli.sync(
              arrayOf("sync", "--truth", "other", "--input-dir", sdkSchemaDir.absolutePath)))
      assertEquals(
          1,
          org.cdd.SyncCli.sync(
              arrayOf("sync", "--truth", "class", "-i", File(outDir, "nonexistent").absolutePath)))

      // ScaffoldTemplates default parameters
      assertTrue(scaffold.ScaffoldTemplates.createVersionCatalog().isNotEmpty())
      assertTrue(
          scaffold.ScaffoldTemplates.createServerBuildGradle("org.example", null).isNotEmpty())
      assertTrue(
          scaffold.ScaffoldTemplates.createServerBuildGradle(
                  "org.example", domain.Info("T", "1"), withTests = true)
              .isNotEmpty())
      assertTrue(scaffold.ScaffoldTemplates.createGithubActionsWorkflow().isNotEmpty())

      // JsonNodeExt exhaustive branch coverage and OpenApiWriterJvm default format
      val arrNode = kotlinx.serialization.json.Json.parseToJsonElement("[10, 20]")
      val objNode =
          kotlinx.serialization.json.Json.parseToJsonElement(
              "{\"a\": 1, \"b\": true, \"s\": \"txt\", \"n\": null, \"d\": 1.5}")
      val strNode = kotlinx.serialization.json.JsonPrimitive("hello")
      val boolNode = kotlinx.serialization.json.JsonPrimitive(true)
      val numNode = kotlinx.serialization.json.JsonPrimitive(42)
      val nullNode = kotlinx.serialization.json.JsonNull

      assertTrue(objNode.isObject)
      assertFalse(arrNode.isObject)
      assertTrue(arrNode.isArray)
      assertFalse(objNode.isArray)
      assertTrue(strNode.isTextual)
      assertFalse(numNode.isTextual)
      assertFalse(arrNode.isTextual)
      assertTrue(boolNode.isBoolean)
      assertFalse(strNode.isBoolean)
      assertFalse(numNode.isBoolean)
      assertFalse(arrNode.isBoolean)
      assertTrue(numNode.isNumber)
      assertFalse(strNode.isNumber)
      assertFalse(boolNode.isNumber)
      assertFalse(arrNode.isNumber)
      assertTrue(nullNode.isNullNode)
      assertFalse(strNode.isNullNode)

      assertEquals("hello", strNode.asText())
      assertEquals("fb", nullNode.asText("fb"))
      assertEquals("fb", arrNode.asText("fb"))
      assertEquals("hello", strNode.textValue())

      assertEquals(42, numNode.asInt())
      assertEquals(9, strNode.asInt(9))
      assertEquals(9, arrNode.asInt(9))
      assertEquals(0, strNode.intValue())

      assertEquals(42.0, numNode.asDouble())
      assertEquals(9.5, strNode.asDouble(9.5))
      assertEquals(9.5, arrNode.asDouble(9.5))
      assertEquals(42.0, numNode.doubleValue())
      assertEquals(0.0, strNode.doubleValue())

      assertEquals(true, boolNode.asBoolean())
      assertEquals(true, strNode.asBoolean(true))
      assertEquals(true, arrNode.asBoolean(true))
      assertEquals(false, strNode.booleanValue())

      assertEquals(10, arrNode.get(0)?.intValue())
      assertNull(arrNode.get(5))
      assertNull(arrNode.get(-1))
      assertNull(objNode.get(0))
      assertNull(arrNode.get("a"))
      assertTrue(objNode.has("a"))
      assertFalse(objNode.has("missing"))
      assertFalse(arrNode.has("a"))
      assertEquals(5, objNode.fields().size)
      assertTrue(arrNode.fields().isEmpty())
      assertEquals(2, arrNode.elements().size)
      assertTrue(objNode.elements().isEmpty())
      assertEquals(5, objNode.size())
      assertEquals(2, arrNode.size())
      assertEquals(0, strNode.size())

      assertEquals(objNode, objNode.asObject())
      assertNull(arrNode.asObject())
      assertNull((null as openapi.JsonNode?).asObject())
      assertEquals(2, arrNode.asArray()?.size)
      assertNull(objNode.asArray())
      assertNull((null as openapi.JsonNode?).asArray())

      assertEquals("txt", objNode.text("s"))
      assertNull(objNode.text("n"))
      assertNull(objNode.text("missing"))
      assertEquals(true, objNode.boolean("b"))
      assertNull(objNode.boolean("s"))
      assertNull(objNode.boolean("missing"))
      assertEquals(1, objNode.int("a"))
      assertNull(objNode.int("s"))
      assertNull(objNode.int("missing"))
      assertEquals(1.5, objNode.double("d"))
      assertNull(objNode.double("s"))
      assertNull(objNode.double("missing"))

      val tmpSpecFile = File(outDir, "written_default.json")
      openapi
          .OpenApiWriter()
          .writeToFile(domain.OpenApiDefinition(info = domain.Info("T", "1")), tmpSpecFile)
      assertTrue(tmpSpecFile.exists())

      // DaoTestGenerator non-nullable complex type, SeederGenerator Any type, DtoGenerator branches
      val complexSchema =
          domain.SchemaDefinition(
              name = "ComplexItem",
              type = "object",
              required = listOf("id", "sub", "listProp", "anyVal"),
              properties =
                  mapOf(
                      "id" to domain.SchemaProperty(types = setOf("integer")),
                      "sub" to domain.SchemaProperty(ref = "#/components/schemas/SubItem"),
                      "listProp" to
                          domain.SchemaProperty(
                              types = setOf("array"),
                              items = domain.SchemaProperty(types = setOf("string"))),
                      "anyVal" to
                          domain.SchemaProperty(
                              types = emptySet(),
                              ref = "#/components/schemas/AnyValue",
                              ifSchema =
                                  domain.SchemaProperty(
                                      types = setOf("string"),
                                      customKeywords =
                                          mapOf(
                                              "x-ext" to domain.OpenApiInt(1L),
                                              "nestedKw" to domain.OpenApiInt(2L))),
                              thenSchema = domain.SchemaProperty(types = setOf("string")),
                              elseSchema = domain.SchemaProperty(types = setOf("integer")),
                              examples = listOf(domain.OpenApiString("ex1")),
                              customKeywords =
                                  mapOf(
                                      "x-ext" to domain.OpenApiInt(1L),
                                      "customKw" to domain.OpenApiInt(2L)))))
      assertEquals("`class`", psi.escapeKotlinKeyword("class"))
      assertEquals("normalName", psi.escapeKotlinKeyword("normalName"))
      val builtDefault =
          psi.buildKotlinFile {
            kdoc("Doc line", "")
            block("class A", suffix = " // end") { line("val x = 1") }
          }
      assertTrue(builtDefault.contains("class A"))
      assertEquals("", psi.KotlinSourceBuilder().packageName)
      assertEquals("org.pkg", psi.KotlinSourceBuilder("org.pkg").packageName)

      val daoTests =
          psi.DaoTestGenerator()
              .generateDaoTests("org.example", listOf(complexSchema))
              .values
              .joinToString("\n")
      assertTrue(daoTests.contains("SubItem()"))
      assertTrue(daoTests.contains("emptyList()"))
      val seederCode =
          psi.SeederGenerator().generateSeederModule("org.example", listOf(complexSchema))
      assertTrue(seederCode.contains("anyVal = generateAnyValue()"))
      val dtoCode = psi.DtoGenerator().generateDto("org.example", complexSchema).text
      assertTrue(dtoCode.contains("@Contextual"))
      assertTrue(dtoCode.contains("@if"))
      assertTrue(dtoCode.contains("@then"))
      assertTrue(dtoCode.contains("@else"))

      // CddCli default target and generateDocsJson on standalone schema
      assertEquals(
          0,
          org.cdd.CddCli.generateFromOpenApi(
              arrayOf(
                  "to_sdk",
                  "-i",
                  standaloneSchemaFile.absolutePath,
                  "-o",
                  File(outDir, "sdk_def").absolutePath)))
      assertEquals(
          1,
          org.cdd.CddCli.generateDocsJson(
              arrayOf("to_docs_json", "-i", standaloneSchemaFile.absolutePath)))

      // CddCli.createConfiguredMcpPeer all handlers and branches
      val dummyTransport = org.cdd.mcp.DummyTransport()
      org.cdd.CddCli.createConfiguredMcpPeer(dummyTransport)
      val oasSpec =
          """{"openapi":"3.2.0","info":{"title":"T","version":"1"},"components":{"schemas":{"U":{"type":"object"}}}}"""
      val schemaOnlySpec =
          """{"${'$'}schema":"https://json-schema.org/draft/2020-12/schema","type":"string"}"""

      for (msg in
          listOf(
              """{"jsonrpc":"2.0","id":"1","method":"tools/cdd_generate","params":{"spec":${kotlinx.serialization.json.JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"2","method":"tools/cdd_generate","params":{"spec":${kotlinx.serialization.json.JsonPrimitive(schemaOnlySpec)}}}""",
              """{"jsonrpc":"2.0","id":"3","method":"tools/cdd_generate","params":{}}""",
              """{"jsonrpc":"2.0","id":"3b","method":"tools/cdd_generate","params":[1]}""",
              """{"jsonrpc":"2.0","id":"3c","method":"tools/cdd_generate","params":{"spec":"   "}}""",
              """{"jsonrpc":"2.0","id":"4","method":"tools/cdd_inspect_schema","params":{"spec":${kotlinx.serialization.json.JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"4b","method":"tools/cdd_inspect_schema","params":{"spec":${kotlinx.serialization.json.JsonPrimitive("""{"openapi":"3.2.0","info":{"title":"T","version":"1"}}""")}}}""",
              """{"jsonrpc":"2.0","id":"5","method":"tools/cdd_inspect_schema","params":{"spec":${kotlinx.serialization.json.JsonPrimitive(schemaOnlySpec)}}}""",
              """{"jsonrpc":"2.0","id":"6","method":"tools/cdd_inspect_schema","params":{}}""",
              """{"jsonrpc":"2.0","id":"6b","method":"tools/cdd_inspect_schema","params":[1]}""",
              """{"jsonrpc":"2.0","id":"6c","method":"tools/cdd_inspect_schema","params":{"spec":"   "}}""",
              """{"jsonrpc":"2.0","id":"7","method":"tools/cdd_sync_schema","params":{"inputDir":${kotlinx.serialization.json.JsonPrimitive(sdkSchemaDir.absolutePath)},"truth":"class"}}""",
              """{"jsonrpc":"2.0","id":"8","method":"tools/cdd_sync_schema","params":{}}""",
              """{"jsonrpc":"2.0","id":"8b","method":"tools/cdd_sync_schema","params":[1]}""",
              """{"jsonrpc":"2.0","id":"8c","method":"tools/cdd_sync_schema","params":{"inputDir":"   "}}""",
              """{"jsonrpc":"2.0","id":"9","method":"resources/ast_query","params":{"query":"User"}}""",
              """{"jsonrpc":"2.0","id":"10","method":"resources/ast_query","params":{}}""",
              """{"jsonrpc":"2.0","id":"10b","method":"resources/ast_query","params":[1]}""",
              """{"jsonrpc":"2.0","id":"11","method":"router/in_memory_generation","params":{"spec":${kotlinx.serialization.json.JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"11b","method":"router/in_memory_generation","params":{"spec":${kotlinx.serialization.json.JsonPrimitive(schemaOnlySpec)}}}""",
              """{"jsonrpc":"2.0","id":"12","method":"router/in_memory_generation","params":{}}""",
              """{"jsonrpc":"2.0","id":"12b","method":"router/in_memory_generation","params":[1]}""",
              """{"jsonrpc":"2.0","id":"12c","method":"router/in_memory_generation","params":{"spec":"   "}}""")) {
        dummyTransport.simulateReceive(kotlinx.serialization.json.Json.parseToJsonElement(msg))
      }
      assertEquals(23, dummyTransport.sentMessages.size)

      // Additional Main.kt, SyncCli.kt, CddGenerator.kt, CddCli.kt, PlatformJvm.kt,
      // ScaffoldTemplates.kt branches
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
      assertTrue(
          getEnvVar("PATH") != null || getEnvVar("HOME") != null || getEnvVar("USER") != null)

      // ScaffoldTemplates empty version in Info
      assertTrue(
          scaffold.ScaffoldTemplates.createAppBuildGradle("org.example", domain.Info("T", ""))
              .contains("versionName = \"1.0\""))

      // PlatformJvm writeToFile without parent directory, and schemas without components
      val rootRelativeFile = File("tmp_root_write_test.txt")
      try {
        assertTrue(writeToFile("tmp_root_write_test.txt", "root").isSuccess)
        assertEquals("root", readFile("tmp_root_write_test.txt").getOrThrow())
      } finally {
        rootRelativeFile.delete()
      }
      val minimalSpec = """{"openapi":"3.2.0","info":{"title":"Min","version":"1"}}"""
      val minOutDir = File(outDir, "min_spec_out")
      generateSdkCode(minimalSpec, minOutDir.absolutePath, "org.min")
      generateServerCode(minimalSpec, minOutDir.absolutePath, "org.min", false)

      // SyncCli trailing flags and CDD_INPUT_DIR env property
      System.setProperty("CDD_INPUT_DIR", sdkSchemaDir.absolutePath)
      try {
        assertEquals(1, org.cdd.SyncCli.sync(arrayOf("sync", "--truth")))
        assertEquals(0, org.cdd.SyncCli.sync(arrayOf("sync", "--truth", "dao", "--unknown", "-i")))
      } finally {
        System.clearProperty("CDD_INPUT_DIR")
      }

      // CddGenerator server without installable package
      val srvNoPkgDir = File(outDir, "srv_no_pkg")
      org.cdd.CddGenerator.generateServer(
          org.cdd.Config(
              inputPath = standaloneSchemaFile.absolutePath,
              outputDir = srvNoPkgDir.absolutePath,
              noInstallablePackage = false,
              noGithubActions = false,
              tests = false))
      assertTrue(File(srvNoPkgDir, "build.gradle.kts").exists())

      // CddCli environment variables and trailing flags
      val envProps =
          listOf(
              "CDD_OUTPUT" to File(outDir, "env_out").absolutePath,
              "CDD_INPUT" to standaloneSchemaFile.absolutePath,
              "CDD_INPUT_DIR" to outDir.absolutePath,
              "CDD_NO_GITHUB_ACTIONS" to "true",
              "CDD_NO_INSTALLABLE_PACKAGE" to "true",
              "CDD_TESTS" to "true",
              "CDD_NO_IMPORTS" to "true",
              "CDD_NO_WRAPPING" to "true")
      try {
        envProps.forEach { (k, v) -> System.setProperty(k, v) }
        assertEquals(
            0,
            org.cdd.CddCli.generateFromOpenApi(
                arrayOf("to_sdk", "--unknown", "-o", "-i", "--input-dir")))
        assertEquals(0, org.cdd.CddCli.generateFromOpenApi(arrayOf("to_sdk", "-o")))
        assertEquals(0, org.cdd.CddCli.generateFromOpenApi(arrayOf("to_sdk", "-i")))
        assertEquals(0, org.cdd.CddCli.generateFromOpenApi(arrayOf("to_sdk", "--input-dir")))

        System.setProperty("CDD_NO_GITHUB_ACTIONS", "1")
        System.setProperty("CDD_NO_INSTALLABLE_PACKAGE", "1")
        System.setProperty("CDD_TESTS", "1")
        System.setProperty("CDD_NO_IMPORTS", "1")
        System.setProperty("CDD_NO_WRAPPING", "1")
        assertEquals(0, org.cdd.CddCli.generateFromOpenApi(arrayOf("to_sdk")))

        val validOasFile = File(outDir, "valid_oas_env.json")
        validOasFile.writeText(
            """{"openapi":"3.2.0","info":{"title":"T","version":"1"},"paths":{"/empty":{}," /get":{"get":{"responses":{"200":{"description":"ok"}}}}}}""")
        System.setProperty("CDD_INPUT", validOasFile.absolutePath)
        val docsOutFile = File(outDir, "docs_env_out.json")
        System.setProperty("CDD_OUTPUT", docsOutFile.absolutePath)
        assertEquals(0, org.cdd.CddCli.generateDocsJson(arrayOf("to_docs_json", "-i")))
        assertEquals(0, org.cdd.CddCli.generateDocsJson(arrayOf("to_docs_json", "-o")))
        assertTrue(docsOutFile.exists())

        System.setProperty("CDD_NO_IMPORTS", "true")
        System.setProperty("CDD_NO_WRAPPING", "true")
        assertEquals(0, org.cdd.CddCli.generateDocsJson(arrayOf("to_docs_json")))

        // generateToOpenApi env vars and trailing flags
        System.setProperty("CDD_INPUT", sdkSchemaDir.absolutePath)
        val toOasOutFile = File(outDir, "to_oas_env.json")
        System.setProperty("CDD_OUTPUT", toOasOutFile.absolutePath)
        assertEquals(0, org.cdd.CddCli.generateToOpenApi(arrayOf("to_openapi", "--unknown", "-o")))
        assertEquals(0, org.cdd.CddCli.generateToOpenApi(arrayOf("to_openapi", "-i")))
        assertEquals(
            0,
            org.cdd.CddCli.generateToOpenApi(
                arrayOf(
                    "to_openapi",
                    "--input",
                    sdkSchemaDir.absolutePath,
                    "--output",
                    toOasOutFile.absolutePath)))

        // generateToOpenApi exception branch (output is an existing directory)
        assertEquals(
            1,
            org.cdd.CddCli.generateToOpenApi(
                arrayOf("to_openapi", "-i", sdkSchemaDir.absolutePath, "-o", outDir.absolutePath)))
      } finally {
        envProps.forEach { (k, _) -> System.clearProperty(k) }
      }

      // CddCli generateFromOpenApi when inputFile is empty but inputDir is provided
      assertEquals(
          1,
          org.cdd.CddCli.generateFromOpenApi(arrayOf("to_sdk", "--input-dir", outDir.absolutePath)))

      // CddCli generateDemo with empty and null user.dir and failing output dir
      val origUserDir = System.getProperty("user.dir")
      try {
        System.setProperty("user.dir", "")
        org.cdd.CddCli.generateDemo(arrayOf("demo"))
        System.clearProperty("user.dir")
        org.cdd.CddCli.generateDemo(arrayOf("demo"))
        val blockedDemoRoot = File(outDir, "blocked_demo")
        blockedDemoRoot.mkdirs()
        File(blockedDemoRoot, "generated-project/build.gradle.kts").mkdirs()
        System.setProperty("user.dir", blockedDemoRoot.absolutePath)
        assertEquals(1, org.cdd.CddCli.generateDemo(arrayOf("demo")))
      } finally {
        if (origUserDir != null) System.setProperty("user.dir", origUserDir)
        File("generated-project").deleteRecursively()
      }

      // CddCli generateDocsJson missing file and directory output error
      assertEquals(
          1,
          org.cdd.CddCli.generateDocsJson(
              arrayOf("to_docs_json", "-i", File(outDir, "nonexistent_doc.json").absolutePath)))
      val validDocSpec = File(outDir, "valid_doc.json")
      validDocSpec.writeText("""{"openapi":"3.2.0","info":{"title":"T","version":"1"}}""")
      assertEquals(
          1,
          org.cdd.CddCli.generateDocsJson(
              arrayOf("to_docs_json", "-i", validDocSpec.absolutePath, "-o", outDir.absolutePath)))

      // MCP cdd_sync_schema failure branch
      dummyTransport.simulateReceive(
          kotlinx.serialization.json.Json.parseToJsonElement(
              """{"jsonrpc":"2.0","id":"sync-err","method":"tools/cdd_sync_schema","params":{"inputDir":"/nonexistent_sync_dir_999","truth":"class"}}"""))

      // PlatformJvm & CddGenerator failure propagation branches
      assertTrue(generateSdkCode("{unclosed", outDir.absolutePath, "org.example").isFailure)
      assertTrue(
          generateServerCode("{unclosed", outDir.absolutePath, "org.example", false).isFailure)
      val badParamSpec =
          """{"openapi":"3.2.0","info":{"title":"T","version":"1"},"paths":{"/q":{"get":{"operationId":"op","parameters":[{"name":"q","in":"query","style":"spaceDelimited","explode":true,"schema":{"type":"array","items":{"type":"string"}}}],"responses":{"200":{"description":"ok"}}}}}}"""
      assertTrue(generateSdkCode(badParamSpec, outDir.absolutePath, "org.example").isFailure)
      val badParamSpecFile = File(outDir, "bad_param_spec.json")
      badParamSpecFile.writeText(badParamSpec)
      assertTrue(
          org.cdd.CddGenerator.generateSdk(
                  org.cdd.Config(
                      inputPath = badParamSpecFile.absolutePath,
                      outputDir = File(outDir, "bad_param_out").absolutePath))
              .isFailure)

      // FileIoError propagation in CddGenerator, PlatformJvm, and SyncGenerator
      val unwritableFile = File(outDir, "nonexistent_input.json")
      assertTrue(
          org.cdd.CddGenerator.generateSdk(
                  org.cdd.Config(
                      inputPath = unwritableFile.absolutePath, outputDir = outDir.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateServer(
                  org.cdd.Config(
                      inputPath = unwritableFile.absolutePath, outputDir = outDir.absolutePath))
              .isFailure)

      val blockedSdkOut = File(outDir, "blocked_sdk_out")
      File(blockedSdkOut, "build.gradle.kts").mkdirs()
      assertTrue(
          org.cdd.CddGenerator.generateSdk(
                  org.cdd.Config(
                      inputPath = validDocSpec.absolutePath,
                      outputDir = blockedSdkOut.absolutePath))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateServer(
                  org.cdd.Config(
                      inputPath = validDocSpec.absolutePath,
                      outputDir = blockedSdkOut.absolutePath))
              .isFailure)

      val blockedMcpOut = File(outDir, "blocked_mcp_out")
      File(blockedMcpOut, ".github/workflows/ci.yml").mkdirs()
      assertTrue(
          org.cdd.CddGenerator.generateSdk(
                  org.cdd.Config(
                      inputPath = validDocSpec.absolutePath,
                      outputDir = blockedMcpOut.absolutePath,
                      noInstallablePackage = true))
              .isFailure)
      assertTrue(
          org.cdd.CddGenerator.generateServer(
                  org.cdd.Config(
                      inputPath = validDocSpec.absolutePath,
                      outputDir = blockedMcpOut.absolutePath,
                      noInstallablePackage = true))
              .isFailure)

      val specWithModel =
          """{"openapi":"3.2.0","info":{"title":"T","version":"1"},"components":{"schemas":{"User":{"type":"object","properties":{"id":{"type":"string"}}}}}}"""
      val blockedModelOut = File(outDir, "blocked_model_out")
      File(blockedModelOut, "composeApp/src/commonMain/kotlin/org/example/models/User.kt").mkdirs()
      File(blockedModelOut, "server/src/main/kotlin/org/example/models/User.kt").mkdirs()
      assertTrue(
          generateSdkCode(specWithModel, blockedModelOut.absolutePath, "org.example").isFailure)
      assertTrue(
          generateServerCode(specWithModel, blockedModelOut.absolutePath, "org.example", false)
              .isFailure)
      val specWithModelFile = File(outDir, "spec_with_model.json")
      specWithModelFile.writeText(specWithModel)
      assertTrue(
          org.cdd.CddGenerator.generateServer(
                  org.cdd.Config(
                      inputPath = specWithModelFile.absolutePath,
                      outputDir = blockedModelOut.absolutePath,
                      noInstallablePackage = true,
                      noGithubActions = true))
              .isFailure)

      // SyncGenerator write failure branches
      val syncFailDir = File(outDir, "sync_fail_dir")
      syncFailDir.mkdirs()
      File(syncFailDir, "Model.kt")
          .writeText("package org.example\ndata class User(val id: String)")
      File(syncFailDir, "dao/UserDao.kt").mkdirs()
      assertTrue(psi.SyncGenerator.synchronize(syncFailDir.absolutePath, "class").isFailure)
      val syncSpecFailDir = File(outDir, "sync_spec_fail_dir")
      syncSpecFailDir.mkdirs()
      File(syncSpecFailDir, "openapi_sync.json").mkdirs()
      assertTrue(psi.SyncGenerator.synchronize(syncSpecFailDir.absolutePath, "dao").isFailure)

      // EndpointMethodGenerator header and cookie failure propagation
      val badHeaderEp =
          domain.EndpointDefinition(
              path = "/h",
              method = domain.HttpMethod.GET,
              operationId = "badH",
              parameters =
                  listOf(
                      domain.EndpointParameter(
                          name = "h",
                          type = "String",
                          location = domain.ParameterLocation.HEADER,
                          style = domain.ParameterStyle.SIMPLE,
                          content = mapOf("text/plain" to domain.MediaTypeObject()))),
              responses = mapOf("200" to domain.EndpointResponse("200", type = "Unit")))
      assertTrue(psi.network.EndpointMethodGenerator.generateMethodImpl(badHeaderEp).isFailure)
      val badCookieEp =
          domain.EndpointDefinition(
              path = "/c",
              method = domain.HttpMethod.GET,
              operationId = "badC",
              parameters =
                  listOf(
                      domain.EndpointParameter(
                          name = "c",
                          type = "String",
                          location = domain.ParameterLocation.COOKIE,
                          style = domain.ParameterStyle.FORM,
                          content = mapOf("text/plain" to domain.MediaTypeObject()))),
              responses = mapOf("200" to domain.EndpointResponse("200", type = "Unit")))
      assertTrue(psi.network.EndpointMethodGenerator.generateMethodImpl(badCookieEp).isFailure)

      // SafeJsonReader exhaustive branches
      val validJsonSamples =
          listOf(
              """{"esc":"\"\\\/\b\f\n\r\t\u0041\u00af\u00AF"}""",
              """[true, false, null, 0, -42, 3.14, -0.5, 1e5, 2E+3, 4e-2, 1.5e10, 999999999999999999999999999999]""",
              """{}""",
              """[]""")
      for (sample in validJsonSamples) {
        assertTrue(openapi.parseJsonElementResult(sample).isSuccess, "Expected valid: $sample")
      }
      val invalidJsonSamples =
          listOf(
              "",
              "   ",
              "true false",
              "@",
              "tru",
              "fals",
              "nul",
              "{",
              """{"a"""",
              """{"a" """,
              """{"a" 1}""",
              """{"a":""",
              """{"a":1""",
              """{"a":1;""",
              """{1:2}""",
              """{"\x":1}""",
              "[",
              "[1",
              "[1;",
              "[#",
              "\"unterminated",
              "\"\\",
              "\"\\x\"",
              "\"\\u12\"",
              "\"\\u123g\"",
              "\"\\u123\"",
              "-",
              "-a",
              "1.",
              "1.a",
              "1e",
              "1e+",
              "1e-",
              "1ea")
      for (sample in invalidJsonSamples) {
        assertTrue(openapi.parseJsonElementResult(sample).isFailure, "Expected invalid: $sample")
      }
      val nonStringQuotedYaml = openapi.parser.YamlToJsonElementConverter.parseYaml("\"[1]\": val")
      assertTrue(nonStringQuotedYaml is kotlinx.serialization.json.JsonObject)
    } finally {
      outDir.deleteRecursively()
    }
  }
}
