package cdd.classes

import cdd.openapi.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class EmitCoverage2Test {
    @Test
    fun `buildPropertyKDoc tests`() {
        val gen = DtoGenerator()
        val doc1 = gen.buildPropertyKDoc(SchemaProperty(
            description = "d", example = "ex", enumValues = listOf("a"),
            externalDocs = ExternalDocumentation(url="u"), discriminator = Discriminator("p"),
            contentMediaType = "mt", contentEncoding = "enc", title = "t",
            defaultValue = "dv", constValue = "cv"
        ))
        assertTrue(doc1.contains("d"))
        
        val doc2 = gen.buildPropertyKDoc(SchemaProperty(
            examples = listOf("ex1", "ex2")
        ))
        assertTrue(doc2.contains("ex1"))
    }
}
