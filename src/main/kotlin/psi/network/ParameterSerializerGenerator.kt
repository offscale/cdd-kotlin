/**
 * Generates parameter serialization expressions for Matrix, Label, Form, Simple, SpaceDelimited,
 * PipeDelimited, Cookie, and DeepObject styles, plus companion encoding helpers.
 */
package psi.network

import domain.EndpointDefinition
import domain.EndpointParameter
import domain.ParameterLocation
import domain.ParameterStyle

/**
 * Generated query parameter lines and optional empty-value fallback line.
 *
 * @property lines Generated Ktor query parameter statements.
 * @property emptyValueLine Optional fallback line when `allowEmptyValue = true` and argument is
 *   `null`.
 */
data class QueryParamLines(val lines: List<String>, val emptyValueLine: String?)

/** Generates Ktor parameter serialization statements and companion encoding helper functions. */
object ParameterSerializerGenerator {
  /**
   * Builds companion object helper functions required by [endpoints].
   *
   * @param endpoints Endpoint definitions.
   * @return Companion object code or empty string.
   */
  fun buildHelperFunctions(endpoints: List<EndpointDefinition>): String {
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
          EndpointMethodGenerator.resolveRequestContentType(ep)?.let {
            EndpointMethodGenerator.isFormUrlEncodedMediaType(it)
          } == true
        }
    val needsMultipartBodyEncoding =
        endpoints.any { ep ->
          EndpointMethodGenerator.resolveRequestContentType(ep)?.let {
            EndpointMethodGenerator.isMultipartFormDataMediaType(it)
          } == true
        }
    val needsMultipartPositionalEncoding =
        endpoints.any { ep -> EndpointMethodGenerator.requiresMultipartPositionalEncoding(ep) }
    val needsMultipartEncoding = needsMultipartBodyEncoding || needsMultipartPositionalEncoding
    val needsSequentialJsonEncoding =
        endpoints.any { ep -> EndpointMethodGenerator.requiresSequentialJsonRequest(ep) }
    val needsSequentialJsonDecoding =
        endpoints.any { ep -> EndpointMethodGenerator.requiresSequentialJsonResponse(ep) }
    val needsPathEncoding =
        endpoints.any { ep -> ep.parameters.any { it.location == ParameterLocation.PATH } }
    val needsAllowReserved =
        endpoints.any { ep ->
          ep.parameters.any { it.location == ParameterLocation.QUERY && it.allowReserved == true }
        } || needsFormBodyEncoding
    val needsEncodingPrimitives = needsAllowReserved || needsPathEncoding
    val needsContentEncodingHelpers = needsQueryStringEncoding || needsParameterContentSerialization
    val needsJsonElementHelpers =
        needsContentEncodingHelpers || needsFormBodyEncoding || needsMultipartEncoding
    val needsJsonContentHelper = needsFormBodyEncoding || needsMultipartEncoding

    val blocks = mutableListOf<String>()

    if (needsEncodingPrimitives) {
      blocks +=
          """
                private fun isUnreserved(ch: Char): Boolean {
                    return ch.isLetterOrDigit() || ch == '-' || ch == '.' || ch == '_' || ch == '~'
                }

                private fun isHexDigit(ch: Char): Boolean {
                    return ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'
                }

                private fun byteToHex(b: Byte): String {
                    val value = b.toInt() and 0xFF
                    val digits = "0123456789ABCDEF"
                    return "${'$'}{digits[value ushr 4]}${'$'}{digits[value and 0x0F]}"
                }
            """
              .trimIndent()
    }

    if (needsAllowReserved) {
      blocks +=
          """
                private fun encodeAllowReserved(value: String): String {
                    if (value.isEmpty()) return value
                    val sb = StringBuilder()
                    var i = 0
                    while (i < value.length) {
                        val ch = value[i]
                        if (ch == '%' && i + 2 < value.length && isHexDigit(value[i + 1]) && isHexDigit(value[i + 2])) {
                            sb.append(ch).append(value[i + 1]).append(value[i + 2])
                            i += 3
                            continue
                        }
                        if (isUnreserved(ch) || isReserved(ch)) {
                            sb.append(ch)
                            i += 1
                            continue
                        }
                        val bytes = ch.toString().encodeToByteArray()
                        for (b in bytes) {
                            sb.append('%')
                            sb.append(byteToHex(b))
                        }
                        i += 1
                    }
                    return sb.toString()
                }

                private fun isReserved(ch: Char): Boolean {
                    return ":/?#[]@!${'$'}&'()*+,;=".indexOf(ch) >= 0
                }
            """
              .trimIndent()
    }

    if (needsPathEncoding) {
      blocks +=
          """
                private fun encodePathComponent(value: String, allowReserved: Boolean): String {
                    if (value.isEmpty()) return value
                    val sb = StringBuilder()
                    var i = 0
                    while (i < value.length) {
                        val ch = value[i]
                        if (ch == '%' && i + 2 < value.length && isHexDigit(value[i + 1]) && isHexDigit(value[i + 2])) {
                            sb.append(ch).append(value[i + 1]).append(value[i + 2])
                            i += 3
                            continue
                        }
                        val allowed = isUnreserved(ch) || (allowReserved && isPathReservedAllowed(ch))
                        if (allowed) {
                            sb.append(ch)
                            i += 1
                            continue
                        }
                        val bytes = ch.toString().encodeToByteArray()
                        for (b in bytes) {
                            sb.append('%')
                            sb.append(byteToHex(b))
                        }
                        i += 1
                    }
                    return sb.toString()
                }

                private fun isPathReservedAllowed(ch: Char): Boolean {
                    return ":@!${'$'}&'()*+,;=".indexOf(ch) >= 0
                }
            """
              .trimIndent()
    }

    if (needsQueryStringEncoding) {
      blocks +=
          """
                private inline fun <reified T> encodeQueryStringContent(value: T, contentType: String): String {
                    val normalized = contentType.substringBefore(";").trim().lowercase()
                    return when {
                        normalized == "application/x-www-form-urlencoded" -> encodeFormUrlEncoded(value)
                        normalized == "application/json" || normalized.endsWith("+json") -> encodeJsonQuery(value)
                        else -> value.toString().encodeURLQueryComponent()
                    }
                }

                private inline fun <reified T> encodeJsonQuery(value: T): String {
                    val json = Json.encodeToString(value)
                    return json.encodeURLQueryComponent()
                }
            """
              .trimIndent()
    }

    if (needsContentEncodingHelpers) {
      blocks +=
          """
                private inline fun <reified T> encodeJsonString(value: T): String {
                    return Json.encodeToString(value)
                }

                private inline fun <reified T> encodeFormUrlEncoded(value: T): String {
                    val element = Json.encodeToJsonElement(value)
                    if (element is JsonObject) {
                        val params = Parameters.build {
                            element.forEach { (key, elementValue) ->
                                when (elementValue) {
                                    is JsonArray -> elementValue.forEach { item -> append(key, jsonElementToString(item)) }
                                    else -> append(key, jsonElementToString(elementValue))
                                }
                            }
                        }
                        return params.formUrlEncode()
                    }
                    return jsonElementToString(element).encodeURLQueryComponent()
                }
            """
              .trimIndent()
    }

    if (needsSequentialJsonEncoding) {
      blocks +=
          """
                private inline fun <reified T> encodeSequentialJson(items: Iterable<T>, contentType: String): String {
                    val normalized = contentType.substringBefore(";").trim().lowercase()
                    if (!items.iterator().hasNext()) return ""
                    return if (normalized.endsWith("json-seq")) {
                        buildString {
                            items.forEach { item ->
                                append('\u001E')
                                append(Json.encodeToString(item))
                                append('\n')
                            }
                        }
                    } else {
                        items.joinToString("\n") { Json.encodeToString(it) }
                    }
                }
            """
              .trimIndent()
    }

    if (needsSequentialJsonDecoding) {
      blocks +=
          """
                private inline fun <reified T> decodeSequentialJsonList(payload: String, contentType: String): List<T> {
                    val normalized = contentType.substringBefore(";").trim().lowercase()
                    return if (normalized.endsWith("json-seq")) {
                        payload.split('\u001E')
                            .asSequence()
                            .map { it.trimStart('\n', '\r') }
                            .filter { it.isNotBlank() }
                            .map { Json.decodeFromString<T>(it) }
                            .toList()
                    } else {
                        payload.lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .map { Json.decodeFromString<T>(it) }
                            .toList()
                    }
                }
            """
              .trimIndent()
    }

    if (needsParameterContentSerialization) {
      blocks +=
          """
                private inline fun <reified T> serializeContentValue(value: T, contentType: String): String {
                    val normalized = contentType.substringBefore(";").trim().lowercase()
                    return when {
                        normalized == "application/x-www-form-urlencoded" -> encodeFormUrlEncoded(value)
                        normalized == "application/json" || normalized.endsWith("+json") -> encodeJsonString(value)
                        else -> value.toString()
                    }
                }
            """
              .trimIndent()
    }

    if (needsFormBodyEncoding) {
      blocks +=
          """
                private data class FormPair(val key: String, val value: String, val allowReserved: Boolean)

                private inline fun <reified T> encodeFormBody(
                    value: T,
                    encoding: Map<String, String> = emptyMap(),
                    styles: Map<String, String> = emptyMap(),
                    explode: Map<String, Boolean> = emptyMap(),
                    allowReserved: Map<String, Boolean> = emptyMap()
                ): OutgoingContent {
                    val element = Json.encodeToJsonElement(value)
                    val pairs = mutableListOf<FormPair>()
                    when (element) {
                        is JsonObject -> {
                            element.forEach { (key, elementValue) ->
                                appendFormPairs(
                                    pairs,
                                    key,
                                    elementValue,
                                    encoding[key],
                                    styles[key],
                                    explode[key],
                                    allowReserved[key] == true
                                )
                            }
                        }
                        else -> appendFormPairs(
                            pairs,
                            "value",
                            element,
                            encoding["value"],
                            styles["value"],
                            explode["value"],
                            allowReserved["value"] == true
                        )
                    }
                    val hasOverrides = styles.isNotEmpty() || explode.isNotEmpty() || allowReserved.isNotEmpty()
                    return if (!hasOverrides) {
                        val params = Parameters.build {
                            pairs.forEach { pair -> append(pair.key, pair.value) }
                        }
                        FormDataContent(params)
                    } else {
                        val encoded = pairs.joinToString("&") { pair ->
                            encodeFormComponent(pair.key, pair.allowReserved) + "=" +
                                encodeFormComponent(pair.value, pair.allowReserved)
                        }
                        TextContent(encoded, ContentType.Application.FormUrlEncoded)
                    }
                }

                private fun appendFormPairs(
                    pairs: MutableList<FormPair>,
                    key: String,
                    element: JsonElement,
                    contentType: String?,
                    styleOverride: String?,
                    explodeOverride: Boolean?,
                    allowReserved: Boolean
                ) {
                    val style = normalizeFormStyle(styleOverride)
                    val explode = explodeOverride ?: (style == "form")
                    when (element) {
                        is JsonArray -> {
                            when (style) {
                                "form" -> {
                                    if (explode) {
                                        element.forEach { item ->
                                            pairs.add(FormPair(key, serializeElement(item, contentType), allowReserved))
                                        }
                                    } else {
                                        val joined = element.joinToString(",") { item ->
                                            serializeElement(item, contentType)
                                        }
                                        pairs.add(FormPair(key, joined, allowReserved))
                                    }
                                }
                                "spacedelimited" -> {
                                    if (!explode) {
                                        val joined = element.joinToString(" ") { item ->
                                            serializeElement(item, contentType)
                                        }
                                        pairs.add(FormPair(key, joined, allowReserved))
                                    }
                                }
                                "pipedelimited" -> {
                                    if (!explode) {
                                        val joined = element.joinToString("|") { item ->
                                            serializeElement(item, contentType)
                                        }
                                        pairs.add(FormPair(key, joined, allowReserved))
                                    }
                                }
                                "deepobject" -> Unit
                                else -> pairs.add(FormPair(key, serializeElement(element, contentType), allowReserved))
                            }
                        }
                        is JsonObject -> {
                            when (style) {
                                "form" -> {
                                    if (explode) {
                                        element.forEach { (propName, propValue) ->
                                            pairs.add(
                                                FormPair(
                                                    propName,
                                                    serializeElement(propValue, contentType),
                                                    allowReserved
                                                )
                                            )
                                        }
                                    } else {
                                        val joined = element.entries.joinToString(",") { entry ->
                                            entry.key + "," + serializeElement(entry.value, contentType)
                                        }
                                        pairs.add(FormPair(key, joined, allowReserved))
                                    }
                                }
                                "deepobject" -> {
                                    element.forEach { (propName, propValue) ->
                                        pairs.add(
                                            FormPair(
                                                "${'$'}key[${'$'}propName]",
                                                serializeElement(propValue, contentType),
                                                allowReserved
                                            )
                                        )
                                    }
                                }
                                "spacedelimited" -> {
                                    if (!explode) {
                                        val joined = element.entries
                                            .flatMap { entry -> listOf(entry.key, serializeElement(entry.value, contentType)) }
                                            .joinToString(" ")
                                        pairs.add(FormPair(key, joined, allowReserved))
                                    }
                                }
                                "pipedelimited" -> {
                                    if (!explode) {
                                        val joined = element.entries
                                            .flatMap { entry -> listOf(entry.key, serializeElement(entry.value, contentType)) }
                                            .joinToString("|")
                                        pairs.add(FormPair(key, joined, allowReserved))
                                    }
                                }
                                else -> pairs.add(FormPair(key, serializeElement(element, contentType), allowReserved))
                            }
                        }
                        else -> pairs.add(FormPair(key, serializeElement(element, contentType), allowReserved))
                    }
                }

                private fun normalizeFormStyle(style: String?): String {
                    return style?.trim()?.lowercase() ?: "form"
                }

                private fun encodeFormComponent(value: String, allowReserved: Boolean): String {
                    val encoded = if (allowReserved) encodeAllowReserved(value) else value.encodeURLQueryComponent()
                    return encoded.replace("%20", "+")
                }
            """
              .trimIndent()
    }

    if (needsMultipartEncoding) {
      blocks +=
          """
                private inline fun <reified T> encodeMultipartBody(
                    value: T,
                    encoding: Map<String, String> = emptyMap(),
                    headers: Map<String, Map<String, String>> = emptyMap()
                ): MultiPartFormDataContent {
                    val element = Json.encodeToJsonElement(value)
                    val data = formData {
                        when (element) {
                            is JsonObject -> {
                                element.forEach { (key, elementValue) ->
                                    appendMultipartValue(key, elementValue, encoding[key], headers[key])
                                }
                            }
                            else -> appendMultipartValue("value", element, encoding["value"], headers["value"])
                        }
                    }
                    return MultiPartFormDataContent(data)
                }

                private fun FormBuilder.appendMultipartValue(
                    key: String,
                    element: JsonElement,
                    contentType: String?,
                    headerOverrides: Map<String, String>?
                ) {
                    when (element) {
                        is JsonArray -> element.forEach { appendMultipartValue(key, it, contentType, headerOverrides) }
                        else -> {
                            val value = serializeElement(element, contentType)
                            val headers = buildMultipartHeaders(contentType, headerOverrides)
                            if (headers != null) {
                                append(key, value, headers)
                            } else {
                                append(key, value)
                            }
                        }
                    }
                }
            """
              .trimIndent()
    }

    if (needsMultipartPositionalEncoding) {
      blocks +=
          """
                private inline fun <reified T> encodeMultipartPositional(
                    value: T,
                    prefixContentTypes: List<String?> = emptyList(),
                    prefixHeaders: List<Map<String, String>> = emptyList(),
                    itemContentType: String? = null,
                    itemHeaders: Map<String, String> = emptyMap()
                ): MultiPartFormDataContent {
                    val element = Json.encodeToJsonElement(value)
                    val items = when (element) {
                        is JsonArray -> element
                        else -> JsonArray(listOf(element))
                    }
                    val data = formData {
                        items.forEachIndexed { index, item ->
                            val isPrefix = index < prefixContentTypes.size
                            val contentType = if (isPrefix) prefixContentTypes[index] else itemContentType
                            val headerOverrides = if (isPrefix) {
                                prefixHeaders.getOrNull(index) ?: emptyMap()
                            } else {
                                itemHeaders
                            }
                            val headers = if (headerOverrides.isEmpty()) null else headerOverrides
                            appendMultipartValue("part${'$'}index", item, contentType, headers)
                        }
                    }
                    return MultiPartFormDataContent(data)
                }
            """
              .trimIndent()
    }

    if (needsMultipartEncoding) {
      blocks +=
          """
                private fun buildMultipartHeaders(
                    contentType: String?,
                    overrides: Map<String, String>?
                ): Headers? {
                    if (contentType == null && overrides.isNullOrEmpty()) return null
                    return Headers.build {
                        if (!contentType.isNullOrBlank()) {
                            append(HttpHeaders.ContentType, contentType)
                        }
                        overrides?.forEach { (name, value) ->
                            if (!name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                                append(name, value)
                            }
                        }
                    }
                }
            """
              .trimIndent()
    }

    if (needsJsonContentHelper) {
      blocks +=
          """
                private fun serializeElement(element: JsonElement, contentType: String?): String {
                    return if (isJsonMediaType(contentType)) element.toString() else jsonElementToString(element)
                }

                private fun isJsonMediaType(contentType: String?): Boolean {
                    if (contentType == null) return false
                    val normalized = contentType.substringBefore(";").trim().lowercase()
                    return normalized == "application/json" || normalized.endsWith("+json")
                }
            """
              .trimIndent()
    }

    if (needsJsonElementHelpers) {
      blocks +=
          """
                private fun jsonElementToString(element: JsonElement): String {
                    return when (element) {
                        is JsonPrimitive -> element.content
                        else -> element.toString()
                    }
                }
            """
              .trimIndent()
    }

    if (blocks.isEmpty()) return ""

    val body = blocks.joinToString("\n\n").prependIndent("        ")
    return "    companion object {\n$body\n    }"
  }

  private fun isListType(type: String): Boolean {
    val clean = type.replace("?", "")
    return clean.startsWith("List<") || clean.contains("Array")
  }

  private fun isMapType(type: String): Boolean {
    val clean = type.replace("?", "")
    return clean.startsWith("Map<") || clean.startsWith("MutableMap<")
  }

  /**
   * Builds query parameter statements for [param] wrapped in a [Result].
   *
   * @param param Endpoint parameter.
   * @param paramType Resolved Kotlin type.
   * @return A [Result] containing [QueryParamLines] or a
   *   [domain.CddError.ParameterSerializationError].
   */
  fun buildQueryParamLines(param: EndpointParameter, paramType: String): Result<QueryParamLines> {
    if (param.content.isNotEmpty()) {
      if (param.style != null || param.explode != null || param.allowReserved != null) {
        return domain.CddError.ParameterSerializationError(
                param.name,
                "OAS 3.2: query parameters with content must not set style/explode/allowReserved for ${param.name}")
            .let { Result.failure(domain.CddException(it)) }
      }
      val contentType = EndpointMethodGenerator.resolveParameterContentType(param)
      val serialized = "serializeContentValue(${param.safeName}, \"$contentType\")"
      val emptyValueLine =
          if (param.allowEmptyValue == true) {
            "parameter(\"${param.name}\", \"\")"
          } else {
            null
          }
      return Result.success(
          QueryParamLines(listOf("parameter(\"${param.name}\", $serialized)"), emptyValueLine))
    }

    val style = param.style ?: ParameterStyle.FORM
    val explode =
        if (param.explode != null) {
          param.explode
        } else {
          style == ParameterStyle.FORM
        }
    val allowReserved = param.allowReserved == true

    val isList = isListType(paramType)
    val isMap = isMapType(paramType)

    val emptyValueLine =
        if (param.allowEmptyValue == true) {
          if (allowReserved) {
            "url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(\"\"))"
          } else {
            "parameter(\"${param.name}\", \"\")"
          }
        } else {
          null
        }

    val lines =
        if (isList) {
          buildQueryArrayLines(param, style, explode, allowReserved).getOrElse {
            return Result.failure(it)
          }
        } else if (isMap) {
          buildQueryObjectLines(param, style, explode, allowReserved).getOrElse {
            return Result.failure(it)
          }
        } else if (allowReserved) {
          listOf(
              "url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(${param.safeName}.toString()))")
        } else {
          listOf("parameter(\"${param.name}\", ${param.safeName})")
        }

    return Result.success(QueryParamLines(lines, emptyValueLine))
  }

  private fun buildQueryArrayLines(
      param: EndpointParameter,
      style: ParameterStyle,
      explode: Boolean,
      allowReserved: Boolean
  ): Result<List<String>> {
    if (allowReserved) {
      return when (style) {
        ParameterStyle.FORM -> {
          if (explode) {
            Result.success(
                listOf(
                    "${param.safeName}.forEach { value -> url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(value.toString())) }"))
          } else {
            Result.success(
                listOf(
                    "url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(${param.safeName}.joinToString(\",\")))"))
          }
        }
        ParameterStyle.SPACE_DELIMITED -> {
          if (explode) {
            return domain.CddError.ParameterSerializationError(
                    param.name,
                    "OAS 3.2: spaceDelimited does not support explode=true for ${param.name}")
                .let { Result.failure(domain.CddException(it)) }
          }
          Result.success(
              listOf(
                  "url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(${param.safeName}.joinToString(\" \")))"))
        }
        ParameterStyle.PIPE_DELIMITED -> {
          if (explode) {
            return domain.CddError.ParameterSerializationError(
                    param.name,
                    "OAS 3.2: pipeDelimited does not support explode=true for ${param.name}")
                .let { Result.failure(domain.CddException(it)) }
          }
          Result.success(
              listOf(
                  "url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(${param.safeName}.joinToString(\"|\")))"))
        }
        ParameterStyle.DEEP_OBJECT -> {
          Result.success(
              listOf(
                  "${param.safeName}.forEach { value -> url.encodedParameters.append(\"${param.name}[]\", encodeAllowReserved(value.toString())) }"))
        }
        else ->
            Result.success(
                listOf(
                    "url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(${param.safeName}.toString()))"))
      }
    }

    return when (style) {
      ParameterStyle.FORM -> {
        if (explode) {
          Result.success(
              listOf("${param.safeName}.forEach { value -> parameter(\"${param.name}\", value) }"))
        } else {
          Result.success(
              listOf("parameter(\"${param.name}\", ${param.safeName}.joinToString(\",\"))"))
        }
      }
      ParameterStyle.SPACE_DELIMITED -> {
        if (explode) {
          return domain.CddError.ParameterSerializationError(
                  param.name,
                  "OAS 3.2: spaceDelimited does not support explode=true for ${param.name}")
              .let { Result.failure(domain.CddException(it)) }
        }
        Result.success(
            listOf("parameter(\"${param.name}\", ${param.safeName}.joinToString(\" \"))"))
      }
      ParameterStyle.PIPE_DELIMITED -> {
        if (explode) {
          return domain.CddError.ParameterSerializationError(
                  param.name,
                  "OAS 3.2: pipeDelimited does not support explode=true for ${param.name}")
              .let { Result.failure(domain.CddException(it)) }
        }
        Result.success(
            listOf("parameter(\"${param.name}\", ${param.safeName}.joinToString(\"|\"))"))
      }
      ParameterStyle.DEEP_OBJECT -> {
        Result.success(
            listOf("${param.safeName}.forEach { value -> parameter(\"${param.name}[]\", value) }"))
      }
      else -> Result.success(listOf("parameter(\"${param.name}\", ${param.safeName})"))
    }
  }

  private fun buildQueryObjectLines(
      param: EndpointParameter,
      style: ParameterStyle,
      explode: Boolean,
      allowReserved: Boolean
  ): Result<List<String>> {
    if (allowReserved) {
      return when (style) {
        ParameterStyle.FORM -> {
          if (explode) {
            Result.success(
                listOf(
                    "${param.safeName}.forEach { (key, value) -> url.encodedParameters.append(encodeAllowReserved(key.toString()), encodeAllowReserved(value.toString())) }"))
          } else {
            val joinExpr =
                "${param.safeName}.entries.joinToString(\",\") { \"${'$'}{it.key},${'$'}{it.value}\" }"
            Result.success(
                listOf(
                    "url.encodedParameters.append(\"${param.name}\", encodeAllowReserved($joinExpr))"))
          }
        }
        ParameterStyle.DEEP_OBJECT -> {
          Result.success(
              listOf(
                  "${param.safeName}.forEach { (key, value) -> url.encodedParameters.append(encodeAllowReserved(\"${param.name}[${'$'}key]\"), encodeAllowReserved(value.toString())) }"))
        }
        ParameterStyle.SPACE_DELIMITED -> {
          if (explode) {
            return domain.CddError.ParameterSerializationError(
                    param.name,
                    "OAS 3.2: spaceDelimited does not support explode=true for ${param.name}")
                .let { Result.failure(domain.CddException(it)) }
          }
          val joinExpr =
              "${param.safeName}.entries.flatMap { listOf(it.key, it.value) }.joinToString(\" \")"
          Result.success(
              listOf(
                  "url.encodedParameters.append(\"${param.name}\", encodeAllowReserved($joinExpr))"))
        }
        ParameterStyle.PIPE_DELIMITED -> {
          if (explode) {
            return domain.CddError.ParameterSerializationError(
                    param.name,
                    "OAS 3.2: pipeDelimited does not support explode=true for ${param.name}")
                .let { Result.failure(domain.CddException(it)) }
          }
          val joinExpr =
              "${param.safeName}.entries.flatMap { listOf(it.key, it.value) }.joinToString(\"|\")"
          Result.success(
              listOf(
                  "url.encodedParameters.append(\"${param.name}\", encodeAllowReserved($joinExpr))"))
        }
        else ->
            Result.success(
                listOf(
                    "url.encodedParameters.append(\"${param.name}\", encodeAllowReserved(${param.safeName}.toString()))"))
      }
    }

    return when (style) {
      ParameterStyle.FORM -> {
        if (explode) {
          Result.success(
              listOf("${param.safeName}.forEach { (key, value) -> parameter(key, value) }"))
        } else {
          val joinExpr =
              "${param.safeName}.entries.joinToString(\",\") { \"${'$'}{it.key},${'$'}{it.value}\" }"
          Result.success(listOf("parameter(\"${param.name}\", $joinExpr)"))
        }
      }
      ParameterStyle.DEEP_OBJECT -> {
        Result.success(
            listOf(
                "${param.safeName}.forEach { (key, value) -> parameter(\"${param.name}[${'$'}key]\", value) }"))
      }
      ParameterStyle.SPACE_DELIMITED -> {
        if (explode) {
          return domain.CddError.ParameterSerializationError(
                  param.name,
                  "OAS 3.2: spaceDelimited does not support explode=true for ${param.name}")
              .let { Result.failure(domain.CddException(it)) }
        }
        val joinExpr =
            "${param.safeName}.entries.flatMap { listOf(it.key, it.value) }.joinToString(\" \")"
        Result.success(listOf("parameter(\"${param.name}\", $joinExpr)"))
      }
      ParameterStyle.PIPE_DELIMITED -> {
        if (explode) {
          return domain.CddError.ParameterSerializationError(
                  param.name,
                  "OAS 3.2: pipeDelimited does not support explode=true for ${param.name}")
              .let { Result.failure(domain.CddException(it)) }
        }
        val joinExpr =
            "${param.safeName}.entries.flatMap { listOf(it.key, it.value) }.joinToString(\"|\")"
        Result.success(listOf("parameter(\"${param.name}\", $joinExpr)"))
      }
      else -> Result.success(listOf("parameter(\"${param.name}\", ${param.safeName})"))
    }
  }

  /**
   * Wraps parameter lines in a null check if [optional] is true.
   *
   * @param optional Whether optional.
   * @param paramName Parameter identifier.
   * @param lines Statement lines.
   * @param emptyValueLine Optional empty fallback.
   * @return Wrapped lines.
   */
  fun wrapOptional(
      optional: Boolean,
      paramName: String,
      lines: List<String>,
      emptyValueLine: String? = null
  ): List<String> {
    if (!optional) return lines
    val inner = lines.joinToString("\n                    ")
    return if (emptyValueLine != null) {
      listOf(
          "if ($paramName != null) {\n                    $inner\n            } else {\n                    $emptyValueLine\n            }")
    } else {
      listOf("if ($paramName != null) {\n                    $inner\n            }")
    }
  }

  /**
   * Builds header parameter statements for [param] wrapped in a [Result].
   *
   * @param param Endpoint parameter.
   * @param paramType Resolved Kotlin type.
   * @return A [Result] containing statement lines or a
   *   [domain.CddError.ParameterSerializationError].
   */
  fun buildHeaderParamLines(param: EndpointParameter, paramType: String): Result<List<String>> {
    if (param.content.isNotEmpty()) {
      if (param.style != null || param.explode != null || param.allowReserved != null) {
        return domain.CddError.ParameterSerializationError(
                param.name,
                "OAS 3.2: header parameters with content must not set style/explode/allowReserved for ${param.name}")
            .let { Result.failure(domain.CddException(it)) }
      }
      val contentType = EndpointMethodGenerator.resolveParameterContentType(param)
      val serialized = "serializeContentValue(${param.safeName}, \"$contentType\")"
      return Result.success(listOf("header(\"${param.name}\", $serialized)"))
    }

    val explode = param.explode == true
    val cleanType = paramType.replace(" ", "")
    val isList = isListType(cleanType)
    val isMap = isMapType(cleanType)

    val valueExpr =
        if (isList) {
          buildArrayJoinExpr(param.safeName, ",")
        } else if (isMap) {
          val keyValueDelimiter = if (explode) "=" else ","
          buildObjectJoinExpr(param.safeName, ",", keyValueDelimiter)
        } else {
          param.safeName
        }

    // Headers only allow "simple" style in OAS, treat all as simple.
    return Result.success(listOf("header(\"${param.name}\", $valueExpr)"))
  }

  /**
   * Builds cookie parameter statements for [param] wrapped in a [Result].
   *
   * @param param Endpoint parameter.
   * @param paramType Resolved Kotlin type.
   * @return A [Result] containing statement lines or a
   *   [domain.CddError.ParameterSerializationError].
   */
  fun buildCookieParamLines(param: EndpointParameter, paramType: String): Result<List<String>> {
    if (param.content.isNotEmpty()) {
      if (param.style != null || param.explode != null || param.allowReserved != null) {
        return domain.CddError.ParameterSerializationError(
                param.name,
                "OAS 3.2: cookie parameters with content must not set style/explode/allowReserved for ${param.name}")
            .let { Result.failure(domain.CddException(it)) }
      }
      val contentType = EndpointMethodGenerator.resolveParameterContentType(param)
      val serialized = "serializeContentValue(${param.safeName}, \"$contentType\")"
      return Result.success(listOf("cookie(\"${param.name}\", $serialized)"))
    }

    val style = param.style ?: ParameterStyle.FORM
    val explode =
        if (param.explode != null) {
          param.explode
        } else {
          style != ParameterStyle.SIMPLE
        }
    val cleanType = paramType.replace(" ", "")
    val isList = isListType(cleanType)
    val isMap = isMapType(cleanType)

    if (!isList && !isMap) {
      return Result.success(listOf("cookie(\"${param.name}\", ${param.safeName}.toString())"))
    }

    val lines =
        if (style == ParameterStyle.FORM) {
          buildFormStyleCookieLines(param, isList, explode)
        } else {
          buildCookieStyleLines(param, isList, explode)
        }
    return Result.success(lines)
  }

  private fun buildCookieStyleLines(
      param: EndpointParameter,
      isList: Boolean,
      explode: Boolean
  ): List<String> {
    return when {
      isList && explode ->
          listOf(
              "${param.safeName}.forEach { value -> cookie(\"${param.name}\", value.toString()) }")
      isList -> listOf("cookie(\"${param.name}\", ${buildArrayJoinExpr(param.safeName, ",")})")
      explode ->
          listOf(
              "${param.safeName}.forEach { (key, value) -> cookie(key.toString(), value.toString()) }")
      else -> listOf("cookie(\"${param.name}\", ${buildObjectJoinExpr(param.safeName, ",", ",")})")
    }
  }

  private fun buildFormStyleCookieLines(
      param: EndpointParameter,
      isList: Boolean,
      explode: Boolean
  ): List<String> {
    if (explode) {
      return if (isList) {
        val expr = "${param.safeName}.joinToString(\"&\") { \"${param.name}=${'$'}it\" }"
        listOf("header(\"Cookie\", $expr)")
      } else {
        val expr = buildObjectJoinExpr(param.safeName, "&", "=")
        listOf("header(\"Cookie\", $expr)")
      }
    }

    return if (isList) {
      listOf("cookie(\"${param.name}\", ${buildArrayJoinExpr(param.safeName, ",")})")
    } else {
      listOf("cookie(\"${param.name}\", ${buildObjectJoinExpr(param.safeName, ",", ",")})")
    }
  }

  /**
   * Builds path template replacement expression for [param] wrapped in a [Result].
   *
   * @param param Path parameter.
   * @param paramType Resolved Kotlin type.
   * @return A [Result] containing the template replacement string or a
   *   [domain.CddError.ParameterSerializationError].
   */
  fun buildPathParamReplacement(param: EndpointParameter, paramType: String): Result<String> {
    if (param.content.isNotEmpty()) {
      if (param.style != null || param.explode != null || param.allowReserved != null) {
        return domain.CddError.ParameterSerializationError(
                param.name,
                "OAS 3.2: path parameters with content must not set style/explode/allowReserved for ${param.name}")
            .let { Result.failure(domain.CddException(it)) }
      }
      val contentType = EndpointMethodGenerator.resolveParameterContentType(param)
      val serialized = "serializeContentValue(${param.safeName}, \"$contentType\")"
      return Result.success(template("encodePathComponent($serialized, false)"))
    }

    val style = param.style ?: ParameterStyle.SIMPLE
    val explode = param.explode == true
    val allowReserved = param.allowReserved == true
    val cleanType = paramType.replace(" ", "")
    val isList = isListType(cleanType)
    val isMap = isMapType(cleanType)

    if (!isList && !isMap) {
      val encoded = "encodePathComponent(${param.safeName}.toString(), $allowReserved)"
      val res =
          when (style) {
            ParameterStyle.MATRIX -> ";${param.name}=${template(encoded)}"
            ParameterStyle.LABEL -> ".${template(encoded)}"
            else -> template(encoded)
          }
      return Result.success(res)
    }

    val res =
        when (style) {
          ParameterStyle.MATRIX ->
              buildMatrixPathReplacement(param.safeName, isList, explode, allowReserved)
          ParameterStyle.LABEL ->
              buildLabelPathReplacement(param.safeName, isList, explode, allowReserved)
          else -> buildSimplePathReplacement(param.safeName, isList, explode, allowReserved)
        }
    return Result.success(res)
  }

  private fun buildMatrixPathReplacement(
      name: String,
      isList: Boolean,
      explode: Boolean,
      allowReserved: Boolean
  ): String {
    return when {
      isList && explode ->
          ";$name=${template(buildPathArrayJoinExpr(name, ";$name=", allowReserved))}"
      isList -> ";$name=${template(buildPathArrayJoinExpr(name, ",", allowReserved))}"
      explode -> ";${template(buildPathObjectJoinExpr(name, ";", "=", allowReserved))}"
      else -> ";$name=${template(buildPathObjectJoinExpr(name, ",", ",", allowReserved))}"
    }
  }

  private fun buildLabelPathReplacement(
      name: String,
      isList: Boolean,
      explode: Boolean,
      allowReserved: Boolean
  ): String {
    return if (isList) {
      val delimiter = if (explode) "." else ","
      ".${template(buildPathArrayJoinExpr(name, delimiter, allowReserved))}"
    } else {
      val entryDelimiter = if (explode) "." else ","
      val keyValueDelimiter = if (explode) "=" else ","
      ".${template(buildPathObjectJoinExpr(name, entryDelimiter, keyValueDelimiter, allowReserved))}"
    }
  }

  private fun buildSimplePathReplacement(
      name: String,
      isList: Boolean,
      explode: Boolean,
      allowReserved: Boolean
  ): String {
    return if (isList) {
      template(buildPathArrayJoinExpr(name, ",", allowReserved))
    } else {
      val keyValueDelimiter = if (explode) "=" else ","
      template(buildPathObjectJoinExpr(name, ",", keyValueDelimiter, allowReserved))
    }
  }

  private fun template(expr: String): String = "\${$expr}"

  private fun buildArrayJoinExpr(name: String, delimiter: String): String {
    return "$name.joinToString(\"$delimiter\")"
  }

  private fun buildPathArrayJoinExpr(
      name: String,
      delimiter: String,
      allowReserved: Boolean
  ): String {
    val encodeExpr = "encodePathComponent(it.toString(), $allowReserved)"
    return "$name.joinToString(\"$delimiter\") { $encodeExpr }"
  }

  private fun buildObjectJoinExpr(
      name: String,
      entryDelimiter: String,
      keyValueDelimiter: String
  ): String {
    val pairExpr = "\"${'$'}{it.key}$keyValueDelimiter${'$'}{it.value}\""
    return "$name.entries.joinToString(\"$entryDelimiter\") { $pairExpr }"
  }

  private fun buildPathObjectJoinExpr(
      name: String,
      entryDelimiter: String,
      keyValueDelimiter: String,
      allowReserved: Boolean
  ): String {
    val keyExpr = "encodePathComponent(it.key.toString(), $allowReserved)"
    val valueExpr = "encodePathComponent(it.value.toString(), $allowReserved)"
    val pairExpr = "\"${'$'}{$keyExpr}$keyValueDelimiter${'$'}{$valueExpr}\""
    return "$name.entries.joinToString(\"$entryDelimiter\") { $pairExpr }"
  }
}
