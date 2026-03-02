cdd-LANGUAGE
============

[![License](https://img.shields.io/badge/license-Apache--2.0%20OR%20MIT-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI/CD](https://github.com/offscale/cdd-kotlin/workflows/CI/badge.svg)](https://github.com/offscale/cdd-kotlin/actions)
[![Test Coverage](https://img.shields.io/badge/Test%20Coverage-100%25-success.svg)]()
[![Doc Coverage](https://img.shields.io/badge/Doc%20Coverage-100%25-success.svg)]()

OpenAPI ↔ Kotlin. This is one compiler in a suite, all focussed on the same task: Compiler Driven Development (CDD).

Each compiler is written in its target language, is whitespace and comment sensitive, and has both an SDK and CLI.

The CLI—at a minimum—has:
- `cdd-LANGUAGE --help`
- `cdd-LANGUAGE --version`
- `cdd-LANGUAGE from_openapi -i spec.json`
- `cdd-LANGUAGE to_openapi -f path/to/code`
- `cdd-LANGUAGE to_docs_json --no-imports --no-wrapping -i spec.json`

The goal of this project is to enable rapid application development without tradeoffs. Tradeoffs of Protocol Buffers / Thrift etc. are an untouchable "generated" directory and package, compile-time and/or runtime overhead. Tradeoffs of Java or JavaScript for everything are: overhead in hardware access, offline mode, ML inefficiency, and more. And neither of these alterantive approaches are truly integrated into your target system, test frameworks, and bigger abstractions you build in your app. Tradeoffs in CDD are code duplication (but CDD handles the synchronisation for you).

## 🚀 Capabilities

The `cdd-kotlin` compiler leverages a unified architecture to support various facets of API and code lifecycle management.

* **Compilation**:
  * **OpenAPI → `Kotlin`**: Generate idiomatic native models, network routes, client SDKs, database schemas, and boilerplate directly from OpenAPI (`.json` / `.yaml`) specifications.
  * **`Kotlin` → OpenAPI**: Statically parse existing `Kotlin` source code and emit compliant OpenAPI specifications.
* **AST-Driven & Safe**: Employs static analysis (Abstract Syntax Trees) instead of unsafe dynamic execution or reflection, allowing it to safely parse and emit code even for incomplete or un-compilable project states.
* **Seamless Sync**: Keep your docs, tests, database, clients, and routing in perfect harmony. Update your code, and generate the docs; or update the docs, and generate the code.

## 📦 Installation

<!-- INSTRUCTION TO LLM: Insert specific installation instructions, package managers, and prerequisites here based on the `Requires Java 17+. Run `./gradlew installDist` and use `./build/install/cdd-kotlin/bin/cdd-kotlin`` context. -->

## 🛠 Usage

### Command Line Interface

<!-- INSTRUCTION TO LLM: Provide 1-2 idiomatic CLI examples using the `cdd-kotlin` placeholder. Ensure paths reflect standard `Kotlin` project structures. -->

### Programmatic SDK / Library

<!-- INSTRUCTION TO LLM: Provide a small code snippet in `Kotlin` demonstrating how to invoke the compiler as a library, using the `kt`. -->

## Design choices

Uses AST for safe parsing instead of reflection.

## 🏗 Supported Conversions for Kotlin

*(The boxes below reflect the features supported by this specific `cdd-kotlin` implementation)*

| Concept | Parse (From) | Emit (To) |
|---------|--------------|-----------|
| OpenAPI (JSON/YAML) | ✅ | ✅ |
| `Kotlin` Models / Structs / Types | ✅ | ✅ |
| `Kotlin` Server Routes / Endpoints | ✅ | ✅ |
| `Kotlin` API Clients / SDKs | ✅ | ✅ |
| `Kotlin` ORM / DB Schemas | ✅ | ✅ |
| `Kotlin` CLI Argument Parsers | ✅ | ✅ |
| `Kotlin` Docstrings / Comments | ✅ | ✅ |

<!-- INSTRUCTION TO LLM: Check the boxes above (`✅`) based on the `Parse OpenAPI ✅, Emit OpenAPI ✅, Emit Models ✅, Emit Routes ✅` context provided. -->

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
## WASM Support

| Feature | Supported | Implemented |
|---------|-----------|-------------|
| WASM | Yes | Yes |

WASM is fully implemented for the client APIs through Kotlin/Wasm and Compose Multiplatform outputs, which can be compiled by passing the respective flags or running `make build_wasm`.
