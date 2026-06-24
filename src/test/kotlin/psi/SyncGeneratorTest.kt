package psi

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SyncGeneratorTest {

  @Test
  fun `synchronize with class truth updates DAOs and OpenAPI`() {
    val tempDir = Files.createTempDirectory("sync-generator-test").toFile()

    // Setup Models
    val modelsDir = File(tempDir, "models").apply { mkdirs() }
    File(modelsDir, "Models.kt")
        .writeText(
            """
            package org.example.models
            data class SynchronizedUser(val id: Int, val name: String)
            """
                .trimIndent())

    // Setup DAO
    val daoDir = File(tempDir, "dao").apply { mkdirs() }
    val daoFile = File(daoDir, "Daos.kt")
    daoFile.writeText(
        """
            package org.example.dao
            // Outdated DAO
            """
            .trimIndent())

    SyncGenerator.synchronize(tempDir.absolutePath, "class")

    // Assert DAO was updated
    val updatedDaoFile = File(tempDir, "dao/SynchronizedUserDao.kt")
    assertTrue(updatedDaoFile.exists(), "DAO file was not generated")
    val updatedDao = updatedDaoFile.readText()
    assertTrue(updatedDao.contains("interface SynchronizedUserDao"), "DAO was not synchronized")
    assertTrue(updatedDao.contains("ConcreteSynchronizedUserDao"), "DAO was not synchronized")

    // Assert OpenAPI was synced
    val specFile = File(tempDir, "openapi_sync.json")
    assertTrue(specFile.exists(), "OpenAPI spec was not synchronized")
    assertTrue(specFile.readText().contains("SynchronizedUser"), "OpenAPI spec missing model")

    tempDir.deleteRecursively()
  }

  @Test
  fun `synchronize throws on invalid directory`() {
    assertThrows(IllegalArgumentException::class.java) {
      SyncGenerator.synchronize("/nonexistent_directory_sync_123", "class")
    }
  }
}
