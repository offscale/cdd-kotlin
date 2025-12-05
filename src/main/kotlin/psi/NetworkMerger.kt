package psi

import domain.EndpointDefinition
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Merges new Endpoint Definitions into an existing Ktor API Implementation file.
 * Preserves existing methods and user customizations.
 */
class NetworkMerger {

    private val psiFactory = PsiInfrastructure.createPsiFactory()
    private val generator = NetworkGenerator()

    /**
     * Merges endpoints into existing source code string.
     * Convenience wrapper for string-based operations (used by Tests).
     *
     * @param sourceCode The original Kotlin file content.
     * @param endpoints The list of spec definitions to merge in.
     * @return The modified Kotlin source code.
     */
    fun mergeApi(sourceCode: String, endpoints: List<EndpointDefinition>): String {
        val file = psiFactory.createFile("TempApi.kt", sourceCode)
        return merge(file, endpoints)
    }

    /**
     * Merges a list of endpoints into the existing Kotlin file (PSI).
     * Identifies missing methods in Interface and Implementation and appends them.
     */
    fun merge(existingFile: KtFile, endpoints: List<EndpointDefinition>): String {
        // 1. Identify Interface and Implementation Classes
        val classes = existingFile.collectDescendantsOfType<KtClass>()
        val interfaceClass = classes.find { it.isInterface() }
        val implClass = classes.find { !it.isInterface() && it.name?.endsWith("Exception") == false }

        if (interfaceClass == null || implClass == null) {
            // Cannot merge if structure isn't standard; return original text or throw
            // For robustness, returning original text if structure is unrecognized
            return existingFile.text
        }

        // 2. Scan existing method names
        val existingInterfaceMethods = interfaceClass.collectDescendantsOfType<KtNamedFunction>().mapNotNull { it.name }.toSet()
        val existingImplMethods = implClass.collectDescendantsOfType<KtNamedFunction>().mapNotNull { it.name }.toSet()

        // 3. Filter for missing endpoints
        val missingInInterface = endpoints.filter { it.operationId !in existingInterfaceMethods }
        val missingInImpl = endpoints.filter { it.operationId !in existingImplMethods }

        if (missingInInterface.isEmpty() && missingInImpl.isEmpty()) {
            return existingFile.text // No changes needed
        }

        var sourceCode = existingFile.text

        // 4. Append to Interface
        if (missingInInterface.isNotEmpty()) {
            val interfaceBody = interfaceClass.getBody()
            // Find closing brace of interface
            val insertOffset = interfaceBody?.rBrace?.textRange?.startOffset ?: (interfaceClass.textRange.endOffset - 1)

            // Generate signatures
            val newSignatures = missingInInterface.joinToString("\n\n") { ep ->
                "    " + generator.generateMethodSignature(ep)
            }

            // Insert
            sourceCode = StringBuilder(sourceCode).insert(insertOffset, "\n$newSignatures\n").toString()
        }

        // 5. Append to Implementation
        // We must re-locate indices because sourceCode length changed after Interface insertion.
        // Heuristic: Find the class declaration in the NEW string, then find its closing brace.

        val implStart = sourceCode.indexOf("class ${implClass.name}")
        if (implStart != -1 && missingInImpl.isNotEmpty()) {
            // Find the end of validity for search (e.g. before "class ApiException")
            val apiExceptionIndex = sourceCode.indexOf("class ApiException")
            val searchBound = if (apiExceptionIndex != -1) apiExceptionIndex else sourceCode.length

            // Find the last '}' before that bound
            val insertPoint = sourceCode.lastIndexOf("}", searchBound)

            if (insertPoint != -1) {
                val newMethods = missingInImpl.joinToString("\n\n") { ep ->
                    generator.generateMethodImpl(ep)
                }
                sourceCode = StringBuilder(sourceCode).insert(insertPoint, "\n$newMethods\n").toString()
            }
        }

        return sourceCode
    }
}
