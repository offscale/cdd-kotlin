import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MainTest {

    private fun withUserDir(dir: Path, block: () -> Unit) {
        val original = System.getProperty("user.dir")
        try {
            System.setProperty("user.dir", dir.toString())
            block()
        } finally {
            System.setProperty("user.dir", original)
        }
    }

    @Test
    fun `main generates scaffold in demo mode`(@TempDir tempDir: Path) {
        withUserDir(tempDir) {
            main()
            val outputDir = File(tempDir.toFile(), "generated-project")
            assertTrue(File(outputDir, "build.gradle.kts").exists())
            assertTrue(File(outputDir, "composeApp/build.gradle.kts").exists())
        }
    }

    @Test
    fun `main handles generation failures gracefully`(@TempDir tempDir: Path) {
        withUserDir(tempDir) {
            val blocker = File(tempDir.toFile(), "generated-project")
            blocker.writeText("not a directory")
            main()
            assertTrue(blocker.isFile)
        }
    }
}
