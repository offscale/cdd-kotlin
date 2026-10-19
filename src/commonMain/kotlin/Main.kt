/** Main entry point and CLI dispatcher for the `cdd-kotlin` toolchain. */
import domain.CddError
import domain.toFailure

/**
 * Main entry point for the cdd-kotlin CLI.
 *
 * @param args Command-line arguments.
 */
fun main(args: Array<String>) {
  runCliResult(args)
}

/**
 * Runs the CLI logic and returns a [kotlin.Result] wrapping `0` on success or a [CddError.CliError]
 * on non-zero exit code.
 *
 * @param args Command-line arguments.
 * @return A [kotlin.Result] containing `0` on success or a [CddError.CliError] on failure.
 */
fun runCliResult(args: Array<String>): kotlin.Result<Int> {
  val exitCode = runCli(args)
  return if (exitCode == 0) {
    kotlin.Result.success(0)
  } else {
    CddError.CliError("CLI exited with code $exitCode", exitCode).toFailure()
  }
}

/**
 * Runs the CLI logic and returns an exit code.
 *
 * @param args Command-line arguments.
 * @return the exit code (0 for success, non-zero for failure).
 */
fun runCli(args: Array<String>): Int {
  if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
    return printHelp(args)
  }

  if (args.contains("--version") || args.contains("-v")) {
    println("0.0.4")
    return 0
  }

  if (args[0] == "from_arazzo") {
    val target = if (args.size > 1 && !args[1].startsWith("-")) args[1] else "to_sdk"
    return org.cdd.CddCli.generateFromArazzo(args, target)
  }

  if (args[0] == "to_arazzo") {
    return org.cdd.CddCli.generateToArazzo(args)
  }

  val command =
      if (args.size > 1 && args[0] == "from_openapi") {
        if (args[1].startsWith("-")) "to_sdk" else args[1]
      } else {
        args[0]
      }

  if (command == "to_sdk" || command == "to_sdk_cli" || command == "to_server") {
    return org.cdd.CddCli.generateFromOpenApi(args, command)
  }

  if (command == "to_openapi") {
    return org.cdd.CddCli.generateToOpenApi(args)
  }

  if (command == "sync") {
    return org.cdd.SyncCli.sync(args)
  }

  if (command == "mcp") {
    return org.cdd.CddCli.runMcpServer(args)
  }

  if (command == "serve_json_rpc") {
    return org.cdd.CddCli.serveJsonRpc(args)
  }

  if (command == "to_docs_json") {
    return org.cdd.CddCli.generateDocsJson(args)
  }

  if (command == "demo") {
    return org.cdd.CddCli.generateDemo(args)
  }

  println("Unknown command: $command")
  return 1
}

/**
 * Prints context-aware CLI help text for the root command or a specific subcommand.
 *
 * @param args Command-line arguments passed to the CLI.
 * @return Exit code `0`.
 */
private fun printHelp(args: Array<String>): Int {
  val positional = args.filter { !it.startsWith("-") }
  val cmd = if (positional.isNotEmpty()) positional[0] else ""
  val sub = if (positional.size > 1) positional[1] else ""
  val key = if (sub.isNotEmpty()) "$cmd $sub" else cmd

  if (key == "from_openapi to_sdk" || key == "to_sdk") {
    val path = if (cmd == "to_sdk") "to_sdk" else "from_openapi to_sdk"
    printOpenApiTargetHelp(path, "Kotlin Multiplatform Client SDK")
    return 0
  }
  if (key == "from_openapi to_sdk_cli" || key == "to_sdk_cli") {
    val path = if (cmd == "to_sdk_cli") "to_sdk_cli" else "from_openapi to_sdk_cli"
    printOpenApiTargetHelp(path, "Kotlin Multiplatform Client SDK + Clikt CLI")
    return 0
  }
  if (key == "from_openapi to_server" || key == "to_server") {
    val path = if (cmd == "to_server") "to_server" else "from_openapi to_server"
    printOpenApiTargetHelp(path, "Ktor Server, Exposed DAOs, Faker Seeder, IdP, and Webhooks")
    return 0
  }
  if (cmd == "from_openapi") {
    println("Usage: cdd-kotlin from_openapi [to_sdk|to_sdk_cli|to_server] [options]")
    println("")
    println(
        "Generate Kotlin Multiplatform SDK, CLI, or Ktor Server code from an OpenAPI (2.0 / 3.0 / 3.1 / 3.2) specification.")
    println("")
    println("Subcommands:")
    println("  to_sdk          Generate a Kotlin Multiplatform Client SDK (default)")
    println("  to_sdk_cli      Generate a Kotlin Multiplatform Client SDK and Clikt CLI")
    println(
        "  to_server       Generate a Ktor Server with Exposed ORM DAOs, Faker seeder, IdP, and webhooks")
    printCommonGenOptions(includeInputDir = true)
    return 0
  }
  if (cmd == "to_openapi") {
    println("Usage: cdd-kotlin to_openapi -i <source_dir> [-o <spec.json>]")
    println("")
    println("Extract an OpenAPI 3.2.0 specification from Kotlin PSI source files.")
    println("")
    println("Options:")
    println("  -i, --input <dir>       Input directory containing Kotlin source files (.kt)")
    println("  -o, --output <file>     Output OpenAPI JSON file path (default: out.json)")
    println("  -h, --help              Show this help message")
    println("")
    println("Environment Variables:")
    println("  CDD_INPUT               Default input source directory")
    println("  CDD_OUTPUT              Default output specification path")
    return 0
  }
  if (key == "from_arazzo to_sdk") {
    printArazzoTargetHelp("from_arazzo to_sdk", "Kotlin Multiplatform Arazzo Workflow SDK")
    return 0
  }
  if (key == "from_arazzo to_sdk_cli") {
    printArazzoTargetHelp(
        "from_arazzo to_sdk_cli", "Kotlin Multiplatform Arazzo Workflow SDK + CLI")
    return 0
  }
  if (key == "from_arazzo to_server") {
    printArazzoTargetHelp(
        "from_arazzo to_server", "Ktor Arazzo Workflow Server Routes, Mock Server, and E2E Tests")
    return 0
  }
  if (cmd == "from_arazzo") {
    println("Usage: cdd-kotlin from_arazzo [to_sdk|to_sdk_cli|to_server] [options]")
    println("")
    println(
        "Generate Kotlin workflow orchestrators, CLI runners, or Ktor server routes from an Arazzo 1.1.0 specification.")
    println("")
    println("Subcommands:")
    println("  to_sdk          Generate a Kotlin Multiplatform Workflow SDK (default)")
    println("  to_sdk_cli      Generate a Kotlin Multiplatform Workflow SDK and CLI runner")
    println("  to_server       Generate Ktor workflow routes, stateful mock server, and E2E tests")
    printCommonGenOptions(includeInputDir = false)
    return 0
  }
  if (cmd == "to_arazzo") {
    println("Usage: cdd-kotlin to_arazzo -i <source_dir_or_file> [-o <arazzo.json|yaml>]")
    println("")
    println("Extract an Arazzo 1.1.0 workflow specification from Kotlin workflow source code.")
    println("")
    println("Options:")
    println("  -i, --input <path>      Input Kotlin workflow source directory or .kt file")
    println(
        "  -o, --output <file>     Output Arazzo 1.1.0 specification path (.json or .yaml, default: arazzo.json)")
    println("  -h, --help              Show this help message")
    println("")
    println("Environment Variables:")
    println("  CDD_INPUT               Default input workflow source path")
    println("  CDD_OUTPUT              Default output Arazzo specification path")
    return 0
  }
  if (cmd == "sync") {
    println("Usage: cdd-kotlin sync -i <project_dir> [--truth <class|dao|arazzo|openapi>]")
    println("")
    println(
        "Bi-directionally synchronize Kotlin data classes, Exposed DAOs, Arazzo workflows, and OpenAPI specifications.")
    println("")
    println("Options:")
    println("  -i, --input-dir <dir>   Target project directory to synchronize")
    println(
        "  --truth <source>        Authoritative source of truth: class, dao, arazzo, or openapi (default: class)")
    println("  -h, --help              Show this help message")
    println("")
    println("Environment Variables:")
    println("  CDD_INPUT_DIR           Default project directory to synchronize")
    return 0
  }
  if (cmd == "to_docs_json") {
    println("Usage: cdd-kotlin to_docs_json -i <spec.json|yaml> [-o <docs.json>] [options]")
    println("")
    println(
        "Generate JSON documentation with Ktor client code snippets for an OpenAPI specification.")
    println("")
    println("Options:")
    println("  -i, --input <file>      Input OpenAPI specification file (.json or .yaml)")
    println("  -o, --output <file>     Optional output file for generated documentation JSON")
    println("  --no-imports            Omit import statements from generated Ktor snippets")
    println("  --no-wrapping           Omit suspend fun main() wrapper from generated snippets")
    println("  -h, --help              Show this help message")
    println("")
    println("Environment Variables:")
    println("  CDD_INPUT, INPUT        Default input OpenAPI specification path")
    println("  CDD_OUTPUT              Default output documentation JSON path")
    println("  CDD_NO_IMPORTS          Set to true/1 to omit imports")
    println("  CDD_NO_WRAPPING         Set to true/1 to omit function wrapping")
    return 0
  }
  if (cmd == "serve_json_rpc") {
    println("Usage: cdd-kotlin serve_json_rpc")
    println("")
    println("Start the JSON-RPC 2.0 server over standard input/output (stdio).")
    println("")
    println("Options:")
    println("  -h, --help              Show this help message")
    return 0
  }
  if (cmd == "mcp") {
    println("Usage: cdd-kotlin mcp")
    println("")
    println(
        "Start the Model Context Protocol (MCP) JSON-RPC peer over standard input/output (stdio).")
    println("")
    println("Registered MCP Methods:")
    println(
        "  tools/cdd_generate          Validate and generate artifacts from an inline OpenAPI spec")
    println("  tools/cdd_inspect_schema    Inspect component schema names and metadata")
    println(
        "  tools/cdd_sync_schema       Synchronize a project directory against a source of truth")
    println("  resources/ast_query         Query AST symbols across the workspace")
    println("  router/in_memory_generation Generate MCP bridge files in memory")
    println("")
    println("Options:")
    println("  -h, --help                  Show this help message")
    return 0
  }
  if (cmd == "demo") {
    println("Usage: cdd-kotlin demo")
    println("")
    println(
        "Generate a sample Kotlin Multiplatform & Jetpack Compose scaffold project in ./generated-project.")
    println("")
    println("Options:")
    println("  -h, --help              Show this help message")
    return 0
  }
  println("cdd-kotlin CLI")
  println("Usage:")
  println("  cdd-kotlin [subcommand] [options]")
  println("")
  println("Subcommands:")
  println("  from_openapi    Generate code from an OpenAPI specification.")
  println("  to_openapi      Generate an OpenAPI specification from source code.")
  println("  from_arazzo     Generate SDK, CLI, or Server code from an Arazzo 1.1.0 specification.")
  println(
      "  to_arazzo       Generate an Arazzo 1.1.0 specification from Kotlin workflow source code.")
  println(
      "  sync            Synchronize Kotlin DTOs, Exposed DAOs, Arazzo workflows, and OpenAPI specs.")
  println(
      "  to_docs_json    Generate JSON documentation with code snippets for an OpenAPI specification.")
  println("  serve_json_rpc  Run the JSON RPC server via stdio.")
  println("  mcp             Run the Model Context Protocol server via stdio.")
  println("  demo            Generate a sample Kotlin Multiplatform scaffold project.")
  println("")
  println("Options:")
  println("  --help, -h      Show this help message")
  println("  --version, -v   Show version information")
  println("")
  println("Examples:")
  println("  cdd-kotlin to_docs_json --no-imports --no-wrapping -i spec.json -o docs.json")
  println(
      "  cdd-kotlin from_openapi to_sdk_cli -i spec.json -o target_directory [--no-github-actions] [--no-installable-package] [--tests]")
  println(
      "  cdd-kotlin from_openapi to_sdk -i spec.json -o target_directory [--no-github-actions] [--no-installable-package] [--tests]")
  println(
      "  cdd-kotlin from_arazzo to_sdk -i arazzo.json -o target_directory [--no-github-actions] [--no-installable-package] [--tests]")
  println("  cdd-kotlin to_arazzo -i src_directory -o arazzo.json")
  return 0
}

/**
 * Prints help text for a specific `from_openapi` target.
 *
 * @param commandPath Full command invocation string.
 * @param description Human-readable description of the target artifact.
 */
private fun printOpenApiTargetHelp(commandPath: String, description: String) {
  println("Usage: cdd-kotlin $commandPath -i <spec.json|yaml> [-o <output_dir>] [options]")
  println("")
  println("Generate $description from an OpenAPI specification.")
  printCommonGenOptions(includeInputDir = true)
}

/**
 * Prints help text for a specific `from_arazzo` target.
 *
 * @param commandPath Full command invocation string.
 * @param description Human-readable description of the target artifact.
 */
private fun printArazzoTargetHelp(commandPath: String, description: String) {
  println("Usage: cdd-kotlin $commandPath -i <arazzo.json|yaml> [-o <output_dir>] [options]")
  println("")
  println("Generate $description from an Arazzo 1.1.0 specification.")
  printCommonGenOptions(includeInputDir = false)
}

/**
 * Prints shared code-generation CLI options and environment variables.
 *
 * @param includeInputDir Whether to document the `--input-dir` option.
 */
private fun printCommonGenOptions(includeInputDir: Boolean) {
  println("")
  println("Options:")
  println("  -i, --input <file>          Input specification file (.json or .yaml)")
  if (includeInputDir) {
    println("  --input-dir <dir>           Input directory containing specification files")
  }
  println("  -o, --output <dir>          Output directory for generated project (default: out)")
  println("  --no-github-actions         Skip generating .github/workflows/ci.yml")
  println("  --no-installable-package    Skip generating Gradle build scaffolding")
  println("  --tests                     Generate companion unit, mock, and E2E test suites")
  println("  -h, --help                  Show this help message")
  println("")
  println("Environment Variables:")
  println("  CDD_INPUT                   Default input specification file")
  if (includeInputDir) {
    println("  CDD_INPUT_DIR               Default input specification directory")
  }
  println("  CDD_OUTPUT                  Default output directory")
  println("  CDD_NO_GITHUB_ACTIONS       Set to true/1 to skip GitHub Actions generation")
  println("  CDD_NO_INSTALLABLE_PACKAGE  Set to true/1 to skip Gradle package scaffolding")
  println("  CDD_TESTS                   Set to true/1 to generate test suites")
}
