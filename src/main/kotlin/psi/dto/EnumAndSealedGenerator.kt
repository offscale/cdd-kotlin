/** Generates Kotlin Enum Classes and Sealed Interfaces from OpenAPI [SchemaDefinition]s. */
package psi.dto

import domain.DynamicAnchorContext
import domain.OpenApiString
import domain.SchemaDefinition
import psi.capitalizeName
import psi.escapeKotlinKeyword

/**
 * Generates Kotlin `enum class` and polymorphic `sealed interface` declarations from OpenAPI
 * [SchemaDefinition]s.
 */
object EnumAndSealedGenerator {
  /**
   * Generates a Kotlin `sealed interface` source file for a polymorphic (`oneOf`/`anyOf`)
   * [SchemaDefinition].
   *
   * @param packageName Target Kotlin package name.
   * @param definition The schema definition.
   * @param dynamicContext Dynamic anchor resolution context.
   * @return The generated Kotlin source code.
   */
  fun buildSealedInterfaceSource(
      packageName: String,
      definition: SchemaDefinition,
      dynamicContext: DynamicAnchorContext
  ): String {
    val imports =
        mutableSetOf(
            "kotlinx.serialization.Serializable",
            "kotlinx.serialization.json.JsonClassDiscriminator",
            "kotlinx.serialization.ExperimentalSerializationApi")
    DataClassGenerator.addTypeImports(imports, definition)

    val kdoc = SchemaDocTagWriter.buildClassKDoc(definition)
    val deprecatedAnnotation = SchemaDocTagWriter.buildDeprecatedAnnotation(definition.deprecated)

    val discriminatorAnnotation =
        if (definition.discriminator != null) {
          "@OptIn(ExperimentalSerializationApi::class)\n@JsonClassDiscriminator(\"${definition.discriminator.propertyName}\")"
        } else {
          ""
        }

    val propertyLines =
        definition.properties
            .map { (name, prop) ->
              val kotlinType =
                  DataClassGenerator.resolveKotlinType(
                      name, prop, definition.required, dynamicContext.scope)
              val desc = SchemaDocTagWriter.buildPropertyKDoc(prop)
              val deprecatedPropAnnotation =
                  SchemaDocTagWriter.buildDeprecatedAnnotation(prop.deprecated, indent = "    ")
              "$desc$deprecatedPropAnnotation    val ${escapeKotlinKeyword(name)}: $kotlinType"
            }
            .joinToString("\n\n")

    return """ 
            package $packageName

            ${imports.sorted().joinToString("\n") { "import $it" }} 

            $kdoc$deprecatedAnnotation@Serializable
            $discriminatorAnnotation
            sealed interface ${definition.safeName} { 
            $propertyLines
            } 
        """
        .trimIndent()
  }

  /**
   * Generates a Kotlin `enum class` source file for a string-enumerated [SchemaDefinition].
   *
   * @param packageName Target Kotlin package name.
   * @param definition The schema definition.
   * @return The generated Kotlin source code.
   */
  fun buildEnumSource(packageName: String, definition: SchemaDefinition): String {
    val imports =
        mutableSetOf("kotlinx.serialization.Serializable", "kotlinx.serialization.SerialName")
    val kdoc = SchemaDocTagWriter.buildClassKDoc(definition)
    val deprecatedAnnotation = SchemaDocTagWriter.buildDeprecatedAnnotation(definition.deprecated)

    val values = definition.enumValues!!.map { (it as OpenApiString).value }
    val entries =
        values.joinToString(",\n\n") { value ->
          val validName = sanitizeEnumName(value)
          val annotation = "    @SerialName(\"$value\")"
          "$annotation\n    $validName"
        }

    return """ 
            package $packageName

            ${imports.sorted().joinToString("\n") { "import $it" }} 

            $kdoc$deprecatedAnnotation@Serializable
            enum class ${definition.safeName} { 
            $entries
            } 
        """
        .trimIndent()
  }

  /**
   * Converts a raw string enum value into a valid PascalCase Kotlin enum entry identifier.
   *
   * @param raw Raw enum string value.
   * @return Sanitized Kotlin enum entry identifier.
   */
  fun sanitizeEnumName(raw: String): String {
    val cleaned = raw.replace(Regex("[^a-zA-Z0-9]"), " ")
    var ident =
        cleaned
            .split(" ")
            .filter { it.isNotEmpty() }
            .joinToString("") { part -> capitalizeName(part.lowercase()) }

    if (ident.isEmpty()) ident = "Unknown"
    if (ident.first().isDigit()) {
      ident = "_$ident"
    }
    return ident
  }
}
