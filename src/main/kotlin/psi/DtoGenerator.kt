package psi

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
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
    private val jsonMapper = ObjectMapper(JsonFactory())

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
        // 0. Boolean Schema (true/false)
        definition.booleanSchema?.let { booleanValue ->
            return buildBooleanAliasSource(packageName, definition, booleanValue)
        }

        // 1. Pure $ref alias
        definition.ref?.let { ref ->
            return buildRefAliasSource(packageName, definition, ref)
        }

        // 1. Enum Generation
        if (!definition.enumValues.isNullOrEmpty()) {
            val enumValues = definition.enumValues!!
            val allStrings = enumValues.all { it is String }
            if (allStrings) {
                return buildEnumSource(packageName, definition)
            }
            // Non-string enums cannot be represented as Kotlin enum classes.
            // Prefer a typealias to the base type and preserve values via KDoc tags.
            if (definition.primaryType != "object" || definition.properties.isEmpty()) {
                return buildAliasSource(packageName, definition)
            }
        }

        // 2. Polymorphic Parent (Sealed Interface)
        if (definition.oneOf.isNotEmpty() || definition.anyOf.isNotEmpty() ||
            definition.oneOfSchemas.isNotEmpty() || definition.anyOfSchemas.isNotEmpty()
        ) {
            return buildSealedInterfaceSource(packageName, definition)
        }

        // 3. Primitive / Array Aliases
        if (definition.primaryType != "object" && definition.properties.isEmpty()) {
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

    private fun buildBooleanAliasSource(packageName: String, definition: SchemaDefinition, value: Boolean): String {
        val kdoc = buildClassKDoc(definition)
        val deprecatedAnnotation = buildDeprecatedAnnotation(definition.deprecated)
        val kotlinType = if (value) "Any" else "Nothing"

        return """
            package $packageName

            $kdoc$deprecatedAnnotation
            typealias ${definition.name} = $kotlinType
        """.trimIndent()
    }

    private fun buildRefAliasSource(packageName: String, definition: SchemaDefinition, ref: String): String {
        val kdoc = buildClassKDoc(definition)
        val deprecatedAnnotation = buildDeprecatedAnnotation(definition.deprecated)
        val target = ReferenceResolver.resolveRefToType(ref)

        return """
            package $packageName

            $kdoc$deprecatedAnnotation
            typealias ${definition.name} = $target
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

        val values = definition.enumValues!!.map { it as String }
        val entries = values.joinToString(",\n\n") { value ->
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
        val hasExamplesList = !definition.examplesList.isNullOrEmpty()
        val hasEnum = !definition.enumValues.isNullOrEmpty()
        val hasMediaType = definition.contentMediaType != null
        val hasEncoding = definition.contentEncoding != null
        val hasTitle = !definition.title.isNullOrBlank()
        val hasDefault = definition.defaultValue != null
        val hasConst = definition.constValue != null
        val hasDeprecated = definition.deprecated
        val hasReadOnly = definition.readOnly
        val hasWriteOnly = definition.writeOnly
        val hasSchemaId = !definition.schemaId.isNullOrBlank()
        val hasSchemaDialect = !definition.schemaDialect.isNullOrBlank()
        val hasAnchor = !definition.anchor.isNullOrBlank()
        val hasDynamicAnchor = !definition.dynamicAnchor.isNullOrBlank()
        val hasDynamicRef = !definition.dynamicRef.isNullOrBlank()
        val hasDefs = definition.defs.isNotEmpty()
        val hasMinLength = definition.minLength != null
        val hasMaxLength = definition.maxLength != null
        val hasPattern = definition.pattern != null
        val hasMinimum = definition.minimum != null
        val hasMaximum = definition.maximum != null
        val hasMultipleOf = definition.multipleOf != null
        val hasExclusiveMinimum = definition.exclusiveMinimum != null
        val hasExclusiveMaximum = definition.exclusiveMaximum != null
        val hasMinItems = definition.minItems != null
        val hasMaxItems = definition.maxItems != null
        val hasUniqueItems = definition.uniqueItems != null
        val hasMinProperties = definition.minProperties != null
        val hasMaxProperties = definition.maxProperties != null
        val hasMinContains = definition.minContains != null
        val hasMaxContains = definition.maxContains != null
        val hasContains = definition.contains != null
        val hasPrefixItems = definition.prefixItems.isNotEmpty()
        val hasDiscriminator = definition.discriminator != null
        val hasXml = definition.xml != null
        val hasComment = !definition.comment.isNullOrBlank()
        val hasPatternProperties = definition.patternProperties.isNotEmpty()
        val hasPropertyNames = definition.propertyNames != null
        val hasDependentRequired = definition.dependentRequired.isNotEmpty()
        val hasDependentSchemas = definition.dependentSchemas.isNotEmpty()
        val hasUnevaluatedProperties = definition.unevaluatedProperties != null
        val hasUnevaluatedItems = definition.unevaluatedItems != null
        val hasContentSchema = definition.contentSchema != null
        val hasCustomKeywords = definition.customKeywords.isNotEmpty()

        if (!hasDesc && !hasExtDocs && !hasExample && !hasExamples && !hasExamplesList && !hasEnum && !hasMediaType && !hasEncoding &&
            !hasTitle && !hasDefault && !hasConst && !hasDeprecated && !hasReadOnly && !hasWriteOnly &&
            !hasSchemaId && !hasSchemaDialect && !hasAnchor && !hasDynamicAnchor && !hasDynamicRef && !hasDefs &&
            !hasMinLength && !hasMaxLength && !hasPattern && !hasMinimum && !hasMaximum &&
            !hasMultipleOf && !hasExclusiveMinimum && !hasExclusiveMaximum &&
            !hasMinItems && !hasMaxItems && !hasUniqueItems && !hasMinProperties && !hasMaxProperties &&
            !hasMinContains && !hasMaxContains && !hasContains && !hasPrefixItems && !hasDiscriminator && !hasXml &&
            !hasComment && !hasPatternProperties && !hasPropertyNames &&
            !hasDependentRequired && !hasDependentSchemas &&
            !hasUnevaluatedProperties && !hasUnevaluatedItems && !hasContentSchema && !hasCustomKeywords
        ) return ""

        val sb = StringBuilder("/**\n")
        var hasAny = false
        fun appendLine(line: String, blankBefore: Boolean = true) {
            if (hasAny && blankBefore) sb.append(" *\n")
            sb.append(" * ").append(line).append("\n")
            hasAny = true
        }

        if (hasDesc) appendLine(definition.description!!, blankBefore = false)

        if (hasExtDocs) {
            val docs = definition.externalDocs!!
            val suffix = if (docs.description != null) " ${docs.description}" else ""
            appendLine("@see ${docs.url}$suffix")
        }

        if (hasDiscriminator) {
            val disc = definition.discriminator!!
            appendLine("@discriminator ${disc.propertyName}")
            if (disc.mapping.isNotEmpty()) {
                appendLine("@discriminatorMapping ${jsonMapper.writeValueAsString(disc.mapping)}")
            }
            if (disc.defaultMapping != null) {
                appendLine("@discriminatorDefault ${disc.defaultMapping}")
            }
        }

        if (hasExample) appendLine("@example ${renderDocValue(definition.example)}")
        if (hasExamplesList) appendLine("@examples ${renderExamplesList(definition.examplesList!!)}")

        if (hasExamples) {
            definition.examples.forEach { (k, v) ->
                appendLine("@example $k: ${renderDocValue(v)}")
            }
        }
        if (hasEnum) {
            definition.enumValues!!.forEach { value ->
                appendLine("@enum ${renderEnumValue(value)}")
            }
        }

        if (hasTitle) appendLine("@title ${definition.title}")
        if (hasDefault) appendLine("@default ${renderDocValue(definition.defaultValue)}")
        if (hasConst) appendLine("@const ${renderDocValue(definition.constValue)}")
        if (hasComment) appendLine("@comment ${definition.comment}")
        if (hasSchemaId) appendLine("@schemaId ${definition.schemaId}")
        if (hasSchemaDialect) appendLine("@schemaDialect ${definition.schemaDialect}")
        if (hasAnchor) appendLine("@anchor ${definition.anchor}")
        if (hasDynamicAnchor) appendLine("@dynamicAnchor ${definition.dynamicAnchor}")
        if (hasDynamicRef) appendLine("@dynamicRef ${definition.dynamicRef}")
        if (hasDefs) appendLine("@defs ${renderSchemaMap(definition.defs)}")

        if (hasMinLength) appendLine("@minLength ${definition.minLength}")
        if (hasMaxLength) appendLine("@maxLength ${definition.maxLength}")
        if (hasPattern) appendLine("@pattern ${definition.pattern}")
        if (hasMinimum) appendLine("@minimum ${definition.minimum}")
        if (hasMaximum) appendLine("@maximum ${definition.maximum}")
        if (hasMultipleOf) appendLine("@multipleOf ${definition.multipleOf}")
        if (hasExclusiveMinimum) appendLine("@exclusiveMinimum ${definition.exclusiveMinimum}")
        if (hasExclusiveMaximum) appendLine("@exclusiveMaximum ${definition.exclusiveMaximum}")
        if (hasMinItems) appendLine("@minItems ${definition.minItems}")
        if (hasMaxItems) appendLine("@maxItems ${definition.maxItems}")
        if (hasUniqueItems) appendLine("@uniqueItems ${definition.uniqueItems}")
        if (hasMinProperties) appendLine("@minProperties ${definition.minProperties}")
        if (hasMaxProperties) appendLine("@maxProperties ${definition.maxProperties}")
        if (hasMinContains) appendLine("@minContains ${definition.minContains}")
        if (hasMaxContains) appendLine("@maxContains ${definition.maxContains}")
        if (hasContains) appendLine("@contains ${renderSchema(definition.contains!!)}")
        if (hasPrefixItems) appendLine("@prefixItems ${renderSchemaList(definition.prefixItems)}")

        if (hasPatternProperties) {
            appendLine("@patternProperties ${renderSchemaMap(definition.patternProperties)}")
        }
        if (hasPropertyNames) {
            appendLine("@propertyNames ${renderSchema(definition.propertyNames!!)}")
        }
        if (hasDependentRequired) {
            appendLine("@dependentRequired ${renderStringListMap(definition.dependentRequired)}")
        }
        if (hasDependentSchemas) {
            appendLine("@dependentSchemas ${renderSchemaMap(definition.dependentSchemas)}")
        }
        if (hasUnevaluatedProperties) {
            appendLine("@unevaluatedProperties ${renderSchema(definition.unevaluatedProperties!!)}")
        }
        if (hasUnevaluatedItems) {
            appendLine("@unevaluatedItems ${renderSchema(definition.unevaluatedItems!!)}")
        }
        if (hasContentSchema) {
            appendLine("@contentSchema ${renderSchema(definition.contentSchema!!)}")
        }
        if (hasCustomKeywords) {
            appendLine("@keywords ${renderCustomKeywords(definition.customKeywords)}")
        }

        if (hasDeprecated) appendLine("@deprecated")
        if (hasReadOnly) appendLine("@readOnly")
        if (hasWriteOnly) appendLine("@writeOnly")
        if (hasMediaType) appendLine("@contentMediaType ${definition.contentMediaType}")
        if (hasEncoding) appendLine("@contentEncoding ${definition.contentEncoding}")

        if (hasXml) {
            val xml = definition.xml!!
            xml.name?.let { appendLine("@xmlName $it") }
            xml.namespace?.let { appendLine("@xmlNamespace $it") }
            xml.prefix?.let { appendLine("@xmlPrefix $it") }
            xml.nodeType?.let { appendLine("@xmlNodeType $it") }
            if (xml.attribute) appendLine("@xmlAttribute")
            if (xml.wrapped) appendLine("@xmlWrapped")
        }

        sb.append(" */\n")
        return sb.toString()
    }

    private fun schemaToProperty(definition: SchemaDefinition): SchemaProperty {
        val effectiveTypes = definition.effectiveTypes
        return SchemaProperty(
            booleanSchema = definition.booleanSchema,
            types = effectiveTypes,
            format = definition.format,
            contentMediaType = definition.contentMediaType,
            contentEncoding = definition.contentEncoding,
            minLength = definition.minLength,
            maxLength = definition.maxLength,
            pattern = definition.pattern,
            minimum = definition.minimum,
            maximum = definition.maximum,
            multipleOf = definition.multipleOf,
            exclusiveMinimum = definition.exclusiveMinimum,
            exclusiveMaximum = definition.exclusiveMaximum,
            minItems = definition.minItems,
            maxItems = definition.maxItems,
            uniqueItems = definition.uniqueItems,
            minProperties = definition.minProperties,
            maxProperties = definition.maxProperties,
            items = definition.items,
            additionalProperties = definition.additionalProperties,
            ref = definition.ref,
            dynamicRef = definition.dynamicRef,
            description = definition.description,
            title = definition.title,
            defaultValue = definition.defaultValue,
            constValue = definition.constValue,
            enumValues = definition.enumValues,
            deprecated = definition.deprecated,
            readOnly = definition.readOnly,
            writeOnly = definition.writeOnly,
            externalDocs = definition.externalDocs,
            discriminator = definition.discriminator,
            example = definition.example,
            xml = definition.xml,
            prefixItems = definition.prefixItems,
            contains = definition.contains,
            patternProperties = definition.patternProperties,
            propertyNames = definition.propertyNames,
            dependentRequired = definition.dependentRequired,
            dependentSchemas = definition.dependentSchemas,
            unevaluatedProperties = definition.unevaluatedProperties,
            unevaluatedItems = definition.unevaluatedItems,
            contentSchema = definition.contentSchema,
            customKeywords = definition.customKeywords,
            defs = definition.defs
        )
    }

    private fun buildPropertyKDoc(prop: SchemaProperty): String {
        val hasDesc = !prop.description.isNullOrBlank()
        val hasExample = prop.example != null
        val hasExamples = !prop.examples.isNullOrEmpty()
        val hasEnum = !prop.enumValues.isNullOrEmpty()
        val hasExtDocs = prop.externalDocs != null
        val hasDiscriminator = prop.discriminator != null
        val hasMediaType = prop.contentMediaType != null
        val hasEncoding = prop.contentEncoding != null
        val hasTitle = !prop.title.isNullOrBlank()
        val hasDefault = prop.defaultValue != null
        val hasConst = prop.constValue != null
        val hasDeprecated = prop.deprecated
        val hasReadOnly = prop.readOnly
        val hasWriteOnly = prop.writeOnly
        val hasSchemaId = !prop.schemaId.isNullOrBlank()
        val hasSchemaDialect = !prop.schemaDialect.isNullOrBlank()
        val hasAnchor = !prop.anchor.isNullOrBlank()
        val hasDynamicAnchor = !prop.dynamicAnchor.isNullOrBlank()
        val hasDynamicRef = !prop.dynamicRef.isNullOrBlank()
        val hasDefs = prop.defs.isNotEmpty()
        val hasMinLength = prop.minLength != null
        val hasMaxLength = prop.maxLength != null
        val hasPattern = prop.pattern != null
        val hasMinimum = prop.minimum != null
        val hasMaximum = prop.maximum != null
        val hasMultipleOf = prop.multipleOf != null
        val hasExclusiveMinimum = prop.exclusiveMinimum != null
        val hasExclusiveMaximum = prop.exclusiveMaximum != null
        val hasMinItems = prop.minItems != null
        val hasMaxItems = prop.maxItems != null
        val hasUniqueItems = prop.uniqueItems != null
        val hasMinProperties = prop.minProperties != null
        val hasMaxProperties = prop.maxProperties != null
        val hasMinContains = prop.minContains != null
        val hasMaxContains = prop.maxContains != null
        val hasContains = prop.contains != null
        val hasPrefixItems = prop.prefixItems.isNotEmpty()
        val hasXml = prop.xml != null
        val hasComment = !prop.comment.isNullOrBlank()
        val hasPatternProperties = prop.patternProperties.isNotEmpty()
        val hasPropertyNames = prop.propertyNames != null
        val hasDependentRequired = prop.dependentRequired.isNotEmpty()
        val hasDependentSchemas = prop.dependentSchemas.isNotEmpty()
        val hasUnevaluatedProperties = prop.unevaluatedProperties != null
        val hasUnevaluatedItems = prop.unevaluatedItems != null
        val hasContentSchema = prop.contentSchema != null
        val hasCustomKeywords = prop.customKeywords.isNotEmpty()

        if (!hasDesc && !hasExample && !hasExamples && !hasEnum && !hasExtDocs && !hasDiscriminator &&
            !hasMediaType && !hasEncoding && !hasTitle && !hasDefault && !hasConst &&
            !hasDeprecated && !hasReadOnly && !hasWriteOnly &&
            !hasSchemaId && !hasSchemaDialect && !hasAnchor && !hasDynamicAnchor && !hasDynamicRef && !hasDefs &&
            !hasMinLength && !hasMaxLength && !hasPattern && !hasMinimum && !hasMaximum &&
            !hasMultipleOf && !hasExclusiveMinimum && !hasExclusiveMaximum &&
            !hasMinItems && !hasMaxItems && !hasUniqueItems && !hasMinProperties && !hasMaxProperties &&
            !hasMinContains && !hasMaxContains && !hasContains && !hasPrefixItems && !hasXml &&
            !hasComment && !hasPatternProperties && !hasPropertyNames &&
            !hasDependentRequired && !hasDependentSchemas &&
            !hasUnevaluatedProperties && !hasUnevaluatedItems && !hasContentSchema && !hasCustomKeywords
        ) return ""

        val sb = StringBuilder("    /**\n")
        var hasAny = false
        fun appendLine(line: String, blankBefore: Boolean = true) {
            if (hasAny && blankBefore) sb.append("     *\n")
            sb.append("     * ").append(line).append("\n")
            hasAny = true
        }

        if (hasDesc) appendLine(prop.description!!, blankBefore = false)
        if (hasTitle) appendLine("@title ${prop.title}")
        if (hasExtDocs) {
            val docs = prop.externalDocs!!
            val suffix = if (docs.description != null) " ${docs.description}" else ""
            appendLine("@see ${docs.url}$suffix")
        }
        if (hasDiscriminator) {
            val disc = prop.discriminator!!
            appendLine("@discriminator ${disc.propertyName}")
            if (disc.mapping.isNotEmpty()) {
                appendLine("@discriminatorMapping ${jsonMapper.writeValueAsString(disc.mapping)}")
            }
            if (disc.defaultMapping != null) {
                appendLine("@discriminatorDefault ${disc.defaultMapping}")
            }
        }
        if (hasDefault) appendLine("@default ${renderDocValue(prop.defaultValue)}")
        if (hasConst) appendLine("@const ${renderDocValue(prop.constValue)}")
        if (hasComment) appendLine("@comment ${prop.comment}")
        if (hasSchemaId) appendLine("@schemaId ${prop.schemaId}")
        if (hasSchemaDialect) appendLine("@schemaDialect ${prop.schemaDialect}")
        if (hasAnchor) appendLine("@anchor ${prop.anchor}")
        if (hasDynamicAnchor) appendLine("@dynamicAnchor ${prop.dynamicAnchor}")
        if (hasDynamicRef) appendLine("@dynamicRef ${prop.dynamicRef}")
        if (hasDefs) appendLine("@defs ${renderSchemaMap(prop.defs)}")

        if (hasMinLength) appendLine("@minLength ${prop.minLength}")
        if (hasMaxLength) appendLine("@maxLength ${prop.maxLength}")
        if (hasPattern) appendLine("@pattern ${prop.pattern}")
        if (hasMinimum) appendLine("@minimum ${prop.minimum}")
        if (hasMaximum) appendLine("@maximum ${prop.maximum}")
        if (hasMultipleOf) appendLine("@multipleOf ${prop.multipleOf}")
        if (hasExclusiveMinimum) appendLine("@exclusiveMinimum ${prop.exclusiveMinimum}")
        if (hasExclusiveMaximum) appendLine("@exclusiveMaximum ${prop.exclusiveMaximum}")
        if (hasMinItems) appendLine("@minItems ${prop.minItems}")
        if (hasMaxItems) appendLine("@maxItems ${prop.maxItems}")
        if (hasUniqueItems) appendLine("@uniqueItems ${prop.uniqueItems}")
        if (hasMinProperties) appendLine("@minProperties ${prop.minProperties}")
        if (hasMaxProperties) appendLine("@maxProperties ${prop.maxProperties}")
        if (hasMinContains) appendLine("@minContains ${prop.minContains}")
        if (hasMaxContains) appendLine("@maxContains ${prop.maxContains}")
        if (hasContains) appendLine("@contains ${renderSchema(prop.contains!!)}")
        if (hasPrefixItems) appendLine("@prefixItems ${renderSchemaList(prop.prefixItems)}")

        if (hasPatternProperties) {
            appendLine("@patternProperties ${renderSchemaMap(prop.patternProperties)}")
        }
        if (hasPropertyNames) {
            appendLine("@propertyNames ${renderSchema(prop.propertyNames!!)}")
        }
        if (hasDependentRequired) {
            appendLine("@dependentRequired ${renderStringListMap(prop.dependentRequired)}")
        }
        if (hasDependentSchemas) {
            appendLine("@dependentSchemas ${renderSchemaMap(prop.dependentSchemas)}")
        }
        if (hasUnevaluatedProperties) {
            appendLine("@unevaluatedProperties ${renderSchema(prop.unevaluatedProperties!!)}")
        }
        if (hasUnevaluatedItems) {
            appendLine("@unevaluatedItems ${renderSchema(prop.unevaluatedItems!!)}")
        }
        if (hasContentSchema) {
            appendLine("@contentSchema ${renderSchema(prop.contentSchema!!)}")
        }
        if (hasCustomKeywords) {
            appendLine("@keywords ${renderCustomKeywords(prop.customKeywords)}")
        }

        if (hasDeprecated) appendLine("@deprecated")
        if (hasReadOnly) appendLine("@readOnly")
        if (hasWriteOnly) appendLine("@writeOnly")
        if (hasMediaType) appendLine("@contentMediaType ${prop.contentMediaType}")
        if (hasEncoding) appendLine("@contentEncoding ${prop.contentEncoding}")
        if (hasExample) appendLine("@example ${renderDocValue(prop.example)}")
        if (hasExamples) {
            prop.examples!!.forEachIndexed { index, value ->
                appendLine("@example example${index + 1}: ${renderDocValue(value)}")
            }
        }
        if (hasEnum) {
            prop.enumValues!!.forEach { value ->
                appendLine("@enum ${renderEnumValue(value)}")
            }
        }

        if (hasXml) {
            val xml = prop.xml!!
            xml.name?.let { appendLine("@xmlName $it") }
            xml.namespace?.let { appendLine("@xmlNamespace $it") }
            xml.prefix?.let { appendLine("@xmlPrefix $it") }
            xml.nodeType?.let { appendLine("@xmlNodeType $it") }
            if (xml.attribute) appendLine("@xmlAttribute")
            if (xml.wrapped) appendLine("@xmlWrapped")
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

    private fun renderDocValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> value
            is Number, is Boolean -> value.toString()
            is Map<*, *>, is Iterable<*> -> jsonMapper.writeValueAsString(value)
            else -> value.toString()
        }
    }

    private fun renderExamplesList(values: List<Any?>): String {
        return jsonMapper.writeValueAsString(values)
    }

    private fun renderEnumValue(value: Any?): String {
        return jsonMapper.writeValueAsString(value)
    }

    private fun renderSchema(schema: SchemaProperty): String {
        return jsonMapper.writeValueAsString(schemaPropertyToDocValue(schema))
    }

    private fun renderSchemaMap(map: Map<String, SchemaProperty>): String {
        val converted = map.mapValues { schemaPropertyToDocValue(it.value) }
        return jsonMapper.writeValueAsString(converted)
    }

    private fun renderSchemaList(list: List<SchemaProperty>): String {
        val converted = list.map { schemaPropertyToDocValue(it) }
        return jsonMapper.writeValueAsString(converted)
    }

    private fun renderStringListMap(map: Map<String, List<String>>): String {
        return jsonMapper.writeValueAsString(map)
    }

    private fun renderCustomKeywords(map: Map<String, Any?>): String {
        return jsonMapper.writeValueAsString(map)
    }

    private fun schemaPropertyToDocValue(schema: SchemaProperty): Any {
        schema.booleanSchema?.let { return it }
        schema.ref?.let { return mapOf("\$ref" to it) }
        schema.dynamicRef?.let { return mapOf("\$dynamicRef" to it) }

        val map = linkedMapOf<String, Any?>()
        val typeValue = schemaTypeValue(schema.types)
        typeValue?.let { map["type"] = it }

        map.putIfNotNull("\$id", schema.schemaId)
        map.putIfNotNull("\$schema", schema.schemaDialect)
        map.putIfNotNull("\$anchor", schema.anchor)
        map.putIfNotNull("\$dynamicAnchor", schema.dynamicAnchor)
        map.putIfNotNull("\$comment", schema.comment)
        map.putIfNotNull("format", schema.format)
        map.putIfNotNull("contentMediaType", schema.contentMediaType)
        map.putIfNotNull("contentEncoding", schema.contentEncoding)
        map.putIfNotNull("minLength", schema.minLength)
        map.putIfNotNull("maxLength", schema.maxLength)
        map.putIfNotNull("pattern", schema.pattern)
        schema.enumValues?.let { map["enum"] = it }
        map.putIfNotNull("minimum", schema.minimum)
        map.putIfNotNull("maximum", schema.maximum)
        map.putIfNotNull("multipleOf", schema.multipleOf)
        map.putIfNotNull("exclusiveMinimum", schema.exclusiveMinimum)
        map.putIfNotNull("exclusiveMaximum", schema.exclusiveMaximum)
        map.putIfNotNull("minItems", schema.minItems)
        map.putIfNotNull("maxItems", schema.maxItems)
        map.putIfNotNull("uniqueItems", schema.uniqueItems)
        map.putIfNotNull("minProperties", schema.minProperties)
        map.putIfNotNull("maxProperties", schema.maxProperties)

        schema.items?.let { map["items"] = schemaPropertyToDocValue(it) }
        map.putIfNotEmpty("prefixItems", schema.prefixItems.map { schemaPropertyToDocValue(it) })
        schema.contains?.let { map["contains"] = schemaPropertyToDocValue(it) }
        map.putIfNotNull("minContains", schema.minContains)
        map.putIfNotNull("maxContains", schema.maxContains)

        if (schema.properties.isNotEmpty()) {
            map["properties"] = schema.properties.mapValues { schemaPropertyToDocValue(it.value) }
        }
        if (schema.patternProperties.isNotEmpty()) {
            map["patternProperties"] = schema.patternProperties.mapValues { schemaPropertyToDocValue(it.value) }
        }
        schema.propertyNames?.let { map["propertyNames"] = schemaPropertyToDocValue(it) }
        schema.additionalProperties?.let { map["additionalProperties"] = schemaPropertyToDocValue(it) }
        map.putIfNotEmpty("required", schema.required)
        map.putIfNotEmpty("\$defs", schema.defs.mapValues { schemaPropertyToDocValue(it.value) })
        map.putIfNotEmpty("dependentRequired", schema.dependentRequired)
        if (schema.dependentSchemas.isNotEmpty()) {
            map["dependentSchemas"] = schema.dependentSchemas.mapValues { schemaPropertyToDocValue(it.value) }
        }

        schema.enumValues?.let { map["enum"] = it }
        map.putIfNotNull("description", schema.description)
        map.putIfNotNull("title", schema.title)
        map.putIfNotNull("default", schema.defaultValue)
        map.putIfNotNull("const", schema.constValue)
        map.putIfTrue("deprecated", schema.deprecated)
        map.putIfTrue("readOnly", schema.readOnly)
        map.putIfTrue("writeOnly", schema.writeOnly)

        schema.externalDocs?.let { docs ->
            val docsMap = linkedMapOf<String, Any?>("url" to docs.url)
            docs.description?.let { docsMap["description"] = it }
            map["externalDocs"] = docsMap
        }
        schema.discriminator?.let { disc ->
            val discMap = linkedMapOf<String, Any?>("propertyName" to disc.propertyName)
            if (disc.mapping.isNotEmpty()) discMap["mapping"] = disc.mapping
            disc.defaultMapping?.let { discMap["defaultMapping"] = it }
            map["discriminator"] = discMap
        }
        schema.xml?.let { xml ->
            val xmlMap = linkedMapOf<String, Any?>()
            xml.name?.let { xmlMap["name"] = it }
            xml.namespace?.let { xmlMap["namespace"] = it }
            xml.prefix?.let { xmlMap["prefix"] = it }
            xml.nodeType?.let { xmlMap["nodeType"] = it }
            if (xml.attribute) xmlMap["attribute"] = true
            if (xml.wrapped) xmlMap["wrapped"] = true
            if (xmlMap.isNotEmpty()) map["xml"] = xmlMap
        }

        map.putIfNotEmpty("oneOf", schema.oneOf.map { schemaPropertyToDocValue(it) })
        map.putIfNotEmpty("anyOf", schema.anyOf.map { schemaPropertyToDocValue(it) })
        map.putIfNotEmpty("allOf", schema.allOf.map { schemaPropertyToDocValue(it) })
        schema.not?.let { map["not"] = schemaPropertyToDocValue(it) }
        schema.ifSchema?.let { map["if"] = schemaPropertyToDocValue(it) }
        schema.thenSchema?.let { map["then"] = schemaPropertyToDocValue(it) }
        schema.elseSchema?.let { map["else"] = schemaPropertyToDocValue(it) }
        map.putIfNotNull("example", schema.example)
        schema.examples?.let { map["examples"] = it }

        schema.unevaluatedProperties?.let { map["unevaluatedProperties"] = schemaPropertyToDocValue(it) }
        schema.unevaluatedItems?.let { map["unevaluatedItems"] = schemaPropertyToDocValue(it) }
        schema.contentSchema?.let { map["contentSchema"] = schemaPropertyToDocValue(it) }
        if (schema.customKeywords.isNotEmpty()) {
            schema.customKeywords.forEach { (key, value) ->
                if (!key.startsWith("x-") && !map.containsKey(key)) {
                    map[key] = value
                }
            }
        }

        return map
    }

    private fun schemaTypeValue(types: Set<String>): Any? {
        if (types.isEmpty()) return null
        val list = types.toList().sorted()
        return if (list.size == 1) list.first() else list
    }

    private fun <T> MutableMap<String, Any?>.putIfNotNull(key: String, value: T?) {
        if (value != null) this[key] = value
    }

    private fun <T> MutableMap<String, Any?>.putIfNotEmpty(key: String, value: Collection<T>?) {
        if (!value.isNullOrEmpty()) this[key] = value
    }

    private fun <K, V> MutableMap<String, Any?>.putIfNotEmpty(key: String, value: Map<K, V>?) {
        if (!value.isNullOrEmpty()) this[key] = value
    }

    private fun MutableMap<String, Any?>.putIfTrue(key: String, value: Boolean) {
        if (value) this[key] = true
    }
}
