/**
 * Bi-directional synchronization CLI entrypoint for aligning code definitions and OpenAPI specs.
 */
package org.cdd

import performSync

/** Provides the Bi-Directional Synchronization API. */
object SyncCli {
  /**
   * Synchronizes code definitions based on a source of truth.
   *
   * @param args Command-line arguments.
   * @return Exit code (0 for success, 1 for error).
   */
  fun sync(args: Array<String>): Int {
    val flags = CliArgumentParser.parse(args, defaultInputDir = ".", allowInputDirOnShortI = true)
    val truth = flags.truth
    val inputDir = flags.inputDir

    if (truth.isEmpty()) {
      println("Missing --truth <class|dao|arazzo|openapi>")
      return 1
    }

    println("Syncing $inputDir using truth: $truth")
    return performSync(inputDir, truth)
        .fold(
            onSuccess = { 0 },
            onFailure = { e ->
              println("Synchronization failed: ${e.message}")
              1
            })
  }
}
