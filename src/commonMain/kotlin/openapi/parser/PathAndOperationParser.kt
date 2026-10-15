/**
 * OpenAPI `paths`, `webhooks`, [PathItem], [EndpointDefinition], [EndpointParameter],
 * [RequestBody], [EndpointResponse], [Callback], [Header], [Link], and [MediaTypeObject] parser.
 */
package openapi.parser

import domain.Callback
import domain.Components
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.ExampleObject
import domain.Header
import domain.HttpMethod
import domain.Link
import domain.MediaTypeObject
import domain.OpenApiValue
import domain.ParameterLocation
import domain.ParameterStyle
import domain.PathItem
import domain.ReferenceObject
import domain.RequestBody
import domain.SecurityRequirement
import domain.Server
import domain.ServerVariable
import domain.Tag
import openapi.JsonNode
import openapi.asArray
import openapi.asObject
import openapi.boolean
import openapi.fields
import openapi.get
import openapi.has
import openapi.isNullNode
import openapi.isObject
import openapi.size
import openapi.text
import openapi.textValue
import psi.ReferenceResolver
import psi.TypeMappers

/**
 * Result of parsing a `paths` or `webhooks` object node.
 *
 * @property items Parsed path items keyed by path or webhook name.
 * @property extensions Specification extensions (`x-*`) on the container object.
 * @property explicitEmpty Whether the object was explicitly present and empty (`{}`).
 */
data class PathsParseResult(
    val items: Map<String, PathItem>,
    val extensions: Map<String, OpenApiValue>,
    val explicitEmpty: Boolean
)

/**
 * Result of parsing a `security` array node.
 *
 * @property requirements Parsed list of security requirement maps.
 * @property explicitEmpty Whether `security: []` was explicitly present.
 */
data class SecurityParseResult(
    val requirements: List<SecurityRequirement>,
    val explicitEmpty: Boolean
)

/**
 * Parses OpenAPI `paths`, `webhooks`, operations, parameters, request bodies, responses, headers,
 * callbacks, links, media types, examples, servers, and security requirements.
 */
object PathAndOperationParser {
  /**
   * Parses a list of [Server] objects from a JSON array node.
   *
   * @param node The `servers` JSON array node.
   * @return List of parsed [Server]s.
   */
  fun parseServers(node: JsonNode?): List<Server> {
    val array = node.asArray() ?: return emptyList()
    return array.mapNotNull { srv -> parseServerObject(srv) }
  }

  private fun parseServerVariables(node: JsonNode?): Map<String, ServerVariable> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      val enumValues = raw.get("enum").asArray()?.mapNotNull { it.textValue() }
      name to
          ServerVariable(
              default = raw.text("default").orEmpty(),
              enum = enumValues,
              description = raw.text("description"),
              extensions = SchemaNodeParser.parseExtensions(raw))
    }
  }

  /**
   * Parses a `paths` or `webhooks` JSON object node into a [PathsParseResult].
   *
   * @param node The `paths` or `webhooks` JSON node.
   * @param components Optional components context for `$ref` resolution.
   * @param currentSelfBase Active `$self` base URI.
   * @return Parsed [PathsParseResult].
   */
  fun parsePaths(
      node: JsonNode?,
      components: Components?,
      currentSelfBase: String? = null
  ): PathsParseResult {
    val obj = node.asObject() ?: return PathsParseResult(emptyMap(), emptyMap(), false)
    val explicitEmpty = obj.size() == 0
    val items = LinkedHashMap<String, PathItem>()
    val extensions = LinkedHashMap<String, OpenApiValue>()
    obj.fields().forEach { (path, raw) ->
      if (path.startsWith("x-")) {
        extensions[path] = SchemaNodeParser.nodeToValue(raw)
      } else {
        items[path] = parsePathItem(path, raw, components, currentSelfBase)
      }
    }
    return PathsParseResult(items, extensions, explicitEmpty)
  }

  /**
   * Parses a single [PathItem] from a JSON node.
   *
   * @param path Path template string.
   * @param node The Path Item JSON node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Parsed [PathItem].
   */
  fun parsePathItem(
      path: String,
      node: JsonNode,
      components: Components?,
      currentSelfBase: String? = null
  ): PathItem {
    val ref = node.text("\$ref")

    val additionalOpsFromField =
        parseAdditionalOperations(
            node.get("additionalOperations"), path, components, currentSelfBase)
    val additionalOpsFromLoose =
        parseAdditionalOperationFields(node, path, components, currentSelfBase)

    return PathItem(
        ref = ref,
        summary = node.text("summary"),
        description = node.text("description"),
        get =
            parseOperationIfPresent(
                path, "get", HttpMethod.GET, node.get("get"), components, currentSelfBase),
        put =
            parseOperationIfPresent(
                path, "put", HttpMethod.PUT, node.get("put"), components, currentSelfBase),
        post =
            parseOperationIfPresent(
                path, "post", HttpMethod.POST, node.get("post"), components, currentSelfBase),
        delete =
            parseOperationIfPresent(
                path, "delete", HttpMethod.DELETE, node.get("delete"), components, currentSelfBase),
        options =
            parseOperationIfPresent(
                path,
                "options",
                HttpMethod.OPTIONS,
                node.get("options"),
                components,
                currentSelfBase),
        head =
            parseOperationIfPresent(
                path, "head", HttpMethod.HEAD, node.get("head"), components, currentSelfBase),
        patch =
            parseOperationIfPresent(
                path, "patch", HttpMethod.PATCH, node.get("patch"), components, currentSelfBase),
        trace =
            parseOperationIfPresent(
                path, "trace", HttpMethod.TRACE, node.get("trace"), components, currentSelfBase),
        query =
            parseOperationIfPresent(
                path, "query", HttpMethod.QUERY, node.get("query"), components, currentSelfBase),
        additionalOperations = additionalOpsFromField + additionalOpsFromLoose,
        parameters = parseParameters(node.get("parameters"), components, currentSelfBase),
        servers = parseServers(node.get("servers")),
        extensions = SchemaNodeParser.parseExtensions(node))
  }

  private fun parseAdditionalOperations(
      node: JsonNode?,
      path: String,
      components: Components?,
      currentSelfBase: String?
  ): Map<String, EndpointDefinition> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (method, raw) ->
      method to parseOperation(path, method, HttpMethod.CUSTOM, raw, components, currentSelfBase)
    }
  }

  private fun parseAdditionalOperationFields(
      obj: JsonNode,
      path: String,
      components: Components?,
      currentSelfBase: String?
  ): Map<String, EndpointDefinition> {
    val reserved =
        setOf(
            "\$ref",
            "summary",
            "description",
            "servers",
            "parameters",
            "additionalOperations",
            "get",
            "put",
            "post",
            "delete",
            "options",
            "head",
            "patch",
            "trace",
            "query")
    val fields = obj.fields().asSequence()
    val map = LinkedHashMap<String, EndpointDefinition>()
    fields.forEach { (key, value) ->
      if (reserved.contains(key)) return@forEach
      if (key.startsWith("x-")) return@forEach
      if (!value.isObject) return@forEach
      map[key] = parseOperation(path, key, HttpMethod.CUSTOM, value, components, currentSelfBase)
    }
    return map
  }

  private fun parseOperationIfPresent(
      path: String,
      methodToken: String,
      method: HttpMethod,
      node: JsonNode?,
      components: Components?,
      currentSelfBase: String?
  ): EndpointDefinition? {
    val obj = node.asObject() ?: return null
    return parseOperation(path, methodToken, method, obj, components, currentSelfBase)
  }

  /**
   * Parses an HTTP operation into an [EndpointDefinition].
   *
   * @param path Path string.
   * @param methodToken HTTP method token.
   * @param method Parsed [HttpMethod].
   * @param node Operation JSON node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Parsed [EndpointDefinition].
   */
  fun parseOperation(
      path: String,
      methodToken: String,
      method: HttpMethod,
      node: JsonNode,
      components: Components?,
      currentSelfBase: String? = null
  ): EndpointDefinition {
    val operationIdNode = node.get("operationId")
    val operationIdExplicit = operationIdNode != null && !operationIdNode.isNullNode
    val operationId = node.text("operationId") ?: defaultOperationId(methodToken, path)
    val requestBody = parseRequestBody(node.get("requestBody"), components, currentSelfBase)
    val responses = parseResponses(node.get("responses"), components, currentSelfBase)
    val requestBodyType =
        SchemaNodeParser.selectSchema(requestBody?.content, components)?.let {
          TypeMappers.mapType(it)
        }
    val securityResult = parseSecurityRequirements(node.get("security"))

    return EndpointDefinition(
        path = path,
        method = method,
        customMethod = if (method == HttpMethod.CUSTOM) methodToken else null,
        operationId = operationId,
        operationIdExplicit = operationIdExplicit,
        parameters = parseParameters(node.get("parameters"), components),
        requestBodyType = requestBodyType,
        requestBody = requestBody,
        responses = responses,
        summary = node.text("summary"),
        description = node.text("description"),
        externalDocs = SchemaNodeParser.parseExternalDocs(node.get("externalDocs")),
        tags = Swagger2Upgrader.parseStringArray(node, "tags"),
        callbacks = parseCallbacks(node.get("callbacks"), components, currentSelfBase),
        consumes = Swagger2Upgrader.parseStringArray(node, "consumes"),
        produces = Swagger2Upgrader.parseStringArray(node, "produces"),
        schemes = Swagger2Upgrader.parseStringArray(node, "schemes"),
        deprecated = node.boolean("deprecated") ?: false,
        security = securityResult.requirements,
        securityExplicitEmpty = securityResult.explicitEmpty,
        servers = parseServers(node.get("servers")),
        extensions = SchemaNodeParser.parseExtensions(node))
  }

  /**
   * Parses a map of [Callback] definitions.
   *
   * @param node The `callbacks` JSON object node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Map of callback name to [Callback].
   */
  fun parseCallbacks(
      node: JsonNode?,
      components: Components?,
      currentSelfBase: String? = null
  ): Map<String, Callback> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, callbackNode) ->
      name to parseCallbackObject(callbackNode, components, currentSelfBase)
    }
  }

  private fun parseCallbackObject(
      node: JsonNode,
      components: Components?,
      currentSelfBase: String?
  ): Callback {
    val ref = node.text("\$ref")
    if (ref != null) {
      val reference =
          ReferenceObject(
              ref = ref, summary = node.text("summary"), description = node.text("description"))
      val resolved =
          components?.let {
            ComponentParser.resolveCallbackComponentRef(
                Callback.Reference(reference), it, mutableSetOf(), currentSelfBase)
          }
      return when (resolved) {
        is Callback.Inline -> resolved.copy(reference = reference)
        else -> Callback.Reference(reference)
      }
    }

    val extensions = SchemaNodeParser.parseExtensions(node)
    val expressions =
        node
            .fields()
            .asSequence()
            .filterNot { (key, _) -> key.startsWith("x-") }
            .associate { (expression, pathItemNode) ->
              expression to parsePathItem("/", pathItemNode, components, currentSelfBase)
            }

    return Callback.Inline(expressions = expressions, extensions = extensions)
  }

  /**
   * Parses a list of [EndpointParameter]s from a `parameters` JSON array node.
   *
   * @param node The `parameters` JSON array node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return List of parsed [EndpointParameter]s.
   */
  fun parseParameters(
      node: JsonNode?,
      components: Components?,
      currentSelfBase: String? = null
  ): List<EndpointParameter> {
    val array = node.asArray() ?: return emptyList()
    return array.map { paramNode -> parseParameter(paramNode, components, currentSelfBase) }
  }

  /**
   * Parses a single [EndpointParameter] from a JSON node.
   *
   * @param node Parameter JSON node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Parsed [EndpointParameter].
   */
  fun parseParameter(
      node: JsonNode,
      components: Components?,
      currentSelfBase: String? = null
  ): EndpointParameter {
    val ref = node.text("\$ref")
    if (ref != null) {
      val reference =
          ReferenceObject(
              ref = ref, summary = node.text("summary"), description = node.text("description"))
      val base =
          ComponentParser.resolveParameterRef(ref, components, currentSelfBase)
              ?: EndpointParameter(
                  name = ReferenceResolver.resolveRefToType(ref),
                  type = "String",
                  location = ParameterLocation.QUERY)
      return base.copy(
          description = reference.description ?: base.description, reference = reference)
    }

    val name = node.text("name") ?: "param"
    val location = parseParameterLocation(node.text("in"))
    val schemaFromNode = node.get("schema")?.let { SchemaNodeParser.parseSchemaProperty(it) }
    val isSwagger2Direct = Swagger2Upgrader.isSwagger2DirectSchema(node)
    val schema =
        if (isSwagger2Direct) SchemaNodeParser.parseSchemaProperty(node) else schemaFromNode
    val content = parseContentMap(node.get("content"), components, currentSelfBase)
    val typeFromSchema = schema ?: SchemaNodeParser.selectSchema(content, components)
    val type = if (typeFromSchema != null) TypeMappers.mapType(typeFromSchema) else "String"

    val requiredDefault = location == ParameterLocation.PATH
    val required = node.boolean("required") ?: requiredDefault

    return EndpointParameter(
        name = name,
        type = type,
        location = location,
        isRequired = required,
        schema = schema,
        content = content,
        description = node.text("description"),
        deprecated = node.boolean("deprecated") ?: false,
        allowEmptyValue = node.boolean("allowEmptyValue"),
        collectionFormat = node.text("collectionFormat"),
        style = parseParameterStyle(node.text("style")),
        explode = node.boolean("explode"),
        allowReserved = node.boolean("allowReserved"),
        example = parseInlineExample(node.get("example")),
        examples = parseExampleMap(node.get("examples"), components, currentSelfBase),
        extensions = SchemaNodeParser.parseExtensions(node))
  }

  private fun parseInlineExample(node: JsonNode?): ExampleObject? {
    return if (node != null) ExampleObject(value = SchemaNodeParser.nodeToValue(node)) else null
  }

  /**
   * Parses an optional [RequestBody] from a JSON node.
   *
   * @param node RequestBody JSON node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Parsed [RequestBody] or `null`.
   */
  fun parseRequestBody(
      node: JsonNode?,
      components: Components?,
      currentSelfBase: String? = null
  ): RequestBody? {
    val obj = node.asObject() ?: return null
    val ref = obj.text("\$ref")
    if (ref != null) {
      val reference =
          ReferenceObject(
              ref = ref, summary = obj.text("summary"), description = obj.text("description"))
      val base =
          ComponentParser.resolveRequestBodyRef(ref, components, currentSelfBase) ?: RequestBody()
      return base.copy(
          description = reference.description ?: base.description, reference = reference)
    }
    val hasContent = obj.has("content")
    return RequestBody(
        description = obj.text("description"),
        content = parseContentMap(obj.get("content"), components, currentSelfBase),
        contentPresent = hasContent,
        required = obj.boolean("required") ?: false,
        extensions = SchemaNodeParser.parseExtensions(obj))
  }

  /**
   * Parses a map of status codes to [EndpointResponse]s.
   *
   * @param node Responses JSON object node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Map of status code to [EndpointResponse].
   */
  fun parseResponses(
      node: JsonNode?,
      components: Components?,
      currentSelfBase: String? = null
  ): Map<String, EndpointResponse> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (code, raw) ->
      code to parseResponse(code, raw, components, currentSelfBase)
    }
  }

  /**
   * Parses a single [EndpointResponse] for [code].
   *
   * @param code HTTP status code key.
   * @param node Response JSON node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Parsed [EndpointResponse].
   */
  fun parseResponse(
      code: String,
      node: JsonNode,
      components: Components?,
      currentSelfBase: String? = null
  ): EndpointResponse {
    val ref = node.text("\$ref")
    if (ref != null) {
      val reference =
          ReferenceObject(
              ref = ref, summary = node.text("summary"), description = node.text("description"))
      val base =
          ComponentParser.resolveResponseRef(ref, components, currentSelfBase)
              ?: EndpointResponse(statusCode = code, description = "ref:$ref")
      return base.copy(
          statusCode = code,
          summary = reference.summary ?: base.summary,
          description = reference.description ?: base.description,
          reference = reference)
    }

    val contentPresent = node.has("content")
    val content = parseContentMap(node.get("content"), components, currentSelfBase)
    val schema = node.get("schema")?.let { SchemaNodeParser.parseSchemaProperty(it) }
    val typeFromSchema = schema ?: SchemaNodeParser.selectSchema(content, components)
    val type = if (typeFromSchema != null) TypeMappers.mapType(typeFromSchema) else null

    return EndpointResponse(
        statusCode = code,
        summary = node.text("summary"),
        description = node.text("description"),
        headers = parseHeaders(node.get("headers"), components, currentSelfBase),
        content = content,
        contentPresent = contentPresent,
        schema = schema,
        examples =
            parseValueMap(node.get("examples")).mapValues { ExampleObject(value = it.value) },
        type = type,
        links = parseLinks(node.get("links"), components, currentSelfBase),
        extensions = SchemaNodeParser.parseExtensions(node))
  }

  private fun parseValueMap(node: JsonNode?): Map<String, OpenApiValue> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (k, v) -> k to SchemaNodeParser.nodeToValue(v) }
  }

  /**
   * Parses a map of [Header] objects.
   *
   * @param node Headers JSON object node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Map of header name to [Header].
   */
  fun parseHeaders(
      node: JsonNode?,
      components: Components?,
      currentSelfBase: String? = null
  ): Map<String, Header> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      name to parseHeader(raw, components, currentSelfBase)
    }
  }

  /**
   * Parses a single [Header] object.
   *
   * @param node Header JSON node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Parsed [Header].
   */
  fun parseHeader(
      node: JsonNode,
      components: Components?,
      currentSelfBase: String? = null
  ): Header {
    val ref = node.text("\$ref")
    if (ref != null) {
      val reference =
          ReferenceObject(
              ref = ref, summary = node.text("summary"), description = node.text("description"))
      val base =
          ComponentParser.resolveHeaderRef(ref, components, currentSelfBase)
              ?: Header(type = "String")
      return base.copy(
          description = reference.description ?: base.description, reference = reference)
    }

    val schemaFromNode = node.get("schema")?.let { SchemaNodeParser.parseSchemaProperty(it) }
    val isSwagger2Direct = Swagger2Upgrader.isSwagger2DirectSchema(node)
    val schema =
        if (isSwagger2Direct) SchemaNodeParser.parseSchemaProperty(node) else schemaFromNode
    val content = parseContentMap(node.get("content"), components, currentSelfBase)
    val typeFromSchema = schema ?: SchemaNodeParser.selectSchema(content, components)
    val type = if (typeFromSchema != null) TypeMappers.mapType(typeFromSchema) else "String"

    return Header(
        type = type,
        schema = schema,
        content = content,
        description = node.text("description"),
        required = node.boolean("required") ?: false,
        deprecated = node.boolean("deprecated") ?: false,
        example = parseInlineExample(node.get("example")),
        examples = parseExampleMap(node.get("examples"), components, currentSelfBase),
        collectionFormat = node.text("collectionFormat"),
        style = parseParameterStyle(node.text("style")) ?: ParameterStyle.SIMPLE,
        explode = node.boolean("explode") ?: false,
        extensions = SchemaNodeParser.parseExtensions(node))
  }

  /**
   * Parses a map of [Link] objects.
   *
   * @param node Links JSON object node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Map of link name to [Link].
   */
  fun parseLinks(
      node: JsonNode?,
      components: Components?,
      currentSelfBase: String? = null
  ): Map<String, Link> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      name to parseLink(raw, components, currentSelfBase)
    }
  }

  /**
   * Parses a single [Link] object.
   *
   * @param node Link JSON node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Parsed [Link].
   */
  fun parseLink(node: JsonNode, components: Components?, currentSelfBase: String? = null): Link {
    val ref = node.text("\$ref")
    if (ref != null) {
      val reference =
          ReferenceObject(
              ref = ref, summary = node.text("summary"), description = node.text("description"))
      val base = ComponentParser.resolveLinkRef(ref, components, currentSelfBase) ?: Link()
      return base.copy(
          ref = ref, reference = reference, description = reference.description ?: base.description)
    }
    return Link(
        operationId = node.text("operationId"),
        operationRef = node.text("operationRef"),
        parameters = parseValueMap(node.get("parameters")),
        requestBody = node.get("requestBody")?.let { SchemaNodeParser.nodeToValue(it) },
        description = node.text("description"),
        server = node.get("server")?.let { parseServerObject(it) },
        extensions = SchemaNodeParser.parseExtensions(node))
  }

  /**
   * Parses a media-type content map into [MediaTypeObject] entries.
   *
   * @param node Content JSON object node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Map of media type string to [MediaTypeObject].
   */
  fun parseContentMap(
      node: JsonNode?,
      components: Components?,
      currentSelfBase: String? = null
  ): Map<String, MediaTypeObject> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      name to parseMediaTypeObject(raw, components, currentSelfBase)
    }
  }

  /**
   * Parses a single [MediaTypeObject].
   *
   * @param node MediaType JSON node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Parsed [MediaTypeObject].
   */
  fun parseMediaTypeObject(
      node: JsonNode,
      components: Components?,
      currentSelfBase: String? = null
  ): MediaTypeObject {
    val ref = node.text("\$ref")
    if (ref != null) {
      val reference =
          ReferenceObject(
              ref = ref, summary = node.text("summary"), description = node.text("description"))
      val base =
          ComponentParser.resolveMediaTypeRef(ref, components, currentSelfBase) ?: MediaTypeObject()
      return base.copy(ref = ref, reference = reference)
    }

    return MediaTypeObject(
        schema = node.get("schema")?.let { SchemaNodeParser.parseSchemaProperty(it) },
        itemSchema = node.get("itemSchema")?.let { SchemaNodeParser.parseSchemaProperty(it) },
        example = parseInlineExample(node.get("example")),
        examples = parseExampleMap(node.get("examples"), components),
        encoding = parseEncodingMap(node.get("encoding"), components, currentSelfBase),
        prefixEncoding =
            parseEncodingArray(node.get("prefixEncoding"), components, currentSelfBase),
        itemEncoding =
            node.get("itemEncoding")?.let { parseEncodingObject(it, components, currentSelfBase) },
        extensions = SchemaNodeParser.parseExtensions(node))
  }

  private fun parseEncodingMap(
      node: JsonNode?,
      components: Components?,
      currentSelfBase: String?
  ): Map<String, domain.EncodingObject> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      name to parseEncodingObject(raw, components, currentSelfBase)
    }
  }

  private fun parseEncodingArray(
      node: JsonNode?,
      components: Components?,
      currentSelfBase: String?
  ): List<domain.EncodingObject> {
    val array = node.asArray() ?: return emptyList()
    return array.map { parseEncodingObject(it, components, currentSelfBase) }
  }

  private fun parseEncodingObject(
      node: JsonNode,
      components: Components?,
      currentSelfBase: String?
  ): domain.EncodingObject {
    return domain.EncodingObject(
        contentType = node.text("contentType"),
        headers = parseHeaders(node.get("headers"), components),
        style = parseParameterStyle(node.text("style")),
        explode = node.boolean("explode"),
        allowReserved = node.boolean("allowReserved"),
        encoding = parseEncodingMap(node.get("encoding"), components, currentSelfBase),
        prefixEncoding =
            parseEncodingArray(node.get("prefixEncoding"), components, currentSelfBase),
        itemEncoding =
            node.get("itemEncoding")?.let { parseEncodingObject(it, components, currentSelfBase) },
        extensions = SchemaNodeParser.parseExtensions(node))
  }

  /**
   * Parses a map of [ExampleObject]s.
   *
   * @param node Examples JSON object node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Map of example name to [ExampleObject].
   */
  fun parseExampleMap(
      node: JsonNode?,
      components: Components?,
      currentSelfBase: String? = null
  ): Map<String, ExampleObject> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      name to parseExampleObject(raw, components, currentSelfBase)
    }
  }

  /**
   * Parses a single [ExampleObject].
   *
   * @param node Example JSON node.
   * @param components Optional components context.
   * @param currentSelfBase Active `$self` base URI.
   * @return Parsed [ExampleObject].
   */
  fun parseExampleObject(
      node: JsonNode,
      components: Components?,
      currentSelfBase: String? = null
  ): ExampleObject {
    val obj = node.asObject() ?: return ExampleObject(value = SchemaNodeParser.nodeToValue(node))
    val ref = obj.text("\$ref")
    if (ref != null) {
      val reference =
          ReferenceObject(
              ref = ref, summary = obj.text("summary"), description = obj.text("description"))
      val base =
          ComponentParser.resolveExampleRef(ref, components, currentSelfBase) ?: ExampleObject()
      return base.copy(
          ref = ref,
          summary = reference.summary ?: base.summary,
          description = reference.description ?: base.description)
    }
    return ExampleObject(
        summary = obj.text("summary"),
        description = obj.text("description"),
        dataValue = obj.get("dataValue")?.let { SchemaNodeParser.nodeToValue(it) },
        serializedValue = obj.text("serializedValue"),
        externalValue = obj.text("externalValue"),
        value = obj.get("value")?.let { SchemaNodeParser.nodeToValue(it) },
        extensions = SchemaNodeParser.parseExtensions(obj))
  }

  /**
   * Parses an array of security requirements (`security`).
   *
   * @param node Security JSON array node.
   * @return Parsed [SecurityParseResult].
   */
  fun parseSecurityRequirements(node: JsonNode?): SecurityParseResult {
    val array = node.asArray() ?: return SecurityParseResult(emptyList(), false)
    if (array.isEmpty()) return SecurityParseResult(emptyList(), true)
    val requirements =
        array.mapNotNull { item ->
          val obj = item.asObject() ?: return@mapNotNull null
          obj.fields().asSequence().associate { (k, _) ->
            k to Swagger2Upgrader.parseStringArray(obj, k)
          }
        }
    return SecurityParseResult(requirements, false)
  }

  /**
   * Parses a list of root [Tag] objects.
   *
   * @param node Tags JSON array node.
   * @return List of parsed [Tag]s.
   */
  fun parseTags(node: JsonNode?): List<Tag> {
    val array = node.asArray() ?: return emptyList()
    return array.mapNotNull { tagNode ->
      val obj = tagNode.asObject() ?: return@mapNotNull null
      Tag(
          name = obj.text("name") ?: "tag",
          summary = obj.text("summary"),
          description = obj.text("description"),
          externalDocs = SchemaNodeParser.parseExternalDocs(obj.get("externalDocs")),
          parent = obj.text("parent"),
          kind = obj.text("kind"),
          extensions = SchemaNodeParser.parseExtensions(obj))
    }
  }

  /**
   * Parses reusable component `pathItems`.
   *
   * @param node PathItems JSON object node.
   * @param currentSelfBase Active `$self` base URI.
   * @return Map of path item name to [PathItem].
   */
  fun parsePathItems(node: JsonNode?, currentSelfBase: String? = null): Map<String, PathItem> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      name to parsePathItem(name, raw, null, currentSelfBase)
    }
  }

  private fun parseParameterLocation(value: String?): ParameterLocation {
    return when (value?.lowercase()) {
      "path" -> ParameterLocation.PATH
      "query" -> ParameterLocation.QUERY
      "querystring" -> ParameterLocation.QUERYSTRING
      "header" -> ParameterLocation.HEADER
      "cookie" -> ParameterLocation.COOKIE
      "body" -> ParameterLocation.BODY
      "formdata" -> ParameterLocation.FORMDATA
      else -> ParameterLocation.QUERY
    }
  }

  private fun parseParameterStyle(value: String?): ParameterStyle? {
    val normalized = value.orEmpty().lowercase().replace("_", "")
    val styles =
        mapOf(
            "matrix" to ParameterStyle.MATRIX,
            "label" to ParameterStyle.LABEL,
            "simple" to ParameterStyle.SIMPLE,
            "form" to ParameterStyle.FORM,
            "cookie" to ParameterStyle.COOKIE,
            "spacedelimited" to ParameterStyle.SPACE_DELIMITED,
            "pipedelimited" to ParameterStyle.PIPE_DELIMITED,
            "deepobject" to ParameterStyle.DEEP_OBJECT)
    return styles[normalized]
  }

  private fun defaultOperationId(methodToken: String, path: String): String {
    val raw = "${methodToken}_${path}"
    return raw.replace(Regex("[^a-zA-Z0-9_]"), "_").replace(Regex("_+"), "_").trim('_')
  }

  private fun parseServerObject(node: JsonNode): Server? {
    val obj = node.asObject() ?: return null
    return Server(
        url = obj.text("url") ?: "/",
        description = obj.text("description"),
        variables = parseServerVariables(obj.get("variables")),
        name = obj.text("name"),
        extensions = SchemaNodeParser.parseExtensions(obj))
  }
}
