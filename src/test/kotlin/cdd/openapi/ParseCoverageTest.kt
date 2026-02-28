package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.nio.file.Files

class ParseCoverageTest {
    @Test
    fun `parseDocumentFile and parseSchemaFile`() {
        val parser = OpenApiParser()
        
        val tempJson = Files.createTempFile("test", ".json").toFile()
        tempJson.writeText("{\"type\": \"string\"}")
        tempJson.deleteOnExit()
        
        val doc = parser.parseDocumentFile(tempJson)
        assertNotNull(doc)
        
        val schema = parser.parseSchemaFile(tempJson)
        assertNotNull(schema)
        assertEquals(setOf("string"), schema.types)
        
        val tempYaml = Files.createTempFile("test", ".yaml").toFile()
        tempYaml.writeText("type: string")
        tempYaml.deleteOnExit()
        
        val schemaYaml = parser.parseSchemaFile(tempYaml)
        assertNotNull(schemaYaml)
        assertEquals(setOf("string"), schemaYaml.types)
    }
}
