actual fun getEnvVar(name: String): String? = if (name == "PWD") System.getProperty("user.dir") else System.getenv(name)

actual fun readFile(path: String): String = java.io.File(path).readText()

actual fun writeToFile(path: String, content: String) {
    val file = java.io.File(path)
    file.parentFile?.mkdirs()
    file.writeText(content)
}
