/** Encodes, decodes, and merges OpenAPI [Components], `pathItems`, and `webhooks` KDoc metadata. */
package psi.metadata

import domain.Callback
import domain.Components
import domain.EndpointParameter
import domain.EndpointResponse
import domain.ExampleObject
import domain.Header
import domain.Info
import domain.Link
import domain.MediaTypeObject
import domain.OpenApiDefinition
import domain.OpenApiValue
import domain.OpenApiValues
import domain.PathItem
import domain.RequestBody
import domain.SchemaDefinition
import domain.SecurityScheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import openapi.OpenApiParser
import openapi.OpenApiWriter
import openapi.get
import openapi.isObject
import psi.OpenApiMetadata

/**
 * Encodes, decodes, and merges OpenAPI [Components] sections (`schemas`, `parameters`, `responses`,
 * `requestBodies`, `headers`, `pathItems`, `mediaTypes`, `securitySchemes`, `extensions`), root
 * `pathItems`, and `webhooks` metadata.
 */
object ComponentsMetadataCodec {
  private val openApiWriter = OpenApiWriter()

  /**
   * Merges [metadata] component maps onto an optional [base] [Components] instance.
   *
   * @param metadata The source [OpenApiMetadata].
   * @param base Optional base [Components] to merge into. Metadata keys override base keys.
   * @return The merged [Components], or `null` if both [base] and metadata components are empty.
   */
  fun toComponents(metadata: OpenApiMetadata, base: Components? = null): Components? {
    val hasMetadataComponents =
        metadata.securitySchemes.isNotEmpty() ||
            metadata.componentSchemas.isNotEmpty() ||
            metadata.componentExamples.isNotEmpty() ||
            metadata.componentLinks.isNotEmpty() ||
            metadata.componentCallbacks.isNotEmpty() ||
            metadata.componentParameters.isNotEmpty() ||
            metadata.componentResponses.isNotEmpty() ||
            metadata.componentRequestBodies.isNotEmpty() ||
            metadata.componentHeaders.isNotEmpty() ||
            metadata.componentPathItems.isNotEmpty() ||
            metadata.componentMediaTypes.isNotEmpty() ||
            metadata.componentsExtensions.isNotEmpty()

    if (base == null && !hasMetadataComponents) return null

    val seed = base ?: Components()
    val mergedSchemas = LinkedHashMap<String, SchemaDefinition>()
    mergedSchemas.putAll(seed.schemas)
    mergedSchemas.putAll(metadata.componentSchemas)

    val mergedSecuritySchemes = LinkedHashMap<String, SecurityScheme>()
    mergedSecuritySchemes.putAll(seed.securitySchemes)
    mergedSecuritySchemes.putAll(metadata.securitySchemes)

    val mergedExamples = LinkedHashMap<String, ExampleObject>()
    mergedExamples.putAll(seed.examples)
    mergedExamples.putAll(metadata.componentExamples)

    val mergedLinks = LinkedHashMap<String, Link>()
    mergedLinks.putAll(seed.links)
    mergedLinks.putAll(metadata.componentLinks)

    val mergedCallbacks = LinkedHashMap<String, Callback>()
    mergedCallbacks.putAll(seed.callbacks)
    mergedCallbacks.putAll(metadata.componentCallbacks)

    val mergedParameters = LinkedHashMap<String, EndpointParameter>()
    mergedParameters.putAll(seed.parameters)
    mergedParameters.putAll(metadata.componentParameters)

    val mergedResponses = LinkedHashMap<String, EndpointResponse>()
    mergedResponses.putAll(seed.responses)
    mergedResponses.putAll(metadata.componentResponses)

    val mergedRequestBodies = LinkedHashMap<String, RequestBody>()
    mergedRequestBodies.putAll(seed.requestBodies)
    mergedRequestBodies.putAll(metadata.componentRequestBodies)

    val mergedHeaders = LinkedHashMap<String, Header>()
    mergedHeaders.putAll(seed.headers)
    mergedHeaders.putAll(metadata.componentHeaders)

    val mergedPathItems = LinkedHashMap<String, PathItem>()
    mergedPathItems.putAll(seed.pathItems)
    mergedPathItems.putAll(metadata.componentPathItems)

    val mergedMediaTypes = LinkedHashMap<String, MediaTypeObject>()
    mergedMediaTypes.putAll(seed.mediaTypes)
    mergedMediaTypes.putAll(metadata.componentMediaTypes)

    val mergedExtensions = LinkedHashMap<String, OpenApiValue>()
    mergedExtensions.putAll(seed.extensions)
    mergedExtensions.putAll(metadata.componentsExtensions.filterKeys { it.startsWith("x-") })

    return seed.copy(
        schemas = mergedSchemas,
        securitySchemes = mergedSecuritySchemes,
        examples = mergedExamples,
        links = mergedLinks,
        callbacks = mergedCallbacks,
        parameters = mergedParameters,
        responses = mergedResponses,
        requestBodies = mergedRequestBodies,
        headers = mergedHeaders,
        pathItems = mergedPathItems,
        mediaTypes = mergedMediaTypes,
        extensions = mergedExtensions)
  }

  /**
   * Encodes `components.schemas` into a compact JSON string for `@componentSchemas`.
   *
   * @param schemas Map of schema definitions.
   * @return Compact JSON string.
   */
  fun renderComponentSchemas(schemas: Map<String, SchemaDefinition>): String {
    if (schemas.isEmpty()) return "{}"
    val tempDefinition =
        OpenApiDefinition(
            info = Info(title = "Component Schemas", version = "0.0.0"),
            components = Components(schemas = schemas))
    val json = openApiWriter.writeJson(tempDefinition)
    val node = Json.parseToJsonElement(json)
    val schemasNode = node.get("components")!!.get("schemas")!!
    return OpenApiValues.encodeJsonElementToString(schemasNode)
  }

  /**
   * Encodes `components.parameters` into a compact JSON string for `@componentParameters`.
   *
   * @param parameters Map of parameter definitions.
   * @return Compact JSON string.
   */
  fun renderComponentParameters(parameters: Map<String, EndpointParameter>): String {
    if (parameters.isEmpty()) return "{}"
    val tempDefinition =
        OpenApiDefinition(
            info = Info(title = "Component Parameters", version = "0.0.0"),
            components = Components(parameters = parameters))
    val json = openApiWriter.writeJson(tempDefinition)
    val node = Json.parseToJsonElement(json)
    val paramsNode = node.get("components")!!.get("parameters")!!
    return OpenApiValues.encodeJsonElementToString(paramsNode)
  }

  /**
   * Encodes `components.responses` into a compact JSON string for `@componentResponses`.
   *
   * @param responses Map of response definitions.
   * @return Compact JSON string.
   */
  fun renderComponentResponses(responses: Map<String, EndpointResponse>): String {
    if (responses.isEmpty()) return "{}"
    val tempDefinition =
        OpenApiDefinition(
            info = Info(title = "Component Responses", version = "0.0.0"),
            components = Components(responses = responses))
    val json = openApiWriter.writeJson(tempDefinition)
    val node = Json.parseToJsonElement(json)
    val responsesNode = node.get("components")!!.get("responses")!!
    return OpenApiValues.encodeJsonElementToString(responsesNode)
  }

  /**
   * Encodes `components.requestBodies` into a compact JSON string for `@componentRequestBodies`.
   *
   * @param bodies Map of request body definitions.
   * @return Compact JSON string.
   */
  fun renderComponentRequestBodies(bodies: Map<String, RequestBody>): String {
    if (bodies.isEmpty()) return "{}"
    val tempDefinition =
        OpenApiDefinition(
            info = Info(title = "Component Request Bodies", version = "0.0.0"),
            components = Components(requestBodies = bodies))
    val json = openApiWriter.writeJson(tempDefinition)
    val node = Json.parseToJsonElement(json)
    val bodiesNode = node.get("components")!!.get("requestBodies")!!
    return OpenApiValues.encodeJsonElementToString(bodiesNode)
  }

  /**
   * Encodes `components.headers` into a compact JSON string for `@componentHeaders`.
   *
   * @param headers Map of header definitions.
   * @return Compact JSON string.
   */
  fun renderComponentHeaders(headers: Map<String, Header>): String {
    if (headers.isEmpty()) return "{}"
    val tempDefinition =
        OpenApiDefinition(
            info = Info(title = "Component Headers", version = "0.0.0"),
            components = Components(headers = headers))
    val json = openApiWriter.writeJson(tempDefinition)
    val node = Json.parseToJsonElement(json)
    val headersNode = node.get("components")!!.get("headers")!!
    return OpenApiValues.encodeJsonElementToString(headersNode)
  }

  /**
   * Encodes `components.pathItems` into a compact JSON string for `@componentPathItems`.
   *
   * @param pathItems Map of path item definitions.
   * @return Compact JSON string.
   */
  fun renderComponentPathItems(pathItems: Map<String, PathItem>): String {
    if (pathItems.isEmpty()) return "{}"
    val tempDefinition =
        OpenApiDefinition(
            info = Info(title = "Component Path Items", version = "0.0.0"),
            components = Components(pathItems = pathItems))
    val json = openApiWriter.writeJson(tempDefinition)
    val node = Json.parseToJsonElement(json)
    val pathItemsNode = node.get("components")!!.get("pathItems")!!
    return OpenApiValues.encodeJsonElementToString(pathItemsNode)
  }

  /**
   * Encodes `components.mediaTypes` into a compact JSON string for `@componentMediaTypes`.
   *
   * @param mediaTypes Map of media type definitions.
   * @return Compact JSON string.
   */
  fun renderComponentMediaTypes(mediaTypes: Map<String, MediaTypeObject>): String {
    if (mediaTypes.isEmpty()) return "{}"
    val tempDefinition =
        OpenApiDefinition(
            info = Info(title = "Component Media Types", version = "0.0.0"),
            components = Components(mediaTypes = mediaTypes))
    val json = openApiWriter.writeJson(tempDefinition)
    val node = Json.parseToJsonElement(json)
    val mediaTypesNode = node.get("components")!!.get("mediaTypes")!!
    return OpenApiValues.encodeJsonElementToString(mediaTypesNode)
  }

  /**
   * Encodes root `pathItems` into a compact JSON string for `@pathItems`.
   *
   * @param pathItems Map of path items.
   * @return Compact JSON string.
   */
  fun renderPathItems(pathItems: Map<String, PathItem>): String {
    if (pathItems.isEmpty()) return "{}"
    val tempDefinition =
        OpenApiDefinition(info = Info(title = "PathItems", version = "0.0.0"), paths = pathItems)
    val json = openApiWriter.writeJson(tempDefinition)
    val node = Json.parseToJsonElement(json)
    val pathsNode = node.get("paths")!!
    return OpenApiValues.encodeJsonElementToString(pathsNode)
  }

  /**
   * Encodes root `webhooks` into a compact JSON string for `@webhooks`.
   *
   * @param webhooks Map of webhook path items.
   * @return Compact JSON string.
   */
  fun renderWebhooks(webhooks: Map<String, PathItem>): String {
    if (webhooks.isEmpty()) return "{}"
    val tempDefinition =
        OpenApiDefinition(info = Info(title = "Webhooks", version = "0.0.0"), webhooks = webhooks)
    val json = openApiWriter.writeJson(tempDefinition)
    val node = Json.parseToJsonElement(json)
    val webhooksNode = node.get("webhooks")!!
    return OpenApiValues.encodeJsonElementToString(webhooksNode)
  }

  /**
   * Parses a wrapped `components.<sectionKey>` KDoc tag from [lines].
   *
   * @param lines Normalized KDoc lines.
   * @param tag The KDoc tag prefix (e.g., `@componentSchemas`).
   * @param title Dummy document title for parsing.
   * @param sectionKey Key under `components`.
   * @param parser OpenAPI parser instance.
   * @return Parsed [Components] or `null`.
   */
  fun parseWrappedComponentSection(
      lines: List<String>,
      tag: String,
      title: String,
      sectionKey: String,
      parser: OpenApiParser = OpenApiParser()
  ): Components? {
    val line = lines.firstOrNull { it.startsWith(tag) } ?: return null
    val json = line.removePrefix(tag).trim()
    if (json.isEmpty()) return null
    val node = openapi.parseJsonElementOrNull(json) ?: return null
    if (!node.isObject) return null

    val root = buildJsonObject {
      put("openapi", "3.2.0")
      putJsonObject("info") {
        put("title", title)
        put("version", "0.0.0")
      }
      putJsonObject("components") { put(sectionKey, node) }
    }
    return parser
        .parseString(Json.encodeToString(JsonElement.serializer(), root), OpenApiParser.Format.JSON)
        .components
  }

  /**
   * Extracts root `pathItems` from `@pathItems` in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @param parser OpenAPI parser instance.
   * @return Map of path to [PathItem].
   */
  fun extractPathItemsLine(
      lines: List<String>,
      parser: OpenApiParser = OpenApiParser()
  ): Map<String, PathItem> {
    val line = lines.firstOrNull { it.startsWith("@pathItems") } ?: return emptyMap()
    val json = line.removePrefix("@pathItems").trim()
    if (json.isEmpty()) return emptyMap()
    val node = openapi.parseJsonElementOrNull(json) ?: return emptyMap()
    if (!node.isObject) return emptyMap()

    val wrapper = buildJsonObject {
      put("openapi", "3.2.0")
      putJsonObject("info") {
        put("title", "PathItems")
        put("version", "0.0.0")
      }
      put("paths", node)
    }

    return parser
        .parseString(
            Json.encodeToString(JsonElement.serializer(), wrapper), OpenApiParser.Format.JSON)
        .paths
  }

  /**
   * Extracts root `webhooks` from `@webhooks` in [lines].
   *
   * @param lines Normalized KDoc lines.
   * @param parser OpenAPI parser instance.
   * @return Map of webhook name to [PathItem].
   */
  fun extractWebhooksLine(
      lines: List<String>,
      parser: OpenApiParser = OpenApiParser()
  ): Map<String, PathItem> {
    val line = lines.firstOrNull { it.startsWith("@webhooks") } ?: return emptyMap()
    val json = line.removePrefix("@webhooks").trim()
    if (json.isEmpty()) return emptyMap()
    val node = openapi.parseJsonElementOrNull(json) ?: return emptyMap()
    if (!node.isObject) return emptyMap()

    val wrapper = buildJsonObject {
      put("openapi", "3.2.0")
      putJsonObject("info") {
        put("title", "Webhooks")
        put("version", "0.0.0")
      }
      put("webhooks", node)
    }

    return parser
        .parseString(
            Json.encodeToString(JsonElement.serializer(), wrapper), OpenApiParser.Format.JSON)
        .webhooks
  }

  /**
   * Extracts path-level metadata (excluding HTTP operations) from a `paths` map.
   *
   * @param paths Full paths map.
   * @return Filtered map containing only path-level metadata fields.
   */
  fun extractPathItemMetadata(paths: Map<String, PathItem>): Map<String, PathItem> {
    if (paths.isEmpty()) return emptyMap()
    val result = LinkedHashMap<String, PathItem>()
    paths.forEach { (path, item) ->
      val metadata =
          PathItem(
              ref = item.ref,
              summary = item.summary,
              description = item.description,
              parameters = item.parameters,
              servers = item.servers,
              extensions = item.extensions)
      if (hasPathMetadata(metadata)) {
        result[path] = metadata
      }
    }
    return result
  }

  private fun hasPathMetadata(item: PathItem): Boolean {
    return item.ref != null ||
        item.summary != null ||
        item.description != null ||
        item.parameters.isNotEmpty() ||
        item.servers.isNotEmpty() ||
        item.extensions.isNotEmpty()
  }
}
