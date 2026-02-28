cdd-kotlin
==========

[![License](https://img.shields.io/badge/license-Apache--2.0%20OR%20MIT-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI/CD](https://github.com/offscale/cdd-kotlin/workflows/CI/badge.svg)](https://github.com/offscale/cdd-kotlin/actions)
<!-- REPLACE WITH separate test and doc coverage badges that you generate in pre-commit hook -->

OpenAPI ↔ Kotlin. This is one compiler in a suite, all focussed on the same task: Compiler Driven Development (CDD).

Each compiler is written in its target language, is whitespace and comment sensitive, and has both an SDK and CLI.

The CLI—at a minimum—has:
- `cdd_kotlin --help`
- `cdd_kotlin --version`
- `cdd_kotlin from_openapi -i spec.json`
- `cdd_kotlin to_openapi -f path/to/code`
- `cdd_kotlin to_docs_json --no-imports --no-wrapping -i spec.json`

The goal of this project is to enable rapid application development without tradeoffs. Tradeoffs of Protocol Buffers / Thrift etc. are an untouchable "generated" directory and package, compile-time and/or runtime overhead. Tradeoffs of Java or JavaScript for everything are: overhead in hardware access, offline mode, ML inefficiency, and more. And neither of these alterantive approaches are truly integrated into your target system, test frameworks, and bigger abstractions you build in your app. Tradeoffs in CDD are code duplication (but CDD handles the synchronisation for you).

## 🚀 Capabilities

The `cdd-kotlin` compiler leverages a unified architecture to support various facets of API and code lifecycle management.

* **Compilation**:
  * **OpenAPI → Kotlin**: Generate idiomatic native models, network routes, client SDKs, database schemas, and boilerplate directly from OpenAPI (`.json` / `.yaml`) specifications.
  * **Kotlin → OpenAPI**: Statically parse existing Kotlin source code and emit compliant OpenAPI specifications.
* **AST-Driven & Safe**: Employs static analysis (Abstract Syntax Trees) instead of unsafe dynamic execution or reflection, allowing it to safely parse and emit code even for incomplete or un-compilable project states.
* **Seamless Sync**: Keep your docs, tests, database, clients, and routing in perfect harmony. Update your code, and generate the docs; or update the docs, and generate the code.

## 📦 Installation

Requires Kotlin 2.2+ and Java 17+.

To use the CLI directly from the source, clone the repository and use Gradle:

```bash
git clone https://github.com/offscale/cdd-kotlin.git
cd cdd-kotlin
./gradlew run --args="--help"
```

## 🛠 Usage

### Command Line Interface

Generate a Kotlin KMP project from an OpenAPI spec:
```bash
./gradlew run --args="from_openapi -i petstore.json -o ./my-client --clientName PetstoreClient"
```

Parse existing Kotlin code back into an OpenAPI spec:
```bash
./gradlew run --args="to_openapi -f ./my-client/composeApp/src/commonMain/kotlin --format json"
```

Merge updates from an OpenAPI spec directly into existing Kotlin code without destroying custom logic:
```bash
./gradlew run --args="merge_openapi -s updated_spec.json -d ./my-client/composeApp/src/commonMain/kotlin"
```

### Programmatic SDK / Library

You can integrate `cdd-kotlin` directly into your Kotlin applications or build scripts.

```kotlin
import cdd.openapi.OpenApiParser
import java.io.File

fun main() {
    val parser = OpenApiParser()
    val spec = parser.parseFile(File("petstore.json"))
    
    println("Parsed API: ${spec.info.title} v${spec.info.version}")
    println("Endpoints: ${spec.paths.size}")
}
```

## Design choices

`cdd-kotlin` uniquely leverages the **Kotlin Compiler PSI (Program Structure Interface)**. By analyzing the raw Abstract Syntax Tree (AST) rather than relying on reflection or compiled `.class` files, `cdd-kotlin` can seamlessly merge code, preserve original formatting, and retain manual comments/docstrings when synchronizing code with OpenAPI specs. 

This approach provides a "Round-Trip" guarantee: if you generate Kotlin from an OpenAPI spec, you can parse that Kotlin code right back into an identical OpenAPI spec. We also uniquely support scaffolding Jetpack Compose UI components (Forms, Grids, Screens) directly from your API models.

## 🏗 Supported Conversions for Kotlin

*(The boxes below reflect the features supported by this specific `cdd-kotlin` implementation)*

| Concept | Parse (From) | Emit (To) |
|---------|--------------|-----------|
| OpenAPI (JSON/YAML) | [✅] | [✅] |
| Kotlin Models / Structs / Types | [✅] | [✅] |
| Kotlin Server Routes / Endpoints | [✅] | [✅] |
| Kotlin API Clients / SDKs | [✅] | [✅] |
| Kotlin ORM / DB Schemas | [ ] | [ ] |
| Kotlin CLI Argument Parsers | [ ] | [ ] |
| Kotlin Docstrings / Comments | [✅] | [✅] |

---

## License

Licensed under either of

- Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE) or <https://www.apache.org/licenses/LICENSE-2.0>)
- MIT license ([LICENSE-MIT](LICENSE-MIT) or <https://opensource.org/licenses/MIT>)

at your option.

### Contribution

Unless you explicitly state otherwise, any contribution intentionally submitted
for inclusion in the work by you, as defined in the Apache-2.0 license, shall be
dual licensed as above, without any additional terms or conditions.