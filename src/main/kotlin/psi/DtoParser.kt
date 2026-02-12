package psi

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import domain.SchemaDefinition
import domain.SchemaProperty
import domain.Xml
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
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
 * - KDoc @contentMediaType / @contentEncoding -> content metadata round-trip
 */
class DtoParser {
    private val jsonMapper = ObjectMapper(JsonFactory())
    private val schemaKnownKeys = setOf(
        "\$ref",
        "\$dynamicRef",
        "\$id",
        "\$schema",
        "\$anchor",
        "\$dynamicAnchor",
        "\$comment",
        "\$defs",
        "type",
        "format",
        "contentMediaType",
        "contentEncoding",
        "minLength",
        "maxLength",
        "pattern",
        "minimum",
        "maximum",
        "multipleOf",
        "exclusiveMinimum",
        "exclusiveMaximum",
        "minItems",
        "maxItems",
        "uniqueItems",
        "minProperties",
        "maxProperties",
        "items",
        "prefixItems",
        "contains",
        "minContains",
        "maxContains",
        "properties",
        "patternProperties",
        "propertyNames",
        "additionalProperties",
        "required",
        "dependentRequired",
        "dependentSchemas",
        "enum",
        "description",
        "title",
        "default",
        "const",
        "deprecated",
        "readOnly",
        "writeOnly",
        "externalDocs",
        "discriminator",
        "xml",
        "oneOf",
        "anyOf",
        "allOf",
        "not",
        "if",
        "then",
        "else",
        "example",
        "examples",
        "unevaluatedProperties",
        "unevaluatedItems",
        "contentSchema"
    )

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
        val examplesList = extractExamplesList(alias)
        val enumValues = extractEnumValues(alias)
        val title = extractTagValue(alias, "title")
        val defaultValue = extractTagLiteral(alias, "default")
        val constValue = extractTagLiteral(alias, "const")
        val deprecated = hasTag(alias, "deprecated") || hasAnnotation(alias, "Deprecated")
        val readOnly = hasTag(alias, "readOnly")
        val writeOnly = hasTag(alias, "writeOnly")
        val schemaId = extractTagValue(alias, "schemaId")
        val schemaDialect = extractTagValue(alias, "schemaDialect")
        val anchor = extractTagValue(alias, "anchor")
        val dynamicAnchor = extractTagValue(alias, "dynamicAnchor")
        val dynamicRef = extractTagValue(alias, "dynamicRef")
        val defs = extractSchemaMapTag(alias, "defs")
        val contentMediaType = extractTagValue(alias, "contentMediaType")
        val contentEncoding = extractTagValue(alias, "contentEncoding")
        val minLength = extractTagInt(alias, "minLength")
        val maxLength = extractTagInt(alias, "maxLength")
        val pattern = extractTagValue(alias, "pattern")
        val minimum = extractTagDouble(alias, "minimum")
        val maximum = extractTagDouble(alias, "maximum")
        val multipleOf = extractTagDouble(alias, "multipleOf")
        val exclusiveMinimum = extractTagDouble(alias, "exclusiveMinimum")
        val exclusiveMaximum = extractTagDouble(alias, "exclusiveMaximum")
        val minItems = extractTagInt(alias, "minItems")
        val maxItems = extractTagInt(alias, "maxItems")
        val uniqueItems = extractTagBoolean(alias, "uniqueItems")
        val minProperties = extractTagInt(alias, "minProperties")
        val maxProperties = extractTagInt(alias, "maxProperties")
        val minContains = extractTagInt(alias, "minContains")
        val maxContains = extractTagInt(alias, "maxContains")
        val contains = extractSchemaTag(alias, "contains")
        val prefixItems = extractSchemaListTag(alias, "prefixItems")
        val comment = extractTagValue(alias, "comment")
        val patternProperties = extractSchemaMapTag(alias, "patternProperties")
        val propertyNames = extractSchemaTag(alias, "propertyNames")
        val dependentRequired = extractStringListMapTag(alias, "dependentRequired")
        val dependentSchemas = extractSchemaMapTag(alias, "dependentSchemas")
        val unevaluatedProperties = extractSchemaTag(alias, "unevaluatedProperties")
        val unevaluatedItems = extractSchemaTag(alias, "unevaluatedItems")
        val contentSchema = extractSchemaTag(alias, "contentSchema")
        val xml = extractXml(alias)
        val customKeywords = extractCustomKeywords(alias)

        return when {
            isMapAlias -> SchemaDefinition(
                name = name,
                type = "object",
                types = schemaProp.types,
                schemaId = schemaId,
                schemaDialect = schemaDialect,
                anchor = anchor,
                dynamicAnchor = dynamicAnchor,
                dynamicRef = dynamicRef,
                defs = defs,
                additionalProperties = schemaProp.additionalProperties,
                description = description,
                title = title,
                defaultValue = defaultValue,
                constValue = constValue,
                deprecated = deprecated,
                readOnly = readOnly,
                writeOnly = writeOnly,
                contentMediaType = contentMediaType ?: schemaProp.contentMediaType,
                contentEncoding = contentEncoding ?: schemaProp.contentEncoding,
                minLength = minLength,
                maxLength = maxLength,
                pattern = pattern,
                minimum = minimum,
                maximum = maximum,
                multipleOf = multipleOf,
                exclusiveMinimum = exclusiveMinimum,
                exclusiveMaximum = exclusiveMaximum,
                minItems = minItems,
                maxItems = maxItems,
                uniqueItems = uniqueItems,
                minProperties = minProperties,
                maxProperties = maxProperties,
                minContains = minContains,
                maxContains = maxContains,
                contains = contains,
                prefixItems = prefixItems,
                comment = comment,
                patternProperties = patternProperties,
                propertyNames = propertyNames,
                dependentRequired = dependentRequired,
                dependentSchemas = dependentSchemas,
                unevaluatedProperties = unevaluatedProperties,
                unevaluatedItems = unevaluatedItems,
                contentSchema = contentSchema,
                customKeywords = customKeywords,
                externalDocs = externalDocs,
                example = example,
                examples = examples,
                examplesList = examplesList,
                enumValues = enumValues,
                xml = xml
            )
            isArrayAlias -> SchemaDefinition(
                name = name,
                type = "array",
                types = schemaProp.types,
                schemaId = schemaId,
                schemaDialect = schemaDialect,
                anchor = anchor,
                dynamicAnchor = dynamicAnchor,
                dynamicRef = dynamicRef,
                defs = defs,
                items = schemaProp.items,
                description = description,
                title = title,
                defaultValue = defaultValue,
                constValue = constValue,
                deprecated = deprecated,
                readOnly = readOnly,
                writeOnly = writeOnly,
                contentMediaType = contentMediaType ?: schemaProp.contentMediaType,
                contentEncoding = contentEncoding ?: schemaProp.contentEncoding,
                minLength = minLength,
                maxLength = maxLength,
                pattern = pattern,
                minimum = minimum,
                maximum = maximum,
                multipleOf = multipleOf,
                exclusiveMinimum = exclusiveMinimum,
                exclusiveMaximum = exclusiveMaximum,
                minItems = minItems,
                maxItems = maxItems,
                uniqueItems = uniqueItems,
                minProperties = minProperties,
                maxProperties = maxProperties,
                minContains = minContains,
                maxContains = maxContains,
                contains = contains,
                prefixItems = prefixItems,
                comment = comment,
                patternProperties = patternProperties,
                propertyNames = propertyNames,
                dependentRequired = dependentRequired,
                dependentSchemas = dependentSchemas,
                unevaluatedProperties = unevaluatedProperties,
                unevaluatedItems = unevaluatedItems,
                contentSchema = contentSchema,
                customKeywords = customKeywords,
                externalDocs = externalDocs,
                example = example,
                examples = examples,
                examplesList = examplesList,
                enumValues = enumValues,
                xml = xml
            )
            else -> SchemaDefinition(
                name = name,
                type = schemaProp.type,
                types = schemaProp.types,
                format = schemaProp.format,
                schemaId = schemaId,
                schemaDialect = schemaDialect,
                anchor = anchor,
                dynamicAnchor = dynamicAnchor,
                dynamicRef = dynamicRef,
                defs = defs,
                contentMediaType = contentMediaType ?: schemaProp.contentMediaType,
                contentEncoding = contentEncoding ?: schemaProp.contentEncoding,
                description = description,
                title = title,
                defaultValue = defaultValue,
                constValue = constValue,
                deprecated = deprecated,
                readOnly = readOnly,
                writeOnly = writeOnly,
                minLength = minLength,
                maxLength = maxLength,
                pattern = pattern,
                minimum = minimum,
                maximum = maximum,
                multipleOf = multipleOf,
                exclusiveMinimum = exclusiveMinimum,
                exclusiveMaximum = exclusiveMaximum,
                minItems = minItems,
                maxItems = maxItems,
                uniqueItems = uniqueItems,
                minProperties = minProperties,
                maxProperties = maxProperties,
                minContains = minContains,
                maxContains = maxContains,
                contains = contains,
                prefixItems = prefixItems,
                comment = comment,
                patternProperties = patternProperties,
                propertyNames = propertyNames,
                dependentRequired = dependentRequired,
                dependentSchemas = dependentSchemas,
                unevaluatedProperties = unevaluatedProperties,
                unevaluatedItems = unevaluatedItems,
                contentSchema = contentSchema,
                customKeywords = customKeywords,
                externalDocs = externalDocs,
                example = example,
                examples = examples,
                examplesList = examplesList,
                enumValues = enumValues,
                xml = xml
            )
        }
    }

    private fun parseEnum(ktClass: KtClass): SchemaDefinition {
        val name = ktClass.name ?: "UnknownEnum"
        val description = extractDoc(ktClass)
        val externalDocs = extractExternalDocs(ktClass)
        val (example, examples) = extractExamples(ktClass)
        val examplesList = extractExamplesList(ktClass)
        val enumValues = extractEnumValues(ktClass)
        val title = extractTagValue(ktClass, "title")
        val defaultValue = extractTagLiteral(ktClass, "default")
        val constValue = extractTagLiteral(ktClass, "const")
        val deprecated = hasTag(ktClass, "deprecated") || hasAnnotation(ktClass, "Deprecated")
        val readOnly = hasTag(ktClass, "readOnly")
        val writeOnly = hasTag(ktClass, "writeOnly")
        val schemaId = extractTagValue(ktClass, "schemaId")
        val schemaDialect = extractTagValue(ktClass, "schemaDialect")
        val anchor = extractTagValue(ktClass, "anchor")
        val dynamicAnchor = extractTagValue(ktClass, "dynamicAnchor")
        val dynamicRef = extractTagValue(ktClass, "dynamicRef")
        val defs = extractSchemaMapTag(ktClass, "defs")
        val contentMediaType = extractTagValue(ktClass, "contentMediaType")
        val contentEncoding = extractTagValue(ktClass, "contentEncoding")
        val minLength = extractTagInt(ktClass, "minLength")
        val maxLength = extractTagInt(ktClass, "maxLength")
        val pattern = extractTagValue(ktClass, "pattern")
        val minimum = extractTagDouble(ktClass, "minimum")
        val maximum = extractTagDouble(ktClass, "maximum")
        val multipleOf = extractTagDouble(ktClass, "multipleOf")
        val exclusiveMinimum = extractTagDouble(ktClass, "exclusiveMinimum")
        val exclusiveMaximum = extractTagDouble(ktClass, "exclusiveMaximum")
        val minItems = extractTagInt(ktClass, "minItems")
        val maxItems = extractTagInt(ktClass, "maxItems")
        val uniqueItems = extractTagBoolean(ktClass, "uniqueItems")
        val minProperties = extractTagInt(ktClass, "minProperties")
        val maxProperties = extractTagInt(ktClass, "maxProperties")
        val minContains = extractTagInt(ktClass, "minContains")
        val maxContains = extractTagInt(ktClass, "maxContains")
        val contains = extractSchemaTag(ktClass, "contains")
        val prefixItems = extractSchemaListTag(ktClass, "prefixItems")
        val comment = extractTagValue(ktClass, "comment")
        val patternProperties = extractSchemaMapTag(ktClass, "patternProperties")
        val propertyNames = extractSchemaTag(ktClass, "propertyNames")
        val dependentRequired = extractStringListMapTag(ktClass, "dependentRequired")
        val dependentSchemas = extractSchemaMapTag(ktClass, "dependentSchemas")
        val unevaluatedProperties = extractSchemaTag(ktClass, "unevaluatedProperties")
        val unevaluatedItems = extractSchemaTag(ktClass, "unevaluatedItems")
        val contentSchema = extractSchemaTag(ktClass, "contentSchema")
        val xml = extractXml(ktClass)
        val customKeywords = extractCustomKeywords(ktClass)

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
            schemaId = schemaId,
            schemaDialect = schemaDialect,
            anchor = anchor,
            dynamicAnchor = dynamicAnchor,
            dynamicRef = dynamicRef,
            defs = defs,
            description = description,
            title = title,
            defaultValue = defaultValue,
            constValue = constValue,
            deprecated = deprecated,
            readOnly = readOnly,
            writeOnly = writeOnly,
            contentMediaType = contentMediaType,
            contentEncoding = contentEncoding,
            minLength = minLength,
            maxLength = maxLength,
            pattern = pattern,
            minimum = minimum,
            maximum = maximum,
            multipleOf = multipleOf,
            exclusiveMinimum = exclusiveMinimum,
            exclusiveMaximum = exclusiveMaximum,
            minItems = minItems,
            maxItems = maxItems,
            uniqueItems = uniqueItems,
            minProperties = minProperties,
            maxProperties = maxProperties,
            minContains = minContains,
            maxContains = maxContains,
            contains = contains,
            prefixItems = prefixItems,
            comment = comment,
            patternProperties = patternProperties,
            propertyNames = propertyNames,
            dependentRequired = dependentRequired,
            dependentSchemas = dependentSchemas,
            unevaluatedProperties = unevaluatedProperties,
            unevaluatedItems = unevaluatedItems,
            contentSchema = contentSchema,
            customKeywords = customKeywords,
            externalDocs = externalDocs,
            example = example,
            examples = examples,
            examplesList = examplesList,
            xml = xml
        )
    }

    private fun parseSealedInterface(ktClass: KtClass): SchemaDefinition {
        val name = ktClass.name ?: "UnknownInterface"
        val description = extractDoc(ktClass)
        val externalDocs = extractExternalDocs(ktClass)
        val (example, examples) = extractExamples(ktClass)
        val examplesList = extractExamplesList(ktClass)
        val enumValues = extractEnumValues(ktClass)
        val title = extractTagValue(ktClass, "title")
        val defaultValue = extractTagLiteral(ktClass, "default")
        val constValue = extractTagLiteral(ktClass, "const")
        val deprecated = hasTag(ktClass, "deprecated") || hasAnnotation(ktClass, "Deprecated")
        val readOnly = hasTag(ktClass, "readOnly")
        val writeOnly = hasTag(ktClass, "writeOnly")
        val schemaId = extractTagValue(ktClass, "schemaId")
        val schemaDialect = extractTagValue(ktClass, "schemaDialect")
        val anchor = extractTagValue(ktClass, "anchor")
        val dynamicAnchor = extractTagValue(ktClass, "dynamicAnchor")
        val dynamicRef = extractTagValue(ktClass, "dynamicRef")
        val defs = extractSchemaMapTag(ktClass, "defs")
        val contentMediaType = extractTagValue(ktClass, "contentMediaType")
        val contentEncoding = extractTagValue(ktClass, "contentEncoding")
        val minLength = extractTagInt(ktClass, "minLength")
        val maxLength = extractTagInt(ktClass, "maxLength")
        val pattern = extractTagValue(ktClass, "pattern")
        val minimum = extractTagDouble(ktClass, "minimum")
        val maximum = extractTagDouble(ktClass, "maximum")
        val multipleOf = extractTagDouble(ktClass, "multipleOf")
        val exclusiveMinimum = extractTagDouble(ktClass, "exclusiveMinimum")
        val exclusiveMaximum = extractTagDouble(ktClass, "exclusiveMaximum")
        val minItems = extractTagInt(ktClass, "minItems")
        val maxItems = extractTagInt(ktClass, "maxItems")
        val uniqueItems = extractTagBoolean(ktClass, "uniqueItems")
        val minProperties = extractTagInt(ktClass, "minProperties")
        val maxProperties = extractTagInt(ktClass, "maxProperties")
        val minContains = extractTagInt(ktClass, "minContains")
        val maxContains = extractTagInt(ktClass, "maxContains")
        val contains = extractSchemaTag(ktClass, "contains")
        val prefixItems = extractSchemaListTag(ktClass, "prefixItems")
        val comment = extractTagValue(ktClass, "comment")
        val patternProperties = extractSchemaMapTag(ktClass, "patternProperties")
        val propertyNames = extractSchemaTag(ktClass, "propertyNames")
        val dependentRequired = extractStringListMapTag(ktClass, "dependentRequired")
        val dependentSchemas = extractSchemaMapTag(ktClass, "dependentSchemas")
        val unevaluatedProperties = extractSchemaTag(ktClass, "unevaluatedProperties")
        val unevaluatedItems = extractSchemaTag(ktClass, "unevaluatedItems")
        val contentSchema = extractSchemaTag(ktClass, "contentSchema")
        val xml = extractXml(ktClass)
        val customKeywords = extractCustomKeywords(ktClass)

        val kdocDiscriminator = extractDiscriminator(ktClass)

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

        val discriminatorObj = when {
            kdocDiscriminator != null -> kdocDiscriminator
            propName != null -> domain.Discriminator(propertyName = propName)
            else -> null
        }

        val properties = mutableMapOf<String, domain.SchemaProperty>()
        val requiredFields = mutableListOf<String>()

        ktClass.getProperties().forEach { prop ->
            val kotlinType = prop.typeReference?.text ?: "String"
            val rawName = prop.name ?: "unknown"

            val baseProp = TypeMappers.kotlinToSchemaProperty(kotlinType)
            val (example, examples) = extractExamples(prop)
            val schemaProp = baseProp.copy(
                description = extractDoc(prop),
                example = example,
                examples = examples?.values?.toList(),
                enumValues = extractEnumValues(prop),
                title = extractTagValue(prop, "title"),
                defaultValue = extractTagLiteral(prop, "default"),
                constValue = extractTagLiteral(prop, "const"),
                externalDocs = extractExternalDocs(prop),
                discriminator = extractDiscriminator(prop),
                schemaId = extractTagValue(prop, "schemaId"),
                schemaDialect = extractTagValue(prop, "schemaDialect"),
                anchor = extractTagValue(prop, "anchor"),
                dynamicAnchor = extractTagValue(prop, "dynamicAnchor"),
                dynamicRef = extractTagValue(prop, "dynamicRef"),
                defs = extractSchemaMapTag(prop, "defs"),
                contentMediaType = extractTagValue(prop, "contentMediaType") ?: baseProp.contentMediaType,
                contentEncoding = extractTagValue(prop, "contentEncoding") ?: baseProp.contentEncoding,
                minLength = extractTagInt(prop, "minLength"),
                maxLength = extractTagInt(prop, "maxLength"),
                pattern = extractTagValue(prop, "pattern"),
                minimum = extractTagDouble(prop, "minimum"),
                maximum = extractTagDouble(prop, "maximum"),
                multipleOf = extractTagDouble(prop, "multipleOf"),
                exclusiveMinimum = extractTagDouble(prop, "exclusiveMinimum"),
                exclusiveMaximum = extractTagDouble(prop, "exclusiveMaximum"),
                minItems = extractTagInt(prop, "minItems"),
                maxItems = extractTagInt(prop, "maxItems"),
                uniqueItems = extractTagBoolean(prop, "uniqueItems"),
                minProperties = extractTagInt(prop, "minProperties"),
                maxProperties = extractTagInt(prop, "maxProperties"),
                minContains = extractTagInt(prop, "minContains"),
                maxContains = extractTagInt(prop, "maxContains"),
                contains = extractSchemaTag(prop, "contains"),
                prefixItems = extractSchemaListTag(prop, "prefixItems"),
                comment = extractTagValue(prop, "comment"),
                patternProperties = extractSchemaMapTag(prop, "patternProperties"),
                propertyNames = extractSchemaTag(prop, "propertyNames"),
                dependentRequired = extractStringListMapTag(prop, "dependentRequired"),
                dependentSchemas = extractSchemaMapTag(prop, "dependentSchemas"),
                unevaluatedProperties = extractSchemaTag(prop, "unevaluatedProperties"),
                unevaluatedItems = extractSchemaTag(prop, "unevaluatedItems"),
                contentSchema = extractSchemaTag(prop, "contentSchema"),
                customKeywords = extractCustomKeywords(prop),
                deprecated = hasTag(prop, "deprecated") || hasAnnotation(prop, "Deprecated"),
                readOnly = hasTag(prop, "readOnly"),
                writeOnly = hasTag(prop, "writeOnly"),
                xml = extractXml(prop)
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
            schemaId = schemaId,
            schemaDialect = schemaDialect,
            anchor = anchor,
            dynamicAnchor = dynamicAnchor,
            dynamicRef = dynamicRef,
            defs = defs,
            properties = properties,
            required = requiredFields,
            description = description,
            title = title,
            defaultValue = defaultValue,
            constValue = constValue,
            deprecated = deprecated,
            readOnly = readOnly,
            writeOnly = writeOnly,
            contentMediaType = contentMediaType,
            contentEncoding = contentEncoding,
            minLength = minLength,
            maxLength = maxLength,
            pattern = pattern,
            minimum = minimum,
            maximum = maximum,
            multipleOf = multipleOf,
            exclusiveMinimum = exclusiveMinimum,
            exclusiveMaximum = exclusiveMaximum,
            minItems = minItems,
            maxItems = maxItems,
            uniqueItems = uniqueItems,
            minProperties = minProperties,
            maxProperties = maxProperties,
            minContains = minContains,
            maxContains = maxContains,
            contains = contains,
            prefixItems = prefixItems,
            comment = comment,
            patternProperties = patternProperties,
            propertyNames = propertyNames,
            dependentRequired = dependentRequired,
            dependentSchemas = dependentSchemas,
            unevaluatedProperties = unevaluatedProperties,
            unevaluatedItems = unevaluatedItems,
            contentSchema = contentSchema,
            customKeywords = customKeywords,
            externalDocs = externalDocs,
            discriminator = discriminatorObj,
            oneOf = emptyList(),
            example = example,
            examples = examples,
            examplesList = examplesList,
            enumValues = enumValues,
            xml = xml
        )
    }

    private fun parseDataClass(ktClass: KtClass): SchemaDefinition {
        val name = ktClass.name ?: "UnknownClass"
        val description = extractDoc(ktClass)
        val externalDocs = extractExternalDocs(ktClass)
        val (example, examples) = extractExamples(ktClass)
        val examplesList = extractExamplesList(ktClass)
        val title = extractTagValue(ktClass, "title")
        val defaultValue = extractTagLiteral(ktClass, "default")
        val constValue = extractTagLiteral(ktClass, "const")
        val deprecated = hasTag(ktClass, "deprecated") || hasAnnotation(ktClass, "Deprecated")
        val readOnly = hasTag(ktClass, "readOnly")
        val writeOnly = hasTag(ktClass, "writeOnly")
        val schemaId = extractTagValue(ktClass, "schemaId")
        val schemaDialect = extractTagValue(ktClass, "schemaDialect")
        val anchor = extractTagValue(ktClass, "anchor")
        val dynamicAnchor = extractTagValue(ktClass, "dynamicAnchor")
        val dynamicRef = extractTagValue(ktClass, "dynamicRef")
        val defs = extractSchemaMapTag(ktClass, "defs")
        val contentMediaType = extractTagValue(ktClass, "contentMediaType")
        val contentEncoding = extractTagValue(ktClass, "contentEncoding")
        val minLength = extractTagInt(ktClass, "minLength")
        val maxLength = extractTagInt(ktClass, "maxLength")
        val pattern = extractTagValue(ktClass, "pattern")
        val minimum = extractTagDouble(ktClass, "minimum")
        val maximum = extractTagDouble(ktClass, "maximum")
        val multipleOf = extractTagDouble(ktClass, "multipleOf")
        val exclusiveMinimum = extractTagDouble(ktClass, "exclusiveMinimum")
        val exclusiveMaximum = extractTagDouble(ktClass, "exclusiveMaximum")
        val minItems = extractTagInt(ktClass, "minItems")
        val maxItems = extractTagInt(ktClass, "maxItems")
        val uniqueItems = extractTagBoolean(ktClass, "uniqueItems")
        val minProperties = extractTagInt(ktClass, "minProperties")
        val maxProperties = extractTagInt(ktClass, "maxProperties")
        val minContains = extractTagInt(ktClass, "minContains")
        val maxContains = extractTagInt(ktClass, "maxContains")
        val contains = extractSchemaTag(ktClass, "contains")
        val prefixItems = extractSchemaListTag(ktClass, "prefixItems")
        val comment = extractTagValue(ktClass, "comment")
        val patternProperties = extractSchemaMapTag(ktClass, "patternProperties")
        val propertyNames = extractSchemaTag(ktClass, "propertyNames")
        val dependentRequired = extractStringListMapTag(ktClass, "dependentRequired")
        val dependentSchemas = extractSchemaMapTag(ktClass, "dependentSchemas")
        val unevaluatedProperties = extractSchemaTag(ktClass, "unevaluatedProperties")
        val unevaluatedItems = extractSchemaTag(ktClass, "unevaluatedItems")
        val contentSchema = extractSchemaTag(ktClass, "contentSchema")
        val xml = extractXml(ktClass)
        val discriminator = extractDiscriminator(ktClass)
        val customKeywords = extractCustomKeywords(ktClass)

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

            val baseProp = TypeMappers.kotlinToSchemaProperty(kotlinType)
            val (example, examples) = extractExamples(param)
            val schemaProp = baseProp.copy(
                description = extractDoc(param),
                example = example,
                examples = examples?.values?.toList(),
                enumValues = extractEnumValues(param),
                title = extractTagValue(param, "title"),
                defaultValue = extractTagLiteral(param, "default"),
                constValue = extractTagLiteral(param, "const"),
                externalDocs = extractExternalDocs(param),
                discriminator = extractDiscriminator(param),
                schemaId = extractTagValue(param, "schemaId"),
                schemaDialect = extractTagValue(param, "schemaDialect"),
                anchor = extractTagValue(param, "anchor"),
                dynamicAnchor = extractTagValue(param, "dynamicAnchor"),
                dynamicRef = extractTagValue(param, "dynamicRef"),
                defs = extractSchemaMapTag(param, "defs"),
                contentMediaType = extractTagValue(param, "contentMediaType") ?: baseProp.contentMediaType,
                contentEncoding = extractTagValue(param, "contentEncoding") ?: baseProp.contentEncoding,
                minLength = extractTagInt(param, "minLength"),
                maxLength = extractTagInt(param, "maxLength"),
                pattern = extractTagValue(param, "pattern"),
                minimum = extractTagDouble(param, "minimum"),
                maximum = extractTagDouble(param, "maximum"),
                multipleOf = extractTagDouble(param, "multipleOf"),
                exclusiveMinimum = extractTagDouble(param, "exclusiveMinimum"),
                exclusiveMaximum = extractTagDouble(param, "exclusiveMaximum"),
                minItems = extractTagInt(param, "minItems"),
                maxItems = extractTagInt(param, "maxItems"),
                uniqueItems = extractTagBoolean(param, "uniqueItems"),
                minProperties = extractTagInt(param, "minProperties"),
                maxProperties = extractTagInt(param, "maxProperties"),
                minContains = extractTagInt(param, "minContains"),
                maxContains = extractTagInt(param, "maxContains"),
                contains = extractSchemaTag(param, "contains"),
                prefixItems = extractSchemaListTag(param, "prefixItems"),
                comment = extractTagValue(param, "comment"),
                patternProperties = extractSchemaMapTag(param, "patternProperties"),
                propertyNames = extractSchemaTag(param, "propertyNames"),
                dependentRequired = extractStringListMapTag(param, "dependentRequired"),
                dependentSchemas = extractSchemaMapTag(param, "dependentSchemas"),
                unevaluatedProperties = extractSchemaTag(param, "unevaluatedProperties"),
                unevaluatedItems = extractSchemaTag(param, "unevaluatedItems"),
                contentSchema = extractSchemaTag(param, "contentSchema"),
                customKeywords = extractCustomKeywords(param),
                deprecated = hasTag(param, "deprecated") || hasAnnotation(param, "Deprecated"),
                readOnly = hasTag(param, "readOnly"),
                writeOnly = hasTag(param, "writeOnly"),
                xml = extractXml(param)
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
            schemaId = schemaId,
            schemaDialect = schemaDialect,
            anchor = anchor,
            dynamicAnchor = dynamicAnchor,
            dynamicRef = dynamicRef,
            defs = defs,
            properties = properties,
            required = requiredFields,
            description = description,
            title = title,
            defaultValue = defaultValue,
            constValue = constValue,
            deprecated = deprecated,
            readOnly = readOnly,
            writeOnly = writeOnly,
            contentMediaType = contentMediaType,
            contentEncoding = contentEncoding,
            minLength = minLength,
            maxLength = maxLength,
            pattern = pattern,
            minimum = minimum,
            maximum = maximum,
            multipleOf = multipleOf,
            exclusiveMinimum = exclusiveMinimum,
            exclusiveMaximum = exclusiveMaximum,
            minItems = minItems,
            maxItems = maxItems,
            uniqueItems = uniqueItems,
            minProperties = minProperties,
            maxProperties = maxProperties,
            externalDocs = externalDocs,
            minContains = minContains,
            maxContains = maxContains,
            contains = contains,
            prefixItems = prefixItems,
            comment = comment,
            patternProperties = patternProperties,
            propertyNames = propertyNames,
            dependentRequired = dependentRequired,
            dependentSchemas = dependentSchemas,
            unevaluatedProperties = unevaluatedProperties,
            unevaluatedItems = unevaluatedItems,
            contentSchema = contentSchema,
            customKeywords = customKeywords,
            allOf = superTypes,
            example = example,
            examples = examples,
            examplesList = examplesList,
            xml = xml,
            discriminator = discriminator
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

    private fun extractDiscriminator(element: org.jetbrains.kotlin.psi.KtDeclaration): domain.Discriminator? {
        val propertyName = extractTagValue(element, "discriminator") ?: return null
        val mappingLiteral = extractTagLiteral(element, "discriminatorMapping")
        val mapping = (mappingLiteral as? Map<*, *>)?.entries?.associate { (k, v) ->
            k.toString() to v.toString()
        } ?: emptyMap()
        val defaultMapping = extractTagValue(element, "discriminatorDefault")

        return domain.Discriminator(
            propertyName = propertyName,
            mapping = mapping,
            defaultMapping = defaultMapping
        )
    }

    private fun extractXml(element: org.jetbrains.kotlin.psi.KtDeclaration): Xml? {
        val name = extractTagValue(element, "xmlName")
        val namespace = extractTagValue(element, "xmlNamespace")
        val prefix = extractTagValue(element, "xmlPrefix")
        val nodeType = extractTagValue(element, "xmlNodeType")
        val attribute = extractTagBoolean(element, "xmlAttribute")
        val wrapped = extractTagBoolean(element, "xmlWrapped")

        if (name == null && namespace == null && prefix == null && nodeType == null &&
            attribute == null && wrapped == null
        ) {
            return null
        }

        return Xml(
            name = name,
            namespace = namespace,
            prefix = prefix,
            nodeType = nodeType,
            attribute = attribute ?: false,
            wrapped = wrapped ?: false
        )
    }

    private fun extractCustomKeywords(element: org.jetbrains.kotlin.psi.KtDeclaration): Map<String, Any?> {
        val literal = extractTagLiteral(element, "keywords") ?: return emptyMap()
        val map = literal as? Map<*, *> ?: return emptyMap()
        return map.entries
            .filter { it.key is String }
            .associate { (key, value) -> key as String to value }
    }

    private fun extractSchemaTag(element: org.jetbrains.kotlin.psi.KtDeclaration, tag: String): SchemaProperty? {
        val raw = extractTagValue(element, tag) ?: return null
        val node = runCatching { jsonMapper.readTree(raw) }.getOrNull() ?: return null
        return parseSchemaPropertyNode(node)
    }

    private fun extractSchemaListTag(
        element: org.jetbrains.kotlin.psi.KtDeclaration,
        tag: String
    ): List<SchemaProperty> {
        val raw = extractTagValue(element, tag) ?: return emptyList()
        val node = runCatching { jsonMapper.readTree(raw) }.getOrNull() ?: return emptyList()
        if (!node.isArray) return emptyList()
        return node.map { parseSchemaPropertyNode(it) }
    }

    private fun extractSchemaMapTag(
        element: org.jetbrains.kotlin.psi.KtDeclaration,
        tag: String
    ): Map<String, SchemaProperty> {
        val raw = extractTagValue(element, tag) ?: return emptyMap()
        val node = runCatching { jsonMapper.readTree(raw) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()
        return node.fields().asSequence().associate { (k, v) ->
            k to parseSchemaPropertyNode(v)
        }
    }

    private fun extractStringListMapTag(
        element: org.jetbrains.kotlin.psi.KtDeclaration,
        tag: String
    ): Map<String, List<String>> {
        val raw = extractTagValue(element, tag) ?: return emptyMap()
        val node = runCatching { jsonMapper.readTree(raw) }.getOrNull() ?: return emptyMap()
        if (!node.isObject) return emptyMap()
        return node.fields().asSequence().associate { (k, v) ->
            val list = if (v.isArray) v.mapNotNull { it.textValue() } else emptyList()
            k to list
        }
    }

    private fun parseSchemaPropertyNode(node: JsonNode): SchemaProperty {
        if (node.isBoolean) {
            return SchemaProperty(booleanSchema = node.booleanValue())
        }
        val obj = node.asObject()
        val ref = obj?.text("\$ref")
        if (ref != null) return SchemaProperty(ref = ref)
        val dynamicRef = obj?.text("\$dynamicRef")
        if (dynamicRef != null) return SchemaProperty(dynamicRef = dynamicRef)
        val customKeywords = parseCustomKeywordsNode(obj)

        return SchemaProperty(
            booleanSchema = null,
            types = parseTypesNode(obj),
            schemaId = obj?.text("\$id"),
            schemaDialect = obj?.text("\$schema"),
            anchor = obj?.text("\$anchor"),
            dynamicAnchor = obj?.text("\$dynamicAnchor"),
            comment = obj?.text("\$comment"),
            format = obj?.text("format"),
            contentMediaType = obj?.text("contentMediaType"),
            contentEncoding = obj?.text("contentEncoding"),
            minLength = obj?.int("minLength"),
            maxLength = obj?.int("maxLength"),
            pattern = obj?.text("pattern"),
            minimum = obj?.double("minimum"),
            maximum = obj?.double("maximum"),
            multipleOf = obj?.double("multipleOf"),
            exclusiveMinimum = obj?.double("exclusiveMinimum"),
            exclusiveMaximum = obj?.double("exclusiveMaximum"),
            minItems = obj?.int("minItems"),
            maxItems = obj?.int("maxItems"),
            uniqueItems = obj?.boolean("uniqueItems"),
            minProperties = obj?.int("minProperties"),
            maxProperties = obj?.int("maxProperties"),
            items = obj?.get("items")?.let { parseSchemaPropertyNode(it) },
            prefixItems = parseSchemaPropertyListNode(obj?.get("prefixItems")),
            contains = obj?.get("contains")?.let { parseSchemaPropertyNode(it) },
            minContains = obj?.int("minContains"),
            maxContains = obj?.int("maxContains"),
            properties = parseSchemaPropertiesNode(obj?.get("properties")),
            required = obj?.get("required")?.asArray()?.mapNotNull { it.textValue() } ?: emptyList(),
            additionalProperties = parseAdditionalPropertiesNode(obj?.get("additionalProperties")),
            defs = parseSchemaPropertiesNode(obj?.get("\$defs")),
            patternProperties = parseSchemaPropertiesNode(obj?.get("patternProperties")),
            propertyNames = obj?.get("propertyNames")?.let { parseSchemaPropertyNode(it) },
            dependentRequired = parseDependentRequiredNode(obj?.get("dependentRequired")),
            dependentSchemas = parseSchemaPropertiesNode(obj?.get("dependentSchemas")),
            description = obj?.text("description"),
            title = obj?.text("title"),
            defaultValue = obj?.get("default")?.let { nodeToValue(it) },
            constValue = obj?.get("const")?.let { nodeToValue(it) },
            deprecated = obj?.boolean("deprecated") ?: false,
            readOnly = obj?.boolean("readOnly") ?: false,
            writeOnly = obj?.boolean("writeOnly") ?: false,
            externalDocs = parseExternalDocsNode(obj?.get("externalDocs")),
            discriminator = parseDiscriminatorNode(obj?.get("discriminator")),
            enumValues = obj?.get("enum")?.asArray()?.map { nodeToValue(it) },
            oneOf = parseSchemaPropertyListNode(obj?.get("oneOf")),
            anyOf = parseSchemaPropertyListNode(obj?.get("anyOf")),
            allOf = parseSchemaPropertyListNode(obj?.get("allOf")),
            not = obj?.get("not")?.let { parseSchemaPropertyNode(it) },
            ifSchema = obj?.get("if")?.let { parseSchemaPropertyNode(it) },
            thenSchema = obj?.get("then")?.let { parseSchemaPropertyNode(it) },
            elseSchema = obj?.get("else")?.let { parseSchemaPropertyNode(it) },
            example = obj?.get("example")?.let { nodeToValue(it) },
            examples = parseSchemaExamplesListNode(obj?.get("examples")),
            xml = parseXmlNode(obj?.get("xml")),
            unevaluatedProperties = obj?.get("unevaluatedProperties")?.let { parseSchemaPropertyNode(it) },
            unevaluatedItems = obj?.get("unevaluatedItems")?.let { parseSchemaPropertyNode(it) },
            contentSchema = obj?.get("contentSchema")?.let { parseSchemaPropertyNode(it) },
            customKeywords = customKeywords
        )
    }

    private fun parseCustomKeywordsNode(obj: JsonNode?): Map<String, Any?> {
        val node = obj?.asObject() ?: return emptyMap()
        return node.fields().asSequence()
            .filter { (key, _) -> !key.startsWith("x-") && key !in schemaKnownKeys }
            .associate { (key, value) -> key to nodeToValue(value) }
    }

    private fun parseSchemaPropertiesNode(node: JsonNode?): Map<String, SchemaProperty> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseSchemaPropertyNode(raw)
        }
    }

    private fun parseSchemaPropertyListNode(node: JsonNode?): List<SchemaProperty> {
        val array = node.asArray() ?: return emptyList()
        return array.map { parseSchemaPropertyNode(it) }
    }

    private fun parseAdditionalPropertiesNode(node: JsonNode?): SchemaProperty? {
        return when {
            node == null -> null
            node.isBoolean -> SchemaProperty(booleanSchema = node.booleanValue())
            node.isObject -> parseSchemaPropertyNode(node)
            else -> null
        }
    }

    private fun parseDependentRequiredNode(node: JsonNode?): Map<String, List<String>> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (key, raw) ->
            val list = raw.asArray()?.mapNotNull { it.textValue() } ?: emptyList()
            key to list
        }
    }

    private fun parseTypesNode(obj: JsonNode?): Set<String> {
        val node = obj?.get("type") ?: return emptySet()
        return when {
            node.isTextual -> setOf(node.asText())
            node.isArray -> node.mapNotNull { it.textValue() }.toSet()
            else -> emptySet()
        }
    }

    private fun parseExternalDocsNode(node: JsonNode?): domain.ExternalDocumentation? {
        val obj = node.asObject() ?: return null
        val url = obj.text("url") ?: return null
        return domain.ExternalDocumentation(
            url = url,
            description = obj.text("description")
        )
    }

    private fun parseDiscriminatorNode(node: JsonNode?): domain.Discriminator? {
        val obj = node.asObject() ?: return null
        return domain.Discriminator(
            propertyName = obj.text("propertyName") ?: "type",
            mapping = obj.get("mapping")?.asObject()?.fields()?.asSequence()?.associate { (k, v) ->
                k to (v.textValue() ?: v.toString())
            } ?: emptyMap(),
            defaultMapping = obj.text("defaultMapping")
        )
    }

    private fun parseXmlNode(node: JsonNode?): Xml? {
        val obj = node.asObject() ?: return null
        return Xml(
            name = obj.text("name"),
            namespace = obj.text("namespace"),
            prefix = obj.text("prefix"),
            nodeType = obj.text("nodeType"),
            attribute = obj.boolean("attribute") ?: false,
            wrapped = obj.boolean("wrapped") ?: false
        )
    }

    private fun parseSchemaExamplesListNode(node: JsonNode?): List<Any?>? {
        if (node == null || !node.isArray) return null
        return node.map { nodeToValue(it) }
    }

    private fun extractExamples(element: org.jetbrains.kotlin.psi.KtDeclaration): Pair<Any?, Map<String, Any?>?> {
        val docComment = element.docComment ?: return Pair(null, null)

        val exampleLines = docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .filter { it.startsWith("@example") && !it.startsWith("@examples") }
            .map { it.removePrefix("@example").trim() }

        if (exampleLines.isEmpty()) return Pair(null, null)

        var simpleExample: Any? = null
        val mapExamples = mutableMapOf<String, Any?>()

        exampleLines.forEach { line ->
            val firstColon = line.indexOf(':')
            if (firstColon > 0) {
                val potentialKey = line.substring(0, firstColon).trim()
                if (potentialKey.matches(Regex("[a-zA-Z0-9_]+"))) {
                    val value = line.substring(firstColon + 1).trim()
                    mapExamples[potentialKey] = parseDocLiteral(value)
                    return@forEach
                }
            }
            simpleExample = parseDocLiteral(line)
        }

        val finalMap = if (mapExamples.isNotEmpty()) mapExamples else null
        return Pair(simpleExample, finalMap)
    }

    private fun extractExamplesList(element: org.jetbrains.kotlin.psi.KtDeclaration): List<Any?>? {
        val raw = extractTagValue(element, "examples") ?: return null
        val parsed = parseDocLiteral(raw)
        return when (parsed) {
            is List<*> -> parsed.map { it }
            else -> null
        }
    }

    private fun extractEnumValues(element: org.jetbrains.kotlin.psi.KtDeclaration): List<Any?>? {
        val docComment = element.docComment ?: return null
        val values = docComment.text
            .split("\n")
            .map { cleanKDocLine(it) }
            .filter { it.startsWith("@enum") }
            .map { it.removePrefix("@enum").trim() }
            .filter { it.isNotEmpty() }
            .map { parseDocLiteral(it) }
        return if (values.isEmpty()) null else values
    }

    private fun extractExampleProperty(element: org.jetbrains.kotlin.psi.KtDeclaration): Any? {
        val (ex, _) = extractExamples(element)
        return ex
    }

    private fun extractTagLiteral(element: org.jetbrains.kotlin.psi.KtDeclaration, tag: String): Any? {
        val raw = extractTagValue(element, tag) ?: return null
        return parseDocLiteral(raw)
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

    private fun parseDocLiteral(raw: String): Any? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        when (trimmed.lowercase()) {
            "null" -> return null
            "true" -> return true
            "false" -> return false
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
            return runCatching { nodeToValue(jsonMapper.readTree(trimmed)) }.getOrElse { trimmed }
        }

        if (NUMBER_REGEX.matches(trimmed)) {
            return trimmed.toIntOrNull()
                ?: trimmed.toLongOrNull()
                ?: trimmed.toDoubleOrNull()
                ?: trimmed
        }

        return trimmed
    }

    private fun nodeToValue(node: JsonNode): Any? {
        return when {
            node.isNull -> null
            node.isTextual -> node.asText()
            node.isNumber -> node.numberValue()
            node.isBoolean -> node.booleanValue()
            node.isArray -> node.map { nodeToValue(it) }
            node.isObject -> node.fields().asSequence().associate { (k, v) -> k to nodeToValue(v) }
            else -> node.toString()
        }
    }

    private fun extractTagInt(element: org.jetbrains.kotlin.psi.KtDeclaration, tag: String): Int? {
        return extractTagValue(element, tag)?.toIntOrNull()
    }

    private fun extractTagDouble(element: org.jetbrains.kotlin.psi.KtDeclaration, tag: String): Double? {
        return extractTagValue(element, tag)?.toDoubleOrNull()
    }

    private fun extractTagBoolean(element: org.jetbrains.kotlin.psi.KtDeclaration, tag: String): Boolean? {
        val raw = extractTagValue(element, tag)
        return when {
            raw == null -> if (hasTag(element, tag)) true else null
            raw.equals("true", ignoreCase = true) -> true
            raw.equals("false", ignoreCase = true) -> false
            else -> null
        }
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

    private companion object {
        private val NUMBER_REGEX = Regex("^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$")
    }
}

private fun JsonNode?.asObject(): JsonNode? = if (this != null && this.isObject) this else null

private fun JsonNode?.asArray(): List<JsonNode>? = if (this != null && this.isArray) this.toList() else null

private fun JsonNode.text(field: String): String? = this.get(field)?.takeIf { !it.isNull }?.asText()

private fun JsonNode.boolean(field: String): Boolean? = this.get(field)?.takeIf { it.isBoolean }?.booleanValue()

private fun JsonNode.int(field: String): Int? = this.get(field)?.takeIf { it.isNumber }?.intValue()

private fun JsonNode.double(field: String): Double? = this.get(field)?.takeIf { it.isNumber }?.doubleValue()
