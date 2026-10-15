/**
 * Parses KDoc tags on Kotlin declarations into OpenAPI [SchemaProperty] and constraint metadata.
 */
package psi.dto

import domain.Discriminator
import domain.OpenApiArray
import domain.OpenApiBoolean
import domain.OpenApiInt
import domain.OpenApiNull
import domain.OpenApiNumber
import domain.OpenApiObject
import domain.OpenApiString
import domain.OpenApiValue
import domain.OpenApiValues
import domain.SchemaProperty
import domain.Xml
import domain.stringOrNull
import openapi.JsonNode
import openapi.asArray
import openapi.asObject
import openapi.asText
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
import openapi.isObject
import openapi.isTextual
import openapi.text
import openapi.textValue
import org.jetbrains.kotlin.psi.KtModifierListOwner

/**
 * Result of parsing a composition KDoc tag (`@oneOf`, `@anyOf`, `@allOf`).
 *
 * @property refs List of `$ref` or type name references.
 * @property schemas List of inline [SchemaProperty] subschemas.
 */
data class CompositionTagResult(val refs: List<String>, val schemas: List<SchemaProperty>)

/**
 * Extracts OpenAPI schema metadata, constraints, and JSON Schema keywords from Kotlin KDoc blocks.
 */
object SchemaDocTagParser {
  private val NUMBER_REGEX = Regex("^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$")
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
   * Extracts free-form description text (non-tag lines) from [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @return The description string or `null`.
   */
  fun extractDoc(element: org.jetbrains.kotlin.psi.KtDeclaration): String? {
    val docComment = element.docComment ?: return null

    return docComment.text
        .split("\n")
        .map { cleanKDocLine(it) }
        .filter { it.isNotEmpty() && !it.startsWith("@") }
        .joinToString(" ")
        .ifEmpty { null }
  }

  /**
   * Extracts an [domain.ExternalDocumentation] object from a `@see` tag in [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @return The parsed [domain.ExternalDocumentation] or `null`.
   */
  fun extractExternalDocs(
      element: org.jetbrains.kotlin.psi.KtDeclaration
  ): domain.ExternalDocumentation? {
    val docComment = element.docComment ?: return null

    val seeLine =
        docComment.text.split("\n").map { cleanKDocLine(it) }.find { it.startsWith("@see") }
            ?: return null

    val content = seeLine.removePrefix("@see").trim()
    if (content.isEmpty()) return null
    val parts = content.split(" ", limit = 2)
    val url = parts[0]
    val desc = parts.getOrNull(1)

    return domain.ExternalDocumentation(url = url, description = desc)
  }

  /**
   * Extracts a [domain.Discriminator] object from `@discriminator*` tags in [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @return The parsed [domain.Discriminator] or `null`.
   */
  fun extractDiscriminator(element: org.jetbrains.kotlin.psi.KtDeclaration): domain.Discriminator? {
    val propertyName = extractTagValue(element, "discriminator") ?: return null
    val mappingLiteral = extractTagLiteral(element, "discriminatorMapping")
    val mappingObj = mappingLiteral as? OpenApiObject
    val mapping =
        if (mappingObj != null) {
          mappingObj.properties.entries.associate { (k, v) ->
            k to (v.stringOrNull() ?: v.toUntyped().toString())
          }
        } else {
          emptyMap()
        }
    val defaultMapping = extractTagValue(element, "discriminatorDefault")

    return domain.Discriminator(
        propertyName = propertyName, mapping = mapping, defaultMapping = defaultMapping)
  }

  /**
   * Extracts an [Xml] metadata object from `@xml*` tags in [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @return The parsed [Xml] or `null`.
   */
  fun extractXml(element: org.jetbrains.kotlin.psi.KtDeclaration): Xml? {
    val name = extractTagValue(element, "xmlName")
    val namespace = extractTagValue(element, "xmlNamespace")
    val prefix = extractTagValue(element, "xmlPrefix")
    val nodeType = extractTagValue(element, "xmlNodeType")
    val attribute = extractTagBoolean(element, "xmlAttribute")
    val wrapped = extractTagBoolean(element, "xmlWrapped")

    if (listOf(name, namespace, prefix, nodeType, attribute, wrapped).all { it == null }) {
      return null
    }

    return Xml(
        name = name,
        namespace = namespace,
        prefix = prefix,
        nodeType = nodeType,
        attribute = attribute ?: false,
        wrapped = wrapped ?: false)
  }

  /**
   * Extracts custom vocabulary keywords from the `@keywords` KDoc tag on [element].
   *
   * @param element The PSI declaration to inspect.
   * @return Map of custom keyword names to parsed values.
   */
  fun extractCustomKeywords(
      element: org.jetbrains.kotlin.psi.KtDeclaration
  ): Map<String, OpenApiValue> {
    val literal = extractTagLiteral(element, "keywords") ?: return emptyMap()
    val obj = literal as? OpenApiObject ?: return emptyMap()
    return obj.properties
  }

  /**
   * Extracts a composition tag (`@oneOf`, `@anyOf`, `@allOf`) from [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @param tag Tag name without `@`.
   * @return Parsed [CompositionTagResult].
   */
  fun extractCompositionTag(
      element: org.jetbrains.kotlin.psi.KtDeclaration,
      tag: String
  ): CompositionTagResult {
    val raw = extractTagValue(element, tag) ?: return CompositionTagResult(emptyList(), emptyList())
    val node = openapi.parseJsonElementOrNull(raw)
    val arr = node.asArray() ?: return CompositionTagResult(emptyList(), emptyList())

    val refs = mutableListOf<String>()
    val schemas = mutableListOf<SchemaProperty>()

    arr.forEach { item ->
      when {
        item.isTextual -> refs.add(item.textValue()!!)
        item.isObject && item.has("\$ref") -> {
          val ref = item.text("\$ref")
          if (!ref.isNullOrBlank()) refs.add(ref)
        }
        else -> schemas.add(parseSchemaPropertyNode(item))
      }
    }

    return CompositionTagResult(refs, schemas)
  }

  /**
   * Extracts a single [SchemaProperty] from a JSON-encoded KDoc tag on [element].
   *
   * @param element The PSI declaration to inspect.
   * @param tag Tag name without `@`.
   * @return The parsed [SchemaProperty] or `null`.
   */
  fun extractSchemaTag(
      element: org.jetbrains.kotlin.psi.KtDeclaration,
      tag: String
  ): SchemaProperty? {
    val raw = extractTagValue(element, tag) ?: return null
    val node = openapi.parseJsonElementOrNull(raw) ?: return null
    return parseSchemaPropertyNode(node)
  }

  /**
   * Extracts a list of [SchemaProperty] items from a JSON-encoded KDoc tag on [element].
   *
   * @param element The PSI declaration to inspect.
   * @param tag Tag name without `@`.
   * @return List of parsed [SchemaProperty] objects.
   */
  fun extractSchemaListTag(
      element: org.jetbrains.kotlin.psi.KtDeclaration,
      tag: String
  ): List<SchemaProperty> {
    val raw = extractTagValue(element, tag) ?: return emptyList()
    val node = openapi.parseJsonElementOrNull(raw)
    val arr = node.asArray() ?: return emptyList()
    return arr.map { parseSchemaPropertyNode(it) }
  }

  /**
   * Extracts a map of [SchemaProperty] values from a JSON-encoded KDoc tag on [element].
   *
   * @param element The PSI declaration to inspect.
   * @param tag Tag name without `@`.
   * @return Map of key to parsed [SchemaProperty].
   */
  fun extractSchemaMapTag(
      element: org.jetbrains.kotlin.psi.KtDeclaration,
      tag: String
  ): Map<String, SchemaProperty> {
    val raw = extractTagValue(element, tag) ?: return emptyMap()
    val node = openapi.parseJsonElementOrNull(raw)
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (k, v) -> k to parseSchemaPropertyNode(v) }
  }

  /**
   * Extracts a map of string lists from a JSON-encoded KDoc tag on [element].
   *
   * @param element The PSI declaration to inspect.
   * @param tag Tag name without `@`.
   * @return Map of key to string lists.
   */
  fun extractStringListMapTag(
      element: org.jetbrains.kotlin.psi.KtDeclaration,
      tag: String
  ): Map<String, List<String>> {
    val raw = extractTagValue(element, tag) ?: return emptyMap()
    val node = openapi.parseJsonElementOrNull(raw)
    return parseDependentRequiredNode(node)
  }

  private fun parseSchemaPropertyNode(node: JsonNode): SchemaProperty {
    if (node.isBoolean) {
      return SchemaProperty(booleanSchema = node.booleanValue())
    }
    val obj = node.asObject() ?: return SchemaProperty()
    val ref = obj.text("\$ref")
    if (ref != null) return SchemaProperty(ref = ref)
    val dynamicRef = obj.text("\$dynamicRef")
    if (dynamicRef != null) return SchemaProperty(dynamicRef = dynamicRef)
    val customKeywords = parseCustomKeywordsNode(obj)

    return SchemaProperty(
        booleanSchema = null,
        types = parseTypesNode(obj),
        schemaId = obj.text("\$id"),
        schemaDialect = obj.text("\$schema"),
        anchor = obj.text("\$anchor"),
        dynamicAnchor = obj.text("\$dynamicAnchor"),
        comment = obj.text("\$comment"),
        format = obj.text("format"),
        contentMediaType = obj.text("contentMediaType"),
        contentEncoding = obj.text("contentEncoding"),
        minLength = obj.int("minLength"),
        maxLength = obj.int("maxLength"),
        pattern = obj.text("pattern"),
        minimum = obj.double("minimum"),
        maximum = obj.double("maximum"),
        multipleOf = obj.double("multipleOf"),
        exclusiveMinimum = obj.double("exclusiveMinimum"),
        exclusiveMaximum = obj.double("exclusiveMaximum"),
        minItems = obj.int("minItems"),
        maxItems = obj.int("maxItems"),
        uniqueItems = obj.boolean("uniqueItems"),
        minProperties = obj.int("minProperties"),
        maxProperties = obj.int("maxProperties"),
        items = obj.get("items")?.let { parseSchemaPropertyNode(it) },
        prefixItems = parseSchemaPropertyListNode(obj.get("prefixItems")),
        contains = obj.get("contains")?.let { parseSchemaPropertyNode(it) },
        minContains = obj.int("minContains"),
        maxContains = obj.int("maxContains"),
        properties = parseSchemaPropertiesNode(obj.get("properties")),
        required = obj.get("required").asArray()?.mapNotNull { it.textValue() } ?: emptyList(),
        additionalProperties = parseAdditionalPropertiesNode(obj.get("additionalProperties")),
        defs = parseSchemaPropertiesNode(obj.get("\$defs")),
        patternProperties = parseSchemaPropertiesNode(obj.get("patternProperties")),
        propertyNames = obj.get("propertyNames")?.let { parseSchemaPropertyNode(it) },
        dependentRequired = parseDependentRequiredNode(obj.get("dependentRequired")),
        dependentSchemas = parseSchemaPropertiesNode(obj.get("dependentSchemas")),
        description = obj.text("description"),
        title = obj.text("title"),
        defaultValue = obj.get("default")?.let { nodeToValue(it) },
        constValue = obj.get("const")?.let { nodeToValue(it) },
        deprecated = obj.boolean("deprecated") ?: false,
        readOnly = obj.boolean("readOnly") ?: false,
        writeOnly = obj.boolean("writeOnly") ?: false,
        externalDocs = parseExternalDocsNode(obj.get("externalDocs")),
        discriminator = parseDiscriminatorNode(obj.get("discriminator")),
        enumValues = obj.get("enum").asArray()?.map { nodeToValue(it) },
        oneOf = parseSchemaPropertyListNode(obj.get("oneOf")),
        anyOf = parseSchemaPropertyListNode(obj.get("anyOf")),
        allOf = parseSchemaPropertyListNode(obj.get("allOf")),
        not = obj.get("not")?.let { parseSchemaPropertyNode(it) },
        ifSchema = obj.get("if")?.let { parseSchemaPropertyNode(it) },
        thenSchema = obj.get("then")?.let { parseSchemaPropertyNode(it) },
        elseSchema = obj.get("else")?.let { parseSchemaPropertyNode(it) },
        example = obj.get("example")?.let { nodeToValue(it) },
        examples = parseSchemaExamplesListNode(obj.get("examples")),
        xml = parseXmlNode(obj.get("xml")),
        unevaluatedProperties =
            obj.get("unevaluatedProperties")?.let { parseSchemaPropertyNode(it) },
        unevaluatedItems = obj.get("unevaluatedItems")?.let { parseSchemaPropertyNode(it) },
        contentSchema = obj.get("contentSchema")?.let { parseSchemaPropertyNode(it) },
        customKeywords = customKeywords)
  }

  private fun parseCustomKeywordsNode(obj: JsonNode): Map<String, OpenApiValue> {
    return obj.fields()
        .asSequence()
        .filter { (key, _) -> !key.startsWith("x-") && key !in schemaKnownKeys }
        .associate { (key, value) -> key to nodeToValue(value) }
  }

  private fun parseSchemaPropertiesNode(node: JsonNode?): Map<String, SchemaProperty> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (name, raw) ->
      name to parseSchemaPropertyNode(raw)
    }
  }

  private fun parseSchemaPropertyListNode(node: JsonNode?): List<SchemaProperty> {
    val array = node.asArray() ?: return emptyList()
    return array.map { parseSchemaPropertyNode(it) }
  }

  private fun parseAdditionalPropertiesNode(node: JsonNode?): SchemaProperty? {
    return when {
      node == null -> null
      node.isBoolean -> SchemaProperty(booleanSchema = node.booleanValue())
      node.isObject -> parseSchemaPropertyNode(node)
      else -> null
    }
  }

  private fun parseDependentRequiredNode(node: JsonNode?): Map<String, List<String>> {
    val obj = node.asObject() ?: return emptyMap()
    return obj.fields().asSequence().associate { (key, raw) ->
      val arr = raw.asArray()
      val list = if (arr != null) arr.mapNotNull { it.textValue() } else emptyList()
      key to list
    }
  }

  private fun parseTypesNode(obj: JsonNode): Set<String> {
    val node = obj.get("type") ?: return emptySet()
    return when {
      node.isTextual -> setOf(node.asText()!!)
      node.isArray -> node.elements().mapNotNull { it.textValue() }.toSet()
      else -> emptySet()
    }
  }

  private fun parseExternalDocsNode(node: JsonNode?): domain.ExternalDocumentation? {
    val obj = node.asObject() ?: return null
    val url = obj.text("url") ?: return null
    return domain.ExternalDocumentation(url = url, description = obj.text("description"))
  }

  private fun parseDiscriminatorNode(node: JsonNode?): domain.Discriminator? {
    val obj = node.asObject() ?: return null
    val mappingObj = obj.get("mapping").asObject()
    val mapping =
        if (mappingObj != null) {
          mappingObj.fields().asSequence().associate { (k, v) ->
            k to (v.textValue() ?: v.toString())
          }
        } else {
          emptyMap()
        }
    return domain.Discriminator(
        propertyName = obj.text("propertyName") ?: "type",
        mapping = mapping,
        defaultMapping = obj.text("defaultMapping"))
  }

  private fun parseXmlNode(node: JsonNode?): Xml? {
    val obj = node.asObject() ?: return null
    return Xml(
        name = obj.text("name"),
        namespace = obj.text("namespace"),
        prefix = obj.text("prefix"),
        nodeType = obj.text("nodeType"),
        attribute = obj.boolean("attribute") ?: false,
        wrapped = obj.boolean("wrapped") ?: false)
  }

  private fun parseSchemaExamplesListNode(node: JsonNode?): List<OpenApiValue>? {
    val arr = node.asArray() ?: return null
    return arr.map { nodeToValue(it) }
  }

  /**
   * Extracts `@example` tags from [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @return Pair of `(singleExample, namedExamplesMap)`.
   */
  fun extractExamples(
      element: org.jetbrains.kotlin.psi.KtDeclaration
  ): Pair<OpenApiValue?, Map<String, OpenApiValue>?> {
    val docComment = element.docComment ?: return Pair(null, null)

    val exampleLines =
        docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .filter { it.startsWith("@example") && !it.startsWith("@examples") }
            .map { it.removePrefix("@example").trim() }

    if (exampleLines.isEmpty()) return Pair(null, null)

    var simpleExample: OpenApiValue? = null
    val mapExamples = mutableMapOf<String, OpenApiValue>()

    exampleLines.forEach { line ->
      val firstColon = line.indexOf(':')
      if (firstColon > 0) {
        val potentialKey = line.substring(0, firstColon).trim()
        if (potentialKey.matches(Regex("[a-zA-Z0-9_]+"))) {
          val value = line.substring(firstColon + 1).trim()
          mapExamples[potentialKey] = parseDocLiteral(value)
          return@forEach
        }
      }
      simpleExample = parseDocLiteral(line)
    }

    val finalMap = if (mapExamples.isNotEmpty()) mapExamples else null
    return Pair(simpleExample, finalMap)
  }

  /**
   * Extracts the JSON array of examples from the `@examples` KDoc tag on [element].
   *
   * @param element The PSI declaration to inspect.
   * @return List of example values or `null`.
   */
  fun extractExamplesList(element: org.jetbrains.kotlin.psi.KtDeclaration): List<OpenApiValue>? {
    val raw = extractTagValue(element, "examples") ?: return null
    val parsed = parseDocLiteral(raw)
    return when (parsed) {
      is OpenApiArray -> parsed.elements
      else -> null
    }
  }

  /**
   * Extracts `@enum` tag values from [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @return List of enumerated literal values or `null`.
   */
  fun extractEnumValues(element: org.jetbrains.kotlin.psi.KtDeclaration): List<OpenApiValue>? {
    val docComment = element.docComment ?: return null
    val values =
        docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .filter { it == "@enum" || it.startsWith("@enum ") }
            .map { it.removePrefix("@enum").trim() }
            .filter { it.isNotEmpty() }
            .map { parseDocLiteral(it) }
    return if (values.isEmpty()) null else values
  }

  /**
   * Extracts and parses a literal value from `@<tag>` in [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @param tag Tag name without `@`.
   * @return The parsed [OpenApiValue] or `null`.
   */
  fun extractTagLiteral(
      element: org.jetbrains.kotlin.psi.KtDeclaration,
      tag: String
  ): OpenApiValue? {
    val raw = extractTagValue(element, tag) ?: return null
    return parseDocLiteral(raw)
  }

  /**
   * Extracts the raw string value following `@<tag>` in [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @param tag Tag name without `@`.
   * @return The trimmed string value or `null`.
   */
  fun extractTagValue(element: org.jetbrains.kotlin.psi.KtDeclaration, tag: String): String? {
    val docComment = element.docComment ?: return null

    val line =
        docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .find { it == "@$tag" || it.startsWith("@$tag ") } ?: return null

    val value = line.removePrefix("@$tag").trim()
    return value.ifEmpty { null }
  }

  private fun parseDocLiteral(raw: String): OpenApiValue {
    val trimmed = raw.trim()
    when (trimmed.lowercase()) {
      "null" -> return OpenApiNull
      "true" -> return OpenApiBoolean(true)
      "false" -> return OpenApiBoolean(false)
    }

    if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
      val parsed = openapi.parseJsonElementOrNull(trimmed)
      if (parsed != null) return nodeToValue(parsed)
    }

    if (NUMBER_REGEX.matches(trimmed)) {
      val longVal = trimmed.toLongOrNull()
      if (longVal != null) return OpenApiInt(longVal)
      return OpenApiNumber(trimmed.toDouble(), trimmed)
    }

    return OpenApiString(trimmed)
  }

  private fun nodeToValue(node: JsonNode): OpenApiValue {
    return OpenApiValues.fromJsonElement(node)
  }

  /**
   * Extracts an integer value from `@<tag>` in [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @param tag Tag name without `@`.
   * @return The parsed integer or `null`.
   */
  fun extractTagInt(element: org.jetbrains.kotlin.psi.KtDeclaration, tag: String): Int? {
    return extractTagValue(element, tag)?.toIntOrNull()
  }

  /**
   * Extracts a double value from `@<tag>` in [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @param tag Tag name without `@`.
   * @return The parsed double or `null`.
   */
  fun extractTagDouble(element: org.jetbrains.kotlin.psi.KtDeclaration, tag: String): Double? {
    return extractTagValue(element, tag)?.toDoubleOrNull()
  }

  /**
   * Extracts a boolean value (or flag presence) from `@<tag>` in [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @param tag Tag name without `@`.
   * @return The parsed boolean or `null`.
   */
  fun extractTagBoolean(element: org.jetbrains.kotlin.psi.KtDeclaration, tag: String): Boolean? {
    val raw = extractTagValue(element, tag)
    return when {
      raw == null -> if (hasTag(element, tag)) true else null
      raw.equals("true", ignoreCase = true) -> true
      raw.equals("false", ignoreCase = true) -> false
      else -> null
    }
  }

  /**
   * Checks whether `@<tag>` is present in [element] KDoc.
   *
   * @param element The PSI declaration to inspect.
   * @param tag Tag name without `@`.
   * @return `true` if the tag is present.
   */
  fun hasTag(element: org.jetbrains.kotlin.psi.KtDeclaration, tag: String): Boolean {
    val docComment = element.docComment ?: return false
    val normalized = docComment.text.split("\n").map { cleanKDocLine(it) }

    return normalized.any { it == "@$tag" || it.startsWith("@$tag ") }
  }

  /**
   * Checks whether [element] has an annotation with short name [annotationName].
   *
   * @param element The PSI modifier list owner.
   * @param annotationName Short name of the annotation.
   * @return `true` if the annotation is present.
   */
  fun hasAnnotation(element: KtModifierListOwner, annotationName: String): Boolean {
    return element.annotationEntries.any { it.shortName!!.asString() == annotationName }
  }

  private fun cleanKDocLine(line: String): String {
    return line.trim().replace("/**", "").replace("*/", "").replaceFirst("*", "").trim()
  }
}
