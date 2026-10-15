/** JVM `java.io.File` extension helpers for [OpenApiWriter]. */
package openapi

import domain.OpenApiDefinition
import java.io.File
import writeToFile

/**
 * Writes the OpenAPI definition to a [File] on the JVM filesystem.
 *
 * @param definition The OpenAPI model.
 * @param file Target file.
 * @param format Output format (defaults to [OpenApiWriter.Format.AUTO], which uses file extension).
 * @return A [Result] indicating success or a [domain.CddError.FileIoError].
 */
fun OpenApiWriter.writeToFile(
    definition: OpenApiDefinition,
    file: File,
    format: OpenApiWriter.Format = OpenApiWriter.Format.AUTO
): Result<Unit> {
  val content = write(definition, format, file.name)
  return writeToFile(file.absolutePath, content)
}

/**
 * Writes an [OpenApiDocument] to a [File] on the JVM filesystem.
 *
 * @param document The document model.
 * @param file Target file.
 * @param format Output format (defaults to [OpenApiWriter.Format.AUTO], which uses file extension).
 * @return A [Result] indicating success or a [domain.CddError.FileIoError].
 */
fun OpenApiWriter.writeDocumentToFile(
    document: OpenApiDocument,
    file: File,
    format: OpenApiWriter.Format = OpenApiWriter.Format.AUTO
): Result<Unit> {
  val content = writeDocument(document, format, file.name)
  return writeToFile(file.absolutePath, content)
}
