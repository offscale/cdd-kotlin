/** Ktor client interface, implementation, authentication, and OpenAPI KDoc code generator. */
package psi

import domain.EndpointDefinition
import domain.ParameterLocation
import domain.PathItem
import domain.SecurityScheme
import domain.Server
import org.jetbrains.kotlin.psi.KtFile

/**
 * Generates Ktor Network Interface and Implementation code from Endpoint Definitions. Supports:
 * - Result<T> return types
 * - Path, Query, Querystring, Header, Cookie parameters
 * - Parameter Serialization Styles (Matrix, Label, Form, Pipe/Space Delimited, DeepObject) and
 *   Explode logic
 * - Query parameter allowReserved/allowEmptyValue behaviors for compliant URL serialization
 * - Server Base URL configuration
 * - KDoc generation (including callbacks, response summaries, and operation extensions)
 * - Interface-level KDoc for root OpenAPI metadata (info, servers, security, tags, externalDocs,
 *   extensions, securitySchemes)
 * - Ktor Auth Plugin configuration (Basic, Bearer, ApiKey)
 * - Header/Cookie/Path array & object serialization (simple, matrix, label, cookie/form)
 */
class NetworkGenerator {

  private val psiFactory = PsiInfrastructure.createPsiFactory()

  /**
   * Generates a complete API file with Interface, Implementation, and Exception classes wrapped in
   * a [Result].
   *
   * @param packageName The target package.
   * @param apiName The class name for the API.
   * @param endpoints List of endpoints to generate.
   * @param servers List of Server definitions for configuration.
   * @param securitySchemes Map of Security Schemes defined in Components.
   * @param webhooks Optional webhook definitions to preserve via KDoc metadata.
   * @param metadata Optional root-level OpenAPI metadata to preserve via interface KDoc.
   * @return A [Result] containing the generated [KtFile] or a
   *   [domain.CddError.ParameterSerializationError].
   */
  fun generateApi(
      packageName: String,
      apiName: String,
      endpoints: List<EndpointDefinition>,
      servers: List<Server> = emptyList(),
      securitySchemes: Map<String, SecurityScheme> = emptyMap(),
      webhooks: Map<String, PathItem> = emptyMap(),
      metadata: OpenApiMetadata? = null
  ): Result<KtFile> {
    val resolvedMetadata = metadata ?: OpenApiMetadata()
    val resolvedServers =
        if (resolvedMetadata.servers.isNotEmpty()) resolvedMetadata.servers else servers
    val resolvedSecuritySchemes =
        if (resolvedMetadata.securitySchemes.isNotEmpty()) {
          resolvedMetadata.securitySchemes
        } else {
          securitySchemes
        }
    val resolvedWebhooks =
        LinkedHashMap<String, PathItem>().apply {
          putAll(resolvedMetadata.webhooks)
          putAll(webhooks)
        }

    val needsQueryStringEncoding =
        endpoints.any { ep ->
          ep.parameters.any {
            it.location == ParameterLocation.QUERYSTRING && it.content.isNotEmpty()
          }
        }
    val needsParameterContentSerialization =
        endpoints.any { ep ->
          ep.parameters.any {
            it.location != ParameterLocation.QUERYSTRING && it.content.isNotEmpty()
          }
        }
    val needsFormBodyEncoding =
        endpoints.any { ep ->
          psi.network.EndpointMethodGenerator.resolveRequestContentType(ep)?.let {
            psi.network.EndpointMethodGenerator.isFormUrlEncodedMediaType(it)
          } == true
        }
    val needsMultipartBodyEncoding =
        endpoints.any { ep ->
          psi.network.EndpointMethodGenerator.resolveRequestContentType(ep)?.let {
            psi.network.EndpointMethodGenerator.isMultipartFormDataMediaType(it)
          } == true
        }
    val needsMultipartPositionalEncoding =
        endpoints.any { ep ->
          psi.network.EndpointMethodGenerator.requiresMultipartPositionalEncoding(ep)
        }
    val needsMultipartEncoding = needsMultipartBodyEncoding || needsMultipartPositionalEncoding
    val needsSequentialJsonEncoding =
        endpoints.any { ep ->
          psi.network.EndpointMethodGenerator.requiresSequentialJsonRequest(ep)
        }
    val needsSequentialJsonDecoding =
        endpoints.any { ep ->
          psi.network.EndpointMethodGenerator.requiresSequentialJsonResponse(ep)
        }
    val needsSequentialJsonSupport = needsSequentialJsonEncoding || needsSequentialJsonDecoding
    val needsOAuthHelpers =
        resolvedSecuritySchemes.values.any { scheme ->
          scheme.type == "oauth2" || scheme.type == "openIdConnect"
        }
    val needsJsonEncoding =
        needsQueryStringEncoding ||
            needsParameterContentSerialization ||
            needsFormBodyEncoding ||
            needsMultipartEncoding ||
            needsSequentialJsonSupport ||
            needsOAuthHelpers

    // imports
    val baseImports =
        mutableSetOf(
            "io.ktor.client.*",
            "io.ktor.client.call.*",
            "io.ktor.client.request.*",
            "io.ktor.client.request.setBody",
            "io.ktor.http.*")

    if (needsFormBodyEncoding || needsMultipartEncoding || needsOAuthHelpers) {
      baseImports.add("io.ktor.client.request.forms.*")
    }
    if (needsFormBodyEncoding) {
      baseImports.add("io.ktor.http.content.*")
    }
    if (needsSequentialJsonDecoding) {
      baseImports.add("io.ktor.client.statement.*")
    }
    if (needsOAuthHelpers) {
      baseImports.add("io.ktor.client.statement.*")
      baseImports.add("java.security.MessageDigest")
      baseImports.add("java.util.Base64")
      baseImports.add("kotlin.random.Random")
      baseImports.add("kotlinx.coroutines.delay")
    }

    if (needsJsonEncoding) {
      baseImports.add("kotlinx.serialization.json.encodeToJsonElement")
      baseImports.add("kotlinx.serialization.encodeToString")
      if (needsSequentialJsonDecoding) {
        baseImports.add("kotlinx.serialization.decodeFromString")
      }
      baseImports.add("kotlinx.serialization.json.*")
    }

    // Add Auth imports if needed
    if (resolvedSecuritySchemes.isNotEmpty()) {
      baseImports.add("io.ktor.client.plugins.*")
      baseImports.add("io.ktor.client.plugins.auth.*")
      baseImports.add("io.ktor.client.plugins.auth.providers.*")
    }

    val basePackage = packageName.substringBeforeLast(".")
    baseImports.add("${basePackage}.models.*")

    val nl = 10.toChar()
    val q = '"'
    val interfaceName = "I$apiName"
    val implName = apiName

    val interfaceMethods =
        endpoints.joinToString("$nl$nl") { ep ->
          val signature = psi.network.EndpointMethodGenerator.generateMethodSignature(ep)
          val doc = psi.network.EndpointMethodGenerator.generateKDoc(ep)
          "$doc    $signature"
        }

    val implMethodList = mutableListOf<String>()
    for (ep in endpoints) {
      val doc = psi.network.EndpointMethodGenerator.generateKDoc(ep)
      val impl =
          psi.network.EndpointMethodGenerator.generateMethodImpl(ep).getOrElse {
            return Result.failure(it)
          }
      implMethodList.add("$doc    $impl")
    }
    val implMethods = implMethodList.joinToString("$nl$nl")

    val helperFunctions = psi.network.ParameterSerializerGenerator.buildHelperFunctions(endpoints)

    val defaultUrl = if (resolvedServers.isNotEmpty()) resolvedServers.first().url else "/"
    val serverSupport = psi.network.ServerUrlResolverGenerator.buildServerSupport(resolvedServers)

    val companionObject =
        buildCompanionObject(resolvedServers, resolvedSecuritySchemes, serverSupport)

    val baseUrlParam =
        if (serverSupport != null) {
          val parts = mutableListOf("serverIndex: Int = 0", "serverName: String? = null")
          if (serverSupport.hasVariables) {
            parts.add("serverVariables: ServerVariables = ServerVariables()")
          }
          val defaultCall =
              if (serverSupport.hasVariables) {
                "defaultBaseUrl(serverIndex, serverName, serverVariables)"
              } else {
                "defaultBaseUrl(serverIndex, serverName)"
              }
          parts.add("private val baseUrl: String = $defaultCall")
          parts.joinToString(",$nl                ")
        } else {
          "private val baseUrl: String = $q${psi.network.ServerUrlResolverGenerator.escapeKotlinString(defaultUrl)}$q"
        }

    val interfaceDoc =
        generateInterfaceKDoc(
            resolvedMetadata, resolvedWebhooks, resolvedServers, resolvedSecuritySchemes)

    val content =
        buildKotlinFile(packageName) {
          baseImports.sorted().forEach { import(it) }
          line("// Result is part of kotlin standard library since 1.3")
          line()
          if (interfaceDoc.isNotEmpty()) {
            raw("$interfaceDoc$nl")
          }
          line("interface $interfaceName {")
          if (interfaceMethods.isNotEmpty()) {
            raw("$interfaceMethods$nl")
          }
          line("}")
          line()
          line("class $implName(")
          line("    private val client: HttpClient,")
          line("    $baseUrlParam")
          line(") : $interfaceName {")
          if (implMethods.isNotEmpty()) {
            raw("$implMethods$nl")
          }
          if (helperFunctions.isNotEmpty()) {
            raw("$helperFunctions$nl")
          }
          if (companionObject.isNotEmpty()) {
            line("    companion object {")
            raw("${companionObject.prependIndent("    ")}$nl")
            line("    }")
          }
          line("}")
          line()
          line("class ApiException(message: String) : Exception(message)")
        }

    return Result.success(psiFactory.createFile("$apiName.kt", content))
  }

  private fun generateInterfaceKDoc(
      metadata: OpenApiMetadata,
      webhooks: Map<String, PathItem>,
      servers: List<Server>,
      securitySchemes: Map<String, SecurityScheme>
  ): String {
    val lines = mutableListOf<String>()
    if (metadata.openapi != null || metadata.jsonSchemaDialect != null || metadata.self != null) {
      lines.add(" * @openapi ${psi.metadata.InfoMetadataCodec.renderOpenApiMeta(metadata)}")
    }
    if (metadata.info != null) {
      lines.add(" * @info ${psi.metadata.InfoMetadataCodec.renderInfo(metadata.info)}")
    }
    if (servers.isNotEmpty()) {
      val serversJson =
          psi.network.EndpointMethodGenerator.encodeCompactJson(
              domain.OpenApiArray(
                  servers.map { psi.metadata.InfoMetadataCodec.serverToDocValue(it) }))
      lines.add(" * @servers $serversJson")
    }
    if (metadata.securityExplicitEmpty && metadata.security.isEmpty()) {
      lines.add(" * @securityEmpty")
    }
    if (metadata.security.isNotEmpty()) {
      metadata.security.forEach { requirement ->
        lines.add(
            " * @security ${psi.metadata.SecurityMetadataCodec.renderSecurityRequirement(requirement)}")
      }
    }
    if (metadata.tags.isNotEmpty()) {
      lines.add(" * @tags ${psi.metadata.TagsMetadataCodec.renderTags(metadata.tags)}")
    }
    if (metadata.externalDocs != null) {
      lines.add(
          " * @externalDocs ${psi.metadata.InfoMetadataCodec.renderExternalDocs(metadata.externalDocs)}")
    }
    if (metadata.extensions.isNotEmpty()) {
      lines.add(
          " * @extensions ${psi.network.EndpointMethodGenerator.encodeCompactJson(domain.OpenApiObject(metadata.extensions))}")
    }
    if (metadata.pathsExtensions.isNotEmpty()) {
      lines.add(
          " * @pathsExtensions ${psi.network.EndpointMethodGenerator.renderExtensions(metadata.pathsExtensions)}")
    }
    if (metadata.pathsExplicitEmpty) {
      lines.add(" * @pathsEmpty")
    }
    if (metadata.pathItems.isNotEmpty()) {
      lines.add(
          " * @pathItems ${psi.metadata.ComponentsMetadataCodec.renderPathItems(metadata.pathItems)}")
    }
    if (metadata.webhooksExtensions.isNotEmpty()) {
      lines.add(
          " * @webhooksExtensions ${psi.network.EndpointMethodGenerator.renderExtensions(metadata.webhooksExtensions)}")
    }
    if (metadata.webhooksExplicitEmpty) {
      lines.add(" * @webhooksEmpty")
    }
    if (securitySchemes.isNotEmpty()) {
      lines.add(
          " * @securitySchemes ${psi.metadata.SecurityMetadataCodec.renderSecuritySchemes(securitySchemes)}")
    }
    if (metadata.componentSchemas.isNotEmpty()) {
      lines.add(
          " * @componentSchemas ${psi.metadata.ComponentsMetadataCodec.renderComponentSchemas(metadata.componentSchemas)}")
    }
    if (metadata.componentExamples.isNotEmpty()) {
      val mapped =
          metadata.componentExamples.mapValues {
            psi.network.EndpointMethodGenerator.exampleObjectToDocValue(it.value)
          }
      lines.add(
          " * @componentExamples ${psi.network.EndpointMethodGenerator.encodeCompactJson(domain.OpenApiObject(mapped))}")
    }
    if (metadata.componentLinks.isNotEmpty()) {
      val mapped =
          metadata.componentLinks.mapValues {
            psi.network.EndpointMethodGenerator.linkToDocValue(it.value)
          }
      lines.add(
          " * @componentLinks ${psi.network.EndpointMethodGenerator.encodeCompactJson(domain.OpenApiObject(mapped))}")
    }
    if (metadata.componentCallbacks.isNotEmpty()) {
      lines.add(
          " * @componentCallbacks ${psi.network.EndpointMethodGenerator.renderCallbacks(metadata.componentCallbacks)}")
    }
    if (metadata.componentParameters.isNotEmpty()) {
      lines.add(
          " * @componentParameters ${psi.metadata.ComponentsMetadataCodec.renderComponentParameters(metadata.componentParameters)}")
    }
    if (metadata.componentResponses.isNotEmpty()) {
      lines.add(
          " * @componentResponses ${psi.metadata.ComponentsMetadataCodec.renderComponentResponses(metadata.componentResponses)}")
    }
    if (metadata.componentRequestBodies.isNotEmpty()) {
      lines.add(
          " * @componentRequestBodies ${psi.metadata.ComponentsMetadataCodec.renderComponentRequestBodies(metadata.componentRequestBodies)}")
    }
    if (metadata.componentHeaders.isNotEmpty()) {
      lines.add(
          " * @componentHeaders ${psi.metadata.ComponentsMetadataCodec.renderComponentHeaders(metadata.componentHeaders)}")
    }
    if (metadata.componentPathItems.isNotEmpty()) {
      lines.add(
          " * @componentPathItems ${psi.metadata.ComponentsMetadataCodec.renderComponentPathItems(metadata.componentPathItems)}")
    }
    if (metadata.componentMediaTypes.isNotEmpty()) {
      lines.add(
          " * @componentMediaTypes ${psi.metadata.ComponentsMetadataCodec.renderComponentMediaTypes(metadata.componentMediaTypes)}")
    }
    if (metadata.componentsExtensions.isNotEmpty()) {
      lines.add(
          " * @componentsExtensions ${psi.network.EndpointMethodGenerator.renderExtensions(metadata.componentsExtensions)}")
    }
    if (webhooks.isNotEmpty()) {
      lines.add(" * @webhooks ${psi.metadata.ComponentsMetadataCodec.renderWebhooks(webhooks)}")
    }
    if (lines.isEmpty()) return ""
    return "/**\n" + lines.joinToString("\n") + "\n */"
  }

  private fun buildCompanionObject(
      servers: List<Server>,
      securitySchemes: Map<String, SecurityScheme>,
      serverSupport: psi.network.ServerSupport?
  ): String {
    val blocks = mutableListOf<String>()

    if (serverSupport != null) {
      blocks.add(serverSupport.companionSnippet)
    } else if (servers.isNotEmpty()) {
      val listStr =
          servers.joinToString(", ") {
            "\"${psi.network.ServerUrlResolverGenerator.escapeKotlinString(it.url)}\""
          }
      blocks.add("val SERVERS = listOf($listStr)")
    }

    if (securitySchemes.isNotEmpty()) {
      blocks.add(psi.network.AuthCodeGenerator.generateAuthFactory(securitySchemes))
    }

    if (blocks.isEmpty()) return ""

    return blocks.joinToString("\n\n").prependIndent("    ")
  }

  /**
   * Generates the Kotlin function signature for an endpoint returning `Result<T>`.
   *
   * @param ep Endpoint definition.
   * @return Signature string.
   */
  fun generateMethodSignature(ep: EndpointDefinition): String =
      psi.network.EndpointMethodGenerator.generateMethodSignature(ep, false)
}
