package psi

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
   */
  fun resolveRefToType(ref: String): String {
    val raw = resolveRefToTypeRaw(ref)
    return sanitizeTypeName(raw)
  }

  private fun resolveRefToTypeRaw(ref: String): String {
    // 1. Handle Fragment Identifier (#)
    if (ref.contains("#")) {
      val fragment = ref.substringAfterLast("#")
      val decodedFragment = percentDecode(fragment)
      // JSON Pointer (e.g. /components/schemas/Foo)
      if (decodedFragment.startsWith("/")) {
        val tokens = decodedFragment.split("/").filter { it.isNotEmpty() }
        if (tokens.isNotEmpty()) {
          return unescapeJsonPointer(tokens.last())
        }
      }
      // If fragment is just #Name
      if (decodedFragment.isNotEmpty() && !decodedFragment.startsWith("/")) {
        return unescapeJsonPointer(decodedFragment)
      }
    }

    // 2. Handle File Paths / URLs (No fragment, or fragment processing failed to yield name)
    // Take the file name without extension
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

    // Fallback: Return raw ref if simple string
    return ref.substringAfterLast("/")
  }

  /** Sanitizes a string to be used as a valid type name. */
  fun sanitizeTypeName(raw: String): String {
    if (raw.isBlank()) return "Unknown"
    val parts = raw.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "Unknown"
    val joined = parts.joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
    val name = if (joined.first().isDigit()) "Type$joined" else joined
    if (name.length <= 50) return name
    // Truncate to avoid "File name too long" compilation errors for extremely long OpenAPI path
    // names.
    // Use hashCode() formatted as hex for a pseudo-unique suffix.
    val hash = name.hashCode().toUInt().toString(16).padStart(8, '0')
    return name.take(40) + "_" + hash
  }

  private fun unescapeJsonPointer(token: String): String {
    return token.replace("~1", "/").replace("~0", "~")
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
    return bytes.copyOf(byteCount).decodeToString()
  }

  private fun hexToInt(ch: Char): Int {
    return when (ch) {
      in '0'..'9' -> ch.code - '0'.code
      in 'a'..'f' -> ch.code - 'a'.code + 10
      in 'A'..'F' -> ch.code - 'A'.code + 10
      else -> -1
    }
  }
}
