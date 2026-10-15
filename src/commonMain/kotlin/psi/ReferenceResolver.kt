/** Resolves OpenAPI Reference Objects (`$ref`) into valid, sanitized Kotlin type identifiers. */
package psi

import openapi.percentDecode
import openapi.unescapeJsonPointer

/**
 * Logic for resolving OpenAPI Reference Objects ($ref) into distinct Kotlin type names. Handles
 * internal constraints, file paths, and fragment identifiers.
 */
object ReferenceResolver {

  /**
   * Extracts a usable Kotlin Type Name from a URI Reference.
   *
   * Examples:
   * - `#/components/schemas/User` -> `User`
   * - `#/definitions/Pet` -> `Pet`
   * - `User` -> `User`
   * - `./models/Address.json` -> `Address`
   * - `https://example.com/api/contracts/Order.yaml#/definitions/Detail` -> `Detail`
   *
   * @param ref The raw OpenAPI `$ref` string.
   * @return The sanitized Kotlin type identifier.
   */
  fun resolveRefToType(ref: String): String {
    val raw = resolveRefToTypeRaw(ref)
    return sanitizeTypeName(raw)
  }

  private fun resolveRefToTypeRaw(ref: String): String {
    if (ref.contains("#")) {
      val fragment = ref.substringAfterLast("#")
      val decodedFragment = percentDecode(fragment)
      if (decodedFragment.startsWith("/")) {
        val tokens = decodedFragment.split("/").filter { it.isNotEmpty() }
        if (tokens.isNotEmpty()) {
          return unescapeJsonPointer(tokens.last())
        }
      } else if (decodedFragment.isNotEmpty()) {
        return unescapeJsonPointer(decodedFragment)
      }
    }

    val path = ref.substringBefore("#")
    if (path.isNotEmpty()) {
      val filename = percentDecode(path.substringAfterLast("/"))
      if (filename.contains(".")) {
        return filename.substringBeforeLast(".")
      }
      if (filename.isNotEmpty()) {
        return filename
      }
    }

    return ref.substringAfterLast("/")
  }

  /**
   * Sanitizes a string to be used as a valid Kotlin type name.
   *
   * @param raw The raw candidate name string.
   * @return A valid PascalCase Kotlin identifier capped at 50 characters.
   */
  fun sanitizeTypeName(raw: String): String {
    if (raw.isBlank()) return "Unknown"
    val parts = raw.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "Unknown"
    val joined = parts.joinToString("") { part -> capitalizeName(part) }
    val name = if (joined.first().isDigit()) "Type$joined" else joined
    if (name.length <= 50) return name
    val hash = name.hashCode().toUInt().toString(16).padStart(8, '0')
    return name.take(40) + "_" + hash
  }
}
