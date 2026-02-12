package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DtoGeneratorBooleanRefTest {

    private val generator = DtoGenerator()

    @Test
    fun `generate aliases for boolean schemas`() {
        val anySchema = SchemaDefinition(
            name = "AnyValue",
            type = "object",
            booleanSchema = true
        )
        val noneSchema = SchemaDefinition(
            name = "NoValue",
            type = "object",
            booleanSchema = false
        )

        val anyText = generator.generateDto("com.test", anySchema).text
        val noneText = generator.generateDto("com.test", noneSchema).text

        assertTrue(anyText.contains("typealias AnyValue = Any"))
        assertTrue(noneText.contains("typealias NoValue = Nothing"))
    }

    @Test
    fun `generate alias for schema ref`() {
        val aliasSchema = SchemaDefinition(
            name = "Alias",
            type = "object",
            ref = "#/components/schemas/Target"
        )

        val text = generator.generateDto("com.test", aliasSchema).text
        assertTrue(text.contains("typealias Alias = Target"))
    }

    @Test
    fun `emit numeric constraint tags in kdoc`() {
        val schema = SchemaDefinition(
            name = "Numeric",
            type = "number",
            multipleOf = 2.5,
            exclusiveMinimum = 1.0,
            exclusiveMaximum = 9.0
        )

        val propertySchema = SchemaDefinition(
            name = "Container",
            type = "object",
            properties = mapOf(
                "count" to SchemaProperty(
                    type = "integer",
                    multipleOf = 2.0,
                    exclusiveMinimum = 1.0,
                    exclusiveMaximum = 9.0
                )
            ),
            required = listOf("count")
        )

        val schemaText = generator.generateDto("com.test", schema).text
        val propertyText = generator.generateDto("com.test", propertySchema).text

        assertTrue(schemaText.contains("@multipleOf 2.5"))
        assertTrue(schemaText.contains("@exclusiveMinimum 1.0"))
        assertTrue(schemaText.contains("@exclusiveMaximum 9.0"))

        assertTrue(propertyText.contains("@multipleOf 2.0"))
        assertTrue(propertyText.contains("@exclusiveMinimum 1.0"))
        assertTrue(propertyText.contains("@exclusiveMaximum 9.0"))
    }
}
