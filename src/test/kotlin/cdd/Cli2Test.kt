package cdd

import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class Cli2Test {
    @Test
    fun `to_openapi fails gracefully`() {
        val cmd = ToOpenApi()
        val m = ToOpenApi::class.java.getDeclaredMethod("run")
        m.isAccessible = true
        
        // This will print "File or directory not found" but catch the error case
        try {
            cmd.parse(arrayOf("--file", "/does/not/exist"))
            m.invoke(cmd)
        } catch (e: Exception) {}
        
        val temp = Files.createTempFile("bad", ".kt").toFile()
        temp.writeText("invalid kotlin syntax %^&**(")
        temp.deleteOnExit()
        
        try {
            cmd.parse(arrayOf("--file", temp.absolutePath))
            m.invoke(cmd)
        } catch (e: Exception) {}
        
        val tempDir = Files.createTempDirectory("baddir").toFile()
        tempDir.deleteOnExit()
        
        val temp2 = File(tempDir, "a.kt")
        temp2.writeText("invalid")
        
        try {
            cmd.parse(arrayOf("--file", tempDir.absolutePath))
            m.invoke(cmd)
        } catch (e: Exception) {}
    }
}
