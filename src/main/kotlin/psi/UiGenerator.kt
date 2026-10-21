/** Jetpack Compose / Compose Multiplatform form, grid, and screen controller generator. */
package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.jetbrains.kotlin.psi.KtFile

/**
 * Generates Jetpack Compose / Compose Multiplatform UI code for a given Schema. Creates Forms,
 * Grids, and full Screen controllers connecting to APIs. Updated to handle SchemaProperty.types
 * set.
 */
class UiGenerator {

  private val psiFactory = PsiInfrastructure.createPsiFactory()

  /**
   * Generates a Kotlin file containing the Composable Form.
   *
   * @param packageName Target package name.
   * @param schema The [SchemaDefinition] to build a form for.
   * @return The generated [KtFile].
   */
  fun generateForm(packageName: String, schema: SchemaDefinition): KtFile {
    val className = schema.safeName
    val formName = "${className}Form"
    val modelsImport = resolveSiblingImport(packageName, "models")

    val imports =
        """ 
            package $packageName
            
            import androidx.compose.foundation.layout.* 
            import androidx.compose.foundation.text.KeyboardOptions
            import androidx.compose.material.* 
            import androidx.compose.runtime.* 
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.text.input.KeyboardType
            import androidx.compose.ui.unit.dp$modelsImport
        """
            .trimIndent()

    val stateDefinitions =
        schema.properties.entries.joinToString("\n    ") { (name, prop) ->
          val safeName = escapeKotlinKeyword(name)
          val stateInit = "initialState?.$safeName"
          val mappedType = TypeMappers.mapType(prop)
          val isBool = mappedType == "Boolean"
          val isString = mappedType == "String"
          val fallback = if (isBool) "false" else "\"\""
          val conversion = if (isBool || isString) "" else ".toString()"

          "var $safeName by remember { mutableStateOf($stateInit$conversion ?: $fallback) }"
        }

    val inputFields =
        schema.properties.entries.joinToString("\n\n        ") { (name, prop) ->
          generateInput(name, prop, schema.required.contains(name))
        }

    val constructorArgs =
        schema.properties.entries.joinToString(",\n                                    ") {
            (name, prop) ->
          val safeName = escapeKotlinKeyword(name)
          val isNullable = prop.types.contains("null") || !schema.required.contains(name)
          val value = buildConstructorArgExpression(safeName, prop, isNullable)
          "$safeName = $value"
        }

    val content =
        """ 
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
        """
            .trimIndent()

    return psiFactory.createFile("$formName.kt", content)
  }

  /**
   * Generates a Kotlin file containing the Composable Data Grid.
   *
   * @param packageName Target package name.
   * @param schema The [SchemaDefinition] to build a grid for.
   * @return The generated [KtFile].
   */
  fun generateGrid(packageName: String, schema: SchemaDefinition): KtFile {
    val className = schema.safeName
    val gridName = "${className}Grid"
    val modelsImport = resolveSiblingImport(packageName, "models")

    val imports =
        """ 
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
            import androidx.compose.ui.unit.dp$modelsImport
        """
            .trimIndent()

    val validProps =
        schema.properties.filter {
          // Exclude object refs for simplcity in grid
          !it.value.types.contains("object") && it.value.ref == null
        }

    val headerCells =
        validProps.keys.joinToString("\n                        ") { name ->
          val label = capitalizeName(name)
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
            """
              .trimIndent()
        }

    val rowCells =
        validProps
            .map { (name, prop) ->
              val safeName = escapeKotlinKeyword(name)
              val isNullable = prop.types.contains("null") || !schema.required.contains(name)
              val displayCode =
                  when {
                    prop.types.contains("array") && isNullable ->
                        "item.$safeName?.joinToString(\", \").orEmpty()"
                    prop.types.contains("array") -> "item.$safeName.joinToString(\", \")"
                    else -> "item.$safeName.toString()"
                  }
              """ 
            Text( 
                text = $displayCode, 
                modifier = Modifier.weight(1f) 
            ) 
            """
                  .trimIndent()
            }
            .joinToString("\n                            ")

    val sortCases =
        validProps
            .mapNotNull { (name, prop) ->
              if (prop.types.contains("array")) return@mapNotNull null
              val safeName = escapeKotlinKeyword(name)
              val mappedType = TypeMappers.mapType(prop)
              val selector =
                  if (isComparableType(mappedType)) "it.$safeName" else "it.$safeName.toString()"
              "\"$name\" -> sorted.sortedBy { $selector }"
            }
            .joinToString("\n                        ")

    val content =
        """ 
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
        """
            .trimIndent()

    return psiFactory.createFile("$gridName.kt", content)
  }

  /**
   * Generates a Full Screen Composable that fetches data from an API and displays it in a Grid.
   *
   * @param packageName Target package name.
   * @param screenName Composable screen function name.
   * @param schema The [SchemaDefinition] displayed on the screen.
   * @param apiClassName Name of the generated API client class.
   * @param listOperationId Name of the list endpoint method on [apiClassName].
   * @return The generated [KtFile].
   */
  fun generateScreen(
      packageName: String,
      screenName: String,
      schema: SchemaDefinition,
      apiClassName: String,
      listOperationId: String
  ): KtFile {
    val modelName = schema.safeName
    val gridName = "${modelName}Grid"
    val modelsImport = resolveSiblingImport(packageName, "models")
    val apiImport = resolveSiblingImport(packageName, "api")

    val content =
        """ 
            package $packageName
            
            import androidx.compose.foundation.layout.Box
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.material.Button
            import androidx.compose.material.CircularProgressIndicator
            import androidx.compose.material.Text
            import androidx.compose.runtime.* 
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier$modelsImport$apiImport
            
            @Composable
            fun $screenName(api: $apiClassName) { 
                var items by remember { mutableStateOf<List<$modelName>>(emptyList()) } 
                var selectedItem by remember { mutableStateOf<$modelName?>(null) }
                var isLoading by remember { mutableStateOf(true) } 
                var error by remember { mutableStateOf<String?>(null) } 
                var retryCount by remember { mutableStateOf(0) }
                
                // Fetch Data Effect
                LaunchedEffect(Unit, retryCount) { 
                    api.$listOperationId().fold(
                        onSuccess = { 
                            items = it
                            isLoading = false
                        },
                        onFailure = { e -> 
                            error = e.message ?: "Unknown error" 
                            isLoading = false
                        }
                    )
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
                                    retryCount++
                                }) { 
                                    Text("Retry") 
                                } 
                            } 
                        } 
                        else -> { 
                            $gridName( 
                                items = items, 
                                onItemClick = { selectedItem = it } 
                            ) 
                        } 
                    } 
                } 
            } 
        """
            .trimIndent()

    return psiFactory.createFile("$screenName.kt", content)
  }

  private fun buildConstructorArgExpression(
      safeName: String,
      prop: SchemaProperty,
      isNullable: Boolean
  ): String {
    return when (val mappedType = TypeMappers.mapType(prop)) {
      "Long" -> "$safeName.toLongOrNull() ?: 0L"
      "Int" -> "$safeName.toIntOrNull() ?: 0"
      "Float" -> "$safeName.toFloatOrNull() ?: 0.0f"
      "Double" -> "$safeName.toDoubleOrNull() ?: 0.0"
      "Boolean" -> safeName
      "String" -> safeName
      "Instant" ->
          if (isNullable) {
            "runCatching { kotlinx.datetime.Instant.parse($safeName) }.getOrNull()"
          } else {
            "runCatching { kotlinx.datetime.Instant.parse($safeName) }.getOrElse { kotlinx.datetime.Instant.fromEpochMilliseconds(0) }"
          }
      "LocalDate" ->
          if (isNullable) {
            "runCatching { kotlinx.datetime.LocalDate.parse($safeName) }.getOrNull()"
          } else {
            "runCatching { kotlinx.datetime.LocalDate.parse($safeName) }.getOrElse { kotlinx.datetime.LocalDate.fromEpochDays(0) }"
          }
      "ByteArray" -> "$safeName.encodeToByteArray()"
      "List<String>" ->
          "if ($safeName.isBlank()) emptyList() else $safeName.split(\",\").map { it.trim() }"
      "kotlinx.serialization.json.JsonElement" ->
          "kotlinx.serialization.json.JsonPrimitive($safeName)"
      "Nothing" -> "return@Button"
      else ->
          when {
            mappedType.startsWith("List<") -> "initialState?.$safeName ?: emptyList()"
            mappedType.startsWith("Map<") -> "initialState?.$safeName ?: emptyMap()"
            isNullable -> "initialState?.$safeName"
            else -> "initialState?.$safeName ?: return@Button"
          }
    }
  }

  private fun isComparableType(mappedType: String): Boolean =
      when (mappedType) {
        "String",
        "Int",
        "Long",
        "Double",
        "Float",
        "Boolean",
        "Instant",
        "LocalDate" -> true
        else -> false
      }

  private fun resolveSiblingImport(packageName: String, siblingSubpackage: String): String {
    val targetPkg =
        when {
          packageName == "ui" -> siblingSubpackage
          packageName.endsWith(".ui") -> "${packageName.removeSuffix(".ui")}.$siblingSubpackage"
          packageName.endsWith(".$siblingSubpackage") -> null
          packageName.isNotBlank() -> "$packageName.$siblingSubpackage"
          else -> null
        }
    return if (targetPkg != null) "\n            import $targetPkg.*" else ""
  }

  private fun generateInput(name: String, prop: SchemaProperty, isRequired: Boolean): String {
    val label = capitalizeName(name)
    val safeName = escapeKotlinKeyword(name)
    val modifier = "Modifier.fillMaxWidth()"

    return when {
      prop.types.contains("boolean") ->
          """ 
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { 
                    Checkbox( 
                        checked = $safeName, 
                        onCheckedChange = { $safeName = it } 
                    ) 
                    Text("$label") 
                } 
            """
              .trimIndent()
      prop.types.any { it == "integer" || it == "number" } ->
          """ 
                OutlinedTextField( 
                    value = $safeName, 
                    onValueChange = { $safeName = it }, 
                    label = { Text("$label") }, 
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                    modifier = $modifier, 
                    singleLine = true
                ) 
            """
              .trimIndent()
      else -> {
        val requiredMarker = if (isRequired) "*" else ""
        """ 
                OutlinedTextField( 
                    value = $safeName, 
                    onValueChange = { $safeName = it }, 
                    label = { Text("$label$requiredMarker") }, 
                    modifier = $modifier, 
                    singleLine = true
                ) 
                """
            .trimIndent()
      }
    }
  }
}
