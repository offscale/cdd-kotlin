/** Ktor client interface, implementation, authentication, and OpenAPI KDoc code generator. */
package psi

import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.ExampleObject
import domain.ExternalDocumentation
import domain.Header
import domain.Info
import domain.Link
import domain.MediaTypeObject
import domain.ParameterLocation
import domain.PathItem
import domain.RequestBody
import domain.SecurityScheme
import domain.Server
import domain.Tag
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

    val importsBlock =
        """
            package $packageName
            
            ${baseImports.sorted().joinToString("\n") { "import $it" }}
            
            // Result is part of kotlin standard library since 1.3
        """
            .trimIndent()

    val interfaceName = "I$apiName"
    val implName = apiName

    // Generate Interface Methods
    val interfaceMethods =
        endpoints.joinToString("\n\n") { ep ->
          val signature = psi.network.EndpointMethodGenerator.generateMethodSignature(ep)
          val doc = psi.network.EndpointMethodGenerator.generateKDoc(ep)
          "$doc    $signature"
        }

    // Generate Implementation Methods
    val implMethodList = mutableListOf<String>()
    for (ep in endpoints) {
      val doc = psi.network.EndpointMethodGenerator.generateKDoc(ep)
      val impl =
          psi.network.EndpointMethodGenerator.generateMethodImpl(ep).getOrElse {
            return Result.failure(it)
          }
      implMethodList.add("$doc    $impl")
    }
    val implMethods = implMethodList.joinToString("\n\n")

    val helperFunctions = psi.network.ParameterSerializerGenerator.buildHelperFunctions(endpoints)

    // Server Logic
    val defaultUrl = if (resolvedServers.isNotEmpty()) resolvedServers.first().url else "/"
    val serverSupport = psi.network.ServerUrlResolverGenerator.buildServerSupport(resolvedServers)

    // Companion Object (Servers + Auth Factory)
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
          parts.joinToString(",\n                ")
        } else {
          "private val baseUrl: String = \"${psi.network.ServerUrlResolverGenerator.escapeKotlinString(defaultUrl)}\""
        }

    val interfaceDoc =
        generateInterfaceKDoc(
            resolvedMetadata, resolvedWebhooks, resolvedServers, resolvedSecuritySchemes)

    val content =
        """
            $importsBlock
            
            $interfaceDoc
            interface $interfaceName {
            $interfaceMethods
            }
            
            class $implName(
                private val client: HttpClient,
                $baseUrlParam
            ) : $interfaceName {
            $implMethods
            $helperFunctions
            $companionObject
            }
            
            class ApiException(message: String) : Exception(message)
        """
            .trimIndent()

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
      lines.add(" * @openapi ${renderOpenApiMeta(metadata)}")
    }
    if (metadata.info != null) {
      lines.add(" * @info ${renderInfo(metadata.info)}")
    }
    if (servers.isNotEmpty()) {
      val serversJson =
          psi.network.EndpointMethodGenerator.encodeCompactJson(
              servers.map { psi.metadata.InfoMetadataCodec.serverToDocValue(it) })
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
      lines.add(" * @tags ${renderTags(metadata.tags)}")
    }
    if (metadata.externalDocs != null) {
      lines.add(" * @externalDocs ${renderExternalDocs(metadata.externalDocs)}")
    }
    if (metadata.extensions.isNotEmpty()) {
      lines.add(
          " * @extensions ${psi.network.EndpointMethodGenerator.encodeCompactJson(metadata.extensions)}")
    }
    if (metadata.pathsExtensions.isNotEmpty()) {
      lines.add(" * @pathsExtensions ${renderExtensions(metadata.pathsExtensions)}")
    }
    if (metadata.pathsExplicitEmpty) {
      lines.add(" * @pathsEmpty")
    }
    if (metadata.pathItems.isNotEmpty()) {
      lines.add(" * @pathItems ${renderPathItems(metadata.pathItems)}")
    }
    if (metadata.webhooksExtensions.isNotEmpty()) {
      lines.add(" * @webhooksExtensions ${renderExtensions(metadata.webhooksExtensions)}")
    }
    if (metadata.webhooksExplicitEmpty) {
      lines.add(" * @webhooksEmpty")
    }
    if (securitySchemes.isNotEmpty()) {
      lines.add(" * @securitySchemes ${renderSecuritySchemes(securitySchemes)}")
    }
    if (metadata.componentSchemas.isNotEmpty()) {
      lines.add(" * @componentSchemas ${renderComponentSchemas(metadata.componentSchemas)}")
    }
    if (metadata.componentExamples.isNotEmpty()) {
      lines.add(" * @componentExamples ${renderComponentExamples(metadata.componentExamples)}")
    }
    if (metadata.componentLinks.isNotEmpty()) {
      lines.add(" * @componentLinks ${renderComponentLinks(metadata.componentLinks)}")
    }
    if (metadata.componentCallbacks.isNotEmpty()) {
      lines.add(
          " * @componentCallbacks ${psi.network.EndpointMethodGenerator.renderCallbacks(metadata.componentCallbacks)}")
    }
    if (metadata.componentParameters.isNotEmpty()) {
      lines.add(
          " * @componentParameters ${renderComponentParameters(metadata.componentParameters)}")
    }
    if (metadata.componentResponses.isNotEmpty()) {
      lines.add(" * @componentResponses ${renderComponentResponses(metadata.componentResponses)}")
    }
    if (metadata.componentRequestBodies.isNotEmpty()) {
      lines.add(
          " * @componentRequestBodies ${renderComponentRequestBodies(metadata.componentRequestBodies)}")
    }
    if (metadata.componentHeaders.isNotEmpty()) {
      lines.add(" * @componentHeaders ${renderComponentHeaders(metadata.componentHeaders)}")
    }
    if (metadata.componentPathItems.isNotEmpty()) {
      lines.add(" * @componentPathItems ${renderComponentPathItems(metadata.componentPathItems)}")
    }
    if (metadata.componentMediaTypes.isNotEmpty()) {
      lines.add(
          " * @componentMediaTypes ${renderComponentMediaTypes(metadata.componentMediaTypes)}")
    }
    if (metadata.componentsExtensions.isNotEmpty()) {
      lines.add(" * @componentsExtensions ${renderExtensions(metadata.componentsExtensions)}")
    }
    if (webhooks.isNotEmpty()) {
      lines.add(" * @webhooks ${renderWebhooks(webhooks)}")
    }
    if (lines.isEmpty()) return ""
    return "/**\n" + lines.joinToString("\n") + "\n */"
  }

  private fun renderOpenApiMeta(metadata: OpenApiMetadata): String =
      psi.metadata.InfoMetadataCodec.renderOpenApiMeta(metadata)

  private fun renderExtensions(extensions: Map<String, domain.OpenApiValue>): String =
      psi.network.EndpointMethodGenerator.renderExtensions(extensions)

  private fun renderPathItems(pathItems: Map<String, PathItem>): String =
      psi.metadata.ComponentsMetadataCodec.renderPathItems(pathItems)

  private fun renderInfo(info: Info): String = psi.metadata.InfoMetadataCodec.renderInfo(info)

  private fun renderTags(tags: List<Tag>): String = psi.metadata.TagsMetadataCodec.renderTags(tags)

  private fun renderExternalDocs(docs: ExternalDocumentation): String =
      psi.metadata.InfoMetadataCodec.renderExternalDocs(docs)

  private fun renderSecuritySchemes(securitySchemes: Map<String, SecurityScheme>): String =
      psi.metadata.SecurityMetadataCodec.renderSecuritySchemes(securitySchemes)

  private fun renderComponentSchemas(schemas: Map<String, domain.SchemaDefinition>): String =
      psi.metadata.ComponentsMetadataCodec.renderComponentSchemas(schemas)

  private fun renderComponentExamples(examples: Map<String, ExampleObject>): String {
    val mapped =
        examples.mapValues { psi.network.EndpointMethodGenerator.exampleObjectToDocValue(it.value) }
    return psi.network.EndpointMethodGenerator.encodeCompactJson(mapped)
  }

  private fun renderComponentLinks(links: Map<String, Link>): String {
    val mapped = links.mapValues { psi.network.EndpointMethodGenerator.linkToDocValue(it.value) }
    return psi.network.EndpointMethodGenerator.encodeCompactJson(mapped)
  }

  private fun renderComponentParameters(parameters: Map<String, EndpointParameter>): String =
      psi.metadata.ComponentsMetadataCodec.renderComponentParameters(parameters)

  private fun renderComponentResponses(responses: Map<String, EndpointResponse>): String =
      psi.metadata.ComponentsMetadataCodec.renderComponentResponses(responses)

  private fun renderComponentRequestBodies(bodies: Map<String, RequestBody>): String =
      psi.metadata.ComponentsMetadataCodec.renderComponentRequestBodies(bodies)

  private fun renderComponentHeaders(headers: Map<String, Header>): String =
      psi.metadata.ComponentsMetadataCodec.renderComponentHeaders(headers)

  private fun renderComponentPathItems(pathItems: Map<String, PathItem>): String =
      psi.metadata.ComponentsMetadataCodec.renderComponentPathItems(pathItems)

  private fun renderComponentMediaTypes(mediaTypes: Map<String, MediaTypeObject>): String =
      psi.metadata.ComponentsMetadataCodec.renderComponentMediaTypes(mediaTypes)

  private fun renderWebhooks(webhooks: Map<String, PathItem>): String =
      psi.metadata.ComponentsMetadataCodec.renderWebhooks(webhooks)

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
