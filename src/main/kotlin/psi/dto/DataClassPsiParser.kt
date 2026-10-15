/**
 * Parses Kotlin PSI class, enum, sealed interface, and typealias declarations into OpenAPI
 * [SchemaDefinition]s.
 */
package psi.dto

import domain.Discriminator
import domain.OpenApiString
import domain.SchemaDefinition
import domain.SchemaProperty
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import psi.TypeMappers

/**
 * Metadata for a concrete subtype of a Kotlin `sealed interface`.
 *
 * @property kotlinName Simple Kotlin class name of the subtype.
 * @property serialName Optional `@SerialName` override on the subtype.
 */
data class SealedSubtype(val kotlinName: String, val serialName: String?)

/**
 * Extracts OpenAPI [SchemaDefinition]s from Kotlin PSI declarations (`KtClass`, `KtTypeAlias`)
 * using [SchemaDocTagParser] for KDoc metadata extraction.
 */
object DataClassPsiParser {
  private fun extractBaseSchemaDefinition(
      decl: KtDeclaration,
      name: String,
      type: String
  ): SchemaDefinition {
    val (example, examples) = SchemaDocTagParser.extractExamples(decl)
    val oneOfTag = SchemaDocTagParser.extractCompositionTag(decl, "oneOf")
    val anyOfTag = SchemaDocTagParser.extractCompositionTag(decl, "anyOf")
    val allOfTag = SchemaDocTagParser.extractCompositionTag(decl, "allOf")
    val deprecated =
        SchemaDocTagParser.hasTag(decl, "deprecated") ||
            SchemaDocTagParser.hasAnnotation(decl, "Deprecated")

    return SchemaDefinition(
        name = name,
        type = type,
        schemaId = SchemaDocTagParser.extractTagValue(decl, "schemaId"),
        schemaDialect = SchemaDocTagParser.extractTagValue(decl, "schemaDialect"),
        anchor = SchemaDocTagParser.extractTagValue(decl, "anchor"),
        dynamicAnchor = SchemaDocTagParser.extractTagValue(decl, "dynamicAnchor"),
        dynamicRef = SchemaDocTagParser.extractTagValue(decl, "dynamicRef"),
        defs = SchemaDocTagParser.extractSchemaMapTag(decl, "defs"),
        description = SchemaDocTagParser.extractDoc(decl),
        title = SchemaDocTagParser.extractTagValue(decl, "title"),
        defaultValue = SchemaDocTagParser.extractTagLiteral(decl, "default"),
        constValue = SchemaDocTagParser.extractTagLiteral(decl, "const"),
        deprecated = deprecated,
        readOnly = SchemaDocTagParser.hasTag(decl, "readOnly"),
        writeOnly = SchemaDocTagParser.hasTag(decl, "writeOnly"),
        contentMediaType = SchemaDocTagParser.extractTagValue(decl, "contentMediaType"),
        contentEncoding = SchemaDocTagParser.extractTagValue(decl, "contentEncoding"),
        minLength = SchemaDocTagParser.extractTagInt(decl, "minLength"),
        maxLength = SchemaDocTagParser.extractTagInt(decl, "maxLength"),
        pattern = SchemaDocTagParser.extractTagValue(decl, "pattern"),
        minimum = SchemaDocTagParser.extractTagDouble(decl, "minimum"),
        maximum = SchemaDocTagParser.extractTagDouble(decl, "maximum"),
        multipleOf = SchemaDocTagParser.extractTagDouble(decl, "multipleOf"),
        exclusiveMinimum = SchemaDocTagParser.extractTagDouble(decl, "exclusiveMinimum"),
        exclusiveMaximum = SchemaDocTagParser.extractTagDouble(decl, "exclusiveMaximum"),
        minItems = SchemaDocTagParser.extractTagInt(decl, "minItems"),
        maxItems = SchemaDocTagParser.extractTagInt(decl, "maxItems"),
        uniqueItems = SchemaDocTagParser.extractTagBoolean(decl, "uniqueItems"),
        minProperties = SchemaDocTagParser.extractTagInt(decl, "minProperties"),
        maxProperties = SchemaDocTagParser.extractTagInt(decl, "maxProperties"),
        minContains = SchemaDocTagParser.extractTagInt(decl, "minContains"),
        maxContains = SchemaDocTagParser.extractTagInt(decl, "maxContains"),
        contains = SchemaDocTagParser.extractSchemaTag(decl, "contains"),
        prefixItems = SchemaDocTagParser.extractSchemaListTag(decl, "prefixItems"),
        comment = SchemaDocTagParser.extractTagValue(decl, "comment"),
        patternProperties = SchemaDocTagParser.extractSchemaMapTag(decl, "patternProperties"),
        propertyNames = SchemaDocTagParser.extractSchemaTag(decl, "propertyNames"),
        dependentRequired = SchemaDocTagParser.extractStringListMapTag(decl, "dependentRequired"),
        dependentSchemas = SchemaDocTagParser.extractSchemaMapTag(decl, "dependentSchemas"),
        unevaluatedProperties = SchemaDocTagParser.extractSchemaTag(decl, "unevaluatedProperties"),
        unevaluatedItems = SchemaDocTagParser.extractSchemaTag(decl, "unevaluatedItems"),
        contentSchema = SchemaDocTagParser.extractSchemaTag(decl, "contentSchema"),
        customKeywords = SchemaDocTagParser.extractCustomKeywords(decl),
        additionalProperties = SchemaDocTagParser.extractSchemaTag(decl, "additionalProperties"),
        oneOf = oneOfTag.refs,
        oneOfSchemas = oneOfTag.schemas,
        anyOf = anyOfTag.refs,
        anyOfSchemas = anyOfTag.schemas,
        allOf = allOfTag.refs,
        allOfSchemas = allOfTag.schemas,
        not = SchemaDocTagParser.extractSchemaTag(decl, "not"),
        ifSchema = SchemaDocTagParser.extractSchemaTag(decl, "if"),
        thenSchema = SchemaDocTagParser.extractSchemaTag(decl, "then"),
        elseSchema = SchemaDocTagParser.extractSchemaTag(decl, "else"),
        externalDocs = SchemaDocTagParser.extractExternalDocs(decl),
        discriminator = SchemaDocTagParser.extractDiscriminator(decl),
        example = example,
        examples = examples,
        examplesList = SchemaDocTagParser.extractExamplesList(decl),
        enumValues = SchemaDocTagParser.extractEnumValues(decl),
        xml = SchemaDocTagParser.extractXml(decl))
  }

  private fun parsePropertyDeclaration(decl: KtDeclaration, kotlinType: String): SchemaProperty {
    val baseProp = TypeMappers.kotlinToSchemaProperty(kotlinType)
    val (example, examples) = SchemaDocTagParser.extractExamples(decl)
    val additionalPropertiesTag = SchemaDocTagParser.extractSchemaTag(decl, "additionalProperties")
    val deprecated =
        SchemaDocTagParser.hasTag(decl, "deprecated") ||
            SchemaDocTagParser.hasAnnotation(decl, "Deprecated")

    return baseProp.copy(
        description = SchemaDocTagParser.extractDoc(decl),
        example = example,
        examples = if (examples != null) examples.values.toList() else null,
        enumValues = SchemaDocTagParser.extractEnumValues(decl),
        title = SchemaDocTagParser.extractTagValue(decl, "title"),
        defaultValue = SchemaDocTagParser.extractTagLiteral(decl, "default"),
        constValue = SchemaDocTagParser.extractTagLiteral(decl, "const"),
        externalDocs = SchemaDocTagParser.extractExternalDocs(decl),
        discriminator = SchemaDocTagParser.extractDiscriminator(decl),
        schemaId = SchemaDocTagParser.extractTagValue(decl, "schemaId"),
        schemaDialect = SchemaDocTagParser.extractTagValue(decl, "schemaDialect"),
        anchor = SchemaDocTagParser.extractTagValue(decl, "anchor"),
        dynamicAnchor = SchemaDocTagParser.extractTagValue(decl, "dynamicAnchor"),
        dynamicRef = SchemaDocTagParser.extractTagValue(decl, "dynamicRef"),
        defs = SchemaDocTagParser.extractSchemaMapTag(decl, "defs"),
        contentMediaType =
            SchemaDocTagParser.extractTagValue(decl, "contentMediaType")
                ?: baseProp.contentMediaType,
        contentEncoding =
            SchemaDocTagParser.extractTagValue(decl, "contentEncoding") ?: baseProp.contentEncoding,
        minLength = SchemaDocTagParser.extractTagInt(decl, "minLength"),
        maxLength = SchemaDocTagParser.extractTagInt(decl, "maxLength"),
        pattern = SchemaDocTagParser.extractTagValue(decl, "pattern"),
        minimum = SchemaDocTagParser.extractTagDouble(decl, "minimum"),
        maximum = SchemaDocTagParser.extractTagDouble(decl, "maximum"),
        multipleOf = SchemaDocTagParser.extractTagDouble(decl, "multipleOf"),
        exclusiveMinimum = SchemaDocTagParser.extractTagDouble(decl, "exclusiveMinimum"),
        exclusiveMaximum = SchemaDocTagParser.extractTagDouble(decl, "exclusiveMaximum"),
        minItems = SchemaDocTagParser.extractTagInt(decl, "minItems"),
        maxItems = SchemaDocTagParser.extractTagInt(decl, "maxItems"),
        uniqueItems = SchemaDocTagParser.extractTagBoolean(decl, "uniqueItems"),
        minProperties = SchemaDocTagParser.extractTagInt(decl, "minProperties"),
        maxProperties = SchemaDocTagParser.extractTagInt(decl, "maxProperties"),
        minContains = SchemaDocTagParser.extractTagInt(decl, "minContains"),
        maxContains = SchemaDocTagParser.extractTagInt(decl, "maxContains"),
        contains = SchemaDocTagParser.extractSchemaTag(decl, "contains"),
        prefixItems = SchemaDocTagParser.extractSchemaListTag(decl, "prefixItems"),
        comment = SchemaDocTagParser.extractTagValue(decl, "comment"),
        patternProperties = SchemaDocTagParser.extractSchemaMapTag(decl, "patternProperties"),
        propertyNames = SchemaDocTagParser.extractSchemaTag(decl, "propertyNames"),
        dependentRequired = SchemaDocTagParser.extractStringListMapTag(decl, "dependentRequired"),
        dependentSchemas = SchemaDocTagParser.extractSchemaMapTag(decl, "dependentSchemas"),
        unevaluatedProperties = SchemaDocTagParser.extractSchemaTag(decl, "unevaluatedProperties"),
        unevaluatedItems = SchemaDocTagParser.extractSchemaTag(decl, "unevaluatedItems"),
        contentSchema = SchemaDocTagParser.extractSchemaTag(decl, "contentSchema"),
        additionalProperties = additionalPropertiesTag ?: baseProp.additionalProperties,
        oneOf = SchemaDocTagParser.extractSchemaListTag(decl, "oneOf"),
        anyOf = SchemaDocTagParser.extractSchemaListTag(decl, "anyOf"),
        allOf = SchemaDocTagParser.extractSchemaListTag(decl, "allOf"),
        not = SchemaDocTagParser.extractSchemaTag(decl, "not"),
        ifSchema = SchemaDocTagParser.extractSchemaTag(decl, "if"),
        thenSchema = SchemaDocTagParser.extractSchemaTag(decl, "then"),
        elseSchema = SchemaDocTagParser.extractSchemaTag(decl, "else"),
        customKeywords = SchemaDocTagParser.extractCustomKeywords(decl),
        deprecated = deprecated,
        readOnly = SchemaDocTagParser.hasTag(decl, "readOnly"),
        writeOnly = SchemaDocTagParser.hasTag(decl, "writeOnly"),
        xml = SchemaDocTagParser.extractXml(decl))
  }

  /**
   * Parses a Kotlin `typealias` declaration into a [SchemaDefinition], or `null` if unsupported.
   *
   * @param alias The PSI typealias declaration.
   * @return The extracted [SchemaDefinition] or `null`.
   */
  fun parseTypeAlias(alias: KtTypeAlias): SchemaDefinition? {
    val name = alias.name!!
    val typeRef = alias.getTypeReference()!!.text

    val schemaProp = TypeMappers.kotlinToSchemaProperty(typeRef)
    val isMapAlias = schemaProp.additionalProperties != null
    val isArrayAlias = schemaProp.types.contains("array")
    val isPrimitiveAlias = schemaProp.type in setOf("string", "integer", "number", "boolean")

    if (!isMapAlias && !isArrayAlias && !isPrimitiveAlias) return null

    val base = extractBaseSchemaDefinition(alias, name, schemaProp.type)
    val mergedMediaType = base.contentMediaType
    val mergedEncoding = base.contentEncoding ?: schemaProp.contentEncoding

    return when {
      isMapAlias ->
          base.copy(
              type = "object",
              types = schemaProp.types,
              additionalProperties = base.additionalProperties ?: schemaProp.additionalProperties,
              contentMediaType = mergedMediaType,
              contentEncoding = mergedEncoding,
              discriminator = null)
      isArrayAlias ->
          base.copy(
              type = "array",
              types = schemaProp.types,
              items = schemaProp.items,
              contentMediaType = mergedMediaType,
              contentEncoding = mergedEncoding,
              discriminator = null)
      else ->
          base.copy(
              type = schemaProp.type,
              types = schemaProp.types,
              format = schemaProp.format,
              contentMediaType = mergedMediaType,
              contentEncoding = mergedEncoding,
              discriminator = null)
    }
  }

  /**
   * Parses a Kotlin `enum class` declaration into a [SchemaDefinition].
   *
   * @param ktClass The PSI enum class declaration.
   * @return The extracted [SchemaDefinition].
   */
  fun parseEnum(ktClass: KtClass): SchemaDefinition {
    val name = ktClass.name!!
    val base = extractBaseSchemaDefinition(ktClass, name, "string")

    val values =
        ktClass.declarations.filterIsInstance<KtEnumEntry>().map { entry ->
          val rawValue = extractSerialNameAnnotation(entry) ?: entry.name!!
          OpenApiString(rawValue)
        }

    return base.copy(enumValues = values, discriminator = null)
  }

  /**
   * Parses a Kotlin `sealed interface` declaration into a polymorphic [SchemaDefinition].
   *
   * @param ktClass The PSI sealed interface declaration.
   * @param subtypes Resolved concrete subtypes in the same file.
   * @return The extracted [SchemaDefinition].
   */
  fun parseSealedInterface(ktClass: KtClass, subtypes: List<SealedSubtype>): SchemaDefinition {
    val name = ktClass.name!!
    val base = extractBaseSchemaDefinition(ktClass, name, "object")

    val discriminatorAnnotation =
        ktClass.annotationEntries.find { it.shortName!!.asString() == "JsonClassDiscriminator" }

    val propName =
        if (discriminatorAnnotation != null) {
          val arg = discriminatorAnnotation.valueArgumentList!!.arguments.first()
          val expression = arg.getArgumentExpression() as KtStringTemplateExpression
          expression.entries.first().text
        } else {
          null
        }

    val discriminatorObj =
        when {
          base.discriminator != null -> base.discriminator
          propName != null -> Discriminator(propertyName = propName)
          else -> null
        }

    val inferredOneOf =
        if (base.oneOf.isEmpty() && base.oneOfSchemas.isEmpty()) {
          subtypes.map { "#/components/schemas/${it.kotlinName}" }
        } else {
          emptyList()
        }

    val inferredMapping =
        subtypes
            .mapNotNull { subtype ->
              subtype.serialName?.let { serial ->
                serial to "#/components/schemas/${subtype.kotlinName}"
              }
            }
            .toMap()

    val mergedDiscriminator = mergeDiscriminatorMappings(discriminatorObj, inferredMapping)
    val oneOfRefs = if (inferredOneOf.isNotEmpty()) inferredOneOf else base.oneOf
    val oneOfSchemas = if (inferredOneOf.isNotEmpty()) emptyList() else base.oneOfSchemas

    val properties = mutableMapOf<String, SchemaProperty>()
    val requiredFields = mutableListOf<String>()

    ktClass.getProperties().forEach { prop ->
      val kotlinType = prop.typeReference!!.text
      val rawName = prop.name!!
      val jsonName = extractSerialNameAnnotation(prop) ?: rawName
      properties[jsonName] = parsePropertyDeclaration(prop, kotlinType)
      if (!kotlinType.contains("?")) {
        requiredFields.add(jsonName)
      }
    }

    return base.copy(
        properties = properties,
        required = requiredFields,
        discriminator = mergedDiscriminator,
        oneOf = oneOfRefs,
        oneOfSchemas = oneOfSchemas)
  }

  /**
   * Parses a Kotlin `data class` declaration into an object [SchemaDefinition].
   *
   * @param ktClass The PSI data class declaration.
   * @return The extracted [SchemaDefinition].
   */
  fun parseDataClass(ktClass: KtClass): SchemaDefinition {
    val name = ktClass.name!!
    val base = extractBaseSchemaDefinition(ktClass, name, "object")

    val properties = mutableMapOf<String, SchemaProperty>()
    val requiredFields = mutableListOf<String>()

    val parameters = ktClass.primaryConstructor!!.valueParameters

    parameters.forEach { param ->
      val kotlinType = param.typeReference!!.text
      val rawName = param.name!!
      val jsonName = extractSerialNameAnnotation(param) ?: rawName
      properties[jsonName] = parsePropertyDeclaration(param, kotlinType)
      if (!kotlinType.contains("?")) {
        requiredFields.add(jsonName)
      }
    }

    val superTypes =
        ktClass.superTypeListEntries.map { it.text }.filter { it !in setOf("Any", "Serializable") }
    val mergedAllOf = (superTypes + base.allOf).distinct()

    return base.copy(
        properties = properties, required = requiredFields, allOf = mergedAllOf, enumValues = null)
  }

  /**
   * Builds an index mapping sealed interface names to their concrete subtypes within [classes].
   *
   * @param classes All classes discovered in the Kotlin file.
   * @param sealedInterfaceNames Set of sealed interface simple names.
   * @return Map of sealed interface name to sorted list of [SealedSubtype]s.
   */
  fun buildSealedSubtypeIndex(
      classes: List<KtClass>,
      sealedInterfaceNames: Set<String>
  ): Map<String, List<SealedSubtype>> {
    if (sealedInterfaceNames.isEmpty()) return emptyMap()
    val index = mutableMapOf<String, MutableList<SealedSubtype>>()
    classes.forEach { candidate ->
      val subtypeName = candidate.name!!
      candidate.superTypeListEntries.forEach { entry ->
        val typeText = entry.typeReference!!.text
        val simpleName = extractSimpleTypeName(typeText)
        if (simpleName in sealedInterfaceNames && simpleName != subtypeName) {
          val serialName = extractSerialNameAnnotation(candidate)
          index.getOrPut(simpleName) { mutableListOf() }.add(SealedSubtype(subtypeName, serialName))
        }
      }
    }
    return index.mapValues { (_, values) ->
      values.distinctBy { it.kotlinName }.sortedBy { it.kotlinName }
    }
  }

  private fun extractSimpleTypeName(typeText: String): String {
    val withoutGenerics = typeText.substringBefore("<").trim()
    val withoutNullable = withoutGenerics.removeSuffix("?").trim()
    return withoutNullable.substringAfterLast(".")
  }

  private fun extractSerialNameAnnotation(owner: KtModifierListOwner): String? {
    val serialNameEntry =
        owner.annotationEntries.find { it.shortName!!.asString() == "SerialName" } ?: return null
    val arg = serialNameEntry.valueArgumentList!!.arguments.first()
    val expression = arg.getArgumentExpression() as KtStringTemplateExpression
    return expression.entries.first().text
  }

  private fun mergeDiscriminatorMappings(
      discriminator: Discriminator?,
      inferredMapping: Map<String, String>
  ): Discriminator? {
    if (discriminator == null || inferredMapping.isEmpty()) return discriminator
    val merged = inferredMapping.toMutableMap()
    discriminator.mapping.forEach { (key, value) -> merged[key] = value }
    return discriminator.copy(mapping = merged)
  }
}
