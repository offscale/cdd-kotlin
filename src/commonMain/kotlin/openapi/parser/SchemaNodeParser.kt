/** JSON Schema Draft 2020-12 and OpenAPI 3.0/3.1/3.2 schema node parser. */
package openapi.parser

import domain.Components
import domain.Discriminator
import domain.ExternalDocumentation
import domain.MediaTypeObject
import domain.OpenApiValue
import domain.OpenApiValues
import domain.SchemaDefinition
import domain.SchemaProperty
import domain.Xml
import openapi.JsonNode
import openapi.asArray
import openapi.asObject
import openapi.boolean
import openapi.booleanValue
import openapi.double
import openapi.elements
import openapi.fields
import openapi.get
import openapi.has
import openapi.int
import openapi.isArray
import openapi.isBoolean
import openapi.isNullNode
import openapi.isNumber
import openapi.isObject
import openapi.isTextual
import openapi.text
import openapi.textValue

/**
 * Parses OpenAPI and JSON Schema Draft 2020-12 schema definitions, properties, boolean schemas,
 * multi-type arrays, `$defs`, composition keywords, and media-type schema inference.
 */
object SchemaNodeParser {
  private val schemaKnownKeys =
      setOf(
          "\$ref",
          "\$dynamicRef",
          "\$id",
          "\$schema",
          "\$anchor",
          "\$dynamicAnchor",
          "\$comment",
          "\$defs",
          "type",
          "nullable",
          "format",
          "contentMediaType",
          "contentEncoding",
          "minLength",
          "maxLength",
          "pattern",
          "minimum",
          "maximum",
          "multipleOf",
          "exclusiveMinimum",
          "exclusiveMaximum",
          "minItems",
          "maxItems",
          "uniqueItems",
          "minProperties",
          "maxProperties",
          "items",
          "prefixItems",
          "contains",
          "minContains",
          "maxContains",
          "properties",
          "patternProperties",
          "propertyNames",
          "additionalProperties",
          "required",
          "dependentRequired",
          "dependentSchemas",
          "enum",
          "description",
          "title",
          "default",
          "const",
          "deprecated",
          "readOnly",
          "writeOnly",
          "externalDocs",
          "discriminator",
          "xml",
          "oneOf",
          "anyOf",
          "allOf",
          "not",
          "if",
          "then",
          "else",
          "example",
          "examples",
          "unevaluatedProperties",
          "unevaluatedItems",
          "contentSchema")

  /**
   * Parses a standalone JSON Schema root node into a [SchemaProperty] wrapped in a [Result].
   *
   * @param root The JSON Schema root node.
   * @return A [Result] containing the parsed [SchemaProperty] or a
   *   [domain.CddError.SchemaParseError].
   */
  fun parseSchemaRootResult(root: JsonNode): Result<SchemaProperty> {
    if (!root.isObject && !root.isBoolean) {
      return domain.CddError.SchemaParseError("Schema document root must be an object or boolean")
          .let { Result.failure(domain.CddException(it)) }
    }
    if (root.isObject && root.has("openapi")) {
      return domain.CddError.SchemaParseError(
              "Expected a Schema Object root, but found an OpenAPI Object (`openapi` field)")
          .let { Result.failure(domain.CddException(it)) }
    }
    return Result.success(parseSchemaProperty(root))
  }

  /**
   * Parses a standalone JSON Schema root node into a [SchemaProperty].
   *
   * @param root The JSON Schema root node.
   * @return Parsed [SchemaProperty].
   */
  fun parseSchemaRoot(root: JsonNode): SchemaProperty = parseSchemaProperty(root)

  /**
   * Parses a map of named [SchemaDefinition]s from a `schemas` or `definitions` node.
   *
   * @param node The JSON object node mapping schema names to schema definitions.
   * @return Map of schema names to [SchemaDefinition]s.
   */
  fun parseSchemas(node: JsonNode?): Map<String, SchemaDefinition> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      name to parseSchemaDefinition(name, raw)
    }
  }

  /**
   * Parses a single named [SchemaDefinition].
   *
   * @param name The schema name.
   * @param node The JSON node representing the schema.
   * @return Parsed [SchemaDefinition].
   */
  fun parseSchemaDefinition(name: String, node: JsonNode): SchemaDefinition {
    val prop = parseSchemaProperty(node)
    if (prop.booleanSchema != null) {
      return SchemaDefinition(
          name = name, booleanSchema = prop.booleanSchema, type = "object", typeExplicit = false)
    }
    val rawTypes = parseTypes(node)
    val resolvedType = resolveSchemaType(rawTypes, node)

    return SchemaDefinition(
        name = name,
        ref = prop.ref,
        dynamicRef = prop.dynamicRef,
        booleanSchema = null,
        type = resolvedType,
        typeExplicit = node.has("type"),
        schemaId = prop.schemaId,
        schemaDialect = prop.schemaDialect,
        anchor = prop.anchor,
        dynamicAnchor = prop.dynamicAnchor,
        comment = prop.comment,
        defs = prop.defs,
        types = prop.types,
        format = prop.format,
        contentMediaType = prop.contentMediaType,
        contentEncoding = prop.contentEncoding,
        minLength = prop.minLength,
        maxLength = prop.maxLength,
        pattern = prop.pattern,
        minimum = prop.minimum,
        maximum = prop.maximum,
        multipleOf = prop.multipleOf,
        exclusiveMinimum = prop.exclusiveMinimum,
        exclusiveMaximum = prop.exclusiveMaximum,
        minItems = prop.minItems,
        maxItems = prop.maxItems,
        collectionFormat = prop.collectionFormat,
        uniqueItems = prop.uniqueItems,
        minProperties = prop.minProperties,
        maxProperties = prop.maxProperties,
        items = prop.items,
        prefixItems = prop.prefixItems,
        contains = prop.contains,
        minContains = prop.minContains,
        maxContains = prop.maxContains,
        properties = prop.properties,
        patternProperties = prop.patternProperties,
        propertyNames = prop.propertyNames,
        additionalProperties = prop.additionalProperties,
        required = prop.required,
        dependentRequired = prop.dependentRequired,
        dependentSchemas = prop.dependentSchemas,
        enumValues = prop.enumValues,
        description = prop.description,
        title = prop.title,
        defaultValue = prop.defaultValue,
        constValue = prop.constValue,
        deprecated = prop.deprecated,
        readOnly = prop.readOnly,
        writeOnly = prop.writeOnly,
        externalDocs = prop.externalDocs,
        discriminator = prop.discriminator,
        xml = prop.xml,
        unevaluatedProperties = prop.unevaluatedProperties,
        unevaluatedItems = prop.unevaluatedItems,
        contentSchema = prop.contentSchema,
        oneOf = parseSchemaRefs(node.get("oneOf")),
        oneOfSchemas = parseSchemaInlineList(node.get("oneOf")),
        anyOf = parseSchemaRefs(node.get("anyOf")),
        anyOfSchemas = parseSchemaInlineList(node.get("anyOf")),
        allOf = parseSchemaRefs(node.get("allOf")),
        allOfSchemas = parseSchemaInlineList(node.get("allOf")),
        not = prop.not,
        ifSchema = prop.ifSchema,
        thenSchema = prop.thenSchema,
        elseSchema = prop.elseSchema,
        example = prop.example,
        examples = parseSchemaExamples(node.get("examples")),
        examplesList = prop.examples,
        customKeywords = prop.customKeywords,
        extensions = prop.extensions)
  }

  private fun parseOptionalSchemaProperty(node: JsonNode?): SchemaProperty? {
    return if (node != null) parseSchemaProperty(node) else null
  }

  private fun parseOptionalValue(node: JsonNode?): OpenApiValue? {
    return if (node != null) nodeToValue(node) else null
  }

  private fun parseSchemaProperties(node: JsonNode?): Map<String, SchemaProperty> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) -> name to parseSchemaProperty(raw) }
  }

  private fun parseSchemaPropertyList(node: JsonNode?): List<SchemaProperty> {
    val array = node.asArray() ?: return emptyList()
    return array.map { parseSchemaProperty(it) }
  }

  private fun parseSchemaInlineList(node: JsonNode?): List<SchemaProperty> {
    val array = node.asArray() ?: return emptyList()
    return array.mapNotNull { item ->
      if (item.text("\$ref") != null) return@mapNotNull null
      parseSchemaProperty(item)
    }
  }

  /**
   * Parses an inline or nested [SchemaProperty] from a JSON node.
   *
   * @param node The JSON node representing the schema property.
   * @return Parsed [SchemaProperty].
   */
  fun parseSchemaProperty(node: JsonNode): SchemaProperty {
    if (node.isBoolean) {
      return SchemaProperty(booleanSchema = node.booleanValue())
    }
    val ref = node.text("\$ref")
    val dynamicRef = node.text("\$dynamicRef")
    val customKeywords = parseCustomSchemaKeywords(node)
    val rawTypes = parseTypes(node)
    val legacyNullable = isLegacyNullable(node)
    val inferredType =
        if (rawTypes.isEmpty() && legacyNullable) resolveSchemaType(rawTypes, node) else null
    val types = applyLegacyNullable(rawTypes, inferredType, legacyNullable)

    return SchemaProperty(
        booleanSchema = null,
        ref = ref,
        dynamicRef = dynamicRef,
        types = types,
        schemaId = node.text("\$id"),
        schemaDialect = node.text("\$schema"),
        anchor = node.text("\$anchor"),
        dynamicAnchor = node.text("\$dynamicAnchor"),
        format = node.text("format"),
        contentMediaType = node.text("contentMediaType"),
        contentEncoding = node.text("contentEncoding"),
        minLength = node.int("minLength"),
        maxLength = node.int("maxLength"),
        pattern = node.text("pattern"),
        minimum = node.double("minimum"),
        maximum = node.double("maximum"),
        multipleOf = node.double("multipleOf"),
        exclusiveMinimum = node.double("exclusiveMinimum"),
        exclusiveMaximum = node.double("exclusiveMaximum"),
        minItems = node.int("minItems"),
        maxItems = node.int("maxItems"),
        collectionFormat = node.text("collectionFormat"),
        uniqueItems = node.boolean("uniqueItems"),
        minProperties = node.int("minProperties"),
        maxProperties = node.int("maxProperties"),
        items = parseOptionalSchemaProperty(node.get("items")),
        prefixItems = parseSchemaPropertyList(node.get("prefixItems")),
        contains = parseOptionalSchemaProperty(node.get("contains")),
        minContains = node.int("minContains"),
        maxContains = node.int("maxContains"),
        properties = parseSchemaProperties(node.get("properties")),
        required = Swagger2Upgrader.parseStringArray(node, "required"),
        additionalProperties = parseAdditionalProperties(node.get("additionalProperties")),
        defs = parseSchemaProperties(node.get("\$defs")),
        patternProperties = parseSchemaProperties(node.get("patternProperties")),
        propertyNames = parseOptionalSchemaProperty(node.get("propertyNames")),
        dependentRequired = parseDependentRequired(node.get("dependentRequired")),
        dependentSchemas = parseSchemaProperties(node.get("dependentSchemas")),
        description = node.text("description"),
        title = node.text("title"),
        defaultValue = parseOptionalValue(node.get("default")),
        constValue = parseOptionalValue(node.get("const")),
        deprecated = node.boolean("deprecated") ?: false,
        readOnly = node.boolean("readOnly") ?: false,
        writeOnly = node.boolean("writeOnly") ?: false,
        externalDocs = parseExternalDocs(node.get("externalDocs")),
        discriminator = parseDiscriminator(node.get("discriminator")),
        comment = node.text("\$comment"),
        enumValues = parseEnumValues(node.get("enum")),
        oneOf = parseSchemaPropertyList(node.get("oneOf")),
        anyOf = parseSchemaPropertyList(node.get("anyOf")),
        allOf = parseSchemaPropertyList(node.get("allOf")),
        not = parseOptionalSchemaProperty(node.get("not")),
        ifSchema = parseOptionalSchemaProperty(node.get("if")),
        thenSchema = parseOptionalSchemaProperty(node.get("then")),
        elseSchema = parseOptionalSchemaProperty(node.get("else")),
        example = parseOptionalValue(node.get("example")),
        examples = parseSchemaExamplesList(node.get("examples")),
        xml = parseXml(node.get("xml")),
        unevaluatedProperties = parseOptionalSchemaProperty(node.get("unevaluatedProperties")),
        unevaluatedItems = parseOptionalSchemaProperty(node.get("unevaluatedItems")),
        contentSchema = parseOptionalSchemaProperty(node.get("contentSchema")),
        customKeywords = customKeywords,
        extensions = parseExtensions(node))
  }

  private fun parseAdditionalProperties(node: JsonNode?): SchemaProperty? {
    return when {
      node == null -> null
      node.isBoolean -> SchemaProperty(booleanSchema = node.booleanValue())
      node.isObject -> parseSchemaProperty(node)
      else -> null
    }
  }

  /**
   * Parses a JSON object mapping property names to lists of required dependent property names.
   *
   * @param node The JSON node representing `dependentRequired`.
   * @return Map of property name to dependent required property names.
   */
  fun parseDependentRequired(node: JsonNode?): Map<String, List<String>> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (key, _) ->
      key to Swagger2Upgrader.parseStringArray(obj, key)
    }
  }

  private fun parseEnumValues(node: JsonNode?): List<OpenApiValue>? {
    val array = node.asArray() ?: return null
    return array.map { nodeToValue(it) }
  }

  private fun parseSchemaExamples(node: JsonNode?): Map<String, OpenApiValue>? {
    if (node == null) return null
    if (node.isArray) {
      val map = LinkedHashMap<String, OpenApiValue>()
      node.elements().forEachIndexed { index, item ->
        map["example${index + 1}"] = nodeToValue(item)
      }
      return map
    }
    if (node.isObject) {
      return node.fields().asSequence().associate { (k, v) -> k to nodeToValue(v) }
    }
    return null
  }

  private fun parseSchemaExamplesList(node: JsonNode?): List<OpenApiValue>? {
    if (node == null || !node.isArray) return null
    return node.elements().map { nodeToValue(it) }
  }

  private fun parseCustomSchemaKeywords(node: JsonNode): Map<String, OpenApiValue> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields()
        .asSequence()
        .filter { (key, _) -> !key.startsWith("x-") && key !in schemaKnownKeys }
        .associate { (key, value) -> key to nodeToValue(value) }
  }

  private fun parseDiscriminator(node: JsonNode?): Discriminator? {
    val obj = node.asObject() ?: return null
    val mapping = ComponentParser.parseScopesMap(obj.get("mapping")).orEmpty()
    return Discriminator(
        propertyName = obj.text("propertyName") ?: "type",
        mapping = mapping,
        defaultMapping = obj.text("defaultMapping"),
        extensions = parseExtensions(obj))
  }

  private fun parseXml(node: JsonNode?): Xml? {
    val obj = node.asObject() ?: return null
    return Xml(
        name = obj.text("name"),
        namespace = obj.text("namespace"),
        prefix = obj.text("prefix"),
        nodeType = obj.text("nodeType"),
        attribute = obj.boolean("attribute") ?: false,
        wrapped = obj.boolean("wrapped") ?: false,
        extensions = parseExtensions(obj))
  }

  /**
   * Parses an [ExternalDocumentation] object from a JSON node.
   *
   * @param node The JSON node.
   * @return Parsed [ExternalDocumentation] or `null`.
   */
  fun parseExternalDocs(node: JsonNode?): ExternalDocumentation? {
    val obj = node.asObject() ?: return null
    val url = obj.text("url") ?: return null
    return ExternalDocumentation(
        description = obj.text("description"), url = url, extensions = parseExtensions(obj))
  }

  private fun parseTypes(node: JsonNode): Set<String> {
    val typeNode = node.get("type") ?: return emptySet()
    return when {
      typeNode.isTextual -> setOfNotNull(typeNode.textValue())
      typeNode.isArray -> typeNode.elements().mapNotNull { it.textValue() }.toSet()
      else -> emptySet()
    }
  }

  private fun isLegacyNullable(node: JsonNode): Boolean {
    return (node.boolean("nullable") == true) or (node.boolean("x-nullable") == true)
  }

  private fun applyLegacyNullable(
      rawTypes: Set<String>,
      fallbackType: String?,
      legacyNullable: Boolean
  ): Set<String> {
    if (!legacyNullable) return rawTypes
    val baseTypes =
        if (rawTypes.isNotEmpty()) {
          rawTypes
        } else {
          setOfNotNull(fallbackType)
        }
    return baseTypes + "null"
  }

  private fun resolveSchemaType(types: Set<String>, node: JsonNode): String {
    if (types.isNotEmpty()) {
      return (types - "null").firstOrNull() ?: "null"
    }
    inferEnumType(node.get("enum"))?.let {
      return it
    }
    val arrayKeys = setOf("items", "prefixItems", "contains", "minItems", "maxItems", "uniqueItems")
    val objectKeys =
        setOf(
            "properties",
            "additionalProperties",
            "patternProperties",
            "propertyNames",
            "dependentSchemas",
            "required",
            "minProperties",
            "maxProperties")
    val numberKeys =
        setOf("minimum", "maximum", "multipleOf", "exclusiveMinimum", "exclusiveMaximum")
    val stringKeys =
        setOf("minLength", "maxLength", "pattern", "contentEncoding", "contentMediaType", "format")
    return when {
      arrayKeys.any { node.has(it) } -> "array"
      objectKeys.any { node.has(it) } -> "object"
      numberKeys.any { node.has(it) } -> "number"
      stringKeys.any { node.has(it) } -> "string"
      else -> "object"
    }
  }

  private fun inferEnumType(node: JsonNode?): String? {
    val array = node.asArray() ?: return null
    val types =
        array
            .map { item ->
              when {
                item.isTextual -> "string"
                item.isNumber -> "number"
                item.isBoolean -> "boolean"
                item.isNullNode -> "null"
                item.isArray -> "array"
                else -> "object"
              }
            }
            .toSet() - "null"
    return if (types.size == 1) types.first() else null
  }

  private fun parseSchemaRefs(node: JsonNode?): List<String> {
    val array = node.asArray() ?: return emptyList()
    return array.mapNotNull { item -> item.text("\$ref") ?: item.textValue() }
  }

  /**
   * Selects the preferred [SchemaProperty] from a media-type content map.
   *
   * @param content Content map keyed by media type.
   * @param components Optional components context.
   * @return Selected or inferred [SchemaProperty], or `null`.
   */
  fun selectSchema(
      content: Map<String, MediaTypeObject>?,
      components: Components?
  ): SchemaProperty? {
    if (content == null || content.isEmpty()) return null
    val preferredEntry = selectPreferredMediaTypeEntry(content)
    val preferred = preferredEntry.value
    preferred.schema?.let {
      return it
    }
    preferred.itemSchema?.let {
      return wrapItemSchemaAsArray(it)
    }
    val ref = preferred.ref ?: preferred.reference?.ref
    if (ref != null) {
      return SchemaProperty(ref = ref)
    }
    return inferSchemaFromMediaType(preferredEntry.key)
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

  private fun normalizeMediaTypeKey(value: String): String {
    return value.substringBefore(";").trim().lowercase()
  }

  private fun isConcreteMediaType(value: String): Boolean {
    val main = value.trim().substringBefore(";").trim()
    val parts = main.split("/")
    return parts.size == 2 && !main.contains("*")
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

  /**
   * Extracts specification extensions (`x-*` fields) from a JSON object node.
   *
   * @param node The JSON node.
   * @return Map of extension keys to [OpenApiValue]s.
   */
  fun parseExtensions(node: JsonNode?): Map<String, OpenApiValue> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields()
        .asSequence()
        .filter { (key, _) -> key.startsWith("x-") }
        .associate { (key, value) -> key to nodeToValue(value) }
  }

  /**
   * Converts a [JsonNode] into a strongly-typed [OpenApiValue].
   *
   * @param node The JSON node.
   * @return Converted [OpenApiValue].
   */
  fun nodeToValue(node: JsonNode): OpenApiValue {
    return OpenApiValues.fromJsonElement(node)
  }
}
