/** Unit and integration tests in [UiGeneratorTest]. */
package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UiGeneratorTest {

  private val generator = UiGenerator()

  @AfterAll
  fun tearDown() {
    PsiInfrastructure.dispose()
  }

  // --- Form Tests ---

  @Test
  fun `generateForm creates composable with correct state mappings`() {
    val schema =
        SchemaDefinition(
            name = "UserProfile",
            type = "object",
            properties =
                mapOf("name" to SchemaProperty("string"), "age" to SchemaProperty("integer")),
            required = listOf("name"))

    val file = generator.generateForm("com.ui", schema)
    val text = file.text

    assertTrue(text.contains("@Composable"))
    assertTrue(text.contains("fun UserProfileForm"))
    assertTrue(text.contains("var name by remember { mutableStateOf"))
    assertTrue(text.contains("var age by remember { mutableStateOf"))
    assertTrue(text.contains("initialState?.age.toString()"))
  }

  @Test
  fun `generateForm creates TextFields and Checkboxes`() {
    val schema =
        SchemaDefinition(
            name = "Settings",
            type = "object",
            properties =
                mapOf(
                    "label" to SchemaProperty("string"),
                    "isEnabled" to SchemaProperty("boolean"),
                    "score" to SchemaProperty("number")),
            required = listOf("label"))

    val text = generator.generateForm("com.ui", schema).text

    assertTrue(text.contains("OutlinedTextField"))
    assertTrue(text.contains("Checkbox"))
    assertTrue(text.contains("KeyboardType.Number"))
  }

  @Test
  fun `generateForm creates Submit button and reconstructs object`() {
    val schema =
        SchemaDefinition(
            name = "Product",
            type = "object",
            properties =
                mapOf(
                    "title" to SchemaProperty("string"),
                    "optText" to SchemaProperty(types = setOf("string", "null")),
                    "bigId" to SchemaProperty("integer", format = "int64"),
                    "price" to SchemaProperty("number"),
                    "weight" to SchemaProperty("number", format = "float"),
                    "createdAt" to SchemaProperty("string", format = "date-time"),
                    "updatedAt" to SchemaProperty("string", format = "date-time"),
                    "releaseDate" to SchemaProperty("string", format = "date"),
                    "expiryDate" to SchemaProperty("string", format = "date"),
                    "avatar" to SchemaProperty("string", format = "byte"),
                    "tags" to SchemaProperty("array", items = SchemaProperty("string")),
                    "scores" to SchemaProperty("array", items = SchemaProperty("integer")),
                    "attrs" to
                        SchemaProperty("object", additionalProperties = SchemaProperty("string")),
                    "rawJson" to SchemaProperty(booleanSchema = true),
                    "neverVal" to SchemaProperty(booleanSchema = false),
                    "reqCategory" to SchemaProperty(ref = "#/components/schemas/Category"),
                    "optCategory" to SchemaProperty(ref = "#/components/schemas/Category")),
            required =
                listOf(
                    "title",
                    "optText",
                    "price",
                    "createdAt",
                    "releaseDate",
                    "reqCategory",
                    "neverVal"))

    val text = generator.generateForm("com.ui", schema).text

    assertTrue(text.contains("import com.models.*"))
    assertTrue(text.contains("Button("))
    assertTrue(text.contains("onSubmit("))
    assertTrue(text.contains("Product("))
    assertTrue(text.contains("price = price.toDoubleOrNull()"))
    assertTrue(text.contains("weight = weight.toFloatOrNull() ?: 0.0f"))
    assertTrue(text.contains("bigId = bigId.toLongOrNull() ?: 0L"))
    assertTrue(
        text.contains(
            "createdAt = runCatching { kotlinx.datetime.Instant.parse(createdAt) }.getOrElse { kotlinx.datetime.Instant.fromEpochMilliseconds(0) }"))
    assertTrue(
        text.contains(
            "updatedAt = runCatching { kotlinx.datetime.Instant.parse(updatedAt) }.getOrNull()"))
    assertTrue(
        text.contains(
            "releaseDate = runCatching { kotlinx.datetime.LocalDate.parse(releaseDate) }.getOrElse { kotlinx.datetime.LocalDate.fromEpochDays(0) }"))
    assertTrue(
        text.contains(
            "expiryDate = runCatching { kotlinx.datetime.LocalDate.parse(expiryDate) }.getOrNull()"))
    assertTrue(text.contains("avatar = avatar.encodeToByteArray()"))
    assertTrue(
        text.contains(
            "tags = if (tags.isBlank()) emptyList() else tags.split(\",\").map { it.trim() }"))
    assertTrue(text.contains("scores = initialState?.scores ?: emptyList()"))
    assertTrue(text.contains("attrs = initialState?.attrs ?: emptyMap()"))
    assertTrue(text.contains("rawJson = kotlinx.serialization.json.JsonPrimitive(rawJson)"))
    assertTrue(text.contains("neverVal = return@Button"))
    assertTrue(text.contains("reqCategory = initialState?.reqCategory ?: return@Button"))
    assertTrue(text.contains("optCategory = initialState?.optCategory"))
  }

  // --- Grid Tests ---

  @Test
  fun `generateGrid creates LazyColumn with Headers and Rows`() {
    val schema =
        SchemaDefinition(
            name = "User",
            type = "object",
            properties =
                mapOf(
                    "name" to SchemaProperty("string"),
                    "email" to SchemaProperty("string"),
                    "isActive" to SchemaProperty("boolean"),
                    "nestedObj" to SchemaProperty("object"),
                    "refProp" to
                        SchemaProperty(types = setOf("string"), ref = "#/components/schemas/Ref")))

    val file = generator.generateGrid("ui", schema)
    val text = file.text

    assertTrue(text.contains("import models.*"))
    assertTrue(text.contains("fun UserGrid"))
    assertTrue(text.contains("text = \"Name\"")) // Direct param check
    assertTrue(text.contains("LazyColumn"))
    assertTrue(text.contains("items(sortedItems)"))
    assertTrue(text.contains("item.name.toString()"))
    assertTrue(text.contains("onItemClick(item)"))
  }

  @Test
  fun `generateGrid implements sorting`() {
    val schema =
        SchemaDefinition(
            name = "Order",
            type = "object",
            properties =
                mapOf(
                    "id" to SchemaProperty("integer"),
                    "bigId" to SchemaProperty("integer", format = "int64"),
                    "amount" to SchemaProperty("number"),
                    "ratio" to SchemaProperty("number", format = "float"),
                    "shipDate" to SchemaProperty("string", format = "date-time"),
                    "birthDate" to SchemaProperty("string", format = "date"),
                    "blob" to SchemaProperty("string", format = "byte")))

    val text = generator.generateGrid("com.models", schema).text

    assertTrue(text.contains("var sortField by remember"))
    assertTrue(text.contains("when (sortField) {"))
    assertTrue(text.contains("\"id\" -> sorted.sortedBy { it.id }"))
    assertTrue(text.contains("\"bigId\" -> sorted.sortedBy { it.bigId }"))
    assertTrue(text.contains("\"amount\" -> sorted.sortedBy { it.amount }"))
    assertTrue(text.contains("\"ratio\" -> sorted.sortedBy { it.ratio }"))
    assertTrue(text.contains("\"shipDate\" -> sorted.sortedBy { it.shipDate }"))
    assertTrue(text.contains("\"birthDate\" -> sorted.sortedBy { it.birthDate }"))
    assertTrue(text.contains("\"blob\" -> sorted.sortedBy { it.blob.toString() }"))
  }

  @Test
  fun `generateGrid handles Arrays correctly`() {
    val schema =
        SchemaDefinition(
            name = "Task",
            type = "object",
            properties =
                mapOf(
                    "reqTags" to SchemaProperty(type = "array", items = SchemaProperty("string")),
                    "nullTags" to
                        SchemaProperty(
                            types = setOf("array", "null"), items = SchemaProperty("string")),
                    "optTags" to SchemaProperty(type = "array", items = SchemaProperty("string"))),
            required = listOf("reqTags", "nullTags"))
    val text = generator.generateGrid("", schema).text
    assertTrue(text.contains("item.reqTags.joinToString(\", \")"))
    assertTrue(text.contains("item.nullTags?.joinToString(\", \").orEmpty()"))
    assertTrue(text.contains("item.optTags?.joinToString(\", \").orEmpty()"))
  }

  // --- Screen Tests (New) ---

  @Test
  fun `generateScreen connects API to Grid with state management`() {
    val schema = SchemaDefinition(name = "User", type = "object")

    val file =
        generator.generateScreen(
            packageName = "com.app",
            screenName = "UserScreen",
            schema = schema,
            apiClassName = "UserApi",
            listOperationId = "getUsers")
    val text = file.text

    // Definition
    assertTrue(text.contains("fun UserScreen(api: UserApi)"))

    // State
    assertTrue(text.contains("var items by remember { mutableStateOf<List<User>>(emptyList()) }"))
    assertTrue(text.contains("var isLoading by remember { mutableStateOf(true) }"))

    // API Call
    assertTrue(text.contains("LaunchedEffect(Unit, retryCount)"))
    assertTrue(text.contains("api.getUsers().fold("))
    assertTrue(text.contains("items = it"))
    assertTrue(text.contains("retryCount++"))
    assertTrue(text.contains("onItemClick = { selectedItem = it }"))

    // UI Logic
    assertTrue(text.contains("CircularProgressIndicator()")) // Loading
    assertTrue(text.contains("Text(\"Error: \$error\")")) // Error
    assertTrue(text.contains("UserGrid(")) // Success -> Grid
    assertTrue(text.contains("items = items"))
  }
}
