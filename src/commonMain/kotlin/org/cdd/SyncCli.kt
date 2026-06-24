package org.cdd

import getEnvVar

/** Provides the Bi-Directional Synchronization API. */
object SyncCli {
  /**
   * Synchronizes code definitions based on a source of truth.
   *
   * @param args Command-line arguments.
   * @return Exit code (0 for success).
   */
  fun sync(args: Array<String>): Int {
    var truth = ""
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
      println("Missing --truth <class|sqlalchemy|function>")
      return 1
    }

    println("Syncing $inputDir using truth: $truth")
    // Implementation logic will delegate to the generator
    return 0
  }
}
