package openapi

import domain.OpenApiDefinition
import domain.SchemaProperty

/**
 * Represents a parsed OpenAPI Description document, which can be either
 * an OpenAPI Object or a standalone Schema Object (JSON Schema 2020-12).
 */
sealed interface OpenApiDocument {
    /** An OpenAPI Description document rooted at an OpenAPI Object. */
    data class OpenApi(val definition: OpenApiDefinition) : OpenApiDocument

    /** A standalone Schema Object document. */
    data class Schema(val schema: SchemaProperty) : OpenApiDocument
}
