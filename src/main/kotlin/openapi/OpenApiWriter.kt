package openapi

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import domain.Callback
import domain.Components
import domain.Contact
import domain.Discriminator
import domain.EndpointDefinition
import domain.EndpointParameter
import domain.EndpointResponse
import domain.ExampleObject
import domain.ExternalDocumentation
import domain.Header
import domain.Info
import domain.Link
import domain.MediaTypeObject
import domain.OAuthFlow
import domain.OAuthFlows
import domain.OpenApiDefinition
import domain.ParameterLocation
import domain.ParameterStyle
import domain.PathItem
import domain.RequestBody
import domain.ReferenceObject
import domain.SchemaDefinition
import domain.SchemaProperty
import domain.SecurityRequirement
import domain.SecurityScheme
import domain.Server
import domain.ServerVariable
import domain.Tag
import domain.Xml
import java.io.File

/**
 * Serializes [OpenApiDefinition] instances to JSON or YAML strings.
 */
class OpenApiWriter(
    private val jsonMapper: ObjectMapper = ObjectMapper(JsonFactory()),
    private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory())
) {
    /**
     * Supported output formats for OpenAPI serialization.
     */
    enum class Format {
        /** Auto-detect based on file extension or fallback to JSON. */
        AUTO,
        /** JSON output. */
        JSON,
        /** YAML output. */
        YAML
    }

    /**
     * Writes the OpenAPI definition to a string in the requested [format].
     */
    fun write(definition: OpenApiDefinition, format: Format = Format.JSON, fileName: String? = null): String {
        val resolved = resolveFormat(format, fileName)
        val mapper = if (resolved == Format.JSON) jsonMapper else yamlMapper
        val tree = openApiToMap(definition)
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree)
    }

    /**
     * Writes the OpenAPI definition to JSON.
     */
    fun writeJson(definition: OpenApiDefinition): String = write(definition, Format.JSON)

    /**
     * Writes the OpenAPI definition to YAML.
     */
    fun writeYaml(definition: OpenApiDefinition): String = write(definition, Format.YAML)

    /**
     * Writes the OpenAPI definition to a file.
     */
    fun writeToFile(definition: OpenApiDefinition, file: File, format: Format = Format.AUTO) {
        val content = write(definition, format, file.name)
        file.writeText(content)
    }

    private fun resolveFormat(format: Format, fileName: String?): Format {
        if (format != Format.AUTO) return format
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            "yaml", "yml" -> Format.YAML
            "json" -> Format.JSON
            else -> Format.JSON
        }
    }

    private fun openApiToMap(definition: OpenApiDefinition): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["openapi"] = definition.openapi
        map["info"] = infoToMap(definition.info)
        map.putIfNotNull("jsonSchemaDialect", definition.jsonSchemaDialect)
        map.putIfNotEmpty("servers", definition.servers.map { serverToMap(it) })
        val pathsMap = pathsToMap(definition.paths, definition.pathsExtensions)
        if (pathsMap.isNotEmpty()) {
            map["paths"] = pathsMap
        }
        val webhooksMap = pathsToMap(definition.webhooks, definition.webhooksExtensions)
        if (webhooksMap.isNotEmpty()) {
            map["webhooks"] = webhooksMap
        }
        definition.components?.let { map["components"] = componentsToMap(it) }
        securityToValue(definition.security, definition.securityExplicitEmpty)?.let { map["security"] = it }
        map.putIfNotEmpty("tags", definition.tags.map { tagToMap(it) })
        definition.externalDocs?.let { map["externalDocs"] = externalDocsToMap(it) }
        if (definition.self != null) {
            map["\$self"] = definition.self
        }
        map.putExtensions(definition.extensions)
        return map
    }

    private fun infoToMap(info: Info): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["title"] = info.title
        map["version"] = info.version
        map.putIfNotNull("summary", info.summary)
        map.putIfNotNull("description", info.description)
        map.putIfNotNull("termsOfService", info.termsOfService)
        info.contact?.let { map["contact"] = contactToMap(it) }
        info.license?.let { map["license"] = licenseToMap(it) }
        map.putExtensions(info.extensions)
        return map
    }

    private fun contactToMap(contact: Contact): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("name", contact.name)
        map.putIfNotNull("url", contact.url)
        map.putIfNotNull("email", contact.email)
        map.putExtensions(contact.extensions)
        return map
    }

    private fun licenseToMap(license: domain.License): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["name"] = license.name
        map.putIfNotNull("identifier", license.identifier)
        map.putIfNotNull("url", license.url)
        map.putExtensions(license.extensions)
        return map
    }

    private fun serverToMap(server: Server): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["url"] = server.url
        map.putIfNotNull("description", server.description)
        map.putIfNotNull("name", server.name)
        server.variables?.let { vars ->
            if (vars.isNotEmpty()) {
                map["variables"] = vars.mapValues { serverVariableToMap(it.value) }
            }
        }
        map.putExtensions(server.extensions)
        return map
    }

    private fun serverVariableToMap(variable: ServerVariable): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["default"] = variable.default
        map.putIfNotEmpty("enum", variable.enum)
        map.putIfNotNull("description", variable.description)
        map.putExtensions(variable.extensions)
        return map
    }

    private fun pathItemToMap(item: PathItem): Map<String, Any?> {
        item.ref?.let { return mapOf("\$ref" to it) }

        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("summary", item.summary)
        map.putIfNotNull("description", item.description)
        item.get?.let { map["get"] = operationToMap(it) }
        item.put?.let { map["put"] = operationToMap(it) }
        item.post?.let { map["post"] = operationToMap(it) }
        item.delete?.let { map["delete"] = operationToMap(it) }
        item.options?.let { map["options"] = operationToMap(it) }
        item.head?.let { map["head"] = operationToMap(it) }
        item.patch?.let { map["patch"] = operationToMap(it) }
        item.trace?.let { map["trace"] = operationToMap(it) }
        item.query?.let { map["query"] = operationToMap(it) }
        if (item.additionalOperations.isNotEmpty()) {
            map["additionalOperations"] = item.additionalOperations.mapValues { operationToMap(it.value) }
        }
        map.putIfNotEmpty("parameters", item.parameters.map { parameterToMap(it) })
        map.putIfNotEmpty("servers", item.servers.map { serverToMap(it) })
        map.putExtensions(item.extensions)
        return map
    }

    private fun operationToMap(operation: EndpointDefinition): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map.putIfNotEmpty("tags", operation.tags)
        map.putIfNotNull("summary", operation.summary)
        map.putIfNotNull("description", operation.description)
        operation.externalDocs?.let { map["externalDocs"] = externalDocsToMap(it) }
        map["operationId"] = operation.operationId
        map.putIfNotEmpty("parameters", operation.parameters.map { parameterToMap(it) })
        operation.requestBody?.let { map["requestBody"] = requestBodyToMap(it) }
        if (operation.requestBody == null && operation.requestBodyType != null) {
            map["requestBody"] = requestBodyToMap(
                RequestBody(
                    content = mapOf(
                        "application/json" to MediaTypeObject(
                            schema = SchemaProperty(ref = "#/components/schemas/${operation.requestBodyType}")
                        )
                    ),
                    required = true
                )
            )
        }
        if (operation.responses.isNotEmpty()) {
            map["responses"] = operation.responses.mapValues { responseToMap(it.value) }
        }
        map.putIfNotEmpty("callbacks", callbacksToMap(operation.callbacks))
        map.putIfTrue("deprecated", operation.deprecated)
        securityToValue(operation.security, operation.securityExplicitEmpty)?.let { map["security"] = it }
        map.putIfNotEmpty("servers", operation.servers.map { serverToMap(it) })
        map.putExtensions(operation.extensions)
        return map
    }

    private fun callbacksToMap(callbacks: Map<String, Callback>): Map<String, Any?> {
        if (callbacks.isEmpty()) return emptyMap()
        return callbacks.mapValues { (_, cb) -> callbackToMap(cb) }
    }

    private fun callbackToMap(callback: Callback): Map<String, Any?> {
        return when (callback) {
            is Callback.Reference -> referenceToMap(callback.reference)
            is Callback.Inline -> {
                val map = linkedMapOf<String, Any?>()
                callback.expressions.forEach { (expression, pathItem) ->
                    map[expression] = pathItemToMap(pathItem)
                }
                map.putExtensions(callback.extensions)
                map
            }
        }
    }

    private fun parameterToMap(parameter: EndpointParameter): Map<String, Any?> {
        parameter.reference?.let { return referenceToMap(it) }
        val map = linkedMapOf<String, Any?>()
        map["name"] = parameter.name
        map["in"] = parameterLocation(parameter.location)
        map.putIfNotNull("description", parameter.description)
        map.putIfTrue("required", parameter.isRequired)
        map.putIfTrue("deprecated", parameter.deprecated)
        map.putIfNotNull("allowEmptyValue", parameter.allowEmptyValue)
        parameter.style?.let { map["style"] = parameterStyle(it) }
        map.putIfNotNull("explode", parameter.explode)
        map.putIfNotNull("allowReserved", parameter.allowReserved)

        if (parameter.content.isNotEmpty()) {
            map["content"] = contentToMap(parameter.content)
        } else if (parameter.schema != null) {
            map["schema"] = schemaPropertyToMap(parameter.schema)
        }

        parameter.example?.let { exampleValue(it) }?.let { map["example"] = it }
        map.putIfNotEmpty("examples", exampleMapToMap(parameter.examples))
        map.putExtensions(parameter.extensions)
        return map
    }

    private fun requestBodyToMap(body: RequestBody): Map<String, Any?> {
        body.reference?.let { return referenceToMap(it) }
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("description", body.description)
        map["content"] = contentToMap(body.content)
        map.putIfTrue("required", body.required)
        map.putExtensions(body.extensions)
        return map
    }

    private fun responseToMap(response: EndpointResponse): Map<String, Any?> {
        response.reference?.let { return referenceToMap(it) }
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("summary", response.summary)
        map.putIfNotNull("description", response.description)
        map.putIfNotEmpty("headers", response.headers.mapValues { headerToMap(it.value) })

        if (response.content.isNotEmpty()) {
            map["content"] = contentToMap(response.content)
        } else if (response.type != null) {
            map["content"] = contentToMap(
                mapOf(
                    "application/json" to MediaTypeObject(
                        schema = SchemaProperty(ref = "#/components/schemas/${response.type}")
                    )
                )
            )
        }

        response.links?.let { map["links"] = it.mapValues { linkToMap(it.value) } }
        map.putExtensions(response.extensions)
        return map
    }

    private fun headerToMap(header: Header): Map<String, Any?> {
        header.reference?.let { return referenceToMap(it) }
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("description", header.description)
        map.putIfTrue("required", header.required)
        map.putIfTrue("deprecated", header.deprecated)
        map.putIfNotNull("style", header.style?.let { parameterStyle(it) })
        map.putIfNotNull("explode", header.explode)

        if (header.content.isNotEmpty()) {
            map["content"] = contentToMap(header.content)
        } else if (header.schema != null) {
            map["schema"] = schemaPropertyToMap(header.schema)
        }

        header.example?.let { exampleValue(it) }?.let { map["example"] = it }
        map.putIfNotEmpty("examples", exampleMapToMap(header.examples))
        map.putExtensions(header.extensions)
        return map
    }

    private fun linkToMap(link: Link): Map<String, Any?> {
        link.reference?.let { return referenceToMap(it) }
        link.ref?.let {
            val refMap = linkedMapOf<String, Any?>("\$ref" to it)
            refMap.putIfNotNull("description", link.description)
            return refMap
        }

        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("operationId", link.operationId)
        map.putIfNotNull("operationRef", link.operationRef)
        if (link.parameters.isNotEmpty()) map["parameters"] = link.parameters
        map.putIfNotNull("requestBody", link.requestBody)
        map.putIfNotNull("description", link.description)
        link.server?.let { map["server"] = serverToMap(it) }
        map.putExtensions(link.extensions)
        return map
    }

    private fun contentToMap(content: Map<String, MediaTypeObject>): Map<String, Any?> {
        return content.mapValues { mediaTypeToMap(it.value) }
    }

    private fun mediaTypeToMap(mediaType: MediaTypeObject): Map<String, Any?> {
        mediaType.reference?.let { return referenceToMap(it) }
        mediaType.ref?.let { return mapOf("\$ref" to it) }
        val map = linkedMapOf<String, Any?>()
        mediaType.schema?.let { map["schema"] = schemaPropertyToMap(it) }
        mediaType.itemSchema?.let { map["itemSchema"] = schemaPropertyToMap(it) }
        mediaType.example?.let { exampleValue(it) }?.let { map["example"] = it }
        map.putIfNotEmpty("examples", exampleMapToMap(mediaType.examples))
        map.putIfNotEmpty("encoding", mediaType.encoding.mapValues { encodingToMap(it.value) })
        map.putIfNotEmpty("prefixEncoding", mediaType.prefixEncoding.map { encodingToMap(it) })
        mediaType.itemEncoding?.let { map["itemEncoding"] = encodingToMap(it) }
        map.putExtensions(mediaType.extensions)
        return map
    }

    private fun encodingToMap(encoding: domain.EncodingObject): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("contentType", encoding.contentType)
        map.putIfNotEmpty("headers", encoding.headers.mapValues { headerToMap(it.value) })
        encoding.style?.let { map["style"] = parameterStyle(it) }
        map.putIfNotNull("explode", encoding.explode)
        map.putIfNotNull("allowReserved", encoding.allowReserved)
        map.putIfNotEmpty("encoding", encoding.encoding.mapValues { encodingToMap(it.value) })
        map.putIfNotEmpty("prefixEncoding", encoding.prefixEncoding.map { encodingToMap(it) })
        encoding.itemEncoding?.let { map["itemEncoding"] = encodingToMap(it) }
        map.putExtensions(encoding.extensions)
        return map
    }

    private fun componentsToMap(components: Components): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map.putIfNotEmpty("schemas", components.schemas.mapValues { schemaToMap(it.value) })
        map.putIfNotEmpty("responses", components.responses.mapValues { responseToMap(it.value) })
        map.putIfNotEmpty("parameters", components.parameters.mapValues { parameterToMap(it.value) })
        map.putIfNotEmpty("requestBodies", components.requestBodies.mapValues { requestBodyToMap(it.value) })
        map.putIfNotEmpty("headers", components.headers.mapValues { headerToMap(it.value) })
        map.putIfNotEmpty("securitySchemes", components.securitySchemes.mapValues { securitySchemeToMap(it.value) })
        map.putIfNotEmpty("examples", exampleMapToMap(components.examples))
        map.putIfNotEmpty("links", components.links.mapValues { linkToMap(it.value) })
        map.putIfNotEmpty("callbacks", callbacksToMap(components.callbacks))
        map.putIfNotEmpty("pathItems", components.pathItems.mapValues { pathItemToMap(it.value) })
        map.putIfNotEmpty("mediaTypes", components.mediaTypes.mapValues { mediaTypeToMap(it.value) })
        map.putExtensions(components.extensions)
        return map
    }

    private fun schemaToMap(schema: SchemaDefinition): Any {
        schema.booleanSchema?.let { return it }
        val map = linkedMapOf<String, Any?>()
        schema.ref?.let { map["\$ref"] = it }
        schema.dynamicRef?.let { map["\$dynamicRef"] = it }
        val typeValue = typeValue(schema.types, schema.type)
        typeValue?.let { map["type"] = it }

        map.putIfNotNull("\$id", schema.schemaId)
        map.putIfNotNull("\$schema", schema.schemaDialect)
        map.putIfNotNull("\$anchor", schema.anchor)
        map.putIfNotNull("\$dynamicAnchor", schema.dynamicAnchor)
        map.putIfNotNull("\$comment", schema.comment)
        map.putIfNotEmpty("\$defs", schema.defs.mapValues { schemaPropertyToMap(it.value) })
        map.putIfNotNull("format", schema.format)
        map.putIfNotNull("contentMediaType", schema.contentMediaType)
        map.putIfNotNull("contentEncoding", schema.contentEncoding)
        map.putIfNotNull("minLength", schema.minLength)
        map.putIfNotNull("maxLength", schema.maxLength)
        map.putIfNotNull("pattern", schema.pattern)
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

        schema.items?.let { map["items"] = schemaPropertyToMap(it) }
        map.putIfNotEmpty("prefixItems", schema.prefixItems.map { schemaPropertyToMap(it) })
        schema.contains?.let { map["contains"] = schemaPropertyToMap(it) }
        map.putIfNotNull("minContains", schema.minContains)
        map.putIfNotNull("maxContains", schema.maxContains)
        if (schema.properties.isNotEmpty()) {
            map["properties"] = schema.properties.mapValues { schemaPropertyToMap(it.value) }
        }
        if (schema.patternProperties.isNotEmpty()) {
            map["patternProperties"] = schema.patternProperties.mapValues { schemaPropertyToMap(it.value) }
        }
        schema.propertyNames?.let { map["propertyNames"] = schemaPropertyToMap(it) }
        schema.additionalProperties?.let { map["additionalProperties"] = schemaPropertyToMap(it) }
        map.putIfNotEmpty("required", schema.required)
        map.putIfNotEmpty("dependentRequired", schema.dependentRequired)
        if (schema.dependentSchemas.isNotEmpty()) {
            map["dependentSchemas"] = schema.dependentSchemas.mapValues { schemaPropertyToMap(it.value) }
        }
        schema.enumValues?.let { map["enum"] = it }
        map.putIfNotNull("description", schema.description)
        map.putIfNotNull("title", schema.title)
        map.putIfNotNull("default", schema.defaultValue)
        map.putIfNotNull("const", schema.constValue)
        map.putIfTrue("deprecated", schema.deprecated)
        map.putIfTrue("readOnly", schema.readOnly)
        map.putIfTrue("writeOnly", schema.writeOnly)
        schema.externalDocs?.let { map["externalDocs"] = externalDocsToMap(it) }
        schema.discriminator?.let { map["discriminator"] = discriminatorToMap(it) }
        schema.xml?.let { map["xml"] = xmlToMap(it) }
        schema.unevaluatedProperties?.let { map["unevaluatedProperties"] = schemaPropertyToMap(it) }
        schema.unevaluatedItems?.let { map["unevaluatedItems"] = schemaPropertyToMap(it) }
        schema.contentSchema?.let { map["contentSchema"] = schemaPropertyToMap(it) }
        map.putIfNotEmpty("oneOf", schemaCompositionToMaps(schema.oneOf, schema.oneOfSchemas))
        map.putIfNotEmpty("anyOf", schemaCompositionToMaps(schema.anyOf, schema.anyOfSchemas))
        map.putIfNotEmpty("allOf", schemaCompositionToMaps(schema.allOf, schema.allOfSchemas))
        schema.not?.let { map["not"] = schemaPropertyToMap(it) }
        schema.ifSchema?.let { map["if"] = schemaPropertyToMap(it) }
        schema.thenSchema?.let { map["then"] = schemaPropertyToMap(it) }
        schema.elseSchema?.let { map["else"] = schemaPropertyToMap(it) }
        map.putIfNotNull("example", schema.example)
        schema.examplesList?.let { map["examples"] = it }
        if (schema.examplesList == null) {
            schema.examples?.let { map["examples"] = it.values.toList() }
        }
        map.putCustomKeywords(schema.customKeywords)
        map.putExtensions(schema.extensions)
        return map
    }

    private fun schemaPropertyToMap(schema: SchemaProperty): Any {
        schema.booleanSchema?.let { return it }

        val map = linkedMapOf<String, Any?>()
        schema.ref?.let { map["\$ref"] = it }
        schema.dynamicRef?.let { map["\$dynamicRef"] = it }
        val typeValue = typeValue(schema.types, null)
        typeValue?.let { map["type"] = it }

        map.putIfNotNull("\$id", schema.schemaId)
        map.putIfNotNull("\$schema", schema.schemaDialect)
        map.putIfNotNull("\$anchor", schema.anchor)
        map.putIfNotNull("\$dynamicAnchor", schema.dynamicAnchor)
        map.putIfNotNull("format", schema.format)
        map.putIfNotNull("contentMediaType", schema.contentMediaType)
        map.putIfNotNull("contentEncoding", schema.contentEncoding)
        map.putIfNotNull("minLength", schema.minLength)
        map.putIfNotNull("maxLength", schema.maxLength)
        map.putIfNotNull("pattern", schema.pattern)
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
        schema.items?.let { map["items"] = schemaPropertyToMap(it) }
        map.putIfNotEmpty("prefixItems", schema.prefixItems.map { schemaPropertyToMap(it) })
        schema.contains?.let { map["contains"] = schemaPropertyToMap(it) }
        map.putIfNotNull("minContains", schema.minContains)
        map.putIfNotNull("maxContains", schema.maxContains)
        if (schema.properties.isNotEmpty()) {
            map["properties"] = schema.properties.mapValues { schemaPropertyToMap(it.value) }
        }
        map.putIfNotEmpty("required", schema.required)
        schema.additionalProperties?.let { map["additionalProperties"] = schemaPropertyToMap(it) }
        map.putIfNotEmpty("\$defs", schema.defs.mapValues { schemaPropertyToMap(it.value) })
        if (schema.patternProperties.isNotEmpty()) {
            map["patternProperties"] = schema.patternProperties.mapValues { schemaPropertyToMap(it.value) }
        }
        schema.propertyNames?.let { map["propertyNames"] = schemaPropertyToMap(it) }
        map.putIfNotEmpty("dependentRequired", schema.dependentRequired)
        if (schema.dependentSchemas.isNotEmpty()) {
            map["dependentSchemas"] = schema.dependentSchemas.mapValues { schemaPropertyToMap(it.value) }
        }
        map.putIfNotNull("description", schema.description)
        map.putIfNotNull("title", schema.title)
        map.putIfNotNull("default", schema.defaultValue)
        map.putIfNotNull("const", schema.constValue)
        map.putIfTrue("deprecated", schema.deprecated)
        map.putIfTrue("readOnly", schema.readOnly)
        map.putIfTrue("writeOnly", schema.writeOnly)
        schema.externalDocs?.let { map["externalDocs"] = externalDocsToMap(it) }
        schema.discriminator?.let { map["discriminator"] = discriminatorToMap(it) }
        map.putIfNotNull("\$comment", schema.comment)
        schema.enumValues?.let { map["enum"] = it }
        map.putIfNotEmpty("oneOf", schema.oneOf.map { schemaPropertyToMap(it) })
        map.putIfNotEmpty("anyOf", schema.anyOf.map { schemaPropertyToMap(it) })
        map.putIfNotEmpty("allOf", schema.allOf.map { schemaPropertyToMap(it) })
        schema.not?.let { map["not"] = schemaPropertyToMap(it) }
        schema.ifSchema?.let { map["if"] = schemaPropertyToMap(it) }
        schema.thenSchema?.let { map["then"] = schemaPropertyToMap(it) }
        schema.elseSchema?.let { map["else"] = schemaPropertyToMap(it) }
        map.putIfNotNull("example", schema.example)
        schema.examples?.let { map["examples"] = it }
        schema.xml?.let { map["xml"] = xmlToMap(it) }
        schema.unevaluatedProperties?.let { map["unevaluatedProperties"] = schemaPropertyToMap(it) }
        schema.unevaluatedItems?.let { map["unevaluatedItems"] = schemaPropertyToMap(it) }
        schema.contentSchema?.let { map["contentSchema"] = schemaPropertyToMap(it) }
        map.putCustomKeywords(schema.customKeywords)
        map.putExtensions(schema.extensions)
        return map
    }

    private fun referenceToMap(reference: ReferenceObject): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>("\$ref" to reference.ref)
        map.putIfNotNull("summary", reference.summary)
        map.putIfNotNull("description", reference.description)
        return map
    }

    private fun discriminatorToMap(discriminator: Discriminator): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["propertyName"] = discriminator.propertyName
        if (discriminator.mapping.isNotEmpty()) map["mapping"] = discriminator.mapping
        map.putIfNotNull("defaultMapping", discriminator.defaultMapping)
        map.putExtensions(discriminator.extensions)
        return map
    }

    private fun xmlToMap(xml: Xml): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("name", xml.name)
        map.putIfNotNull("namespace", xml.namespace)
        map.putIfNotNull("prefix", xml.prefix)
        map.putIfNotNull("nodeType", xml.nodeType)
        map.putIfTrue("attribute", xml.attribute)
        map.putIfTrue("wrapped", xml.wrapped)
        map.putExtensions(xml.extensions)
        return map
    }

    private fun externalDocsToMap(docs: ExternalDocumentation): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["url"] = docs.url
        map.putIfNotNull("description", docs.description)
        map.putExtensions(docs.extensions)
        return map
    }

    private fun tagToMap(tag: Tag): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["name"] = tag.name
        map.putIfNotNull("summary", tag.summary)
        map.putIfNotNull("description", tag.description)
        tag.externalDocs?.let { map["externalDocs"] = externalDocsToMap(it) }
        map.putIfNotNull("parent", tag.parent)
        map.putIfNotNull("kind", tag.kind)
        map.putExtensions(tag.extensions)
        return map
    }

    private fun securitySchemeToMap(scheme: SecurityScheme): Map<String, Any?> {
        scheme.reference?.let { return referenceToMap(it) }
        val map = linkedMapOf<String, Any?>()
        map["type"] = scheme.type
        map.putIfNotNull("description", scheme.description)
        map.putIfNotNull("name", scheme.name)
        map.putIfNotNull("in", scheme.`in`)
        map.putIfNotNull("scheme", scheme.scheme)
        map.putIfNotNull("bearerFormat", scheme.bearerFormat)
        scheme.flows?.let { map["flows"] = oauthFlowsToMap(it) }
        map.putIfNotNull("openIdConnectUrl", scheme.openIdConnectUrl)
        map.putIfNotNull("oauth2MetadataUrl", scheme.oauth2MetadataUrl)
        map.putIfTrue("deprecated", scheme.deprecated)
        map.putExtensions(scheme.extensions)
        return map
    }

    private fun oauthFlowsToMap(flows: OAuthFlows): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        flows.implicit?.let { map["implicit"] = oauthFlowToMap(it) }
        flows.password?.let { map["password"] = oauthFlowToMap(it) }
        flows.clientCredentials?.let { map["clientCredentials"] = oauthFlowToMap(it) }
        flows.authorizationCode?.let { map["authorizationCode"] = oauthFlowToMap(it) }
        flows.deviceAuthorization?.let { map["deviceAuthorization"] = oauthFlowToMap(it) }
        map.putExtensions(flows.extensions)
        return map
    }

    private fun oauthFlowToMap(flow: OAuthFlow): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        map["scopes"] = flow.scopes
        map.putIfNotNull("authorizationUrl", flow.authorizationUrl)
        map.putIfNotNull("tokenUrl", flow.tokenUrl)
        map.putIfNotNull("refreshUrl", flow.refreshUrl)
        map.putIfNotNull("deviceAuthorizationUrl", flow.deviceAuthorizationUrl)
        map.putExtensions(flow.extensions)
        return map
    }

    private fun exampleMapToMap(examples: Map<String, ExampleObject>): Map<String, Any?> {
        if (examples.isEmpty()) return emptyMap()
        return examples.mapValues { exampleObjectToMap(it.value) }
    }

    private fun exampleObjectToMap(example: ExampleObject): Map<String, Any?> {
        example.ref?.let {
            val refMap = linkedMapOf<String, Any?>("\$ref" to it)
            refMap.putIfNotNull("summary", example.summary)
            refMap.putIfNotNull("description", example.description)
            return refMap
        }

        val map = linkedMapOf<String, Any?>()
        map.putIfNotNull("summary", example.summary)
        map.putIfNotNull("description", example.description)
        map.putIfNotNull("dataValue", example.dataValue)
        map.putIfNotNull("serializedValue", example.serializedValue)
        map.putIfNotNull("externalValue", example.externalValue)
        map.putIfNotNull("value", example.value)
        map.putExtensions(example.extensions)
        return map
    }

    private fun exampleValue(example: ExampleObject): Any? {
        return when {
            example.ref != null -> mapOf("\$ref" to example.ref)
            example.dataValue != null -> example.dataValue
            example.value != null -> example.value
            example.serializedValue != null -> example.serializedValue
            example.externalValue != null -> example.externalValue
            else -> null
        }
    }

    private fun typeValue(types: Set<String>, fallback: String?): Any? {
        if (types.isNotEmpty()) {
            val list = types.toList().sorted()
            return if (list.size == 1) list.first() else list
        }
        return fallback
    }

    private fun schemaRefListToMaps(refs: List<String>): List<Map<String, Any?>> {
        if (refs.isEmpty()) return emptyList()
        return refs.map { token ->
            val ref = if (looksLikeRef(token)) token else "#/components/schemas/$token"
            mapOf("\$ref" to ref)
        }
    }

    private fun schemaCompositionToMaps(
        refs: List<String>,
        inlines: List<SchemaProperty>
    ): List<Any> {
        if (refs.isEmpty() && inlines.isEmpty()) return emptyList()
        val entries = mutableListOf<Any>()
        entries.addAll(schemaRefListToMaps(refs))
        entries.addAll(inlines.map { schemaPropertyToMap(it) })
        return entries
    }

    private fun looksLikeRef(token: String): Boolean {
        return token.startsWith("#") || token.startsWith("./") || token.contains("/") || token.endsWith(".json") || token.endsWith(".yaml")
    }

    private fun pathsToMap(
        paths: Map<String, PathItem>,
        extensions: Map<String, Any?>
    ): Map<String, Any?> {
        if (paths.isEmpty() && extensions.isEmpty()) return emptyMap()
        val map = linkedMapOf<String, Any?>()
        paths.forEach { (path, item) ->
            map[path] = pathItemToMap(item)
        }
        extensions.forEach { (key, value) ->
            if (key.startsWith("x-")) {
                map[key] = value
            }
        }
        return map
    }

    private fun parameterLocation(location: ParameterLocation): String {
        return when (location) {
            ParameterLocation.PATH -> "path"
            ParameterLocation.QUERY -> "query"
            ParameterLocation.QUERYSTRING -> "querystring"
            ParameterLocation.HEADER -> "header"
            ParameterLocation.COOKIE -> "cookie"
        }
    }

    private fun parameterStyle(style: ParameterStyle): String {
        return when (style) {
            ParameterStyle.MATRIX -> "matrix"
            ParameterStyle.LABEL -> "label"
            ParameterStyle.SIMPLE -> "simple"
            ParameterStyle.FORM -> "form"
            ParameterStyle.SPACE_DELIMITED -> "spaceDelimited"
            ParameterStyle.PIPE_DELIMITED -> "pipeDelimited"
            ParameterStyle.DEEP_OBJECT -> "deepObject"
            ParameterStyle.COOKIE -> "cookie"
        }
    }

    private fun securityToList(security: List<SecurityRequirement>): List<Map<String, List<String>>> {
        if (security.isEmpty()) return emptyList()
        return security.map { requirement ->
            requirement.entries.associate { it.key to it.value }
        }
    }

    private fun securityToValue(
        security: List<SecurityRequirement>,
        explicitEmpty: Boolean
    ): Any? {
        if (security.isNotEmpty()) return securityToList(security)
        return if (explicitEmpty) emptyList<Map<String, List<String>>>() else null
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

    private fun MutableMap<String, Any?>.putCustomKeywords(custom: Map<String, Any?>) {
        if (custom.isEmpty()) return
        custom.forEach { (key, value) ->
            if (key.startsWith("x-")) return@forEach
            if (!this.containsKey(key)) {
                this[key] = value
            }
        }
    }

    private fun MutableMap<String, Any?>.putExtensions(extensions: Map<String, Any?>) {
        if (extensions.isEmpty()) return
        extensions.forEach { (key, value) ->
            if (key.startsWith("x-")) {
                this[key] = value
            }
        }
    }
}
