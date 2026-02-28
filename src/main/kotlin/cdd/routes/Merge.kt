package cdd.routes

import cdd.openapi.*
import cdd.classes.*
import cdd.routes.*
import cdd.docstrings.*
import cdd.shared.*
import cdd.scaffold.*


import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

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

    /**
     * Merges endpoints directly into the Kotlin source code, preserving manual comments
     * and non-generated methods (e.g., helpers).
     */
    fun mergeApiSource(
        existingCode: String,
        newSpec: List<EndpointDefinition>,
        interfaceName: String,
        implName: String
    ): String {
        val psiFactory = PsiInfrastructure.createPsiFactory()
        val file = psiFactory.createFile("Fragment.kt", existingCode)
        
        var currentSource = existingCode
        
        // 1. Process Interface
        val targetInterface = file.collectDescendantsOfType<KtClass>().firstOrNull { it.isInterface() && it.name == interfaceName }
        if (targetInterface != null) {
            currentSource = mergeClass(currentSource, targetInterface.name!!, newSpec, isInterface = true)
        }
        
        // 2. Process Implementation
        val fileAfterInterface = psiFactory.createFile("Fragment2.kt", currentSource)
        val targetImpl = fileAfterInterface.collectDescendantsOfType<KtClass>().firstOrNull { !it.isInterface() && it.name == implName }
        if (targetImpl != null) {
            currentSource = mergeClass(currentSource, targetImpl.name!!, newSpec, isInterface = false)
        }
        
        return currentSource
    }
    
    /** Auto generated docs */ fun mergeClass(
        source: String,
        className: String,
        newSpec: List<EndpointDefinition>,
        isInterface: Boolean
    ): String {
        val psiFactory = PsiInfrastructure.createPsiFactory()
        val file = psiFactory.createFile("Fragment.kt", source)
        val targetClass = file.collectDescendantsOfType<KtClass>().firstOrNull { it.name == className } ?: return source
        
        val generator = NetworkGenerator()
        val classBody = targetClass.body ?: return source
        
        // 1. Map existing functions mapped by operationId
        val existingFunctions = classBody.collectDescendantsOfType<KtNamedFunction>()
        val existingEndpointsMap = NetworkParser().parse(source).associateBy { it.operationId }
        
        // Find existing endpoint functions so we don't accidentally remove manual helper functions
        val endpointFunctions = existingFunctions.filter { existingEndpointsMap.containsKey(it.name) }
        
        // 2. Find functions to REMOVE
        val specOperationIds = newSpec.map { it.operationId }.toSet()
        val functionsToRemove = endpointFunctions.filter { !specOperationIds.contains(it.name) }
            .sortedByDescending { it.startOffset } // Process backwards to not invalidate earlier offsets
            
        val sb = java.lang.StringBuilder(source)
        
        for (func in functionsToRemove) {
            val startToRemove = func.startOffset
            val endToRemove = func.endOffset
            
            sb.delete(startToRemove, endToRemove)
            
            // Clean up trailing whitespace up to the newline
            var current = startToRemove - 1
            while (current >= 0 && (sb[current] == ' ' || sb[current] == '\t')) {
                sb.deleteCharAt(current)
                current--
            }
            if (current >= 0 && sb[current] == '\n') {
                sb.deleteCharAt(current)
            }
        }
        
        // Update tempSource with removals
        var tempSource = sb.toString()
        
        // 3. Find functions to UPDATE
        val tempFile = psiFactory.createFile("Temp.kt", tempSource)
        val tempClass = tempFile.collectDescendantsOfType<KtClass>().firstOrNull { it.name == className } ?: return tempSource
        val remainingFunctions = tempClass.body?.collectDescendantsOfType<KtNamedFunction>() ?: emptyList()
        val remainingEndpointFunctions = remainingFunctions.filter { existingEndpointsMap.containsKey(it.name) }
        
        val functionsToUpdate = remainingEndpointFunctions.filter { specOperationIds.contains(it.name) }
            .sortedByDescending { it.startOffset } // Sort backwards
            
        val sbUpdate = java.lang.StringBuilder(tempSource)
        
        for (func in functionsToUpdate) {
            val specEndpoint = newSpec.first { it.operationId == func.name }
            val newDoc = generator.generateKDoc(specEndpoint)
            val newMethod = if (isInterface) {
                generator.generateMethodSignature(specEndpoint)
            } else {
                generator.generateMethodImpl(specEndpoint).trimStart()
            }
            
            val startToReplace = func.docComment?.startOffset 
                ?: func.modifierList?.startOffset 
                ?: func.funKeyword?.startOffset 
                ?: func.startOffset
            val endToReplace = func.endOffset
            
            // Re-indent logic:
            val indentation = "    " // 4 spaces
            val replacement = "$newDoc$indentation$newMethod"
            
            sbUpdate.replace(startToReplace, endToReplace, replacement)
        }
        
        tempSource = sbUpdate.toString()
        
        // 4. Find endpoints to ADD
        val existingFunctionNames = endpointFunctions.mapNotNull { it.name }.toSet()
        val endpointsToAdd = newSpec.filter { !existingFunctionNames.contains(it.operationId) }
        
        if (endpointsToAdd.isNotEmpty()) {
            val addFile = psiFactory.createFile("Add.kt", tempSource)
            val addClass = addFile.collectDescendantsOfType<KtClass>().firstOrNull { it.name == className }
            val rBraceOffset = addClass?.body?.rBrace?.startOffset
            
            if (rBraceOffset != null) {
                val sbAdd = java.lang.StringBuilder(tempSource)
                val indentation = "    " // 4 spaces
                val newMethodsCode = endpointsToAdd.joinToString("\n\n") { ep ->
                    val doc = generator.generateKDoc(ep)
                    val method = if (isInterface) {
                        generator.generateMethodSignature(ep)
                    } else {
                        generator.generateMethodImpl(ep).replaceFirst("override ", "")
                        if (generator.generateMethodImpl(ep).startsWith("override ")) generator.generateMethodImpl(ep) else "override ${generator.generateMethodImpl(ep)}"
                        // Wait, generateMethodImpl actually includes the "override " inside its template, as well as the signature.
                        generator.generateMethodImpl(ep).trimStart()
                    }
                    "$doc$indentation$method"
                }
                
                sbAdd.insert(rBraceOffset, "\n$indentation$newMethodsCode\n")
                tempSource = sbAdd.toString()
            }
        }
        
        return tempSource
    }
}
