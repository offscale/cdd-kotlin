/**
 * Main entry point for the cdd-kotlin CLI.
 *
 * @param args Command-line arguments.
 */
fun main(args: Array<String>) {
  val exitCode = runCli(args)
  if (exitCode != 0) {
    throw RuntimeException("CLI exited with code $exitCode")
  }
}

/**
 * Runs the CLI logic and returns an exit code.
 *
 * @param args Command-line arguments.
 * @return the exit code (0 for success, non-zero for failure).
 */
fun runCli(args: Array<String>): Int {
  try {
    if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
      println("cdd-kotlin CLI")
      println("Usage:")
      println("  cdd-kotlin [subcommand] [options]")
      println("\nSubcommands:")
      println("  from_openapi    Generate code from an OpenAPI specification.")
      println("  to_openapi      Generate an OpenAPI specification from source code.")
      println("  mcp             Run the Model Context Protocol server via stdio.")
      println(
          "  to_docs_json    Generate JSON documentation with code snippets for an OpenAPI specification.")
      println("\nOptions:")
      println("  --help, -h      Show this help message")
      println("  --version, -v   Show version information")
      println("\nExamples:")
      println("  cdd-kotlin to_docs_json --no-imports --no-wrapping -i spec.json -o docs.json")
      println(
          "  cdd-kotlin from_openapi to_sdk_cli -i spec.json -o target_directory [--no-github-actions] [--no-installable-package] [--tests]")
      println(
          "  cdd-kotlin from_openapi to_sdk -i spec.json -o target_directory [--no-github-actions] [--no-installable-package] [--tests]")
      return 0
    }

    if (args.contains("--version")) {
      println("0.0.2")
      return 0
    }

    val command = if (args.size > 1 && args[0] == "from_openapi") args[1] else args[0]

    if (command == "to_sdk" || command == "to_sdk_cli") {
      return org.cdd.CddCli.generateFromOpenApi(args)
    }

    if (command == "to_openapi") {
      return org.cdd.CddCli.generateToOpenApi(args)
    }

    if (command == "mcp") {
      return org.cdd.CddCli.runMcpServer(args)
    }

    if (command == "to_docs_json") {
      return org.cdd.CddCli.generateDocsJson(args)
    }

    if (command == "demo") {
      return org.cdd.CddCli.generateDemo(args)
    }

    println("Unknown command: $command")
    return 1
  } catch (e: Throwable) {
    println("Execution failed: " + e.message)
    e.printStackTrace()
    return 1
  }
}
