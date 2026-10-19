# `cdd-kotlin` Compiler Architecture

`cdd-kotlin` is a Kotlin Multiplatform compiler toolchain (`commonMain`, `jvmMain`, `wasmMain`) that translates bidirectionally between **OpenAPI 3.2.0 / Swagger 2.0 / Arazzo 1.1.0** specifications and idiomatic **Kotlin** source code (Ktor Client SDKs, Clikt CLIs, Jetpack Compose Multiplatform UI components, Ktor Netty Servers, JetBrains Exposed ORM DAOs, and Kotlin Faker seeders).

---

## High-Level Source Layout

```text
src/
├── commonMain/kotlin/
│   ├── Main.kt                  # CLI entrypoint and subcommand/help router
│   ├── Platform.kt              # Multiplatform expect declarations (I/O, env, PSI hooks)
│   ├── arazzo/                  # Arazzo 1.1.0 parser, validator, writer, registry, runtime expressions
│   ├── domain/                  # OpenAPI 3.2.0, Arazzo 1.1.0, JSON Schema 2020-12, and CddError models
│   ├── openapi/                 # OpenAPI/Swagger parser (JSON & YAML), validator, writer, assembler
│   ├── org/cdd/                 # Programmatic SDK (CddGenerator, CddCli, SyncCli, Config, MCP)
│   ├── psi/                     # Multiplatform KotlinSourceBuilder, ReferenceResolver, TypeMappers
│   └── scaffold/                # Gradle KMP, Compose Multiplatform, Ktor Server & CI project templates
├── main/kotlin/                 # JVM target (Kotlin Compiler Embeddable PSI engine)
│   ├── PlatformJvm.kt           # JVM actual implementations
│   ├── openapi/                 # JVM file writer extensions
│   └── psi/                     # PSI parsers, surgical CST mergers, and generators (DTO, Network, DAO, DB, Seeder, IdP, Webhooks, UI, Arazzo)
└── wasmMain/kotlin/             # WASI Preview 1 target (wasmWasi)
    ├── PlatformWasm.kt          # Direct WASI Preview 1 syscall bindings
    └── org/cdd/wasm/Main.kt     # @WasmExport entrypoints
```

---

## Compilation Pipeline

1. **Specification Ingestion & Normalization (`openapi/`, `arazzo/`)**:
   - `OpenApiParser` and `YamlToJsonElementConverter` parse JSON and YAML documents into strongly typed `OpenApiDefinition` domain models, automatically upgrading Swagger 2.0 and OpenAPI 3.0/3.1 constructs to OpenAPI 3.2.0.
   - `ArazzoParser` and `ArazzoValidator` parse and validate Arazzo 1.1.0 multi-step workflows, `$` runtime expressions (`RuntimeExpressionEngine`), and step criteria (`ArazzoCriterionEvaluator`).
2. **Concrete Syntax Tree (PSI) Parsing & Surgical Merging (`src/main/kotlin/psi/`)**:
   - `PsiInfrastructure` initializes a headless `KotlinCoreEnvironment` and `KtPsiFactory` from `org.jetbrains.kotlin:kotlin-compiler-embeddable`.
   - `DtoParser`, `NetworkParser`, and `WorkflowPsiParser` inspect `KtFile`, `KtClass`, `KtNamedFunction`, annotations (`@Serializable`, `@SerialName`), and structured KDoc tags (`@arazzo-workflow`, `@arazzo-step`, `@openapi-*`) without reflection or code execution.
   - `DtoMerger` and `WorkflowSdkMerger` locate exact PSI token offsets (such as primary constructor closing parentheses) to inject new schema fields or workflow steps while preserving existing comments, formatting, and user-authored helper functions.
3. **Multi-Target Emission (`psi/` & `scaffold/`)**:
   - **Client SDK & CLI**: `DtoGenerator`, `NetworkGenerator`, `UiGenerator`, and `WorkflowSdkGenerator`.
   - **Server & ORM**: `ServerMainGenerator`, `DaoGenerator` (JetBrains Exposed), `DbConnectionGenerator`, `SeederGenerator` (`kotlin-faker`), `IdpGenerator`, `WebhookGenerator`, and `WorkflowServerGenerator`.
   - **MCP Bridge**: `McpClientGenerator` and `McpPeer`.
4. **Error Handling (`domain/CddError.kt`)**:
   - All parsing, validation, PSI merging, and file I/O operations return `kotlin.Result<T>` backed by a unified sealed `CddError` hierarchy.
