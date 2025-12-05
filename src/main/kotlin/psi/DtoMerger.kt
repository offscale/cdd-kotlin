package psi

import domain.SchemaDefinition
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Service to merge new fields from an OpenAPI schema into an existing Kotlin Data Class.
 * Uses PSI to locate the injection point (Prop List), then performs safe text insertion
 * to avoid 'HEADLESS' environment crashes related to treeCopyHandler extension points.
 */
class DtoMerger {

    fun mergeDto(existingCode: String, schema: SchemaDefinition): String {
        val psiFactory = PsiInfrastructure.createPsiFactory()
        val file = psiFactory.createFile("Fragment.kt", existingCode)

        // Find the target class
        val targetClass = file.collectDescendantsOfType<KtClass>()
            .firstOrNull { it.name == schema.name }
            ?: throw IllegalArgumentException("Class ${schema.name} not found in provided code")

        // Validate structure
        val primaryConstructor = targetClass.primaryConstructor
            ?: throw IllegalStateException("Class ${schema.name} must have a primary constructor")

        val parameterList = primaryConstructor.valueParameterList
            ?: throw IllegalStateException("Class ${schema.name} constructor config error")

        // Identify existing properties
        val existingNames = parameterList.parameters.mapNotNull { it.name }.toSet()

        // Identify fields to add
        val missingProperties = schema.properties.filterKeys { !existingNames.contains(it) }

        if (missingProperties.isEmpty()) {
            return existingCode
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

            val isRequired = schema.required.contains(name)
            val type = TypeMappers.mapType(prop)
            val finalType = if (isRequired) type else "$type?"
            val defaultVal = if (!isRequired) " = null" else ""

            sb.append("@kotlinx.serialization.SerialName(\"$name\") val $name: $finalType$defaultVal")
        }

        // Calculate insertion point: The start of the closing parenthesis ')'
        // parameterList.lastChild is the closing parenthesis
        val closingParenthesis = parameterList.lastChild
            ?: throw IllegalStateException("Invalid parameter list structure")

        val insertionOffset = closingParenthesis.startOffset

        // Use StringBuilder to insert the new text into the original source
        val fileSource = StringBuilder(existingCode)
        fileSource.insert(insertionOffset, sb.toString())

        return fileSource.toString()
    }
}
