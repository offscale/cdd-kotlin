actual fun writeToFile(path: String, content: String) {
    val file = java.io.File(path)
    file.parentFile?.mkdirs()
    file.writeText(content)
}
