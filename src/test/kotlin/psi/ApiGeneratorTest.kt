package psi

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiGeneratorTest {

  @Test
  fun testGenerateOpenApi() {
    val tempDir = Files.createTempDirectory("api-generator-test").toFile()
    val inputFile = File(tempDir, "TestApi.kt")
    inputFile.writeText(
        """
            package org.example
            
            /**
             * @openapi 3.2.0
             * @info {"title": "Test API", "version": "1.0.0"}
             */
            interface TestApi
            
            data class Person(val name: String)
        """
            .trimIndent())

    val outputFile = File(tempDir, "out.json")

    ApiGenerator.generateOpenApi(tempDir.absolutePath, outputFile.absolutePath)

    assertTrue(outputFile.exists())
    val content = outputFile.readText()
    assertTrue(content.contains("Test API"))
    assertTrue(content.contains("Person"))

    tempDir.deleteRecursively()
  }

  @Test
  fun testGenerateOpenApiInvalidDir() {
    assertThrows(IllegalArgumentException::class.java) {
      ApiGenerator.generateOpenApi("/nonexistent_directory_12345", "out.json")
    }
  }
}
