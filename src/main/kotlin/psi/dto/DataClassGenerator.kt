/** Generates Kotlin Data Classes and Typealiases from OpenAPI [SchemaDefinition]s. */
package psi.dto

import domain.DynamicAnchorContext
import domain.DynamicAnchorScope
import domain.SchemaDefinition
import domain.SchemaProperty
import psi.ReferenceResolver
import psi.TypeMappers
import psi.buildKotlinFile
import psi.escapeKotlinKeyword

/**
 * Generates Kotlin source code for Data Classes and Typealiases (boolean, `$ref`, map, primitive,
 * array) from OpenAPI [SchemaDefinition]s.
 */
object DataClassGenerator {
  private val NL = 10.toChar()

  /**
   * Generates a Kotlin `typealias` source file for a primitive or array [SchemaDefinition].
   *
   * @param packageName Target Kotlin package name.
   * @param definition The schema definition.
   * @param dynamicContext Dynamic anchor resolution context.
   * @return The generated Kotlin source code.
   */
  fun buildAliasSource(
      packageName: String,
      definition: SchemaDefinition,
      dynamicContext: DynamicAnchorContext
  ): String {
    val imports = mutableSetOf<String>()
    addTypeImports(imports, definition)

    val kdoc = SchemaDocTagWriter.buildClassKDoc(definition)
    val deprecatedAnnotation = SchemaDocTagWriter.buildDeprecatedAnnotation(definition.deprecated)
    val prop = dynamicContext.root
    val baseType = TypeMappers.mapType(prop, dynamicContext.scope::resolveDynamicRef)
    val isNullable = prop.types.contains("null")
    val kotlinType = if (isNullable) "$baseType?" else baseType

    return buildKotlinFile(packageName) {
      imports.sorted().forEach { import(it) }
      raw("$kdoc$deprecatedAnnotation")
      line("typealias ${definition.safeName} = $kotlinType")
    }
  }

  /**
   * Generates a Kotlin `typealias` source file for a boolean schema.
   *
   * @param packageName Target Kotlin package name.
   * @param definition The schema definition.
   * @param value The boolean schema value.
   * @return The generated Kotlin source code.
   */
  fun buildBooleanAliasSource(
      packageName: String,
      definition: SchemaDefinition,
      value: Boolean
  ): String {
    val kdoc = SchemaDocTagWriter.buildClassKDoc(definition)
    val deprecatedAnnotation = SchemaDocTagWriter.buildDeprecatedAnnotation(definition.deprecated)
    val kotlinType = if (value) "Any" else "Nothing"

    return buildKotlinFile(packageName) {
      raw("$kdoc$deprecatedAnnotation")
      line("typealias ${definition.safeName} = $kotlinType")
    }
  }

  /**
   * Generates a Kotlin `typealias` source file for a `$ref` schema alias.
   *
   * @param packageName Target Kotlin package name.
   * @param definition The schema definition.
   * @param ref The target `$ref` URI.
   * @return The generated Kotlin source code.
   */
  fun buildRefAliasSource(packageName: String, definition: SchemaDefinition, ref: String): String {
    val kdoc = SchemaDocTagWriter.buildClassKDoc(definition)
    val deprecatedAnnotation = SchemaDocTagWriter.buildDeprecatedAnnotation(definition.deprecated)
    val target = ReferenceResolver.resolveRefToType(ref)

    return buildKotlinFile(packageName) {
      raw("$kdoc$deprecatedAnnotation")
      line("typealias ${definition.safeName} = $target")
    }
  }

  /**
   * Generates a Kotlin `typealias` source file for an object schema with only
   * `additionalProperties`.
   *
   * @param packageName Target Kotlin package name.
   * @param definition The schema definition.
   * @param dynamicContext Dynamic anchor resolution context.
   * @return The generated Kotlin source code.
   */
  fun buildMapAliasSource(
      packageName: String,
      definition: SchemaDefinition,
      dynamicContext: DynamicAnchorContext
  ): String {
    val imports = mutableSetOf<String>()
    addTypeImports(imports, definition)

    val kdoc = SchemaDocTagWriter.buildClassKDoc(definition)
    val deprecatedAnnotation = SchemaDocTagWriter.buildDeprecatedAnnotation(definition.deprecated)
    val valueType =
        TypeMappers.mapType(
            definition.additionalProperties!!, dynamicContext.scope::resolveDynamicRef)

    return buildKotlinFile(packageName) {
      imports.sorted().forEach { import(it) }
      raw("$kdoc$deprecatedAnnotation")
      line("typealias ${definition.safeName} = Map<String, $valueType>")
    }
  }

  /**
   * Generates a Kotlin `data class` (or empty `class`) source file for an object
   * [SchemaDefinition].
   *
   * @param packageName Target Kotlin package name.
   * @param definition The schema definition.
   * @param dynamicContext Dynamic anchor resolution context.
   * @return The generated Kotlin source code.
   */
  fun buildDataClassSource(
      packageName: String,
      definition: SchemaDefinition,
      dynamicContext: DynamicAnchorContext
  ): String {
    val imports = mutableSetOf<String>()
    imports.add("kotlinx.serialization.Serializable")
    imports.add("kotlinx.serialization.Contextual")
    imports.add("kotlinx.serialization.SerialName")
    addTypeImports(imports, definition)

    val parents = definition.allOf.map { ReferenceResolver.resolveRefToType(it) }
    val inheritanceClause =
        if (parents.isNotEmpty()) {
          " : ${parents.joinToString(", ")}"
        } else {
          ""
        }

    val propertyLines =
        definition.properties
            .map { (name, prop) ->
              val kotlinType =
                  resolveKotlinType(name, prop, definition.required, dynamicContext.scope)
              val defaultVal = if (kotlinType.endsWith("?")) " = null" else ""

              val desc = SchemaDocTagWriter.buildPropertyKDoc(prop)
              val deprecatedPropAnnotation =
                  SchemaDocTagWriter.buildDeprecatedAnnotation(prop.deprecated, indent = "    ")
              val serialName = """    @SerialName("$name")"""
              val contextual = if (kotlinType.startsWith("Any")) "    @Contextual$NL" else ""
              "$desc$deprecatedPropAnnotation$serialName$NL$contextual    val ${escapeKotlinKeyword(name)}: $kotlinType$defaultVal"
            }
            .joinToString(",$NL$NL")

    val kdoc = SchemaDocTagWriter.buildClassKDoc(definition)
    val deprecatedAnnotation = SchemaDocTagWriter.buildDeprecatedAnnotation(definition.deprecated)

    val isDataClass = propertyLines.isNotBlank()
    val classKeyword = if (isDataClass) "data class" else "class"
    val classBody =
        if (isDataClass) "( $NL$propertyLines$NL)$inheritanceClause" else inheritanceClause
    return buildKotlinFile(packageName) {
      imports.sorted().forEach { import(it) }
      raw("$kdoc$deprecatedAnnotation")
      line("@Serializable")
      line("$classKeyword ${escapeKotlinKeyword(definition.safeName)}$classBody")
    }
  }

  /**
   * Resolves the Kotlin type string (including nullability suffix `?`) for a property.
   *
   * @param name Property name.
   * @param prop Property schema.
   * @param requiredList List of required property names on the parent schema.
   * @param dynamicScope Dynamic anchor scope.
   * @return The resolved Kotlin type string.
   */
  fun resolveKotlinType(
      name: String,
      prop: SchemaProperty,
      requiredList: List<String>,
      dynamicScope: DynamicAnchorScope
  ): String {
    val baseType = TypeMappers.mapType(prop, dynamicScope::resolveDynamicRef)

    val isExplicitlyNullable = prop.types.contains("null")
    val isRequired = requiredList.contains(name)

    return if (isExplicitlyNullable || !isRequired) {
      "$baseType?"
    } else {
      baseType
    }
  }

  /**
   * Populates required date/time imports into [imports] based on formats used in [definition].
   *
   * @param imports Mutable set of import strings to populate.
   * @param definition The schema definition to inspect.
   */
  fun addTypeImports(imports: MutableSet<String>, definition: SchemaDefinition) {
    val formats = mutableSetOf<String>()
    definition.format?.let { formats.add(it) }
    definition.properties.values.forEach { collectFormats(it, formats) }
    definition.additionalProperties?.let { collectFormats(it, formats) }
    definition.items?.let { collectFormats(it, formats) }

    if (formats.contains("date")) {
      imports.add("kotlinx.datetime.LocalDate")
    }
    if (formats.contains("date-time")) {
      imports.add("kotlinx.datetime.Instant")
    }
  }

  private fun collectFormats(prop: SchemaProperty, acc: MutableSet<String>) {
    prop.format?.let { acc.add(it) }
    prop.items?.let { collectFormats(it, acc) }
    prop.additionalProperties?.let { collectFormats(it, acc) }
  }
}
