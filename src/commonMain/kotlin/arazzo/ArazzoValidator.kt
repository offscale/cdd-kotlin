/**
 * Structural and semantic validator for Arazzo 1.1.0 and 1.0.x specifications.
 *
 * Verifies version strings, required fields, unique identifiers, mutual exclusivity of step
 * targets, acyclic `dependsOn` graphs, valid `goto` targets, reusable component references, and
 * runtime expression syntax with full [kotlin.Result] percolation.
 */
package arazzo

import domain.ArazzoComponents
import domain.ArazzoDefinition
import domain.ArazzoParameter
import domain.ArazzoParameterLocation
import domain.CddError
import domain.CriterionObject
import domain.FailureActionObject
import domain.FailureActionType
import domain.OpenApiString
import domain.OutputValue
import domain.ReusableOr
import domain.StepObject
import domain.SuccessActionObject
import domain.SuccessActionType
import domain.Workflow
import domain.toFailure
import openapi.OpenApiIssue
import openapi.OpenApiIssueSeverity

/** Multiplatform structural and semantic validator for [ArazzoDefinition] documents. */
class ArazzoValidator {
  private val versionRegex = Regex("""^1\.[01]\.[0-9]+(-[A-Za-z0-9.\-]+)?(\+[A-Za-z0-9.\-]+)?$""")
  private val sourceNameRegex = Regex("""^[A-Za-z0-9_\-]+$""")

  /**
   * Validates an [ArazzoDefinition] and returns a list of [OpenApiIssue]s.
   *
   * @param definition The [ArazzoDefinition] to validate.
   * @return List of discovered [OpenApiIssue]s (empty when 100% valid).
   */
  fun validate(definition: ArazzoDefinition): List<OpenApiIssue> {
    val issues = mutableListOf<OpenApiIssue>()
    if (!versionRegex.matches(definition.arazzo)) {
      issues.add(
          OpenApiIssue(
              OpenApiIssueSeverity.ERROR,
              "arazzo",
              "Unsupported Arazzo specification version '${definition.arazzo}' (expected 1.0.x or 1.1.x)"))
    }
    if (definition.info.title.isBlank()) {
      issues.add(
          OpenApiIssue(OpenApiIssueSeverity.ERROR, "info.title", "info.title must not be blank"))
    }
    if (definition.info.version.isBlank()) {
      issues.add(
          OpenApiIssue(
              OpenApiIssueSeverity.ERROR, "info.version", "info.version must not be blank"))
    }
    validateSourceDescriptions(definition, issues)
    val workflowIds = definition.workflows.map { it.workflowId }.toSet()
    if (definition.workflows.isEmpty()) {
      issues.add(
          OpenApiIssue(
              OpenApiIssueSeverity.ERROR,
              "workflows",
              "Arazzo document must define at least one workflow"))
    }
    val seenWorkflowIds = mutableSetOf<String>()
    for ((wfIdx, wf) in definition.workflows.withIndex()) {
      val wfPath = "workflows[$wfIdx]"
      if (wf.workflowId.isBlank()) {
        issues.add(
            OpenApiIssue(
                OpenApiIssueSeverity.ERROR, "$wfPath.workflowId", "workflowId must not be blank"))
      } else if (!seenWorkflowIds.add(wf.workflowId)) {
        issues.add(
            OpenApiIssue(
                OpenApiIssueSeverity.ERROR,
                "$wfPath.workflowId",
                "Duplicate workflowId '${wf.workflowId}'"))
      }
      for (dep in wf.dependsOn) {
        if (dep !in workflowIds && !dep.startsWith("\$sourceDescriptions.")) {
          issues.add(
              OpenApiIssue(
                  OpenApiIssueSeverity.ERROR,
                  "$wfPath.dependsOn",
                  "Workflow '${wf.workflowId}' dependsOn unknown workflow '$dep'"))
        }
      }
      validateWorkflow(wf, wfPath, workflowIds, definition.components, issues)
    }
    detectWorkflowCycles(definition.workflows, issues)
    validateComponents(definition.components, workflowIds, issues)
    return issues
  }

  /**
   * Validates an [ArazzoDefinition] and returns a [Result] containing [definition] on success or a
   * [CddError.ArazzoValidationError] failure if any error-level issues exist.
   *
   * @param definition The [ArazzoDefinition] to validate.
   * @return A [Result] containing [definition] or a [CddError.ArazzoValidationError].
   */
  fun validateResult(definition: ArazzoDefinition): Result<ArazzoDefinition> {
    val errors = validate(definition).map { "${it.path}: ${it.message}" }
    return if (errors.isEmpty()) {
      Result.success(definition)
    } else {
      CddError.ArazzoValidationError(errors).toFailure()
    }
  }

  private fun validateSourceDescriptions(
      definition: ArazzoDefinition,
      issues: MutableList<OpenApiIssue>
  ) {
    if (definition.sourceDescriptions.isEmpty()) {
      issues.add(
          OpenApiIssue(
              OpenApiIssueSeverity.ERROR,
              "sourceDescriptions",
              "Arazzo document must contain at least one sourceDescription"))
    }
    val seenNames = mutableSetOf<String>()
    for ((idx, src) in definition.sourceDescriptions.withIndex()) {
      val path = "sourceDescriptions[$idx]"
      if (!sourceNameRegex.matches(src.name)) {
        issues.add(
            OpenApiIssue(
                OpenApiIssueSeverity.ERROR,
                "$path.name",
                "SourceDescription name '${src.name}' must match [A-Za-z0-9_-]+"))
      } else if (!seenNames.add(src.name)) {
        issues.add(
            OpenApiIssue(
                OpenApiIssueSeverity.ERROR,
                "$path.name",
                "Duplicate sourceDescription name '${src.name}'"))
      }
      if (src.url.isBlank()) {
        issues.add(
            OpenApiIssue(
                OpenApiIssueSeverity.ERROR, "$path.url", "SourceDescription url must not be blank"))
      }
    }
  }

  private fun validateWorkflow(
      workflow: Workflow,
      wfPath: String,
      allWorkflowIds: Set<String>,
      components: ArazzoComponents?,
      issues: MutableList<OpenApiIssue>
  ) {
    if (workflow.steps.isEmpty()) {
      issues.add(
          OpenApiIssue(
              OpenApiIssueSeverity.ERROR,
              "$wfPath.steps",
              "Workflow '${workflow.workflowId}' must contain at least one step"))
    }
    val stepIds = workflow.steps.map { it.stepId }.toSet()
    val seenStepIds = mutableSetOf<String>()
    for ((stepIdx, step) in workflow.steps.withIndex()) {
      val stepPath = "$wfPath.steps[$stepIdx]"
      if (step.stepId.isBlank()) {
        issues.add(
            OpenApiIssue(
                OpenApiIssueSeverity.ERROR, "$stepPath.stepId", "stepId must not be blank"))
      } else if (!seenStepIds.add(step.stepId)) {
        issues.add(
            OpenApiIssue(
                OpenApiIssueSeverity.ERROR,
                "$stepPath.stepId",
                "Duplicate stepId '${step.stepId}' in workflow '${workflow.workflowId}'"))
      }
      validateStep(step, stepPath, stepIds, allWorkflowIds, components, issues)
    }
    detectStepCycles(workflow, wfPath, issues)
    validateParameterList(
        workflow.parameters, "$wfPath.parameters", isOperationStep = false, components, issues)
    validateSuccessActions(
        workflow.successActions,
        "$wfPath.successActions",
        stepIds,
        allWorkflowIds,
        components,
        issues)
    validateFailureActions(
        workflow.failureActions,
        "$wfPath.failureActions",
        stepIds,
        allWorkflowIds,
        components,
        issues)
    validateOutputs(workflow.outputs, "$wfPath.outputs", issues)
  }

  private fun validateStep(
      step: StepObject,
      stepPath: String,
      stepIds: Set<String>,
      allWorkflowIds: Set<String>,
      components: ArazzoComponents?,
      issues: MutableList<OpenApiIssue>
  ) {
    val targetCount =
        listOfNotNull(step.operationId, step.operationPath, step.channelPath, step.workflowId).size
    if (targetCount != 1) {
      issues.add(
          OpenApiIssue(
              OpenApiIssueSeverity.ERROR,
              stepPath,
              "Step '${step.stepId}' must specify exactly one of operationId, operationPath, channelPath, or workflowId (found $targetCount)"))
    }
    if (step.timeout != null && step.timeout <= 0L) {
      issues.add(
          OpenApiIssue(
              OpenApiIssueSeverity.ERROR,
              "$stepPath.timeout",
              "Step timeout must be a positive integer (found ${step.timeout})"))
    }
    for (dep in step.dependsOn) {
      if (dep !in stepIds) {
        issues.add(
            OpenApiIssue(
                OpenApiIssueSeverity.ERROR,
                "$stepPath.dependsOn",
                "Step '${step.stepId}' dependsOn unknown stepId '$dep'"))
      }
    }
    val isOperationStep = step.workflowId == null
    validateParameterList(
        step.parameters, "$stepPath.parameters", isOperationStep, components, issues)
    if (step.requestBody != null) {
      for ((repIdx, rep) in step.requestBody.replacements.withIndex()) {
        if (rep.target.isBlank()) {
          issues.add(
              OpenApiIssue(
                  OpenApiIssueSeverity.ERROR,
                  "$stepPath.requestBody.replacements[$repIdx].target",
                  "PayloadReplacement target must not be blank"))
        }
        val strVal = (rep.value as? OpenApiString)?.value.orEmpty()
        if (RuntimeExpressionEngine.isOrContainsExpression(strVal)) {
          RuntimeExpressionEngine.parseExpression(strVal).onFailure {
            issues.add(
                OpenApiIssue(
                    OpenApiIssueSeverity.ERROR,
                    "$stepPath.requestBody.replacements[$repIdx].value",
                    it.message.toString()))
          }
        }
      }
    }
    validateCriteria(step.successCriteria, "$stepPath.successCriteria", issues)
    validateSuccessActions(
        step.onSuccess, "$stepPath.onSuccess", stepIds, allWorkflowIds, components, issues)
    validateFailureActions(
        step.onFailure, "$stepPath.onFailure", stepIds, allWorkflowIds, components, issues)
    validateOutputs(step.outputs, "$stepPath.outputs", issues)
  }

  private fun validateParameterList(
      parameters: List<ReusableOr<ArazzoParameter>>,
      path: String,
      isOperationStep: Boolean,
      components: ArazzoComponents?,
      issues: MutableList<OpenApiIssue>
  ) {
    var hasQuery = false
    var hasQueryString = false
    for ((idx, item) in parameters.withIndex()) {
      val itemPath = "$path[$idx]"
      when (item) {
        is ReusableOr.Inline -> {
          val p = item.value
          if (p.name.isBlank()) {
            issues.add(
                OpenApiIssue(
                    OpenApiIssueSeverity.ERROR,
                    "$itemPath.name",
                    "Parameter name must not be blank"))
          }
          if (isOperationStep && p.location == null) {
            issues.add(
                OpenApiIssue(
                    OpenApiIssueSeverity.ERROR,
                    "$itemPath.in",
                    "Parameter '${p.name}' on operation step must specify 'in'"))
          }
          if (p.location == ArazzoParameterLocation.QUERY) hasQuery = true
          if (p.location == ArazzoParameterLocation.QUERYSTRING) hasQueryString = true
          val strVal = (p.value as? OpenApiString)?.value.orEmpty()
          if (RuntimeExpressionEngine.isOrContainsExpression(strVal)) {
            RuntimeExpressionEngine.parseExpression(strVal).onFailure {
              issues.add(
                  OpenApiIssue(
                      OpenApiIssueSeverity.ERROR, "$itemPath.value", it.message.toString()))
            }
          }
        }
        is ReusableOr.Reference -> {
          validateReusableRef(item.reusable.reference, "parameters", components, itemPath, issues)
        }
      }
    }
    if (hasQuery && hasQueryString) {
      issues.add(
          OpenApiIssue(
              OpenApiIssueSeverity.ERROR,
              path,
              "Cannot mix 'query' and 'querystring' parameters on the same step"))
    }
  }

  private fun validateSuccessActions(
      actions: List<ReusableOr<SuccessActionObject>>,
      path: String,
      stepIds: Set<String>,
      allWorkflowIds: Set<String>,
      components: ArazzoComponents?,
      issues: MutableList<OpenApiIssue>
  ) {
    val seenNames = mutableSetOf<String>()
    for ((idx, item) in actions.withIndex()) {
      val itemPath = "$path[$idx]"
      when (item) {
        is ReusableOr.Inline -> {
          val act = item.value
          if (!seenNames.add(act.name)) {
            issues.add(
                OpenApiIssue(
                    OpenApiIssueSeverity.ERROR,
                    "$itemPath.name",
                    "Duplicate successAction name '${act.name}'"))
          }
          validateSuccessActionObject(act, itemPath, stepIds, allWorkflowIds, issues)
        }
        is ReusableOr.Reference -> {
          validateReusableRef(
              item.reusable.reference, "successActions", components, itemPath, issues)
        }
      }
    }
  }

  private fun validateSuccessActionObject(
      act: SuccessActionObject,
      path: String,
      stepIds: Set<String>,
      allWorkflowIds: Set<String>,
      issues: MutableList<OpenApiIssue>
  ) {
    when (act.type) {
      SuccessActionType.END -> {
        if (act.stepId != null || act.workflowId != null) {
          issues.add(
              OpenApiIssue(
                  OpenApiIssueSeverity.ERROR,
                  path,
                  "SuccessAction '${act.name}' of type 'end' must not specify stepId or workflowId"))
        }
      }
      SuccessActionType.GOTO -> {
        val hasStep = act.stepId != null
        val hasWf = act.workflowId != null
        if (hasStep == hasWf) {
          issues.add(
              OpenApiIssue(
                  OpenApiIssueSeverity.ERROR,
                  path,
                  "SuccessAction '${act.name}' of type 'goto' must specify exactly one of stepId or workflowId"))
        } else if (act.stepId != null && stepIds.isNotEmpty() && act.stepId !in stepIds) {
          issues.add(
              OpenApiIssue(
                  OpenApiIssueSeverity.ERROR,
                  "$path.stepId",
                  "SuccessAction '${act.name}' targets unknown stepId '${act.stepId}'"))
        } else if (act.workflowId != null &&
            !act.workflowId.startsWith("\$sourceDescriptions.") &&
            act.workflowId !in allWorkflowIds) {
          issues.add(
              OpenApiIssue(
                  OpenApiIssueSeverity.ERROR,
                  "$path.workflowId",
                  "SuccessAction '${act.name}' targets unknown workflowId '${act.workflowId}'"))
        }
      }
    }
    validateCriteria(act.criteria, "$path.criteria", issues)
  }

  private fun validateFailureActions(
      actions: List<ReusableOr<FailureActionObject>>,
      path: String,
      stepIds: Set<String>,
      allWorkflowIds: Set<String>,
      components: ArazzoComponents?,
      issues: MutableList<OpenApiIssue>
  ) {
    val seenNames = mutableSetOf<String>()
    for ((idx, item) in actions.withIndex()) {
      val itemPath = "$path[$idx]"
      when (item) {
        is ReusableOr.Inline -> {
          val act = item.value
          if (!seenNames.add(act.name)) {
            issues.add(
                OpenApiIssue(
                    OpenApiIssueSeverity.ERROR,
                    "$itemPath.name",
                    "Duplicate failureAction name '${act.name}'"))
          }
          validateFailureActionObject(act, itemPath, stepIds, allWorkflowIds, issues)
        }
        is ReusableOr.Reference -> {
          validateReusableRef(
              item.reusable.reference, "failureActions", components, itemPath, issues)
        }
      }
    }
  }

  private fun validateFailureActionObject(
      act: FailureActionObject,
      path: String,
      stepIds: Set<String>,
      allWorkflowIds: Set<String>,
      issues: MutableList<OpenApiIssue>
  ) {
    if (act.retryAfter != null && act.retryAfter < 0.0) {
      issues.add(
          OpenApiIssue(
              OpenApiIssueSeverity.ERROR,
              "$path.retryAfter",
              "retryAfter must be non-negative (found ${act.retryAfter})"))
    }
    if (act.retryLimit != null && act.retryLimit < 0) {
      issues.add(
          OpenApiIssue(
              OpenApiIssueSeverity.ERROR,
              "$path.retryLimit",
              "retryLimit must be non-negative (found ${act.retryLimit})"))
    }
    when (act.type) {
      FailureActionType.END -> {
        if (act.stepId != null || act.workflowId != null) {
          issues.add(
              OpenApiIssue(
                  OpenApiIssueSeverity.ERROR,
                  path,
                  "FailureAction '${act.name}' of type 'end' must not specify stepId or workflowId"))
        }
      }
      FailureActionType.GOTO -> {
        val hasStep = act.stepId != null
        val hasWf = act.workflowId != null
        if (hasStep == hasWf) {
          issues.add(
              OpenApiIssue(
                  OpenApiIssueSeverity.ERROR,
                  path,
                  "FailureAction '${act.name}' of type 'goto' must specify exactly one of stepId or workflowId"))
        } else if (act.stepId != null && stepIds.isNotEmpty() && act.stepId !in stepIds) {
          issues.add(
              OpenApiIssue(
                  OpenApiIssueSeverity.ERROR,
                  "$path.stepId",
                  "FailureAction '${act.name}' targets unknown stepId '${act.stepId}'"))
        } else if (act.workflowId != null &&
            !act.workflowId.startsWith("\$sourceDescriptions.") &&
            act.workflowId !in allWorkflowIds) {
          issues.add(
              OpenApiIssue(
                  OpenApiIssueSeverity.ERROR,
                  "$path.workflowId",
                  "FailureAction '${act.name}' targets unknown workflowId '${act.workflowId}'"))
        }
      }
      FailureActionType.RETRY -> {
        if (act.stepId != null && act.workflowId != null) {
          issues.add(
              OpenApiIssue(
                  OpenApiIssueSeverity.ERROR,
                  path,
                  "FailureAction '${act.name}' of type 'retry' cannot specify both stepId and workflowId"))
        }
      }
    }
    validateCriteria(act.criteria, "$path.criteria", issues)
  }

  private fun validateCriteria(
      criteria: List<CriterionObject>,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    for ((idx, crit) in criteria.withIndex()) {
      val critPath = "$path[$idx]"
      if (crit.condition.isBlank()) {
        issues.add(
            OpenApiIssue(
                OpenApiIssueSeverity.ERROR,
                "$critPath.condition",
                "Criterion condition must not be blank"))
      }
      val ctxStr = crit.context
      if (ctxStr != null) {
        RuntimeExpressionEngine.parseExpression(ctxStr).onFailure {
          issues.add(
              OpenApiIssue(OpenApiIssueSeverity.ERROR, "$critPath.context", it.message.toString()))
        }
      }
    }
  }

  private fun validateOutputs(
      outputs: Map<String, OutputValue>,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    for ((name, outVal) in outputs) {
      when (outVal) {
        is OutputValue.Expression -> {
          RuntimeExpressionEngine.parseExpression(outVal.expression).onFailure {
            issues.add(
                OpenApiIssue(OpenApiIssueSeverity.ERROR, "$path.$name", it.message.toString()))
          }
        }
        is OutputValue.Selector -> {
          RuntimeExpressionEngine.parseExpression(outVal.selector.context).onFailure {
            issues.add(
                OpenApiIssue(
                    OpenApiIssueSeverity.ERROR, "$path.$name.context", it.message.toString()))
          }
          if (outVal.selector.selector.isBlank()) {
            issues.add(
                OpenApiIssue(
                    OpenApiIssueSeverity.ERROR,
                    "$path.$name.selector",
                    "Selector query string must not be blank"))
          }
        }
      }
    }
  }

  private fun validateReusableRef(
      reference: String,
      expectedCategory: String,
      components: ArazzoComponents?,
      path: String,
      issues: MutableList<OpenApiIssue>
  ) {
    val prefix = "\$components.$expectedCategory."
    if (!reference.startsWith(prefix)) {
      issues.add(
          OpenApiIssue(
              OpenApiIssueSeverity.ERROR,
              "$path.reference",
              "Reusable reference '$reference' must start with '$prefix'"))
      return
    }
    val key = reference.removePrefix(prefix)
    val map =
        when (expectedCategory) {
          "parameters" -> components?.parameters
          "successActions" -> components?.successActions
          else -> components?.failureActions
        }
    if (map?.containsKey(key) != true) {
      issues.add(
          OpenApiIssue(
              OpenApiIssueSeverity.ERROR,
              "$path.reference",
              "Reusable reference '$reference' does not exist in components.$expectedCategory"))
    }
  }

  private fun validateComponents(
      components: ArazzoComponents?,
      allWorkflowIds: Set<String>,
      issues: MutableList<OpenApiIssue>
  ) {
    if (components == null) return
    for ((key, action) in components.successActions) {
      validateSuccessActionObject(
          action, "components.successActions.$key", emptySet(), allWorkflowIds, issues)
    }
    for ((key, action) in components.failureActions) {
      validateFailureActionObject(
          action, "components.failureActions.$key", emptySet(), allWorkflowIds, issues)
    }
  }

  private fun detectWorkflowCycles(workflows: List<Workflow>, issues: MutableList<OpenApiIssue>) {
    val adj = workflows.associate { it.workflowId to it.dependsOn }
    val state = mutableMapOf<String, Int>()
    for (wf in workflows) {
      if (!state.containsKey(wf.workflowId) && hasCycleDfs(wf.workflowId, adj, state)) {
        issues.add(
            OpenApiIssue(
                OpenApiIssueSeverity.ERROR,
                "workflows",
                "Cyclic workflow dependency detected involving '${wf.workflowId}'"))
        break
      }
    }
  }

  private fun detectStepCycles(
      workflow: Workflow,
      wfPath: String,
      issues: MutableList<OpenApiIssue>
  ) {
    val adj = workflow.steps.associate { it.stepId to it.dependsOn }
    val state = mutableMapOf<String, Int>()
    for (step in workflow.steps) {
      if (!state.containsKey(step.stepId) && hasCycleDfs(step.stepId, adj, state)) {
        issues.add(
            OpenApiIssue(
                OpenApiIssueSeverity.ERROR,
                "$wfPath.steps",
                "Cyclic step dependency detected in workflow '${workflow.workflowId}' involving step '${step.stepId}'"))
        break
      }
    }
  }

  private fun hasCycleDfs(
      node: String,
      adj: Map<String, List<String>>,
      state: MutableMap<String, Int>
  ): Boolean {
    state[node] = 1
    val neighbors = adj.getValue(node)
    for (neighbor in neighbors) {
      val nState = state[neighbor] ?: 0
      if (nState == 1) return true
      if (nState == 0 && adj.containsKey(neighbor) && hasCycleDfs(neighbor, adj, state)) {
        return true
      }
    }
    state[node] = 2
    return false
  }
}
