package psi

import domain.EndpointDefinition
import domain.EndpointParameter
import domain.HttpMethod
import domain.ParameterLocation
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Parsers Kotlin source code to extract API Endpoint definitions implementation details.
 * Analyzes Ktor `client.request` calls to reverse-engineer the API Spec.
 */
class NetworkParser {

    /**
     * Parses the provided source code.
     * @param sourceCode The Kotlin file content.
     * @return A list of endpoints found in the code.
     */
    fun parse(sourceCode: String): List<EndpointDefinition> {
        val psiFactory = PsiInfrastructure.createPsiFactory()
        val file = psiFactory.createFile("Analysis.kt", sourceCode)

        val functions = file.collectDescendantsOfType<KtNamedFunction>()

        return functions.mapNotNull { parseFunction(it) }
    }

    private fun parseFunction(func: KtNamedFunction): EndpointDefinition? {
        val operationId = func.name ?: return null

        // Find the client.request call
        val callExpressions = func.collectDescendantsOfType<KtCallExpression>()
        val requestCall = callExpressions.find { call ->
            val callee = call.calleeExpression
            callee?.text == "request"
        } ?: return null

        // 1. Extract Path (URL)
        val urlArg = requestCall.valueArguments.firstOrNull()
        val stringTemplate = urlArg?.getArgumentExpression() as? KtStringTemplateExpression
        val path = parseUrlTemplate(stringTemplate) ?: "/"

        // 2. Analyze Lambda for Method and Params
        var method = HttpMethod.GET // Default
        val parameters = mutableListOf<EndpointParameter>()
        var requestBodyVarName: String? = null

        // Add Path Parameters detected from URL
        val pathParams = extractPathParams(path)
        pathParams.forEach { name ->
            parameters.add(EndpointParameter(name, "String", ParameterLocation.PATH))
        }

        val lambdaArg = requestCall.lambdaArguments.firstOrNull()
        val block = lambdaArg?.getLambdaExpression()?.bodyExpression

        block?.statements?.forEach { statement ->
            if (statement is KtBinaryExpression) {
                // Check for 'method = HttpMethod.Post'
                if (statement.left?.text == "method") {
                    val rightText = statement.right?.text
                    method = parseMethod(rightText)
                }
            } else if (statement is KtCallExpression) {
                val callee = statement.calleeExpression?.text
                when (callee) {
                    "parameter" -> extractParam(statement, ParameterLocation.QUERY)?.let { parameters.add(it) }
                    "header" -> extractParam(statement, ParameterLocation.HEADER)?.let { parameters.add(it) }
                    "setBody" -> {
                        requestBodyVarName = statement.valueArguments.firstOrNull()?.getArgumentExpression()?.text
                    }
                }
            }
        }

        // 3. Infer Types from Function Signature
        val finalParams = parameters.map { param ->
            val funcArg = func.valueParameters.find { it.name == param.name || "`" + it.name + "`" == param.name }
            val type = funcArg?.typeReference?.text ?: "String"
            param.copy(type = type)
        }

        // 4. Request Body Type
        var requestBodyType: String? = null
        if (requestBodyVarName != null) {
            val funcArg = func.valueParameters.find { it.name == requestBodyVarName }
            requestBodyType = funcArg?.typeReference?.text
        }

        // 5. Response Type
        var responseType = "Unit"

        val parent = requestCall.parent
        if (parent is KtDotQualifiedExpression && parent.selectorExpression is KtCallExpression) {
            val chainedCall = parent.selectorExpression as KtCallExpression
            if (chainedCall.calleeExpression?.text == "body") {
                val typeArg = chainedCall.typeArgumentList?.arguments?.firstOrNull()
                if (typeArg != null) {
                    responseType = typeArg.text
                }
            }
        }

        if (responseType == "Unit" && func.typeReference != null) {
            responseType = func.typeReference!!.text
        }

        // 6. Extract KDoc Summary
        val summary = extractDoc(func)

        return EndpointDefinition(
            path = path,
            method = method,
            operationId = operationId,
            parameters = finalParams,
            requestBodyType = requestBodyType,
            responseType = if (responseType == "Unit") null else responseType,
            summary = summary
        )
    }

    private fun parseUrlTemplate(template: KtStringTemplateExpression?): String? {
        if (template == null) return null

        val sb = StringBuilder()
        template.entries.forEach { entry ->
            if (entry is org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry) {
                sb.append(entry.text)
            } else if (entry is org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry) {
                sb.append("{${entry.expression?.text}}")
            } else if (entry is org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry) {
                val content = entry.expression?.text
                sb.append("{$content}")
            }
        }
        return sb.toString()
    }

    private fun extractPathParams(path: String): List<String> {
        val regex = "\\{([^}]+)\\}".toRegex()
        return regex.findAll(path).map { it.groupValues[1] }.toList()
    }

    private fun parseMethod(text: String?): HttpMethod {
        if (text == null) return HttpMethod.GET
        val methodStr = text.substringAfter("HttpMethod.")
        return try {
            HttpMethod.valueOf(methodStr.uppercase())
        } catch (e: Exception) {
            HttpMethod.GET
        }
    }

    private fun extractParam(expression: KtCallExpression, location: ParameterLocation): EndpointParameter? {
        val args = expression.valueArguments
        if (args.size < 2) return null

        val keyExpr = args[0].getArgumentExpression()
        val key = keyExpr?.text?.replace("\"", "") ?: "unknown"

        return EndpointParameter(
            name = key,
            type = "String",
            location = location
        )
    }

    /**
     * Reuse logic to strip KDoc markers.
     */
    private fun extractDoc(element: org.jetbrains.kotlin.psi.KtDeclaration): String? {
        val docComment = element.docComment ?: return null
        return docComment.text
            .split("\n")
            .map { line ->
                line.trim()
                    .replace("/**", "")
                    .replace("*/", "")
                    .replaceFirst("*", "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { null }
    }
}
