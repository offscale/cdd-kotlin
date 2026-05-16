expect fun getEnvVar(name: String): String?

expect fun readFile(path: String): String

expect fun writeToFile(path: String, content: String)

expect fun generateOpenApi(inputDir: String, outputFile: String)
