/** Unit and integration tests in [SyncGeneratorTest]. */
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
  fun `synchronize returns failure on invalid directory`() {
    assertTrue(SyncGenerator.synchronize("/nonexistent_directory_sync_123", "class").isFailure)
    val tempFile = Files.createTempFile("sync-file", ".txt").toFile()
    try {
      assertTrue(SyncGenerator.synchronize(tempFile.absolutePath, "class").isFailure)
    } finally {
      tempFile.delete()
    }

    val tempDir = Files.createTempDirectory("sync-other-truths").toFile()
    try {
      File(tempDir, "README.md").writeText("# ignore non-kt file")
      File(tempDir, "Tables.kt")
          .writeText(
              """
              package custom.pkg.dao
              val anon = object : Runnable { override fun run() {} }
              object Table : Table("empty_name")
              object NoBodyTable : Table("no_body")
              object AccountTable : Table("accounts") {
                val ignoredProp = 1
                val noInitColumn: Int
                val unknownCallColumn = customCol("u")
                val idColumn = integer("id").autoIncrement()
                val totalColumn = long("total")
                val scoreColumn = double("score").nullable()
                val activeColumn = bool("active")
                val nameColumn = varchar("name", 255)
                val bioColumn = text("bio").nullable()
              }
              """
                  .trimIndent())
      assertTrue(SyncGenerator.synchronize(tempDir.absolutePath, "sqlalchemy").isSuccess)
      assertTrue(File(tempDir, "models/Account.kt").exists())
      // Second run merges into existing Account.kt via DtoMerger
      assertTrue(SyncGenerator.synchronize(tempDir.absolutePath, "dao").isSuccess)
      // Corrupt Account.kt so DtoMerger falls back to DtoGenerator
      File(tempDir, "models/Account.kt").writeText("package custom.pkg.models\n// no class")
      assertTrue(SyncGenerator.synchronize(tempDir.absolutePath, "model").isSuccess)
      assertTrue(SyncGenerator.synchronize(tempDir.absolutePath, "dao").isSuccess)
      assertTrue(File(tempDir, "openapi_sync.json").exists())

      // Test openapi truth target (with existing openapi_sync.json and without)
      assertTrue(SyncGenerator.synchronize(tempDir.absolutePath, "openapi").isSuccess)
      File(tempDir, "openapi_sync.json").writeText("{bad json")
      assertTrue(SyncGenerator.synchronize(tempDir.absolutePath, "openapi").isFailure)
      File(tempDir, "openapi_sync.json").delete()
      File(tempDir, "openapi.json").mkdirs()
      assertTrue(SyncGenerator.synchronize(tempDir.absolutePath, "openapi").isSuccess)
      File(tempDir, "openapi.json").deleteRecursively()

      // Test failure when models/ is a regular file blocking writeModelSchemas
      File(tempDir, "models").deleteRecursively()
      File(tempDir, "models").writeText("blocked")
      assertTrue(SyncGenerator.synchronize(tempDir.absolutePath, "dao").isFailure)
      File(tempDir, "openapi_sync.json")
          .writeText(
              """{"openapi":"3.2.0","info":{"title":"T","version":"1"},"components":{"schemas":{"A":{"type":"object"}}}}""")
      assertTrue(SyncGenerator.synchronize(tempDir.absolutePath, "openapi").isFailure)
      File(tempDir, "openapi_sync.json")
          .writeText("""{"openapi":"3.2.0","info":{"title":"T","version":"1"}}""")
      File(tempDir, "models").delete()
      assertTrue(SyncGenerator.synchronize(tempDir.absolutePath, "openapi").isSuccess)

      // Test blank package fallback and unknown truth target failure
      val blankPkgDir = Files.createTempDirectory("sync-blank-pkg").toFile()
      try {
        File(blankPkgDir, "OnlyDao.kt").writeText("package .models\ndata class Item(val id: Int)")
        assertTrue(SyncGenerator.synchronize(blankPkgDir.absolutePath, "class").isSuccess)
      } finally {
        blankPkgDir.deleteRecursively()
      }

      assertTrue(SyncGenerator.synchronize(tempDir.absolutePath, "unknown_truth").isFailure)
    } finally {
      tempDir.deleteRecursively()
    }
  }
}
