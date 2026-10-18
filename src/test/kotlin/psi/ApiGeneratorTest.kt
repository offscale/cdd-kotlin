/** Unit and integration tests in [ApiGeneratorTest]. */
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
             * @openapi {"openapi": "3.3.0"}
             * @info {"title": "Test API", "version": "1.0.0"}
             */
            interface TestApi
            
            data class Person(val name: String)
        """
            .trimIndent())

    val inputFile2 = File(tempDir, "TestApi2.kt")
    inputFile2.writeText(
        """
            package org.example
            data class Company(val name: String)
        """
            .trimIndent())

    val outputFile = File(tempDir, "out.json")

    ApiGenerator.generateToOpenApi(tempDir.absolutePath, outputFile.absolutePath)

    assertTrue(outputFile.exists())
    val content = outputFile.readText()
    assertTrue(content.contains("Test API"))
    assertTrue(content.contains("Person"))

    tempDir.deleteRecursively()
  }

  @Test
  fun testGenerateOpenApiInvalidDir() {
    val res = ApiGenerator.generateToOpenApi("/nonexistent_directory_12345", "out.json")
    assertTrue(res.isFailure)
  }

  @Test
  fun testGenerateOpenApiFileInsteadOfDir() {
    val tempFile = File.createTempFile("api-generator-test-file", ".kt")
    val res = ApiGenerator.generateToOpenApi(tempFile.absolutePath, "out.json")
    assertTrue(res.isFailure)
    tempFile.delete()
  }

  @Test
  fun testGenerateOpenApiDefaultsAndExtensions() {
    val tempDir = Files.createTempDirectory("api-generator-defaults").toFile()
    val inputFile = File(tempDir, "TestApi.kt")
    inputFile.writeText(
        """
            package org.example
            
            interface TestApi
            
            data class Person(val name: String)
        """
            .trimIndent())

    val ignoredFile = File(tempDir, "ignored.txt")
    ignoredFile.writeText("Not a kotlin file")

    val outputFile = File(tempDir, "out.json")

    ApiGenerator.generateToOpenApi(tempDir.absolutePath, outputFile.absolutePath)

    assertTrue(outputFile.exists())
    val content = outputFile.readText()
    assertTrue(content.contains("Generated API"))
    assertTrue(content.contains("3.2.0"))
    assertTrue(content.contains("Person"))

    tempDir.deleteRecursively()
  }

  @Test
  fun testGenerateOpenApiEmptyDir() {
    val tempDir = Files.createTempDirectory("api-generator-empty").toFile()
    val outputFile = File(tempDir, "out.json")

    ApiGenerator.generateToOpenApi(tempDir.absolutePath, outputFile.absolutePath)

    assertTrue(outputFile.exists())
    val content = outputFile.readText()
    // It should still generate a valid, albeit empty, OpenAPI doc.
    assertTrue(content.contains("Generated API"))

    tempDir.deleteRecursively()
  }
}
