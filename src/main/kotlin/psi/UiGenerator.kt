package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.jetbrains.kotlin.psi.KtFile

/**
 * Generates Jetpack Compose / Compose Multiplatform UI code for a given Schema.
 * Creates Forms, Grids, and full Screen controllers connecting to APIs.
 */
class UiGenerator {

    private val psiFactory = PsiInfrastructure.createPsiFactory()

    /**
     * Generates a Kotlin file containing the Composable Form.
     */
    fun generateForm(packageName: String, schema: SchemaDefinition): KtFile {
        val className = schema.name
        val formName = "${className}Form"

        val imports = """
            package $packageName
            
            import androidx.compose.foundation.layout.*
            import androidx.compose.foundation.text.KeyboardOptions
            import androidx.compose.material.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.text.input.KeyboardType
            import androidx.compose.ui.unit.dp
        """.trimIndent()

        val stateDefinitions = schema.properties.entries.joinToString("\n    ") { (name, prop) ->
            val stateInit = "initialState?.$name"
            val fallback = if (isBoolean(prop)) "false" else "\"\""
            val conversion = if (isBoolean(prop) || prop.type == "string") "" else ".toString()"

            "var $name by remember { mutableStateOf($stateInit$conversion ?: $fallback) }"
        }

        val inputFields = schema.properties.entries.joinToString("\n\n        ") { (name, prop) ->
            generateInput(name, prop, schema.required.contains(name))
        }

        val constructorArgs = schema.properties.entries.joinToString(",\n                                    ") { (name, prop) ->
            val value = when {
                prop.type == "integer" && prop.format == "int64" -> "$name.toLongOrNull() ?: 0L"
                prop.type == "integer" -> "$name.toIntOrNull() ?: 0"
                prop.type == "number" -> "$name.toDoubleOrNull() ?: 0.0"
                prop.type == "boolean" -> name
                else -> name
            }
            "$name = $value"
        }

        val content = """
            $imports
            
            @Composable
            fun $formName(
                initialState: $className? = null,
                onSubmit: ($className) -> Unit
            ) {
                // Form State
                $stateDefinitions
            
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    $inputFields
            
                    Button(
                        onClick = {
                            onSubmit(
                                $className(
                                    $constructorArgs
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Submit")
                    }
                }
            }
        """.trimIndent()

        return psiFactory.createFile("$formName.kt", content)
    }

    /**
     * Generates a Kotlin file containing the Composable Data Grid.
     */
    fun generateGrid(packageName: String, schema: SchemaDefinition): KtFile {
        val className = schema.name
        val gridName = "${className}Grid"

        val imports = """
            package $packageName
            
            import androidx.compose.foundation.clickable
            import androidx.compose.foundation.layout.*
            import androidx.compose.foundation.lazy.LazyColumn
            import androidx.compose.foundation.lazy.items
            import androidx.compose.material.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.draw.alpha
            import androidx.compose.ui.text.font.FontWeight
            import androidx.compose.ui.unit.dp
        """.trimIndent()

        val validProps = schema.properties.filter {
            val type = it.value.type
            type != "object" && it.value.ref == null
        }

        val headerCells = validProps.keys.joinToString("\n                        ") { name ->
            val label = name.replaceFirstChar { it.uppercase() }
            """
            Text(
                text = "$label",
                modifier = Modifier.weight(1f).clickable {
                    if (sortField == "$name") {
                        sortAscending = !sortAscending
                    } else {
                        sortField = "$name"
                        sortAscending = true
                    }
                },
                fontWeight = FontWeight.Bold
            )
            """.trimIndent()
        }

        val rowCells = validProps.map { (name, prop) ->
            val displayCode = when (prop.type) {
                "array" -> "item.$name.joinToString(\", \")"
                else -> "item.$name.toString()"
            }
            """
            Text(
                text = $displayCode,
                modifier = Modifier.weight(1f)
            )
            """.trimIndent()
        }.joinToString("\n                            ")

        val sortCases = validProps.mapNotNull { (name, prop) ->
            if (prop.type == "array") return@mapNotNull null
            "\"$name\" -> sorted.sortedBy { it.$name }"
        }.joinToString("\n                        ")

        val content = """
            $imports
            
            @Composable
            fun $gridName(
                items: List<$className>,
                onItemClick: ($className) -> Unit
            ) {
                var sortField by remember { mutableStateOf<String?>(null) }
                var sortAscending by remember { mutableStateOf(true) }
            
                val sortedItems = remember(items, sortField, sortAscending) {
                    val sorted = items
                    val result = when (sortField) {
                        $sortCases
                        else -> sorted
                    }
                    if (sortAscending) result else result.reversed()
                }
            
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        $headerCells
                    }
                    
                    Divider()
            
                    LazyColumn {
                        items(sortedItems) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onItemClick(item) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                $rowCells
                            }
                            Divider(modifier = Modifier.alpha(0.5f))
                        }
                    }
                }
            }
        """.trimIndent()

        return psiFactory.createFile("$gridName.kt", content)
    }

    /**
     * Generates a Full Screen Composable that fetches data from an API and displays it in a Grid.
     *
     * @param packageName Package Name.
     * @param screenName Name of the screen (e.g. "UserScreen").
     * @param schema The domain model schema (e.g. User).
     * @param apiClassName The API Class name (e.g. "UserApi").
     * @param listOperationId The method name on the API to fetch the list (e.g. "getUsers").
     */
    fun generateScreen(
        packageName: String,
        screenName: String,
        schema: SchemaDefinition,
        apiClassName: String,
        listOperationId: String
    ): KtFile {
        val modelName = schema.name
        val gridName = "${modelName}Grid"

        val content = """
            package $packageName
            
            import androidx.compose.foundation.layout.Box
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.material.Button
            import androidx.compose.material.CircularProgressIndicator
            import androidx.compose.material.Text
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            
            @Composable
            fun $screenName(api: $apiClassName) {
                var items by remember { mutableStateOf<List<$modelName>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                
                // Fetch Data Effect
                LaunchedEffect(Unit) {
                    try {
                        items = api.$listOperationId()
                        isLoading = false
                    } catch (e: Exception) {
                        error = e.message ?: "Unknown error"
                        isLoading = false
                    }
                }
            
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator()
                        }
                        error != null -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Error: ${'$'}error")
                                Button(onClick = { 
                                    isLoading = true
                                    error = null
                                    // Retry logic would go here, requiring a restart of LaunchedEffect or separate function
                                }) {
                                    Text("Retry")
                                }
                            }
                        }
                        else -> {
                            $gridName(
                                items = items,
                                onItemClick = { /* Handle Click */ }
                            )
                        }
                    }
                }
            }
        """.trimIndent()

        return psiFactory.createFile("$screenName.kt", content)
    }

    private fun generateInput(name: String, prop: SchemaProperty, isRequired: Boolean): String {
        val label = name.replaceFirstChar { it.uppercase() }
        val modifier = "Modifier.fillMaxWidth()"

        return when (prop.type) {
            "boolean" -> """
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = $name,
                        onCheckedChange = { $name = it }
                    )
                    Text("$label")
                }
            """.trimIndent()
            "integer", "number" -> """
                OutlinedTextField(
                    value = $name,
                    onValueChange = { $name = it },
                    label = { Text("$label") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = $modifier,
                    singleLine = true
                )
            """.trimIndent()
            else -> {
                val requiredMarker = if (isRequired) "*" else ""
                """
                OutlinedTextField(
                    value = $name,
                    onValueChange = { $name = it },
                    label = { Text("$label$requiredMarker") },
                    modifier = $modifier,
                    singleLine = true
                )
                """.trimIndent()
            }
        }
    }

    private fun isBoolean(prop: SchemaProperty): Boolean = prop.type == "boolean"
}
