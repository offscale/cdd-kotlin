package org.cdd

/** Configuration options for the CDD Generator. */
data class Config(
    val inputPath: String,
    val outputDir: String,
    val noGithubActions: Boolean = false,
    val noInstallablePackage: Boolean = false,
    val tests: Boolean = false
)
