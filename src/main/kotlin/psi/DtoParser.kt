package psi

import domain.SchemaDefinition
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Service that parses Kotlin source code to extract OpenAPI [SchemaDefinition]s.
 * Analyzes Data Classes (Objects), Enums, and Map typealiases.
 * Supports:
 * - KDoc comments -> 'description'
 * - KDoc @see tag -> 'externalDocs'
 * - KDoc @example tag -> 'example' / 'examples'
 * - @SerialName annotations -> overrides property name
 * - Nullability -> 'required' list AND 'type: ["x", "null"]' matching OAS 3.2 compliance
 * - Sealed Interfaces -> Polymorphism (oneOf, discriminator)
 * - Inheritance -> allOf
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
        val aliases = file.collectDescendantsOfType<KtTypeAlias>()

        val classSchemas = classes.mapNotNull { ktClass ->
            when {
                ktClass.isEnum() -> parseEnum(ktClass)
                ktClass.isData() -> parseDataClass(ktClass)
                ktClass.isInterface() && ktClass.isSealed() -> parseSealedInterface(ktClass)
                else -> null
            }
        }

        val aliasSchemas = aliases.mapNotNull { parseTypeAlias(it) }

        return classSchemas + aliasSchemas
    }

    private fun parseTypeAlias(alias: KtTypeAlias): SchemaDefinition? {
        val name = alias.name ?: return null
        val typeRef = alias.getTypeReference()?.text ?: return null

        val schemaProp = TypeMappers.kotlinToSchemaProperty(typeRef)
        val isMapAlias = schemaProp.types.contains("object") && schemaProp.additionalProperties != null
        val isArrayAlias = schemaProp.types.contains("array")
        val isPrimitiveAlias = schemaProp.types.any { it in setOf("string", "integer", "number", "boolean") }

        if (!isMapAlias && !isArrayAlias && !isPrimitiveAlias) return null

        val description = extractDoc(alias)
        val externalDocs = extractExternalDocs(alias)
        val (example, examples) = extractExamples(alias)
        val title = extractTagValue(alias, "title")
        val defaultValue = extractTagValue(alias, "default")
        val constValue = extractTagValue(alias, "const")
        val deprecated = hasTag(alias, "deprecated") || hasAnnotation(alias, "Deprecated")
        val readOnly = hasTag(alias, "readOnly")
        val writeOnly = hasTag(alias, "writeOnly")

        return when {
            isMapAlias -> SchemaDefinition(
                name = name,
                type = "object",
                types = schemaProp.types,
                additionalProperties = schemaProp.additionalProperties,
                description = description,
                title = title,
                defaultValue = defaultValue,
                constValue = constValue,
                deprecated = deprecated,
                readOnly = readOnly,
                writeOnly = writeOnly,
                externalDocs = externalDocs,
                example = example,
                examples = examples
            )
            isArrayAlias -> SchemaDefinition(
                name = name,
                type = "array",
                types = schemaProp.types,
                items = schemaProp.items,
                description = description,
                title = title,
                defaultValue = defaultValue,
                constValue = constValue,
                deprecated = deprecated,
                readOnly = readOnly,
                writeOnly = writeOnly,
                externalDocs = externalDocs,
                example = example,
                examples = examples
            )
            else -> SchemaDefinition(
                name = name,
                type = schemaProp.type,
                types = schemaProp.types,
                format = schemaProp.format,
                contentMediaType = schemaProp.contentMediaType,
                contentEncoding = schemaProp.contentEncoding,
                description = description,
                title = title,
                defaultValue = defaultValue,
                constValue = constValue,
                deprecated = deprecated,
                readOnly = readOnly,
                writeOnly = writeOnly,
                externalDocs = externalDocs,
                example = example,
                examples = examples
            )
        }
    }

    private fun parseEnum(ktClass: KtClass): SchemaDefinition {
        val name = ktClass.name ?: "UnknownEnum"
        val description = extractDoc(ktClass)
        val externalDocs = extractExternalDocs(ktClass)
        val (example, examples) = extractExamples(ktClass)
        val title = extractTagValue(ktClass, "title")
        val defaultValue = extractTagValue(ktClass, "default")
        val constValue = extractTagValue(ktClass, "const")
        val deprecated = hasTag(ktClass, "deprecated") || hasAnnotation(ktClass, "Deprecated")
        val readOnly = hasTag(ktClass, "readOnly")
        val writeOnly = hasTag(ktClass, "writeOnly")

        val values = ktClass.declarations
            .filterIsInstance<org.jetbrains.kotlin.psi.KtEnumEntry>()
            .mapNotNull { entry ->
                val serialNameEntry = entry.annotationEntries.find {
                    it.shortName?.asString() == "SerialName"
                }

                if (serialNameEntry != null) {
                    val arg = serialNameEntry.valueArgumentList?.arguments?.firstOrNull()
                    val expression = arg?.getArgumentExpression() as? KtStringTemplateExpression
                    expression?.entries?.firstOrNull()?.text ?: entry.name
                } else {
                    entry.name
                }
            }

        return SchemaDefinition(
            name = name,
            type = "string",
            enumValues = values,
            description = description,
            title = title,
            defaultValue = defaultValue,
            constValue = constValue,
            deprecated = deprecated,
            readOnly = readOnly,
            writeOnly = writeOnly,
            externalDocs = externalDocs,
            example = example,
            examples = examples
        )
    }

    private fun parseSealedInterface(ktClass: KtClass): SchemaDefinition {
        val name = ktClass.name ?: "UnknownInterface"
        val description = extractDoc(ktClass)
        val externalDocs = extractExternalDocs(ktClass)
        val (example, examples) = extractExamples(ktClass)
        val title = extractTagValue(ktClass, "title")
        val defaultValue = extractTagValue(ktClass, "default")
        val constValue = extractTagValue(ktClass, "const")
        val deprecated = hasTag(ktClass, "deprecated") || hasAnnotation(ktClass, "Deprecated")
        val readOnly = hasTag(ktClass, "readOnly")
        val writeOnly = hasTag(ktClass, "writeOnly")

        val discriminatorAnnotation = ktClass.annotationEntries.find {
            it.shortName?.asString() == "JsonClassDiscriminator"
        }

        val propName = if (discriminatorAnnotation != null) {
            val arg = discriminatorAnnotation.valueArgumentList?.arguments?.firstOrNull()
            val expression = arg?.getArgumentExpression() as? KtStringTemplateExpression
            expression?.entries?.firstOrNull()?.text
        } else {
            null
        }

        val discriminatorObj = if (propName != null) {
            domain.Discriminator(propertyName = propName)
        } else {
            null
        }

        val properties = mutableMapOf<String, domain.SchemaProperty>()
        val requiredFields = mutableListOf<String>()

        ktClass.getProperties().forEach { prop ->
            val kotlinType = prop.typeReference?.text ?: "String"
            val rawName = prop.name ?: "unknown"

            val schemaProp = TypeMappers.kotlinToSchemaProperty(kotlinType).copy(
                description = extractDoc(prop),
                example = extractExampleProperty(prop),
                title = extractTagValue(prop, "title"),
                defaultValue = extractTagValue(prop, "default"),
                constValue = extractTagValue(prop, "const"),
                deprecated = hasTag(prop, "deprecated") || hasAnnotation(prop, "Deprecated"),
                readOnly = hasTag(prop, "readOnly"),
                writeOnly = hasTag(prop, "writeOnly")
            )
            properties[rawName] = schemaProp

            // If not nullable, add to required
            if (!kotlinType.contains("?")) {
                requiredFields.add(rawName)
            }
        }

        return SchemaDefinition(
            name = name,
            type = "object",
            properties = properties,
            required = requiredFields,
            description = description,
            title = title,
            defaultValue = defaultValue,
            constValue = constValue,
            deprecated = deprecated,
            readOnly = readOnly,
            writeOnly = writeOnly,
            externalDocs = externalDocs,
            discriminator = discriminatorObj,
            oneOf = emptyList(),
            example = example,
            examples = examples
        )
    }

    private fun parseDataClass(ktClass: KtClass): SchemaDefinition {
        val name = ktClass.name ?: "UnknownClass"
        val description = extractDoc(ktClass)
        val externalDocs = extractExternalDocs(ktClass)
        val (example, examples) = extractExamples(ktClass)
        val title = extractTagValue(ktClass, "title")
        val defaultValue = extractTagValue(ktClass, "default")
        val constValue = extractTagValue(ktClass, "const")
        val deprecated = hasTag(ktClass, "deprecated") || hasAnnotation(ktClass, "Deprecated")
        val readOnly = hasTag(ktClass, "readOnly")
        val writeOnly = hasTag(ktClass, "writeOnly")

        val properties = mutableMapOf<String, domain.SchemaProperty>()
        val requiredFields = mutableListOf<String>()

        val parameters = ktClass.primaryConstructor?.valueParameters ?: emptyList()

        parameters.forEach { param ->
            val kotlinType = param.typeReference?.text ?: "String"
            val rawName = param.name ?: "unknown"

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

            val schemaProp = TypeMappers.kotlinToSchemaProperty(kotlinType).copy(
                description = extractDoc(param),
                example = extractExampleProperty(param),
                title = extractTagValue(param, "title"),
                defaultValue = extractTagValue(param, "default"),
                constValue = extractTagValue(param, "const"),
                deprecated = hasTag(param, "deprecated") || hasAnnotation(param, "Deprecated"),
                readOnly = hasTag(param, "readOnly"),
                writeOnly = hasTag(param, "writeOnly")
            )
            properties[jsonName] = schemaProp

            // If not nullable, add to required
            if (!kotlinType.contains("?")) {
                requiredFields.add(jsonName)
            }
        }

        val superTypes = ktClass.superTypeListEntries
            .map { it.text }
            .filter { it != "Any" && it != "Serializable" }

        return SchemaDefinition(
            name = name,
            type = "object",
            properties = properties,
            required = requiredFields,
            description = description,
            title = title,
            defaultValue = defaultValue,
            constValue = constValue,
            deprecated = deprecated,
            readOnly = readOnly,
            writeOnly = writeOnly,
            externalDocs = externalDocs,
            allOf = superTypes,
            example = example,
            examples = examples
        )
    }

    private fun extractDoc(element: org.jetbrains.kotlin.psi.KtDeclaration): String? {
        val docComment = element.docComment ?: return null

        return docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .filter { it.isNotEmpty() && !it.startsWith("@") }
            .joinToString(" ")
            .ifEmpty { null }
    }

    private fun extractExternalDocs(element: org.jetbrains.kotlin.psi.KtDeclaration): domain.ExternalDocumentation? {
        val docComment = element.docComment ?: return null

        val seeLine = docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .find { it.startsWith("@see") }
            ?: return null

        val content = seeLine.removePrefix("@see").trim()
        val parts = content.split(" ", limit = 2)
        val url = parts.getOrNull(0) ?: return null
        val desc = parts.getOrNull(1)

        return domain.ExternalDocumentation(
            url = url,
            description = desc
        )
    }

    private fun extractExamples(element: org.jetbrains.kotlin.psi.KtDeclaration): Pair<String?, Map<String, String>?> {
        val docComment = element.docComment ?: return Pair(null, null)

        val exampleLines = docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .filter { it.startsWith("@example") }
            .map { it.removePrefix("@example").trim() }

        if (exampleLines.isEmpty()) return Pair(null, null)

        var simpleExample: String? = null
        val mapExamples = mutableMapOf<String, String>()

        exampleLines.forEach { line ->
            val firstColon = line.indexOf(':')
            if (firstColon > 0) {
                val potentialKey = line.substring(0, firstColon).trim()
                if (potentialKey.matches(Regex("[a-zA-Z0-9_]+"))) {
                    val value = line.substring(firstColon + 1).trim()
                    mapExamples[potentialKey] = value
                    return@forEach
                }
            }
            simpleExample = line
        }

        val finalMap = if (mapExamples.isNotEmpty()) mapExamples else null
        return Pair(simpleExample, finalMap)
    }

    private fun extractExampleProperty(element: org.jetbrains.kotlin.psi.KtDeclaration): String? {
        val (ex, _) = extractExamples(element)
        return ex
    }

    private fun extractTagValue(element: org.jetbrains.kotlin.psi.KtDeclaration, tag: String): String? {
        val docComment = element.docComment ?: return null

        val line = docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .find { it.startsWith("@$tag") }
            ?: return null

        val value = line.removePrefix("@$tag").trim()
        return value.ifEmpty { null }
    }

    private fun hasTag(element: org.jetbrains.kotlin.psi.KtDeclaration, tag: String): Boolean {
        val docComment = element.docComment ?: return false
        val normalized = docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }

        return normalized.any { it == "@$tag" || it.startsWith("@$tag ") }
    }

    private fun hasAnnotation(element: KtModifierListOwner, annotationName: String): Boolean {
        return element.annotationEntries.any { it.shortName?.asString() == annotationName }
    }

    private fun cleanKDocLine(line: String): String {
        return line.trim()
            .replace("/**", "")
            .replace("*/", "")
            .replaceFirst("*", "")
            .trim()
    }
}
