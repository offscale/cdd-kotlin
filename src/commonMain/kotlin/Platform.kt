expect fun getEnvVar(name: String): String?

expect fun readFile(path: String): String

expect fun writeToFile(path: String, content: String)

expect fun generateToOpenApi(inputDir: String, outputFile: String)

expect fun generateSdkCode(inputJson: String, outputDir: String, packageName: String)

expect fun generateServerCode(
    inputJson: String,
    outputDir: String,
    packageName: String,
    withTests: Boolean
)

expect fun performSync(inputDir: String, truth: String)
