/**
 * Bi-directional synchronization CLI entrypoint for aligning code definitions and OpenAPI specs.
 */
package org.cdd

import getEnvVar
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
    var truth = getEnvVar("CDD_TRUTH") ?: ""
    var inputDir = getEnvVar("CDD_INPUT_DIR") ?: "."

    var i = 1
    while (i < args.size) {
      val arg = args[i]
      if (arg == "--truth") {
        if (i + 1 < args.size) truth = args[++i]
      } else if (arg == "-i" || arg == "--input-dir") {
        if (i + 1 < args.size) inputDir = args[++i]
      }
      i++
    }

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
