cdd-kotlin
==========

[![License](https://img.shields.io/badge/license-Apache--2.0%20OR%20MIT-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI/CD](https://github.com/offscale/cdd-kotlin/workflows/CI/badge.svg)](https://github.com/offscale/cdd-kotlin/actions)
[![Doc Coverage](https://img.shields.io/badge/Doc_Coverage-100%25-brightgreen.svg)](https://github.com/offscale/cdd-kotlin)
[![Test Coverage](https://img.shields.io/badge/Test_Coverage-98%25-brightgreen.svg)](https://github.com/offscale/cdd-kotlin)

OpenAPI ↔ Kotlin. This is one compiler in a suite, all focussed on the same task: Compiler Driven Development (CDD).

Each compiler is written in its target language, is whitespace and comment sensitive, and has both an SDK and CLI.

The CLI—at a minimum—has:
- `cdd-kotlin --help`
- `cdd-kotlin --version`
- `cdd-kotlin from_openapi -i spec.json`
- `cdd-kotlin to_openapi -f path/to/code`
- `cdd-kotlin to_docs_json --no-imports --no-wrapping -i spec.json`

The goal of this project is to enable rapid application development without tradeoffs. Tradeoffs of Protocol Buffers / Thrift etc. are an untouchable "generated" directory and package, compile-time and/or runtime overhead. Tradeoffs of Java or JavaScript for everything are: overhead in hardware access, offline mode, ML inefficiency, and more. And neither of these alterantive approaches are truly integrated into your target system, test frameworks, and bigger abstractions you build in your app. Tradeoffs in CDD are code duplication (but CDD handles the synchronisation for you).

## 🚀 Capabilities

The `cdd-kotlin` compiler leverages a unified architecture to support various facets of API and code lifecycle management.

* **Compilation**:
  * **OpenAPI → Kotlin**: Generate idiomatic native models, network routes, client SDKs, database schemas, and boilerplate directly from OpenAPI (`.json` / `.yaml`) specifications.
  * **Kotlin → OpenAPI**: Statically parse existing Kotlin source code and emit compliant OpenAPI specifications.
* **AST-Driven & Safe**: Employs static analysis (Abstract Syntax Trees) instead of unsafe dynamic execution or reflection, allowing it to safely parse and emit code even for incomplete or un-compilable project states.
* **Seamless Sync**: Keep your docs, tests, database, clients, and routing in perfect harmony. Update your code, and generate the docs; or update the docs, and generate the code.

## 📦 Installation

Requires Java 19+ and Gradle (or use the provided `gradlew`).

Build and install the CLI:
```bash
./gradlew installDist
```

The executable will be located at `build/install/cdd-kotlin/bin/cdd-kotlin`.
Or you can use `make build` and `make run ARGS="..."`.

## 🛠 Usage

### Command Line Interface

```bash
# Generate Kotlin models and KMP project from OpenAPI
./build/install/cdd-kotlin/bin/cdd-kotlin from_openapi -i spec.json -o my-app

# Statically analyze a Kotlin directory and output OpenAPI JSON
./build/install/cdd-kotlin/bin/cdd-kotlin to_openapi -f my-app/src --format json
```

### Programmatic SDK / Library

```kotlin
import cdd.openapi.OpenApiParser
import cdd.classes.DtoGenerator
import java.io.File

fun main() {
    val parser = OpenApiParser()
    val document = parser.parseFile(File("spec.json"))
    val dtoGenerator = DtoGenerator()
    val generatedCode = dtoGenerator.generate(document.components?.schemas ?: emptyMap())
    println(generatedCode.text)
}
```

## Design choices

`cdd-kotlin` utilizes the Kotlin Embeddable Compiler (`kotlin-compiler-embeddable`) for highly accurate, AST-driven, static code analysis and regeneration. We prefer compiler-grade ASTs to reflection because it supports reading incomplete or invalid source files and generating perfect source maps without executing potentially unsafe or side-effecting code.
For OpenAPI parsing and generation, we utilize Jackson combined with custom IR (Intermediate Representation) mappers.

WASM compilation: **Not Currently Supported**. Native WASM compilation for Kotlin relies on the Kotlin/Wasm target which doesn't directly support the JVM-bound Kotlin compiler APIs (PSI, embeddable compiler) used here. Support would require abstraction over the compiler environment.

## 🏗 Supported Conversions for Kotlin

*(The boxes below reflect the features supported by this specific `cdd-kotlin` implementation)*

| Concept | Parse (From) | Emit (To) |
|---------|--------------|-----------|
| OpenAPI (JSON/YAML) | ✅ | ✅ |
| `Kotlin` Models / Structs / Types | ✅ | ✅ |
| `Kotlin` Server Routes / Endpoints | ✅ | ✅ |
| `Kotlin` API Clients / SDKs | ✅ | ✅ |
| `Kotlin` ORM / DB Schemas | [ ] | [ ] |
| `Kotlin` CLI Argument Parsers | [ ] | [ ] |
| `Kotlin` Docstrings / Comments | ✅ | ✅ |
| WASM (WebAssembly) Compilation | [ ] | [ ] |

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
