package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.jetbrains.kotlin.psi.KtFile

/**
 * Generates Kotlin Data Transfer Objects (DTOs) from Schema Definitions.
 * Uses kotlinx.serialization annotations.
 * Updated for OAS 3.2 Dual/Array Types support, additionalProperties maps,
 * and OAS 3.1 Content Encoding.
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

        // 3. Primitive / Array Aliases
        if (definition.primaryType != "object") {
            return buildAliasSource(packageName, definition)
        }

        // 4. Map/Dynamic Object Alias (additionalProperties only)
        if (definition.primaryType == "object" && definition.properties.isEmpty() && definition.additionalProperties != null) {
            return buildMapAliasSource(packageName, definition)
        }

        // 5. Standard Data Class
        return buildDataClassSource(packageName, definition)
    }

    private fun buildAliasSource(packageName: String, definition: SchemaDefinition): String {
        val imports = mutableSetOf<String>()
        addTypeImports(imports, definition)

        val kdoc = buildClassKDoc(definition)
        val deprecatedAnnotation = buildDeprecatedAnnotation(definition.deprecated)
        val prop = schemaToProperty(definition)
        val baseType = TypeMappers.mapType(prop)
        val isNullable = prop.types.contains("null")
        val kotlinType = if (isNullable) "$baseType?" else baseType

        return """
            package $packageName

            ${imports.sorted().joinToString("\n") { "import $it" }}

            $kdoc$deprecatedAnnotation
            typealias ${definition.name} = $kotlinType
        """.trimIndent()
    }

    private fun buildMapAliasSource(packageName: String, definition: SchemaDefinition): String {
        val imports = mutableSetOf<String>()
        addTypeImports(imports, definition)

        val kdoc = buildClassKDoc(definition)
        val deprecatedAnnotation = buildDeprecatedAnnotation(definition.deprecated)
        val valueType = TypeMappers.mapType(definition.additionalProperties!!)

        return """
            package $packageName

            ${imports.sorted().joinToString("\n") { "import $it" }}

            $kdoc$deprecatedAnnotation
            typealias ${definition.name} = Map<String, $valueType>
        """.trimIndent()
    }

    private fun buildSealedInterfaceSource(packageName: String, definition: SchemaDefinition): String {
        val imports = mutableSetOf(
            "kotlinx.serialization.Serializable",
            "kotlinx.serialization.JsonClassDiscriminator",
            "kotlinx.serialization.ExperimentalSerializationApi"
        )
        addTypeImports(imports, definition)

        val kdoc = buildClassKDoc(definition)
        val deprecatedAnnotation = buildDeprecatedAnnotation(definition.deprecated)

        val discriminatorAnnotation = if (definition.discriminator != null) {
            "@OptIn(ExperimentalSerializationApi::class)\n@JsonClassDiscriminator(\"${definition.discriminator.propertyName}\")"
        } else {
            ""
        }

        val propertyLines = definition.properties.map { (name, prop) ->
            val kotlinType = resolveKotlinType(name, prop, definition.required)
            val desc = buildPropertyKDoc(prop)
            val deprecatedPropAnnotation = buildDeprecatedAnnotation(prop.deprecated, indent = "    ")
            "$desc$deprecatedPropAnnotation    val $name: $kotlinType"
        }.joinToString("\n\n")

        return """ 
            package $packageName

            ${imports.sorted().joinToString("\n") { "import $it" }} 

            $kdoc$deprecatedAnnotation@Serializable
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
            val deprecatedPropAnnotation = buildDeprecatedAnnotation(prop.deprecated, indent = "    ")
            val serialName = "    @SerialName(\"$name\")"

            "$desc$deprecatedPropAnnotation$serialName\n    val $name: $kotlinType$defaultVal"
        }.joinToString(",\n\n")

        val kdoc = buildClassKDoc(definition)
        val deprecatedAnnotation = buildDeprecatedAnnotation(definition.deprecated)

        return """ 
            package $packageName
            
            ${imports.sorted().joinToString("\n") { "import $it" }} 
            
            $kdoc$deprecatedAnnotation@Serializable
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
        val deprecatedAnnotation = buildDeprecatedAnnotation(definition.deprecated)

        val entries = definition.enumValues!!.joinToString(",\n\n") { value ->
            val validName = sanitizeEnumName(value)
            val annotation = "    @SerialName(\"$value\")"
            "$annotation\n    $validName"
        }

        return """ 
            package $packageName

            ${imports.sorted().joinToString("\n") { "import $it" }} 

            $kdoc$deprecatedAnnotation@Serializable
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

    private fun buildClassKDoc(definition: SchemaDefinition): String {
        val hasDesc = !definition.description.isNullOrBlank()
        val hasExtDocs = definition.externalDocs != null
        val hasExample = definition.example != null
        val hasExamples = !definition.examples.isNullOrEmpty()
        val hasMediaType = definition.contentMediaType != null
        val hasEncoding = definition.contentEncoding != null
        val hasTitle = !definition.title.isNullOrBlank()
        val hasDefault = definition.defaultValue != null
        val hasConst = definition.constValue != null
        val hasDeprecated = definition.deprecated
        val hasReadOnly = definition.readOnly
        val hasWriteOnly = definition.writeOnly

        if (!hasDesc && !hasExtDocs && !hasExample && !hasExamples && !hasMediaType && !hasEncoding &&
            !hasTitle && !hasDefault && !hasConst && !hasDeprecated && !hasReadOnly && !hasWriteOnly
        ) return ""

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

        if (hasTitle) {
            if (hasDesc || hasExtDocs || hasExample || hasExamples) sb.append(" *\n")
            sb.append(" * @title ${definition.title}\n")
        }

        if (hasDefault) {
            if (hasDesc || hasExtDocs || hasExample || hasExamples || hasTitle) sb.append(" *\n")
            sb.append(" * @default ${definition.defaultValue}\n")
        }

        if (hasConst) {
            if (hasDesc || hasExtDocs || hasExample || hasExamples || hasTitle || hasDefault) sb.append(" *\n")
            sb.append(" * @const ${definition.constValue}\n")
        }

        if (hasDeprecated) {
            if (hasDesc || hasExtDocs || hasExample || hasExamples || hasTitle || hasDefault || hasConst) sb.append(" *\n")
            sb.append(" * @deprecated\n")
        }

        if (hasReadOnly) {
            if (hasDesc || hasExtDocs || hasExample || hasExamples || hasTitle || hasDefault || hasConst || hasDeprecated) sb.append(" *\n")
            sb.append(" * @readOnly\n")
        }

        if (hasWriteOnly) {
            if (hasDesc || hasExtDocs || hasExample || hasExamples || hasTitle || hasDefault || hasConst || hasDeprecated || hasReadOnly) sb.append(" *\n")
            sb.append(" * @writeOnly\n")
        }

        if (hasMediaType) {
            if (hasDesc || hasExtDocs || hasExample || hasExamples || hasTitle || hasDefault || hasConst || hasDeprecated || hasReadOnly || hasWriteOnly) {
                sb.append(" *\n")
            }
            sb.append(" * @contentMediaType ${definition.contentMediaType}\n")
        }

        if (hasEncoding) {
            if (hasDesc || hasExtDocs || hasExample || hasExamples || hasTitle || hasDefault || hasConst || hasDeprecated || hasReadOnly || hasWriteOnly || hasMediaType) {
                sb.append(" *\n")
            }
            sb.append(" * @contentEncoding ${definition.contentEncoding}\n")
        }

        sb.append(" */\n")
        return sb.toString()
    }

    private fun schemaToProperty(definition: SchemaDefinition): SchemaProperty {
        val effectiveTypes = definition.effectiveTypes
        return SchemaProperty(
            types = effectiveTypes,
            format = definition.format,
            contentMediaType = definition.contentMediaType,
            contentEncoding = definition.contentEncoding,
            items = definition.items,
            additionalProperties = definition.additionalProperties,
            description = definition.description,
            title = definition.title,
            defaultValue = definition.defaultValue,
            constValue = definition.constValue,
            deprecated = definition.deprecated,
            readOnly = definition.readOnly,
            writeOnly = definition.writeOnly,
            example = definition.example,
            xml = definition.xml
        )
    }

    private fun buildPropertyKDoc(prop: SchemaProperty): String {
        val hasDesc = !prop.description.isNullOrBlank()
        val hasExample = prop.example != null
        val hasMediaType = prop.contentMediaType != null
        val hasEncoding = prop.contentEncoding != null
        val hasTitle = !prop.title.isNullOrBlank()
        val hasDefault = prop.defaultValue != null
        val hasConst = prop.constValue != null
        val hasDeprecated = prop.deprecated
        val hasReadOnly = prop.readOnly
        val hasWriteOnly = prop.writeOnly

        if (!hasDesc && !hasExample && !hasMediaType && !hasEncoding && !hasTitle && !hasDefault && !hasConst &&
            !hasDeprecated && !hasReadOnly && !hasWriteOnly
        ) return ""

        val sb = StringBuilder("    /**\n")
        if (hasDesc) {
            sb.append("     * ${prop.description}\n")
        }
        if (hasTitle) {
            if (hasDesc) sb.append("     *\n")
            sb.append("     * @title ${prop.title}\n")
        }
        if (hasDefault) {
            if (hasDesc || hasTitle) sb.append("     *\n")
            sb.append("     * @default ${prop.defaultValue}\n")
        }
        if (hasConst) {
            if (hasDesc || hasTitle || hasDefault) sb.append("     *\n")
            sb.append("     * @const ${prop.constValue}\n")
        }
        if (hasDeprecated) {
            if (hasDesc || hasTitle || hasDefault || hasConst) sb.append("     *\n")
            sb.append("     * @deprecated\n")
        }
        if (hasReadOnly) {
            if (hasDesc || hasTitle || hasDefault || hasConst || hasDeprecated) sb.append("     *\n")
            sb.append("     * @readOnly\n")
        }
        if (hasWriteOnly) {
            if (hasDesc || hasTitle || hasDefault || hasConst || hasDeprecated || hasReadOnly) sb.append("     *\n")
            sb.append("     * @writeOnly\n")
        }
        if (hasMediaType) {
            if (hasDesc || hasTitle || hasDefault || hasConst || hasDeprecated || hasReadOnly || hasWriteOnly) sb.append("     *\n")
            sb.append("     * @contentMediaType ${prop.contentMediaType}\n")
        }
        if (hasEncoding) {
            if (hasDesc || hasTitle || hasDefault || hasConst || hasDeprecated || hasReadOnly || hasWriteOnly || hasMediaType) {
                sb.append("     *\n")
            }
            sb.append("     * @contentEncoding ${prop.contentEncoding}\n")
        }
        if (hasExample) {
            if (hasDesc || hasTitle || hasDefault || hasConst || hasDeprecated || hasReadOnly || hasWriteOnly || hasMediaType || hasEncoding) {
                sb.append("     *\n")
            }
            sb.append("     * @example ${prop.example}\n")
        }
        sb.append("     */\n")
        return sb.toString()
    }

    private fun buildDeprecatedAnnotation(isDeprecated: Boolean, indent: String = ""): String {
        return if (isDeprecated) "${indent}@Deprecated(\"Deprecated\")\n" else ""
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
