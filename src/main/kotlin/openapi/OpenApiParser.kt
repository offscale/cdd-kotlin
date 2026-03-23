package openapi

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonNode
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
import domain.HttpMethod
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
import psi.ReferenceResolver
import psi.TypeMappers
import java.io.File
import java.net.URI

/**
 * Parses OpenAPI 3.2 JSON/YAML documents into the domain IR models.
 *
 * This parser is intentionally lenient: missing required fields are defaulted
 * where possible to keep round-trip workflows resilient.
 */
class OpenApiParser(
    private val jsonMapper: ObjectMapper = ObjectMapper(JsonFactory()),
    private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory())
) {
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
        "nullable",
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

    private var currentSelfBase: String? = null
    private var currentRegistry: OpenApiDocumentRegistry? = null

    private inline fun <T> withSelfBase(
        self: String?,
        baseUri: String?,
        registry: OpenApiDocumentRegistry?,
        block: () -> T
    ): T {
        val previous = currentSelfBase
        val previousRegistry = currentRegistry
        currentSelfBase = resolveSelfBase(self, baseUri)
        currentRegistry = registry
        return try {
            block()
        } finally {
            currentSelfBase = previous
            currentRegistry = previousRegistry
        }
    }

    private fun normalizeSelfBase(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return trimmed.substringBefore("#")
    }

    private fun resolveSelfBase(self: String?, baseUri: String?): String? {
        val normalizedSelf = normalizeSelfBase(self)
        if (!normalizedSelf.isNullOrBlank()) {
            val normalizedBase = normalizeSelfBase(baseUri)
            if (normalizedBase.isNullOrBlank() || isAbsoluteUri(normalizedSelf)) {
                return normalizedSelf
            }
            return resolveAgainstBase(normalizedBase, normalizedSelf)
        }
        return normalizeSelfBase(baseUri)
    }

    private fun isAbsoluteUri(value: String): Boolean {
        return try {
            URI(value).isAbsolute
        } catch (_: Exception) {
            false
        }
    }

    private fun isSelfBaseMatch(refBase: String, contextBase: String? = currentSelfBase): Boolean {
        val selfBase = contextBase?.trimEnd('#').orEmpty()
        if (selfBase.isBlank()) return true
        if (refBase.isBlank()) return true
        val normalizedRefBase = refBase.trimEnd('#')
        return normalizedRefBase == selfBase
    }
    /**
     * Supported input formats for OpenAPI sources.
     */
    enum class Format {
        /** Auto-detect format based on file extension or content. */
        AUTO,
        /** JSON input. */
        JSON,
        /** YAML input. */
        YAML
    }

    /**
     * Parses a raw OpenAPI document string into the IR representation.
     *
     * @param source Raw JSON or YAML content.
     * @param formatHint Optional format hint for parsing.
     * @param baseUri Optional base URI for resolving relative $ref values and relative `$self` bases.
     * @param registry Optional registry for resolving external $ref targets without network access.
     * @return Parsed [OpenApiDefinition].
     */
    fun parseString(
        source: String,
        formatHint: Format = Format.AUTO,
        baseUri: String? = null,
        registry: OpenApiDocumentRegistry? = null
    ): OpenApiDefinition {
        val format = resolveFormat(source, formatHint, null)
        val root = readTree(source, format)
        return parseOpenApi(root, baseUri, registry)
    }

    /**
     * Parses an OpenAPI document from a file into the IR representation.
     *
     * @param file OpenAPI JSON/YAML file.
     * @param formatHint Optional format hint for parsing.
     * @param baseUri Optional base URI for resolving relative $ref values and relative `$self` bases.
     * @param registry Optional registry for resolving external $ref targets without network access.
     * @return Parsed [OpenApiDefinition].
     */
    fun parseFile(
        file: File,
        formatHint: Format = Format.AUTO,
        baseUri: String? = null,
        registry: OpenApiDocumentRegistry? = null
    ): OpenApiDefinition {
        val content = file.readText()
        val format = resolveFormat(content, formatHint, file.name)
        val root = readTree(content, format)
        return parseOpenApi(root, baseUri, registry)
    }

    /**
     * Parses a raw document string into either an OpenAPI document or a Schema document.
     *
     * If the root contains an `openapi` field, it is treated as an OpenAPI Object.
     * Otherwise, the root is parsed as a Schema Object (boolean or object).
     */
    fun parseDocumentString(
        source: String,
        formatHint: Format = Format.AUTO,
        baseUri: String? = null,
        registry: OpenApiDocumentRegistry? = null
    ): OpenApiDocument {
        val format = resolveFormat(source, formatHint, null)
        val root = readTree(source, format)
        return parseDocument(root, baseUri, registry)
    }

    /**
     * Parses a document from a file into either an OpenAPI document or a Schema document.
     *
     * @param baseUri Optional base URI for resolving relative $ref values and relative `$self` bases.
     * @param registry Optional registry for resolving external $ref targets without network access.
     */
    fun parseDocumentFile(
        file: File,
        formatHint: Format = Format.AUTO,
        baseUri: String? = null,
        registry: OpenApiDocumentRegistry? = null
    ): OpenApiDocument {
        val content = file.readText()
        val format = resolveFormat(content, formatHint, file.name)
        val root = readTree(content, format)
        return parseDocument(root, baseUri, registry)
    }

    /**
     * Parses a raw document string as a Schema Object, throwing if it is not a Schema document.
     */
    fun parseSchemaString(
        source: String,
        formatHint: Format = Format.AUTO,
        baseUri: String? = null,
        registry: OpenApiDocumentRegistry? = null
    ): SchemaProperty {
        return when (val doc = parseDocumentString(source, formatHint, baseUri, registry)) {
            is OpenApiDocument.Schema -> doc.schema
            is OpenApiDocument.OpenApi ->
                throw IllegalArgumentException("Document is an OpenAPI Object, not a Schema Object")
        }
    }

    /**
     * Parses a file as a Schema Object, throwing if it is not a Schema document.
     */
    fun parseSchemaFile(
        file: File,
        formatHint: Format = Format.AUTO,
        baseUri: String? = null,
        registry: OpenApiDocumentRegistry? = null
    ): SchemaProperty {
        return when (val doc = parseDocumentFile(file, formatHint, baseUri, registry)) {
            is OpenApiDocument.Schema -> doc.schema
            is OpenApiDocument.OpenApi ->
                throw IllegalArgumentException("Document is an OpenAPI Object, not a Schema Object")
        }
    }

    private fun resolveFormat(source: String, hint: Format, fileName: String?): Format {
        if (hint != Format.AUTO) return hint
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (ext == "json") return Format.JSON
        if (ext == "yaml" || ext == "yml") return Format.YAML
        val trimmed = source.trimStart()
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) Format.JSON else Format.YAML
    }

    private fun readTree(source: String, format: Format): JsonNode {
        val mapper = if (format == Format.JSON) jsonMapper else yamlMapper
        return mapper.readTree(source)
    }

    private fun parseDocument(
        root: JsonNode,
        baseUri: String?,
        registry: OpenApiDocumentRegistry?
    ): OpenApiDocument {
        val obj = root.asObject()
        return if (obj != null && obj.has("openapi")) {
            OpenApiDocument.OpenApi(parseOpenApi(root, baseUri, registry))
        } else {
            OpenApiDocument.Schema(parseSchemaRoot(root))
        }
    }

    private fun parseSchemaRoot(root: JsonNode): SchemaProperty {
        if (!root.isObject && !root.isBoolean) {
            throw IllegalArgumentException("Schema document root must be an object or boolean")
        }
        return parseSchemaProperty(root)
    }

    private fun parseOpenApi(
        root: JsonNode,
        baseUri: String?,
        registry: OpenApiDocumentRegistry?
    ): OpenApiDefinition {
        val self = root.text("\$self")
        return withSelfBase(self, baseUri, registry) {
            val components = parseComponents(root.get("components"))
            val pathsResult = parsePaths(root.get("paths"), components)
            val webhooksResult = parsePaths(root.get("webhooks"), components)
            val securityResult = parseSecurityRequirements(root.get("security"))
            OpenApiDefinition(
                openapi = root.text("openapi") ?: "3.2.0",
                info = parseInfo(root.get("info")) ?: Info(title = "Unknown", version = "0.0.0"),
                jsonSchemaDialect = root.text("jsonSchemaDialect"),
                servers = parseServers(root.get("servers")),
                paths = pathsResult.items,
                pathsExtensions = pathsResult.extensions,
                pathsExplicitEmpty = pathsResult.explicitEmpty,
                webhooks = webhooksResult.items,
                webhooksExtensions = webhooksResult.extensions,
                webhooksExplicitEmpty = webhooksResult.explicitEmpty,
                components = components,
                security = securityResult.requirements,
                securityExplicitEmpty = securityResult.explicitEmpty,
                tags = parseTags(root.get("tags")),
                externalDocs = parseExternalDocs(root.get("externalDocs")),
                self = self,
                extensions = parseExtensions(root)
            )
        }
    }

    private fun parseInfo(node: JsonNode?): Info? {
        val obj = node.asObject() ?: return null
        return Info(
            title = obj.text("title") ?: "Unknown",
            version = obj.text("version") ?: "0.0.0",
            summary = obj.text("summary"),
            description = obj.text("description"),
            termsOfService = obj.text("termsOfService"),
            contact = parseContact(obj.get("contact")),
            license = parseLicense(obj.get("license")),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseContact(node: JsonNode?): Contact? {
        val obj = node.asObject() ?: return null
        return Contact(
            name = obj.text("name"),
            url = obj.text("url"),
            email = obj.text("email"),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseLicense(node: JsonNode?): domain.License? {
        val obj = node.asObject() ?: return null
        return domain.License(
            name = obj.text("name") ?: "Unknown",
            identifier = obj.text("identifier"),
            url = obj.text("url"),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseServers(node: JsonNode?): List<Server> {
        val array = node.asArray() ?: return emptyList()
        return array.mapNotNull { srv ->
            val obj = srv.asObject() ?: return@mapNotNull null
            Server(
                url = obj.text("url") ?: "/",
                description = obj.text("description"),
                variables = parseServerVariables(obj.get("variables")),
                name = obj.text("name"),
                extensions = parseExtensions(obj)
            )
        }
    }

    private fun parseServerVariables(node: JsonNode?): Map<String, ServerVariable> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            val value = raw.asObject()
            val enumValues = value?.get("enum")?.asArray()?.mapNotNull { it.textValue() }
            name to ServerVariable(
                default = value?.text("default") ?: "",
                enum = enumValues,
                description = value?.text("description"),
                extensions = parseExtensions(value)
            )
        }
    }

    private data class PathsParseResult(
        val items: Map<String, PathItem>,
        val extensions: Map<String, Any?>,
        val explicitEmpty: Boolean
    )

    private fun parsePaths(node: JsonNode?, components: Components?): PathsParseResult {
        val obj = node.asObject() ?: return PathsParseResult(emptyMap(), emptyMap(), false)
        val explicitEmpty = obj.size() == 0
        val items = LinkedHashMap<String, PathItem>()
        val extensions = LinkedHashMap<String, Any?>()
        obj.fields().forEach { (path, raw) ->
            if (path.startsWith("x-")) {
                extensions[path] = nodeToValue(raw)
            } else {
                items[path] = parsePathItem(path, raw, components)
            }
        }
        return PathsParseResult(items, extensions, explicitEmpty)
    }

    private fun parsePathItem(path: String, node: JsonNode, components: Components?): PathItem {
        val obj = node.asObject() ?: return PathItem()
        val ref = obj.text("\$ref")

        val additionalOpsFromField = parseAdditionalOperations(obj.get("additionalOperations"), path, components)
        val additionalOpsFromLoose = parseAdditionalOperationFields(obj, path, components)

        return PathItem(
            ref = ref,
            summary = obj.text("summary"),
            description = obj.text("description"),
            get = parseOperationIfPresent(path, "get", HttpMethod.GET, obj.get("get"), components),
            put = parseOperationIfPresent(path, "put", HttpMethod.PUT, obj.get("put"), components),
            post = parseOperationIfPresent(path, "post", HttpMethod.POST, obj.get("post"), components),
            delete = parseOperationIfPresent(path, "delete", HttpMethod.DELETE, obj.get("delete"), components),
            options = parseOperationIfPresent(path, "options", HttpMethod.OPTIONS, obj.get("options"), components),
            head = parseOperationIfPresent(path, "head", HttpMethod.HEAD, obj.get("head"), components),
            patch = parseOperationIfPresent(path, "patch", HttpMethod.PATCH, obj.get("patch"), components),
            trace = parseOperationIfPresent(path, "trace", HttpMethod.TRACE, obj.get("trace"), components),
            query = parseOperationIfPresent(path, "query", HttpMethod.QUERY, obj.get("query"), components),
            additionalOperations = additionalOpsFromField + additionalOpsFromLoose,
            parameters = parseParameters(obj.get("parameters"), components),
            servers = parseServers(obj.get("servers")),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseAdditionalOperations(
        node: JsonNode?,
        path: String,
        components: Components?
    ): Map<String, EndpointDefinition> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (method, raw) ->
            method to parseOperation(path, method, HttpMethod.CUSTOM, raw, components)
        }
    }

    private fun parseAdditionalOperationFields(
        obj: JsonNode,
        path: String,
        components: Components?
    ): Map<String, EndpointDefinition> {
        val reserved = setOf(
            "\$ref", "summary", "description", "servers", "parameters", "additionalOperations",
            "get", "put", "post", "delete", "options", "head", "patch", "trace", "query"
        )
        val fields = obj.fields().asSequence()
        val map = LinkedHashMap<String, EndpointDefinition>()
        fields.forEach { (key, value) ->
            if (reserved.contains(key)) return@forEach
            if (key.startsWith("x-")) return@forEach
            if (!value.isObject) return@forEach
            map[key] = parseOperation(path, key, HttpMethod.CUSTOM, value, components)
        }
        return map
    }

    private fun parseOperationIfPresent(
        path: String,
        methodToken: String,
        method: HttpMethod,
        node: JsonNode?,
        components: Components?
    ): EndpointDefinition? {
        val obj = node?.asObject() ?: return null
        return parseOperation(path, methodToken, method, obj, components)
    }

    private fun parseOperation(
        path: String,
        methodToken: String,
        method: HttpMethod,
        node: JsonNode,
        components: Components?
    ): EndpointDefinition {
        val operationIdNode = node.get("operationId")
        val operationIdExplicit = operationIdNode != null && !operationIdNode.isNull
        val operationId = operationIdNode?.textValue() ?: defaultOperationId(methodToken, path)
        val requestBody = parseRequestBody(node.get("requestBody"), components)
        val responses = parseResponses(node.get("responses"), components)
        val requestBodyType = selectSchema(requestBody?.content, components)?.let { TypeMappers.mapType(it) }
        val securityResult = parseSecurityRequirements(node.get("security"))

        return EndpointDefinition(
            path = path,
            method = method,
            customMethod = if (method == HttpMethod.CUSTOM) methodToken else null,
            operationId = operationId,
            operationIdExplicit = operationIdExplicit,
            parameters = parseParameters(node.get("parameters"), components),
            requestBodyType = requestBodyType,
            requestBody = requestBody,
            responses = responses,
            summary = node.text("summary"),
            description = node.text("description"),
            externalDocs = parseExternalDocs(node.get("externalDocs")),
            tags = node.get("tags")?.asArray()?.mapNotNull { it.textValue() } ?: emptyList(),
            callbacks = parseCallbacks(node.get("callbacks"), components),
            deprecated = node.boolean("deprecated") ?: false,
            security = securityResult.requirements,
            securityExplicitEmpty = securityResult.explicitEmpty,
            servers = parseServers(node.get("servers")),
            extensions = parseExtensions(node)
        )
    }

    private fun parseCallbacks(node: JsonNode?, components: Components?): Map<String, Callback> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, callbackNode) ->
            name to parseCallbackObject(callbackNode, components)
        }
    }

    private fun parseCallbackObject(node: JsonNode, components: Components?): Callback {
        val obj = node.asObject() ?: return Callback.Inline()
        val ref = obj.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            val resolved = components?.let { resolveCallbackComponentRef(Callback.Reference(reference), it) }
            return when (resolved) {
                is Callback.Inline -> resolved.copy(reference = reference)
                else -> Callback.Reference(reference)
            }
        }

        val extensions = parseExtensions(obj)
        val expressions = obj.fields().asSequence()
            .filterNot { (key, _) -> key.startsWith("x-") }
            .associate { (expression, pathItemNode) ->
                expression to parsePathItem("/", pathItemNode, components)
            }

        return Callback.Inline(expressions = expressions, extensions = extensions)
    }

    private fun parseParameters(node: JsonNode?, components: Components?): List<EndpointParameter> {
        val array = node.asArray() ?: return emptyList()
        return array.map { paramNode -> parseParameter(paramNode, components) }
    }

    private fun parseParameter(node: JsonNode, components: Components?): EndpointParameter {
        val obj = node.asObject()
        val ref = obj?.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            val resolved = resolveParameterRef(ref, components)
            if (resolved != null) {
                return resolved.copy(
                    description = reference.description ?: resolved.description,
                    reference = reference
                )
            }
            val fallbackName = ReferenceResolver.resolveRefToType(ref)
            return EndpointParameter(
                name = fallbackName,
                type = "String",
                location = ParameterLocation.QUERY,
                description = reference.description,
                reference = reference
            )
        }

        val name = obj?.text("name") ?: "param"
        val location = parseParameterLocation(obj?.text("in"))
        val schema = obj?.get("schema")?.let { parseSchemaProperty(it) }
        val content = parseContentMap(obj?.get("content"), components)
        val typeFromSchema = schema ?: selectSchema(content, components)
        val type = typeFromSchema?.let { TypeMappers.mapType(it) } ?: "String"

        val requiredDefault = if (location == ParameterLocation.PATH) true else false
        val required = obj?.boolean("required") ?: requiredDefault

        return EndpointParameter(
            name = name,
            type = type,
            location = location,
            isRequired = required,
            schema = schema,
            content = content,
            description = obj?.text("description"),
            deprecated = obj?.boolean("deprecated") ?: false,
            allowEmptyValue = obj?.boolean("allowEmptyValue"),
            style = parseParameterStyle(obj?.text("style")),
            explode = obj?.boolean("explode"),
            allowReserved = obj?.boolean("allowReserved"),
            example = obj?.get("example")?.let { ExampleObject(value = nodeToValue(it)) },
            examples = parseExampleMap(obj?.get("examples"), components),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseRequestBody(node: JsonNode?, components: Components?): RequestBody? {
        val obj = node.asObject() ?: return null
        val ref = obj.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            val resolved = resolveRequestBodyRef(ref, components)
            return resolved?.copy(
                description = reference.description ?: resolved.description,
                reference = reference
            ) ?: RequestBody(
                description = reference.description,
                reference = reference
            )
        }
        val hasContent = obj.has("content")
        return RequestBody(
            description = obj.text("description"),
            content = parseContentMap(obj.get("content"), components),
            contentPresent = hasContent,
            required = obj.boolean("required") ?: false,
            extensions = parseExtensions(obj)
        )
    }

    private fun parseResponses(node: JsonNode?, components: Components?): Map<String, EndpointResponse> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (code, raw) ->
            code to parseResponse(code, raw, components)
        }
    }

    private fun parseResponse(code: String, node: JsonNode, components: Components?): EndpointResponse {
        val obj = node.asObject()
        val ref = obj?.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            val resolved = resolveResponseRef(ref, components)
            return resolved?.copy(
                statusCode = code,
                summary = reference.summary ?: resolved.summary,
                description = reference.description ?: resolved.description,
                reference = reference
            ) ?: EndpointResponse(
                statusCode = code,
                summary = reference.summary,
                description = reference.description ?: "ref:$ref",
                reference = reference
            )
        }

        val contentPresent = obj?.has("content") == true
        val content = parseContentMap(obj?.get("content"), components)
        val type = selectSchema(content, components)?.let { TypeMappers.mapType(it) }

        return EndpointResponse(
            statusCode = code,
            summary = obj?.text("summary"),
            description = obj?.text("description"),
            headers = parseHeaders(obj?.get("headers"), components),
            content = content,
            contentPresent = contentPresent,
            type = type,
            links = parseLinks(obj?.get("links"), components),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseHeaders(node: JsonNode?, components: Components?): Map<String, Header> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseHeader(raw, components)
        }
    }

    private fun parseHeader(node: JsonNode, components: Components?): Header {
        val obj = node.asObject()
        val ref = obj?.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            val resolved = resolveHeaderRef(ref, components)
            return resolved?.copy(
                description = reference.description ?: resolved.description,
                reference = reference
            ) ?: Header(
                type = "String",
                description = reference.description,
                reference = reference
            )
        }

        val schema = obj?.get("schema")?.let { parseSchemaProperty(it) }
        val content = parseContentMap(obj?.get("content"), components)
        val typeFromSchema = schema ?: selectSchema(content, components)
        val type = typeFromSchema?.let { TypeMappers.mapType(it) } ?: "String"

        return Header(
            type = type,
            schema = schema,
            content = content,
            description = obj?.text("description"),
            required = obj?.boolean("required") ?: false,
            deprecated = obj?.boolean("deprecated") ?: false,
            example = obj?.get("example")?.let { ExampleObject(value = nodeToValue(it)) },
            examples = parseExampleMap(obj?.get("examples"), components),
            style = parseParameterStyle(obj?.text("style")) ?: ParameterStyle.SIMPLE,
            explode = obj?.boolean("explode") ?: false,
            extensions = parseExtensions(obj)
        )
    }

    private fun parseLinks(node: JsonNode?, components: Components?): Map<String, Link> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseLink(raw, components)
        }
    }

    private fun parseLink(node: JsonNode, components: Components?): Link {
        val obj = node.asObject() ?: return Link()
        val ref = obj.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            val resolved = resolveLinkRef(ref, components)
            return resolved?.copy(
                ref = ref,
                reference = reference,
                description = reference.description ?: resolved.description
            ) ?: Link(
                ref = ref,
                reference = reference,
                description = obj.text("description")
            )
        }
        return Link(
            operationId = obj.text("operationId"),
            operationRef = obj.text("operationRef"),
            parameters = obj.get("parameters")?.asObject()?.fields()?.asSequence()?.associate { (k, v) ->
                k to nodeToValue(v)
            } ?: emptyMap(),
            requestBody = obj.get("requestBody")?.let { nodeToValue(it) },
            description = obj.text("description"),
            server = obj.get("server")?.let { parseServerObject(it) },
            extensions = parseExtensions(obj)
        )
    }

    private fun parseContentMap(
        node: JsonNode?,
        components: Components?
    ): Map<String, MediaTypeObject> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseMediaTypeObject(raw, components)
        }
    }

    private fun parseMediaTypeObject(node: JsonNode, components: Components?): MediaTypeObject {
        val obj = node.asObject() ?: return MediaTypeObject()
        val ref = obj.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            val resolved = resolveMediaTypeRef(ref, components)
            return resolved?.copy(ref = ref, reference = reference) ?: MediaTypeObject(ref = ref, reference = reference)
        }

        return MediaTypeObject(
            schema = obj.get("schema")?.let { parseSchemaProperty(it) },
            itemSchema = obj.get("itemSchema")?.let { parseSchemaProperty(it) },
            example = obj.get("example")?.let { ExampleObject(value = nodeToValue(it)) },
            examples = parseExampleMap(obj.get("examples"), components),
            encoding = parseEncodingMap(obj.get("encoding"), components),
            prefixEncoding = parseEncodingArray(obj.get("prefixEncoding"), components),
            itemEncoding = obj.get("itemEncoding")?.let { parseEncodingObject(it, components) },
            extensions = parseExtensions(obj)
        )
    }

    private fun parseEncodingMap(
        node: JsonNode?,
        components: Components?
    ): Map<String, domain.EncodingObject> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseEncodingObject(raw, components)
        }
    }

    private fun parseEncodingArray(
        node: JsonNode?,
        components: Components?
    ): List<domain.EncodingObject> {
        val array = node.asArray() ?: return emptyList()
        return array.map { parseEncodingObject(it, components) }
    }

    private fun parseEncodingObject(
        node: JsonNode,
        components: Components?
    ): domain.EncodingObject {
        val obj = node.asObject() ?: return domain.EncodingObject()
        return domain.EncodingObject(
            contentType = obj.text("contentType"),
            headers = parseHeaders(obj.get("headers"), components),
            style = parseParameterStyle(obj.text("style")),
            explode = obj.boolean("explode"),
            allowReserved = obj.boolean("allowReserved"),
            encoding = parseEncodingMap(obj.get("encoding"), components),
            prefixEncoding = parseEncodingArray(obj.get("prefixEncoding"), components),
            itemEncoding = obj.get("itemEncoding")?.let { parseEncodingObject(it, components) },
            extensions = parseExtensions(obj)
        )
    }

    private fun parseExampleMap(node: JsonNode?, components: Components?): Map<String, ExampleObject> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseExampleObject(raw, components)
        }
    }

    private fun parseExampleObject(node: JsonNode, components: Components?): ExampleObject {
        val obj = node.asObject() ?: return ExampleObject(value = nodeToValue(node))
        val ref = obj.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            val resolved = resolveExampleRef(ref, components)
            return ExampleObject(
                ref = ref,
                summary = reference.summary ?: resolved?.summary,
                description = reference.description ?: resolved?.description,
                dataValue = resolved?.dataValue,
                serializedValue = resolved?.serializedValue,
                externalValue = resolved?.externalValue,
                value = resolved?.value,
                extensions = resolved?.extensions ?: emptyMap()
            )
        }
        return ExampleObject(
            summary = obj.text("summary"),
            description = obj.text("description"),
            dataValue = obj.get("dataValue")?.let { nodeToValue(it) },
            serializedValue = obj.text("serializedValue"),
            externalValue = obj.text("externalValue"),
            value = obj.get("value")?.let { nodeToValue(it) },
            extensions = parseExtensions(obj)
        )
    }

    private fun parseComponents(node: JsonNode?): Components? {
        val obj = node.asObject() ?: return null
        val headers = parseComponentHeaders(obj.get("headers"))
        val partialComponents = Components(headers = headers)
        val components = Components(
            schemas = parseSchemas(obj.get("schemas")),
            responses = parseComponentResponses(obj.get("responses")),
            parameters = parseComponentParameters(obj.get("parameters")),
            requestBodies = parseComponentRequestBodies(obj.get("requestBodies")),
            headers = headers,
            securitySchemes = parseSecuritySchemes(obj.get("securitySchemes")),
            examples = parseExampleMap(obj.get("examples"), null),
            links = parseLinks(obj.get("links"), null),
            callbacks = parseCallbacks(obj.get("callbacks"), null),
            pathItems = parsePathItems(obj.get("pathItems")),
            mediaTypes = parseContentMap(obj.get("mediaTypes"), partialComponents),
            extensions = parseExtensions(obj)
        )
        return resolveComponentRefs(components)
    }

    private fun resolveComponentRefs(components: Components): Components {
        val resolvedParameters = components.parameters.mapValues { (_, param) ->
            resolveParameterComponentRef(param, components)
        }
        val resolvedResponses = components.responses.mapValues { (_, response) ->
            resolveResponseComponentRef(response, components)
        }
        val resolvedRequestBodies = components.requestBodies.mapValues { (_, body) ->
            resolveRequestBodyComponentRef(body, components)
        }
        val resolvedHeaders = components.headers.mapValues { (_, header) ->
            resolveHeaderComponentRef(header, components)
        }
        val resolvedLinks = components.links.mapValues { (_, link) ->
            resolveLinkComponentRef(link, components)
        }
        val resolvedExamples = components.examples.mapValues { (_, example) ->
            resolveExampleComponentRef(example, components)
        }
        val resolvedMediaTypes = components.mediaTypes.mapValues { (_, media) ->
            resolveMediaTypeComponentRef(media, components)
        }
        val resolvedSecuritySchemes = components.securitySchemes.mapValues { (_, scheme) ->
            resolveSecuritySchemeComponentRef(scheme, components)
        }
        val resolvedCallbacks = components.callbacks.mapValues { (_, callback) ->
            resolveCallbackComponentRef(callback, components)
        }

        return components.copy(
            parameters = resolvedParameters,
            responses = resolvedResponses,
            requestBodies = resolvedRequestBodies,
            headers = resolvedHeaders,
            links = resolvedLinks,
            examples = resolvedExamples,
            mediaTypes = resolvedMediaTypes,
            securitySchemes = resolvedSecuritySchemes,
            callbacks = resolvedCallbacks
        )
    }

    private fun resolveParameterComponentRef(
        param: EndpointParameter,
        components: Components,
        visited: MutableSet<String> = mutableSetOf(),
        contextBase: String? = currentSelfBase
    ): EndpointParameter {
        val ref = param.reference?.ref ?: return param
        val lookup = resolveComponentLookup(ref, "parameters", components, contextBase) ?: return param
        val visitKey = "parameters:${lookup.base ?: "local"}:${lookup.key}"
        if (!visited.add(visitKey)) return param
        val target = lookup.components.parameters[lookup.key] ?: return param
        val resolved = resolveParameterComponentRef(target, lookup.components, visited, lookup.base)
        val reference = param.reference ?: ReferenceObject(ref = ref)
        val mergedDescription = reference.description ?: param.description ?: resolved.description
        val mergedExtensions = mergeExtensions(resolved.extensions, param.extensions)
        return resolved.copy(
            description = mergedDescription,
            reference = reference,
            extensions = mergedExtensions
        )
    }

    private fun resolveResponseComponentRef(
        response: EndpointResponse,
        components: Components,
        visited: MutableSet<String> = mutableSetOf(),
        contextBase: String? = currentSelfBase
    ): EndpointResponse {
        val ref = response.reference?.ref ?: return response
        val lookup = resolveComponentLookup(ref, "responses", components, contextBase) ?: return response
        val visitKey = "responses:${lookup.base ?: "local"}:${lookup.key}"
        if (!visited.add(visitKey)) return response
        val target = lookup.components.responses[lookup.key] ?: return response
        val resolved = resolveResponseComponentRef(target, lookup.components, visited, lookup.base)
        val reference = response.reference ?: ReferenceObject(ref = ref)
        val mergedSummary = reference.summary ?: response.summary ?: resolved.summary
        val placeholderDescription = "ref:$ref"
        val responseDescription = response.description?.takeUnless { it == placeholderDescription }
        val mergedDescription = reference.description ?: responseDescription ?: resolved.description
        val mergedExtensions = mergeExtensions(resolved.extensions, response.extensions)
        return resolved.copy(
            statusCode = response.statusCode,
            summary = mergedSummary,
            description = mergedDescription,
            reference = reference,
            extensions = mergedExtensions
        )
    }

    private fun resolveRequestBodyComponentRef(
        body: RequestBody,
        components: Components,
        visited: MutableSet<String> = mutableSetOf(),
        contextBase: String? = currentSelfBase
    ): RequestBody {
        val ref = body.reference?.ref ?: return body
        val lookup = resolveComponentLookup(ref, "requestBodies", components, contextBase) ?: return body
        val visitKey = "requestBodies:${lookup.base ?: "local"}:${lookup.key}"
        if (!visited.add(visitKey)) return body
        val target = lookup.components.requestBodies[lookup.key] ?: return body
        val resolved = resolveRequestBodyComponentRef(target, lookup.components, visited, lookup.base)
        val reference = body.reference ?: ReferenceObject(ref = ref)
        val mergedDescription = reference.description ?: body.description ?: resolved.description
        val mergedExtensions = mergeExtensions(resolved.extensions, body.extensions)
        return resolved.copy(
            description = mergedDescription,
            reference = reference,
            extensions = mergedExtensions
        )
    }

    private fun resolveHeaderComponentRef(
        header: Header,
        components: Components,
        visited: MutableSet<String> = mutableSetOf(),
        contextBase: String? = currentSelfBase
    ): Header {
        val ref = header.reference?.ref ?: return header
        val lookup = resolveComponentLookup(ref, "headers", components, contextBase) ?: return header
        val visitKey = "headers:${lookup.base ?: "local"}:${lookup.key}"
        if (!visited.add(visitKey)) return header
        val target = lookup.components.headers[lookup.key] ?: return header
        val resolved = resolveHeaderComponentRef(target, lookup.components, visited, lookup.base)
        val reference = header.reference ?: ReferenceObject(ref = ref)
        val mergedDescription = reference.description ?: header.description ?: resolved.description
        val mergedExtensions = mergeExtensions(resolved.extensions, header.extensions)
        return resolved.copy(
            description = mergedDescription,
            reference = reference,
            extensions = mergedExtensions
        )
    }

    private fun resolveLinkComponentRef(
        link: Link,
        components: Components,
        visited: MutableSet<String> = mutableSetOf(),
        contextBase: String? = currentSelfBase
    ): Link {
        val ref = link.reference?.ref ?: link.ref ?: return link
        val lookup = resolveComponentLookup(ref, "links", components, contextBase) ?: return link
        val visitKey = "links:${lookup.base ?: "local"}:${lookup.key}"
        if (!visited.add(visitKey)) return link
        val target = lookup.components.links[lookup.key] ?: return link
        val resolved = resolveLinkComponentRef(target, lookup.components, visited, lookup.base)
        val reference = link.reference ?: ReferenceObject(ref = ref)
        val mergedDescription = reference.description ?: link.description ?: resolved.description
        val mergedExtensions = mergeExtensions(resolved.extensions, link.extensions)
        return resolved.copy(
            ref = ref,
            reference = reference,
            description = mergedDescription,
            extensions = mergedExtensions
        )
    }

    private fun resolveExampleComponentRef(
        example: ExampleObject,
        components: Components,
        visited: MutableSet<String> = mutableSetOf(),
        contextBase: String? = currentSelfBase
    ): ExampleObject {
        val ref = example.ref ?: return example
        val lookup = resolveComponentLookup(ref, "examples", components, contextBase) ?: return example
        val visitKey = "examples:${lookup.base ?: "local"}:${lookup.key}"
        if (!visited.add(visitKey)) return example
        val target = lookup.components.examples[lookup.key] ?: return example
        val resolved = resolveExampleComponentRef(target, lookup.components, visited, lookup.base)
        val mergedSummary = example.summary ?: resolved.summary
        val mergedDescription = example.description ?: resolved.description
        val mergedExtensions = mergeExtensions(resolved.extensions, example.extensions)
        return resolved.copy(
            ref = ref,
            summary = mergedSummary,
            description = mergedDescription,
            extensions = mergedExtensions
        )
    }

    private fun resolveMediaTypeComponentRef(
        media: MediaTypeObject,
        components: Components,
        visited: MutableSet<String> = mutableSetOf(),
        contextBase: String? = currentSelfBase
    ): MediaTypeObject {
        val ref = media.reference?.ref ?: media.ref ?: return media
        val lookup = resolveComponentLookup(ref, "mediaTypes", components, contextBase) ?: return media
        val visitKey = "mediaTypes:${lookup.base ?: "local"}:${lookup.key}"
        if (!visited.add(visitKey)) return media
        val target = lookup.components.mediaTypes[lookup.key] ?: return media
        val resolved = resolveMediaTypeComponentRef(target, lookup.components, visited, lookup.base)
        val reference = media.reference ?: ReferenceObject(ref = ref)
        val mergedExtensions = mergeExtensions(resolved.extensions, media.extensions)
        return resolved.copy(
            ref = ref,
            reference = reference,
            extensions = mergedExtensions
        )
    }

    private fun resolveCallbackComponentRef(
        callback: Callback,
        components: Components,
        visited: MutableSet<String> = mutableSetOf(),
        contextBase: String? = currentSelfBase
    ): Callback {
        return when (callback) {
            is Callback.Inline -> callback
            is Callback.Reference -> {
                val ref = callback.reference.ref
                val lookup = resolveComponentLookup(ref, "callbacks", components, contextBase) ?: return callback
                val visitKey = "callbacks:${lookup.base ?: "local"}:${lookup.key}"
                if (!visited.add(visitKey)) return callback
                val target = lookup.components.callbacks[lookup.key] ?: return callback
                val resolved = resolveCallbackComponentRef(target, lookup.components, visited, lookup.base)
                when (resolved) {
                    is Callback.Inline -> resolved.copy(reference = callback.reference)
                    is Callback.Reference -> callback
                }
            }
        }
    }

    private fun resolveSecuritySchemeComponentRef(
        scheme: SecurityScheme,
        components: Components,
        visited: MutableSet<String> = mutableSetOf(),
        contextBase: String? = currentSelfBase
    ): SecurityScheme {
        val ref = scheme.reference?.ref ?: return scheme
        val lookup = resolveComponentLookup(ref, "securitySchemes", components, contextBase) ?: return scheme
        val visitKey = "securitySchemes:${lookup.base ?: "local"}:${lookup.key}"
        if (!visited.add(visitKey)) return scheme
        val target = lookup.components.securitySchemes[lookup.key] ?: return scheme
        val resolved = resolveSecuritySchemeComponentRef(target, lookup.components, visited, lookup.base)
        val reference = scheme.reference ?: ReferenceObject(ref = ref)
        val mergedDescription = reference.description ?: scheme.description ?: resolved.description
        val mergedExtensions = mergeExtensions(resolved.extensions, scheme.extensions)
        return resolved.copy(
            description = mergedDescription,
            reference = reference,
            extensions = mergedExtensions
        )
    }

    private fun mergeExtensions(
        base: Map<String, Any?>,
        overrides: Map<String, Any?>
    ): Map<String, Any?> {
        if (base.isEmpty() && overrides.isEmpty()) return emptyMap()
        val merged = LinkedHashMap<String, Any?>()
        merged.putAll(base)
        merged.putAll(overrides)
        return merged
    }

    private fun parseSchemas(node: JsonNode?): Map<String, SchemaDefinition> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseSchemaDefinition(name, raw)
        }
    }

    private fun parseSchemaDefinition(name: String, node: JsonNode): SchemaDefinition {
        if (node.isBoolean) {
            return SchemaDefinition(
                name = name,
                booleanSchema = node.booleanValue(),
                type = "object",
                typeExplicit = false
            )
        }
        val obj = node.asObject()
        val typeExplicit = obj?.has("type") == true
        val rawTypes = parseTypes(obj)
        val legacyNullable = isLegacyNullable(obj)
        val resolvedType = resolveSchemaType(rawTypes, obj)
        val types = applyLegacyNullable(rawTypes, resolvedType, legacyNullable)
        val examples = parseSchemaExamples(obj?.get("examples"))
        val examplesList = parseSchemaExamplesList(obj?.get("examples"))
        val customKeywords = parseCustomSchemaKeywords(obj)

        return SchemaDefinition(
            name = name,
            ref = obj?.text("\$ref"),
            dynamicRef = obj?.text("\$dynamicRef"),
            booleanSchema = null,
            type = resolvedType,
            typeExplicit = typeExplicit,
            schemaId = obj?.text("\$id"),
            schemaDialect = obj?.text("\$schema"),
            anchor = obj?.text("\$anchor"),
            dynamicAnchor = obj?.text("\$dynamicAnchor"),
            comment = obj?.text("\$comment"),
            defs = parseSchemaProperties(obj?.get("\$defs")),
            types = types,
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
            items = obj?.get("items")?.let { parseSchemaProperty(it) },
            prefixItems = parseSchemaPropertyList(obj?.get("prefixItems")),
            contains = obj?.get("contains")?.let { parseSchemaProperty(it) },
            minContains = obj?.int("minContains"),
            maxContains = obj?.int("maxContains"),
            properties = parseSchemaProperties(obj?.get("properties")),
            patternProperties = parseSchemaProperties(obj?.get("patternProperties")),
            propertyNames = obj?.get("propertyNames")?.let { parseSchemaProperty(it) },
            additionalProperties = parseAdditionalProperties(obj?.get("additionalProperties")),
            required = obj?.get("required")?.asArray()?.mapNotNull { it.textValue() } ?: emptyList(),
            dependentRequired = parseDependentRequired(obj?.get("dependentRequired")),
            dependentSchemas = parseSchemaProperties(obj?.get("dependentSchemas")),
            enumValues = parseEnumValues(obj?.get("enum")),
            description = obj?.text("description"),
            title = obj?.text("title"),
            defaultValue = obj?.get("default")?.let { nodeToValue(it) },
            constValue = obj?.get("const")?.let { nodeToValue(it) },
            deprecated = obj?.boolean("deprecated") ?: false,
            readOnly = obj?.boolean("readOnly") ?: false,
            writeOnly = obj?.boolean("writeOnly") ?: false,
            externalDocs = parseExternalDocs(obj?.get("externalDocs")),
            discriminator = parseDiscriminator(obj?.get("discriminator")),
            xml = parseXml(obj?.get("xml")),
            unevaluatedProperties = obj?.get("unevaluatedProperties")?.let { parseSchemaProperty(it) },
            unevaluatedItems = obj?.get("unevaluatedItems")?.let { parseSchemaProperty(it) },
            contentSchema = obj?.get("contentSchema")?.let { parseSchemaProperty(it) },
            oneOf = parseSchemaRefs(obj?.get("oneOf")),
            oneOfSchemas = parseSchemaInlineList(obj?.get("oneOf")),
            anyOf = parseSchemaRefs(obj?.get("anyOf")),
            anyOfSchemas = parseSchemaInlineList(obj?.get("anyOf")),
            allOf = parseSchemaRefs(obj?.get("allOf")),
            allOfSchemas = parseSchemaInlineList(obj?.get("allOf")),
            not = obj?.get("not")?.let { parseSchemaProperty(it) },
            ifSchema = obj?.get("if")?.let { parseSchemaProperty(it) },
            thenSchema = obj?.get("then")?.let { parseSchemaProperty(it) },
            elseSchema = obj?.get("else")?.let { parseSchemaProperty(it) },
            example = obj?.get("example")?.let { nodeToValue(it) },
            examples = examples,
            examplesList = examplesList,
            customKeywords = customKeywords,
            extensions = parseExtensions(obj)
        )
    }

    private fun parseSchemaProperties(node: JsonNode?): Map<String, SchemaProperty> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseSchemaProperty(raw)
        }
    }

    private fun parseSchemaPropertyList(node: JsonNode?): List<SchemaProperty> {
        val array = node.asArray() ?: return emptyList()
        return array.map { parseSchemaProperty(it) }
    }

    private fun parseSchemaInlineList(node: JsonNode?): List<SchemaProperty> {
        val array = node.asArray() ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item.asObject()
            if (obj != null && obj.text("\$ref") != null) return@mapNotNull null
            parseSchemaProperty(item)
        }
    }

    private fun parseSchemaProperty(node: JsonNode): SchemaProperty {
        if (node.isBoolean) {
            return SchemaProperty(booleanSchema = node.booleanValue())
        }
        val obj = node.asObject()
        val ref = obj?.text("\$ref")
        val dynamicRef = obj?.text("\$dynamicRef")
        val customKeywords = parseCustomSchemaKeywords(obj)
        val rawTypes = parseTypes(obj)
        val legacyNullable = isLegacyNullable(obj)
        val inferredType = if (rawTypes.isEmpty() && legacyNullable) resolveSchemaType(rawTypes, obj) else null
        val types = applyLegacyNullable(rawTypes, inferredType, legacyNullable)

        return SchemaProperty(
            booleanSchema = null,
            ref = ref,
            dynamicRef = dynamicRef,
            types = types,
            schemaId = obj?.text("\$id"),
            schemaDialect = obj?.text("\$schema"),
            anchor = obj?.text("\$anchor"),
            dynamicAnchor = obj?.text("\$dynamicAnchor"),
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
            items = obj?.get("items")?.let { parseSchemaProperty(it) },
            prefixItems = parseSchemaPropertyList(obj?.get("prefixItems")),
            contains = obj?.get("contains")?.let { parseSchemaProperty(it) },
            minContains = obj?.int("minContains"),
            maxContains = obj?.int("maxContains"),
            properties = parseSchemaProperties(obj?.get("properties")),
            required = obj?.get("required")?.asArray()?.mapNotNull { it.textValue() } ?: emptyList(),
            additionalProperties = parseAdditionalProperties(obj?.get("additionalProperties")),
            defs = parseSchemaProperties(obj?.get("\$defs")),
            patternProperties = parseSchemaProperties(obj?.get("patternProperties")),
            propertyNames = obj?.get("propertyNames")?.let { parseSchemaProperty(it) },
            dependentRequired = parseDependentRequired(obj?.get("dependentRequired")),
            dependentSchemas = parseSchemaProperties(obj?.get("dependentSchemas")),
            description = obj?.text("description"),
            title = obj?.text("title"),
            defaultValue = obj?.get("default")?.let { nodeToValue(it) },
            constValue = obj?.get("const")?.let { nodeToValue(it) },
            deprecated = obj?.boolean("deprecated") ?: false,
            readOnly = obj?.boolean("readOnly") ?: false,
            writeOnly = obj?.boolean("writeOnly") ?: false,
            externalDocs = parseExternalDocs(obj?.get("externalDocs")),
            discriminator = parseDiscriminator(obj?.get("discriminator")),
            comment = obj?.text("\$comment"),
            enumValues = parseEnumValues(obj?.get("enum")),
            oneOf = parseSchemaPropertyList(obj?.get("oneOf")),
            anyOf = parseSchemaPropertyList(obj?.get("anyOf")),
            allOf = parseSchemaPropertyList(obj?.get("allOf")),
            not = obj?.get("not")?.let { parseSchemaProperty(it) },
            ifSchema = obj?.get("if")?.let { parseSchemaProperty(it) },
            thenSchema = obj?.get("then")?.let { parseSchemaProperty(it) },
            elseSchema = obj?.get("else")?.let { parseSchemaProperty(it) },
            example = obj?.get("example")?.let { nodeToValue(it) },
            examples = parseSchemaExamplesList(obj?.get("examples")),
            xml = parseXml(obj?.get("xml")),
            unevaluatedProperties = obj?.get("unevaluatedProperties")?.let { parseSchemaProperty(it) },
            unevaluatedItems = obj?.get("unevaluatedItems")?.let { parseSchemaProperty(it) },
            contentSchema = obj?.get("contentSchema")?.let { parseSchemaProperty(it) },
            customKeywords = customKeywords,
            extensions = parseExtensions(obj)
        )
    }

    private fun parseAdditionalProperties(node: JsonNode?): SchemaProperty? {
        return when {
            node == null -> null
            node.isBoolean -> SchemaProperty(booleanSchema = node.booleanValue())
            node.isObject -> parseSchemaProperty(node)
            else -> null
        }
    }

    private fun parseDependentRequired(node: JsonNode?): Map<String, List<String>> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (key, raw) ->
            val list = raw.asArray()?.mapNotNull { it.textValue() } ?: emptyList()
            key to list
        }
    }

    private fun parseEnumValues(node: JsonNode?): List<Any?>? {
        val array = node.asArray() ?: return null
        return array.map { nodeToValue(it) }
    }

    private fun parseSchemaExamples(node: JsonNode?): Map<String, Any?>? {
        if (node == null) return null
        if (node.isArray) {
            val map = LinkedHashMap<String, Any?>()
            node.forEachIndexed { index, item ->
                map["example${index + 1}"] = nodeToValue(item)
            }
            return map
        }
        if (node.isObject) {
            return node.fields().asSequence().associate { (k, v) -> k to nodeToValue(v) }
        }
        return null
    }

    private fun parseSchemaExamplesList(node: JsonNode?): List<Any?>? {
        if (node == null || !node.isArray) return null
        return node.map { nodeToValue(it) }
    }

    private fun parseCustomSchemaKeywords(obj: JsonNode?): Map<String, Any?> {
        val node = obj?.asObject() ?: return emptyMap()
        return node.fields().asSequence()
            .filter { (key, _) -> !key.startsWith("x-") && key !in schemaKnownKeys }
            .associate { (key, value) -> key to nodeToValue(value) }
    }

    private fun parseDiscriminator(node: JsonNode?): Discriminator? {
        val obj = node.asObject() ?: return null
        return Discriminator(
            propertyName = obj.text("propertyName") ?: "type",
            mapping = obj.get("mapping")?.asObject()?.fields()?.asSequence()?.associate { (k, v) ->
                k to (v.textValue() ?: v.toString())
            } ?: emptyMap(),
            defaultMapping = obj.text("defaultMapping"),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseXml(node: JsonNode?): Xml? {
        val obj = node.asObject() ?: return null
        return Xml(
            name = obj.text("name"),
            namespace = obj.text("namespace"),
            prefix = obj.text("prefix"),
            nodeType = obj.text("nodeType"),
            attribute = obj.boolean("attribute") ?: false,
            wrapped = obj.boolean("wrapped") ?: false,
            extensions = parseExtensions(obj)
        )
    }

    private fun parseSecuritySchemes(node: JsonNode?): Map<String, SecurityScheme> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseSecurityScheme(raw)
        }
    }

    private fun parseSecurityScheme(node: JsonNode): SecurityScheme {
        val obj = node.asObject() ?: return SecurityScheme(type = "apiKey")
        val ref = obj.text("\$ref")
        if (ref != null) {
            val reference = ReferenceObject(
                ref = ref,
                summary = obj.text("summary"),
                description = obj.text("description")
            )
            return SecurityScheme(
                reference = reference,
                description = reference.description
            )
        }
        return SecurityScheme(
            type = obj.text("type") ?: "apiKey",
            description = obj.text("description"),
            name = obj.text("name"),
            `in` = obj.text("in"),
            scheme = obj.text("scheme"),
            bearerFormat = obj.text("bearerFormat"),
            flows = parseOAuthFlows(obj.get("flows")),
            openIdConnectUrl = obj.text("openIdConnectUrl"),
            oauth2MetadataUrl = obj.text("oauth2MetadataUrl"),
            deprecated = obj.boolean("deprecated") ?: false,
            extensions = parseExtensions(obj)
        )
    }

    private fun parseOAuthFlows(node: JsonNode?): OAuthFlows? {
        val obj = node.asObject() ?: return null
        return OAuthFlows(
            implicit = parseOAuthFlow(obj.get("implicit")),
            password = parseOAuthFlow(obj.get("password")),
            clientCredentials = parseOAuthFlow(obj.get("clientCredentials")),
            authorizationCode = parseOAuthFlow(obj.get("authorizationCode")),
            deviceAuthorization = parseOAuthFlow(obj.get("deviceAuthorization")),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseOAuthFlow(node: JsonNode?): OAuthFlow? {
        val obj = node.asObject() ?: return null
        return OAuthFlow(
            authorizationUrl = obj.text("authorizationUrl"),
            tokenUrl = obj.text("tokenUrl"),
            refreshUrl = obj.text("refreshUrl"),
            scopes = obj.get("scopes")?.asObject()?.fields()?.asSequence()?.associate { (k, v) ->
                k to (v.textValue() ?: v.toString())
            } ?: emptyMap(),
            deviceAuthorizationUrl = obj.text("deviceAuthorizationUrl"),
            extensions = parseExtensions(obj)
        )
    }

    private data class SecurityParseResult(
        val requirements: List<SecurityRequirement>,
        val explicitEmpty: Boolean
    )

    private fun parseSecurityRequirements(node: JsonNode?): SecurityParseResult {
        val array = node.asArray() ?: return SecurityParseResult(emptyList(), false)
        if (array.isEmpty()) return SecurityParseResult(emptyList(), true)
        val requirements = array.mapNotNull { item ->
            val obj = item.asObject() ?: return@mapNotNull null
            obj.fields().asSequence().associate { (k, v) ->
                val scopes = v.asArray()?.mapNotNull { it.textValue() } ?: emptyList()
                k to scopes
            }
        }
        return SecurityParseResult(requirements, false)
    }

    private fun parseTags(node: JsonNode?): List<Tag> {
        val array = node.asArray() ?: return emptyList()
        return array.mapNotNull { tagNode ->
            val obj = tagNode.asObject() ?: return@mapNotNull null
            Tag(
                name = obj.text("name") ?: "tag",
                summary = obj.text("summary"),
                description = obj.text("description"),
                externalDocs = parseExternalDocs(obj.get("externalDocs")),
                parent = obj.text("parent"),
                kind = obj.text("kind"),
                extensions = parseExtensions(obj)
            )
        }
    }

    private fun parseExternalDocs(node: JsonNode?): ExternalDocumentation? {
        val obj = node.asObject() ?: return null
        val url = obj.text("url") ?: return null
        return ExternalDocumentation(
            description = obj.text("description"),
            url = url,
            extensions = parseExtensions(obj)
        )
    }

    private fun parsePathItems(node: JsonNode?): Map<String, PathItem> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parsePathItem(name, raw, null)
        }
    }

    private fun parseParameterLocation(value: String?): ParameterLocation {
        return when (value?.lowercase()) {
            "path" -> ParameterLocation.PATH
            "query" -> ParameterLocation.QUERY
            "querystring" -> ParameterLocation.QUERYSTRING
            "header" -> ParameterLocation.HEADER
            "cookie" -> ParameterLocation.COOKIE
            else -> ParameterLocation.QUERY
        }
    }

    private fun parseParameterStyle(value: String?): ParameterStyle? {
        return when (value) {
            null -> null
            "matrix", "MATRIX" -> ParameterStyle.MATRIX
            "label", "LABEL" -> ParameterStyle.LABEL
            "simple", "SIMPLE" -> ParameterStyle.SIMPLE
            "form", "FORM" -> ParameterStyle.FORM
            "cookie", "COOKIE" -> ParameterStyle.COOKIE
            "spaceDelimited", "space_delimited", "spacedelimited", "SPACEDELIMITED" -> ParameterStyle.SPACE_DELIMITED
            "pipeDelimited", "pipe_delimited", "pipedelimited", "PIPEDELIMITED" -> ParameterStyle.PIPE_DELIMITED
            "deepObject", "deep_object", "deepobject", "DEEPOBJECT" -> ParameterStyle.DEEP_OBJECT
            else -> null
        }
    }

    private fun defaultOperationId(methodToken: String, path: String): String {
        val raw = "${methodToken}_${path}"
        return raw.replace(Regex("[^a-zA-Z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifEmpty { methodToken.lowercase() }
    }

    private fun parseTypes(obj: JsonNode?): Set<String> {
        val node = obj?.get("type") ?: return emptySet()
        return when {
            node.isTextual -> setOf(node.asText())
            node.isArray -> node.mapNotNull { it.textValue() }.toSet()
            else -> emptySet()
        }
    }

    private fun isLegacyNullable(obj: JsonNode?): Boolean {
        return obj?.boolean("nullable") == true || obj?.boolean("x-nullable") == true
    }

    private fun applyLegacyNullable(
        rawTypes: Set<String>,
        fallbackType: String?,
        legacyNullable: Boolean
    ): Set<String> {
        if (!legacyNullable) return rawTypes
        val baseTypes = if (rawTypes.isNotEmpty()) {
            rawTypes
        } else {
            fallbackType?.let { setOf(it) } ?: emptySet()
        }
        if (baseTypes.contains("null")) return baseTypes
        return if (baseTypes.isEmpty()) setOf("null") else baseTypes + "null"
    }

    private fun resolveSchemaType(types: Set<String>, obj: JsonNode?): String {
        if (types.isNotEmpty()) {
            return types.firstOrNull { it != "null" } ?: types.first()
        }
        if (obj == null) return "object"

        inferEnumType(obj.get("enum"))?.let { return it }

        if (obj.has("items") || obj.has("prefixItems") || obj.has("contains") ||
            obj.has("minItems") || obj.has("maxItems") || obj.has("uniqueItems")
        ) {
            return "array"
        }

        if (obj.has("properties") || obj.has("additionalProperties") || obj.has("patternProperties") ||
            obj.has("propertyNames") || obj.has("dependentSchemas") || obj.has("required") ||
            obj.has("minProperties") || obj.has("maxProperties")
        ) {
            return "object"
        }

        if (obj.has("minimum") || obj.has("maximum") || obj.has("multipleOf") ||
            obj.has("exclusiveMinimum") || obj.has("exclusiveMaximum")
        ) {
            return "number"
        }

        if (obj.has("minLength") || obj.has("maxLength") || obj.has("pattern") ||
            obj.has("contentEncoding") || obj.has("contentMediaType") || obj.has("format")
        ) {
            return "string"
        }

        return "object"
    }

    private fun inferEnumType(node: JsonNode?): String? {
        val array = node?.asArray() ?: return null
        if (array.isEmpty()) return null
        val types = array.mapNotNull { item ->
            when {
                item.isTextual -> "string"
                item.isNumber -> "number"
                item.isBoolean -> "boolean"
                item.isNull -> "null"
                item.isArray -> "array"
                item.isObject -> "object"
                else -> null
            }
        }.toSet()
        if (types.isEmpty()) return null
        if (types.size == 1) return types.first()
        if (types.size == 2 && types.contains("null")) return types.first { it != "null" }
        return null
    }

    private fun parseSchemaRefs(node: JsonNode?): List<String> {
        val array = node.asArray() ?: return emptyList()
        return array.mapNotNull { item ->
            item.text("\$ref") ?: item.takeIf { it.isTextual }?.asText()
        }
    }

    private fun selectSchema(
        content: Map<String, MediaTypeObject>?,
        components: Components?
    ): SchemaProperty? {
        if (content == null || content.isEmpty()) return null
        val preferredEntry = selectPreferredMediaTypeEntry(content)
        val preferred = preferredEntry.value
        preferred.schema?.let { return it }
        preferred.itemSchema?.let { return wrapItemSchemaAsArray(it) }
        val ref = preferred.reference?.ref ?: preferred.ref
        if (ref != null) {
            val resolved = resolveMediaTypeRef(ref, components)
            if (resolved != null) {
                resolved.schema?.let { return it }
                resolved.itemSchema?.let { return wrapItemSchemaAsArray(it) }
            }
            return SchemaProperty(ref = ref)
        }
        return inferSchemaFromMediaType(preferredEntry.key)
    }

    private fun wrapItemSchemaAsArray(itemSchema: SchemaProperty): SchemaProperty {
        return SchemaProperty(types = setOf("array"), items = itemSchema)
    }

    private fun inferSchemaFromMediaType(mediaTypeKey: String): SchemaProperty? {
        if (!isConcreteMediaType(mediaTypeKey)) return null
        val normalized = normalizeMediaTypeKey(mediaTypeKey)
        return when {
            isJsonMediaTypeKey(normalized) -> SchemaProperty(booleanSchema = true)
            isTextMediaTypeKey(normalized) -> SchemaProperty(types = setOf("string"))
            else -> SchemaProperty(types = setOf("string"), contentMediaType = normalized)
        }
    }

    private fun normalizeMediaTypeKey(value: String): String {
        return value.substringBefore(";").trim().lowercase()
    }

    private fun isConcreteMediaType(value: String): Boolean {
        val main = value.trim().substringBefore(";").trim()
        val parts = main.split("/")
        if (parts.size != 2) return false
        val type = parts[0]
        val subtype = parts[1]
        if (type == "*") return false
        if (subtype.contains("*")) return false
        return true
    }

    private fun isJsonMediaTypeKey(value: String): Boolean {
        val normalized = normalizeMediaTypeKey(value)
        return normalized == "application/json" || normalized.endsWith("+json")
    }

    private fun isTextMediaTypeKey(value: String): Boolean {
        val normalized = normalizeMediaTypeKey(value)
        return normalized.startsWith("text/") ||
            normalized == "application/xml" ||
            normalized == "text/xml" ||
            normalized.endsWith("+xml") ||
            normalized == "application/x-www-form-urlencoded" ||
            normalized == "multipart/form-data"
    }

    private fun selectPreferredMediaTypeEntry(
        content: Map<String, MediaTypeObject>
    ): Map.Entry<String, MediaTypeObject> {
        if (content.isEmpty()) {
            throw IllegalArgumentException("content map is empty")
        }
        val entries = content.entries.toList()
        val scored = entries.map { entry ->
            entry to mediaTypeScore(entry.key)
        }
        val comparator = Comparator<Pair<Map.Entry<String, MediaTypeObject>, MediaTypeScore>> { a, b ->
            val left = a.second
            val right = b.second
            when {
                left.specificity != right.specificity -> left.specificity.compareTo(right.specificity)
                left.jsonPreference != right.jsonPreference -> left.jsonPreference.compareTo(right.jsonPreference)
                left.length != right.length -> left.length.compareTo(right.length)
                else -> right.key.compareTo(left.key)
            }
        }
        return scored.maxWithOrNull(comparator)?.first ?: entries.first()
    }

    private fun mediaTypeScore(raw: String): MediaTypeScore {
        val main = raw.trim().substringBefore(";").trim().lowercase()
        val parts = main.split("/")
        if (parts.size != 2) {
            return MediaTypeScore(specificity = -1, jsonPreference = 0, length = main.length, key = main)
        }
        val type = parts[0]
        val subtype = parts[1]
        val typeScore = if (type == "*") 0 else 1
        val subtypeScore = when {
            subtype == "*" -> 0
            subtype.startsWith("*+") -> 1
            else -> 2
        }
        val specificity = typeScore * 10 + subtypeScore
        val jsonPreference = if (subtype == "json" || subtype.endsWith("+json")) 1 else 0
        return MediaTypeScore(
            specificity = specificity,
            jsonPreference = jsonPreference,
            length = main.length,
            key = main
        )
    }

    private data class MediaTypeScore(
        val specificity: Int,
        val jsonPreference: Int,
        val length: Int,
        val key: String
    )

    private data class ComponentRef(val base: String?, val key: String)

    private data class ComponentLookup(
        val key: String,
        val components: Components,
        val base: String?
    )

    private fun resolveParameterRef(ref: String, components: Components?): EndpointParameter? {
        val lookup = resolveComponentLookup(ref, "parameters", components) ?: return null
        return lookup.components.parameters[lookup.key]
    }

    private fun resolveMediaTypeRef(ref: String, components: Components?): MediaTypeObject? {
        val lookup = resolveComponentLookup(ref, "mediaTypes", components) ?: return null
        return lookup.components.mediaTypes[lookup.key]
    }

    private fun resolveResponseRef(ref: String, components: Components?): EndpointResponse? {
        val lookup = resolveComponentLookup(ref, "responses", components) ?: return null
        return lookup.components.responses[lookup.key]
    }

    private fun resolveRequestBodyRef(ref: String, components: Components?): RequestBody? {
        val lookup = resolveComponentLookup(ref, "requestBodies", components) ?: return null
        return lookup.components.requestBodies[lookup.key]
    }

    private fun resolveHeaderRef(ref: String, components: Components?): Header? {
        val lookup = resolveComponentLookup(ref, "headers", components) ?: return null
        return lookup.components.headers[lookup.key]
    }

    private fun resolveLinkRef(ref: String, components: Components?): Link? {
        val lookup = resolveComponentLookup(ref, "links", components) ?: return null
        return lookup.components.links[lookup.key]
    }

    private fun resolveExampleRef(ref: String, components: Components?): ExampleObject? {
        val lookup = resolveComponentLookup(ref, "examples", components) ?: return null
        return lookup.components.examples[lookup.key]
    }

    private fun resolveComponentLookup(
        ref: String,
        component: String,
        localComponents: Components?,
        contextBase: String? = currentSelfBase
    ): ComponentLookup? {
        val componentRef = extractComponentRef(ref, component, contextBase) ?: return null
        val baseMatches = componentRef.base != null && isSelfBaseMatch(componentRef.base, contextBase)
        val resolvedComponents = when {
            componentRef.base == null -> localComponents
            baseMatches && localComponents != null -> localComponents
            else -> currentRegistry?.resolveOpenApi(componentRef.base)?.components
        } ?: return null
        val effectiveBase = componentRef.base ?: normalizeSelfBase(contextBase) ?: contextBase
        return ComponentLookup(componentRef.key, resolvedComponents, effectiveBase)
    }

    private fun extractComponentRef(
        ref: String,
        component: String,
        contextBase: String?
    ): ComponentRef? {
        val marker = "#/components/$component/"
        val index = ref.indexOf(marker)
        if (index < 0) return null
        val refBase = ref.substring(0, index)
        val raw = ref.substring(index + marker.length)
        if (raw.isBlank() || raw.contains("/")) return null
        val key = decodeJsonPointer(raw)
        val base = resolveReferenceBase(refBase, contextBase)
        return ComponentRef(base = base, key = key)
    }

    private fun resolveReferenceBase(refBase: String, contextBase: String?): String? {
        val normalized = normalizeSelfBase(refBase)
        if (normalized.isNullOrBlank()) {
            return normalizeSelfBase(contextBase) ?: contextBase
        }
        val resolved = contextBase?.let { resolveAgainstBase(it, normalized) } ?: normalized
        return normalizeSelfBase(resolved) ?: resolved
    }

    private fun resolveAgainstBase(base: String, ref: String): String {
        return try {
            URI(base).resolve(ref).toString()
        } catch (_: Exception) {
            ref
        }
    }

    private fun decodeJsonPointer(value: String): String {
        val unescaped = value.replace("~1", "/").replace("~0", "~")
        return percentDecode(unescaped)
    }

    private fun percentDecode(value: String): String {
        if (!value.contains("%")) return value
        val bytes = ByteArray(value.length)
        var byteCount = 0
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '%' && i + 2 < value.length) {
                val hi = hexToInt(value[i + 1])
                val lo = hexToInt(value[i + 2])
                if (hi >= 0 && lo >= 0) {
                    bytes[byteCount++] = ((hi shl 4) + lo).toByte()
                    i += 3
                    continue
                }
            }
            bytes[byteCount++] = ch.code.toByte()
            i += 1
        }
        return bytes.copyOf(byteCount).toString(Charsets.UTF_8)
    }

    private fun hexToInt(ch: Char): Int {
        return when (ch) {
            in '0'..'9' -> ch.code - '0'.code
            in 'a'..'f' -> ch.code - 'a'.code + 10
            in 'A'..'F' -> ch.code - 'A'.code + 10
            else -> -1
        }
    }

    private fun parseServerObject(node: JsonNode): Server? {
        val obj = node.asObject() ?: return null
        return Server(
            url = obj.text("url") ?: "/",
            description = obj.text("description"),
            variables = parseServerVariables(obj.get("variables")),
            name = obj.text("name"),
            extensions = parseExtensions(obj)
        )
    }

    private fun parseComponentResponses(node: JsonNode?): Map<String, EndpointResponse> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseResponse(name, raw, null)
        }
    }

    private fun parseComponentParameters(node: JsonNode?): Map<String, EndpointParameter> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            val param = parseParameter(raw, null)
            val nameValue = if (param.name == "param") name else param.name
            name to param.copy(name = nameValue)
        }
    }

    private fun parseComponentRequestBodies(node: JsonNode?): Map<String, RequestBody> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to (parseRequestBody(raw, null) ?: RequestBody())
        }
    }

    private fun parseComponentHeaders(node: JsonNode?): Map<String, Header> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence().associate { (name, raw) ->
            name to parseHeader(raw, null)
        }
    }

    private fun parseExtensions(node: JsonNode?): Map<String, Any?> {
        val obj = node.asObject() ?: return emptyMap()
        return obj.fields().asSequence()
            .filter { (key, _) -> key.startsWith("x-") }
            .associate { (key, value) -> key to nodeToValue(value) }
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

}

private fun JsonNode?.asObject(): JsonNode? = if (this != null && this.isObject) this else null

private fun JsonNode?.asArray(): List<JsonNode>? = if (this != null && this.isArray) this.toList() else null

private fun JsonNode.text(field: String): String? = this.get(field)?.takeIf { !it.isNull }?.asText()

private fun JsonNode.boolean(field: String): Boolean? = this.get(field)?.takeIf { it.isBoolean }?.booleanValue()

private fun JsonNode.int(field: String): Int? = this.get(field)?.takeIf { it.isNumber }?.intValue()

private fun JsonNode.double(field: String): Double? = this.get(field)?.takeIf { it.isNumber }?.doubleValue()
