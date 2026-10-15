/**
 * Generates server URL variable interpolation, server selection, and `defaultBaseUrl` companion
 * code for Ktor network clients.
 */
package psi.network

import domain.Server
import domain.ServerVariable
import psi.capitalizeName
import psi.decapitalizeName

/**
 * Generated server companion block and metadata.
 *
 * @property hasVariables Whether any server declares URL template variables.
 * @property companionSnippet Generated Kotlin code for `ServerSpec`, `SERVERS`, `ServerVariables`,
 *   `resolveServerUrl`, `selectServer`, and `defaultBaseUrl`.
 */
data class ServerSupport(val hasVariables: Boolean, val companionSnippet: String)

/**
 * Generates server variable interpolation and index/name resolution helpers for OpenAPI [Server]
 * definitions.
 */
object ServerUrlResolverGenerator {
  /**
   * Builds [ServerSupport] for [servers].
   *
   * @param servers Server definitions.
   * @return [ServerSupport] or `null`.
   */
  fun buildServerSupport(servers: List<Server>): ServerSupport? {
    if (servers.isEmpty()) return null
    val hasVariables = servers.any { !it.variables.isNullOrEmpty() }
    val hasNames = servers.any { !it.name.isNullOrBlank() }
    val needsSelection = servers.size > 1 || hasVariables || hasNames
    if (!needsSelection) return null

    val serverSpecBlock = buildServerSpecBlock(servers)
    val variablesBlock = if (hasVariables) buildServerVariablesBlock(servers) else ""
    val helpersBlock = buildServerHelperFunctions(hasVariables)

    val snippet =
        listOf(serverSpecBlock, variablesBlock, helpersBlock)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

    return ServerSupport(hasVariables = hasVariables, companionSnippet = snippet)
  }

  private fun buildServerSpecBlock(servers: List<Server>): String {
    val entries =
        servers.map { server ->
          val args = mutableListOf("url = \"${escapeKotlinString(server.url)}\"")
          server.name?.let { args.add("name = \"${escapeKotlinString(it)}\"") }
          val variables = server.variables.orEmpty()
          if (variables.isNotEmpty()) {
            val vars =
                variables.entries.joinToString(", ") { (name, variable) ->
                  "\"${escapeKotlinString(name)}\" to \"${escapeKotlinString(variable.default)}\""
                }
            args.add("variables = mapOf($vars)")
          }
          "ServerSpec(${args.joinToString(", ")})"
        }

    val listBody = entries.joinToString(",\n            ")
    return """
            data class ServerSpec(
                val url: String,
                val name: String? = null,
                val variables: Map<String, String> = emptyMap()
            )

            val SERVERS = listOf(
                $listBody
            )
        """
        .trimIndent()
  }

  private fun buildServerVariablesBlock(servers: List<Server>): String {
    val allVariables = LinkedHashMap<String, ServerVariable>()
    servers.forEach { server ->
      val vars = server.variables ?: emptyMap()
      vars.forEach { (rawName, variable) ->
        if (!allVariables.containsKey(rawName)) {
          allVariables[rawName] = variable
        }
      }
    }

    val usedNames = mutableSetOf<String>()
    val propertyLines = mutableListOf<String>()
    val mapEntries = mutableListOf<String>()
    val enumBlocks = mutableListOf<String>()

    allVariables.entries.forEach { (rawName, variable) ->
      val safeName = sanitizeVariableName(rawName)
      var uniqueName = safeName
      var suffix = 2
      while (!usedNames.add(uniqueName)) {
        uniqueName = "${safeName}_${suffix++}"
      }
      val enumValues = variable.enum.orEmpty()
      if (enumValues.isNotEmpty()) {
        val enumName = toPascalCase(uniqueName)
        val constants = buildEnumConstants(enumValues)
        val matchingConst = constants.firstOrNull { it.second == variable.default }
        val defaultConst =
            if (matchingConst != null) matchingConst.first else constants.first().first
        val enumBody =
            constants.joinToString(",\n                ") { (constName, rawValue) ->
              "$constName(\"${escapeKotlinString(rawValue)}\")"
            }
        enumBlocks.add(
            """
                        enum class $enumName(val value: String) {
                            $enumBody
                        }
                    """
                .trimIndent())
        propertyLines.add("val $uniqueName: $enumName = $enumName.$defaultConst")
        mapEntries.add("\"${escapeKotlinString(rawName)}\" to $uniqueName.value")
      } else {
        val defaultValue = escapeKotlinString(variable.default)
        propertyLines.add("val $uniqueName: String = \"$defaultValue\"")
        mapEntries.add("\"${escapeKotlinString(rawName)}\" to $uniqueName")
      }
    }

    val enumsBlock = enumBlocks.joinToString("\n\n")

    return """
            ${enumsBlock.takeIf { it.isNotBlank() } ?: ""}

            data class ServerVariables(
                ${propertyLines.joinToString(",\n                ")}
            ) {
                fun toMap(): Map<String, String> = mapOf(
                    ${mapEntries.joinToString(",\n                    ")}
                )
            }
        """
        .trimIndent()
  }

  private fun buildServerHelperFunctions(hasVariables: Boolean): String {
    val variablesParam =
        if (hasVariables) ", variables: ServerVariables = ServerVariables()" else ""
    val variablesMap = if (hasVariables) "variables.toMap()" else "emptyMap()"
    val serverSpecFallback =
        if (hasVariables) {
          "ServerSpec(url = \"/\", name = null, variables = emptyMap())"
        } else {
          "ServerSpec(url = \"/\", name = null)"
        }
    val defaultBaseUrlSignature =
        "fun defaultBaseUrl(serverIndex: Int = 0, serverName: String? = null$variablesParam): String"

    return """
            fun resolveServerUrl(template: String, variables: Map<String, String>): String {
                var resolved = template
                variables.forEach { (key, value) ->
                    resolved = resolved.replace("{${'$'}key}", value)
                }
                return resolved
            }

            fun selectServer(serverIndex: Int = 0, serverName: String? = null): ServerSpec {
                if (SERVERS.isEmpty()) return $serverSpecFallback
                val byName = serverName?.let { name -> SERVERS.firstOrNull { it.name == name } }
                return byName ?: SERVERS.getOrNull(serverIndex) ?: SERVERS.first()
            }

            $defaultBaseUrlSignature {
                if (SERVERS.isEmpty()) return "/"
                val server = selectServer(serverIndex, serverName)
                val vars = $variablesMap
                val merged = if (server.variables.isNotEmpty()) server.variables + vars else vars
                return if (merged.isEmpty()) server.url else resolveServerUrl(server.url, merged)
            }
        """
        .trimIndent()
  }

  private fun sanitizeVariableName(raw: String): String {
    val cleaned = raw.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_')
    val base = if (cleaned.isEmpty()) "var" else cleaned
    val normalized = if (base.first().isDigit()) "_$base" else base
    return decapitalizeName(normalized)
  }

  private fun toPascalCase(raw: String): String {
    val parts = raw.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotEmpty() }
    val joined = parts.joinToString("") { part -> capitalizeName(part.lowercase()) }
    return if (joined.first().isDigit()) "Var$joined" else joined
  }

  private fun sanitizeEnumConstant(value: String): String {
    val trimmed = value.trim()
    val cleaned = trimmed.replace(Regex("[^a-zA-Z0-9]+"), "_").trim('_')
    val base = if (cleaned.isBlank()) "VALUE" else cleaned
    val normalized = if (base.first().isDigit()) "VALUE_$base" else base
    return normalized.uppercase()
  }

  private fun buildEnumConstants(values: List<String>): List<Pair<String, String>> {
    val used = mutableSetOf<String>()
    return values.map { raw ->
      val base = sanitizeEnumConstant(raw)
      var name = base
      var suffix = 2
      while (!used.add(name)) {
        name = "${base}_${suffix++}"
      }
      name to raw
    }
  }

  /**
   * Escapes a Kotlin string literal.
   *
   * @param value Raw string.
   * @return Escaped string.
   */
  fun escapeKotlinString(value: String): String {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
  }

  /**
   * Resolves a server URL template using default variable values.
   *
   * @param server Server definition.
   * @return Resolved URL.
   */
  fun resolveServerUrlTemplate(server: Server): String {
    val variables = server.variables.orEmpty()
    return resolveServerUrlTemplate(server.url, variables.mapValues { it.value.default })
  }

  /**
   * Resolves a server URL template using [variables].
   *
   * @param template URL template.
   * @param variables Variable map.
   * @return Resolved URL.
   */
  fun resolveServerUrlTemplate(template: String, variables: Map<String, String>): String {
    if (variables.isEmpty()) return template
    var resolved = template
    variables.forEach { (key, value) -> resolved = resolved.replace("{${key}}", value) }
    return resolved
  }
}
