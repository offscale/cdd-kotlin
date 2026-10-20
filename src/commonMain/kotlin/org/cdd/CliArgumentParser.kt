/** Shared, strongly-typed CLI flag and environment variable parser for `cdd-kotlin` commands. */
package org.cdd

import getEnvVar

/**
 * Strongly-typed representation of parsed CLI options across `CddCli` and `SyncCli`.
 *
 * @property output Target output directory or file path (`-o` / `--output` or `CDD_OUTPUT`).
 * @property input Target input specification or directory path (`-i` / `--input` or `CDD_INPUT`).
 * @property inputDir Target input directory path (`--input-dir` or `CDD_INPUT_DIR`).
 * @property truth Synchronization truth target (`--truth` or `CDD_TRUTH`).
 * @property noGithubActions Whether `--no-github-actions` or `CDD_NO_GITHUB_ACTIONS` is enabled.
 * @property noInstallablePackage Whether `--no-installable-package` or `CDD_NO_INSTALLABLE_PACKAGE`
 *   is enabled.
 * @property tests Whether `--tests` or `CDD_TESTS` is enabled.
 * @property noImports Whether `--no-imports` or `CDD_NO_IMPORTS` is enabled.
 * @property noWrapping Whether `--no-wrapping` or `CDD_NO_WRAPPING` is enabled.
 */
data class ParsedCliFlags(
    val output: String,
    val input: String,
    val inputDir: String,
    val truth: String,
    val noGithubActions: Boolean,
    val noInstallablePackage: Boolean,
    val tests: Boolean,
    val noImports: Boolean,
    val noWrapping: Boolean
)

/** Parses CLI argument arrays and environment variable fallbacks into [ParsedCliFlags]. */
object CliArgumentParser {
  /**
   * Parses [args] starting at index `1` and merges with `CDD_*` environment variable defaults.
   *
   * @param args Raw CLI argument array.
   * @param defaultOutput Fallback value for [ParsedCliFlags.output] if `CDD_OUTPUT` is unset.
   * @param defaultInputDir Fallback value for [ParsedCliFlags.inputDir] if `CDD_INPUT_DIR` is
   *   unset.
   * @param allowInputDirOnShortI Whether `-i` also sets [ParsedCliFlags.inputDir] (used by `sync`).
   * @return The parsed [ParsedCliFlags].
   */
  fun parse(
      args: Array<String>,
      defaultOutput: String = ".",
      defaultInputDir: String = "",
      allowInputDirOnShortI: Boolean = false
  ): ParsedCliFlags {
    var output = getEnvVar("CDD_OUTPUT") ?: defaultOutput
    var input = getEnvVar("CDD_INPUT") ?: ""
    var inputDir = getEnvVar("CDD_INPUT_DIR") ?: defaultInputDir
    var truth = getEnvVar("CDD_TRUTH") ?: ""
    var noGithubActions = isEnvFlagEnabled("CDD_NO_GITHUB_ACTIONS")
    var noInstallablePackage = isEnvFlagEnabled("CDD_NO_INSTALLABLE_PACKAGE")
    var tests = isEnvFlagEnabled("CDD_TESTS")
    var noImports = isEnvFlagEnabled("CDD_NO_IMPORTS")
    var noWrapping = isEnvFlagEnabled("CDD_NO_WRAPPING")

    var i = 1
    while (i < args.size) {
      val arg = args[i]
      val hasNext = i + 1 < args.size
      when {
        (arg == "-o" || arg == "--output") && hasNext -> output = args[++i]
        (arg == "-i" || arg == "--input") && hasNext -> {
          val nextVal = args[++i]
          input = nextVal
          if (allowInputDirOnShortI) inputDir = nextVal
        }
        arg == "--input-dir" && hasNext -> inputDir = args[++i]
        arg == "--truth" && hasNext -> truth = args[++i]
        arg == "--no-github-actions" -> noGithubActions = true
        arg == "--no-installable-package" -> noInstallablePackage = true
        arg == "--tests" -> tests = true
        arg == "--no-imports" -> noImports = true
        arg == "--no-wrapping" -> noWrapping = true
      }
      i++
    }

    return ParsedCliFlags(
        output = output,
        input = input,
        inputDir = inputDir,
        truth = truth,
        noGithubActions = noGithubActions,
        noInstallablePackage = noInstallablePackage,
        tests = tests,
        noImports = noImports,
        noWrapping = noWrapping)
  }

  private fun isEnvFlagEnabled(name: String): Boolean {
    val raw = getEnvVar(name)
    return raw == "true" || raw == "1"
  }
}
