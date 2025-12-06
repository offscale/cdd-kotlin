package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.jetbrains.kotlin.psi.KtFile

/**
 * Generates Kotlin Data Transfer Objects (DTOs) from Schema Definitions.
 * Uses kotlinx.serialization annotations.
 * Updated for OAS 3.2 Dual/Array Types support and OAS 3.1 Content Encoding.
 */
class DtoGenerator {

    private val psiFactory = PsiInfrastructure.createPsiFactory()

    /**
     * Generates a Kotlin file containing the Data Class, Enum, or Sealed Interface for the given Schema.
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
        // 1. Enum Generation
        if (!definition.enumValues.isNullOrEmpty()) {
            return buildEnumSource(packageName, definition)
        }

        // 2. Polymorphic Parent (Sealed Interface)
        if (definition.oneOf.isNotEmpty() || definition.anyOf.isNotEmpty()) {
            return buildSealedInterfaceSource(packageName, definition)
        }

        // 3. Standard Data Class
        return buildDataClassSource(packageName, definition)
    }

    private fun buildSealedInterfaceSource(packageName: String, definition: SchemaDefinition): String {
        val imports = mutableSetOf(
            "kotlinx.serialization.Serializable",
            "kotlinx.serialization.JsonClassDiscriminator",
            "kotlinx.serialization.ExperimentalSerializationApi"
        )
        addTypeImports(imports, definition)

        val kdoc = buildClassKDoc(definition)

        val discriminatorAnnotation = if (definition.discriminator != null) {
            "@OptIn(ExperimentalSerializationApi::class)\n@JsonClassDiscriminator(\"${definition.discriminator.propertyName}\")"
        } else {
            ""
        }

        val propertyLines = definition.properties.map { (name, prop) ->
            val kotlinType = resolveKotlinType(name, prop, definition.required)
            val desc = buildPropertyKDoc(prop)
            "$desc    val $name: $kotlinType"
        }.joinToString("\n\n")

        return """ 
            package $packageName

            ${imports.sorted().joinToString("\n") { "import $it" }} 

            $kdoc@Serializable
            $discriminatorAnnotation
            sealed interface ${definition.name} { 
            $propertyLines
            } 
        """.trimIndent()
    }

    private fun buildDataClassSource(packageName: String, definition: SchemaDefinition): String {
        val imports = mutableSetOf<String>()
        imports.add("kotlinx.serialization.Serializable")
        imports.add("kotlinx.serialization.SerialName")
        addTypeImports(imports, definition)

        val parents = definition.allOf.map { ReferenceResolver.resolveRefToType(it) }
        val inheritanceClause = if (parents.isNotEmpty()) {
            " : ${parents.joinToString(", ")}"
        } else {
            ""
        }

        val propertyLines = definition.properties.map { (name, prop) ->
            val kotlinType = resolveKotlinType(name, prop, definition.required)
            val defaultVal = if (kotlinType.endsWith("?")) " = null" else ""

            val desc = buildPropertyKDoc(prop)
            val serialName = "    @SerialName(\"$name\")"

            "$desc$serialName\n    val $name: $kotlinType$defaultVal"
        }.joinToString(",\n\n")

        val kdoc = buildClassKDoc(definition)

        return """ 
            package $packageName
            
            ${imports.sorted().joinToString("\n") { "import $it" }} 
            
            $kdoc@Serializable
            data class ${definition.name}( 
            $propertyLines
            )$inheritanceClause
        """.trimIndent()
    }

    private fun buildEnumSource(packageName: String, definition: SchemaDefinition): String {
        val imports = mutableSetOf(
            "kotlinx.serialization.Serializable",
            "kotlinx.serialization.SerialName"
        )
        val kdoc = buildClassKDoc(definition)

        val entries = definition.enumValues!!.joinToString(",\n\n") { value ->
            val validName = sanitizeEnumName(value)
            val annotation = "    @SerialName(\"$value\")"
            "$annotation\n    $validName"
        }

        return """ 
            package $packageName

            ${imports.sorted().joinToString("\n") { "import $it" }} 

            $kdoc@Serializable
            enum class ${definition.name} { 
            $entries
            } 
        """.trimIndent()
    }

    private fun resolveKotlinType(name: String, prop: SchemaProperty, requiredList: List<String>): String {
        val baseType = TypeMappers.mapType(prop)

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

    private fun addTypeImports(imports: MutableSet<String>, definition: SchemaDefinition) {
        val formats = definition.properties.values.mapNotNull { it.format }

        if (formats.contains("date")) {
            imports.add("kotlinx.datetime.LocalDate")
        }
        if (formats.contains("date-time")) {
            imports.add("kotlinx.datetime.Instant")
        }
    }

    private fun buildClassKDoc(definition: SchemaDefinition): String {
        val hasDesc = !definition.description.isNullOrBlank()
        val hasExtDocs = definition.externalDocs != null
        val hasExample = definition.example != null
        val hasExamples = !definition.examples.isNullOrEmpty()

        if (!hasDesc && !hasExtDocs && !hasExample && !hasExamples) return ""

        val sb = StringBuilder("/**\n")
        if (hasDesc) {
            sb.append(" * ${definition.description}\n")
        }

        if (hasExtDocs) {
            if (hasDesc) sb.append(" *\n")
            val docs = definition.externalDocs
            sb.append(" * @see ${docs.url}")
            if (docs.description != null) {
                sb.append(" ${docs.description}")
            }
            sb.append("\n")
        }

        if (hasExample) {
            if (hasDesc || hasExtDocs) sb.append(" *\n")
            sb.append(" * @example ${definition.example}\n")
        }

        if (hasExamples) {
            if (hasDesc || hasExtDocs || hasExample) sb.append(" *\n")
            definition.examples.forEach { (k, v) ->
                sb.append(" * @example $k: $v\n")
            }
        }

        sb.append(" */\n")
        return sb.toString()
    }

    private fun buildPropertyKDoc(prop: SchemaProperty): String {
        val hasDesc = !prop.description.isNullOrBlank()
        val hasExample = prop.example != null
        val hasMediaType = prop.contentMediaType != null
        val hasEncoding = prop.contentEncoding != null

        if (!hasDesc && !hasExample && !hasMediaType && !hasEncoding) return ""

        val sb = StringBuilder("    /**\n")
        if (hasDesc) {
            sb.append("     * ${prop.description}\n")
        }
        if (hasMediaType) {
            if (hasDesc) sb.append("     *\n")
            sb.append("     * @contentMediaType ${prop.contentMediaType}\n")
        }
        if (hasEncoding) {
            if (hasDesc || hasMediaType) sb.append("     *\n")
            sb.append("     * @contentEncoding ${prop.contentEncoding}\n")
        }
        if (hasExample) {
            if (hasDesc || hasMediaType || hasEncoding) sb.append("     *\n")
            sb.append("     * @example ${prop.example}\n")
        }
        sb.append("     */\n")
        return sb.toString()
    }

    private fun sanitizeEnumName(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9]"), " ")
        var ident = cleaned.split(" ")
            .filter { it.isNotEmpty() }
            .joinToString("") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }

        if (ident.isEmpty()) ident = "Unknown"
        if (ident.first().isDigit()) {
            ident = "_$ident"
        }
        return ident
    }
}
