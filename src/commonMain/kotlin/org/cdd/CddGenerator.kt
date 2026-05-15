package org.cdd

import openapi.OpenApiParser
import openapi.OpenApiDocument
import readFile
import writeToFile

object CddGenerator {
    fun generateSdk(config: Config) {
        val jsonStr = readFile(config.inputPath)
        val parser = OpenApiParser()
        val result = try { parser.parseDocumentString(jsonStr) } catch(e: Throwable) { println("PARSE ERROR: " + e.message); throw e }

        val doc = when (result) {
            is OpenApiDocument.OpenApi -> result.definition
            else -> {
                throw RuntimeException("Not an OpenAPI document")
            }
        }

        println("Generating Kotlin SDK...")

        // Generate Models.kt
        val modelsSb = StringBuilder()
        modelsSb.append("package org.example\n\n")
        modelsSb.append("import kotlinx.serialization.Serializable\n\n")
        
        doc.components?.schemas?.forEach { (name, schema) ->
            modelsSb.append("@Serializable\n")
            modelsSb.append("data class $name(\n")
            val props = mutableListOf<String>()
            schema.properties.forEach { (propName, propDef) ->
                var kotlinType = "String"
                if (propDef.types.contains("integer") || propDef.type == "integer") kotlinType = "Int"
                else if (propDef.types.contains("boolean") || propDef.type == "boolean") kotlinType = "Boolean"
                else if (propDef.types.contains("number") || propDef.type == "number") kotlinType = "Double"
                else if (propDef.types.contains("array") || propDef.type == "array") {
                    kotlinType = "List<kotlinx.serialization.json.JsonElement>"
                } else if (propDef.ref != null) {
                    kotlinType = propDef.ref.split("/").last()
                }
                
                if (!schema.required.contains(propName)) {
                    kotlinType += "? = null"
                }
                props.add("    val $propName: $kotlinType")
            }
            modelsSb.append(props.joinToString(",\n"))
            modelsSb.append("\n)\n\n")
        }
        
        if (doc.components?.schemas?.isNotEmpty() == true) {
            writeToFile("${config.outputDir}/src/main/kotlin/org/example/Models.kt", modelsSb.toString())
        }

        // Generate Client.kt
        val clientSb = StringBuilder()
        clientSb.append("package org.example\n\n")
        clientSb.append("import io.ktor.client.*\n")
        clientSb.append("import io.ktor.client.request.*\n")
        clientSb.append("import io.ktor.client.statement.*\n")
        clientSb.append("import io.ktor.client.plugins.contentnegotiation.*\n")
        clientSb.append("import io.ktor.serialization.kotlinx.json.*\n")
        clientSb.append("import io.ktor.http.ContentType\n")
        clientSb.append("import io.ktor.http.contentType\n\n")
        clientSb.append("class Client(val baseUrl: String = \"http://localhost:8080/v2\") {\n")
        clientSb.append("    val client = HttpClient() {\n")
        clientSb.append("        install(ContentNegotiation) {\n")
        clientSb.append("            json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })\n")
        clientSb.append("        }\n")
        clientSb.append("    }\n\n")
        
        for ((path, pathItem) in doc.paths) {
            val methods = mapOf(
                "get" to pathItem.get,
                "put" to pathItem.put,
                "post" to pathItem.post,
                "delete" to pathItem.delete,
                "options" to pathItem.options,
                "head" to pathItem.head,
                "patch" to pathItem.patch,
                "trace" to pathItem.trace
            )
            for ((methodName, operation) in methods) {
                if (operation == null) continue
                
                val opId = operation.operationId ?: "${methodName}${path.replace("/", "").replace("{", "").replace("}", "")}"
                
                var urlPath = path
                urlPath = urlPath.replace(Regex("\\{([^}]+)\\}")) { matchResult ->
                    "$" + "{${matchResult.groupValues[1]}}"
                }
                
                val paramsStr = mutableListOf<String>()
                val pathParams = Regex("\\{([^}]+)\\}").findAll(path).map { it.groupValues[1] }.toList()
                for (param in pathParams) {
                    paramsStr.add("$param: String")
                }
                val queryParams = operation.parameters.filter { it.location == domain.ParameterLocation.QUERY }
                for (param in queryParams) {
                    paramsStr.add("${param.name}: String? = null")
                }
                
                if (operation.requestBodyType != null) {
                    paramsStr.add("body: ${operation.requestBodyType}")
                }
                
                val arguments = paramsStr.joinToString(", ")
                
                clientSb.append("    suspend fun $opId($arguments): HttpResponse {\n")
                clientSb.append("        return client.${methodName}(\"\$baseUrl$urlPath\") {\n")
                for (param in queryParams) {
                    clientSb.append("            if (${param.name} != null) parameter(\"${param.name}\", ${param.name})\n")
                }
                if (operation.requestBodyType != null) {
                    clientSb.append("            contentType(ContentType.Application.Json)\n")
                    clientSb.append("            setBody(body)\n")
                }
                clientSb.append("        }\n")
                clientSb.append("    }\n\n")
            }
        }
        clientSb.append("}\n")

        writeToFile("${config.outputDir}/src/main/kotlin/org/example/Client.kt", clientSb.toString())

        if (config.tests) {
            // Generate Mocks.kt
            val mocksSb = StringBuilder()
            mocksSb.append("package org.example\n\n")
            mocksSb.append("object Mocks {\n")
            
            doc.components?.schemas?.forEach { (name, schema) ->
                mocksSb.append("    fun create${name}(): $name {\n")
                mocksSb.append("        return $name(\n")
                val props = mutableListOf<String>()
                schema.properties.forEach { (propName, propDef) ->
                    var dummyValue = "\"dummy\""
                    if (propDef.types.contains("integer") || propDef.type == "integer") dummyValue = "0"
                    else if (propDef.types.contains("boolean") || propDef.type == "boolean") dummyValue = "false"
                    else if (propDef.types.contains("number") || propDef.type == "number") dummyValue = "0.0"
                    else if (propDef.types.contains("array") || propDef.type == "array") dummyValue = "emptyList()"
                    else if (propDef.ref != null) dummyValue = "create${propDef.ref.split("/").last()}()"
                    
                    if (!schema.required.contains(propName)) {
                        dummyValue = "null"
                    }
                    props.add("            $propName = $dummyValue")
                }
                mocksSb.append(props.joinToString(",\n"))
                mocksSb.append("\n        )\n")
                mocksSb.append("    }\n")
            }
            mocksSb.append("}\n")
            writeToFile("${config.outputDir}/src/main/kotlin/org/example/Mocks.kt", mocksSb.toString())

            // Generate Tests.kt
            val testsSb = StringBuilder()
            testsSb.append("package org.example\n\n")
            testsSb.append("import io.ktor.client.statement.*\n\n")
            testsSb.append("class IntegrationTest {\n")
            
            for ((path, pathItem) in doc.paths) {
                val methods = mapOf(
                    "get" to pathItem.get,
                    "put" to pathItem.put,
                    "post" to pathItem.post,
                    "delete" to pathItem.delete,
                    "options" to pathItem.options,
                    "head" to pathItem.head,
                    "patch" to pathItem.patch,
                    "trace" to pathItem.trace
                )
                for ((methodName, operation) in methods) {
                    if (operation == null) continue
                    
                    val opId = operation.operationId ?: "${methodName}${path.replace("/", "").replace("{", "").replace("}", "")}"
                    
                    val argsStr = mutableListOf<String>()
                    val pathParams = Regex("\\{([^}]+)\\}").findAll(path).map { it.groupValues[1] }.toList()
                    for (paramName in pathParams) {
                        val param = operation.parameters.find { it.name == paramName }
                        if (param != null) {
                            if (param.type.contains("integer") || param.type.contains("number")) argsStr.add("\"1\"")
                            else if (param.type.contains("boolean")) argsStr.add("\"false\"")
                            else argsStr.add("\"test_string\"")
                        } else {
                            argsStr.add("\"test_string\"")
                        }
                    }
                    val queryParams = operation.parameters.filter { it.location == domain.ParameterLocation.QUERY }
                    for (param in queryParams) {
                        if (param.type.contains("integer") || param.type.contains("number")) argsStr.add("\"1\"")
                        else if (param.type.contains("boolean")) argsStr.add("\"false\"")
                        else argsStr.add("\"test_string\"")
                    }

                    if (operation.requestBodyType != null) {
                        argsStr.add("Mocks.create${operation.requestBodyType}()")
                    }
                    
                    val argsCall = if (argsStr.isNotEmpty()) argsStr.joinToString(", ") else ""
                    
                    testsSb.append("    @org.junit.jupiter.api.Test\n")
                    val methodNameSanitized = methodName.replace("-", "_")
                    val pathSanitized = path.replace("/", "_").replace("{", "").replace("}", "")
                    testsSb.append("    fun test_${methodNameSanitized}_${pathSanitized}() = kotlinx.coroutines.runBlocking {\n")
                    testsSb.append("        val client = Client(\"http://localhost:8080/v2\")\n")
                    testsSb.append("        val response = client.$opId($argsCall)\n")
                    testsSb.append("        org.junit.jupiter.api.Assertions.assertTrue(response.status.value in 200..299, \"Expected success status, got \" + response.status.value)\n")
                    
                    if (operation.responseType != null && operation.responseType != "Unit") {
                        testsSb.append("        val bodyText = response.bodyAsText()\n")
                        testsSb.append("        if (bodyText.isNotEmpty()) {\n")
                        testsSb.append("            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<${operation.responseType}>(bodyText)\n")
                        testsSb.append("        }\n")
                    }
                    
                    testsSb.append("    }\n\n")
                }
            }
            testsSb.append("}\n")
            writeToFile("${config.outputDir}/src/test/kotlin/org/example/IntegrationTest.kt", testsSb.toString())
        }

        // Generate build.gradle.kts
        if (!config.noInstallablePackage) {
            val buildGradleSb = StringBuilder()
            buildGradleSb.append("plugins {\n")
            buildGradleSb.append("    kotlin(\"jvm\") version \"2.0.0\"\n")
            buildGradleSb.append("    kotlin(\"plugin.serialization\") version \"2.0.0\"\n")
            buildGradleSb.append("}\n\n")
            buildGradleSb.append("repositories {\n")
            buildGradleSb.append("    mavenCentral()\n")
            buildGradleSb.append("}\n\n")
            buildGradleSb.append("dependencies {\n")
            buildGradleSb.append("    implementation(\"io.ktor:ktor-client-core:2.3.11\")\n")
            buildGradleSb.append("    implementation(\"io.ktor:ktor-client-cio:2.3.11\")\n")
            buildGradleSb.append("    implementation(\"io.ktor:ktor-client-content-negotiation:2.3.11\")\n")
            buildGradleSb.append("    implementation(\"io.ktor:ktor-serialization-kotlinx-json:2.3.11\")\n")
            buildGradleSb.append("    implementation(\"org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3\")\n")
            buildGradleSb.append("    testImplementation(\"org.junit.jupiter:junit-jupiter:5.10.0\")\n")
            buildGradleSb.append("    testImplementation(\"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0\")\n")
            buildGradleSb.append("    testRuntimeOnly(\"org.junit.platform:junit-platform-launcher\")\n")
            buildGradleSb.append("}\n\n")
            buildGradleSb.append("tasks.test {\n")
            buildGradleSb.append("    useJUnitPlatform()\n")
            buildGradleSb.append("}\n")

            writeToFile("${config.outputDir}/build.gradle.kts", buildGradleSb.toString())
        }

        if (!config.noGithubActions) {
            val ciSb = StringBuilder()
            ciSb.append("name: CI\n\n")
            ciSb.append("on:\n")
            ciSb.append("  push:\n")
            ciSb.append("    branches: [ main ]\n")
            ciSb.append("  pull_request:\n")
            ciSb.append("    branches: [ main ]\n\n")
            ciSb.append("jobs:\n")
            ciSb.append("  build:\n")
            ciSb.append("    runs-on: ubuntu-latest\n")
            ciSb.append("    steps:\n")
            ciSb.append("    - uses: actions/checkout@v4\n")
            ciSb.append("    - name: Set up JDK 21\n")
            ciSb.append("      uses: actions/setup-java@v4\n")
            ciSb.append("      with:\n")
            ciSb.append("        java-version: '21'\n")
            ciSb.append("        distribution: 'temurin'\n")
            ciSb.append("    - name: Build with Gradle\n")
            ciSb.append("      run: ./gradlew build\n")

            writeToFile("${config.outputDir}/.github/workflows/ci.yml", ciSb.toString())
        }
    }
}