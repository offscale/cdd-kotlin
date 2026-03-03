package cdd.routes

import cdd.openapi.*
import cdd.shared.PsiInfrastructure
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Parses CLI command structures to generate endpoint definitions.
 */
class CliParser {
    /**
     * Parses the given source code to extract a list of [EndpointDefinition]s.
     *
     * @param sourceCode the raw source code of the Kotlin file to be parsed.
     * @return a list of parsed endpoint definitions representing CLI commands.
     */
    fun parse(sourceCode: String): List<EndpointDefinition> {
        val file = PsiInfrastructure.createPsiFactory().createFile(sourceCode)
        val endpoints = mutableListOf<EndpointDefinition>()
        
        val classes = file.collectDescendantsOfType<KtClass>()
        
        for (cls in classes) {
            val superTypeStr = cls.superTypeListEntries.firstOrNull()?.text ?: ""
            if (!superTypeStr.contains("CliktCommand")) continue
            if (cls.name == "MainCommand" || cls.name?.endsWith("Command") != true) continue
            
            val nameMatch = Regex("""name\s*=\s*"([^"]+)"""").find(superTypeStr)
            val helpMatch = Regex("""help\s*=\s*"([^"]+)"""").find(superTypeStr)
            if (nameMatch == null) continue
            
            val opId = nameMatch.groupValues[1]
            val summary = helpMatch?.groupValues?.get(1)
            
            val props = cls.collectDescendantsOfType<KtProperty>().filter { it.delegateExpression?.text?.contains("option") == true }
            
            val runFunc = cls.collectDescendantsOfType<KtNamedFunction>().find { it.name == "run" }
            val runText = runFunc?.bodyExpression?.text ?: ""
            if (props.isEmpty() && runText.replace(Regex("""\s"""), "") == "{}") {
                continue // It's likely a Tag command, not an endpoint
            }
            
            val parameters = mutableListOf<EndpointParameter>()
            for (prop in props) {
                val propName = prop.name ?: continue
                val delegateText = prop.delegateExpression?.text ?: ""
                val required = delegateText.contains(".required()")
                val pHelpMatch = Regex("""help\s*=\s*"([^"]+)"""").find(delegateText)
                val pHelp = pHelpMatch?.groupValues?.get(1)
                
                val type = if (delegateText.contains(".int()")) "integer"
                           else if (delegateText.contains(".double()")) "number"
                           else if (delegateText.contains(".boolean()")) "boolean"
                           else "String"
                
                parameters.add(EndpointParameter(
                    name = propName,
                    type = type,
                    location = ParameterLocation.QUERY,
                    isRequired = required,
                    description = pHelp
                ))
            }
            
            val apiMatch = Regex("""([a-zA-Z0-9_]+)Api\(""").find(runText)
            val tag = if (apiMatch != null) {
                apiMatch.groupValues[1]
            } else {
                "Default"
            }
            
            endpoints.add(EndpointDefinition(
                path = "/$tag/$opId",
                method = HttpMethod.POST,
                operationId = opId,
                summary = summary,
                tags = listOf(tag),
                parameters = parameters
            ))
        }
        
        return endpoints
    }
}
