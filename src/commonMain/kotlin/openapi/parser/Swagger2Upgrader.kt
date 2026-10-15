/** Swagger 2.0 to OpenAPI 3.x upgrader and compatibility parser. */
package openapi.parser

import domain.Components
import openapi.JsonNode
import openapi.asArray
import openapi.get
import openapi.has
import openapi.textValue

/**
 * Handles Swagger 2.0 (`swagger: "2.0"`) root fields (`definitions`, `parameters`, `responses`,
 * `securityDefinitions`, `host`, `basePath`, `schemes`, `consumes`, `produces`) and direct
 * parameter/header schema definitions.
 */
object Swagger2Upgrader {
  /**
   * Parses top-level Swagger 2.0 `definitions`, `responses`, `parameters`, and
   * `securityDefinitions` into an OpenAPI [Components] object.
   *
   * @param root The root Swagger 2.0 JSON node.
   * @param currentSelfBase Active `$self` base URI.
   * @return Upgraded [Components] or `null` if all sections are empty.
   */
  fun parseSwagger2Components(root: JsonNode, currentSelfBase: String? = null): Components? {
    val schemas = SchemaNodeParser.parseSchemas(root.get("definitions"))
    val responses = ComponentParser.parseComponentResponses(root.get("responses"), currentSelfBase)
    val parameters =
        ComponentParser.parseComponentParameters(root.get("parameters"), currentSelfBase)
    val securitySchemes = ComponentParser.parseSecuritySchemes(root.get("securityDefinitions"))

    if ((schemas.keys + responses.keys + parameters.keys + securitySchemes.keys).isEmpty()) {
      return null
    }

    val components =
        Components(
            schemas = schemas,
            responses = responses,
            parameters = parameters,
            securitySchemes = securitySchemes)
    return ComponentParser.resolveComponentRefs(components, currentSelfBase)
  }

  /**
   * Checks whether a parameter or header JSON object uses Swagger 2.0 direct `type` fields instead
   * of an OpenAPI 3.x nested `schema` object.
   *
   * @param obj The parameter or header JSON object node.
   * @return `true` if `type` is present directly without `schema`.
   */
  fun isSwagger2DirectSchema(obj: JsonNode): Boolean {
    return obj.has("type") && !obj.has("schema")
  }

  /**
   * Extracts a string array field (such as `schemes`, `consumes`, or `produces`) from [node].
   *
   * @param node The parent JSON node.
   * @param fieldName The array property name.
   * @return List of string values.
   */
  fun parseStringArray(node: JsonNode, fieldName: String): List<String> {
    return node.get(fieldName).asArray().orEmpty().mapNotNull { it.textValue() }
  }
}
