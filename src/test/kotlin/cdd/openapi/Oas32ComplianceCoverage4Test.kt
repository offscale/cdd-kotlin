package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage4Test {
    @Test
    fun `validate security schemes and oauth flows`() {
        val validator = OpenApiValidator()
        
        // http missing scheme
        val ss1 = SecurityScheme(type = "http")
        val def1 = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = Components(securitySchemes = mapOf("s" to ss1)))
        val i1 = validator.validate(def1)
        assertTrue(i1.any { it.message.contains("http security scheme requires scheme") })

        // openIdConnect missing url
        val ss2 = SecurityScheme(type = "openIdConnect")
        val def2 = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = Components(securitySchemes = mapOf("s" to ss2)))
        val i2 = validator.validate(def2)
        assertTrue(i2.any { it.message.contains("openIdConnect security scheme requires openIdConnectUrl") })

        // apiKey missing name/in and invalid in
        val ss3 = SecurityScheme(type = "apiKey", `in` = "bad")
        val def3 = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = Components(securitySchemes = mapOf("s" to ss3)))
        val i3 = validator.validate(def3)
        assertTrue(i3.any { it.message.contains("apiKey security scheme requires name and in") })
        assertTrue(i3.any { it.message.contains("must be one of: query, header, cookie") })

        // oauth2 with bad type warning
        val flows4 = OAuthFlows(implicit = OAuthFlow(authorizationUrl = "https://good"))
        val ss4 = SecurityScheme(type = "http", scheme = "basic", flows = flows4)
        val def4 = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = Components(securitySchemes = mapOf("s" to ss4)))
        val i4 = validator.validate(def4)
        assertTrue(i4.any { it.message.contains("OAuth flows are only valid for oauth2 security schemes") })
        
        // oauth2 flow valid
        val ss5 = SecurityScheme(type = "oauth2", flows = flows4)
        val def5 = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = Components(securitySchemes = mapOf("s" to ss5)))
        validator.validate(def5)
        
        // reference handling
        val ssRef = SecurityScheme(reference = ReferenceObject("#/components/securitySchemes/s"))
        val defRef = OpenApiDefinition(openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(), components = Components(securitySchemes = mapOf("ref" to ssRef, "s" to ss5)))
        validator.validate(defRef)
    }
}
