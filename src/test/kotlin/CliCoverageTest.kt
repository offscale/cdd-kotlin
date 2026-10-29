/** Unit and integration tests in [CliCoverageTest]. */
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import openapi.writeToFile
import org.junit.jupiter.api.Assertions.assertEquals
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
    val outDir = Files.createTempDirectory("test_demo_out").toFile()
    try {
      val args = arrayOf("demo", "-o", outDir.absolutePath)
      val exitCode = runCli(args)
      assertEquals(0, exitCode)
      assertTrue(File(outDir, "build.gradle.kts").exists())
    } finally {
      outDir.deleteRecursively()
    }
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
    try {
      File(outDir, "Sample.kt")
          .writeText("package org.example.models\ndata class Sample(val id: String)")
      val outFile = File(outDir, "out.json")
      val args = arrayOf("to_openapi", "-i", outDir.absolutePath, "-o", outFile.absolutePath)
      val exitCode = runCli(args)
      assertEquals(0, exitCode)
      assertTrue(outFile.exists())
      assertTrue(outFile.readText().contains("Sample"))
    } finally {
      outDir.deleteRecursively()
    }
  }

  @Test
  fun testCliSync() {
    val outDir = Files.createTempDirectory("test_sync").toFile()
    try {
      File(outDir, "Sample.kt")
          .writeText("package org.example.models\ndata class Sample(val id: Int)")
      val exitCode = runCli(arrayOf("sync", "--truth", "class", "-i", outDir.absolutePath))
      assertEquals(0, exitCode)
      assertTrue(File(outDir, "dao/SampleDao.kt").exists())
      assertTrue(File(outDir, "openapi_sync.json").exists())
    } finally {
      outDir.deleteRecursively()
    }
  }

  @Test
  fun testCliMainNoArgs() {
    val args = arrayOf<String>()
    val exitCode = runCli(args)
    assertEquals(0, exitCode)
  }

  @Test
  fun testCliMainHelp() {
    val helpInvocations =
        listOf(
            arrayOf("--help"),
            arrayOf("-h"),
            arrayOf("from_openapi", "--help"),
            arrayOf("from_openapi", "to_sdk", "--help"),
            arrayOf("from_openapi", "to_sdk_cli", "--help"),
            arrayOf("from_openapi", "to_server", "--help"),
            arrayOf("to_sdk", "--help"),
            arrayOf("to_sdk_cli", "--help"),
            arrayOf("to_server", "--help"),
            arrayOf("to_openapi", "--help"),
            arrayOf("from_arazzo", "--help"),
            arrayOf("from_arazzo", "to_sdk", "--help"),
            arrayOf("from_arazzo", "to_sdk_cli", "--help"),
            arrayOf("from_arazzo", "to_server", "--help"),
            arrayOf("to_arazzo", "--help"),
            arrayOf("sync", "--help"),
            arrayOf("to_docs_json", "--help"),
            arrayOf("serve_json_rpc", "--help"),
            arrayOf("mcp", "--help"),
            arrayOf("demo", "--help"),
            arrayOf("unknown", "--help"))
    for (args in helpInvocations) {
      assertEquals(0, runCli(args))
    }
  }

  @Test
  fun testCliMainVersion() {
    assertEquals(0, runCli(arrayOf("--version")))
    assertEquals(0, runCli(arrayOf("-v")))
  }

  @Test
  fun testCliMainUnknown() {
    val args = arrayOf("unknown_command")
    val exitCode = runCli(args)
    assertEquals(1, exitCode)
  }

  @Test
  fun testMainWrapper() {
    val origOut = System.out
    val out = ByteArrayOutputStream()
    System.setOut(PrintStream(out))
    try {
      main(arrayOf("--help"))
      assertTrue(out.toString().contains("Usage:"))
    } finally {
      System.setOut(origOut)
    }
  }

  @Test
  fun testCliToSdkMega() {
    val outDir = Files.createTempDirectory("test_sdk_mega").toFile()
    try {
      val args =
          arrayOf(
              "from_openapi",
              "to_sdk_cli",
              "-i",
              "src/test/resources/mega_spec.json",
              "-o",
              outDir.absolutePath,
              "--no-github-actions")
      val exitCode = runCli(args)
      assertEquals(0, exitCode)
      assertTrue(
          File(outDir, "composeApp/src/commonMain/kotlin/org/example/cli/ApiCli.kt").exists())
    } finally {
      outDir.deleteRecursively()
    }
  }

  @Test
  fun testCliToServerMega() {
    val outDir = Files.createTempDirectory("test_server_mega").toFile()
    try {
      val args =
          arrayOf(
              "from_openapi",
              "to_server",
              "-i",
              "src/test/resources/mega_spec.json",
              "-o",
              outDir.absolutePath,
              "--no-github-actions")
      val exitCode = runCli(args)
      assertEquals(0, exitCode)
      assertTrue(File(outDir, "server/src/main/kotlin/org/example/Main.kt").exists())
    } finally {
      outDir.deleteRecursively()
    }
  }

  @Test
  fun testCliToSdkStripe() {
    val outDir = Files.createTempDirectory("test_sdk_stripe").toFile()
    try {
      val args =
          arrayOf(
              "from_openapi",
              "to_sdk",
              "-i",
              "src/test/resources/stripe.json",
              "-o",
              outDir.absolutePath)
      val exitCode = runCli(args)
      assertEquals(0, exitCode)
      assertTrue(File(outDir, "build.gradle.kts").exists())
    } finally {
      outDir.deleteRecursively()
    }
  }

  @Test
  fun testCliToServerStripe() {
    val outDir = Files.createTempDirectory("test_server_stripe").toFile()
    try {
      val args =
          arrayOf(
              "from_openapi",
              "to_server",
              "-i",
              "src/test/resources/stripe.json",
              "-o",
              outDir.absolutePath)
      val exitCode = runCli(args)
      assertEquals(0, exitCode)
      assertTrue(File(outDir, "server/src/main/kotlin/org/example/Main.kt").exists())
    } finally {
      outDir.deleteRecursively()
    }
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
              inputPath = standaloneSchemaFile.absolutePath, outputDir = sdkSchemaDir.absolutePath),
          includeCli = true)
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
              arrayOf("sync", "--truth", "openapi", "--input-dir", sdkSchemaDir.absolutePath)))
      assertEquals(
          1,
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

      // JsonNodeExt and OpenApiWriterJvm default format
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
              """{"jsonrpc":"2.0","id":"9","method":"resources/ast_query","params":{"query":"U","spec":${kotlinx.serialization.json.JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"9b","method":"resources/ast_query","params":{"query":"*","spec":${kotlinx.serialization.json.JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"9c","method":"resources/ast_query","params":{"query":"Other","spec":${kotlinx.serialization.json.JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"9d","method":"resources/ast_query","params":{"query":"U","spec":${kotlinx.serialization.json.JsonPrimitive("""{"openapi":"3.2.0","info":{"title":"T","version":"1"}}""")}}}""",
              """{"jsonrpc":"2.0","id":"9e","method":"resources/ast_query","params":{"query":"U","spec":${kotlinx.serialization.json.JsonPrimitive(schemaOnlySpec)}}}""",
              """{"jsonrpc":"2.0","id":"10","method":"resources/ast_query","params":{}}""",
              """{"jsonrpc":"2.0","id":"10b","method":"resources/ast_query","params":[1]}""",
              """{"jsonrpc":"2.0","id":"10c","method":"resources/ast_query","params":{"spec":"   "}}""",
              """{"jsonrpc":"2.0","id":"11","method":"router/in_memory_generation","params":{"spec":${kotlinx.serialization.json.JsonPrimitive(oasSpec)}}}""",
              """{"jsonrpc":"2.0","id":"11b","method":"router/in_memory_generation","params":{"spec":${kotlinx.serialization.json.JsonPrimitive(schemaOnlySpec)}}}""",
              """{"jsonrpc":"2.0","id":"12","method":"router/in_memory_generation","params":{}}""",
              """{"jsonrpc":"2.0","id":"12b","method":"router/in_memory_generation","params":[1]}""",
              """{"jsonrpc":"2.0","id":"12c","method":"router/in_memory_generation","params":{"spec":"   "}}""")) {
        dummyTransport.simulateReceive(kotlinx.serialization.json.Json.parseToJsonElement(msg))
      }
      assertEquals(28, dummyTransport.sentMessages.size)

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

      // SyncCli trailing flags and CDD_INPUT_DIR / CDD_TRUTH env properties
      System.setProperty("CDD_INPUT_DIR", sdkSchemaDir.absolutePath)
      try {
        System.setProperty("CDD_TRUTH", "class")
        assertEquals(0, org.cdd.SyncCli.sync(arrayOf("sync")))
        System.clearProperty("CDD_TRUTH")
        assertEquals(1, org.cdd.SyncCli.sync(arrayOf("sync", "--truth")))
        assertEquals(0, org.cdd.SyncCli.sync(arrayOf("sync", "--truth", "dao", "--unknown", "-i")))
      } finally {
        System.clearProperty("CDD_TRUTH")
        System.clearProperty("CDD_INPUT_DIR")
      }
      System.setProperty("CDD_OUTPUT", File(outDir, "demo_env_out").absolutePath)
      try {
        assertEquals(0, org.cdd.CddCli.generateDemo(arrayOf("demo", "--flag", "-o")))
        assertEquals(
            0,
            org.cdd.CddCli.generateDemo(
                arrayOf("demo", "--output", File(outDir, "demo_explicit_out").absolutePath)))
      } finally {
        System.clearProperty("CDD_OUTPUT")
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
                arrayOf(
                    "to_sdk",
                    "--unknown",
                    "-o",
                    File(outDir, "env_out").absolutePath,
                    "--input-dir")))
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

      // CddCli generateDocsJson missing file, directory output, webhooks, Arazzo workflows, and
      // failure branch
      assertEquals(
          1,
          org.cdd.CddCli.generateDocsJson(
              arrayOf("to_docs_json", "-i", File(outDir, "nonexistent_doc.json").absolutePath)))
      val validDocSpec = File(outDir, "valid_doc.json")
      validDocSpec.writeText(
          """
          {
            "openapi": "3.2.0",
            "info": { "title": "T", "version": "1" },
            "servers": [{ "url": "" }],
            "paths": {
              "/items": {
                "parameters": [{ "name": "p0", "in": "query", "schema": { "type": "integer" } }],
                "post": {
                  "operationId": "   ",
                  "parameters": [
                    { "name": "p1", "in": "query", "schema": { "type": "integer", "format": "int64" } },
                    { "name": "p2", "in": "query", "schema": { "type": "number" } },
                    { "name": "p3", "in": "query", "schema": { "type": "number", "format": "float" } },
                    { "name": "p4", "in": "query", "schema": { "type": "boolean" } },
                    { "name": "p5", "in": "query", "schema": { "type": "string", "format": "byte" } },
                    { "name": "p6", "in": "query", "schema": { "type": "array", "items": { "type": "string" } } },
                    { "name": "p7", "in": "query", "schema": { "type": "object", "additionalProperties": { "type": "string" } } },
                    { "name": "p8", "in": "query", "schema": { "type": "object" } },
                    { "name": "p9", "in": "query", "schema": false },
                    { "name": "p10", "in": "query", "content": { "text/plain": { "itemSchema": { "type": "string" } } } },
                    { "name": "p11", "in": "query", "content": { "application/vnd.api+json": { "schema": { "${'$'}ref": "#/components/schemas/ItemDto" } } } },
                    { "name": "p12", "in": "query", "content": { "text/plain": {} } },
                    { "name": "p13", "in": "query", "schema": { "type": "string", "format": "date" } },
                    { "name": "p14", "in": "query", "schema": { "type": "string", "format": "date-time" } }
                  ],
                  "requestBody": {
                    "content": {
                      "application/json": {
                        "schema": { "${'$'}ref": "#/components/schemas/ItemDto" }
                      }
                    }
                  },
                  "responses": { "200": { "description": "ok" } }
                },
                "additionalOperations": {
                  "COPY": {
                    "operationId": "---",
                    "requestBody": { "content": {} },
                    "responses": { "200": { "description": "ok" } }
                  }
                }
              }
            },
            "webhooks": {
              "itemCreated": {
                "post": {
                  "operationId": "onItemCreated",
                  "requestBody": {
                    "content": {
                      "text/plain": {
                        "itemSchema": { "type": "string" }
                      }
                    }
                  },
                  "responses": { "200": { "description": "ok" } }
                }
              }
            },
            "components": {
              "schemas": {
                "ItemDto": { "type": "object", "properties": { "id": { "type": "string" } } }
              }
            }
          }
          """
              .trimIndent())
      val docsTargetDir = File(outDir, "docs_target_dir")
      docsTargetDir.mkdirs()
      assertEquals(
          0,
          org.cdd.CddCli.generateDocsJson(
              arrayOf(
                  "to_docs_json",
                  "-i",
                  validDocSpec.absolutePath,
                  "-o",
                  docsTargetDir.absolutePath)))
      assertTrue(File(docsTargetDir, "docs.json").exists())

      val blockedDocsDir = File(outDir, "blocked_docs_dir")
      File(blockedDocsDir, "docs.json").mkdirs()
      assertEquals(
          1,
          org.cdd.CddCli.generateDocsJson(
              arrayOf(
                  "to_docs_json",
                  "-i",
                  validDocSpec.absolutePath,
                  "-o",
                  blockedDocsDir.absolutePath)))

      val arazzoDocSpec = File(outDir, "arazzo_doc.json")
      arazzoDocSpec.writeText(
          """
          {
            "arazzo": "1.1.0",
            "info": { "title": "Flow", "version": "1.0.0" },
            "sourceDescriptions": [{ "name": "api", "url": "./openapi.json" }],
            "workflows": [
              {
                "workflowId": "checkout-order",
                "steps": [{ "stepId": "s1", "operationId": "createOrder" }]
              }
            ]
          }
          """
              .trimIndent())
      val arazzoDocsOut = File(outDir, "arazzo_docs.json")
      assertEquals(
          0,
          org.cdd.CddCli.generateDocsJson(
              arrayOf(
                  "to_docs_json",
                  "-i",
                  arazzoDocSpec.absolutePath,
                  "-o",
                  arazzoDocsOut.absolutePath)))
      assertTrue(arazzoDocsOut.readText().contains("WorkflowOrchestrator"))
      assertEquals(
          0,
          org.cdd.CddCli.generateDocsJson(
              arrayOf(
                  "to_docs_json",
                  "-i",
                  arazzoDocSpec.absolutePath,
                  "--no-imports",
                  "--no-wrapping")))

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
    } finally {
      outDir.deleteRecursively()
    }
  }
}
