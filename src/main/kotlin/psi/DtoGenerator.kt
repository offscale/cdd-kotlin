package psi

import domain.SchemaDefinition
import org.jetbrains.kotlin.psi.KtFile

/**
 * Generates Kotlin Data Transfer Objects (DTOs) from Schema Definitions.
 * Uses kotlinx.serialization annotations.
 */
class DtoGenerator {

    private val psiFactory = PsiInfrastructure.createPsiFactory()

    /**
     * Generates a Kotlin file containing the Data Class for the given Schema.
     *
     * @param packageName The package name for the generated class.
     * @param definition The schema definition containing properties and requirements.
     * @return A PSI KtFile representing the generated source code.
     */
    fun generateDto(packageName: String, definition: SchemaDefinition): KtFile {
        val content = buildSource(packageName, definition)
        return psiFactory.createFile("${definition.name}.kt", content)
    }

    private fun buildSource(packageName: String, definition: SchemaDefinition): String {
        val imports = mutableSetOf<String>()
        imports.add("kotlinx.serialization.Serializable")
        imports.add("kotlinx.serialization.SerialName")

        val propertyLines = definition.properties.map { (name, prop) ->
            val type = mapType(prop.type, prop.format)
            val isRequired = definition.required.contains(name)

            // If not required, it is nullable and defaults to null
            val typeStr = if (isRequired) type else "$type? = null"

            val desc = if (prop.description != null) "    /** ${prop.description} */\n" else ""
            val serialName = "    @SerialName(\"$name\")"

            "$desc$serialName\n    val $name: $typeStr"
        }.joinToString(",\n\n")

        val classDesc = if (definition.description != null) "/** ${definition.description} */\n" else ""

        return """
            package $packageName
            
            ${imports.sorted().joinToString("\n") { "import $it" }}
            
            $classDesc@Serializable
            data class ${definition.name}(
            $propertyLines
            )
        """.trimIndent()
    }

    private fun mapType(type: String, format: String?): String {
        return when (type) {
            "integer" -> if (format == "int64") "Long" else "Int"
            "number" -> "Double"
            "boolean" -> "Boolean"
            "string" -> "String"
            "array" -> "List<String>" // Basic support for lists of strings for now
            else -> "String" // Fallback for unknown types or object references
        }
    }
}
