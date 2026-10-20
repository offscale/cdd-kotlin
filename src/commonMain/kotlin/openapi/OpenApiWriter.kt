/** Multiplatform OpenAPI 3.2 and Swagger 2.0 JSON/YAML document serializer. */
package openapi

import domain.Callback
import domain.Components
import domain.Contact
import domain.EncodingObject
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.ExampleObject
import domain.Header
import domain.Info
import domain.License
import domain.Link
import domain.MediaTypeObject
import domain.OAuthFlow
import domain.OAuthFlows
import domain.OpenApiArray
import domain.OpenApiBoolean
import domain.OpenApiDefinition
import domain.OpenApiObject
import domain.OpenApiString
import domain.OpenApiValue
import domain.ParameterLocation
import domain.ParameterStyle
import domain.PathItem
import domain.ReferenceObject
import domain.RequestBody
import domain.SchemaProperty
import domain.SecurityRequirement
import domain.SecurityScheme
import domain.Server
import domain.ServerVariable
import domain.Tag
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import openapi.parser.YamlToJsonElementConverter

/** Serializes [OpenApiDefinition] instances to JSON or YAML strings across all targets. */
class OpenApiWriter {
  private val prettyJson = Json { prettyPrint = true }

  /** Supported output formats for OpenAPI serialization. */
  enum class Format {
    /** Auto-detect based on file extension or fallback to JSON. */
    AUTO,
    /** JSON output. */
    JSON,
    /** YAML output. */
    YAML
  }

  /**
   * Writes the OpenAPI definition to a string in the requested [format].
   *
   * @param definition The [OpenApiDefinition] to serialize.
   * @param format The output [Format].
   * @param fileName Optional file name used when [format] is [Format.AUTO].
   * @return The serialized JSON or YAML string.
   */
  fun write(
      definition: OpenApiDefinition,
      format: Format = Format.JSON,
      fileName: String? = null
  ): String {
    val resolved = resolveFormat(format, fileName)
    val element = toOpenApiObject(definition).toJsonElement()
    return if (resolved == Format.JSON) {
      prettyJson.encodeToString(JsonElement.serializer(), element)
    } else {
      YamlToJsonElementConverter.toYaml(element)
    }
  }

  /**
   * Writes an OpenAPI Description document (OpenAPI Object, Schema Object, or Arazzo Object) to a
   * string.
   *
   * @param document The [OpenApiDocument] to serialize.
   * @param format The output [Format].
   * @param fileName Optional file name used when [format] is [Format.AUTO].
   * @return The serialized JSON or YAML string.
   */
  fun writeDocument(
      document: OpenApiDocument,
      format: Format = Format.JSON,
      fileName: String? = null
  ): String {
    val resolved = resolveFormat(format, fileName)
    val value: OpenApiValue =
        when (document) {
          is OpenApiDocument.OpenApi -> toOpenApiObject(document.definition)
          is OpenApiDocument.Schema -> schemaToOpenApiValue(document.schema)
          is OpenApiDocument.Arazzo -> arazzo.ArazzoWriter().toOpenApiObject(document.definition)
        }
    val element = value.toJsonElement()
    return if (resolved == Format.JSON) {
      prettyJson.encodeToString(JsonElement.serializer(), element)
    } else {
      YamlToJsonElementConverter.toYaml(element)
    }
  }

  /**
   * Writes a standalone Schema Object document to a string.
   *
   * @param schema The [SchemaProperty] root to serialize.
   * @param format The output [Format].
   * @param fileName Optional file name used when [format] is [Format.AUTO].
   * @return The serialized JSON or YAML string.
   */
  fun writeSchema(
      schema: SchemaProperty,
      format: Format = Format.JSON,
      fileName: String? = null
  ): String {
    return writeDocument(OpenApiDocument.Schema(schema), format, fileName)
  }

  /**
   * Converts a [SchemaProperty] directly into an [OpenApiValue] AST without round-tripping through
   * a serialized JSON string.
   *
   * @param schema The [SchemaProperty] to convert.
   * @return The resulting [OpenApiValue] (`OpenApiObject` or `OpenApiBoolean`).
   */
  fun schemaToOpenApiValue(schema: SchemaProperty): OpenApiValue =
      SchemaJsonCodec.schemaPropertyToOpenApiValue(schema)

  /**
   * Converts an [OpenApiDefinition] directly into a strongly-typed [OpenApiObject] AST.
   *
   * @param definition The [OpenApiDefinition] to convert.
   * @return The resulting [OpenApiObject].
   */
  fun toOpenApiObject(definition: OpenApiDefinition): OpenApiObject {
    val refBase = normalizeSelfRefBase(definition.self)
    val map = LinkedHashMap<String, OpenApiValue>()
    val isSwagger2 =
        definition.swagger != null || definition.openapi.firstOrNull() in setOf('1', '2')
    if (isSwagger2) {
      map["swagger"] = OpenApiString(definition.swagger ?: "2.0")
    } else {
      map["openapi"] = OpenApiString(definition.openapi)
    }
    map["info"] = infoToObject(definition.info)
    if (isSwagger2) {
      map.putStringIfNotNull("host", definition.host)
      map.putStringIfNotNull("basePath", definition.basePath)
      map.putStringListIfNotEmpty("schemes", definition.schemes)
      map.putStringListIfNotEmpty("consumes", definition.consumes)
      map.putStringListIfNotEmpty("produces", definition.produces)
    }
    map.putStringIfNotNull("jsonSchemaDialect", definition.jsonSchemaDialect)
    map.putListIfNotEmpty("servers", definition.servers.map { serverToObject(it) })
    val pathsObj = pathsToObject(definition.paths, definition.pathsExtensions, refBase, isSwagger2)
    if (pathsObj.properties.isNotEmpty() || definition.pathsExplicitEmpty) {
      map["paths"] = pathsObj
    }
    val webhooksObj =
        pathsToObject(definition.webhooks, definition.webhooksExtensions, refBase, isSwagger2)
    if (webhooksObj.properties.isNotEmpty() || definition.webhooksExplicitEmpty) {
      map["webhooks"] = webhooksObj
    }

    val c = definition.components
    if (c != null) {
      if (isSwagger2) {
        map.putMapIfNotEmpty(
            "definitions",
            c.schemas.mapValues {
              SchemaJsonCodec.schemaDefinitionToOpenApiValue(it.value, refBase)
            })
        map.putMapIfNotEmpty(
            "parameters", c.parameters.mapValues { parameterToObject(it.value, refBase, true) })
        map.putMapIfNotEmpty(
            "responses", c.responses.mapValues { responseToObject(it.value, refBase, true) })
        map.putMapIfNotEmpty(
            "securityDefinitions",
            c.securitySchemes.mapValues { securitySchemeToObject(it.value, true) })
      } else {
        map["components"] = componentsToObject(c, refBase, false)
      }
    }

    securityToValue(definition.security, definition.securityExplicitEmpty)?.let {
      map["security"] = it
    }
    map.putListIfNotEmpty("tags", definition.tags.map { tagToObject(it) })
    definition.externalDocs?.let {
      map["externalDocs"] = SchemaJsonCodec.externalDocsToOpenApiObject(it)
    }
    if (definition.self != null) {
      map["\$self"] = OpenApiString(definition.self)
    }
    map.putExtensions(definition.extensions)
    return OpenApiObject(map)
  }

  /**
   * Writes the OpenAPI definition to JSON.
   *
   * @param definition The [OpenApiDefinition] to serialize.
   * @return The serialized JSON string.
   */
  fun writeJson(definition: OpenApiDefinition): String = write(definition, Format.JSON)

  /**
   * Writes the OpenAPI definition to YAML.
   *
   * @param definition The [OpenApiDefinition] to serialize.
   * @return The serialized YAML string.
   */
  fun writeYaml(definition: OpenApiDefinition): String = write(definition, Format.YAML)

  private fun resolveFormat(format: Format, fileName: String?): Format {
    if (format != Format.AUTO) return format
    val ext = fileName.orEmpty().substringAfterLast('.', "").lowercase()
    return if (ext in setOf("yaml", "yml")) Format.YAML else Format.JSON
  }

  /**
   * Converts an [Info] object into an [OpenApiObject].
   *
   * @param info The [Info] object to convert.
   * @return The [OpenApiObject] representation.
   */
  fun infoToObject(info: Info): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["title"] = OpenApiString(info.title)
    map["version"] = OpenApiString(info.version)
    map.putStringIfNotNull("summary", info.summary)
    map.putStringIfNotNull("description", info.description)
    map.putStringIfNotNull("termsOfService", info.termsOfService)
    info.contact?.let { map["contact"] = contactToObject(it) }
    info.license?.let { map["license"] = licenseToObject(it) }
    map.putExtensions(info.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts a [Contact] object into an [OpenApiObject].
   *
   * @param contact The [Contact] object to convert.
   * @return The [OpenApiObject] representation.
   */
  fun contactToObject(contact: Contact): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map.putStringIfNotNull("name", contact.name)
    map.putStringIfNotNull("url", contact.url)
    map.putStringIfNotNull("email", contact.email)
    map.putExtensions(contact.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts a [License] object into an [OpenApiObject].
   *
   * @param license The [License] object to convert.
   * @return The [OpenApiObject] representation.
   */
  fun licenseToObject(license: License): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["name"] = OpenApiString(license.name)
    map.putStringIfNotNull("identifier", license.identifier)
    map.putStringIfNotNull("url", license.url)
    map.putExtensions(license.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts a [Server] object into an [OpenApiObject].
   *
   * @param server The [Server] object to convert.
   * @return The [OpenApiObject] representation.
   */
  fun serverToObject(server: Server): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["url"] = OpenApiString(server.url)
    map.putStringIfNotNull("description", server.description)
    map.putStringIfNotNull("name", server.name)
    server.variables?.let { vars ->
      if (vars.isNotEmpty()) {
        map["variables"] = OpenApiObject(vars.mapValues { serverVariableToObject(it.value) })
      }
    }
    map.putExtensions(server.extensions)
    return OpenApiObject(map)
  }

  private fun serverVariableToObject(variable: ServerVariable): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["default"] = OpenApiString(variable.default)
    map.putStringListIfNotEmpty("enum", variable.enum.orEmpty())
    map.putStringIfNotNull("description", variable.description)
    map.putExtensions(variable.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts a [PathItem] into an [OpenApiObject].
   *
   * @param item The [PathItem] to convert.
   * @param refBase Optional `$self` base URI.
   * @param isSwagger2 Whether serializing in Swagger 2.0 mode.
   * @return The [OpenApiObject] representation.
   */
  fun pathItemToObject(item: PathItem, refBase: String?, isSwagger2: Boolean): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    item.ref?.let { map["\$ref"] = OpenApiString(it) }
    map.putStringIfNotNull("summary", item.summary)
    map.putStringIfNotNull("description", item.description)
    item.get?.let { map["get"] = operationToObject(it, refBase, isSwagger2) }
    item.put?.let { map["put"] = operationToObject(it, refBase, isSwagger2) }
    item.post?.let { map["post"] = operationToObject(it, refBase, isSwagger2) }
    item.delete?.let { map["delete"] = operationToObject(it, refBase, isSwagger2) }
    item.options?.let { map["options"] = operationToObject(it, refBase, isSwagger2) }
    item.head?.let { map["head"] = operationToObject(it, refBase, isSwagger2) }
    item.patch?.let { map["patch"] = operationToObject(it, refBase, isSwagger2) }
    item.trace?.let { map["trace"] = operationToObject(it, refBase, isSwagger2) }
    item.query?.let { map["query"] = operationToObject(it, refBase, isSwagger2) }
    if (item.additionalOperations.isNotEmpty()) {
      map["additionalOperations"] =
          OpenApiObject(
              item.additionalOperations.mapValues {
                operationToObject(it.value, refBase, isSwagger2)
              })
    }
    map.putListIfNotEmpty(
        "parameters", item.parameters.map { parameterToObject(it, refBase, isSwagger2) })
    map.putListIfNotEmpty("servers", item.servers.map { serverToObject(it) })
    map.putExtensions(item.extensions)
    return OpenApiObject(map)
  }

  private fun operationToObject(
      operation: EndpointDefinition,
      refBase: String?,
      isSwagger2: Boolean
  ): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map.putStringListIfNotEmpty("tags", operation.tags)
    map.putStringIfNotNull("summary", operation.summary)
    map.putStringIfNotNull("description", operation.description)
    operation.externalDocs?.let {
      map["externalDocs"] = SchemaJsonCodec.externalDocsToOpenApiObject(it)
    }
    if (operation.operationIdExplicit) {
      map["operationId"] = OpenApiString(operation.operationId)
    }
    if (isSwagger2) {
      map.putStringListIfNotEmpty("consumes", operation.consumes)
      map.putStringListIfNotEmpty("produces", operation.produces)
      map.putStringListIfNotEmpty("schemes", operation.schemes)
    }
    map.putListIfNotEmpty(
        "parameters", operation.parameters.map { parameterToObject(it, refBase, isSwagger2) })
    operation.requestBody?.let { map["requestBody"] = requestBodyToObject(it) }
    if (operation.requestBody == null && operation.requestBodyType != null) {
      map["requestBody"] =
          requestBodyToObject(
              RequestBody(
                  content =
                      mapOf(
                          "application/json" to
                              MediaTypeObject(
                                  schema =
                                      SchemaProperty(
                                          ref =
                                              componentRef(
                                                  refBase,
                                                  "schemas",
                                                  operation.requestBodyType,
                                                  isSwagger2)))),
                  required = true))
    }
    if (operation.responses.isNotEmpty()) {
      map["responses"] =
          OpenApiObject(
              operation.responses.mapValues { responseToObject(it.value, refBase, isSwagger2) })
    }
    map.putMapIfNotEmpty("callbacks", callbacksToMap(operation.callbacks, refBase))
    map.putIfTrue("deprecated", operation.deprecated)
    securityToValue(operation.security, operation.securityExplicitEmpty)?.let {
      map["security"] = it
    }
    map.putListIfNotEmpty("servers", operation.servers.map { serverToObject(it) })
    map.putExtensions(operation.extensions)
    return OpenApiObject(map)
  }

  private fun callbacksToMap(
      callbacks: Map<String, Callback>,
      refBase: String?
  ): Map<String, OpenApiValue> {
    if (callbacks.isEmpty()) return emptyMap()
    return callbacks.mapValues { (_, cb) -> callbackToObject(cb, refBase) }
  }

  /**
   * Converts a [Callback] into an [OpenApiObject].
   *
   * @param callback The [Callback] to convert.
   * @param refBase Optional `$self` base URI.
   * @return The [OpenApiObject] representation.
   */
  fun callbackToObject(callback: Callback, refBase: String?): OpenApiObject {
    return when (callback) {
      is Callback.Reference -> referenceToObject(callback.reference)
      is Callback.Inline -> {
        callback.reference?.let {
          return referenceToObject(it)
        }
        val map = LinkedHashMap<String, OpenApiValue>()
        callback.expressions.forEach { (expression, pathItem) ->
          map[expression] = pathItemToObject(pathItem, refBase, false)
        }
        map.putExtensions(callback.extensions)
        OpenApiObject(map)
      }
    }
  }

  /**
   * Converts an [EndpointParameter] into an [OpenApiObject].
   *
   * @param parameter The [EndpointParameter] to convert.
   * @param refBase Optional `$self` base URI.
   * @param isSwagger2 Whether serializing in Swagger 2.0 mode.
   * @return The [OpenApiObject] representation.
   */
  fun parameterToObject(
      parameter: EndpointParameter,
      refBase: String?,
      isSwagger2: Boolean
  ): OpenApiObject {
    parameter.reference?.let {
      return referenceToObject(it)
    }
    val map = LinkedHashMap<String, OpenApiValue>()
    map["name"] = OpenApiString(parameter.name)
    map["in"] = OpenApiString(parameterLocation(parameter.location))
    map.putStringIfNotNull("description", parameter.description)
    map.putIfTrue("required", parameter.isRequired)
    map.putIfTrue("deprecated", parameter.deprecated)
    parameter.allowEmptyValue?.let { map["allowEmptyValue"] = OpenApiBoolean(it) }

    if (isSwagger2) {
      if (parameter.location == ParameterLocation.BODY) {
        if (parameter.schema != null) {
          map["schema"] = SchemaJsonCodec.schemaPropertyToOpenApiValue(parameter.schema)
        } else {
          map["schema"] =
              SchemaJsonCodec.schemaPropertyToOpenApiValue(
                  SchemaProperty(types = setOf(parameter.type)))
        }
      } else {
        val primType = swagger2PrimitiveType(parameter.schema?.type, parameter.type)
        map["type"] = OpenApiString(primType)
        map.putStringIfNotNull("format", parameter.schema?.format)
        map.putStringIfNotNull("collectionFormat", parameter.collectionFormat)
        if (parameter.schema?.items != null) {
          map["items"] = SchemaJsonCodec.schemaPropertyToOpenApiValue(parameter.schema.items)
        }
      }
    } else {
      parameter.style?.let { map["style"] = OpenApiString(parameterStyle(it)) }
      parameter.explode?.let { map["explode"] = OpenApiBoolean(it) }
      parameter.allowReserved?.let { map["allowReserved"] = OpenApiBoolean(it) }
      if (parameter.content.isNotEmpty()) {
        map["content"] = contentToObject(parameter.content)
      } else if (parameter.schema != null) {
        map["schema"] = SchemaJsonCodec.schemaPropertyToOpenApiValue(parameter.schema)
      }
    }

    exampleValue(parameter.example)?.let { map["example"] = it }
    map.putMapIfNotEmpty("examples", exampleMapToValues(parameter.examples))
    map.putExtensions(parameter.extensions)
    return OpenApiObject(map)
  }

  private fun swagger2PrimitiveType(schemaType: String?, fallbackType: String): String {
    return if (schemaType == null || schemaType == "object") fallbackType else schemaType
  }

  /**
   * Converts a [RequestBody] into an [OpenApiObject].
   *
   * @param body The [RequestBody] to convert.
   * @return The [OpenApiObject] representation.
   */
  fun requestBodyToObject(body: RequestBody): OpenApiObject {
    body.reference?.let {
      return referenceToObject(it)
    }
    val map = LinkedHashMap<String, OpenApiValue>()
    map.putStringIfNotNull("description", body.description)
    if (body.content.isNotEmpty() || body.contentPresent) {
      map["content"] = contentToObject(body.content)
    }
    map.putIfTrue("required", body.required)
    map.putExtensions(body.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts an [EndpointResponse] into an [OpenApiObject].
   *
   * @param response The [EndpointResponse] to convert.
   * @param refBase Optional `$self` base URI.
   * @param isSwagger2 Whether serializing in Swagger 2.0 mode.
   * @return The [OpenApiObject] representation.
   */
  fun responseToObject(
      response: EndpointResponse,
      refBase: String?,
      isSwagger2: Boolean
  ): OpenApiObject {
    response.reference?.let {
      return referenceToObject(it)
    }
    val map = LinkedHashMap<String, OpenApiValue>()
    if (!isSwagger2) {
      map.putStringIfNotNull("summary", response.summary)
    }
    map.putStringIfNotNull("description", response.description)
    val headers = response.headers.filterKeys { !it.equals("Content-Type", ignoreCase = true) }
    map.putMapIfNotEmpty("headers", headers.mapValues { headerToObject(it.value, isSwagger2) })

    if (isSwagger2) {
      if (response.schema != null) {
        map["schema"] = SchemaJsonCodec.schemaPropertyToOpenApiValue(response.schema)
      } else if (response.type != null) {
        map["schema"] =
            SchemaJsonCodec.schemaPropertyToOpenApiValue(
                SchemaProperty(ref = componentRef(refBase, "schemas", response.type, isSwagger2)))
      } else if (response.content.isNotEmpty()) {
        val mediaSchema = response.content.values.first().schema
        map.putSchemaIfNotNull("schema", mediaSchema)
      }
      if (response.examples.isNotEmpty()) {
        val examplesMap = LinkedHashMap<String, OpenApiValue>()
        for ((mime, example) in response.examples) {
          example.value?.let { examplesMap[mime] = it }
        }
        map.putMapIfNotEmpty("examples", examplesMap)
      }
    } else {
      if (response.content.isNotEmpty() || response.contentPresent) {
        map["content"] = contentToObject(response.content)
      } else if (response.type != null) {
        map["content"] =
            contentToObject(
                mapOf(
                    "application/json" to
                        MediaTypeObject(
                            schema =
                                SchemaProperty(
                                    ref = componentRef(refBase, "schemas", response.type, false)))))
      }
      response.links?.let { map["links"] = OpenApiObject(it.mapValues { linkToObject(it.value) }) }
    }

    map.putExtensions(response.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts a [Header] into an [OpenApiObject].
   *
   * @param header The [Header] to convert.
   * @param isSwagger2 Whether serializing in Swagger 2.0 mode.
   * @return The [OpenApiObject] representation.
   */
  fun headerToObject(header: Header, isSwagger2: Boolean): OpenApiObject {
    header.reference?.let {
      return referenceToObject(it)
    }
    val map = LinkedHashMap<String, OpenApiValue>()
    map.putStringIfNotNull("description", header.description)
    map.putIfTrue("required", header.required)
    map.putIfTrue("deprecated", header.deprecated)

    if (isSwagger2) {
      val primType = swagger2PrimitiveType(header.schema?.type, header.type)
      map["type"] = OpenApiString(primType)
      map.putStringIfNotNull("format", header.schema?.format)
      map.putStringIfNotNull("collectionFormat", header.collectionFormat)
      if (header.schema?.items != null) {
        map["items"] = SchemaJsonCodec.schemaPropertyToOpenApiValue(header.schema.items)
      }
    } else {
      if (header.content.isNotEmpty()) {
        map["content"] = contentToObject(header.content)
      } else if (header.schema != null) {
        map["schema"] = SchemaJsonCodec.schemaPropertyToOpenApiValue(header.schema)
        if (header.style != null) map["style"] = OpenApiString(parameterStyle(header.style))
        header.explode?.let { map["explode"] = OpenApiBoolean(it) }
      }
    }

    exampleValue(header.example)?.let { map["example"] = it }
    map.putMapIfNotEmpty("examples", exampleMapToValues(header.examples))
    map.putExtensions(header.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts a [Link] into an [OpenApiObject].
   *
   * @param link The [Link] to convert.
   * @return The [OpenApiObject] representation.
   */
  fun linkToObject(link: Link): OpenApiObject {
    link.reference?.let {
      return referenceToObject(it)
    }
    link.ref?.let {
      val refMap = LinkedHashMap<String, OpenApiValue>()
      refMap["\$ref"] = OpenApiString(it)
      refMap.putStringIfNotNull("description", link.description)
      return OpenApiObject(refMap)
    }

    val map = LinkedHashMap<String, OpenApiValue>()
    map.putStringIfNotNull("operationId", link.operationId)
    map.putStringIfNotNull("operationRef", link.operationRef)
    if (link.parameters.isNotEmpty()) map["parameters"] = OpenApiObject(link.parameters)
    link.requestBody?.let { map["requestBody"] = it }
    map.putStringIfNotNull("description", link.description)
    link.server?.let { map["server"] = serverToObject(it) }
    map.putExtensions(link.extensions)
    return OpenApiObject(map)
  }

  private fun contentToObject(content: Map<String, MediaTypeObject>): OpenApiObject {
    return OpenApiObject(content.mapValues { mediaTypeToObject(it.value) })
  }

  /**
   * Converts a [MediaTypeObject] into an [OpenApiObject].
   *
   * @param mediaType The [MediaTypeObject] to convert.
   * @return The [OpenApiObject] representation.
   */
  fun mediaTypeToObject(mediaType: MediaTypeObject): OpenApiObject {
    mediaType.reference?.let {
      return referenceToObject(it)
    }
    mediaType.ref?.let {
      return OpenApiObject(mapOf("\$ref" to OpenApiString(it)))
    }
    val map = LinkedHashMap<String, OpenApiValue>()
    map.putSchemaIfNotNull("schema", mediaType.schema)
    map.putSchemaIfNotNull("itemSchema", mediaType.itemSchema)
    exampleValue(mediaType.example)?.let { map["example"] = it }
    map.putMapIfNotEmpty("examples", exampleMapToValues(mediaType.examples))
    map.putMapIfNotEmpty("encoding", mediaType.encoding.mapValues { encodingToObject(it.value) })
    map.putListIfNotEmpty("prefixEncoding", mediaType.prefixEncoding.map { encodingToObject(it) })
    mediaType.itemEncoding?.let { map["itemEncoding"] = encodingToObject(it) }
    map.putExtensions(mediaType.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts an [EncodingObject] into an [OpenApiObject].
   *
   * @param encoding The [EncodingObject] to convert.
   * @return The [OpenApiObject] representation.
   */
  fun encodingToObject(encoding: EncodingObject): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map.putStringIfNotNull("contentType", encoding.contentType)
    map.putMapIfNotEmpty("headers", encoding.headers.mapValues { headerToObject(it.value, false) })
    encoding.style?.let { map["style"] = OpenApiString(parameterStyle(it)) }
    encoding.explode?.let { map["explode"] = OpenApiBoolean(it) }
    encoding.allowReserved?.let { map["allowReserved"] = OpenApiBoolean(it) }
    map.putMapIfNotEmpty("encoding", encoding.encoding.mapValues { encodingToObject(it.value) })
    map.putListIfNotEmpty("prefixEncoding", encoding.prefixEncoding.map { encodingToObject(it) })
    encoding.itemEncoding?.let { map["itemEncoding"] = encodingToObject(it) }
    map.putExtensions(encoding.extensions)
    return OpenApiObject(map)
  }

  private fun componentsToObject(
      components: Components,
      refBase: String?,
      isSwagger2: Boolean
  ): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map.putMapIfNotEmpty(
        "schemas",
        components.schemas.mapValues {
          SchemaJsonCodec.schemaDefinitionToOpenApiValue(it.value, refBase)
        })
    map.putMapIfNotEmpty(
        "responses",
        components.responses.mapValues { responseToObject(it.value, refBase, isSwagger2) })
    map.putMapIfNotEmpty(
        "parameters",
        components.parameters.mapValues { parameterToObject(it.value, refBase, isSwagger2) })
    map.putMapIfNotEmpty(
        "requestBodies", components.requestBodies.mapValues { requestBodyToObject(it.value) })
    map.putMapIfNotEmpty(
        "headers", components.headers.mapValues { headerToObject(it.value, isSwagger2) })
    map.putMapIfNotEmpty(
        "securitySchemes",
        components.securitySchemes.mapValues { securitySchemeToObject(it.value, isSwagger2) })
    map.putMapIfNotEmpty("examples", exampleMapToValues(components.examples))
    map.putMapIfNotEmpty("links", components.links.mapValues { linkToObject(it.value) })
    map.putMapIfNotEmpty("callbacks", callbacksToMap(components.callbacks, refBase))
    map.putMapIfNotEmpty(
        "pathItems",
        components.pathItems.mapValues { pathItemToObject(it.value, refBase, isSwagger2) })
    map.putMapIfNotEmpty(
        "mediaTypes", components.mediaTypes.mapValues { mediaTypeToObject(it.value) })
    map.putExtensions(components.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts a [ReferenceObject] into an [OpenApiObject].
   *
   * @param reference The [ReferenceObject] to convert.
   * @return The [OpenApiObject] representation.
   */
  fun referenceToObject(reference: ReferenceObject): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["\$ref"] = OpenApiString(reference.ref)
    map.putStringIfNotNull("summary", reference.summary)
    map.putStringIfNotNull("description", reference.description)
    map.putExtensions(reference.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts a [Tag] into an [OpenApiObject].
   *
   * @param tag The [Tag] to convert.
   * @return The [OpenApiObject] representation.
   */
  fun tagToObject(tag: Tag): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["name"] = OpenApiString(tag.name)
    map.putStringIfNotNull("summary", tag.summary)
    map.putStringIfNotNull("description", tag.description)
    if (tag.externalDocs != null) {
      map["externalDocs"] = SchemaJsonCodec.externalDocsToOpenApiObject(tag.externalDocs)
    }
    map.putStringIfNotNull("parent", tag.parent)
    map.putStringIfNotNull("kind", tag.kind)
    map.putExtensions(tag.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts a [SecurityScheme] into an [OpenApiObject].
   *
   * @param scheme The [SecurityScheme] to convert.
   * @param isSwagger2 Whether serializing in Swagger 2.0 mode.
   * @return The [OpenApiObject] representation.
   */
  fun securitySchemeToObject(scheme: SecurityScheme, isSwagger2: Boolean): OpenApiObject {
    scheme.reference?.let {
      return referenceToObject(it)
    }
    val map = LinkedHashMap<String, OpenApiValue>()
    map["type"] = OpenApiString(scheme.type)
    map.putStringIfNotNull("description", scheme.description)
    map.putStringIfNotNull("name", scheme.name)
    map.putStringIfNotNull("in", scheme.`in`)
    if (isSwagger2) {
      map.putStringIfNotNull("flow", scheme.flow)
      map.putStringIfNotNull("authorizationUrl", scheme.authorizationUrl)
      map.putStringIfNotNull("tokenUrl", scheme.tokenUrl)
      if (!scheme.scopes.isNullOrEmpty()) {
        map["scopes"] = OpenApiObject(scheme.scopes.mapValues { (_, v) -> OpenApiString(v) })
      }
    } else {
      map.putStringIfNotNull("scheme", scheme.scheme)
      map.putStringIfNotNull("bearerFormat", scheme.bearerFormat)
      scheme.flows?.let { map["flows"] = oauthFlowsToObject(it) }
      map.putStringIfNotNull("openIdConnectUrl", scheme.openIdConnectUrl)
      map.putStringIfNotNull("oauth2MetadataUrl", scheme.oauth2MetadataUrl)
    }
    map.putIfTrue("deprecated", scheme.deprecated)
    map.putExtensions(scheme.extensions)
    return OpenApiObject(map)
  }

  private fun oauthFlowsToObject(flows: OAuthFlows): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map.putFlowIfNotNull("implicit", flows.implicit)
    map.putFlowIfNotNull("password", flows.password)
    map.putFlowIfNotNull("clientCredentials", flows.clientCredentials)
    map.putFlowIfNotNull("authorizationCode", flows.authorizationCode)
    map.putFlowIfNotNull("deviceAuthorization", flows.deviceAuthorization)
    map.putExtensions(flows.extensions)
    return OpenApiObject(map)
  }

  private fun MutableMap<String, OpenApiValue>.putFlowIfNotNull(key: String, flow: OAuthFlow?) {
    if (flow != null) this[key] = oauthFlowToObject(flow)
  }

  private fun oauthFlowToObject(flow: OAuthFlow): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["scopes"] = OpenApiObject(flow.scopes.mapValues { (_, v) -> OpenApiString(v) })
    map.putStringIfNotNull("authorizationUrl", flow.authorizationUrl)
    map.putStringIfNotNull("tokenUrl", flow.tokenUrl)
    map.putStringIfNotNull("refreshUrl", flow.refreshUrl)
    map.putStringIfNotNull("deviceAuthorizationUrl", flow.deviceAuthorizationUrl)
    map.putExtensions(flow.extensions)
    return OpenApiObject(map)
  }

  private fun exampleMapToValues(examples: Map<String, ExampleObject>): Map<String, OpenApiValue> {
    if (examples.isEmpty()) return emptyMap()
    return examples.mapValues { exampleObjectToObject(it.value) }
  }

  /**
   * Converts an [ExampleObject] into an [OpenApiObject].
   *
   * @param example The [ExampleObject] to convert.
   * @return The [OpenApiObject] representation.
   */
  fun exampleObjectToObject(example: ExampleObject): OpenApiObject {
    example.ref?.let {
      val refMap = LinkedHashMap<String, OpenApiValue>()
      refMap["\$ref"] = OpenApiString(it)
      refMap.putStringIfNotNull("summary", example.summary)
      refMap.putStringIfNotNull("description", example.description)
      return OpenApiObject(refMap)
    }

    val map = LinkedHashMap<String, OpenApiValue>()
    map.putStringIfNotNull("summary", example.summary)
    map.putStringIfNotNull("description", example.description)
    example.dataValue?.let { map["dataValue"] = it }
    map.putStringIfNotNull("serializedValue", example.serializedValue)
    map.putStringIfNotNull("externalValue", example.externalValue)
    example.value?.let { map["value"] = it }
    map.putExtensions(example.extensions)
    return OpenApiObject(map)
  }

  private fun exampleValue(example: ExampleObject?): OpenApiValue? {
    if (example == null) return null
    if (example.ref != null) return OpenApiObject(mapOf("\$ref" to OpenApiString(example.ref)))
    return example.dataValue
        ?: example.value
        ?: example.serializedValue?.let { OpenApiString(it) }
        ?: example.externalValue?.let { OpenApiString(it) }
  }

  private fun pathsToObject(
      paths: Map<String, PathItem>,
      extensions: Map<String, OpenApiValue>,
      refBase: String?,
      isSwagger2: Boolean
  ): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    paths.forEach { (path, item) -> map[path] = pathItemToObject(item, refBase, isSwagger2) }
    map.putExtensions(extensions)
    return OpenApiObject(map)
  }

  private fun normalizeSelfRefBase(self: String?): String? {
    val value = self?.trim().orEmpty()
    if (value.isBlank()) return null
    return value.substringBefore("#")
  }

  private fun componentRef(
      base: String?,
      component: String,
      name: String,
      isSwagger2: Boolean
  ): String {
    val encoded = encodeJsonPointerSegment(name)
    val suffix = if (isSwagger2) "#/definitions/$encoded" else "#/components/$component/$encoded"
    val prefix = base.orEmpty().trimEnd('#').trimEnd()
    return if (prefix.isEmpty()) suffix else "$prefix$suffix"
  }

  private fun encodeJsonPointerSegment(value: String): String {
    return value.replace("~", "~0").replace("/", "~1")
  }

  private fun parameterLocation(location: ParameterLocation): String {
    return when (location) {
      ParameterLocation.PATH -> "path"
      ParameterLocation.QUERY -> "query"
      ParameterLocation.QUERYSTRING -> "querystring"
      ParameterLocation.HEADER -> "header"
      ParameterLocation.COOKIE -> "cookie"
      ParameterLocation.BODY -> "body"
      ParameterLocation.FORMDATA -> "formData"
    }
  }

  private fun parameterStyle(style: ParameterStyle): String {
    return when (style) {
      ParameterStyle.MATRIX -> "matrix"
      ParameterStyle.LABEL -> "label"
      ParameterStyle.SIMPLE -> "simple"
      ParameterStyle.FORM -> "form"
      ParameterStyle.SPACE_DELIMITED -> "spaceDelimited"
      ParameterStyle.PIPE_DELIMITED -> "pipeDelimited"
      ParameterStyle.DEEP_OBJECT -> "deepObject"
      ParameterStyle.COOKIE -> "cookie"
    }
  }

  private fun securityToValue(
      security: List<SecurityRequirement>,
      explicitEmpty: Boolean
  ): OpenApiArray? {
    if (security.isNotEmpty()) {
      return OpenApiArray(
          security.map { requirement ->
            OpenApiObject(
                requirement.entries.associate { (scheme, scopes) ->
                  scheme to OpenApiArray(scopes.map { OpenApiString(it) })
                })
          })
    }
    return if (explicitEmpty) OpenApiArray(emptyList()) else null
  }

  private fun MutableMap<String, OpenApiValue>.putSchemaIfNotNull(
      key: String,
      schema: SchemaProperty?
  ) {
    if (schema != null) this[key] = SchemaJsonCodec.schemaPropertyToOpenApiValue(schema)
  }

  private fun MutableMap<String, OpenApiValue>.putStringIfNotNull(key: String, value: String?) {
    if (value != null) this[key] = OpenApiString(value)
  }

  private fun MutableMap<String, OpenApiValue>.putStringListIfNotEmpty(
      key: String,
      value: List<String>
  ) {
    if (value.isNotEmpty()) this[key] = OpenApiArray(value.map { OpenApiString(it) })
  }

  private fun MutableMap<String, OpenApiValue>.putListIfNotEmpty(
      key: String,
      value: List<OpenApiValue>
  ) {
    if (value.isNotEmpty()) this[key] = OpenApiArray(value)
  }

  private fun MutableMap<String, OpenApiValue>.putMapIfNotEmpty(
      key: String,
      value: Map<String, OpenApiValue>
  ) {
    if (value.isNotEmpty()) this[key] = OpenApiObject(value)
  }

  private fun MutableMap<String, OpenApiValue>.putIfTrue(key: String, value: Boolean) {
    if (value) this[key] = OpenApiBoolean(value)
  }

  private fun MutableMap<String, OpenApiValue>.putExtensions(
      extensions: Map<String, OpenApiValue>
  ) {
    extensions.forEach { (key, value) -> this[key] = value }
  }
}
