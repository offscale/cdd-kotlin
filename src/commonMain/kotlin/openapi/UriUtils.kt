/**
 * Multiplatform URI resolution, percent-decoding, and JSON Pointer unescaping utilities.
 *
 * Consolidates URI and JSON Pointer logic previously duplicated across `ReferenceResolver`,
 * `SchemaDynamicResolution`, `OpenApiPathFlattener`, `OpenApiDocumentRegistry`, and
 * `OpenApiValidator`.
 */
package openapi

/**
 * Checks if the given URI string is an absolute URI (RFC 3986 scheme followed by `:`).
 *
 * @param value The URI string to check.
 * @return `true` if [value] has a valid RFC 3986 scheme and no whitespace, `false` otherwise.
 */
fun isAbsoluteUri(value: String): Boolean {
  if (value.isBlank() || value.any { it.isWhitespace() }) return false
  val withoutFragment = value.substringBefore("#")
  if (withoutFragment.isBlank()) return false
  return withoutFragment.startsWith("http://") ||
      withoutFragment.startsWith("https://") ||
      withoutFragment.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))
}

/**
 * Resolves a relative URI [ref] against a [base] URI using RFC 3986 path normalization.
 *
 * @param base The base URI string.
 * @param ref The relative or absolute URI reference string.
 * @return The resolved URI string.
 */
fun resolveUri(base: String, ref: String): String {
  if (isAbsoluteUri(ref)) return ref
  if (base.isBlank()) return ref
  val baseNoFrag = base.substringBefore("#")
  if (ref.startsWith("#")) return baseNoFrag + ref
  if (ref.startsWith("/")) {
    val schemeIdx = baseNoFrag.indexOf("://")
    if (schemeIdx != -1) {
      val pathIdx = baseNoFrag.indexOf("/", schemeIdx + 3)
      if (pathIdx != -1) {
        return baseNoFrag.substring(0, pathIdx) + normalizeDotSegments(ref)
      }
      return baseNoFrag + normalizeDotSegments(ref)
    }
    return normalizeDotSegments(ref)
  }

  val schemeIdx = baseNoFrag.indexOf("://")
  val authorityEnd =
      if (schemeIdx != -1) {
        val slashAfterAuth = baseNoFrag.indexOf("/", schemeIdx + 3)
        if (slashAfterAuth != -1) slashAfterAuth else baseNoFrag.length
      } else {
        0
      }

  val authority = baseNoFrag.substring(0, authorityEnd)
  val basePath = baseNoFrag.substring(authorityEnd)
  val baseDir =
      if (basePath.isEmpty()) {
        "/"
      } else if (basePath.endsWith("/")) {
        basePath
      } else if (basePath.contains("/")) {
        basePath.substringBeforeLast("/") + "/"
      } else {
        ""
      }

  val refPath = ref.substringBefore("#")
  val refFrag = if (ref.contains("#")) "#" + ref.substringAfter("#") else ""
  val combinedPath = baseDir + refPath
  val normalizedPath =
      if (combinedPath.startsWith("/")) {
        normalizeDotSegments(combinedPath)
      } else {
        normalizeRelativeDotSegments(combinedPath)
      }
  return authority + normalizedPath + refFrag
}

/**
 * Normalizes `.` and `..` segments in an absolute path according to RFC 3986 Section 5.2.4.
 *
 * @param path The path to normalize.
 * @return The normalized path.
 */
private fun normalizeDotSegments(path: String): String {
  val segments = path.split("/")
  val output = ArrayDeque<String>()
  for (seg in segments) {
    when (seg) {
      "",
      "." -> {}
      ".." -> output.removeLastOrNull()
      else -> output.addLast(seg)
    }
  }
  val joined = output.joinToString("/")
  if (joined.isEmpty()) return "/"
  val suffix = if (path.endsWith("/")) "/" else ""
  return "/$joined$suffix"
}

/**
 * Normalizes `.` and `..` segments in a relative path while preserving leading `..` segments when
 * no parent directory is available.
 *
 * @param path The relative path to normalize.
 * @return The normalized relative path.
 */
private fun normalizeRelativeDotSegments(path: String): String {
  val segments = path.split("/")
  val output = ArrayDeque<String>()
  for (seg in segments) {
    when (seg) {
      "",
      "." -> {}
      ".." -> {
        if (output.isNotEmpty() and (output.lastOrNull() != "..")) {
          output.removeLast()
        } else {
          output.addLast("..")
        }
      }
      else -> output.addLast(seg)
    }
  }
  val joined = output.joinToString("/")
  if (joined.isEmpty()) return ""
  val suffix = if (path.endsWith("/")) "/" else ""
  return "$joined$suffix"
}

/**
 * Unescapes a single RFC 6901 JSON Pointer reference token (`~1` -> `/`, `~0` -> `~`).
 *
 * @param token The raw JSON Pointer token.
 * @return The unescaped token string.
 */
fun unescapeJsonPointer(token: String): String {
  return token.replace("~1", "/").replace("~0", "~")
}

/**
 * Decodes a single JSON Pointer token by applying both RFC 6901 unescaping and URI
 * percent-decoding.
 *
 * @param token The encoded JSON Pointer token.
 * @return The decoded token string.
 */
fun decodeJsonPointerToken(token: String): String {
  return percentDecode(unescapeJsonPointer(token))
}

/**
 * Decodes percent-encoded UTF-8 sequences (`%XX`) in a URI or fragment string.
 *
 * @param value The percent-encoded string.
 * @return The decoded UTF-8 string.
 */
fun percentDecode(value: String): String {
  if (!value.contains("%")) return value
  val bytes = ByteArray(value.length)
  var byteCount = 0
  var index = 0
  while (index < value.length) {
    val ch = value[index]
    if (ch == '%' && index + 2 < value.length) {
      val hi = hexToInt(value[index + 1])
      val lo = hexToInt(value[index + 2])
      if (hi >= 0 && lo >= 0) {
        bytes[byteCount++] = ((hi shl 4) + lo).toByte()
        index += 3
        continue
      }
    }
    bytes[byteCount++] = ch.code.toByte()
    index += 1
  }
  return bytes.copyOf(byteCount).decodeToString()
}

/**
 * Converts a single hexadecimal character (`0-9`, `a-f`, `A-F`) into its integer value (`0..15`),
 * or `-1` if the character is not a valid hex digit.
 *
 * @param ch The character to convert.
 * @return The integer value (`0..15`) or `-1`.
 */
fun hexToInt(ch: Char): Int {
  return when (ch) {
    in '0'..'9' -> ch.code - '0'.code
    in 'a'..'f' -> ch.code - 'a'.code + 10
    in 'A'..'F' -> ch.code - 'A'.code + 10
    else -> -1
  }
}

/**
 * Parsed representation of a URI for syntax and scheme validation.
 *
 * @property scheme The URI scheme (e.g., `"http"`, `"https"`), or `null` if relative.
 * @property isAbsolute `true` if the URI defines a valid scheme and scheme-specific part.
 */
data class ParsedUri(
    /** The URI scheme (e.g., `"http"`, `"https"`), or `null` if relative. */
    val scheme: String?,
    /** `true` if the URI defines a valid scheme and scheme-specific part. */
    val isAbsolute: Boolean
)

/**
 * Validates and parses a URI string according to RFC 3986 / `java.net.URI` syntax rules without
 * relying on platform-specific URI classes.
 *
 * @param value The URI string to validate.
 * @return A [ParsedUri] if [value] is syntactically valid, or `null` if malformed.
 */
fun parseUriSyntax(value: String): ParsedUri? {
  val invalidChars = setOf('<', '>', '"', '{', '}', '|', '\\', '^', '`')
  for (i in value.indices) {
    val ch = value[i]
    if (ch.isWhitespace() || ch.code <= 0x20 || ch.code == 0x7F || ch in invalidChars) {
      return null
    }
    if (ch == '%') {
      if (i + 2 >= value.length || hexToInt(value[i + 1]) < 0 || hexToInt(value[i + 2]) < 0) {
        return null
      }
    }
  }
  if (value.count { it == '#' } > 1) return null

  val withoutFragment = value.substringBefore('#')
  val fragment = value.substringAfter('#', "")
  val beforeQuery = withoutFragment.substringBefore('?')
  val colonIdx = beforeQuery.indexOf(':')
  val slashIdx = beforeQuery.indexOf('/')

  return if (colonIdx != -1 && (slashIdx == -1 || colonIdx < slashIdx)) {
    val scheme = beforeQuery.substring(0, colonIdx)
    if (!scheme.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*$"))) return null
    val ssp = withoutFragment.substring(colonIdx + 1)
    if (ssp.isEmpty()) return null
    if (!hasValidUriBrackets(ssp, fragment)) return null
    ParsedUri(scheme = scheme, isAbsolute = true)
  } else {
    if (!hasValidUriBrackets(withoutFragment, fragment)) return null
    ParsedUri(scheme = null, isAbsolute = false)
  }
}

/**
 * Checks that square brackets `[` and `]` only appear as a valid IPv6 host literal inside an
 * authority component (`//...`).
 *
 * @param sspOrRelative The scheme-specific part or relative URI without fragment.
 * @param fragment The fragment component without leading `#`.
 * @return `true` if bracket placement is valid, `false` otherwise.
 */
private fun hasValidUriBrackets(sspOrRelative: String, fragment: String): Boolean {
  if (fragment.contains('[') || fragment.contains(']')) return false
  val beforeQuery = sspOrRelative.substringBefore('?')
  val query = sspOrRelative.substringAfter('?', "")
  if (query.contains('[') || query.contains(']')) return false
  if (!beforeQuery.startsWith("//")) {
    return !beforeQuery.contains('[') && !beforeQuery.contains(']')
  }
  val afterSlashes = beforeQuery.substring(2)
  val authority = afterSlashes.substringBefore('/')
  val pathPart = afterSlashes.substringAfter('/', "")
  if (pathPart.contains('[') || pathPart.contains(']')) return false
  if (!authority.contains('[') && !authority.contains(']')) return true
  val userInfo = if (authority.contains('@')) authority.substringBefore('@') else ""
  if (userInfo.contains('[') || userInfo.contains(']')) return false
  val hostPort = authority.substringAfter('@')
  if (!hostPort.startsWith('[')) return false
  val closeIdx = hostPort.indexOf(']')
  if (closeIdx <= 1 || closeIdx != hostPort.lastIndexOf(']') || hostPort.lastIndexOf('[') != 0) {
    return false
  }
  val inside = hostPort.substring(1, closeIdx)
  return inside.contains(':')
}
