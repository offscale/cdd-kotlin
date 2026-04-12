import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

actual fun getEnvVar(name: String): String? = null
actual fun readFile(path: String): String {
    try {
        val p = Path(path)
        val source = SystemFileSystem.source(p).buffered()
        val text = source.readString()
        source.close()
        return text
    } catch (e: Throwable) {
        throw e
    }
}
actual fun writeToFile(path: String, content: String) {
    try {
        val p = Path(path)
    } catch (e: Throwable) {}
}
