package platform

import getEnvVar
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import writeToFile
import readFile
import java.io.File
import java.nio.file.Files

class PlatformJvmTest {

    @Test
    fun testGetEnvVar() {
        val userDir = System.getProperty("user.dir")
        assertEquals(userDir, getEnvVar("PWD"))
        // Test an environment variable that is likely not set to check for null or value
        val testEnv = getEnvVar("NON_EXISTENT_ENV_VAR")
        assertEquals(null, testEnv)
    }

    @Test
    fun testReadWriteFile() {
        val tempDir = Files.createTempDirectory("platform_test")
        val file = File(tempDir.toFile(), "test.txt")
        
        writeToFile(file.absolutePath, "test content")
        assertTrue(file.exists())
        
        val content = readFile(file.absolutePath)
        assertEquals("test content", content)
        
        file.delete()
        tempDir.toFile().delete()
    }
}