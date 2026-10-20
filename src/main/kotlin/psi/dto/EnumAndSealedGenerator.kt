/** Generates Kotlin Enum Classes and Sealed Interfaces from OpenAPI [SchemaDefinition]s. */
package psi.dto

import domain.DynamicAnchorContext
import domain.OpenApiString
import domain.SchemaDefinition
import psi.buildKotlinFile
import psi.capitalizeName
import psi.escapeKotlinKeyword

/**
 * Generates Kotlin `enum class` and polymorphic `sealed interface` declarations from OpenAPI
 * [SchemaDefinition]s.
 */
object EnumAndSealedGenerator {
  private val NL = 10.toChar()

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
            .joinToString("$NL$NL")

    return buildKotlinFile(packageName) {
      imports.sorted().forEach { import(it) }
      raw("$kdoc$deprecatedAnnotation")
      line("@Serializable")
      if (definition.discriminator != null) {
        line("@OptIn(ExperimentalSerializationApi::class)")
        line("""@JsonClassDiscriminator("${definition.discriminator.propertyName}")""")
      }
      line("sealed interface ${definition.safeName} {")
      if (propertyLines.isNotEmpty()) {
        raw("$propertyLines$NL")
      }
      line("}")
    }
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
        values.joinToString(",$NL$NL") { value ->
          val validName = sanitizeEnumName(value)
          val annotation = """    @SerialName("$value")"""
          "$annotation$NL    $validName"
        }

    return buildKotlinFile(packageName) {
      imports.sorted().forEach { import(it) }
      raw("$kdoc$deprecatedAnnotation")
      line("@Serializable")
      line("enum class ${definition.safeName} {")
      raw("$entries$NL")
      line("}")
    }
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
