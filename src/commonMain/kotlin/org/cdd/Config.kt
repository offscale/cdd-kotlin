/** Configuration data models for cdd-kotlin code generation and CLI options. */
package org.cdd

/** Configuration options for the CDD Generator. */
data class Config(
    /** Input OpenAPI specification path or directory. */
    val inputPath: String,
    /** Output target directory path. */
    val outputDir: String,
    /** Whether to omit GitHub Actions workflow generation. */
    val noGithubActions: Boolean = false,
    /**
     * Whether to omit installable package configuration (`build.gradle.kts`,
     * `settings.gradle.kts`).
     */
    val noInstallablePackage: Boolean = false,
    /** Whether to generate only test suites. */
    val tests: Boolean = false,
    /** Target input directory for test generation or synchronization. */
    val inputDir: String = ""
)
