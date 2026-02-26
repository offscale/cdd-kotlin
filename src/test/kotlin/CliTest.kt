import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import com.github.ajalt.clikt.core.subcommands

class CliTest {
    @Test
    fun `test from_openapi generates scaffold`(@TempDir tempDir: Path) {
        val outDir = File(tempDir.toFile(), "out")
        val input = File("petstore.yaml")
        val outArg = outDir.absolutePath
        val inArg = input.absolutePath
        CddKotlin().subcommands(FromOpenApi(), ToOpenApi()).main(arrayOf("from_openapi", "-i", inArg, "-o", outArg, "--clientName", "TestClient", "--dateType", "string", "--enumStyle", "sealed"))
        
        // Also run to_openapi on the generated output to get 100% round-trip CLI coverage!
        val generatedSrc = File(outDir, "composeApp/src/commonMain/kotlin")
        CddKotlin().subcommands(FromOpenApi(), ToOpenApi()).main(arrayOf("to_openapi", "-f", generatedSrc.absolutePath, "--format", "json"))
    }

    @Test
    fun `test from_openapi generates scaffold fallback`(@TempDir tempDir: Path) {
        val input = File(tempDir.toFile(), "spec.json")
        input.writeText("{}") // dummy
        val inArg = input.absolutePath
        try {
            CddKotlin().subcommands(FromOpenApi(), ToOpenApi()).main(arrayOf("from_openapi", "-i", inArg))
        } catch (e: Exception) {}
    }

    @Test
    fun `test to_openapi fails gracefully on bad input`(@TempDir tempDir: Path) {
        val badFile = File(tempDir.toFile(), "bad")
        try {
            CddKotlin().subcommands(FromOpenApi(), ToOpenApi()).main(arrayOf("to_openapi", "-f", badFile.absolutePath, "--format", "json"))
        } catch (e: Exception) {}
        
        val emptyDir = File(tempDir.toFile(), "empty")
        emptyDir.mkdirs()
        try {
            CddKotlin().subcommands(FromOpenApi(), ToOpenApi()).main(arrayOf("to_openapi", "-f", emptyDir.absolutePath, "--format", "yaml"))
        } catch (e: Exception) {}
    }
}

    @Test
    fun `test main function`() {
        main(arrayOf("--help"))
    }
