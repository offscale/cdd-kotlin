/**
 * Generates Ktor client endpoint method signatures, request bodies, multipart/form-data encoding
 * calls, response handling, and operation KDoc blocks.
 */
package psi.network

import domain.Callback
import domain.EncodingObject
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.ExampleObject
import domain.ExternalDocumentation
import domain.Header
import domain.HttpMethod
import domain.Info
import domain.Link
import domain.MediaTypeObject
import domain.OpenApiArray
import domain.OpenApiBoolean
import domain.OpenApiDefinition
import domain.OpenApiObject
import domain.OpenApiString
import domain.OpenApiValue
import domain.OpenApiValues
import domain.ParameterLocation
import domain.ParameterStyle
import domain.PathItem
import domain.ReferenceObject
import domain.RequestBody
import domain.SchemaProperty
import domain.stringOrNull
import domain.toDisplayString
import kotlinx.serialization.json.Json
import openapi.OpenApiWriter
import openapi.get
import psi.TypeMappers
import psi.capitalizeName

/** Generates Ktor endpoint signatures, method bodies, and operation-level KDoc blocks. */
object EndpointMethodGenerator {
  private val openApiWriter = OpenApiWriter()

  /**
   * Encodes an [OpenApiValue] into a compact JSON string.
   *
   * @param value Value to encode.
   * @return Compact JSON string.
   */
  fun encodeCompactJson(value: OpenApiValue): String = OpenApiValues.encodeToString(value)

  /**
   * Renders `x-` specification extensions into a compact JSON string.
   *
   * @param extensions Extensions map.
   * @return Compact JSON string.
   */
  fun renderExtensions(extensions: Map<String, OpenApiValue>): String {
    val filtered = extensions.filterKeys { it.startsWith("x-") }
    return encodeCompactJson(OpenApiObject(filtered))
  }

  private fun renderExternalDocs(docs: ExternalDocumentation): String =
      psi.metadata.InfoMetadataCodec.renderExternalDocs(docs)

  /**
   * Generates the operation KDoc block for [ep].
   *
   * @param ep Endpoint definition.
   * @return KDoc string.
   */
  fun generateKDoc(ep: EndpointDefinition): String {
    val lines = mutableListOf<String>()
    val hasSummary = !ep.summary.isNullOrBlank()
    if (hasSummary) {
      lines.add("     * ${ep.summary}")
    }
    if (!ep.description.isNullOrBlank()) {
      if (hasSummary) lines.add("     *")
      lines.add("     * ${ep.description}")
    }
    if (ep.externalDocs != null) {
      val docs = ep.externalDocs
      if (docs.extensions.isNotEmpty()) {
        lines.add("     * @externalDocs ${renderExternalDocs(docs)}")
      } else {
        val descSuffix = if (docs.description != null) " ${docs.description}" else ""
        lines.add("     * @see ${docs.url}$descSuffix")
      }
    }
    if (ep.tags.isNotEmpty()) {
      lines.add("     * @tag ${ep.tags.joinToString(", ")}")
    }
    if (ep.servers.isNotEmpty()) {
      val serversJson =
          encodeCompactJson(
              OpenApiArray(ep.servers.map { psi.metadata.InfoMetadataCodec.serverToDocValue(it) }))
      lines.add("     * @servers $serversJson")
    }
    if (!ep.operationIdExplicit) {
      lines.add("     * @operationIdOmitted")
    }
    if (ep.securityExplicitEmpty) {
      lines.add("     * @securityEmpty")
    }
    ep.security.forEach { requirement ->
      lines.add("     * @security ${renderSecurityRequirement(requirement)}")
    }
    if (ep.deprecated) {
      lines.add("     * @deprecated")
    }
    ep.parameters
        .filter { !it.description.isNullOrBlank() }
        .forEach { param -> lines.add("     * @param ${param.name} ${param.description}") }
    ep.parameters.forEach { param ->
      param.style?.let { lines.add("     * @paramStyle ${param.name} ${parameterStyleValue(it)}") }
      param.explode?.let { lines.add("     * @paramExplode ${param.name} $it") }
      param.allowReserved?.let { lines.add("     * @paramAllowReserved ${param.name} $it") }
      param.allowEmptyValue?.let { lines.add("     * @paramAllowEmptyValue ${param.name} $it") }
    }
    ep.parameters
        .filter { it.reference != null }
        .forEach { param ->
          val refJson = encodeCompactJson(referenceToDocValue(param.reference!!))
          lines.add("     * @paramRef ${param.name} $refJson")
        }
    ep.parameters
        .filter { it.schema != null && it.content.isEmpty() }
        .forEach { param ->
          val schemaJson = renderParamSchema(param.schema!!)
          lines.add("     * @paramSchema ${param.name} $schemaJson")
        }
    ep.parameters
        .filter { it.content.isNotEmpty() }
        .forEach { param ->
          val contentJson = renderParamContent(param.content)
          lines.add("     * @paramContent ${param.name} $contentJson")
        }
    ep.parameters
        .filter { it.extensions.isNotEmpty() }
        .forEach { param ->
          val json = renderExtensions(param.extensions)
          lines.add("     * @paramExtensions ${param.name} $json")
        }
    ep.parameters.forEach { param ->
      param.example?.let { example ->
        lines.add("     * @paramExample ${param.name} ${formatExampleValue(example)}")
      }
      param.examples.forEach { (key, example) ->
        lines.add("     * @paramExample ${param.name} $key: ${formatExampleValue(example)}")
      }
    }
    val requestBody = ep.requestBody
    if (requestBody != null) {
      val docVal = requestBodyToDocValue(requestBody)
      if (docVal.properties.isNotEmpty()) {
        lines.add("     * @requestBody ${encodeCompactJson(docVal)}")
      }
    }
    ep.responses.forEach { (code, resp) ->
      val typeStr = resp.type ?: "Unit"
      val descStr = resp.description ?: ""
      lines.add("     * @response $code $typeStr $descStr")
    }
    ep.responses
        .filterValues { it.reference != null }
        .forEach { (code, resp) ->
          val refJson = encodeCompactJson(referenceToDocValue(resp.reference!!))
          lines.add("     * @responseRef $code $refJson")
        }
    ep.responses
        .filterValues { it.extensions.isNotEmpty() }
        .forEach { (code, resp) ->
          lines.add("     * @responseExtensions $code ${renderExtensions(resp.extensions)}")
        }
    ep.responses
        .filterValues { it.headers.isNotEmpty() }
        .forEach { (code, resp) ->
          lines.add("     * @responseHeaders $code ${renderResponseHeaders(resp.headers)}")
        }
    ep.responses.forEach { (code, resp) ->
      val links = resp.links.orEmpty()
      if (links.isNotEmpty()) {
        lines.add("     * @responseLinks $code ${renderResponseLinks(links)}")
      }
    }
    ep.responses
        .filterValues { it.content.isNotEmpty() }
        .forEach { (code, resp) ->
          lines.add("     * @responseContent $code ${renderResponseContent(resp.content)}")
        }
    ep.responses
        .filterValues { !it.summary.isNullOrBlank() }
        .forEach { (code, resp) -> lines.add("     * @responseSummary $code ${resp.summary}") }
    if (ep.callbacks.isNotEmpty()) {
      lines.add("     * @callbacks ${renderCallbacks(ep.callbacks)}")
    }
    if (ep.extensions.isNotEmpty()) {
      lines.add("     * @extensions ${encodeCompactJson(OpenApiObject(ep.extensions))}")
    }
    if (lines.isEmpty()) return ""
    return "    /**\n" + lines.joinToString("\n") + "\n     */\n"
  }

  private fun renderSecurityRequirement(requirement: Map<String, List<String>>): String {
    return psi.metadata.SecurityMetadataCodec.renderSecurityRequirement(requirement)
  }

  private fun parameterStyleValue(style: ParameterStyle): String {
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

  private fun formatExampleValue(example: domain.ExampleObject): String {
    val hasMeta =
        listOfNotNull(example.summary, example.description, example.ref).isNotEmpty() ||
            example.extensions.isNotEmpty()
    val nonNullFields =
        listOfNotNull(
            example.externalValue, example.serializedValue, example.dataValue, example.value)
    if (!hasMeta && nonNullFields.size == 1) {
      return when {
        example.externalValue != null -> "external:${example.externalValue}"
        example.serializedValue != null -> example.serializedValue
        example.value != null -> example.value.toDisplayString()
        else -> (example.dataValue as OpenApiValue).toDisplayString()
      }
    }
    return encodeCompactJson(exampleObjectToDocValue(example))
  }

  private fun renderResponseHeaders(headers: Map<String, Header>): String {
    val filtered = headers.filterKeys { !it.equals("Content-Type", ignoreCase = true) }
    val mapped = filtered.mapValues { headerToDocValue(it.value) }
    return encodeCompactJson(OpenApiObject(mapped))
  }

  private fun renderResponseLinks(links: Map<String, Link>): String {
    val mapped = links.mapValues { linkToDocValue(it.value) }
    return encodeCompactJson(OpenApiObject(mapped))
  }

  private fun renderResponseContent(content: Map<String, MediaTypeObject>): String {
    val mapped = content.mapValues { mediaTypeToDocValue(it.value) }
    return encodeCompactJson(OpenApiObject(mapped))
  }

  /**
   * Serializes a callbacks map into a compact JSON string.
   *
   * @param callbacks Callbacks map.
   * @return JSON string.
   */
  fun renderCallbacks(callbacks: Map<String, Callback>): String {
    if (callbacks.isEmpty()) return "{}"
    val tempOperation =
        EndpointDefinition(
            path = "/_callbacks",
            method = HttpMethod.POST,
            operationId = "callbacks",
            responses = mapOf("200" to EndpointResponse(statusCode = "200", description = "ok")),
            callbacks = callbacks)
    val tempDefinition =
        OpenApiDefinition(
            info = Info(title = "Callbacks", version = "0.0.0"),
            paths = mapOf("/_callbacks" to PathItem(post = tempOperation)))
    val json = openApiWriter.writeJson(tempDefinition)
    val node = Json.parseToJsonElement(json)
    val callbacksNode = node.get("paths")!!.get("/_callbacks")!!.get("post")!!.get("callbacks")!!
    return OpenApiValues.encodeJsonElementToString(callbacksNode)
  }

  private fun renderParamSchema(schema: SchemaProperty): String {
    val mapped = schemaPropertyToDocValue(schema)
    return encodeCompactJson(mapped)
  }

  private fun renderParamContent(content: Map<String, MediaTypeObject>): String {
    val mapped = content.mapValues { mediaTypeToDocValue(it.value) }
    return encodeCompactJson(OpenApiObject(mapped))
  }

  private fun requestBodyToDocValue(body: RequestBody): OpenApiObject {
    body.reference?.let {
      return referenceToDocValue(it)
    }
    val map = LinkedHashMap<String, OpenApiValue>()
    body.description?.let { map["description"] = OpenApiString(it) }
    if (body.required) map["required"] = OpenApiBoolean(true)
    val hasContent = body.contentPresent || body.content.isNotEmpty()
    if (hasContent) {
      map["content"] = OpenApiObject(body.content.mapValues { mediaTypeToDocValue(it.value) })
    }
    body.extensions.forEach { (k, v) -> map[k] = v }
    return OpenApiObject(map)
  }

  private fun headerToDocValue(header: Header): OpenApiObject {
    val map =
        LinkedHashMap<String, OpenApiValue>(openApiWriter.headerToObject(header, false).properties)
    if (header.reference == null) {
      if (header.style != null) map["style"] = OpenApiString(parameterStyleValue(header.style))
      header.explode?.let { map["explode"] = OpenApiBoolean(it) }
      header.example?.let { map["example"] = exampleObjectToDocValue(it) }
    }
    return OpenApiObject(map)
  }

  /**
   * Converts a [Link] into a strongly-typed [OpenApiObject] doc value.
   *
   * @param link Link object.
   * @return [OpenApiObject] doc value.
   */
  fun linkToDocValue(link: Link): OpenApiObject = openApiWriter.linkToObject(link)

  private fun mediaTypeToDocValue(mediaType: MediaTypeObject): OpenApiObject {
    val map =
        LinkedHashMap<String, OpenApiValue>(openApiWriter.mediaTypeToObject(mediaType).properties)
    mediaType.example?.let { map["example"] = exampleObjectToDocValue(it) }
    return OpenApiObject(map)
  }

  private fun schemaPropertyToDocValue(schema: SchemaProperty): OpenApiValue =
      psi.dto.SchemaDocTagWriter.schemaPropertyToDocValue(schema)

  /**
   * Converts an [ExampleObject] into a strongly-typed [OpenApiObject] doc value.
   *
   * @param example Example object.
   * @return [OpenApiObject] doc representation.
   */
  fun exampleObjectToDocValue(example: ExampleObject): OpenApiObject =
      openApiWriter.exampleObjectToObject(example)

  private fun referenceToDocValue(reference: ReferenceObject): OpenApiObject =
      openApiWriter.referenceToObject(reference)

  /**
   * Generates the Kotlin function signature for an endpoint returning `Result<T>`.
   *
   * @param ep Endpoint definition.
   * @param isOverride Whether to include `override`.
   * @return Signature string.
   */
  fun generateMethodSignature(ep: EndpointDefinition, isOverride: Boolean = false): String {
    val params =
        ep.parameters
            .map { param ->
              val optional = isOptionalParam(param)
              val type = resolveParameterType(param, optional)
              val deprecatedAnnotation =
                  if (param.deprecated) "@Deprecated(\"Deprecated parameter\") " else ""
              val defaultValue = if (optional && !isOverride) " = null" else ""
              "$deprecatedAnnotation${param.safeName}: $type$defaultValue"
            }
            .toMutableList()

    val bodySignature = resolveRequestBodySignature(ep)
    if (bodySignature != null) {
      val defaultValue = if (bodySignature.isOptional && !isOverride) " = null" else ""
      params.add("body: ${bodySignature.kotlinType}$defaultValue")
    }

    val paramString = params.joinToString(", ")
    val returnType = resolveResponseType(ep)
    val modifier = if (isOverride) "override suspend" else "suspend"
    return "$modifier fun ${ep.operationId}($paramString): Result<$returnType>"
  }

  /**
   * Generates the Ktor implementation block for an endpoint wrapped in a [Result].
   *
   * @param ep Endpoint definition.
   * @return A [Result] containing the method implementation string or a
   *   [domain.CddError.ParameterSerializationError].
   */
  fun generateMethodImpl(ep: EndpointDefinition): Result<String> {
    val returnType = resolveResponseType(ep)
    val signature = generateMethodSignature(ep, isOverride = true)

    // 0. Querystring constraint (OAS 3.2): cannot mix query and querystring
    val queryParams = ep.parameters.filter { it.location == ParameterLocation.QUERY }
    val queryStringParam =
        ep.parameters.firstOrNull { it.location == ParameterLocation.QUERYSTRING }
    if (queryStringParam != null && queryParams.isNotEmpty()) {
      return domain.CddError.ParameterSerializationError(
              queryStringParam.name,
              "OAS 3.2: querystring and query parameters cannot be used together for ${ep.operationId}")
          .let { Result.failure(domain.CddException(it)) }
    }

    // 1. Build Path String based on Style (Matrix, Label, Simple) and array/object serialization
    var currentPath = ep.path
    for (param in ep.parameters.filter { it.location == ParameterLocation.PATH }) {
      val placeholder = "{${param.name}}"
      val isMatrixOrLabel =
          param.style == ParameterStyle.MATRIX || param.style == ParameterStyle.LABEL

      // If Matrix or Label style, we absorb the preceding slash if present
      val target =
          if (isMatrixOrLabel && currentPath.contains("/$placeholder")) {
            "/$placeholder"
          } else {
            placeholder
          }

      val paramType = resolveParameterType(param, forceNullable = false)
      val replacement =
          ParameterSerializerGenerator.buildPathParamReplacement(param, paramType).getOrElse {
            return Result.failure(it)
          }
      currentPath = currentPath.replace(target, replacement)
    }
    val pathTemplate = currentPath

    val baseUrlExpr =
        if (ep.servers.isNotEmpty()) {
          val resolved = ServerUrlResolverGenerator.resolveServerUrlTemplate(ep.servers.first())
          ServerUrlResolverGenerator.escapeKotlinString(resolved)
        } else {
          "\$baseUrl"
        }
    val fullUrl =
        if (pathTemplate.startsWith("/")) "$baseUrlExpr$pathTemplate"
        else "$baseUrlExpr/$pathTemplate"

    // 2. Build Query/Header/Cookie Config
    val paramLines = mutableListOf<String>()
    for (param in ep.parameters) {
      val optional = isOptionalParam(param)
      val paramType = resolveParameterType(param, optional)
      if (param.location == ParameterLocation.QUERY) {
        val queryLines =
            ParameterSerializerGenerator.buildQueryParamLines(param, paramType).getOrElse {
              return Result.failure(it)
            }
        paramLines.addAll(
            ParameterSerializerGenerator.wrapOptional(
                optional, param.safeName, queryLines.lines, queryLines.emptyValueLine))
      } else if (param.location == ParameterLocation.HEADER) {
        val baseLines =
            ParameterSerializerGenerator.buildHeaderParamLines(param, paramType).getOrElse {
              return Result.failure(it)
            }
        paramLines.addAll(
            ParameterSerializerGenerator.wrapOptional(optional, param.safeName, baseLines))
      } else if (param.location == ParameterLocation.COOKIE) {
        val baseLines =
            ParameterSerializerGenerator.buildCookieParamLines(param, paramType).getOrElse {
              return Result.failure(it)
            }
        paramLines.addAll(
            ParameterSerializerGenerator.wrapOptional(optional, param.safeName, baseLines))
      }
    }

    val paramConfig =
        if (paramLines.isNotEmpty()) {
          "\n            " + paramLines.joinToString("\n            ")
        } else {
          ""
        }

    val queryStringContentType = queryStringParam?.let { resolveQueryStringContentType(it) }
    if (queryStringParam != null && queryStringContentType == null) {
      val cleanType =
          resolveParameterType(queryStringParam, isOptionalParam(queryStringParam)).replace(" ", "")
      val isString = cleanType.removeSuffix("?") == "String"
      if (!isString) {
        return domain.CddError.ParameterSerializationError(
                queryStringParam.name,
                "OAS 3.2: querystring parameter must be String when content is not defined for ${ep.operationId}")
            .let { Result.failure(domain.CddException(it)) }
      }
    }

    val queryStringConfig =
        if (queryStringParam != null) {
          val assignment =
              if (queryStringContentType != null) {
                "url.encodedQuery = encodeQueryStringContent(${queryStringParam.name}, \"$queryStringContentType\")"
              } else {
                "url.encodedQuery = ${queryStringParam.name}"
              }
          if (isOptionalParam(queryStringParam)) {
            "\n            if (${queryStringParam.name} != null) {\n                $assignment\n            }"
          } else {
            "\n            $assignment"
          }
        } else {
          ""
        }

    val bodySignature = resolveRequestBodySignature(ep)
    val hasBody = bodySignature != null
    val bodyVarName = "body"
    val isBodyOptional = bodySignature?.isOptional ?: false
    val bodyKotlinType = resolveBodyKotlinType(ep)

    val requestMediaType = resolveRequestMediaTypeEntry(ep)
    val contentType = requestMediaType?.key
    val isFormUrlEncoded = isFormUrlEncodedMediaType(contentType.orEmpty())
    val isMultipartFormData = isMultipartFormDataMediaType(contentType.orEmpty())
    val isMultipart = isMultipartMediaType(contentType.orEmpty())
    val mediaValue = requestMediaType?.value
    val usesPositionalMultipart = isMultipart and hasPositionalEncoding(mediaValue)
    val sequentialRequestElementType =
        if (isSequentialJsonMediaType(contentType.orEmpty())) {
          extractListElementType(bodyKotlinType)
        } else {
          null
        }
    val encodingLiteral = renderEncodingContentTypeLiteral(mediaValue)
    val encodingStyleLiteral = renderEncodingStyleLiteral(mediaValue)
    val encodingExplodeLiteral = renderEncodingExplodeLiteral(mediaValue)
    val encodingAllowReservedLiteral = renderEncodingAllowReservedLiteral(mediaValue)
    val encodingHeadersLiteral = renderEncodingHeadersLiteral(mediaValue)
    val prefixEncodingContentTypesLiteral = renderPrefixEncodingContentTypesLiteral(mediaValue)
    val prefixEncodingHeadersLiteral = renderPrefixEncodingHeadersLiteral(mediaValue)
    val itemEncodingContentTypeLiteral =
        renderItemEncodingContentTypeLiteral(mediaValue?.itemEncoding)
    val itemEncodingHeadersLiteral = renderItemEncodingHeadersLiteral(mediaValue?.itemEncoding)
    val bodyConfig =
        if (hasBody) {
          val lines = mutableListOf<String>()
          if (!isFormUrlEncoded &&
              !isMultipartFormData &&
              isConcreteMediaType(contentType.orEmpty())) {
            lines.add("contentType(ContentType.parse(\"$contentType\"))")
          }
          val encodingArg = if (encodingLiteral != null) ", encoding = $encodingLiteral" else ""
          val encodingStyleArg =
              if (encodingStyleLiteral != null) ", styles = $encodingStyleLiteral" else ""
          val encodingExplodeArg =
              if (encodingExplodeLiteral != null) ", explode = $encodingExplodeLiteral" else ""
          val encodingAllowReservedArg =
              if (encodingAllowReservedLiteral != null)
                  ", allowReserved = $encodingAllowReservedLiteral"
              else ""
          val encodingHeadersArg =
              if (encodingHeadersLiteral != null) ", headers = $encodingHeadersLiteral" else ""
          val positionalArgsList = mutableListOf<String>()
          if (prefixEncodingContentTypesLiteral != null) {
            positionalArgsList.add("prefixContentTypes = $prefixEncodingContentTypesLiteral")
          }
          if (prefixEncodingHeadersLiteral != null) {
            positionalArgsList.add("prefixHeaders = $prefixEncodingHeadersLiteral")
          }
          if (itemEncodingContentTypeLiteral != null) {
            positionalArgsList.add("itemContentType = $itemEncodingContentTypeLiteral")
          }
          if (itemEncodingHeadersLiteral != null) {
            positionalArgsList.add("itemHeaders = $itemEncodingHeadersLiteral")
          }
          val positionalArgs =
              if (positionalArgsList.isNotEmpty()) {
                ", " + positionalArgsList.joinToString(", ")
              } else {
                ""
              }
          val bodyLine =
              when {
                sequentialRequestElementType != null ->
                    "setBody(encodeSequentialJson($bodyVarName, \"${ServerUrlResolverGenerator.escapeKotlinString(contentType!!)}\"))"
                usesPositionalMultipart ->
                    "setBody(encodeMultipartPositional($bodyVarName$positionalArgs))"
                isFormUrlEncoded ->
                    "setBody(encodeFormBody($bodyVarName$encodingArg$encodingStyleArg$encodingExplodeArg$encodingAllowReservedArg))"
                isMultipartFormData ->
                    "setBody(encodeMultipartBody($bodyVarName$encodingArg$encodingHeadersArg))"
                else -> "setBody($bodyVarName)"
              }
          lines.add(bodyLine)

          if (isBodyOptional) {
            val joined = lines.joinToString("\n                ")
            "\n            if ($bodyVarName != null) {\n                $joined\n            }"
          } else {
            "\n            " + lines.joinToString("\n            ")
          }
        } else {
          ""
        }

    val methodStr =
        when (ep.method) {
          domain.HttpMethod.CUSTOM -> {
            val rawMethod = ep.customMethod.orEmpty()
            val safeMethod = rawMethod.replace("\"", "\\\"")
            "HttpMethod(\"$safeMethod\")"
          }
          domain.HttpMethod.QUERY -> "HttpMethod(\"QUERY\")"
          else -> {
            val methodEnumName = capitalizeName(ep.method.name.lowercase())
            "HttpMethod.$methodEnumName"
          }
        }

    val responseMediaType = resolveResponseMediaTypeEntry(ep)
    val responseContentType = responseMediaType?.key
    val sequentialResponseElementType =
        if (responseContentType != null && isSequentialJsonMediaType(responseContentType)) {
          extractListElementType(returnType)
        } else {
          null
        }
    val successBodyExpr =
        if (sequentialResponseElementType != null) {
          "decodeSequentialJsonList<$sequentialResponseElementType>(response.bodyAsText(), \"${ServerUrlResolverGenerator.escapeKotlinString(responseContentType!!)}\")"
        } else {
          "response.body<$returnType>()"
        }

    val code =
        """
    $signature {
        val response = client.request("$fullUrl") {
            method = $methodStr$paramConfig$queryStringConfig$bodyConfig
        }
        return if (response.status.isSuccess()) {
            Result.success($successBodyExpr)
        } else {
            Result.failure(ApiException("Error: " + response.status))
        }
    }
        """
            .trimIndent()
    return Result.success(code)
  }

  private fun resolveParameterType(param: EndpointParameter, forceNullable: Boolean): String {
    val schema = param.schema ?: selectSchema(param.content)
    val rawType =
        if (schema != null) {
          TypeMappers.mapType(schema)
        } else {
          param.type
        }

    val schemaTypes = if (schema != null) schema.types else emptySet()
    val requiresNullable = forceNullable || "null" in schemaTypes
    val cleanType = rawType.trim().removeSuffix("?")
    return if (requiresNullable) "$cleanType?" else cleanType
  }

  private fun resolveQueryStringContentType(param: EndpointParameter): String? {
    return resolveContentType(param.content)
  }

  /**
   * Resolves the preferred content type for [param].
   *
   * @param param Endpoint parameter.
   * @return Content type string or `null`.
   */
  fun resolveParameterContentType(param: EndpointParameter): String {
    return resolveContentType(param.content) ?: "application/json"
  }

  private fun resolveContentType(content: Map<String, MediaTypeObject>): String? {
    if (content.isEmpty()) return null
    val keys = content.keys
    val form = keys.firstOrNull { it.trim().startsWith("application/x-www-form-urlencoded") }
    if (form != null) return form
    val json =
        keys.firstOrNull {
          val trimmed = it.trim()
          trimmed.startsWith("application/json") || trimmed.substringBefore(";").endsWith("+json")
        }
    if (json != null) return json
    return keys.first()
  }

  private fun isOptionalParam(param: EndpointParameter): Boolean {
    if (param.location == ParameterLocation.PATH) return false
    return !param.isRequired
  }

  /**
   * Resolves the preferred request content type for [ep].
   *
   * @param ep Endpoint definition.
   * @return Content type string or `null`.
   */
  fun resolveRequestContentType(ep: EndpointDefinition): String? {
    return resolveRequestMediaTypeEntry(ep)?.key
  }

  private fun resolveRequestMediaTypeEntry(
      ep: EndpointDefinition
  ): Map.Entry<String, MediaTypeObject>? {
    val rb = ep.requestBody ?: return null
    if (rb.content.isEmpty()) return null
    return selectPreferredMediaTypeEntry(rb.content)
  }

  private fun resolveResponseMediaTypeEntry(
      ep: EndpointDefinition
  ): Map.Entry<String, MediaTypeObject>? {
    val success = ep.responses.keys.filter { it.startsWith("2") }.minOrNull() ?: return null
    val response = ep.responses.getValue(success)
    if (response.content.isEmpty()) return null
    return selectPreferredMediaTypeEntry(response.content)
  }

  /**
   * Checks whether [ep] requires sequential JSON request encoding.
   *
   * @param ep Endpoint definition.
   * @return `true` if sequential JSON request.
   */
  fun requiresSequentialJsonRequest(ep: EndpointDefinition): Boolean {
    val contentType = resolveRequestContentType(ep) ?: return false
    if (!isSequentialJsonMediaType(contentType)) return false

    return extractListElementType(resolveBodyKotlinType(ep)) != null
  }

  private fun resolveBodyKotlinType(ep: EndpointDefinition): String =
      resolveRequestBodyType(ep).orEmpty()

  /**
   * Checks whether [ep] requires positional multipart encoding.
   *
   * @param ep Endpoint definition.
   * @return `true` if positional multipart.
   */
  fun requiresMultipartPositionalEncoding(ep: EndpointDefinition): Boolean {
    val entry = resolveRequestMediaTypeEntry(ep) ?: return false
    if (!isMultipartMediaType(entry.key)) return false
    return hasPositionalEncoding(entry.value)
  }

  private fun hasPositionalEncoding(mediaType: MediaTypeObject?): Boolean {
    if (mediaType == null) return false
    return mediaType.prefixEncoding.isNotEmpty() or (mediaType.itemEncoding != null)
  }

  /**
   * Checks whether [ep] requires sequential JSON response decoding.
   *
   * @param ep Endpoint definition.
   * @return `true` if sequential JSON response.
   */
  fun requiresSequentialJsonResponse(ep: EndpointDefinition): Boolean {
    val responseEntry = resolveResponseMediaTypeEntry(ep) ?: return false
    if (!isSequentialJsonMediaType(responseEntry.key)) return false
    val returnType = resolveResponseType(ep)
    return extractListElementType(returnType) != null
  }

  private fun extractListElementType(type: String): String? {
    val trimmed = type.trim().removeSuffix("?")
    return when {
      trimmed.startsWith("List<") -> trimmed.substringAfter("List<").substringBeforeLast(">")
      trimmed.startsWith("MutableList<") ->
          trimmed.substringAfter("MutableList<").substringBeforeLast(">")
      else -> null
    }
  }

  private fun isSequentialJsonMediaType(contentType: String): Boolean {
    val normalized = contentType.substringBefore(";").trim().lowercase()
    return normalized == "application/jsonl" ||
        normalized == "application/x-ndjson" ||
        normalized == "application/ndjson" ||
        normalized.endsWith("json-seq")
  }

  private fun resolveRequestBodyType(ep: EndpointDefinition): String? {
    val rb = ep.requestBody
    val isOpt = rb != null && !rb.required
    ep.requestBodyType?.let { raw ->
      return if (isOpt) "${raw.trim().removeSuffix("?")}?" else raw
    }
    if (rb == null) return null
    val schema = selectSchema(rb.content) ?: return null
    val baseType = TypeMappers.mapType(schema)
    return if (isOpt) "$baseType?" else baseType
  }

  private data class RequestBodySignature(val kotlinType: String, val isOptional: Boolean)

  private fun resolveRequestBodySignature(ep: EndpointDefinition): RequestBodySignature? {
    val kotlinType = resolveRequestBodyType(ep) ?: return null
    val isOptional = kotlinType.trim().endsWith("?")
    return RequestBodySignature(kotlinType, isOptional)
  }

  private fun resolveResponseType(ep: EndpointDefinition): String {
    val success = ep.responses.keys.filter { it.startsWith("2") }.minOrNull() ?: return "Unit"
    val response = ep.responses.getValue(success)
    response.type?.let {
      return it
    }
    val schema = selectSchema(response.content) ?: return "Unit"
    return TypeMappers.mapType(schema)
  }

  private fun selectSchema(content: Map<String, MediaTypeObject>): SchemaProperty? {
    if (content.isEmpty()) return null
    val entry = selectPreferredMediaTypeEntry(content)
    val preferred = entry.value
    preferred.schema?.let {
      return it
    }
    preferred.itemSchema?.let {
      return wrapItemSchemaAsArray(it)
    }
    return inferSchemaFromMediaType(entry.key)
  }

  private fun wrapItemSchemaAsArray(itemSchema: SchemaProperty): SchemaProperty {
    return SchemaProperty(types = setOf("array"), items = itemSchema)
  }

  private fun inferSchemaFromMediaType(mediaTypeKey: String): SchemaProperty? {
    if (!isConcreteMediaType(mediaTypeKey)) return null
    val normalized = normalizeMediaTypeKey(mediaTypeKey)
    return when {
      isJsonMediaTypeKey(normalized) -> SchemaProperty(booleanSchema = true)
      isTextMediaTypeKey(normalized) -> SchemaProperty(types = setOf("string"))
      else -> SchemaProperty(types = setOf("string"), contentMediaType = normalized)
    }
  }

  private fun isJsonMediaTypeKey(value: String): Boolean {
    val normalized = normalizeMediaTypeKey(value)
    return normalized == "application/json" || normalized.endsWith("+json")
  }

  private fun isTextMediaTypeKey(value: String): Boolean {
    val normalized = normalizeMediaTypeKey(value)
    val exactText =
        setOf("application/xml", "application/x-www-form-urlencoded", "multipart/form-data")
    return listOf(
            normalized.startsWith("text/"), normalized.endsWith("+xml"), normalized in exactText)
        .any { it }
  }

  private fun selectPreferredMediaTypeEntry(
      content: Map<String, MediaTypeObject>
  ): Map.Entry<String, MediaTypeObject> {
    val entries = content.entries.toList()
    val scored = entries.map { entry -> entry to mediaTypeScore(entry.key) }
    val comparator =
        compareBy<Pair<Map.Entry<String, MediaTypeObject>, MediaTypeScore>>(
                { it.second.specificity }, { it.second.jsonPreference }, { it.second.length })
            .thenByDescending { it.second.key }
    return scored.maxWithOrNull(comparator)!!.first
  }

  private fun renderEncodingContentTypeLiteral(mediaType: MediaTypeObject?): String? {
    if (mediaType == null || mediaType.encoding.isEmpty()) return null
    val entries =
        mediaType.encoding.mapNotNull { (name, encoding) ->
          encoding.contentType?.let { name to it }
        }
    if (entries.isEmpty()) return null
    val joined =
        entries.joinToString(", ") { (name, type) ->
          "\"${ServerUrlResolverGenerator.escapeKotlinString(name)}\" to \"${ServerUrlResolverGenerator.escapeKotlinString(type)}\""
        }
    return "mapOf($joined)"
  }

  private fun renderEncodingStyleLiteral(mediaType: MediaTypeObject?): String? {
    if (mediaType == null || mediaType.encoding.isEmpty()) return null
    val entries =
        mediaType.encoding.mapNotNull { (name, encoding) ->
          encoding.style?.let { name to parameterStyleValue(it) }
        }
    if (entries.isEmpty()) return null
    val joined =
        entries.joinToString(", ") { (name, style) ->
          "\"${ServerUrlResolverGenerator.escapeKotlinString(name)}\" to \"${ServerUrlResolverGenerator.escapeKotlinString(style)}\""
        }
    return "mapOf($joined)"
  }

  private fun renderEncodingExplodeLiteral(mediaType: MediaTypeObject?): String? {
    if (mediaType == null || mediaType.encoding.isEmpty()) return null
    val entries =
        mediaType.encoding.mapNotNull { (name, encoding) -> encoding.explode?.let { name to it } }
    if (entries.isEmpty()) return null
    val joined =
        entries.joinToString(", ") { (name, explode) ->
          "\"${ServerUrlResolverGenerator.escapeKotlinString(name)}\" to $explode"
        }
    return "mapOf($joined)"
  }

  private fun renderEncodingAllowReservedLiteral(mediaType: MediaTypeObject?): String? {
    if (mediaType == null || mediaType.encoding.isEmpty()) return null
    val entries =
        mediaType.encoding.mapNotNull { (name, encoding) ->
          encoding.allowReserved?.let { name to it }
        }
    if (entries.isEmpty()) return null
    val joined =
        entries.joinToString(", ") { (name, allowReserved) ->
          "\"${ServerUrlResolverGenerator.escapeKotlinString(name)}\" to $allowReserved"
        }
    return "mapOf($joined)"
  }

  private fun renderEncodingHeadersLiteral(mediaType: MediaTypeObject?): String? {
    if (mediaType == null || mediaType.encoding.isEmpty()) return null
    val entries =
        mediaType.encoding.mapNotNull { (name, encoding) ->
          val headerValues = extractEncodingHeaderValues(encoding)
          if (headerValues.isEmpty()) null else name to headerValues
        }
    if (entries.isEmpty()) return null
    val joined =
        entries.joinToString(", ") { (name, headers) ->
          val headerLiteral =
              headers.entries.joinToString(", ") { (headerName, value) ->
                "\"${ServerUrlResolverGenerator.escapeKotlinString(headerName)}\" to \"${ServerUrlResolverGenerator.escapeKotlinString(value)}\""
              }
          "\"${ServerUrlResolverGenerator.escapeKotlinString(name)}\" to mapOf($headerLiteral)"
        }
    return "mapOf($joined)"
  }

  private fun renderPrefixEncodingContentTypesLiteral(mediaType: MediaTypeObject?): String? {
    if (mediaType == null || mediaType.prefixEncoding.isEmpty()) return null
    val joined =
        mediaType.prefixEncoding.joinToString(", ") { encoding ->
          val ct = encoding.contentType
          if (ct != null) "\"${ServerUrlResolverGenerator.escapeKotlinString(ct)}\"" else "null"
        }
    return "listOf($joined)"
  }

  private fun renderPrefixEncodingHeadersLiteral(mediaType: MediaTypeObject?): String? {
    val prefixList = mediaType?.prefixEncoding.orEmpty()
    if (prefixList.isEmpty()) return null
    val entries =
        prefixList.map { encoding ->
          val headerValues = extractEncodingHeaderValues(encoding)
          if (headerValues.isEmpty()) {
            "emptyMap()"
          } else {
            val headerLiteral =
                headerValues.entries.joinToString(", ") { (name, value) ->
                  "\"${ServerUrlResolverGenerator.escapeKotlinString(name)}\" to \"${ServerUrlResolverGenerator.escapeKotlinString(value)}\""
                }
            "mapOf($headerLiteral)"
          }
        }
    val hasAnyHeaders = entries.any { it != "emptyMap()" }
    if (!hasAnyHeaders) return null
    return "listOf(${entries.joinToString(", ")})"
  }

  private fun renderItemEncodingContentTypeLiteral(encoding: EncodingObject?): String? {
    val contentType = encoding?.contentType ?: return null
    return "\"${ServerUrlResolverGenerator.escapeKotlinString(contentType)}\""
  }

  private fun renderItemEncodingHeadersLiteral(encoding: EncodingObject?): String? {
    if (encoding == null) return null
    val headerValues = extractEncodingHeaderValues(encoding)
    if (headerValues.isEmpty()) return null
    val headerLiteral =
        headerValues.entries.joinToString(", ") { (name, value) ->
          "\"${ServerUrlResolverGenerator.escapeKotlinString(name)}\" to \"${ServerUrlResolverGenerator.escapeKotlinString(value)}\""
        }
    return "mapOf($headerLiteral)"
  }

  private fun extractEncodingHeaderValues(encoding: EncodingObject): Map<String, String> {
    if (encoding.headers.isEmpty()) return emptyMap()
    val values = LinkedHashMap<String, String>()
    encoding.headers.forEach { (headerName, header) ->
      if (headerName.equals("Content-Type", ignoreCase = true)) return@forEach
      val value =
          extractHeaderExampleValue(header) ?: extractHeaderDefaultValue(header) ?: return@forEach
      values[headerName] = value
    }
    return values
  }

  private fun extractHeaderExampleValue(header: Header): String? {
    header.example?.let { example ->
      extractExampleString(example)?.let {
        return it
      }
    }
    header.examples.values.forEach { example ->
      extractExampleString(example)?.let {
        return it
      }
    }
    return null
  }

  private fun extractExampleString(example: ExampleObject): String? {
    return listOfNotNull(
            example.serializedValue, example.dataValue.stringOrNull(), example.value.stringOrNull())
        .firstOrNull()
  }

  private fun extractHeaderDefaultValue(header: Header): String? {
    val schema = header.schema ?: return null
    val default = schema.defaultValue.stringOrNull()
    if (default != null) return default
    return schema.enumValues.orEmpty().firstNotNullOfOrNull { it.stringOrNull() }
  }

  private fun normalizeMediaTypeKey(value: String): String {
    return value.substringBefore(";").trim().lowercase()
  }

  /**
   * Checks whether [value] is `application/x-www-form-urlencoded`.
   *
   * @param value Media type string.
   * @return `true` if form-urlencoded.
   */
  fun isFormUrlEncodedMediaType(value: String): Boolean {
    return normalizeMediaTypeKey(value) == "application/x-www-form-urlencoded"
  }

  /**
   * Checks whether [value] is `multipart/form-data`.
   *
   * @param value Media type string.
   * @return `true` if `multipart/form-data`.
   */
  fun isMultipartFormDataMediaType(value: String): Boolean {
    return normalizeMediaTypeKey(value) == "multipart/form-data"
  }

  private fun isMultipartMediaType(value: String): Boolean {
    return normalizeMediaTypeKey(value).startsWith("multipart/")
  }

  private fun isConcreteMediaType(value: String): Boolean {
    val main = value.trim().substringBefore(";").trim()
    val parts = main.split("/")
    return parts.size == 2 && !main.contains("*")
  }

  private fun mediaTypeScore(raw: String): MediaTypeScore {
    val main = raw.trim().substringBefore(";").trim().lowercase()
    val parts = main.split("/")
    if (parts.size != 2) {
      return MediaTypeScore(specificity = -1, jsonPreference = 0, length = main.length, key = main)
    }
    val type = parts[0]
    val subtype = parts[1]
    val typeScore = if (type == "*") 0 else 1
    val subtypeScore =
        when {
          subtype == "*" -> 0
          subtype.startsWith("*+") -> 1
          else -> 2
        }
    val specificity = typeScore * 10 + subtypeScore
    val jsonPreference = if (subtype == "json" || subtype.endsWith("+json")) 1 else 0
    return MediaTypeScore(
        specificity = specificity,
        jsonPreference = jsonPreference,
        length = main.length,
        key = main)
  }

  private data class MediaTypeScore(
      val specificity: Int,
      val jsonPreference: Int,
      val length: Int,
      val key: String
  )

  // Helper to detect Lists
}
