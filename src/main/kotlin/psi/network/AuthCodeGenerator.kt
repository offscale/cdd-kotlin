/**
 * Generates Ktor client authentication factories and OAuth2, PKCE, Device Flow, MutualTLS, HTTP
 * Bearer/Basic, and ApiKey helpers.
 */
package psi.network

import domain.SecurityScheme
import psi.decapitalizeName

/**
 * Generates `createHttpClient` and associated authentication helper blocks (`OAuthTokens`, `Pkce`,
 * `MutualTlsConfig`, token exchange, refresh, and device authorization polling).
 */
object AuthCodeGenerator {
  /**
   * Generates Ktor authentication factory code for [schemes].
   *
   * @param schemes Security schemes map.
   * @return Generated Kotlin code.
   */
  fun generateAuthFactory(schemes: Map<String, SecurityScheme>): String {
    val hasHttpAuth = schemes.values.any { it.type == "http" }
    val hasOAuthAuth = schemes.values.any { it.type == "oauth2" || it.type == "openIdConnect" }
    val hasMutualTls = schemes.values.any { it.type == "mutualTLS" }

    val usedNames = mutableSetOf<String>()
    val schemeParamNames = mutableMapOf<String, String>()
    val basicParamNames = mutableMapOf<String, Pair<String, String>>()
    val args = mutableListOf<String>()

    schemes.forEach { (key, scheme) ->
      val base = sanitizeIdentifier(key).ifEmpty { "auth" }
      val schemeName = scheme.scheme.orEmpty().lowercase()
      when {
        scheme.type == "http" && schemeName == "basic" -> {
          val user = uniqueName("${base}User", usedNames)
          val pass = uniqueName("${base}Pass", usedNames)
          basicParamNames[key] = user to pass
          args += "$user: String? = null"
          args += "$pass: String? = null"
        }
        scheme.type == "oauth2" || scheme.type == "openIdConnect" -> {
          val name = uniqueName(base, usedNames)
          schemeParamNames[key] = name
          args += "$name: OAuthTokens? = null"
        }
        scheme.type == "mutualTLS" -> {
          val name = uniqueName(base, usedNames)
          schemeParamNames[key] = name
          args += "$name: MutualTlsConfig? = null"
        }
        else -> {
          val name = uniqueName(base, usedNames)
          schemeParamNames[key] = name
          args += "$name: String? = null"
        }
      }
    }

    val mutualTlsConfigurerName =
        if (hasMutualTls) uniqueName("mutualTlsConfigurer", usedNames) else null
    if (mutualTlsConfigurerName != null) {
      args += "$mutualTlsConfigurerName: MutualTlsConfigurer? = null"
    }

    // Build Install blocks
    val installBlocks = StringBuilder()
    val apiKeys = schemes.entries.filter { it.value.type == "apiKey" }

    // 1. HTTP Auth (Bearer / Basic) + OAuth2/OpenID Connect
    if (hasHttpAuth || hasOAuthAuth) {
      installBlocks.append("install(Auth) {\n")
      schemes.forEach { (key, scheme) ->
        when (scheme.type) {
          "http" -> {
            val s = scheme.scheme
            when (if (s != null) s.lowercase() else "") {
              "basic" -> {
                val (userParam, passParam) = basicParamNames.getValue(key)
                installBlocks.append(
                    """
                                        basic {
                                            credentials {
                                                if ($userParam != null && $passParam != null) {
                                                    BasicAuthCredentials(username = $userParam, password = $passParam)
                                                } else null
                                            }
                                        }
                                    """
                        .trimIndent()
                        .prependIndent("                ") + "\n")
              }
              "bearer" -> {
                val tokenParam = schemeParamNames.getValue(key)
                installBlocks.append(
                    """
                                    bearer {
                                        loadTokens {
                                            if ($tokenParam != null) {
                                                BearerTokens(accessToken = $tokenParam, refreshToken = null)
                                            } else null
                                        }
                                    }
                                """
                        .trimIndent()
                        .prependIndent("                ") + "\n")
              }
            }
          }
          "oauth2",
          "openIdConnect" -> {
            val tokenParam = schemeParamNames.getValue(key)
            installBlocks.append(
                """
                            bearer {
                                loadTokens {
                                    if ($tokenParam != null) {
                                        BearerTokens(accessToken = $tokenParam.accessToken, refreshToken = $tokenParam.refreshToken)
                                    } else null
                                }
                            }
                        """
                    .trimIndent()
                    .prependIndent("                ") + "\n")
          }
        }
      }
      installBlocks.append("            }\n")
    }

    // 2. Api Keys (DefaultRequest)
    if (apiKeys.isNotEmpty()) {
      installBlocks.append("            install(DefaultRequest) {\n")
      apiKeys.forEach { (key, scheme) ->
        val paramName = schemeParamNames.getValue(key)
        val headerName = scheme.name ?: key
        val location = scheme.`in`

        val block =
            when (location?.lowercase()) {
              "header" -> "header(\"$headerName\", $paramName)"
              "query" -> "url.parameters.append(\"$headerName\", $paramName)"
              "cookie" -> "cookie(\"$headerName\", $paramName)"
              else -> "// Unsupported apiKey location: $location"
            }

        installBlocks.append(
            """
                    if ($paramName != null) {
                        $block
                    }
                """
                .trimIndent()
                .prependIndent("                ") + "\n")
      }
      installBlocks.append("            }\n")
    }

    // 3. mutualTLS configuration hook
    if (mutualTlsConfigurerName != null) {
      installBlocks.append("            $mutualTlsConfigurerName?.let { configurer ->\n")
      schemes
          .filter { it.value.type == "mutualTLS" }
          .forEach { (key, _) ->
            val paramName = schemeParamNames.getValue(key)
            installBlocks.append(
                """
                    if ($paramName != null) {
                        configurer(this, $paramName)
                    }
                """
                    .trimIndent()
                    .prependIndent("                ") + "\n")
          }
      installBlocks.append("            }\n")
    }

    val helperBlocks = mutableListOf<String>()
    if (hasMutualTls) {
      helperBlocks.add(generateMutualTlsHelperBlock())
    }
    if (hasOAuthAuth) {
      helperBlocks.add(generateOAuthHelperBlock())
    }

    val argsBlock = args.joinToString(",\n            ")
    val createClientBlock =
        """
            /**
             * Creates a Ktor HttpClient configured with the defined Security Schemes.
             */
            fun createHttpClient(
                $argsBlock
            ): HttpClient {
                return HttpClient {
        $installBlocks                }
            }
        """
            .trimIndent()

    helperBlocks.add(createClientBlock)

    return helperBlocks.joinToString("\n\n").trimIndent().replace("\n", "\n    ")
  }

  private fun generateMutualTlsHelperBlock(): String {
    return """
            /**
             * Configuration for mutual TLS (mTLS) client credentials.
             */
            data class MutualTlsConfig(
                val keyStorePath: String? = null,
                val keyStorePassword: String? = null,
                val keyStoreType: String = "PKCS12",
                val trustStorePath: String? = null,
                val trustStorePassword: String? = null,
                val trustStoreType: String = "PKCS12"
            )

            /**
             * Hook to apply mutual TLS settings to the Ktor client engine.
             */
            typealias MutualTlsConfigurer = HttpClientConfig<*>.(MutualTlsConfig) -> Unit
        """
        .trimIndent()
  }

  private fun generateOAuthHelperBlock(): String {
    return """
            data class OAuthTokens(
                val accessToken: String,
                val tokenType: String? = null,
                val refreshToken: String? = null,
                val expiresIn: Long? = null,
                val scope: String? = null,
                val idToken: String? = null,
                val raw: JsonObject? = null
            )

            data class OAuthError(
                val error: String? = null,
                val errorDescription: String? = null,
                val errorUri: String? = null,
                val raw: JsonObject? = null
            )

            data class OAuthDeviceCodeResponse(
                val deviceCode: String,
                val userCode: String,
                val verificationUri: String,
                val verificationUriComplete: String? = null,
                val expiresIn: Long? = null,
                val interval: Long? = null,
                val raw: JsonObject? = null
            )

            data class Pkce(
                val codeVerifier: String,
                val codeChallenge: String,
                val method: String = "S256"
            )

            fun createPkceVerifier(length: Int = 64): String {
                val safeLength = length.coerceIn(43, 128)
                val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
                return buildString(safeLength) {
                    repeat(safeLength) {
                        append(chars[Random.nextInt(chars.length)])
                    }
                }
            }

            fun createPkce(length: Int = 64): Pkce {
                val verifier = createPkceVerifier(length)
                val challenge = pkceS256Challenge(verifier)
                return Pkce(codeVerifier = verifier, codeChallenge = challenge)
            }

            private fun pkceS256Challenge(verifier: String): String {
                val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
                return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
            }

            fun buildAuthorizationUrl(
                authorizationUrl: String,
                clientId: String,
                redirectUri: String,
                scopes: List<String> = emptyList(),
                state: String? = null,
                responseType: String = "code",
                codeChallenge: String? = null,
                codeChallengeMethod: String? = null,
                additionalParameters: Map<String, String> = emptyMap()
            ): String {
                val builder = URLBuilder(authorizationUrl)
                builder.parameters.append("response_type", responseType)
                builder.parameters.append("client_id", clientId)
                builder.parameters.append("redirect_uri", redirectUri)
                if (scopes.isNotEmpty()) {
                    builder.parameters.append("scope", scopes.joinToString(" "))
                }
                if (state != null) {
                    builder.parameters.append("state", state)
                }
                if (codeChallenge != null) {
                    builder.parameters.append("code_challenge", codeChallenge)
                    builder.parameters.append("code_challenge_method", codeChallengeMethod ?: "S256")
                }
                additionalParameters.forEach { (key, value) ->
                    builder.parameters.append(key, value)
                }
                return builder.buildString()
            }

            suspend fun exchangeAuthorizationCode(
                client: HttpClient,
                tokenUrl: String,
                clientId: String,
                code: String,
                redirectUri: String,
                clientSecret: String? = null,
                codeVerifier: String? = null,
                additionalParameters: Map<String, String> = emptyMap()
            ): Result<OAuthTokens> {
                val params = buildOAuthParameters(
                    mapOf(
                        "grant_type" to "authorization_code",
                        "code" to code,
                        "redirect_uri" to redirectUri,
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "code_verifier" to codeVerifier
                    ),
                    additionalParameters
                )
                return requestTokenResult(client, tokenUrl, params)
            }

            suspend fun refreshToken(
                client: HttpClient,
                tokenUrl: String,
                refreshToken: String,
                clientId: String,
                clientSecret: String? = null,
                scopes: List<String> = emptyList(),
                additionalParameters: Map<String, String> = emptyMap()
            ): Result<OAuthTokens> {
                val params = buildOAuthParameters(
                    mapOf(
                        "grant_type" to "refresh_token",
                        "refresh_token" to refreshToken,
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "scope" to scopes.takeIf { it.isNotEmpty() }?.joinToString(" ")
                    ),
                    additionalParameters
                )
                return requestTokenResult(client, tokenUrl, params)
            }

            suspend fun clientCredentialsToken(
                client: HttpClient,
                tokenUrl: String,
                clientId: String,
                clientSecret: String? = null,
                scopes: List<String> = emptyList(),
                additionalParameters: Map<String, String> = emptyMap()
            ): Result<OAuthTokens> {
                val params = buildOAuthParameters(
                    mapOf(
                        "grant_type" to "client_credentials",
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "scope" to scopes.takeIf { it.isNotEmpty() }?.joinToString(" ")
                    ),
                    additionalParameters
                )
                return requestTokenResult(client, tokenUrl, params)
            }

            suspend fun passwordToken(
                client: HttpClient,
                tokenUrl: String,
                clientId: String,
                username: String,
                password: String,
                clientSecret: String? = null,
                scopes: List<String> = emptyList(),
                additionalParameters: Map<String, String> = emptyMap()
            ): Result<OAuthTokens> {
                val params = buildOAuthParameters(
                    mapOf(
                        "grant_type" to "password",
                        "username" to username,
                        "password" to password,
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "scope" to scopes.takeIf { it.isNotEmpty() }?.joinToString(" ")
                    ),
                    additionalParameters
                )
                return requestTokenResult(client, tokenUrl, params)
            }

            suspend fun requestDeviceCode(
                client: HttpClient,
                deviceAuthorizationUrl: String,
                clientId: String,
                scopes: List<String> = emptyList(),
                additionalParameters: Map<String, String> = emptyMap()
            ): Result<OAuthDeviceCodeResponse> {
                val params = buildOAuthParameters(
                    mapOf(
                        "client_id" to clientId,
                        "scope" to scopes.takeIf { it.isNotEmpty() }?.joinToString(" ")
                    ),
                    additionalParameters
                )
                val response = client.submitForm(url = deviceAuthorizationUrl, formParameters = params)
                val payload = response.bodyAsText()
                val json = parseJsonObject(payload)
                if (response.status.value !in 200..299) {
                    val error = json?.let { parseOAuthError(it) }
                        ?: OAuthError(error = "http_" + response.status.value, errorDescription = response.status.description)
                    return Result.failure(ApiException(tokenErrorMessage(error)))
                }
                if (json == null) {
                    return Result.failure(ApiException("Device authorization response was not valid JSON."))
                }
                val deviceCode = json["device_code"]?.jsonPrimitive?.contentOrNull
                    ?: return Result.failure(ApiException("Device authorization response missing device_code."))
                val userCode = json["user_code"]?.jsonPrimitive?.contentOrNull
                    ?: return Result.failure(ApiException("Device authorization response missing user_code."))
                val verificationUri = json["verification_uri"]?.jsonPrimitive?.contentOrNull
                    ?: return Result.failure(ApiException("Device authorization response missing verification_uri."))
                val verificationUriComplete = json["verification_uri_complete"]?.jsonPrimitive?.contentOrNull
                val expiresIn = json["expires_in"]?.jsonPrimitive?.longOrNull
                val interval = json["interval"]?.jsonPrimitive?.longOrNull
                return Result.success(
                    OAuthDeviceCodeResponse(
                        deviceCode = deviceCode,
                        userCode = userCode,
                        verificationUri = verificationUri,
                        verificationUriComplete = verificationUriComplete,
                        expiresIn = expiresIn,
                        interval = interval,
                        raw = json
                    )
                )
            }

            suspend fun exchangeDeviceToken(
                client: HttpClient,
                tokenUrl: String,
                deviceCode: String,
                clientId: String,
                clientSecret: String? = null,
                additionalParameters: Map<String, String> = emptyMap()
            ): Result<OAuthTokens> {
                val params = buildOAuthParameters(
                    mapOf(
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        "device_code" to deviceCode,
                        "client_id" to clientId,
                        "client_secret" to clientSecret
                    ),
                    additionalParameters
                )
                return requestTokenResult(client, tokenUrl, params)
            }

            suspend fun pollDeviceToken(
                client: HttpClient,
                tokenUrl: String,
                deviceCode: String,
                clientId: String,
                clientSecret: String? = null,
                intervalSeconds: Long = 5,
                timeoutSeconds: Long = 600,
                additionalParameters: Map<String, String> = emptyMap()
            ): Result<OAuthTokens> {
                var intervalMs = intervalSeconds.coerceAtLeast(1L) * 1000L
                val deadline = System.currentTimeMillis() + (timeoutSeconds.coerceAtLeast(1L) * 1000L)
                val params = buildOAuthParameters(
                    mapOf(
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        "device_code" to deviceCode,
                        "client_id" to clientId,
                        "client_secret" to clientSecret
                    ),
                    additionalParameters
                )
                while (System.currentTimeMillis() < deadline) {
                    when (val result = requestToken(client, tokenUrl, params)) {
                        is OAuthTokenResult.Success -> return Result.success(result.tokens)
                        is OAuthTokenResult.Error -> {
                            when (result.error.error) {
                                "authorization_pending" -> delay(intervalMs)
                                "slow_down" -> {
                                    intervalMs += 5000L
                                    delay(intervalMs)
                                }
                                "expired_token" -> return Result.failure(ApiException(tokenErrorMessage(result.error)))
                                else -> return Result.failure(ApiException(tokenErrorMessage(result.error)))
                            }
                        }
                    }
                }
                return Result.failure(ApiException("Device authorization timed out."))
            }

            private sealed class OAuthTokenResult {
                data class Success(val tokens: OAuthTokens) : OAuthTokenResult()
                data class Error(val error: OAuthError) : OAuthTokenResult()
            }

            private fun buildOAuthParameters(
                base: Map<String, String?>,
                additional: Map<String, String>
            ): Parameters {
                return Parameters.build {
                    base.forEach { (key, value) ->
                        if (value != null) {
                            append(key, value)
                        }
                    }
                    additional.forEach { (key, value) ->
                        append(key, value)
                    }
                }
            }

            private suspend fun requestToken(
                client: HttpClient,
                tokenUrl: String,
                parameters: Parameters
            ): OAuthTokenResult {
                val response = client.submitForm(url = tokenUrl, formParameters = parameters)
                val payload = response.bodyAsText()
                val json = parseJsonObject(payload)
                return if (response.status.value in 200..299) {
                    val tokens = json?.let { parseOAuthTokens(it) }
                    if (tokens != null) {
                        OAuthTokenResult.Success(tokens)
                    } else {
                        OAuthTokenResult.Error(
                            OAuthError(
                                error = "invalid_token_response",
                                errorDescription = "Missing access_token in response.",
                                raw = json
                            )
                        )
                    }
                } else {
                    val error = json?.let { parseOAuthError(it) }
                        ?: OAuthError(
                            error = "http_" + response.status.value,
                            errorDescription = payload.ifBlank { response.status.description },
                            raw = json
                        )
                    OAuthTokenResult.Error(error)
                }
            }

            private suspend fun requestTokenResult(
                client: HttpClient,
                tokenUrl: String,
                parameters: Parameters
            ): Result<OAuthTokens> {
                return when (val result = requestToken(client, tokenUrl, parameters)) {
                    is OAuthTokenResult.Success -> Result.success(result.tokens)
                    is OAuthTokenResult.Error -> Result.failure(ApiException(tokenErrorMessage(result.error)))
                }
            }

            private fun parseOAuthTokens(json: JsonObject): OAuthTokens? {
                val accessToken = json["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
                val tokenType = json["token_type"]?.jsonPrimitive?.contentOrNull
                val refreshToken = json["refresh_token"]?.jsonPrimitive?.contentOrNull
                val expiresIn = json["expires_in"]?.jsonPrimitive?.longOrNull
                val scope = json["scope"]?.jsonPrimitive?.contentOrNull
                val idToken = json["id_token"]?.jsonPrimitive?.contentOrNull
                return OAuthTokens(
                    accessToken = accessToken,
                    tokenType = tokenType,
                    refreshToken = refreshToken,
                    expiresIn = expiresIn,
                    scope = scope,
                    idToken = idToken,
                    raw = json
                )
            }

            private fun parseOAuthError(json: JsonObject): OAuthError {
                return OAuthError(
                    error = json["error"]?.jsonPrimitive?.contentOrNull,
                    errorDescription = json["error_description"]?.jsonPrimitive?.contentOrNull,
                    errorUri = json["error_uri"]?.jsonPrimitive?.contentOrNull,
                    raw = json
                )
            }

            private fun parseJsonObject(payload: String): JsonObject? {
                val trimmed = payload.trim()
                if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
                return Json.parseToJsonElement(trimmed) as? JsonObject
            }

            private fun tokenErrorMessage(error: OAuthError): String {
                val parts = listOfNotNull(error.error, error.errorDescription, error.errorUri)
                return if (parts.isEmpty()) "OAuth error." else "OAuth error: " + parts.joinToString(" - ")
            }
        """
        .trimIndent()
  }

  private fun uniqueName(base: String, usedNames: MutableSet<String>): String {
    var name = base
    var suffix = 2
    while (!usedNames.add(name)) {
      name = "${base}${suffix++}"
    }
    return name
  }

  private fun sanitizeIdentifier(name: String): String {
    return decapitalizeName(name.replace(Regex("[^a-zA-Z0-9]"), ""))
  }
}
