package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage3Test {
    @Test
    fun `validate oauth flows missing and malformed URLs`() {
        val validator = OpenApiValidator()
        
        // implicit flow missing auth url
        val flows1 = OAuthFlows(implicit = OAuthFlow())
        val def1 = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("Test", "1.0"),
            paths = emptyMap(),
            components = Components(securitySchemes = mapOf("s" to SecurityScheme(type = "oauth2", flows = flows1)))
        )
        val i1 = validator.validate(def1)
        assertTrue(i1.any { it.message.contains("implicit OAuth flow requires authorizationUrl") })
        
        // password flow missing token url and malformed urls
        val flows2 = OAuthFlows(password = OAuthFlow(tokenUrl = " ", refreshUrl = "http://bad"))
        val def2 = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("Test", "1.0"),
            paths = emptyMap(),
            components = Components(securitySchemes = mapOf("s" to SecurityScheme(type = "oauth2", flows = flows2)))
        )
        val i2 = validator.validate(def2)
        assertTrue(i2.any { it.message.contains("password OAuth flow requires tokenUrl") })
        
        // client credentials missing token url and malformed urls
        val flows3 = OAuthFlows(clientCredentials = OAuthFlow(tokenUrl = "", refreshUrl = "bad url"))
        val def3 = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("Test", "1.0"),
            paths = emptyMap(),
            components = Components(securitySchemes = mapOf("s" to SecurityScheme(type = "oauth2", flows = flows3)))
        )
        val i3 = validator.validate(def3)
        assertTrue(i3.any { it.message.contains("clientCredentials OAuth flow requires tokenUrl") })
        
        // authorization code missing urls
        val flows4 = OAuthFlows(authorizationCode = OAuthFlow(authorizationUrl = "https://good", refreshUrl = "http://bad"))
        val def4 = OpenApiDefinition(
            openapi = "3.2.0",
            info = Info("Test", "1.0"),
            paths = emptyMap(),
            components = Components(securitySchemes = mapOf("s" to SecurityScheme(type = "oauth2", flows = flows4)))
        )
        val i4 = validator.validate(def4)
        assertTrue(i4.any { it.message.contains("authorizationCode OAuth flow requires authorizationUrl and tokenUrl") })
    }
}
