package org.cdd

data class Config(
    val inputPath: String,
    val outputDir: String,
    val noGithubActions: Boolean = false,
    val noInstallablePackage: Boolean = false,
    val tests: Boolean = false
)
