/** PSI and KDoc parser for Ktor endpoint functions, URL templates, HTTP methods, and parameters. */
package psi.network

import domain.Callback
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.ExampleObject
import domain.ExternalDocumentation
import domain.HttpMethod
import domain.MediaTypeObject
import domain.OpenApiValue
import domain.ParameterLocation
import domain.ParameterStyle
import domain.ReferenceObject
import domain.RequestBody
import domain.SchemaProperty
import domain.Server
import openapi.JsonNode
import openapi.fields
import openapi.isObject
import openapi.parser.PathAndOperationParser
import openapi.parser.SchemaNodeParser
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import psi.TypeMappers
import psi.metadata.InfoMetadataCodec

/**
 * Parses individual Ktor client `KtNamedFunction` nodes into [EndpointDefinition]s, including URL
 * path templates, HTTP methods, parameters, and operation-level KDoc tags.
 */
object EndpointPsiParser {
  private val exampleObjectKeys =
      setOf(
          "\$ref",
          "summary",
          "description",
          "dataValue",
          "serializedValue",
          "externalValue",
          "value")

  private data class ParsedMethod(val method: HttpMethod, val customMethod: String? = null)

  private data class ParamExampleBundle(
      var example: ExampleObject? = null,
      val examples: MutableMap<String, ExampleObject> = mutableMapOf()
  )

  private data class ParamMeta(
      var style: ParameterStyle? = null,
      var explode: Boolean? = null,
      var allowReserved: Boolean? = null,
      var allowEmptyValue: Boolean? = null
  )

  /**
   * Parses a single Ktor client function into an [EndpointDefinition].
   *
   * @param func The PSI function declaration.
   * @return Parsed [EndpointDefinition], or `null` if the function does not contain a Ktor
   *   `request` call.
   */
  fun parseFunction(func: KtNamedFunction): EndpointDefinition? {
    val operationId = func.name!!
    val operationIdExplicit = !extractOperationIdOmitted(func)

    val callExpressions = func.collectDescendantsOfType<KtCallExpression>()
    val requestCall =
        callExpressions.find { call -> call.calleeExpression!!.text == "request" } ?: return null

    // 1. Path
    val urlArg = requestCall.valueArguments.firstOrNull()
    val stringTemplate = urlArg?.getArgumentExpression() as? KtStringTemplateExpression
    val rawPath = parseUrlTemplate(stringTemplate) ?: "/"
    val path = normalizeUrl(rawPath)

    // 2. Method and Params
    var method = HttpMethod.GET
    var customMethod: String? = null
    val parameters = mutableListOf<EndpointParameter>()
    var requestBodyVarName: String? = null
    var requestContentType: String? = null

    val pathParams = extractPathParams(path)
    pathParams.forEach { name ->
      parameters.add(EndpointParameter(name, "String", ParameterLocation.PATH))
    }

    val lambdaArg = requestCall.lambdaArguments.firstOrNull()
    val block = if (lambdaArg != null) lambdaArg.getLambdaExpression()!!.bodyExpression!! else null

    if (block != null) {
      block.collectDescendantsOfType<KtBinaryExpression>().forEach { statement ->
        when (statement.left!!.text) {
          "method" -> {
            val rightText = statement.right!!.text
            val parsed = parseMethod(rightText)
            method = parsed.method
            customMethod = parsed.customMethod
          }
          "url.encodedQuery" -> {
            val rightText = statement.right!!.text
            parameters.add(EndpointParameter(rightText, "String", ParameterLocation.QUERYSTRING))
          }
        }
      }

      block.collectDescendantsOfType<KtCallExpression>().forEach { statement ->
        val callee = statement.calleeExpression!!.text
        when (callee) {
          "parameter" ->
              extractParam(statement, ParameterLocation.QUERY)?.let { parameters.add(it) }
          "header" -> extractParam(statement, ParameterLocation.HEADER)?.let { parameters.add(it) }
          "cookie" -> extractParam(statement, ParameterLocation.COOKIE)?.let { parameters.add(it) }
          "contentType" -> requestContentType = RequestBodyPsiParser.parseContentType(statement)
          "setBody" -> {
            val bodyArg = statement.valueArguments.firstOrNull()
            requestBodyVarName =
                if (bodyArg != null) bodyArg.getArgumentExpression()!!.text else null
          }
        }
      }
    }

    // 3. Types from Signature
    val paramDocs = extractParamDocs(func)
    val paramExamples = extractParamExamples(func)
    val paramMeta = extractParamMeta(func)
    val paramSchemas = extractParamSchemas(func)
    val paramContents = extractParamContents(func)
    val paramRefs = extractParamRefs(func)
    val paramExtensions = extractParamExtensions(func)
    val finalParams =
        parameters.map { param ->
          val funcArg =
              func.valueParameters.find {
                it.name == param.name || "`" + it.name + "`" == param.name
              }
          val type = if (funcArg != null) funcArg.typeReference!!.text else "String"
          val schema = if (funcArg != null) TypeMappers.kotlinToSchemaProperty(type) else null
          val deprecated =
              if (funcArg != null) {
                funcArg.annotationEntries.any { it.shortName!!.asString() == "Deprecated" }
              } else {
                false
              }
          val isOptional =
              if (funcArg != null) {
                type.contains("?") || funcArg.hasDefaultValue()
              } else {
                false
              }
          val isRequired = if (param.location == ParameterLocation.PATH) true else !isOptional
          val docKey = param.name.removeSurrounding("`")
          val description = paramDocs[docKey]
          val exampleBundle = paramExamples[docKey]
          val meta = paramMeta[docKey]
          val schemaOverride = paramSchemas[docKey]
          val rawContent = paramContents[docKey]
          val contentOverride = if (!rawContent.isNullOrEmpty()) rawContent else null
          val refOverride = paramRefs[docKey]
          val extensionsOverride = paramExtensions[docKey] ?: emptyMap()
          val effectiveSchema =
              when {
                contentOverride != null -> null
                schemaOverride != null -> schemaOverride
                else -> schema
              }
          param.copy(
              type = type,
              schema = effectiveSchema,
              content = contentOverride ?: emptyMap(),
              deprecated = deprecated,
              isRequired = isRequired,
              description = description,
              example = exampleBundle?.example,
              examples = if (exampleBundle != null) exampleBundle.examples else emptyMap(),
              style = meta?.style,
              explode = meta?.explode,
              allowReserved = meta?.allowReserved,
              allowEmptyValue = meta?.allowEmptyValue,
              reference = refOverride,
              extensions = extensionsOverride)
        }

    var requestBodyType: String? = null
    var requestBodyRequired = true
    if (requestBodyVarName != null) {
      val cleanVarName = requestBodyVarName.trim()
      val funcArg = func.valueParameters.find { it.name == cleanVarName }
      if (funcArg != null) {
        val paramType = funcArg.typeReference!!.text
        requestBodyType = paramType
        val nullable = paramType.contains("?")
        val hasDefault = funcArg.hasDefaultValue()
        requestBodyRequired = !(nullable || hasDefault)
      }
    }
    var requestBody =
        if (requestBodyType != null) {
          val mediaType = requestContentType ?: "application/json"
          RequestBody(
              content =
                  mapOf(
                      mediaType to
                          MediaTypeObject(
                              schema = TypeMappers.kotlinToSchemaProperty(requestBodyType))),
              required = requestBodyRequired)
        } else {
          null
        }
    val requestBodyOverride = RequestBodyPsiParser.extractRequestBody(func)
    if (requestBodyOverride != null) {
      requestBody = RequestBodyPsiParser.mergeRequestBody(requestBody, requestBodyOverride)
      if (requestBodyType == null) {
        requestBodyType = RequestBodyPsiParser.resolveTypeFromContent(requestBody.content)
      }
    }

    // 4. Return Type & Responses
    val responses = mutableMapOf<String, EndpointResponse>()
    var successType = "Unit"

    val returnTypeRef = func.typeReference?.text
    if (returnTypeRef != null && returnTypeRef != "Unit") {
      val cleanRef = returnTypeRef.replace(" ", "")
      if (cleanRef.startsWith("Result<")) {
        successType = returnTypeRef.substringAfter("Result<").substringBeforeLast(">")
      } else {
        successType = returnTypeRef
      }
    }

    // 5. Extract Metadata (KDoc)
    val summary = extractDoc(func)
    val externalDocs = extractExternalDocs(func)
    val tags = extractTags(func)
    val extractedResponses = RequestBodyPsiParser.extractResponses(func)
    val deprecated = extractDeprecated(func)
    val securityResult = AuthPsiParser.extractSecurity(func)
    val servers = extractServers(func)
    val callbacks = extractCallbacks(func)
    val extensions = extractExtensions(func)

    if (extractedResponses.isNotEmpty()) {
      responses.putAll(extractedResponses)
    } else {
      val type = if (successType == "Unit") null else successType
      responses["200"] =
          EndpointResponse(
              statusCode = "200",
              description = "Success",
              type = type,
              content = RequestBodyPsiParser.contentForType(type))
    }

    return EndpointDefinition(
        path = path,
        method = method,
        customMethod = customMethod,
        operationId = operationId,
        operationIdExplicit = operationIdExplicit,
        parameters = finalParams,
        requestBodyType = requestBodyType,
        requestBody = requestBody,
        responses = responses,
        summary = summary,
        description = extractDescription(func),
        externalDocs = externalDocs,
        tags = tags,
        deprecated = deprecated,
        security = securityResult.requirements,
        securityExplicitEmpty = securityResult.explicitEmpty,
        servers = servers,
        callbacks = callbacks,
        extensions = extensions)
  }

  private fun normalizeUrl(rawUrl: String): String {
    var url = rawUrl.replace("{baseUrl}", "")
    val protoIdx = url.indexOf("://")
    if (url.startsWith("http") && protoIdx > 0) {
      val afterProto = url.substring(protoIdx + 3)
      val slashIdx = afterProto.indexOf("/")
      url = if (slashIdx >= 0) "/" + afterProto.substring(slashIdx + 1) else "/"
    }
    if (url.isEmpty()) return "/"
    return if (url.startsWith("/")) url else "/$url"
  }

  private fun parseUrlTemplate(template: KtStringTemplateExpression?): String? {
    if (template == null) return null
    val sb = StringBuilder()
    template.entries.forEach { entry ->
      when (entry) {
        is KtLiteralStringTemplateEntry -> sb.append(entry.text)
        is KtSimpleNameStringTemplateEntry -> sb.append("{${entry.expression!!.text}}")
        else -> {
          val blockEntry = entry as KtBlockStringTemplateEntry
          val content = blockEntry.expression!!.text
          val paramName = extractPathParamNameFromExpression(content)
          sb.append("{${paramName ?: content}}")
        }
      }
    }
    return sb.toString()
  }

  private fun extractPathParamNameFromExpression(expression: String): String? {
    val trimmed = expression.trim()

    if (trimmed.startsWith("encodePathComponent(")) {
      val inner = trimmed.removePrefix("encodePathComponent(").trim().removeSuffix(")")
      val firstArg = inner.substringBefore(",").trim()
      return extractPathParamNameFromExpression(firstArg)
    }

    if (trimmed.startsWith("serializeContentValue(")) {
      val inner = trimmed.removePrefix("serializeContentValue(").trim().removeSuffix(")")
      val firstArg = inner.substringBefore(",").trim()
      return extractPathParamNameFromExpression(firstArg)
    }

    Regex("""^([A-Za-z_][A-Za-z0-9_]*)\.entries\.joinToString\(""").find(trimmed)?.let {
      return it.groupValues[1]
    }

    Regex("""^([A-Za-z_][A-Za-z0-9_]*)\.joinToString\(""").find(trimmed)?.let {
      return it.groupValues[1]
    }

    val cleaned = trimmed.removeSuffix(".toString()").removeSurrounding("`")
    return cleaned.takeIf { it.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*$")) }
  }

  private fun extractPathParams(path: String): List<String> {
    val regex = """\{([^}]+)}""".toRegex()
    return regex.findAll(path).map { it.groupValues[1] }.toList()
  }

  private fun parseMethod(text: String): ParsedMethod {
    if (text.startsWith("HttpMethod.")) {
      val methodStr = text.substringAfter("HttpMethod.").uppercase()
      val matched = HttpMethod.entries.firstOrNull { it.name == methodStr } ?: HttpMethod.GET
      return ParsedMethod(matched)
    }

    if (text.startsWith("HttpMethod(")) {
      val raw = text.substringAfter("HttpMethod(").substringBeforeLast(")")
      val methodToken = raw.trim('"').trim()
      if (methodToken.isNotBlank()) {
        val upper = methodToken.uppercase()
        val matched = HttpMethod.entries.firstOrNull { it.name == upper }
        return if (matched != null) {
          ParsedMethod(matched)
        } else {
          ParsedMethod(HttpMethod.CUSTOM, methodToken)
        }
      }
    }

    return ParsedMethod(HttpMethod.GET)
  }

  private fun extractParam(
      expression: KtCallExpression,
      location: ParameterLocation
  ): EndpointParameter? {
    val args = expression.valueArguments
    if (args.size < 2) return null
    val keyExpr = args[0].getArgumentExpression()
    val key =
        when (keyExpr) {
          is KtStringTemplateExpression -> {
            val raw = keyExpr.text.trim('"')
            raw.substringBefore("[")
          }
          else -> inferParamNameFromLoop(expression) ?: "unknown"
        }
    return EndpointParameter(name = key, type = "String", location = location)
  }

  private fun inferParamNameFromLoop(expression: KtCallExpression): String? {
    val dotQualified =
        expression.parents.filterIsInstance<KtDotQualifiedExpression>().firstOrNull {
          val selectorCall = it.selectorExpression as KtCallExpression
          selectorCall.calleeExpression!!.text == "forEach"
        } ?: return null
    return dotQualified.receiverExpression.text.removeSurrounding("`")
  }

  private fun extractDoc(element: KtDeclaration): String? {
    val docComment = element.docComment ?: return null
    return docComment.text
        .lines()
        .map { AuthPsiParser.cleanKDocLine(it) }
        .filter { it.isNotEmpty() && !it.startsWith("@") }
        .joinToString(" ")
        .ifEmpty { null }
  }

  private fun parseParamTagPayload(line: String, tag: String): Pair<String, String?>? {
    val content = line.removePrefix(tag).trim()
    if (content.isEmpty()) return null
    val parts = content.split(" ", limit = 2)
    val name = parts[0].trim()
    val payload = if (parts.size > 1) parts[1].trim() else null
    return name to payload
  }

  private fun extractParamDocs(element: KtDeclaration): Map<String, String> {
    val docComment = element.docComment ?: return emptyMap()
    val docs = mutableMapOf<String, String>()

    docComment.text
        .lines()
        .map { AuthPsiParser.cleanKDocLine(it) }
        .filter { it == "@param" || it.startsWith("@param ") }
        .forEach { line ->
          val pair = parseParamTagPayload(line, "@param")
          if (pair != null && pair.second != null) {
            docs[pair.first] = pair.second!!
          }
        }

    return docs
  }

  private fun extractParamExamples(element: KtDeclaration): Map<String, ParamExampleBundle> {
    val docComment = element.docComment ?: return emptyMap()
    val bundles = mutableMapOf<String, ParamExampleBundle>()

    docComment.text
        .lines()
        .map { AuthPsiParser.cleanKDocLine(it) }
        .filter { it.startsWith("@paramExample") }
        .forEach { line ->
          val pair = parseParamTagPayload(line, "@paramExample")
          if (pair != null && pair.second != null) {
            val (paramName, rawExample) = pair.first to pair.second!!
            val bundle = bundles.getOrPut(paramName) { ParamExampleBundle() }
            val namedParts = rawExample.split(": ", limit = 2)
            if (namedParts.size == 2 && !namedParts[0].contains(" ")) {
              val key = namedParts[0].trim()
              val value = namedParts[1].trim()
              bundle.examples[key] = parseParamExampleValue(value)
            } else {
              bundle.example = parseParamExampleValue(rawExample)
            }
          }
        }

    return bundles
  }

  private fun parseParamExampleValue(raw: String): ExampleObject {
    val trimmed = raw.trim()
    if (trimmed.startsWith("external:")) {
      return ExampleObject(externalValue = trimmed.removePrefix("external:"))
    }
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      val node = openapi.parseJsonElementOrNull(trimmed)
      if (node != null) {
        if (node.isObject && looksLikeExampleObject(node)) {
          return PathAndOperationParser.parseExampleObject(node, null)
        }
        return ExampleObject(dataValue = SchemaNodeParser.nodeToValue(node))
      }
    }
    return ExampleObject(serializedValue = trimmed)
  }

  private fun looksLikeExampleObject(node: JsonNode): Boolean {
    return node.fields().any { it.key.startsWith("x-") || it.key in exampleObjectKeys }
  }

  private fun extractParamMeta(element: KtDeclaration): Map<String, ParamMeta> {
    val docComment = element.docComment ?: return emptyMap()
    val metas = mutableMapOf<String, ParamMeta>()
    val lines = docComment.text.lines().map { AuthPsiParser.cleanKDocLine(it) }

    lines
        .filter { it.startsWith("@paramStyle") }
        .forEach { line ->
          val pair = parseParamTagPayload(line, "@paramStyle")
          if (pair != null && pair.second != null) {
            val parsedStyle = parseParameterStyle(pair.second!!)
            if (parsedStyle != null) {
              metas.getOrPut(pair.first) { ParamMeta() }.style = parsedStyle
            }
          }
        }

    lines
        .filter { it.startsWith("@paramExplode") }
        .forEach { line ->
          val pair = parseParamTagPayload(line, "@paramExplode")
          if (pair != null) {
            val parsedBool = AuthPsiParser.parseBooleanTagValue(pair.second)
            if (parsedBool != null) {
              metas.getOrPut(pair.first) { ParamMeta() }.explode = parsedBool
            }
          }
        }

    lines
        .filter { it.startsWith("@paramAllowReserved") }
        .forEach { line ->
          val pair = parseParamTagPayload(line, "@paramAllowReserved")
          if (pair != null) {
            val parsedBool = AuthPsiParser.parseBooleanTagValue(pair.second)
            if (parsedBool != null) {
              metas.getOrPut(pair.first) { ParamMeta() }.allowReserved = parsedBool
            }
          }
        }

    lines
        .filter { it.startsWith("@paramAllowEmptyValue") }
        .forEach { line ->
          val pair = parseParamTagPayload(line, "@paramAllowEmptyValue")
          if (pair != null) {
            val parsedBool = AuthPsiParser.parseBooleanTagValue(pair.second)
            if (parsedBool != null) {
              metas.getOrPut(pair.first) { ParamMeta() }.allowEmptyValue = parsedBool
            }
          }
        }

    return metas
  }

  private fun extractParamSchemas(element: KtDeclaration): Map<String, SchemaProperty> {
    val docComment = element.docComment ?: return emptyMap()
    val lines = docComment.text.lines().map { AuthPsiParser.cleanKDocLine(it) }
    val schemas = mutableMapOf<String, SchemaProperty>()
    lines
        .filter { it.startsWith("@paramSchema") }
        .forEach { line ->
          val pair = parseParamTagPayload(line, "@paramSchema")
          if (pair != null && pair.second != null) {
            val node = openapi.parseJsonElementOrNull(pair.second!!) ?: return@forEach
            schemas[pair.first] = SchemaNodeParser.parseSchemaProperty(node)
          }
        }
    return schemas
  }

  private fun extractParamContents(
      element: KtDeclaration
  ): Map<String, Map<String, MediaTypeObject>> {
    val docComment = element.docComment ?: return emptyMap()
    val lines = docComment.text.lines().map { AuthPsiParser.cleanKDocLine(it) }
    val contents = mutableMapOf<String, Map<String, MediaTypeObject>>()
    lines
        .filter { it.startsWith("@paramContent") }
        .forEach { line ->
          val pair = parseParamTagPayload(line, "@paramContent")
          if (pair != null && pair.second != null) {
            val node = openapi.parseJsonElementOrNull(pair.second!!) ?: return@forEach
            contents[pair.first] = PathAndOperationParser.parseContentMap(node, null)
          }
        }
    return contents
  }

  private fun extractParamRefs(element: KtDeclaration): Map<String, ReferenceObject> {
    val docComment = element.docComment ?: return emptyMap()
    val lines = docComment.text.lines().map { AuthPsiParser.cleanKDocLine(it) }
    val refs = mutableMapOf<String, ReferenceObject>()
    lines
        .filter { it.startsWith("@paramRef") }
        .forEach { line ->
          val pair = parseParamTagPayload(line, "@paramRef")
          if (pair != null && pair.second != null) {
            val refObj = RequestBodyPsiParser.parseReferenceObjectValue(pair.second!!)
            if (refObj != null) refs[pair.first] = refObj
          }
        }
    return refs
  }

  private fun extractParamExtensions(
      element: KtDeclaration
  ): Map<String, Map<String, OpenApiValue>> {
    val docComment = element.docComment ?: return emptyMap()
    val lines = docComment.text.lines().map { AuthPsiParser.cleanKDocLine(it) }
    val extensions = mutableMapOf<String, Map<String, OpenApiValue>>()
    lines
        .filter { it.startsWith("@paramExtensions") }
        .forEach { line ->
          val pair = parseParamTagPayload(line, "@paramExtensions")
          if (pair != null && pair.second != null) {
            val node = openapi.parseJsonElementOrNull(pair.second!!) ?: return@forEach
            val parsed = SchemaNodeParser.parseExtensions(node)
            if (parsed.isNotEmpty()) {
              extensions[pair.first] = (extensions[pair.first] ?: emptyMap()) + parsed
            }
          }
        }
    return extensions
  }

  private fun extractExternalDocs(element: KtDeclaration): ExternalDocumentation? {
    val docComment = element.docComment ?: return null
    val lines = docComment.text.lines().map { AuthPsiParser.cleanKDocLine(it) }
    return InfoMetadataCodec.extractExternalDocsLine(lines)
  }

  private fun extractTags(element: KtDeclaration): List<String> {
    val docComment = element.docComment ?: return emptyList()
    val tagLine =
        docComment.text
            .lines()
            .map { AuthPsiParser.cleanKDocLine(it) }
            .find { it.startsWith("@tag") } ?: return emptyList()
    val content = tagLine.removePrefix("@tag").trim()
    return content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
  }

  private fun extractServers(element: KtDeclaration): List<Server> {
    val docComment = element.docComment ?: return emptyList()
    val lines = docComment.text.lines().map { AuthPsiParser.cleanKDocLine(it) }
    return InfoMetadataCodec.extractServersLine(lines)
  }

  private fun extractCallbacks(element: KtDeclaration): Map<String, Callback> {
    val docComment = element.docComment ?: return emptyMap()
    val lines = docComment.text.lines().map { AuthPsiParser.cleanKDocLine(it) }
    val callbackLines = lines.filter { it.startsWith("@callbacks") }
    if (callbackLines.isEmpty()) return emptyMap()
    val merged = LinkedHashMap<String, Callback>()
    callbackLines.forEach { line ->
      val json = line.removePrefix("@callbacks").trim()
      if (json.isNotEmpty()) {
        merged.putAll(AuthPsiParser.parseCallbacksJson(json))
      }
    }
    return merged
  }

  private fun extractExtensions(element: KtDeclaration): Map<String, OpenApiValue> {
    val docComment = element.docComment ?: return emptyMap()
    val lines = docComment.text.lines().map { AuthPsiParser.cleanKDocLine(it) }
    val extensionLines = lines.filter { it.startsWith("@extensions") }
    if (extensionLines.isEmpty()) return emptyMap()
    val merged = LinkedHashMap<String, OpenApiValue>()
    extensionLines.forEach { line ->
      val json = line.removePrefix("@extensions").trim()
      val node = openapi.parseJsonElementOrNull(json) ?: return@forEach
      if (!node.isObject) return@forEach
      node.fields().forEach { (key, value) -> merged[key] = SchemaNodeParser.nodeToValue(value) }
    }
    return merged
  }

  private fun extractDescription(element: KtDeclaration): String? {
    val docComment = element.docComment ?: return null
    val descLine =
        docComment.text
            .lines()
            .map { AuthPsiParser.cleanKDocLine(it) }
            .find { it.startsWith("@description") } ?: return null
    return descLine.removePrefix("@description").trim().ifEmpty { null }
  }

  private fun extractOperationIdOmitted(element: KtDeclaration): Boolean {
    val docComment = element.docComment ?: return false
    return docComment.text
        .lines()
        .map { AuthPsiParser.cleanKDocLine(it) }
        .any { it == "@operationIdOmitted" || it.startsWith("@operationIdOmitted ") }
  }

  private fun extractDeprecated(element: KtDeclaration): Boolean {
    val docComment = element.docComment
    val hasDocTag =
        if (docComment != null) {
          docComment.text
              .lines()
              .map { AuthPsiParser.cleanKDocLine(it) }
              .any { it == "@deprecated" || it.startsWith("@deprecated ") }
        } else {
          false
        }
    val hasAnnotation = element.annotationEntries.any { it.shortName!!.asString() == "Deprecated" }
    return hasDocTag || hasAnnotation
  }

  private fun parseParameterStyle(value: String): ParameterStyle? {
    return when (value.lowercase().replace("_", "")) {
      "matrix" -> ParameterStyle.MATRIX
      "label" -> ParameterStyle.LABEL
      "simple" -> ParameterStyle.SIMPLE
      "form" -> ParameterStyle.FORM
      "cookie" -> ParameterStyle.COOKIE
      "spacedelimited" -> ParameterStyle.SPACE_DELIMITED
      "pipedelimited" -> ParameterStyle.PIPE_DELIMITED
      "deepobject" -> ParameterStyle.DEEP_OBJECT
      else -> null
    }
  }
}
