/** Non-destructive PSI merger for updating Kotlin data class DTOs from OpenAPI schemas. */
package psi

import domain.CddError
import domain.SchemaDefinition
import domain.toFailure
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Service to merge new fields from an OpenAPI schema into an existing Kotlin Data Class. Uses PSI
 * to locate the injection point (Prop List), then performs safe text insertion to avoid 'HEADLESS'
 * environment crashes related to treeCopyHandler extension points.
 */
class DtoMerger {

  /**
   * Merges missing schema properties into an existing Kotlin data class source wrapped in a
   * [Result].
   *
   * @param existingCode Kotlin source containing the target class.
   * @param schema The schema definition to reconcile against the class.
   * @return A [Result] containing updated source code with any missing fields injected, or a
   *   [CddError.PsiMergeError].
   */
  fun mergeDto(existingCode: String, schema: SchemaDefinition): Result<String> {
    val psiFactory = PsiInfrastructure.createPsiFactory()
    val file = psiFactory.createFile("Fragment.kt", existingCode)

    // Find the target class
    val targetClass =
        file.collectDescendantsOfType<KtClass>().firstOrNull { it.name == schema.safeName }
            ?: return CddError.PsiMergeError(
                    schema.safeName, "Class ${schema.safeName} not found in provided code")
                .toFailure()

    // Validate structure
    val primaryConstructor =
        targetClass.primaryConstructor
            ?: return CddError.PsiMergeError(
                    schema.safeName, "Class ${schema.safeName} must have a primary constructor")
                .toFailure()
    val parameterList =
        primaryConstructor.valueParameterList
            ?: return CddError.PsiMergeError(
                    schema.safeName, "Class ${schema.safeName} must have a parameter list")
                .toFailure()

    // Identify existing properties
    val existingNames =
        parameterList.parameters.mapNotNull { it.name }.map { it.removeSurrounding("`") }.toSet()

    // Identify fields to add
    val missingProperties = schema.properties.filterKeys { !existingNames.contains(it) }

    if (missingProperties.isEmpty()) {
      return Result.success(existingCode)
    }

    // Generate the new code block
    val sb = StringBuilder()

    // Detect if we need a comma
    val hasExistingParams = parameterList.parameters.isNotEmpty()

    missingProperties.forEach { (name, prop) ->
      if (sb.isNotEmpty() || hasExistingParams) {
        // Formatting strategy:
        // If there were params, add comma.
        // Always add newline + indent (4 spaces) to match standard Kotlin guidelines
        sb.append(",\n    ")
      } else {
        // Empty list initially: just newline + indent
        sb.append("\n    ")
      }

      // Calculate Type and Null-defaulting based on new SchemaProperty.types (OAS 3.2)
      val baseType = TypeMappers.mapType(prop)
      val isExplicitlyNullable = prop.types.contains("null")
      val isRequired = schema.required.contains(name)

      // If explicit null type OR not in required list, it is nullable in Kotlin
      val isNullable = isExplicitlyNullable || !isRequired

      val finalType = if (isNullable) "$baseType?" else baseType
      val defaultVal = if (isNullable) " = null" else ""
      val safePropName = escapeKotlinKeyword(name)

      sb.append(
          "@kotlinx.serialization.SerialName(\"$name\") val $safePropName: $finalType$defaultVal")
    }

    // Calculate insertion point: The start of the closing parenthesis ')'
    // parameterList.lastChild is the closing parenthesis
    val closingParenthesis = parameterList.lastChild

    val insertionOffset = closingParenthesis.startOffset

    // Use StringBuilder to insert the new text into the original source
    val fileSource = StringBuilder(existingCode)
    fileSource.insert(insertionOffset, sb.toString())

    return Result.success(fileSource.toString())
  }
}
