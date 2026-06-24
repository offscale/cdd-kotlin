package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerMainGeneratorTest {
  @Test
  fun `generateServerMainModule produces expected cli and server logic`() {
    val generator = ServerMainGenerator()
    val schema =
        SchemaDefinition(
            name = "User",
            type = "object",
            properties =
                mapOf(
                    "id" to SchemaProperty(type = "integer"),
                    "name" to SchemaProperty(type = "string")))
    val result =
        generator.generateServerMain("com.example", listOf(schema)).values.joinToString("\n")

    assertTrue(result.contains("class MockServerCli : CliktCommand"), "Missing CLI class")
    assertTrue(result.contains("val ephemeral by option(\"--ephemeral\""), "Missing ephemeral flag")
    assertTrue(result.contains("val seed by option(\"--seed\""), "Missing seed flag")
    assertTrue(
        result.contains("val daoConfig = DaoFactory.create(useConcrete = useConcrete)"),
        "Missing DAO resolve")
    assertTrue(result.contains("DatabaseConnection.initialize(dbConfig)"), "Missing DB init")
    assertTrue(result.contains("val seeder = DatabaseSeeder(daoConfig)"), "Missing Seeder init")
    assertTrue(result.contains("embeddedServer(Netty, port = 8080)"), "Missing Ktor server setup")
    assertTrue(result.contains("get(\"/user\") {"), "Missing user route")
  }
}
