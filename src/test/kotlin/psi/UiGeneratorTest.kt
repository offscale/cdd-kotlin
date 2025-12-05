package psi

import domain.SchemaDefinition
import domain.SchemaProperty
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.AfterAll

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
        val schema = SchemaDefinition(
            name = "UserProfile",
            type = "object",
            properties = mapOf(
                "name" to SchemaProperty("string"),
                "age" to SchemaProperty("integer")
            ),
            required = listOf("name")
        )

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
        val schema = SchemaDefinition(
            name = "Settings",
            type = "object",
            properties = mapOf(
                "label" to SchemaProperty("string"),
                "isEnabled" to SchemaProperty("boolean"),
                "score" to SchemaProperty("number")
            ),
            required = listOf("label")
        )

        val text = generator.generateForm("com.ui", schema).text

        assertTrue(text.contains("OutlinedTextField"))
        assertTrue(text.contains("Checkbox"))
        assertTrue(text.contains("KeyboardType.Number"))
    }

    @Test
    fun `generateForm creates Submit button and reconstructs object`() {
        val schema = SchemaDefinition(
            name = "Product",
            type = "object",
            properties = mapOf(
                "title" to SchemaProperty("string"),
                "price" to SchemaProperty("number")
            ),
            required = listOf("title", "price")
        )

        val text = generator.generateForm("com.ui", schema).text

        assertTrue(text.contains("Button("))
        assertTrue(text.contains("onSubmit("))
        assertTrue(text.contains("Product("))
        assertTrue(text.contains("price = price.toDoubleOrNull()"))
    }

    // --- Grid Tests ---

    @Test
    fun `generateGrid creates LazyColumn with Headers and Rows`() {
        val schema = SchemaDefinition(
            name = "User",
            type = "object",
            properties = mapOf(
                "name" to SchemaProperty("string"),
                "email" to SchemaProperty("string"),
                "isActive" to SchemaProperty("boolean")
            )
        )

        val file = generator.generateGrid("com.ui", schema)
        val text = file.text

        assertTrue(text.contains("fun UserGrid"))
        assertTrue(text.contains("text = \"Name\"")) // Direct param check
        assertTrue(text.contains("LazyColumn"))
        assertTrue(text.contains("items(sortedItems)"))
        assertTrue(text.contains("item.name.toString()"))
        assertTrue(text.contains("onItemClick(item)"))
    }

    @Test
    fun `generateGrid implements sorting`() {
        val schema = SchemaDefinition(
            name = "Order",
            type = "object",
            properties = mapOf(
                "id" to SchemaProperty("integer"),
                "amount" to SchemaProperty("number")
            )
        )

        val text = generator.generateGrid("com.ui", schema).text

        assertTrue(text.contains("var sortField by remember"))
        assertTrue(text.contains("when (sortField) {"))
        assertTrue(text.contains("\"id\" -> sorted.sortedBy { it.id }"))
    }

    @Test
    fun `generateGrid handles Arrays correctly`() {
        val schema = SchemaDefinition(
            name = "Task",
            type = "object",
            properties = mapOf(
                "tags" to SchemaProperty(type = "array", items = SchemaProperty("string"))
            )
        )
        val text = generator.generateGrid("com.ui", schema).text
        assertTrue(text.contains("item.tags.joinToString(\", \")"))
    }

    // --- Screen Tests (New) ---

    @Test
    fun `generateScreen connects API to Grid with state management`() {
        val schema = SchemaDefinition("User", "object")

        val file = generator.generateScreen(
            packageName = "com.app",
            screenName = "UserScreen",
            schema = schema,
            apiClassName = "UserApi",
            listOperationId = "getUsers"
        )
        val text = file.text

        // Definition
        assertTrue(text.contains("fun UserScreen(api: UserApi)"))

        // State
        assertTrue(text.contains("var items by remember { mutableStateOf<List<User>>(emptyList()) }"))
        assertTrue(text.contains("var isLoading by remember { mutableStateOf(true) }"))

        // API Call
        assertTrue(text.contains("LaunchedEffect(Unit)"))
        assertTrue(text.contains("items = api.getUsers()"))

        // UI Logic
        assertTrue(text.contains("CircularProgressIndicator()")) // Loading
        assertTrue(text.contains("Text(\"Error: \$error\")")) // Error
        assertTrue(text.contains("UserGrid(")) // Success -> Grid
        assertTrue(text.contains("items = items"))
    }
}
