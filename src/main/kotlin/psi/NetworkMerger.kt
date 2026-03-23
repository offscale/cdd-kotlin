package psi

import domain.EndpointDefinition

/**
 * Service to reconcile differences between existing Code definitions and new Specification definitions.
 *
 * Current Strategy: Strict Spec-Driven.
 * 1. Match endpoints by operationId.
 * 2. If found in Spec, the Spec definition overwrites the Code definition (updates interface/types).
 * 3. If in Spec but not Code, it is Added.
 * 4. If in Code but not Spec, it is Removed.
 */
class NetworkMerger {

    /**
     * Merges endpoint definitions by operationId using a strict spec-first strategy.
     *
     * @param existing Existing code-defined endpoints.
     * @param newSpec Newly parsed spec-defined endpoints.
     * @return A merged list reflecting the spec as the source of truth.
     */
    fun mergeEndpoints(
        existing: List<EndpointDefinition>,
        newSpec: List<EndpointDefinition>
    ): List<EndpointDefinition> {
        val existingMap = existing.associateBy { it.operationId }
        val merged = mutableListOf<EndpointDefinition>()

        for (specEndpoint in newSpec) {
            val codeEndpoint = existingMap[specEndpoint.operationId]

            if (codeEndpoint != null) {
                // UPDATE:
                // We have an existing implementation.
                // In a strict CDD flow, the Spec dictates the contract.
                // If we tracked manual implementation bodies, we would merge them here.
                // For now, we take the Spec definition to ensure the Interface is current.
                merged.add(specEndpoint)
            } else {
                // ADD:
                // New endpoint defined in Spec, implies generating new code.
                merged.add(specEndpoint)
            }
        }

        // Implicit DELETE:
        // Any key in existingMap that was not visited is dropped.

        return merged
    }
}
