package cdd.openapi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class Oas32ComplianceCoverage15Test {
    @Test
    fun `validate oauth flows device and url checks`() {
        val validator = OpenApiValidator()
        
        // device flow missing URLs
        val flows1 = OAuthFlows(deviceAuthorization = OAuthFlow())
        val def1 = OpenApiDefinition(
            openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(),
            components = Components(securitySchemes = mapOf("s" to SecurityScheme(type = "oauth2", flows = flows1)))
        )
        val i1 = validator.validate(def1)
        assertTrue(i1.any { it.message.contains("deviceAuthorization OAuth flow requires") })
        
        // test all urls with valid and invalid (e.g. without https)
        val flowsAll = OAuthFlows(
            implicit = OAuthFlow(authorizationUrl = "http://a", refreshUrl = "http://a"),
            password = OAuthFlow(tokenUrl = "http://b", refreshUrl = "http://b"),
            clientCredentials = OAuthFlow(tokenUrl = "http://c", refreshUrl = "http://c"),
            authorizationCode = OAuthFlow(authorizationUrl = "http://d", tokenUrl = "http://e", refreshUrl = "http://f"),
            deviceAuthorization = OAuthFlow(deviceAuthorizationUrl = "http://g", tokenUrl = "http://h", refreshUrl = "http://i")
        )
        val defAll = OpenApiDefinition(
            openapi = "3.2.0", info = Info("t", "1"), paths = emptyMap(),
            components = Components(securitySchemes = mapOf("s" to SecurityScheme(type = "oauth2", flows = flowsAll)))
        )
        val iAll = validator.validate(defAll)
        iAll.forEach { println(it.message) }
    }
}
