/**
 * Parses OpenAPI 3.x, Swagger 2.0, and JSON Schema Draft 2020-12 documents from JSON or YAML into
 * domain models.
 */
package openapi

import domain.CddError
import domain.Contact
import domain.Info
import domain.License
import domain.OpenApiDefinition
import domain.SchemaProperty
import domain.toFailure
import openapi.parser.ComponentParser
import openapi.parser.PathAndOperationParser
import openapi.parser.SchemaNodeParser
import openapi.parser.Swagger2Upgrader
import openapi.parser.YamlToJsonElementConverter

/**
 * Parses OpenAPI 3.x (including 3.2) and Swagger 2.0 specifications from JSON or YAML into the
 * internal [OpenApiDefinition] domain model.
 *
 * Delegates schema parsing to [SchemaNodeParser], paths/operations/callbacks to
 * [PathAndOperationParser], reusable components to [ComponentParser], and Swagger 2.0 upgrading to
 * [Swagger2Upgrader].
 */
class OpenApiParser {
  /** Supported input formats for OpenAPI specifications. */
  enum class Format {
    /** Automatically detect JSON vs YAML based on content. */
    AUTO,
    /** Parse strictly as JSON. */
    JSON,
    /** Parse as YAML (or JSON, as a subset of YAML). */
    YAML
  }

  private val selfBaseStack = ArrayDeque<String?>()

  private val currentSelfBase: String?
    get() = selfBaseStack.lastOrNull()

  private fun <T> withSelfBase(self: String?, baseUri: String?, block: () -> T): T {
    val resolved = ComponentParser.resolveSelfBase(self, baseUri)
    selfBaseStack.addLast(resolved)
    val result = block()
    selfBaseStack.removeLast()
    return result
  }

  /**
   * Parses an OpenAPI document from a raw string into a [Result].
   *
   * @param source Raw JSON or YAML specification content.
   * @param format Format hint (defaults to [Format.AUTO]).
   * @param baseUri Optional retrieval URI used to resolve relative `$self` values.
   * @return A [Result] containing the parsed [OpenApiDefinition] or a [CddError].
   */
  fun parseStringResult(
      source: String,
      format: Format = Format.AUTO,
      baseUri: String? = null,
  ): Result<OpenApiDefinition> {
    val resolvedFormat = resolveFormat(source, format)
    val root =
        readTreeResult(source, resolvedFormat).getOrElse {
          return Result.failure(it)
        }
    if (!root.isObject) {
      return CddError.OpenApiParseError("OpenAPI root must be an object").toFailure()
    }
    return Result.success(parseOpenApi(root, baseUri))
  }

  /**
   * Parses an OpenAPI document from a raw string.
   *
   * @param source Raw JSON or YAML specification content.
   * @param format Format hint (defaults to [Format.AUTO]).
   * @param baseUri Optional retrieval URI used to resolve relative `$self` values.
   * @return Parsed [OpenApiDefinition].
   */
  fun parseString(
      source: String,
      format: Format = Format.AUTO,
      baseUri: String? = null,
  ): OpenApiDefinition {
    val resolvedFormat = resolveFormat(source, format)
    val root = readTree(source, resolvedFormat)
    return parseOpenApi(root, baseUri)
  }

  /**
   * Parses either an OpenAPI document or a standalone Schema Object document from a raw string into
   * a [Result].
   *
   * @param source Raw JSON or YAML document content.
   * @param format Format hint (defaults to [Format.AUTO]).
   * @param baseUri Optional retrieval URI used to resolve relative `$self` values.
   * @return A [Result] containing the parsed [OpenApiDocument] or a [CddError].
   */
  fun parseDocumentStringResult(
      source: String,
      format: Format = Format.AUTO,
      baseUri: String? = null,
  ): Result<OpenApiDocument> {
    val resolvedFormat = resolveFormat(source, format)
    val root =
        readTreeResult(source, resolvedFormat).getOrElse {
          return Result.failure(it)
        }
    return parseDocumentResult(root, baseUri)
  }

  /**
   * Parses either an OpenAPI document or a standalone Schema Object document from a raw string.
   *
   * If the root contains `openapi`, it is parsed as an [OpenApiDefinition]. Otherwise, the root is
   * treated as a JSON Schema (object or boolean) and parsed as a [SchemaProperty].
   *
   * @param source Raw JSON or YAML document content.
   * @param format Format hint (defaults to [Format.AUTO]).
   * @param baseUri Optional retrieval URI used to resolve relative `$self` values.
   * @return Parsed [OpenApiDocument].
   */
  fun parseDocumentString(
      source: String,
      format: Format = Format.AUTO,
      baseUri: String? = null,
  ): OpenApiDocument {
    val resolvedFormat = resolveFormat(source, format)
    val root = readTree(source, resolvedFormat)
    return parseDocument(root, baseUri)
  }

  /**
   * Parses a standalone Schema Object document from a raw string into a [Result].
   *
   * @param source Raw JSON or YAML schema content.
   * @param format Format hint (defaults to [Format.AUTO]).
   * @return A [Result] containing the parsed [SchemaProperty] or a [CddError].
   */
  fun parseSchemaStringResult(
      source: String,
      format: Format = Format.AUTO,
  ): Result<SchemaProperty> {
    val resolvedFormat = resolveFormat(source, format)
    val root =
        readTreeResult(source, resolvedFormat).getOrElse {
          return Result.failure(it)
        }
    return SchemaNodeParser.parseSchemaRootResult(root)
  }

  /**
   * Parses a standalone Schema Object document from a raw string.
   *
   * @param source Raw JSON or YAML schema content.
   * @param format Format hint (defaults to [Format.AUTO]).
   * @return Parsed [SchemaProperty].
   */
  fun parseSchemaString(
      source: String,
      format: Format = Format.AUTO,
  ): SchemaProperty {
    val resolvedFormat = resolveFormat(source, format)
    val root = readTree(source, resolvedFormat)
    return SchemaNodeParser.parseSchemaRoot(root)
  }

  private fun resolveFormat(source: String, hint: Format): Format {
    if (hint != Format.AUTO) return hint
    val trimmed = source.trimStart()
    return if (trimmed.firstOrNull() in setOf('{', '[')) {
      Format.JSON
    } else {
      Format.YAML
    }
  }

  private fun readTreeResult(source: String, format: Format): Result<JsonNode> {
    return if (format == Format.YAML) {
      Result.success(YamlToJsonElementConverter.parseYaml(source))
    } else {
      parseJsonElementResult(source)
    }
  }

  private fun readTree(source: String, format: Format): JsonNode {
    return readTreeResult(source, format).getOrElse { YamlToJsonElementConverter.parseYaml(source) }
  }

  private fun parseDocumentResult(
      root: JsonNode,
      baseUri: String?,
  ): Result<OpenApiDocument> {
    if (root.has("arazzo")) {
      return arazzo.ArazzoParser().parseJsonObjectResult(root).map { OpenApiDocument.Arazzo(it) }
    }
    val isOpenApi = listOf("openapi", "swagger").any { root.has(it) }
    return if (isOpenApi) {
      Result.success(OpenApiDocument.OpenApi(parseOpenApi(root, baseUri)))
    } else {
      SchemaNodeParser.parseSchemaRootResult(root).map { OpenApiDocument.Schema(it) }
    }
  }

  private fun parseDocument(
      root: JsonNode,
      baseUri: String?,
  ): OpenApiDocument {
    return parseDocumentResult(root, baseUri).getOrElse {
      OpenApiDocument.Schema(SchemaNodeParser.parseSchemaRoot(root))
    }
  }

  private fun parseOpenApi(
      root: JsonNode,
      baseUri: String?,
  ): OpenApiDefinition {
    val self = root.text("\$self")
    return withSelfBase(self, baseUri) {
      val isSwagger2 = root.has("swagger")
      val defaultOpenApiVersion = if (isSwagger2) "3.0.0" else "3.2.0"
      val components =
          if (isSwagger2) Swagger2Upgrader.parseSwagger2Components(root, currentSelfBase)
          else ComponentParser.parseComponents(root.get("components"), currentSelfBase)
      val pathsResult =
          PathAndOperationParser.parsePaths(root.get("paths"), components, currentSelfBase)
      val webhooksResult =
          PathAndOperationParser.parsePaths(root.get("webhooks"), components, currentSelfBase)
      val securityResult = PathAndOperationParser.parseSecurityRequirements(root.get("security"))
      OpenApiDefinition(
          openapi = root.text("openapi") ?: defaultOpenApiVersion,
          swagger = root.text("swagger"),
          host = root.text("host"),
          basePath = root.text("basePath"),
          schemes = Swagger2Upgrader.parseStringArray(root, "schemes"),
          consumes = Swagger2Upgrader.parseStringArray(root, "consumes"),
          produces = Swagger2Upgrader.parseStringArray(root, "produces"),
          info = parseInfo(root.get("info")) ?: Info(title = "Unknown", version = "0.0.0"),
          jsonSchemaDialect = root.text("jsonSchemaDialect"),
          servers = PathAndOperationParser.parseServers(root.get("servers")),
          paths = pathsResult.items,
          pathsExtensions = pathsResult.extensions,
          pathsExplicitEmpty = pathsResult.explicitEmpty,
          webhooks = webhooksResult.items,
          webhooksExtensions = webhooksResult.extensions,
          webhooksExplicitEmpty = webhooksResult.explicitEmpty,
          components = components,
          security = securityResult.requirements,
          securityExplicitEmpty = securityResult.explicitEmpty,
          tags = PathAndOperationParser.parseTags(root.get("tags")),
          externalDocs = SchemaNodeParser.parseExternalDocs(root.get("externalDocs")),
          self = self,
          extensions = SchemaNodeParser.parseExtensions(root))
    }
  }

  private fun parseInfo(node: JsonNode?): Info? {
    val obj = node.asObject() ?: return null
    return Info(
        title = obj.text("title") ?: "",
        version = obj.text("version") ?: "",
        summary = obj.text("summary"),
        description = obj.text("description"),
        termsOfService = obj.text("termsOfService"),
        contact = parseContact(obj.get("contact")),
        license = parseLicense(obj.get("license")),
        extensions = SchemaNodeParser.parseExtensions(obj))
  }

  private fun parseContact(node: JsonNode?): Contact? {
    val obj = node.asObject() ?: return null
    return Contact(
        name = obj.text("name"),
        url = obj.text("url"),
        email = obj.text("email"),
        extensions = SchemaNodeParser.parseExtensions(obj))
  }

  private fun parseLicense(node: JsonNode?): License? {
    val obj = node.asObject() ?: return null
    return License(
        name = obj.text("name") ?: "",
        identifier = obj.text("identifier"),
        url = obj.text("url"),
        extensions = SchemaNodeParser.parseExtensions(obj))
  }
}
