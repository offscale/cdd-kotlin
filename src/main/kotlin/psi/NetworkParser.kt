/** Ktor client PSI and OpenAPI KDoc parser for reverse-engineering EndpointDefinitions. */
package psi

import domain.EndpointDefinition
import domain.PathItem
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import psi.network.AuthPsiParser
import psi.network.EndpointPsiParser

/**
 * Parses Kotlin source code to extract API Endpoint definitions implementation details. Analyzes
 * Ktor `client.request` calls to reverse-engineer the API Spec, including querystring parameters
 * set via `url.encodedQuery`, plus KDoc metadata such as callbacks, response summaries,
 * operation-level extensions, and interface-level OpenAPI metadata.
 *
 * @property endpoints Extracted endpoint definitions from Ktor request calls.
 * @property webhooks Webhooks captured from `@webhooks` KDoc metadata.
 * @property metadata Root-level OpenAPI metadata captured from interface-level KDoc.
 */
data class NetworkParseResult(
    val endpoints: List<EndpointDefinition>,
    val webhooks: Map<String, PathItem> = emptyMap(),
    val metadata: OpenApiMetadata = OpenApiMetadata()
)

/** Parses Ktor client PSI files into [NetworkParseResult] and [EndpointDefinition] lists. */
class NetworkParser {
  /**
   * Parses the provided Kotlin [sourceCode] and returns endpoints plus OpenAPI metadata.
   *
   * @param sourceCode Kotlin source code containing Ktor client methods and/or interface KDoc.
   * @return Parsed [NetworkParseResult].
   */
  fun parseWithMetadata(sourceCode: String): NetworkParseResult {
    val psiFactory = PsiInfrastructure.createPsiFactory()
    val file = psiFactory.createFile("Analysis.kt", sourceCode)
    val endpoints = parseEndpoints(file)
    val metadata = AuthPsiParser.extractRootMetadata(file)
    return NetworkParseResult(
        endpoints = endpoints, webhooks = metadata.webhooks, metadata = metadata)
  }

  /**
   * Parses the provided Kotlin [sourceCode] and returns endpoint definitions only.
   *
   * @param sourceCode Kotlin source code containing Ktor client methods.
   * @return List of extracted [EndpointDefinition]s.
   */
  fun parse(sourceCode: String): List<EndpointDefinition> {
    return parseWithMetadata(sourceCode).endpoints
  }

  private fun parseEndpoints(file: KtFile): List<EndpointDefinition> {
    val functions = file.collectDescendantsOfType<KtNamedFunction>()
    return functions.mapNotNull { EndpointPsiParser.parseFunction(it) }
  }
}
