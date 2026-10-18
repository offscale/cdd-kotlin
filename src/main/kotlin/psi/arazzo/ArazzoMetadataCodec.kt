/**
 * Encodes and decodes Arazzo 1.1.0 metadata inside Kotlin KDoc comments for 100% lossless
 * round-tripping across SDK, CLI, and Server generators and PSI parsers.
 */
package psi.arazzo

import arazzo.ArazzoParser
import arazzo.ArazzoWriter
import domain.ArazzoDefinition
import domain.CddError
import domain.OpenApiArray
import domain.Workflow
import domain.toFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import openapi.asObject
import openapi.parseJsonElementResult

/**
 * Codec for embedding and extracting Arazzo 1.1.0 document and workflow metadata in KDoc tags
 * (`@arazzo-spec` and `@arazzo-workflow`).
 */
object ArazzoMetadataCodec {
  private val compactJson = Json { prettyPrint = false }
  private val writer = ArazzoWriter()
  private val parser = ArazzoParser()

  /**
   * Encodes an entire [ArazzoDefinition] as a single-line JSON string suitable for a `@arazzo-spec`
   * KDoc tag.
   *
   * @param definition The [ArazzoDefinition] to encode.
   * @return A [Result] containing the compact JSON string.
   */
  fun encodeDefinitionTagResult(definition: ArazzoDefinition): Result<String> {
    return Result.success(encodeDefinitionTag(definition))
  }

  /**
   * Encodes an entire [ArazzoDefinition] as a single-line JSON string suitable for a `@arazzo-spec`
   * KDoc tag.
   *
   * @param definition The [ArazzoDefinition] to encode.
   * @return The compact JSON string.
   */
  fun encodeDefinitionTag(definition: ArazzoDefinition): String {
    return writer.writeJson(definition, pretty = false)
  }

  /**
   * Decodes an [ArazzoDefinition] from a `@arazzo-spec` KDoc tag JSON payload.
   *
   * @param rawJson The compact JSON string extracted from KDoc.
   * @return A [Result] containing the decoded [ArazzoDefinition] or a [CddError].
   */
  fun decodeDefinitionTagResult(rawJson: String): Result<ArazzoDefinition> {
    return parser.parseStringResult(rawJson, ArazzoParser.Format.JSON)
  }

  /**
   * Encodes a single [Workflow] as a compact single-line JSON string suitable for a
   * `@arazzo-workflow` KDoc tag.
   *
   * @param workflow The [Workflow] to encode.
   * @return A [Result] containing the compact JSON string.
   */
  fun encodeWorkflowTagResult(workflow: Workflow): Result<String> {
    return Result.success(encodeWorkflowTag(workflow))
  }

  /**
   * Encodes a single [Workflow] as a compact single-line JSON string suitable for a
   * `@arazzo-workflow` KDoc tag.
   *
   * @param workflow The [Workflow] to encode.
   * @return The compact JSON string.
   */
  fun encodeWorkflowTag(workflow: Workflow): String {
    val dummyDef =
        ArazzoDefinition(
            arazzo = "1.1.0",
            info = domain.ArazzoInfo(title = "wf", version = "1.0.0"),
            workflows = listOf(workflow))
    val rootObj = writer.toOpenApiObject(dummyDef)
    val workflowsArray = (rootObj.properties["workflows"] as OpenApiArray).elements
    val wfElement = workflowsArray.first().toJsonElement()
    return compactJson.encodeToString(JsonElement.serializer(), wfElement)
  }

  /**
   * Decodes a single [Workflow] from a `@arazzo-workflow` KDoc tag JSON payload.
   *
   * @param rawJson The compact JSON string extracted from KDoc.
   * @return A [Result] containing the decoded [Workflow] or a [CddError].
   */
  fun decodeWorkflowTagResult(rawJson: String): Result<Workflow> {
    val node =
        parseJsonElementResult(rawJson).getOrElse {
          return Result.failure(it)
        }
    val obj =
        node.asObject()
            ?: return CddError.ArazzoParseError("Workflow tag must be a JSON object").toFailure()
    return parser.parseWorkflowResult(obj, 0)
  }

  /**
   * Extracts the JSON payload of a KDoc tag (e.g. `@arazzo-spec`) from a raw KDoc comment string.
   *
   * @param docText Raw KDoc comment text.
   * @param tagName Tag name including `@` (e.g. `"@arazzo-spec"`).
   * @return The extracted JSON string, or `null` if the tag is not present.
   */
  fun extractTagPayload(docText: String?, tagName: String): String? {
    if (docText.isNullOrBlank()) return null
    for (rawLine in docText.lines()) {
      val cleaned = rawLine.trim().removePrefix("/**").removeSuffix("*/").removePrefix("*").trim()
      if (cleaned.startsWith("$tagName ")) {
        return cleaned.removePrefix("$tagName ").trim()
      }
    }
    return null
  }
}
