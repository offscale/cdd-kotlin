/**
 * Generates Kotlin Data Transfer Objects (DTOs), Enums, Sealed Interfaces, and Typealiases from
 * OpenAPI [SchemaDefinition]s.
 */
package psi

import domain.OpenApiString
import domain.SchemaDefinition
import domain.buildDynamicAnchorContext
import org.jetbrains.kotlin.psi.KtFile
import psi.dto.DataClassGenerator
import psi.dto.EnumAndSealedGenerator

/**
 * Generates Kotlin Data Transfer Objects (DTOs) from Schema Definitions. Uses kotlinx.serialization
 * annotations. Updated for OAS 3.2 Dual/Array Types support, additionalProperties maps, and OAS 3.1
 * Content Encoding.
 *
 * Delegates generation to [DataClassGenerator], [EnumAndSealedGenerator], and
 * [psi.dto.SchemaDocTagWriter].
 */
class DtoGenerator {
  private val psiFactory = PsiInfrastructure.createPsiFactory()

  /**
   * Generates a Kotlin file containing the Data Class, Enum, or Sealed Interface for the given
   * Schema.
   *
   * @param packageName The package name for the generated class.
   * @param definition The schema definition containing properties and requirements.
   * @return A PSI KtFile representing the generated source code.
   */
  fun generateDto(packageName: String, definition: SchemaDefinition): KtFile {
    val content = buildSource(packageName, definition)
    return psiFactory.createFile("${definition.safeName}.kt", content)
  }

  /**
   * Returns `true` when [definition] generates a standard Kotlin `data class` with constructor
   * properties (rather than a `typealias`, `enum class`, `sealed interface`, or empty `class`).
   *
   * @param definition The [SchemaDefinition] to inspect.
   * @return `true` if a `data class` with properties is generated.
   */
  fun isDataClass(definition: SchemaDefinition): Boolean {
    if (definition.booleanSchema != null || definition.ref != null) return false
    val enumVals = definition.enumValues
    if (!enumVals.isNullOrEmpty() && enumVals.all { it is OpenApiString }) return false
    if ((definition.oneOf + definition.anyOf).isNotEmpty() ||
        (definition.oneOfSchemas + definition.anyOfSchemas).isNotEmpty()) {
      return false
    }
    return definition.properties.isNotEmpty()
  }

  private fun buildSource(packageName: String, definition: SchemaDefinition): String {
    // 0. Boolean Schema (true/false)
    definition.booleanSchema?.let { booleanValue ->
      return DataClassGenerator.buildBooleanAliasSource(packageName, definition, booleanValue)
    }

    // 1. Pure $ref alias
    definition.ref?.let { ref ->
      return DataClassGenerator.buildRefAliasSource(packageName, definition, ref)
    }

    val dynamicContext = buildDynamicAnchorContext(definition)

    // 1. Enum Generation
    if (!definition.enumValues.isNullOrEmpty()) {
      val enumValues = definition.enumValues
      val allStrings = enumValues.all { it is OpenApiString }
      if (allStrings) {
        return EnumAndSealedGenerator.buildEnumSource(packageName, definition)
      }
      // Non-string enums cannot be represented as Kotlin enum classes.
      // Prefer a typealias to the base type and preserve values via KDoc tags.
      if (definition.properties.isEmpty()) {
        return DataClassGenerator.buildAliasSource(packageName, definition, dynamicContext)
      }
    }

    // 2. Polymorphic Parent (Sealed Interface)
    val hasCompositionRefs = (definition.oneOf + definition.anyOf).isNotEmpty()
    val hasCompositionSchemas = (definition.oneOfSchemas + definition.anyOfSchemas).isNotEmpty()
    if (hasCompositionRefs || hasCompositionSchemas) {
      return EnumAndSealedGenerator.buildSealedInterfaceSource(
          packageName, definition, dynamicContext)
    }

    // 3. Primitive / Array Aliases
    if (definition.primaryType != "object" && definition.properties.isEmpty()) {
      return DataClassGenerator.buildAliasSource(packageName, definition, dynamicContext)
    }

    // 4. Map/Dynamic Object Alias (additionalProperties only)
    if (definition.properties.isEmpty() && definition.additionalProperties != null) {
      return DataClassGenerator.buildMapAliasSource(packageName, definition, dynamicContext)
    }

    // 5. Standard Data Class
    return DataClassGenerator.buildDataClassSource(packageName, definition, dynamicContext)
  }
}
