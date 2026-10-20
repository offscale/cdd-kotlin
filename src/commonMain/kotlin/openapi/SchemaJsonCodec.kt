/**
 * Shared, strongly-typed serializer converting [domain.SchemaDefinition] and
 * [domain.SchemaProperty] domain models into [domain.OpenApiValue] (`OpenApiObject` or
 * `OpenApiBoolean`) ASTs without intermediate `Map<String, Any?>` allocations.
 */
package openapi

import domain.Discriminator
import domain.ExternalDocumentation
import domain.OpenApiArray
import domain.OpenApiBoolean
import domain.OpenApiInt
import domain.OpenApiNumber
import domain.OpenApiObject
import domain.OpenApiString
import domain.OpenApiValue
import domain.SchemaCore
import domain.SchemaDefinition
import domain.SchemaProperty
import domain.Xml

/**
 * Strongly-typed codec for converting OpenAPI 3.2 and JSON Schema Draft 2020-12 schema domain
 * objects into [OpenApiValue] representations.
 */
object SchemaJsonCodec {
  /**
   * Converts a named [SchemaDefinition] into an [OpenApiValue] (`OpenApiBoolean` or
   * `OpenApiObject`).
   *
   * @param schema The [SchemaDefinition] to convert.
   * @param refBase Optional `$self` base URI for resolving short composition type names.
   * @return The serialized [OpenApiValue] AST.
   */
  fun schemaDefinitionToOpenApiValue(schema: SchemaDefinition, refBase: String?): OpenApiValue {
    schema.booleanSchema?.let {
      return OpenApiBoolean(it)
    }
    val map = LinkedHashMap<String, OpenApiValue>()
    val typeFallback = if (schema.typeExplicit) schema.type else null
    populateSchemaCore(map, schema, typeFallback)
    map.putListIfNotEmpty(
        "oneOf", schemaCompositionToValues(schema.oneOf, schema.oneOfSchemas, refBase))
    map.putListIfNotEmpty(
        "anyOf", schemaCompositionToValues(schema.anyOf, schema.anyOfSchemas, refBase))
    map.putListIfNotEmpty(
        "allOf", schemaCompositionToValues(schema.allOf, schema.allOfSchemas, refBase))
    if (schema.examplesList != null) {
      map["examples"] = OpenApiArray(schema.examplesList)
    } else if (schema.examples != null) {
      map["examples"] = OpenApiArray(schema.examples.values.toList())
    }
    putCustomKeywords(map, schema.customKeywords)
    putExtensions(map, schema.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts a [SchemaProperty] into an [OpenApiValue] (`OpenApiBoolean` or `OpenApiObject`).
   *
   * @param schema The [SchemaProperty] to convert.
   * @return The serialized [OpenApiValue] AST.
   */
  fun schemaPropertyToOpenApiValue(schema: SchemaProperty): OpenApiValue {
    schema.booleanSchema?.let {
      return OpenApiBoolean(it)
    }
    val map = LinkedHashMap<String, OpenApiValue>()
    populateSchemaCore(map, schema, null)
    map.putListIfNotEmpty("oneOf", schema.oneOf.map { schemaPropertyToOpenApiValue(it) })
    map.putListIfNotEmpty("anyOf", schema.anyOf.map { schemaPropertyToOpenApiValue(it) })
    map.putListIfNotEmpty("allOf", schema.allOf.map { schemaPropertyToOpenApiValue(it) })
    schema.examples?.let { map["examples"] = OpenApiArray(it) }
    putCustomKeywords(map, schema.customKeywords)
    putExtensions(map, schema.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts a list of composition `$ref` tokens and inline [SchemaProperty] items into a list of
   * [OpenApiValue] elements.
   *
   * @param refs List of `$ref` or schema names.
   * @param inlines List of inline [SchemaProperty] items.
   * @param refBase Optional `$self` base URI.
   * @return Combined list of [OpenApiValue] items.
   */
  fun schemaCompositionToValues(
      refs: List<String>,
      inlines: List<SchemaProperty>,
      refBase: String? = null
  ): List<OpenApiValue> {
    if (refs.isEmpty() && inlines.isEmpty()) return emptyList()
    val entries = mutableListOf<OpenApiValue>()
    refs.forEach { token ->
      val ref = if (looksLikeRef(token)) token else componentSchemaRef(refBase, token)
      entries.add(OpenApiObject(mapOf("\$ref" to OpenApiString(ref))))
    }
    entries.addAll(inlines.map { schemaPropertyToOpenApiValue(it) })
    return entries
  }

  /**
   * Converts an [ExternalDocumentation] object into an [OpenApiObject].
   *
   * @param docs The [ExternalDocumentation] to convert.
   * @return The [OpenApiObject] representation.
   */
  fun externalDocsToOpenApiObject(docs: ExternalDocumentation): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["url"] = OpenApiString(docs.url)
    docs.description?.let { map["description"] = OpenApiString(it) }
    putExtensions(map, docs.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts a [Discriminator] object into an [OpenApiObject].
   *
   * @param discriminator The [Discriminator] to convert.
   * @return The [OpenApiObject] representation.
   */
  fun discriminatorToOpenApiObject(discriminator: Discriminator): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    map["propertyName"] = OpenApiString(discriminator.propertyName)
    if (discriminator.mapping.isNotEmpty()) {
      map["mapping"] = OpenApiObject(discriminator.mapping.mapValues { (_, v) -> OpenApiString(v) })
    }
    discriminator.defaultMapping?.let { map["defaultMapping"] = OpenApiString(it) }
    putExtensions(map, discriminator.extensions)
    return OpenApiObject(map)
  }

  /**
   * Converts an [Xml] metadata object into an [OpenApiObject].
   *
   * @param xml The [Xml] metadata to convert.
   * @return The [OpenApiObject] representation.
   */
  fun xmlToOpenApiObject(xml: Xml): OpenApiObject {
    val map = LinkedHashMap<String, OpenApiValue>()
    xml.name?.let { map["name"] = OpenApiString(it) }
    xml.namespace?.let { map["namespace"] = OpenApiString(it) }
    xml.prefix?.let { map["prefix"] = OpenApiString(it) }
    xml.nodeType?.let { map["nodeType"] = OpenApiString(it) }
    if (xml.attribute) map["attribute"] = OpenApiBoolean(true)
    if (xml.wrapped) map["wrapped"] = OpenApiBoolean(true)
    putExtensions(map, xml.extensions)
    return OpenApiObject(map)
  }

  private fun populateSchemaCore(
      map: MutableMap<String, OpenApiValue>,
      schema: SchemaCore,
      typeFallback: String?
  ) {
    schema.ref?.let { map["\$ref"] = OpenApiString(it) }
    schema.dynamicRef?.let { map["\$dynamicRef"] = OpenApiString(it) }
    typeValue(schema.types, typeFallback)?.let { map["type"] = it }

    map.putStringIfNotNull("\$id", schema.schemaId)
    map.putStringIfNotNull("\$schema", schema.schemaDialect)
    map.putStringIfNotNull("\$anchor", schema.anchor)
    map.putStringIfNotNull("\$dynamicAnchor", schema.dynamicAnchor)
    map.putStringIfNotNull("\$comment", schema.comment)
    if (schema.defs.isNotEmpty()) {
      map["\$defs"] =
          OpenApiObject(schema.defs.mapValues { (_, v) -> schemaPropertyToOpenApiValue(v) })
    }
    map.putStringIfNotNull("format", schema.format)
    map.putStringIfNotNull("contentMediaType", schema.contentMediaType)
    map.putStringIfNotNull("contentEncoding", schema.contentEncoding)
    map.putIntIfNotNull("minLength", schema.minLength)
    map.putIntIfNotNull("maxLength", schema.maxLength)
    map.putStringIfNotNull("pattern", schema.pattern)
    map.putDoubleIfNotNull("minimum", schema.minimum)
    map.putDoubleIfNotNull("maximum", schema.maximum)
    map.putDoubleIfNotNull("multipleOf", schema.multipleOf)
    map.putDoubleIfNotNull("exclusiveMinimum", schema.exclusiveMinimum)
    map.putDoubleIfNotNull("exclusiveMaximum", schema.exclusiveMaximum)
    map.putIntIfNotNull("minItems", schema.minItems)
    map.putIntIfNotNull("maxItems", schema.maxItems)
    map.putBooleanIfNotNull("uniqueItems", schema.uniqueItems)
    map.putIntIfNotNull("minProperties", schema.minProperties)
    map.putIntIfNotNull("maxProperties", schema.maxProperties)

    schema.items?.let { map["items"] = schemaPropertyToOpenApiValue(it) }
    map.putListIfNotEmpty(
        "prefixItems", schema.prefixItems.map { schemaPropertyToOpenApiValue(it) })
    schema.contains?.let { map["contains"] = schemaPropertyToOpenApiValue(it) }
    map.putIntIfNotNull("minContains", schema.minContains)
    map.putIntIfNotNull("maxContains", schema.maxContains)
    if (schema.properties.isNotEmpty()) {
      map["properties"] =
          OpenApiObject(schema.properties.mapValues { (_, v) -> schemaPropertyToOpenApiValue(v) })
    }
    if (schema.patternProperties.isNotEmpty()) {
      map["patternProperties"] =
          OpenApiObject(
              schema.patternProperties.mapValues { (_, v) -> schemaPropertyToOpenApiValue(v) })
    }
    schema.propertyNames?.let { map["propertyNames"] = schemaPropertyToOpenApiValue(it) }
    schema.additionalProperties?.let {
      map["additionalProperties"] = schemaPropertyToOpenApiValue(it)
    }
    if (schema.required.isNotEmpty()) {
      map["required"] = OpenApiArray(schema.required.map { OpenApiString(it) })
    }
    if (schema.dependentRequired.isNotEmpty()) {
      map["dependentRequired"] =
          OpenApiObject(
              schema.dependentRequired.mapValues { (_, list) ->
                OpenApiArray(list.map { OpenApiString(it) })
              })
    }
    if (schema.dependentSchemas.isNotEmpty()) {
      map["dependentSchemas"] =
          OpenApiObject(
              schema.dependentSchemas.mapValues { (_, v) -> schemaPropertyToOpenApiValue(v) })
    }
    schema.enumValues?.let { map["enum"] = OpenApiArray(it) }
    map.putStringIfNotNull("description", schema.description)
    map.putStringIfNotNull("title", schema.title)
    schema.defaultValue?.let { map["default"] = it }
    schema.constValue?.let { map["const"] = it }
    if (schema.deprecated) map["deprecated"] = OpenApiBoolean(true)
    if (schema.readOnly) map["readOnly"] = OpenApiBoolean(true)
    if (schema.writeOnly) map["writeOnly"] = OpenApiBoolean(true)
    schema.externalDocs?.let { map["externalDocs"] = externalDocsToOpenApiObject(it) }
    schema.discriminator?.let { map["discriminator"] = discriminatorToOpenApiObject(it) }
    schema.xml?.let { xml ->
      val xmlObj = xmlToOpenApiObject(xml)
      if (xmlObj.properties.isNotEmpty()) {
        map["xml"] = xmlObj
      }
    }
    schema.unevaluatedProperties?.let {
      map["unevaluatedProperties"] = schemaPropertyToOpenApiValue(it)
    }
    schema.unevaluatedItems?.let { map["unevaluatedItems"] = schemaPropertyToOpenApiValue(it) }
    schema.contentSchema?.let { map["contentSchema"] = schemaPropertyToOpenApiValue(it) }
    schema.not?.let { map["not"] = schemaPropertyToOpenApiValue(it) }
    schema.ifSchema?.let { map["if"] = schemaPropertyToOpenApiValue(it) }
    schema.thenSchema?.let { map["then"] = schemaPropertyToOpenApiValue(it) }
    schema.elseSchema?.let { map["else"] = schemaPropertyToOpenApiValue(it) }
    schema.example?.let { map["example"] = it }
  }

  private fun typeValue(types: Set<String>, fallback: String?): OpenApiValue? {
    if (types.isNotEmpty()) {
      val list = types.toList().sorted()
      return if (list.size == 1) {
        OpenApiString(list.first())
      } else {
        OpenApiArray(list.map { OpenApiString(it) })
      }
    }
    return fallback?.let { OpenApiString(it) }
  }

  private fun looksLikeRef(token: String): Boolean {
    return listOf("#", "./", "/").any { token.contains(it) } ||
        listOf(".json", ".yaml").any { token.endsWith(it) }
  }

  private fun componentSchemaRef(base: String?, name: String): String {
    val encoded = name.replace("~", "~0").replace("/", "~1")
    val suffix = "#/components/schemas/$encoded"
    val prefix = base.orEmpty().trimEnd('#').trimEnd()
    return if (prefix.isEmpty()) suffix else "$prefix$suffix"
  }

  private fun MutableMap<String, OpenApiValue>.putStringIfNotNull(key: String, value: String?) {
    if (value != null) this[key] = OpenApiString(value)
  }

  private fun MutableMap<String, OpenApiValue>.putIntIfNotNull(key: String, value: Int?) {
    if (value != null) this[key] = OpenApiInt(value.toLong())
  }

  private fun MutableMap<String, OpenApiValue>.putDoubleIfNotNull(key: String, value: Double?) {
    if (value != null) this[key] = OpenApiNumber(value)
  }

  private fun MutableMap<String, OpenApiValue>.putBooleanIfNotNull(key: String, value: Boolean?) {
    if (value != null) this[key] = OpenApiBoolean(value)
  }

  private fun MutableMap<String, OpenApiValue>.putListIfNotEmpty(
      key: String,
      value: List<OpenApiValue>
  ) {
    if (value.isNotEmpty()) this[key] = OpenApiArray(value)
  }

  private fun putCustomKeywords(
      map: MutableMap<String, OpenApiValue>,
      custom: Map<String, OpenApiValue>
  ) {
    custom.forEach { (key, value) ->
      if (!key.startsWith("x-") && !map.containsKey(key)) {
        map[key] = value
      }
    }
  }

  private fun putExtensions(
      map: MutableMap<String, OpenApiValue>,
      extensions: Map<String, OpenApiValue>
  ) {
    extensions.forEach { (key, value) -> map[key] = value }
  }
}
