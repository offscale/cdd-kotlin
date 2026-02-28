package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage22Test {
    @Test
    fun `validate security scheme errors and extra props`() {
        val validator = OpenApiValidator()
        
        val ss1 = SecurityScheme(type = "http", oauth2MetadataUrl = "http://a")
        val ss2 = SecurityScheme(type = "http", openIdConnectUrl = "http://a")
        val ss3 = SecurityScheme(type = "apiKey", name = "n", `in` = "header", oauth2MetadataUrl = "http://a")
        val ss4 = SecurityScheme(type = "oauth2", flows = OAuthFlows(), openIdConnectUrl = "http://a")
        
        val ss5 = SecurityScheme(type = "bad")
        
        val c = Components(securitySchemes = mapOf(
            "ss1" to ss1, "ss2" to ss2, "ss3" to ss3, "ss4" to ss4, "ss5" to ss5
        ))
        
        val def = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = c)
        
        val i = validator.validate(def)
        assertTrue(i.any { it.message.contains("oauth2MetadataUrl is only valid for oauth2") })
        assertTrue(i.any { it.message.contains("openIdConnectUrl is only valid for openIdConnect") })
        assertTrue(i.any { it.message.contains("is invalid. Must be one of") })
    }
}
