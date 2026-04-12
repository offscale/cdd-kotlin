package domain

/**
 * Convert a [SchemaDefinition] (component/root schema) to a [SchemaProperty].
 *
 * This is useful when schema selection logic (e.g. dynamic refs) needs a unified view
 * over both root schemas and nested schema properties.
 */
fun SchemaDefinition.toSchemaProperty(): SchemaProperty {
    val effectiveTypes = effectiveTypes
    return SchemaProperty(
        booleanSchema = booleanSchema,
        types = effectiveTypes,
        format = format,
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
        items = items,
        additionalProperties = additionalProperties,
        ref = ref,
        dynamicRef = dynamicRef,
        schemaId = schemaId,
        schemaDialect = schemaDialect,
        anchor = anchor,
        dynamicAnchor = dynamicAnchor,
        comment = comment,
        description = description,
        title = title,
        defaultValue = defaultValue,
        constValue = constValue,
        enumValues = enumValues,
        deprecated = deprecated,
        readOnly = readOnly,
        writeOnly = writeOnly,
        externalDocs = externalDocs,
        discriminator = discriminator,
        example = example,
        examples = examplesList ?: examples?.values?.toList(),
        xml = xml,
        prefixItems = prefixItems,
        contains = contains,
        properties = properties,
        required = required,
        patternProperties = patternProperties,
        propertyNames = propertyNames,
        dependentRequired = dependentRequired,
        dependentSchemas = dependentSchemas,
        unevaluatedProperties = unevaluatedProperties,
        unevaluatedItems = unevaluatedItems,
        contentSchema = contentSchema,
        oneOf = oneOfSchemas,
        anyOf = anyOfSchemas,
        allOf = allOfSchemas,
        not = not,
        ifSchema = ifSchema,
        thenSchema = thenSchema,
        elseSchema = elseSchema,
        customKeywords = customKeywords,
        defs = defs,
        extensions = extensions
    )
}
