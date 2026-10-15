/** OpenAPI `components`, security scheme, and intra-component `$ref` parser and resolver. */
package openapi.parser

import domain.Callback
import domain.Components
import domain.EndpointParameter
import domain.EndpointResponse
import domain.ExampleObject
import domain.Header
import domain.Link
import domain.MediaTypeObject
import domain.OAuthFlow
import domain.OAuthFlows
import domain.OpenApiValue
import domain.ReferenceObject
import domain.RequestBody
import domain.SecurityScheme
import openapi.JsonNode
import openapi.asObject
import openapi.boolean
import openapi.decodeJsonPointerToken
import openapi.fields
import openapi.get
import openapi.resolveUri
import openapi.text
import openapi.textValue

/**
 * Parses OpenAPI `components` sections (schemas, responses, parameters, requestBodies, headers,
 * securitySchemes, examples, links, callbacks, pathItems, mediaTypes) and resolves component
 * references.
 */
object ComponentParser {
  /**
   * Normalizes a `$self` or base URI by stripping any fragment component and trimming blanks.
   *
   * @param value Raw URI string.
   * @return Normalized base URI or `null`.
   */
  fun normalizeSelfBase(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return trimmed.substringBefore("#")
  }

  /**
   * Resolves a document `$self` URI against an optional retrieval [baseUri].
   *
   * @param self Document `$self` value.
   * @param baseUri Retrieval base URI.
   * @return Resolved base URI or `null`.
   */
  fun resolveSelfBase(self: String?, baseUri: String?): String? {
    val normalizedSelf = normalizeSelfBase(self)
    val normalizedBase = normalizeSelfBase(baseUri)
    if (normalizedSelf != null) {
      if (normalizedBase == null || isAbsoluteUri(normalizedSelf)) {
        return normalizedSelf
      }
      return resolveAgainstBase(normalizedBase, normalizedSelf)
    }
    return normalizedBase
  }

  private fun isAbsoluteUri(value: String): Boolean = openapi.isAbsoluteUri(value)

  private fun isSelfBaseMatch(refBase: String, contextBase: String?): Boolean {
    val selfBase = contextBase?.trimEnd('#').orEmpty()
    if (selfBase.isBlank()) return true
    val normalizedRefBase = refBase.trimEnd('#')
    return normalizedRefBase == selfBase
  }

  /**
   * Parses an OpenAPI 3.x `components` object node.
   *
   * @param node The `components` JSON node.
   * @param currentSelfBase Active `$self` base URI for reference resolution.
   * @return Parsed and reference-resolved [Components], or `null`.
   */
  fun parseComponents(node: JsonNode?, currentSelfBase: String? = null): Components? {
    val obj = node.asObject() ?: return null
    val headers = parseComponentHeaders(obj.get("headers"), currentSelfBase)
    val partialComponents = Components(headers = headers)
    val components =
        Components(
            schemas = SchemaNodeParser.parseSchemas(obj.get("schemas")),
            responses = parseComponentResponses(obj.get("responses"), currentSelfBase),
            parameters = parseComponentParameters(obj.get("parameters"), currentSelfBase),
            requestBodies = parseComponentRequestBodies(obj.get("requestBodies"), currentSelfBase),
            headers = headers,
            securitySchemes = parseSecuritySchemes(obj.get("securitySchemes")),
            examples =
                PathAndOperationParser.parseExampleMap(obj.get("examples"), null, currentSelfBase),
            links = PathAndOperationParser.parseLinks(obj.get("links"), null, currentSelfBase),
            callbacks =
                PathAndOperationParser.parseCallbacks(obj.get("callbacks"), null, currentSelfBase),
            pathItems =
                PathAndOperationParser.parsePathItems(obj.get("pathItems"), currentSelfBase),
            mediaTypes =
                PathAndOperationParser.parseContentMap(
                    obj.get("mediaTypes"), partialComponents, currentSelfBase),
            extensions = SchemaNodeParser.parseExtensions(obj))
    return resolveComponentRefs(components, currentSelfBase)
  }

  /**
   * Resolves intra-component `$ref` entries across all sections of [components].
   *
   * @param components Unresolved components object.
   * @param currentSelfBase Active `$self` base URI.
   * @return Resolved [Components].
   */
  fun resolveComponentRefs(components: Components, currentSelfBase: String? = null): Components {
    val resolvedParameters =
        components.parameters.mapValues { (_, param) ->
          resolveParameterComponentRef(param, components, mutableSetOf(), currentSelfBase)
        }
    val resolvedResponses =
        components.responses.mapValues { (_, response) ->
          resolveResponseComponentRef(response, components, mutableSetOf(), currentSelfBase)
        }
    val resolvedRequestBodies =
        components.requestBodies.mapValues { (_, body) ->
          resolveRequestBodyComponentRef(body, components, mutableSetOf(), currentSelfBase)
        }
    val resolvedHeaders =
        components.headers.mapValues { (_, header) ->
          resolveHeaderComponentRef(header, components, mutableSetOf(), currentSelfBase)
        }
    val resolvedLinks =
        components.links.mapValues { (_, link) ->
          resolveLinkComponentRef(link, components, mutableSetOf(), currentSelfBase)
        }
    val resolvedExamples =
        components.examples.mapValues { (_, example) ->
          resolveExampleComponentRef(example, components, mutableSetOf(), currentSelfBase)
        }
    val resolvedMediaTypes =
        components.mediaTypes.mapValues { (_, media) ->
          resolveMediaTypeComponentRef(media, components, mutableSetOf(), currentSelfBase)
        }
    val resolvedSecuritySchemes =
        components.securitySchemes.mapValues { (_, scheme) ->
          resolveSecuritySchemeComponentRef(scheme, components, mutableSetOf(), currentSelfBase)
        }
    val resolvedCallbacks =
        components.callbacks.mapValues { (_, callback) ->
          resolveCallbackComponentRef(callback, components, mutableSetOf(), currentSelfBase)
        }

    return components.copy(
        parameters = resolvedParameters,
        responses = resolvedResponses,
        requestBodies = resolvedRequestBodies,
        headers = resolvedHeaders,
        links = resolvedLinks,
        examples = resolvedExamples,
        mediaTypes = resolvedMediaTypes,
        securitySchemes = resolvedSecuritySchemes,
        callbacks = resolvedCallbacks)
  }

  private fun <T> resolveSingleComponentRef(
      item: T,
      section: String,
      components: Components,
      visited: MutableSet<String>,
      contextBase: String?,
      refExtractor: (T) -> String?,
      mapSelector: (Components) -> Map<String, T>,
      merge: (current: T, resolved: T, ref: String) -> T
  ): T {
    val ref = refExtractor(item) ?: return item
    val lookup = resolveComponentLookup(ref, section, components, contextBase) ?: return item
    val visitKey = "$section:${lookup.base.orEmpty()}:${lookup.key}"
    if (!visited.add(visitKey)) return item
    val target = mapSelector(lookup.components)[lookup.key] ?: return item
    val resolved =
        resolveSingleComponentRef(
            target,
            section,
            lookup.components,
            visited,
            lookup.base,
            refExtractor,
            mapSelector,
            merge)
    return merge(item, resolved, ref)
  }

  private fun firstText(vararg values: String?): String? = values.firstOrNull { it != null }

  private fun resolveParameterComponentRef(
      param: EndpointParameter,
      components: Components,
      visited: MutableSet<String>,
      contextBase: String?
  ): EndpointParameter {
    return resolveSingleComponentRef(
        param,
        "parameters",
        components,
        visited,
        contextBase,
        { it.reference?.ref },
        { it.parameters }) { current, resolved, _ ->
          val reference = current.reference!!
          resolved.copy(
              description =
                  firstText(reference.description, current.description, resolved.description),
              reference = reference,
              extensions = mergeExtensions(resolved.extensions, current.extensions))
        }
  }

  private fun resolveResponseComponentRef(
      response: EndpointResponse,
      components: Components,
      visited: MutableSet<String>,
      contextBase: String?
  ): EndpointResponse {
    return resolveSingleComponentRef(
        response,
        "responses",
        components,
        visited,
        contextBase,
        { it.reference?.ref },
        { it.responses }) { current, resolved, ref ->
          val reference = current.reference!!
          val responseDesc = if (current.description == "ref:$ref") null else current.description
          resolved.copy(
              statusCode = current.statusCode,
              summary = firstText(reference.summary, current.summary, resolved.summary),
              description = firstText(reference.description, responseDesc, resolved.description),
              reference = reference,
              extensions = mergeExtensions(resolved.extensions, current.extensions))
        }
  }

  private fun resolveRequestBodyComponentRef(
      body: RequestBody,
      components: Components,
      visited: MutableSet<String>,
      contextBase: String?
  ): RequestBody {
    return resolveSingleComponentRef(
        body,
        "requestBodies",
        components,
        visited,
        contextBase,
        { it.reference?.ref },
        { it.requestBodies }) { current, resolved, _ ->
          val reference = current.reference!!
          resolved.copy(
              description =
                  firstText(reference.description, current.description, resolved.description),
              reference = reference,
              extensions = mergeExtensions(resolved.extensions, current.extensions))
        }
  }

  private fun resolveHeaderComponentRef(
      header: Header,
      components: Components,
      visited: MutableSet<String>,
      contextBase: String?
  ): Header {
    return resolveSingleComponentRef(
        header,
        "headers",
        components,
        visited,
        contextBase,
        { it.reference?.ref },
        { it.headers }) { current, resolved, _ ->
          val reference = current.reference!!
          resolved.copy(
              description =
                  firstText(reference.description, current.description, resolved.description),
              reference = reference,
              extensions = mergeExtensions(resolved.extensions, current.extensions))
        }
  }

  private fun resolveLinkComponentRef(
      link: Link,
      components: Components,
      visited: MutableSet<String>,
      contextBase: String?
  ): Link {
    return resolveSingleComponentRef(
        link,
        "links",
        components,
        visited,
        contextBase,
        { it.ref ?: it.reference?.ref },
        { it.links }) { current, resolved, ref ->
          val reference = current.reference ?: ReferenceObject(ref = ref)
          resolved.copy(
              ref = ref,
              reference = reference,
              description =
                  firstText(reference.description, current.description, resolved.description),
              extensions = mergeExtensions(resolved.extensions, current.extensions))
        }
  }

  private fun resolveExampleComponentRef(
      example: ExampleObject,
      components: Components,
      visited: MutableSet<String>,
      contextBase: String?
  ): ExampleObject {
    return resolveSingleComponentRef(
        example, "examples", components, visited, contextBase, { it.ref }, { it.examples }) {
            current,
            resolved,
            ref ->
          resolved.copy(
              ref = ref,
              summary = firstText(current.summary, resolved.summary),
              description = firstText(current.description, resolved.description),
              extensions = mergeExtensions(resolved.extensions, current.extensions))
        }
  }

  private fun resolveMediaTypeComponentRef(
      media: MediaTypeObject,
      components: Components,
      visited: MutableSet<String>,
      contextBase: String?
  ): MediaTypeObject {
    return resolveSingleComponentRef(
        media,
        "mediaTypes",
        components,
        visited,
        contextBase,
        { it.ref ?: it.reference?.ref },
        { it.mediaTypes }) { current, resolved, ref ->
          val reference = current.reference ?: ReferenceObject(ref = ref)
          resolved.copy(
              ref = ref,
              reference = reference,
              extensions = mergeExtensions(resolved.extensions, current.extensions))
        }
  }

  /**
   * Resolves a [Callback] component reference against [components].
   *
   * @param callback Callback to resolve.
   * @param components Components registry.
   * @param visited Visited reference keys for cycle detection.
   * @param contextBase Active `$self` base URI.
   * @return Resolved [Callback].
   */
  fun resolveCallbackComponentRef(
      callback: Callback,
      components: Components,
      visited: MutableSet<String> = mutableSetOf(),
      contextBase: String? = null
  ): Callback {
    return resolveSingleComponentRef(
        callback,
        "callbacks",
        components,
        visited,
        contextBase,
        { if (it is Callback.Reference) it.reference.ref else null },
        { it.callbacks }) { current, resolved, _ ->
          val refObj = (current as Callback.Reference).reference
          when (resolved) {
            is Callback.Inline -> resolved.copy(reference = refObj)
            is Callback.Reference -> current
          }
        }
  }

  private fun resolveSecuritySchemeComponentRef(
      scheme: SecurityScheme,
      components: Components,
      visited: MutableSet<String>,
      contextBase: String?
  ): SecurityScheme {
    return resolveSingleComponentRef(
        scheme,
        "securitySchemes",
        components,
        visited,
        contextBase,
        { it.reference?.ref },
        { it.securitySchemes }) { current, resolved, _ ->
          val reference = current.reference!!
          resolved.copy(
              description =
                  firstText(reference.description, current.description, resolved.description),
              reference = reference,
              extensions = mergeExtensions(resolved.extensions, current.extensions))
        }
  }

  private fun mergeExtensions(
      base: Map<String, OpenApiValue>,
      overrides: Map<String, OpenApiValue>
  ): Map<String, OpenApiValue> {
    if (base.isEmpty() and overrides.isEmpty()) return emptyMap()
    val merged = LinkedHashMap<String, OpenApiValue>()
    merged.putAll(base)
    merged.putAll(overrides)
    return merged
  }

  /**
   * Parses a map of [SecurityScheme]s from `securitySchemes` or `securityDefinitions`.
   *
   * @param node The JSON object node.
   * @return Map of scheme names to [SecurityScheme]s.
   */
  fun parseSecuritySchemes(node: JsonNode?): Map<String, SecurityScheme> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) -> name to parseSecurityScheme(raw) }
  }

  private fun parseSecurityScheme(node: JsonNode): SecurityScheme {
    val obj = node.asObject() ?: return SecurityScheme(type = "apiKey")
    val ref = obj.text("\$ref")
    if (ref != null) {
      val reference =
          ReferenceObject(
              ref = ref, summary = obj.text("summary"), description = obj.text("description"))
      return SecurityScheme(reference = reference, description = reference.description)
    }
    return SecurityScheme(
        type = obj.text("type") ?: "apiKey",
        description = obj.text("description"),
        name = obj.text("name"),
        `in` = obj.text("in"),
        scheme = obj.text("scheme"),
        bearerFormat = obj.text("bearerFormat"),
        flows = parseOAuthFlows(obj.get("flows")),
        openIdConnectUrl = obj.text("openIdConnectUrl"),
        oauth2MetadataUrl = obj.text("oauth2MetadataUrl"),
        flow = obj.text("flow"),
        authorizationUrl = obj.text("authorizationUrl"),
        tokenUrl = obj.text("tokenUrl"),
        scopes = parseScopesMap(obj.get("scopes")),
        deprecated = obj.boolean("deprecated") ?: false,
        extensions = SchemaNodeParser.parseExtensions(obj))
  }

  internal fun parseScopesMap(node: JsonNode?): Map<String, String>? {
    val obj = node.asObject() ?: return null
    return obj.fields().asSequence().associate { (k, v) -> k to (v.textValue() ?: v.toString()) }
  }

  private fun parseOAuthFlows(node: JsonNode?): OAuthFlows? {
    val obj = node.asObject() ?: return null
    return OAuthFlows(
        implicit = parseOAuthFlow(obj.get("implicit")),
        password = parseOAuthFlow(obj.get("password")),
        clientCredentials = parseOAuthFlow(obj.get("clientCredentials")),
        authorizationCode = parseOAuthFlow(obj.get("authorizationCode")),
        deviceAuthorization = parseOAuthFlow(obj.get("deviceAuthorization")),
        extensions = SchemaNodeParser.parseExtensions(obj))
  }

  private fun parseOAuthFlow(node: JsonNode?): OAuthFlow? {
    val obj = node.asObject() ?: return null
    return OAuthFlow(
        authorizationUrl = obj.text("authorizationUrl"),
        tokenUrl = obj.text("tokenUrl"),
        refreshUrl = obj.text("refreshUrl"),
        scopes = parseScopesMap(obj.get("scopes")).orEmpty(),
        deviceAuthorizationUrl = obj.text("deviceAuthorizationUrl"),
        extensions = SchemaNodeParser.parseExtensions(obj))
  }

  private data class ComponentRef(val base: String?, val key: String)

  private data class ComponentLookup(
      val key: String,
      val components: Components,
      val base: String?
  )

  /**
   * Resolves a parameter `$ref` against [components].
   *
   * @param ref Reference URI.
   * @param components Optional components object.
   * @param currentSelfBase Active `$self` base URI.
   * @return Resolved [EndpointParameter] or `null`.
   */
  fun resolveParameterRef(
      ref: String,
      components: Components?,
      currentSelfBase: String? = null
  ): EndpointParameter? {
    val lookup =
        resolveComponentLookup(ref, "parameters", components, currentSelfBase) ?: return null
    return lookup.components.parameters[lookup.key]
  }

  /**
   * Resolves a media type `$ref` against [components].
   *
   * @param ref Reference URI.
   * @param components Optional components object.
   * @param currentSelfBase Active `$self` base URI.
   * @return Resolved [MediaTypeObject] or `null`.
   */
  fun resolveMediaTypeRef(
      ref: String,
      components: Components?,
      currentSelfBase: String? = null
  ): MediaTypeObject? {
    val lookup =
        resolveComponentLookup(ref, "mediaTypes", components, currentSelfBase) ?: return null
    return lookup.components.mediaTypes[lookup.key]
  }

  /**
   * Resolves a response `$ref` against [components].
   *
   * @param ref Reference URI.
   * @param components Optional components object.
   * @param currentSelfBase Active `$self` base URI.
   * @return Resolved [EndpointResponse] or `null`.
   */
  fun resolveResponseRef(
      ref: String,
      components: Components?,
      currentSelfBase: String? = null
  ): EndpointResponse? {
    val lookup =
        resolveComponentLookup(ref, "responses", components, currentSelfBase) ?: return null
    return lookup.components.responses[lookup.key]
  }

  /**
   * Resolves a requestBody `$ref` against [components].
   *
   * @param ref Reference URI.
   * @param components Optional components object.
   * @param currentSelfBase Active `$self` base URI.
   * @return Resolved [RequestBody] or `null`.
   */
  fun resolveRequestBodyRef(
      ref: String,
      components: Components?,
      currentSelfBase: String? = null
  ): RequestBody? {
    val lookup =
        resolveComponentLookup(ref, "requestBodies", components, currentSelfBase) ?: return null
    return lookup.components.requestBodies[lookup.key]
  }

  /**
   * Resolves a header `$ref` against [components].
   *
   * @param ref Reference URI.
   * @param components Optional components object.
   * @param currentSelfBase Active `$self` base URI.
   * @return Resolved [Header] or `null`.
   */
  fun resolveHeaderRef(
      ref: String,
      components: Components?,
      currentSelfBase: String? = null
  ): Header? {
    val lookup = resolveComponentLookup(ref, "headers", components, currentSelfBase) ?: return null
    return lookup.components.headers[lookup.key]
  }

  /**
   * Resolves a link `$ref` against [components].
   *
   * @param ref Reference URI.
   * @param components Optional components object.
   * @param currentSelfBase Active `$self` base URI.
   * @return Resolved [Link] or `null`.
   */
  fun resolveLinkRef(ref: String, components: Components?, currentSelfBase: String? = null): Link? {
    val lookup = resolveComponentLookup(ref, "links", components, currentSelfBase) ?: return null
    return lookup.components.links[lookup.key]
  }

  /**
   * Resolves an example `$ref` against [components].
   *
   * @param ref Reference URI.
   * @param components Optional components object.
   * @param currentSelfBase Active `$self` base URI.
   * @return Resolved [ExampleObject] or `null`.
   */
  fun resolveExampleRef(
      ref: String,
      components: Components?,
      currentSelfBase: String? = null
  ): ExampleObject? {
    val lookup = resolveComponentLookup(ref, "examples", components, currentSelfBase) ?: return null
    return lookup.components.examples[lookup.key]
  }

  private fun resolveComponentLookup(
      ref: String,
      component: String,
      localComponents: Components?,
      contextBase: String?
  ): ComponentLookup? {
    val componentRef = extractComponentRef(ref, component, contextBase) ?: return null
    val baseMatches = componentRef.base == null || isSelfBaseMatch(componentRef.base, contextBase)
    val resolvedComponents = if (baseMatches) localComponents else null
    if (resolvedComponents == null) return null
    val effectiveBase = componentRef.base ?: normalizeSelfBase(contextBase)
    return ComponentLookup(componentRef.key, resolvedComponents, effectiveBase)
  }

  private fun extractComponentRef(
      ref: String,
      component: String,
      contextBase: String?
  ): ComponentRef? {
    val marker = "#/components/$component/"
    val index = ref.indexOf(marker)
    if (index < 0) return null
    val refBase = ref.substring(0, index)
    val raw = ref.substring(index + marker.length)
    if (raw.isBlank() or raw.contains("/")) return null
    val key = decodeJsonPointer(raw)
    val base = resolveReferenceBase(refBase, contextBase)
    return ComponentRef(base = base, key = key)
  }

  private fun resolveReferenceBase(refBase: String, contextBase: String?): String? {
    val normalized = normalizeSelfBase(refBase) ?: return normalizeSelfBase(contextBase)
    val resolved =
        if (contextBase != null) resolveAgainstBase(contextBase, normalized) else normalized
    return normalizeSelfBase(resolved)
  }

  private fun resolveAgainstBase(base: String, ref: String): String = resolveUri(base, ref)

  private fun decodeJsonPointer(value: String): String = decodeJsonPointerToken(value)

  /**
   * Parses reusable component responses.
   *
   * @param node The `responses` JSON object node.
   * @param currentSelfBase Active `$self` base URI.
   * @return Map of response name to [EndpointResponse].
   */
  fun parseComponentResponses(
      node: JsonNode?,
      currentSelfBase: String? = null
  ): Map<String, EndpointResponse> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      name to PathAndOperationParser.parseResponse(name, raw, null, currentSelfBase)
    }
  }

  /**
   * Parses reusable component parameters.
   *
   * @param node The `parameters` JSON object node.
   * @param currentSelfBase Active `$self` base URI.
   * @return Map of parameter name to [EndpointParameter].
   */
  fun parseComponentParameters(
      node: JsonNode?,
      currentSelfBase: String? = null
  ): Map<String, EndpointParameter> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      val param = PathAndOperationParser.parseParameter(raw, null, currentSelfBase)
      val nameValue = if (param.name == "param") name else param.name
      name to param.copy(name = nameValue)
    }
  }

  /**
   * Parses reusable component requestBodies.
   *
   * @param node The `requestBodies` JSON object node.
   * @param currentSelfBase Active `$self` base URI.
   * @return Map of requestBody name to [RequestBody].
   */
  fun parseComponentRequestBodies(
      node: JsonNode?,
      currentSelfBase: String? = null
  ): Map<String, RequestBody> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      name to (PathAndOperationParser.parseRequestBody(raw, null, currentSelfBase) ?: RequestBody())
    }
  }

  /**
   * Parses reusable component headers.
   *
   * @param node The `headers` JSON object node.
   * @param currentSelfBase Active `$self` base URI.
   * @return Map of header name to [Header].
   */
  fun parseComponentHeaders(node: JsonNode?, currentSelfBase: String? = null): Map<String, Header> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      name to PathAndOperationParser.parseHeader(raw, null, currentSelfBase)
    }
  }
}
