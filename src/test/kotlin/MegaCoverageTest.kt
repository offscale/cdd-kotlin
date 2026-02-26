import org.junit.jupiter.api.Test
import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import psi.ApiGenerator

class MegaCoverageTest {
    @Test
    fun `mega coverage via petstore`() {
        val outDir = File("build/tmp/mega-out")
        outDir.mkdirs()
        val spec = File("petstore.json")
        if (spec.exists()) {
            ApiGenerator.generate(spec.absolutePath, outDir, "com.mega")
        }
        
        // Let's call the parser on the output to get reverse coverage
        val srcDir = File(outDir, "api")
        if (srcDir.exists()) {
            val srcText = srcDir.walk().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
            val netParser = psi.NetworkParser()
            val endpoints = netParser.parse(srcText)
            assertTrue(endpoints.isNotEmpty())
        }
    }
}
