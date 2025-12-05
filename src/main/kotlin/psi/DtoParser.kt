package psi

import domain.SchemaDefinition
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Service that parses Kotlin source code to extract OpenAPI [SchemaDefinition]s.
 * Analyzes Data Classes (Objects) and Enums.
 * Supports:
 * - KDoc comments -> 'description'
 * - @SerialName annotations -> overrides property name
 * - Nullability -> 'required' list
 */
class DtoParser {

    /**
     * Parses the provided Kotlin source code.
     * @param sourceCode The content of the Kotlin file.
     * @return A list of extracted Schema Definitions.
     */
    fun parse(sourceCode: String): List<SchemaDefinition> {
        val psiFactory = PsiInfrastructure.createPsiFactory()
        val file = psiFactory.createFile("Analysis.kt", sourceCode)

        val classes = file.collectDescendantsOfType<KtClass>()

        return classes.mapNotNull { ktClass ->
            when {
                ktClass.isEnum() -> parseEnum(ktClass)
                ktClass.isData() -> parseDataClass(ktClass)
                else -> null // Ignore non-data, non-enum classes
            }
        }
    }

    private fun parseEnum(ktClass: KtClass): SchemaDefinition {
        val name = ktClass.name ?: "UnknownEnum"
        val description = extractDoc(ktClass)

        // Extract enum entries
        val values = ktClass.declarations
            .filterIsInstance<org.jetbrains.kotlin.psi.KtEnumEntry>()
            .mapNotNull { it.name }

        return SchemaDefinition(
            name = name,
            type = "string", // Enums modeled as string sets
            enumValues = values,
            description = description
        )
    }

    private fun parseDataClass(ktClass: KtClass): SchemaDefinition {
        val name = ktClass.name ?: "UnknownClass"
        val description = extractDoc(ktClass)
        val properties = mutableMapOf<String, domain.SchemaProperty>()
        val requiredFields = mutableListOf<String>()

        val parameters = ktClass.primaryConstructor?.valueParameters ?: emptyList()

        parameters.forEach { param ->
            val kotlinType = param.typeReference?.text ?: "String"
            val rawName = param.name ?: "unknown"

            // Handle SerialName annotation: @SerialName("json_field")
            val serialNameEntry = param.annotationEntries.find {
                it.shortName?.asString() == "SerialName"
            }

            val jsonName = if (serialNameEntry != null) {
                val arg = serialNameEntry.valueArgumentList?.arguments?.firstOrNull()
                val expression = arg?.getArgumentExpression() as? KtStringTemplateExpression
                expression?.entries?.firstOrNull()?.text ?: rawName
            } else {
                rawName
            }

            // Map type
            val schemaProp = TypeMappers.kotlinToSchemaProperty(kotlinType).copy(
                description = extractDoc(param)
            )
            properties[jsonName] = schemaProp

            // Determine nullability. '?' means optional.
            if (!kotlinType.contains("?")) {
                requiredFields.add(jsonName)
            }
        }

        return SchemaDefinition(
            name = name,
            type = "object",
            properties = properties,
            required = requiredFields,
            description = description
        )
    }

    /**
     * cleanup KDoc: removes markers and stars, returning clean text.
     */
    private fun extractDoc(element: org.jetbrains.kotlin.psi.KtDeclaration): String? {
        val docComment = element.docComment ?: return null

        return docComment.text
            .split("\n")
            .map { line ->
                // Clean markers from content
                line.trim()
                    .replace("/**", "")
                    .replace("*/", "")
                    .replaceFirst("*", "") // match the asterisk bullet point
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { null }
    }
}
