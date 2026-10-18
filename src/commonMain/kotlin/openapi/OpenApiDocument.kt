/** Sealed hierarchy representing parsed OpenAPI, Arazzo 1.1.0, or JSON Schema 2020-12 documents. */
package openapi

import domain.ArazzoDefinition
import domain.OpenApiDefinition
import domain.SchemaProperty

/**
 * Represents a parsed API or Workflow Description document, which can be an OpenAPI Object, an
 * Arazzo 1.1.0 Specification Object, or a standalone Schema Object (JSON Schema 2020-12).
 */
sealed interface OpenApiDocument {
  /**
   * An OpenAPI Description document rooted at an OpenAPI Object.
   *
   * @property definition The parsed [OpenApiDefinition].
   */
  data class OpenApi(val definition: OpenApiDefinition) : OpenApiDocument

  /**
   * A standalone Schema Object document.
   *
   * @property schema The parsed [SchemaProperty].
   */
  data class Schema(val schema: SchemaProperty) : OpenApiDocument

  /**
   * An Arazzo 1.1.0 Workflow Specification document.
   *
   * @property definition The parsed [ArazzoDefinition].
   */
  data class Arazzo(val definition: ArazzoDefinition) : OpenApiDocument
}
