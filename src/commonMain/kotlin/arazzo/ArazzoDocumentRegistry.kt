/**
 * Multi-document registry and linker for Arazzo 1.1.0 `sourceDescriptions` and reusable
 * `components`.
 *
 * Resolves `operationId`, `operationPath`, `workflowId`, and `$components.*` references across
 * linked OpenAPI 3.2.0 and Arazzo 1.1.0 specifications with full [kotlin.Result] percolation.
 */
package arazzo

import domain.ArazzoComponents
import domain.ArazzoDefinition
import domain.ArazzoParameter
import domain.CddError
import domain.EndpointDefinition
import domain.FailureActionObject
import domain.OpenApiDefinition
import domain.OpenApiPathFlattener
import domain.ReusableOr
import domain.SchemaProperty
import domain.SourceDescription
import domain.StepObject
import domain.SuccessActionObject
import domain.Workflow
import domain.toFailure
import openapi.OpenApiDocumentRegistry
import openapi.decodeJsonPointerToken
import openapi.resolveUri

/**
 * Resolved OpenAPI operation target for a workflow [StepObject].
 *
 * @property source The [SourceDescription] where the operation is defined.
 * @property endpoint The resolved [EndpointDefinition].
 */
data class ResolvedOperationTarget(val source: SourceDescription, val endpoint: EndpointDefinition)

/**
 * Resolved sub-workflow target for a workflow [StepObject].
 *
 * @property source Optional external [SourceDescription] if the workflow resides in an external
 *   Arazzo document.
 * @property workflow The resolved [Workflow].
 */
data class ResolvedWorkflowTarget(val source: SourceDescription?, val workflow: Workflow)

/**
 * Links an [ArazzoDefinition] with its referenced OpenAPI and Arazzo [SourceDescription]s and
 * dereferences [ReusableOr] components.
 *
 * @property openApiRegistry Backing [OpenApiDocumentRegistry] containing registered OpenAPI and
 *   Arazzo documents.
 */
class ArazzoDocumentRegistry(
    val openApiRegistry: OpenApiDocumentRegistry = OpenApiDocumentRegistry()
) {
  private val openApiBySourceName = LinkedHashMap<String, OpenApiDefinition>()
  private val arazzoBySourceName = LinkedHashMap<String, ArazzoDefinition>()

  /**
   * Registers an [OpenApiDefinition] under a [SourceDescription] name and optional URL.
   *
   * @param sourceName The `sourceDescription.name`.
   * @param definition The [OpenApiDefinition] to bind.
   * @param url Optional URI to also register in [openApiRegistry].
   */
  fun registerOpenApiSource(sourceName: String, definition: OpenApiDefinition, url: String?) {
    openApiBySourceName[sourceName] = definition
    if (url != null) {
      openApiRegistry.registerOpenApi(definition, url)
    }
  }

  /**
   * Registers an external [ArazzoDefinition] under a [SourceDescription] name and optional URL.
   *
   * @param sourceName The `sourceDescription.name`.
   * @param definition The external [ArazzoDefinition] to bind.
   * @param url Optional URI to also register in [openApiRegistry].
   */
  fun registerArazzoSource(sourceName: String, definition: ArazzoDefinition, url: String?) {
    arazzoBySourceName[sourceName] = definition
    if (url != null) {
      openApiRegistry.registerArazzo(definition, url)
    }
  }

  /**
   * Resolves the target OpenAPI [EndpointDefinition] for a [StepObject] specifying `operationId` or
   * `operationPath`.
   *
   * @param arazzo The enclosing [ArazzoDefinition].
   * @param step The [StepObject] to resolve.
   * @return A [Result] containing [ResolvedOperationTarget] or a [CddError.WorkflowExecutionError].
   */
  fun resolveStepOperationResult(
      arazzo: ArazzoDefinition,
      step: StepObject
  ): Result<ResolvedOperationTarget> {
    if (step.operationId != null) {
      return resolveOperationIdResult(arazzo, step.stepId, step.operationId)
    }
    if (step.operationPath != null) {
      return resolveOperationPathResult(arazzo, step.stepId, step.operationPath)
    }
    return CddError.WorkflowExecutionError(
            workflowId = "",
            stepId = step.stepId,
            reason = "Step '${step.stepId}' has neither operationId nor operationPath")
        .toFailure()
  }

  private fun resolveOperationIdResult(
      arazzo: ArazzoDefinition,
      stepId: String,
      operationIdExpr: String
  ): Result<ResolvedOperationTarget> {
    if (operationIdExpr.startsWith("\$sourceDescriptions.")) {
      val rest = operationIdExpr.removePrefix("\$sourceDescriptions.")
      val dotIdx = rest.indexOf('.')
      if (dotIdx <= 0) {
        return CddError.WorkflowExecutionError(
                "", stepId, "Malformed operationId expression '$operationIdExpr'")
            .toFailure()
      }
      val srcName = rest.substring(0, dotIdx)
      val opId = rest.substring(dotIdx + 1)
      val source =
          arazzo.sourceDescriptions.firstOrNull { it.name == srcName }
              ?: return CddError.WorkflowExecutionError(
                      "", stepId, "Unknown sourceDescription '$srcName' in '$operationIdExpr'")
                  .toFailure()
      val openApi =
          lookupOpenApiForSource(arazzo, source)
              ?: return CddError.WorkflowExecutionError(
                      "", stepId, "OpenAPI document for source '$srcName' is not registered")
                  .toFailure()
      val endpoints = OpenApiPathFlattener.flattenPaths(openApi.paths)
      val endpoint =
          endpoints.firstOrNull { it.operationId == opId }
              ?: return CddError.WorkflowExecutionError(
                      "", stepId, "operationId '$opId' not found in source '$srcName'")
                  .toFailure()
      return Result.success(ResolvedOperationTarget(source, endpoint))
    }
    for (source in arazzo.sourceDescriptions) {
      val openApi = lookupOpenApiForSource(arazzo, source) ?: continue
      val endpoints = OpenApiPathFlattener.flattenPaths(openApi.paths)
      val match = endpoints.firstOrNull { it.operationId == operationIdExpr }
      if (match != null) {
        return Result.success(ResolvedOperationTarget(source, match))
      }
    }
    return CddError.WorkflowExecutionError(
            "", stepId, "operationId '$operationIdExpr' not found in any registered source")
        .toFailure()
  }

  private fun resolveOperationPathResult(
      arazzo: ArazzoDefinition,
      stepId: String,
      operationPath: String
  ): Result<ResolvedOperationTarget> {
    val hashIdx = operationPath.indexOf('#')
    if (hashIdx < 0) {
      return CddError.WorkflowExecutionError(
              "", stepId, "operationPath '$operationPath' must contain '#/paths/...' fragment")
          .toFailure()
    }
    val beforeHash = operationPath.substring(0, hashIdx).trim()
    val pointer = operationPath.substring(hashIdx + 1)
    if (!pointer.startsWith("/paths/")) {
      return CddError.WorkflowExecutionError(
              "", stepId, "operationPath fragment '$pointer' must start with '/paths/'")
          .toFailure()
    }
    val afterPaths = pointer.removePrefix("/paths/")
    val slashIdx = afterPaths.indexOf('/')
    if (slashIdx <= 0) {
      return CddError.WorkflowExecutionError(
              "", stepId, "operationPath fragment '$pointer' missing HTTP method segment")
          .toFailure()
    }
    val rawPathToken = afterPaths.substring(0, slashIdx)
    val methodToken = afterPaths.substring(slashIdx + 1).substringBefore('/').uppercase()
    val decodedPath = decodeJsonPointerToken(rawPathToken)

    val source =
        findSourceForOperationPath(arazzo, beforeHash)
            ?: arazzo.sourceDescriptions.firstOrNull()
            ?: return CddError.WorkflowExecutionError(
                    "", stepId, "No sourceDescription matches '$beforeHash'")
                .toFailure()
    val openApi =
        lookupOpenApiForSource(arazzo, source)
            ?: return CddError.WorkflowExecutionError(
                    "", stepId, "OpenAPI document for source '${source.name}' is not registered")
                .toFailure()
    val endpoints = OpenApiPathFlattener.flattenPaths(openApi.paths)
    val endpoint =
        endpoints.firstOrNull {
          it.path == decodedPath && it.method.name.equals(methodToken, ignoreCase = true)
        }
            ?: return CddError.WorkflowExecutionError(
                    "",
                    stepId,
                    "Operation '$methodToken $decodedPath' not found in source '${source.name}'")
                .toFailure()
    return Result.success(ResolvedOperationTarget(source, endpoint))
  }

  private fun findSourceForOperationPath(
      arazzo: ArazzoDefinition,
      beforeHash: String
  ): SourceDescription? {
    if (beforeHash.startsWith("{\$sourceDescriptions.") && beforeHash.endsWith(".url}")) {
      val srcName = beforeHash.removePrefix("{\$sourceDescriptions.").removeSuffix(".url}")
      return arazzo.sourceDescriptions.firstOrNull { it.name == srcName }
    }
    return arazzo.sourceDescriptions.firstOrNull { it.url == beforeHash }
  }

  /**
   * Resolves a target [Workflow] for a [StepObject] specifying `workflowId`.
   *
   * @param arazzo The enclosing [ArazzoDefinition].
   * @param stepWorkflowId The `step.workflowId` value.
   * @return A [Result] containing [ResolvedWorkflowTarget] or a [CddError.WorkflowExecutionError].
   */
  fun resolveStepWorkflowResult(
      arazzo: ArazzoDefinition,
      stepWorkflowId: String
  ): Result<ResolvedWorkflowTarget> {
    if (stepWorkflowId.startsWith("\$sourceDescriptions.")) {
      val rest = stepWorkflowId.removePrefix("\$sourceDescriptions.")
      val parts = rest.split(".")
      if (parts.size >= 3 && parts[1] == "workflows") {
        val srcName = parts[0]
        val wfId = parts.drop(2).joinToString(".")
        val source =
            arazzo.sourceDescriptions.firstOrNull { it.name == srcName }
                ?: return CddError.WorkflowExecutionError(
                        stepWorkflowId, null, "Unknown sourceDescription '$srcName'")
                    .toFailure()
        val extArazzo =
            lookupArazzoForSource(arazzo, source)
                ?: return CddError.WorkflowExecutionError(
                        stepWorkflowId,
                        null,
                        "Arazzo document for source '$srcName' is not registered")
                    .toFailure()
        val wf =
            extArazzo.workflows.firstOrNull { it.workflowId == wfId }
                ?: return CddError.WorkflowExecutionError(
                        stepWorkflowId, null, "Workflow '$wfId' not found in source '$srcName'")
                    .toFailure()
        return Result.success(ResolvedWorkflowTarget(source, wf))
      }
    }
    val local =
        arazzo.workflows.firstOrNull { it.workflowId == stepWorkflowId }
            ?: return CddError.WorkflowExecutionError(
                    stepWorkflowId, null, "Workflow '$stepWorkflowId' not found")
                .toFailure()
    return Result.success(ResolvedWorkflowTarget(null, local))
  }

  /**
   * Dereferences a [ReusableOr] [ArazzoParameter] against [components], applying any overridden
   * `value` from the [domain.ReusableObject].
   *
   * @param item The [ReusableOr] parameter entry.
   * @param components Optional [ArazzoComponents] container.
   * @return A [Result] containing the resolved [ArazzoParameter] or a [CddError.ArazzoParseError].
   */
  fun resolveParameterResult(
      item: ReusableOr<ArazzoParameter>,
      components: ArazzoComponents?
  ): Result<ArazzoParameter> =
      when (item) {
        is ReusableOr.Inline -> Result.success(item.value)
        is ReusableOr.Reference -> {
          val ref = item.reusable.reference
          val key = ref.removePrefix("\$components.parameters.")
          val map = if (components != null) components.parameters else emptyMap()
          val base =
              map[key]
                  ?: return CddError.ArazzoParseError(
                          "Component parameter '$ref' not found in components.parameters")
                      .toFailure()
          val merged =
              if (item.reusable.value != null) {
                base.copy(value = item.reusable.value)
              } else {
                base
              }
          Result.success(merged)
        }
      }

  /**
   * Dereferences a [ReusableOr] [SuccessActionObject] against [components].
   *
   * @param item The [ReusableOr] success action entry.
   * @param components Optional [ArazzoComponents] container.
   * @return A [Result] containing the resolved [SuccessActionObject] or a
   *   [CddError.ArazzoParseError].
   */
  fun resolveSuccessActionResult(
      item: ReusableOr<SuccessActionObject>,
      components: ArazzoComponents?
  ): Result<SuccessActionObject> =
      when (item) {
        is ReusableOr.Inline -> Result.success(item.value)
        is ReusableOr.Reference -> {
          val ref = item.reusable.reference
          val key = ref.removePrefix("\$components.successActions.")
          val map = if (components != null) components.successActions else emptyMap()
          val base =
              map[key]
                  ?: return CddError.ArazzoParseError(
                          "Component successAction '$ref' not found in components.successActions")
                      .toFailure()
          Result.success(base)
        }
      }

  /**
   * Dereferences a [ReusableOr] [FailureActionObject] against [components].
   *
   * @param item The [ReusableOr] failure action entry.
   * @param components Optional [ArazzoComponents] container.
   * @return A [Result] containing the resolved [FailureActionObject] or a
   *   [CddError.ArazzoParseError].
   */
  fun resolveFailureActionResult(
      item: ReusableOr<FailureActionObject>,
      components: ArazzoComponents?
  ): Result<FailureActionObject> =
      when (item) {
        is ReusableOr.Inline -> Result.success(item.value)
        is ReusableOr.Reference -> {
          val ref = item.reusable.reference
          val key = ref.removePrefix("\$components.failureActions.")
          val map = if (components != null) components.failureActions else emptyMap()
          val base =
              map[key]
                  ?: return CddError.ArazzoParseError(
                          "Component failureAction '$ref' not found in components.failureActions")
                      .toFailure()
          Result.success(base)
        }
      }

  /**
   * Resolves a workflow `inputs` [SchemaProperty], dereferencing `$ref` pointing to
   * `$components.inputs.<name>` or `#/components/inputs/<name>`.
   *
   * @param inputs Optional [SchemaProperty] on a [Workflow].
   * @param components Optional [ArazzoComponents] container.
   * @return The resolved [SchemaProperty] or `null`.
   */
  fun resolveWorkflowInputs(
      inputs: SchemaProperty?,
      components: ArazzoComponents?
  ): SchemaProperty? {
    if (inputs == null) return null
    val ref = inputs.ref ?: return inputs
    val name =
        if (ref.startsWith("\$components.inputs.")) {
          ref.removePrefix("\$components.inputs.")
        } else {
          ref.removePrefix("#/components/inputs/")
        }
    val map = if (components != null) components.inputs else emptyMap()
    return map[name] ?: inputs
  }

  private fun lookupOpenApiForSource(
      arazzo: ArazzoDefinition,
      source: SourceDescription
  ): OpenApiDefinition? {
    val byName = openApiBySourceName[source.name]
    if (byName != null) return byName
    val byUrl = openApiRegistry.resolveOpenApi(source.url)
    if (byUrl != null) return byUrl
    val base = arazzo.self
    if (!base.isNullOrBlank()) {
      return openApiRegistry.resolveOpenApi(resolveUri(base, source.url))
    }
    return null
  }

  private fun lookupArazzoForSource(
      arazzo: ArazzoDefinition,
      source: SourceDescription
  ): ArazzoDefinition? {
    val byName = arazzoBySourceName[source.name]
    if (byName != null) return byName
    val byUrl = openApiRegistry.resolveArazzo(source.url)
    if (byUrl != null) return byUrl
    val base = arazzo.self
    if (!base.isNullOrBlank()) {
      return openApiRegistry.resolveArazzo(resolveUri(base, source.url))
    }
    return null
  }
}
