package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ValidateDiscriminatorTest {
    @Test
    fun testValidateDiscriminator() {
        val validator = OpenApiValidator()
        val issues = mutableListOf<OpenApiIssue>()
        
        // 1. All branches: Discriminator present but no composition.
        validator.validateDiscriminator(
            discriminator = Discriminator(propertyName = "type"),
            required = listOf(),
            properties = emptyMap(),
            hasComposition = false,
            path = "path",
            issues = issues
        )
        assertTrue(issues.any { it.message.contains("Discriminator requires oneOf, anyOf, or allOf") })
        issues.clear()
        
        // 2. Discriminator with properties, but propertyName missing from properties map.
        validator.validateDiscriminator(
            discriminator = Discriminator(propertyName = "type"),
            required = listOf("type"),
            properties = mapOf("other" to SchemaProperty(type="string")),
            hasComposition = true,
            path = "path",
            issues = issues
        )
        assertTrue(issues.any { it.message.contains("not defined in schema properties") })
        issues.clear()
        
        // 3. Property is not required and defaultMapping is empty.
        validator.validateDiscriminator(
            discriminator = Discriminator(propertyName = "type", defaultMapping = ""),
            required = listOf(),
            properties = mapOf("type" to SchemaProperty(type="string")),
            hasComposition = true,
            path = "path",
            issues = issues
        )
        assertTrue(issues.any { it.message.contains("defaultMapping is required") })
        issues.clear()
        
        // 4. Everything valid
        validator.validateDiscriminator(
            discriminator = Discriminator(propertyName = "type", defaultMapping = "a"),
            required = listOf("type"),
            properties = mapOf("type" to SchemaProperty(type="string")),
            hasComposition = true,
            path = "path",
            issues = issues
        )
        assertTrue(issues.isEmpty())
        
        // 5. No discriminator
        validator.validateDiscriminator(
            discriminator = null,
            required = listOf(),
            properties = emptyMap(),
            hasComposition = false,
            path = "path",
            issues = issues
        )
        assertTrue(issues.isEmpty())
    }
}
