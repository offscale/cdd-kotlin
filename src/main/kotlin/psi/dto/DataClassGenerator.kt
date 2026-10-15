/** Generates Kotlin Data Classes and Typealiases from OpenAPI [SchemaDefinition]s. */
package psi.dto

import domain.DynamicAnchorContext
import domain.DynamicAnchorScope
import domain.SchemaDefinition
import domain.SchemaProperty
import psi.ReferenceResolver
import psi.TypeMappers
import psi.escapeKotlinKeyword

/**
 * Generates Kotlin source code for Data Classes and Typealiases (boolean, `$ref`, map, primitive,
 * array) from OpenAPI [SchemaDefinition]s.
 */
object DataClassGenerator {
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

    return """
            package $packageName

            ${imports.sorted().joinToString("\n") { "import $it" }}

            $kdoc$deprecatedAnnotation
            typealias ${definition.safeName} = $kotlinType
        """
        .trimIndent()
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

    return """
            package $packageName

            $kdoc$deprecatedAnnotation
            typealias ${definition.safeName} = $kotlinType
        """
        .trimIndent()
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

    return """
            package $packageName

            $kdoc$deprecatedAnnotation
            typealias ${definition.safeName} = $target
        """
        .trimIndent()
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

    return """
            package $packageName

            ${imports.sorted().joinToString("\n") { "import $it" }}

            $kdoc$deprecatedAnnotation
            typealias ${definition.safeName} = Map<String, $valueType>
        """
        .trimIndent()
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
              val serialName = "    @SerialName(\"$name\")"
              val contextual = if (kotlinType.startsWith("Any")) "    @Contextual\n" else ""
              "$desc$deprecatedPropAnnotation$serialName\n$contextual    val ${escapeKotlinKeyword(name)}: $kotlinType$defaultVal"
            }
            .joinToString(",\n\n")

    val kdoc = SchemaDocTagWriter.buildClassKDoc(definition)
    val deprecatedAnnotation = SchemaDocTagWriter.buildDeprecatedAnnotation(definition.deprecated)

    val isDataClass = propertyLines.isNotBlank()
    val classKeyword = if (isDataClass) "data class" else "class"
    val classBody =
        if (isDataClass) "( \n            $propertyLines\n            )$inheritanceClause"
        else "$inheritanceClause"
    return """ 
            package $packageName
            
            ${imports.sorted().joinToString("\n") { "import $it" }} 
            
            $kdoc$deprecatedAnnotation@Serializable
            $classKeyword ${escapeKotlinKeyword(definition.safeName)}$classBody
        """
        .trimIndent()
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

    // Nullability Logic:
    // 1. If "null" is in types -> Nullable (OAS 3.2)
    // 2. If not required -> Nullable (Swagger 2.0 / OAS 3.0 default)
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
