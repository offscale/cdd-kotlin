/** Validator rule for OpenAPI [Server] and [domain.ServerVariable] definitions. */
package openapi.validator

import domain.OpenApiDefinition
import domain.Server
import openapi.OpenApiIssue
import openapi.OpenApiIssueSeverity

/**
 * Validates server lists, unique server names, URL syntax, query/fragment restrictions, and server
 * variable definitions.
 */
object ServerValidator : OpenApiValidationRule {
  override fun validate(
      definition: OpenApiDefinition,
      issues: MutableList<OpenApiIssue>,
      currentSelfBase: String?
  ) {
    validateServers(definition.servers, "$.servers", issues)
  }

  /**
   * Validates a list of [Server] objects at [basePath].
   *
   * @param servers List of servers to validate.
   * @param basePath JSONPath location.
   * @param issues Issue accumulator.
   */
  fun validateServers(servers: List<Server>, basePath: String, issues: MutableList<OpenApiIssue>) {
    if (servers.isEmpty()) return
    val duplicateNames =
        servers
            .mapNotNull { it.name }
            .filter { it.isNotBlank() }
            .groupBy { it }
            .filterValues { it.size > 1 }
    duplicateNames.forEach { (name, values) ->
      issues +=
          OpenApiIssue(
              severity = OpenApiIssueSeverity.ERROR,
              path = basePath,
              message =
                  "Server name '$name' must be unique within the server list (found ${values.size}).")
    }
    servers.forEachIndexed { index, server ->
      if (server.url.contains("?") or server.url.contains("#")) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath[$index].url",
                message = "Server url must not include query or fragment.")
      }
      ValidationCommon.validateUri(server.url, "$basePath[$index].url", issues)
      val variablesInUrl =
          ValidationCommon.SERVER_VARIABLE_REGEX.findAll(server.url)
              .map { it.groupValues[1] }
              .toList()
      val duplicates = variablesInUrl.groupBy { it }.filter { it.value.size > 1 }
      duplicates.forEach { (name, values) ->
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath[$index].url",
                message =
                    "Server variable '$name' must not appear more than once in the url (found ${values.size}).")
      }
      if (variablesInUrl.isNotEmpty() && server.variables == null) {
        issues +=
            OpenApiIssue(
                severity = OpenApiIssueSeverity.ERROR,
                path = "$basePath[$index].variables",
                message = "Server url uses variables but no variables map is defined.")
      }
      val definedVariables = server.variables?.keys.orEmpty()
      variablesInUrl.forEach { variable ->
        if (!definedVariables.contains(variable)) {
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.ERROR,
                  path = "$basePath[$index].variables",
                  message = "Server url variable '$variable' is not defined in variables.")
        }
      }
      if (definedVariables.isNotEmpty()) {
        val unused = definedVariables.filterNot { variablesInUrl.contains(it) }
        if (unused.isNotEmpty()) {
          val detail =
              if (variablesInUrl.isEmpty()) {
                "Server variables are defined but no variables are used in the url."
              } else {
                "Server variables are defined but not used in the url: ${unused.joinToString(", ")}."
              }
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.WARNING,
                  path = "$basePath[$index].variables",
                  message = detail)
        }
      }
      server.variables?.forEach { (name, variable) ->
        if (name.contains("{") or name.contains("}")) {
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.ERROR,
                  path = "$basePath[$index].variables.$name",
                  message = "Server variable name '$name' must not contain '{' or '}'.")
        }
        val enumValues = variable.enum
        if (enumValues != null && enumValues.isEmpty()) {
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.ERROR,
                  path = "$basePath[$index].variables.$name.enum",
                  message = "Server variable enum must not be empty.")
        }
        if (enumValues != null &&
            enumValues.isNotEmpty() &&
            !enumValues.contains(variable.default)) {
          issues +=
              OpenApiIssue(
                  severity = OpenApiIssueSeverity.ERROR,
                  path = "$basePath[$index].variables.$name.default",
                  message = "Server variable default must be one of the enum values.")
        }
      }
    }
  }
}
