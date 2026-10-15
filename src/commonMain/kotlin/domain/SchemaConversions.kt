/**
 * Conversions between [SchemaDefinition] (root/component schema) and [SchemaProperty]
 * (nested/subschema).
 *
 * Because both implement [SchemaCore], conversions preserve 100% of JSON Schema Draft 2020-12 and
 * OpenAPI 3.2 keywords without lossy manual mapping.
 */
package domain

/**
 * Convert a [SchemaDefinition] (component/root schema) to a [SchemaProperty].
 *
 * This is useful when schema selection logic (e.g. dynamic refs) needs a unified view over both
 * root schemas and nested schema properties.
 *
 * @return A [SchemaProperty] containing all schema keywords from this [SchemaDefinition].
 */
fun SchemaDefinition.toSchemaProperty(): SchemaProperty {
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
      collectionFormat = collectionFormat,
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
      examples =
          if (examplesList != null) examplesList
          else if (examples != null) examples.values.toList() else null,
      xml = xml,
      prefixItems = prefixItems,
      contains = contains,
      minContains = minContains,
      maxContains = maxContains,
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
      extensions = extensions)
}

/**
 * Convert a [SchemaProperty] into a named [SchemaDefinition] preserving all JSON Schema 2020-12 and
 * OpenAPI 3.2 properties.
 *
 * @param name The component or model name for the resulting [SchemaDefinition].
 * @return A [SchemaDefinition] containing all properties of this [SchemaProperty].
 */
fun SchemaProperty.toSchemaDefinition(name: String): SchemaDefinition {
  return SchemaDefinition(
      name = name,
      ref = ref,
      dynamicRef = dynamicRef,
      booleanSchema = booleanSchema,
      type = type,
      typeExplicit = types.isNotEmpty(),
      schemaId = schemaId,
      schemaDialect = schemaDialect,
      anchor = anchor,
      dynamicAnchor = dynamicAnchor,
      comment = comment,
      defs = defs,
      types = types,
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
      collectionFormat = collectionFormat,
      uniqueItems = uniqueItems,
      minProperties = minProperties,
      maxProperties = maxProperties,
      items = items,
      prefixItems = prefixItems,
      contains = contains,
      minContains = minContains,
      maxContains = maxContains,
      properties = properties,
      additionalProperties = additionalProperties,
      required = required,
      enumValues = enumValues,
      description = description,
      title = title,
      defaultValue = defaultValue,
      constValue = constValue,
      deprecated = deprecated,
      readOnly = readOnly,
      writeOnly = writeOnly,
      externalDocs = externalDocs,
      discriminator = discriminator,
      xml = xml,
      oneOf = oneOf.mapNotNull { it.ref },
      oneOfSchemas = oneOf,
      anyOf = anyOf.mapNotNull { it.ref },
      anyOfSchemas = anyOf,
      allOf = allOf.mapNotNull { it.ref },
      allOfSchemas = allOf,
      not = not,
      ifSchema = ifSchema,
      thenSchema = thenSchema,
      elseSchema = elseSchema,
      example = example,
      examplesList = examples,
      patternProperties = patternProperties,
      propertyNames = propertyNames,
      dependentRequired = dependentRequired,
      dependentSchemas = dependentSchemas,
      unevaluatedProperties = unevaluatedProperties,
      unevaluatedItems = unevaluatedItems,
      contentSchema = contentSchema,
      customKeywords = customKeywords,
      extensions = extensions)
}
