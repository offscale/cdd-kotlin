/** Renders OpenAPI [SchemaDefinition] and [SchemaProperty] metadata into KDoc tag blocks. */
package psi.dto

import domain.OpenApiArray
import domain.OpenApiBoolean
import domain.OpenApiInt
import domain.OpenApiNull
import domain.OpenApiNumber
import domain.OpenApiObject
import domain.OpenApiString
import domain.OpenApiValue
import domain.OpenApiValues
import domain.SchemaDefinition
import domain.SchemaProperty
import openapi.SchemaJsonCodec

/**
 * Encodes OpenAPI [SchemaDefinition] and [SchemaProperty] constraints, annotations, and metadata
 * into structured KDoc comment strings for Kotlin DTO generation.
 */
object SchemaDocTagWriter {
  /**
   * Builds a class-level KDoc block for the given [SchemaDefinition].
   *
   * @param definition The schema definition to document.
   * @return The formatted KDoc string, or empty string if no metadata.
   */
  fun buildClassKDoc(definition: SchemaDefinition): String {
    val lines = mutableListOf<String>()

    if (!definition.description.isNullOrBlank()) {
      lines.add(definition.description)
    }

    if (definition.externalDocs != null) {
      val docs = definition.externalDocs
      val suffix = if (docs.description != null) " ${docs.description}" else ""
      lines.add("@see ${docs.url}$suffix")
    }

    if (definition.discriminator != null) {
      val disc = definition.discriminator
      lines.add("@discriminator ${disc.propertyName}")
      if (disc.mapping.isNotEmpty()) {
        val mappingObj = OpenApiObject(disc.mapping.mapValues { (_, v) -> OpenApiString(v) })
        lines.add("@discriminatorMapping ${OpenApiValues.encodeToString(mappingObj)}")
      }
      if (disc.defaultMapping != null) {
        lines.add("@discriminatorDefault ${disc.defaultMapping}")
      }
    }

    if (definition.example != null) lines.add("@example ${renderDocValue(definition.example)}")
    if (!definition.examplesList.isNullOrEmpty()) {
      lines.add("@examples ${renderExamplesList(definition.examplesList)}")
    }
    if (!definition.examples.isNullOrEmpty()) {
      definition.examples.forEach { (k, v) -> lines.add("@example $k: ${renderDocValue(v)}") }
    }
    if (!definition.enumValues.isNullOrEmpty()) {
      definition.enumValues.forEach { value -> lines.add("@enum ${renderEnumValue(value)}") }
    }

    if (!definition.title.isNullOrBlank()) lines.add("@title ${definition.title}")
    if (definition.defaultValue != null) {
      lines.add("@default ${renderDocValue(definition.defaultValue)}")
    }
    if (definition.constValue != null) {
      lines.add("@const ${renderDocValue(definition.constValue)}")
    }
    if (!definition.comment.isNullOrBlank()) lines.add("@comment ${definition.comment}")
    if (!definition.schemaId.isNullOrBlank()) lines.add("@schemaId ${definition.schemaId}")
    if (!definition.schemaDialect.isNullOrBlank()) {
      lines.add("@schemaDialect ${definition.schemaDialect}")
    }
    if (!definition.anchor.isNullOrBlank()) lines.add("@anchor ${definition.anchor}")
    if (!definition.dynamicAnchor.isNullOrBlank()) {
      lines.add("@dynamicAnchor ${definition.dynamicAnchor}")
    }
    if (!definition.dynamicRef.isNullOrBlank()) lines.add("@dynamicRef ${definition.dynamicRef}")
    if (definition.defs.isNotEmpty()) lines.add("@defs ${renderSchemaMap(definition.defs)}")

    if (definition.minLength != null) lines.add("@minLength ${definition.minLength}")
    if (definition.maxLength != null) lines.add("@maxLength ${definition.maxLength}")
    if (definition.pattern != null) lines.add("@pattern ${definition.pattern}")
    if (definition.minimum != null) lines.add("@minimum ${definition.minimum}")
    if (definition.maximum != null) lines.add("@maximum ${definition.maximum}")
    if (definition.multipleOf != null) lines.add("@multipleOf ${definition.multipleOf}")
    if (definition.exclusiveMinimum != null) {
      lines.add("@exclusiveMinimum ${definition.exclusiveMinimum}")
    }
    if (definition.exclusiveMaximum != null) {
      lines.add("@exclusiveMaximum ${definition.exclusiveMaximum}")
    }
    if (definition.minItems != null) lines.add("@minItems ${definition.minItems}")
    if (definition.maxItems != null) lines.add("@maxItems ${definition.maxItems}")
    if (definition.uniqueItems != null) lines.add("@uniqueItems ${definition.uniqueItems}")
    if (definition.minProperties != null) lines.add("@minProperties ${definition.minProperties}")
    if (definition.maxProperties != null) lines.add("@maxProperties ${definition.maxProperties}")
    if (definition.minContains != null) lines.add("@minContains ${definition.minContains}")
    if (definition.maxContains != null) lines.add("@maxContains ${definition.maxContains}")
    if (definition.contains != null) lines.add("@contains ${renderSchema(definition.contains)}")
    if (definition.prefixItems.isNotEmpty()) {
      lines.add("@prefixItems ${renderSchemaList(definition.prefixItems)}")
    }

    if (definition.patternProperties.isNotEmpty()) {
      lines.add("@patternProperties ${renderSchemaMap(definition.patternProperties)}")
    }
    if (definition.propertyNames != null) {
      lines.add("@propertyNames ${renderSchema(definition.propertyNames)}")
    }
    if (definition.dependentRequired.isNotEmpty()) {
      lines.add("@dependentRequired ${renderStringListMap(definition.dependentRequired)}")
    }
    if (definition.dependentSchemas.isNotEmpty()) {
      lines.add("@dependentSchemas ${renderSchemaMap(definition.dependentSchemas)}")
    }
    if (definition.unevaluatedProperties != null) {
      lines.add("@unevaluatedProperties ${renderSchema(definition.unevaluatedProperties)}")
    }
    if (definition.unevaluatedItems != null) {
      lines.add("@unevaluatedItems ${renderSchema(definition.unevaluatedItems)}")
    }
    if (definition.contentSchema != null) {
      lines.add("@contentSchema ${renderSchema(definition.contentSchema)}")
    }
    if (definition.additionalProperties != null) {
      lines.add("@additionalProperties ${renderSchema(definition.additionalProperties)}")
    }
    if (definition.oneOf.isNotEmpty() || definition.oneOfSchemas.isNotEmpty()) {
      lines.add("@oneOf ${renderSchemaComposition(definition.oneOf, definition.oneOfSchemas)}")
    }
    if (definition.anyOf.isNotEmpty() || definition.anyOfSchemas.isNotEmpty()) {
      lines.add("@anyOf ${renderSchemaComposition(definition.anyOf, definition.anyOfSchemas)}")
    }
    if (definition.allOf.isNotEmpty() || definition.allOfSchemas.isNotEmpty()) {
      lines.add("@allOf ${renderSchemaComposition(definition.allOf, definition.allOfSchemas)}")
    }
    if (definition.not != null) lines.add("@not ${renderSchema(definition.not)}")
    if (definition.ifSchema != null) lines.add("@if ${renderSchema(definition.ifSchema)}")
    if (definition.thenSchema != null) lines.add("@then ${renderSchema(definition.thenSchema)}")
    if (definition.elseSchema != null) lines.add("@else ${renderSchema(definition.elseSchema)}")
    if (definition.customKeywords.isNotEmpty()) {
      lines.add("@keywords ${renderCustomKeywords(definition.customKeywords)}")
    }

    if (definition.deprecated) lines.add("@deprecated")
    if (definition.readOnly) lines.add("@readOnly")
    if (definition.writeOnly) lines.add("@writeOnly")
    if (definition.contentMediaType != null) {
      lines.add("@contentMediaType ${definition.contentMediaType}")
    }
    if (definition.contentEncoding != null) {
      lines.add("@contentEncoding ${definition.contentEncoding}")
    }

    if (definition.xml != null) {
      val xml = definition.xml
      xml.name?.let { lines.add("@xmlName $it") }
      xml.namespace?.let { lines.add("@xmlNamespace $it") }
      xml.prefix?.let { lines.add("@xmlPrefix $it") }
      xml.nodeType?.let { lines.add("@xmlNodeType $it") }
      if (xml.attribute) lines.add("@xmlAttribute")
      if (xml.wrapped) lines.add("@xmlWrapped")
    }

    if (lines.isEmpty()) return ""
    val sb = StringBuilder()
    sb.appendLine("/**")
    lines.forEachIndexed { index, line ->
      if (index > 0) sb.appendLine(" *")
      sb.append(" * ").appendLine(line)
    }
    sb.appendLine(" */")
    return sb.toString()
  }

  /**
   * Builds a property-level KDoc block for the given [SchemaProperty].
   *
   * @param prop The schema property to document.
   * @return The formatted KDoc string, or empty string if no metadata.
   */
  fun buildPropertyKDoc(prop: SchemaProperty): String {
    val lines = mutableListOf<String>()

    if (!prop.description.isNullOrBlank()) lines.add(prop.description)
    if (!prop.title.isNullOrBlank()) lines.add("@title ${prop.title}")
    if (prop.externalDocs != null) {
      val docs = prop.externalDocs
      val suffix = if (docs.description != null) " ${docs.description}" else ""
      lines.add("@see ${docs.url}$suffix")
    }
    if (prop.discriminator != null) {
      val disc = prop.discriminator
      lines.add("@discriminator ${disc.propertyName}")
      if (disc.mapping.isNotEmpty()) {
        val mappingObj = OpenApiObject(disc.mapping.mapValues { (_, v) -> OpenApiString(v) })
        lines.add("@discriminatorMapping ${OpenApiValues.encodeToString(mappingObj)}")
      }
      if (prop.discriminator.defaultMapping != null) {
        lines.add("@discriminatorDefault ${disc.defaultMapping}")
      }
    }
    if (prop.defaultValue != null) lines.add("@default ${renderDocValue(prop.defaultValue)}")
    if (prop.constValue != null) lines.add("@const ${renderDocValue(prop.constValue)}")
    if (!prop.comment.isNullOrBlank()) lines.add("@comment ${prop.comment}")
    if (!prop.schemaId.isNullOrBlank()) lines.add("@schemaId ${prop.schemaId}")
    if (!prop.schemaDialect.isNullOrBlank()) lines.add("@schemaDialect ${prop.schemaDialect}")
    if (!prop.anchor.isNullOrBlank()) lines.add("@anchor ${prop.anchor}")
    if (!prop.dynamicAnchor.isNullOrBlank()) lines.add("@dynamicAnchor ${prop.dynamicAnchor}")
    if (!prop.dynamicRef.isNullOrBlank()) lines.add("@dynamicRef ${prop.dynamicRef}")
    if (prop.defs.isNotEmpty()) lines.add("@defs ${renderSchemaMap(prop.defs)}")

    if (prop.minLength != null) lines.add("@minLength ${prop.minLength}")
    if (prop.maxLength != null) lines.add("@maxLength ${prop.maxLength}")
    if (prop.pattern != null) lines.add("@pattern ${prop.pattern}")
    if (prop.minimum != null) lines.add("@minimum ${prop.minimum}")
    if (prop.maximum != null) lines.add("@maximum ${prop.maximum}")
    if (prop.multipleOf != null) lines.add("@multipleOf ${prop.multipleOf}")
    if (prop.exclusiveMinimum != null) lines.add("@exclusiveMinimum ${prop.exclusiveMinimum}")
    if (prop.exclusiveMaximum != null) lines.add("@exclusiveMaximum ${prop.exclusiveMaximum}")
    if (prop.minItems != null) lines.add("@minItems ${prop.minItems}")
    if (prop.maxItems != null) lines.add("@maxItems ${prop.maxItems}")
    if (prop.uniqueItems != null) lines.add("@uniqueItems ${prop.uniqueItems}")
    if (prop.minProperties != null) lines.add("@minProperties ${prop.minProperties}")
    if (prop.maxProperties != null) lines.add("@maxProperties ${prop.maxProperties}")
    if (prop.minContains != null) lines.add("@minContains ${prop.minContains}")
    if (prop.maxContains != null) lines.add("@maxContains ${prop.maxContains}")
    if (prop.contains != null) lines.add("@contains ${renderSchema(prop.contains)}")
    if (prop.prefixItems.isNotEmpty()) {
      lines.add("@prefixItems ${renderSchemaList(prop.prefixItems)}")
    }

    if (prop.patternProperties.isNotEmpty()) {
      lines.add("@patternProperties ${renderSchemaMap(prop.patternProperties)}")
    }
    if (prop.propertyNames != null) {
      lines.add("@propertyNames ${renderSchema(prop.propertyNames)}")
    }
    if (prop.dependentRequired.isNotEmpty()) {
      lines.add("@dependentRequired ${renderStringListMap(prop.dependentRequired)}")
    }
    if (prop.dependentSchemas.isNotEmpty()) {
      lines.add("@dependentSchemas ${renderSchemaMap(prop.dependentSchemas)}")
    }
    if (prop.unevaluatedProperties != null) {
      lines.add("@unevaluatedProperties ${renderSchema(prop.unevaluatedProperties)}")
    }
    if (prop.unevaluatedItems != null) {
      lines.add("@unevaluatedItems ${renderSchema(prop.unevaluatedItems)}")
    }
    if (prop.contentSchema != null) {
      lines.add("@contentSchema ${renderSchema(prop.contentSchema)}")
    }
    if (prop.additionalProperties != null) {
      lines.add("@additionalProperties ${renderSchema(prop.additionalProperties)}")
    }
    if (prop.oneOf.isNotEmpty()) lines.add("@oneOf ${renderSchemaList(prop.oneOf)}")
    if (prop.anyOf.isNotEmpty()) lines.add("@anyOf ${renderSchemaList(prop.anyOf)}")
    if (prop.allOf.isNotEmpty()) lines.add("@allOf ${renderSchemaList(prop.allOf)}")
    if (prop.not != null) lines.add("@not ${renderSchema(prop.not)}")
    if (prop.ifSchema != null) lines.add("@if ${renderSchema(prop.ifSchema)}")
    if (prop.thenSchema != null) lines.add("@then ${renderSchema(prop.thenSchema)}")
    if (prop.elseSchema != null) lines.add("@else ${renderSchema(prop.elseSchema)}")
    if (prop.customKeywords.isNotEmpty()) {
      lines.add("@keywords ${renderCustomKeywords(prop.customKeywords)}")
    }

    if (prop.deprecated) lines.add("@deprecated")
    if (prop.readOnly) lines.add("@readOnly")
    if (prop.writeOnly) lines.add("@writeOnly")
    if (prop.contentMediaType != null) lines.add("@contentMediaType ${prop.contentMediaType}")
    if (prop.contentEncoding != null) lines.add("@contentEncoding ${prop.contentEncoding}")
    if (prop.example != null) lines.add("@example ${renderDocValue(prop.example)}")
    if (!prop.examples.isNullOrEmpty()) {
      prop.examples.forEachIndexed { index, value ->
        lines.add("@example example${index + 1}: ${renderDocValue(value)}")
      }
    }
    if (!prop.enumValues.isNullOrEmpty()) {
      prop.enumValues.forEach { value -> lines.add("@enum ${renderEnumValue(value)}") }
    }

    if (prop.xml != null) {
      val xml = prop.xml
      xml.name?.let { lines.add("@xmlName $it") }
      xml.namespace?.let { lines.add("@xmlNamespace $it") }
      xml.prefix?.let { lines.add("@xmlPrefix $it") }
      xml.nodeType?.let { lines.add("@xmlNodeType $it") }
      if (xml.attribute) lines.add("@xmlAttribute")
      if (xml.wrapped) lines.add("@xmlWrapped")
    }

    if (lines.isEmpty()) return ""
    val sb = StringBuilder()
    sb.appendLine("    /**")
    lines.forEachIndexed { index, line ->
      if (index > 0) sb.appendLine("     *")
      sb.append("     * ").appendLine(line)
    }
    sb.appendLine("     */")
    return sb.toString()
  }

  /**
   * Builds a `@Deprecated` annotation line when [isDeprecated] is true.
   *
   * @param isDeprecated Whether the declaration is deprecated.
   * @param indent Optional indentation prefix.
   * @return The annotation line or empty string.
   */
  fun buildDeprecatedAnnotation(isDeprecated: Boolean, indent: String = ""): String {
    return if (isDeprecated) buildString { appendLine("""$indent@Deprecated("Deprecated")""") }
    else ""
  }

  private fun renderDocValue(value: OpenApiValue): String {
    return when (value) {
      is OpenApiNull -> "null"
      is OpenApiString -> value.value
      is OpenApiBoolean -> value.value.toString()
      is OpenApiInt -> value.value.toString()
      is OpenApiNumber -> value.value.toString()
      is OpenApiArray,
      is OpenApiObject -> OpenApiValues.encodeToString(value)
    }
  }

  private fun renderExamplesList(values: List<OpenApiValue>): String {
    return OpenApiValues.encodeToString(OpenApiArray(values))
  }

  private fun renderEnumValue(value: OpenApiValue): String {
    return OpenApiValues.encodeToString(value)
  }

  private fun renderSchema(schema: SchemaProperty): String {
    return OpenApiValues.encodeToString(schemaPropertyToDocValue(schema))
  }

  private fun renderSchemaMap(map: Map<String, SchemaProperty>): String {
    val converted = map.mapValues { schemaPropertyToDocValue(it.value) }
    return OpenApiValues.encodeToString(OpenApiObject(converted))
  }

  private fun renderSchemaList(list: List<SchemaProperty>): String {
    val converted = list.map { schemaPropertyToDocValue(it) }
    return OpenApiValues.encodeToString(OpenApiArray(converted))
  }

  private fun renderSchemaComposition(refs: List<String>, inlines: List<SchemaProperty>): String {
    val entries = SchemaJsonCodec.schemaCompositionToValues(refs, inlines)
    return OpenApiValues.encodeToString(OpenApiArray(entries))
  }

  private fun renderStringListMap(map: Map<String, List<String>>): String {
    val obj =
        OpenApiObject(map.mapValues { (_, list) -> OpenApiArray(list.map { OpenApiString(it) }) })
    return OpenApiValues.encodeToString(obj)
  }

  private fun renderCustomKeywords(map: Map<String, OpenApiValue>): String {
    return OpenApiValues.encodeToString(OpenApiObject(map))
  }

  /**
   * Converts a [SchemaProperty] into its strongly-typed [OpenApiValue] KDoc tag value
   * representation by delegating to [SchemaJsonCodec].
   *
   * @param schema The schema property to convert.
   * @return The [OpenApiBoolean] or [OpenApiObject] representation.
   */
  fun schemaPropertyToDocValue(schema: SchemaProperty): OpenApiValue {
    schema.ref?.let {
      return OpenApiObject(mapOf("\$ref" to OpenApiString(it)))
    }
    schema.dynamicRef?.let {
      return OpenApiObject(mapOf("\$dynamicRef" to OpenApiString(it)))
    }
    return SchemaJsonCodec.schemaPropertyToOpenApiValue(schema)
  }
}
