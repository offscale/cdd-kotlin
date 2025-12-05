cdd-kotlin
==========

[![License: (Apache-2.0 OR MIT)](https://img.shields.io/badge/LICENSE-Apache--2.0%20OR%20MIT-orange)](LICENSE-APACHE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-blue)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Ktor](https://img.shields.io/badge/Ktor-2.3-orange)](https://ktor.io/)
[![CI](https://github.com/offscale/cdd-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/offscale/cdd-kotlin/actions/workflows/ci.yml)

**OpenAPI ↔ Kotlin Multiplatform**

`cdd-kotlin` is a bidirectional, distinct-framework code generator and analysis tool designed for the modern Kotlin
Multiplatform (KMP) ecosystem. It leverages the **Kotlin Compiler PSI (Program Structure Interface)** to not only
generate robust scaffolding, network layers, and UI components but also to **reverse-engineer** existing code back into
abstract specifications.

It bridges the gap between API contracts and full-stack KMP applications targeting Android, iOS, and Desktop.

## Key Features

This tool goes beyond simple template expansion by treating Kotlin source code as a queryable, editable syntax tree.

### 🏗️ Intelligent KMP Scaffolding

Bootstrapping a Multiplatform project is notoriously complex. `cdd-kotlin` automates the generation of a
production-ready infrastructure:

- **Gradle Version Catalogs:** Generates `libs.versions.toml` managing dependencies for Ktor, Compose, and Coroutines.
- **Target Configuration:** auto-configures `androidMain`, `iosMain`, and `desktopMain` source sets.
- **Manifests & Gradle Scripts:** Outputs valid `build.gradle.kts` files and `AndroidManifest.xml`.

### 🔄 Bidirectional Round-Trip Engineering

Reliability is ensured through "Round-Trip" verification. If the tool generates code from a spec, it can parse that code
back into the exact same spec.

- **Generator:** Transforms abstract Schema/Endpoint definitions into `@Serializable` DTOs and Ktor clients.
- **Parser:** Analyzes Kotlin AST to extract models and API definitions from existing source files.
- **Merger:** Smartly injects new properties or endpoints into *existing* files without overwriting manual logic or
  comments (uses PSI text range manipulation).

### 📱 Full-Stack Generation

`cdd-kotlin` covers the entire application layer:

- **Data Layer:** Generates Kotlin Data Classes with `kotlinx.serialization` and KDoc support.
- **Network Layer:** Generates strict `Ktor` interfaces, implementations, exception handling, and parameter
  serialization (Path, Query, Header, Body).
- **UI Layer (Jetpack Compose):** unique support for generating UI components based on data models:
    - **Forms:** Auto-generates Composable forms with state management, input validation, and object reconstruction.
    - **Grids:** Generates sortable data grids/tables.
    - **Screens:** Generates full screens that connect the Network layer to the UI layer with loading/error states.

## Architecture

The project is built around the `kotlin-compiler-embeddable` artifact to manipulate source code programmatically.

```mermaid
graph TD
%% ==========================================
%% 1. STYLE DEFINITIONS
%% ==========================================
%% Define specific classes for consistency
    classDef nodeSpec fill:#ea4335,stroke:#20344b,stroke-width:2px,color:#fff
    classDef nodeLogic fill:#57caff,stroke:#20344b,stroke-width:1px,color:#20344b
    classDef nodeIR fill:#ffffff,stroke:#34a853,stroke-width:3px,color:#20344b
    classDef nodeGen fill:#ffd427,stroke:#20344b,stroke-width:1px,color:#20344b
    classDef nodeRev fill:#5cdb6d,stroke:#20344b,stroke-width:1px,color:#20344b
    classDef nodeKt fill:#20344b,stroke:#20344b,stroke-width:1px,color:#fff

%% ==========================================
%% 2. NODE DEFINITIONS
%% ==========================================

%% TOP: OpenAPI
    OpenAPI(["<div style='line-height:1.2; padding:5px'><b>OpenAPI Spec</b><br/><span style='font-size:10px; font-family:monospace'>/users/#123;id#125;</span></div>"]):::nodeSpec

%% MIDDLE: IR (The Hub)
    IR{{"<div style='line-height:1.2; padding:5px'><b>Domain Models (IR)</b><br/><span style='font-size:10px; font-family:monospace'>EndpointDefinition</span></div>"}}:::nodeIR

%% LOGIC NODES (Left = Forward, Right = Reverse)
    Parser["<b>Spec Parser</b>"]:::nodeLogic
    Spec_Gen["<b>Spec Generator</b>"]:::nodeRev

    Generator["<b>PSI Generators</b>"]:::nodeGen
    Rev_Parser["<b>PSI Parsers</b>"]:::nodeRev

%% BOTTOM: KOTLIN SUBGRAPH
    subgraph KMP [<b>KOTLIN MULTIPLATFORM</b>]
    %% Keep these horizontal to create a 'footer' foundation
        direction LR

        Note_Net["<b>Network</b><br/><span style='font-size:9px'>Ktor Client</span>"]:::nodeKt
        Note_Data["<b>Data</b><br/><span style='font-size:9px'>Serializable</span>"]:::nodeKt
        Note_UI["<b>Compose</b><br/><span style='font-size:9px'>UI Screen</span>"]:::nodeKt
    end

%% ==========================================
%% 3. THE MAIN SPINE (High Weight Links)
%% ==========================================
%% Using thick visible links for the main flow

    OpenAPI ==> Parser
    Parser  ==> IR
    IR      ==> Generator

%% Generator feeds into the 3 Kotlin nodes
    Generator --> Note_Net
    Generator --> Note_Data
    Generator --> Note_UI

%% ==========================================
%% 4. THE REVERSE LOOP (Low Weight Links)
%% ==========================================
%% Dotted lines for feedback loop

%% Kotlin -> Reverse Parser
    Note_Data -.-> Rev_Parser
    Note_Net  -.-> Rev_Parser
    Note_UI   -.-> Rev_Parser

%% Reverse Parser -> IR
    Rev_Parser -.-> IR

%% IR -> Spec Generator -> OpenAPI
    IR -.-> Spec_Gen
    Spec_Gen -.-> OpenAPI
```

## Supported Mappings

### Types

The `TypeMappers` ensure correct conversion between abstract types and Kotlin specific implementations.

| Abstract  | Format  | Kotlin type              |
|-----------|---------|--------------------------|
| `string`  | -       | `String`                 |
| `integer` | `int32` | `Int`                    |
| `integer` | `int64` | `Long`                   |
| `number`  | -       | `Double`                 |
| `boolean` | -       | `Boolean`                |
| `array`   | -       | `List<T>`                |
| `object`  | -       | `Data Class` (Reference) |

### UI Generation

The `UiGenerator` maps data types to Compose components:

- **String** → `OutlinedTextField`
- **Integer/Number** → `OutlinedTextField` (with Number Keyboard)
- **Boolean** → `Checkbox` + `Row`
- **Lists** → `LazyColumn` (in Grids)

## Prerequisite

This project requires a JDK environment capable of running the Kotlin Compiler PSI.

- **JDK:** 17+
- **Kotlin:** 2.0+

## Usage

Currently, the tool acts as a library or a runner. The entry point is `src/main/kotlin/Main.kt`.

### Scaffolding a New Project

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

### Generating Code Programmatically

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

## Testing & Verification

The project contains a comprehensive test suite in `src/test/kotlin` split into three categories:

1. **PSI Tests (`psi/`):** Validates that generators produce valid Kotlin syntax and parsers correctly extract
   definitions from source code.
2. **Scaffold Tests (`scaffold/`):** Ensures all Gradle configurations, version catalogs, and directory structures are
   created correctly.
3. **Round-Trip Verification (`verification/RoundTripTest.kt`):**
    - **Process:** `Spec A` → `Generate Code` → `Parse Code` → `Spec B`.
    - **assertion:** `Spec A == Spec B`.
    - This ensures that no data is lost during the generation/parsing lifecycle.

Run tests via Gradle:

```bash
./gradlew test
```

## License

Licensed under either of

- Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE) or <https://www.apache.org/licenses/LICENSE-2.0>)
- MIT license ([LICENSE-MIT](LICENSE-MIT) or <https://opensource.org/licenses/MIT>)

at your option.
