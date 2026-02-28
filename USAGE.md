# Usage

Currently, the tool acts as a library or a runner. The entry point is `src/main/kotlin/Main.kt`.

## Scaffolding a New Project

The `ScaffoldGenerator` builds a full project structure in the specified output directory.

```kotlin
fun main() {
    val generator = ScaffoldGenerator()
    val outputDir = File("my-kmp-app")

    generator.generate(
        outputDirectory = outputDir,
        projectName = "MyKmpApp",
        packageName = "com.example.app"
    )
}
```

## Generating Code Programmatically

You can generate specific layers using individual generators:

```kotlin
// 1. Define the Schema
val userSchema = SchemaDefinition(
    name = "User",
    type = "object",
    properties = mapOf(
        "username" to SchemaProperty("string"),
        "isActive" to SchemaProperty("boolean")
    )
)

// 2. Generate DTO
val dto = DtoGenerator().generateDto("com.app.model", userSchema)

// 3. Generate Compose Form
val form = UiGenerator().generateForm("com.app.ui", userSchema)
```

## Exporting OpenAPI (Kotlin -> OpenAPI)

You can assemble an OpenAPI document from Kotlin source parsing results and serialize it to JSON or YAML:

```kotlin
val schemas = DtoParser().parse(kotlinDtosSource)
val endpoints = NetworkParser().parse(ktorClientSource)

val definition = OpenApiAssembler().assemble(
    info = Info(title = "My API", version = "1.0.0"),
    schemas = schemas,
    endpoints = endpoints,
    servers = listOf(Server(url = "https://api.example.com")),
    extensions = mapOf("x-owner" to "core-team"),
    // Optional: lift shared path params/summary/description/servers into Path Items
    liftCommonPathMetadata = true
)

val json = OpenApiWriter().writeJson(definition)
val yaml = OpenApiWriter().writeYaml(definition)
```

## Schema Documents (OAS 3.2)

OpenAPI 3.2 allows standalone Schema Object documents (JSON Schema 2020-12) as OAD entries.
You can parse or write these directly:

```kotlin
// Parse any document into either OpenAPI or Schema
val doc = OpenApiParser().parseDocumentString("""{ "type": "string" }""")

// Provide a base URI when $self is missing so relative $ref values can be resolved
val docWithBase = OpenApiParser().parseDocumentString(
    source = """{ "openapi": "3.2.0", "info": { "title": "API", "version": "1.0" } }""",
    baseUri = "file:///apis/openapi.json"
)

// Parse a schema-only document (throws if it's an OpenAPI Object)
val schema = OpenApiParser().parseSchemaString("""{ "type": "string", "minLength": 2 }""")

// Write a standalone schema document
val jsonSchemaDoc = OpenApiWriter().writeSchema(schema)
```

## Multi-Document $ref Registry (No Network)

To resolve cross-document `$ref` values without network access, register additional documents in an
in-memory registry and pass it to the parser. Relative `$self`/`$id` values are resolved against the
provided `baseUri` for lookup:

```kotlin
val registry = OpenApiDocumentRegistry()

// Register shared components (indexed by $self and/or baseUri)
val shared = OpenApiParser().parseString(sharedJson)
registry.registerOpenApi(shared)

// Parse the main document with the registry
val main = OpenApiParser().parseString(mainJson, registry = registry)

// If refs are relative, provide a base URI when parsing and registering
val sharedBase = "https://example.com/root/shared/common.json"
registry.registerOpenApi(shared, baseUri = sharedBase)
val mainWithBase = OpenApiParser().parseString(
    mainJson,
    baseUri = "https://example.com/root/openapi.json",
    registry = registry
)
```
