# cdd-kotlin

[![License: (Apache-2.0 OR MIT)](https://img.shields.io/badge/LICENSE-Apache--2.0%20OR%20MIT-orange)](LICENSE-APACHE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-blue)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Ktor](https://img.shields.io/badge/Ktor-2.3-orange)](https://ktor.io/)
[![CI](https://github.com/offscale/cdd-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/offscale/cdd-kotlin/actions/workflows/ci.yml)

OpenAPI ↔ Kotlin. This is one compiler in a suite, all focussed on the same task: Compiler Driven Development (CDD).

Each compiler is written in its target language, is whitespace and comment sensitive, and has both an SDK and CLI.

The CLI—at a minimum—has:

- `cdd-kotlin --help`
- `cdd-kotlin --version`
- `cdd-kotlin from_openapi to_sdk_cli -i spec.json`
- `cdd-kotlin from_openapi to_sdk -i spec.json`
- `cdd-kotlin from_openapi to_server -i spec.json`
- `cdd-kotlin to_openapi -f path/to/code`
- `cdd-kotlin to_docs_json --no-imports --no-wrapping -i spec.json`
- `cdd-kotlin serve_json_rpc --port 8080 --listen 0.0.0.0`

The goal of this project is to enable rapid application development without tradeoffs. Tradeoffs of Protocol Buffers / Thrift etc. are an untouchable "generated" directory and package, compile-time and/or runtime overhead. Tradeoffs of Java or JavaScript for everything are: overhead in hardware access, offline mode, ML inefficiency, and more. And neither of these alternative approaches are truly integrated into your target system, test frameworks, and bigger abstractions you build in your app. Tradeoffs in CDD are code duplication (but CDD handles the synchronisation for you).

## 🚀 Capabilities

The `cdd-kotlin` compiler leverages a unified architecture to support various facets of API and code lifecycle management.

- **Compilation**:
    - **OpenAPI → `Kotlin`**: Generate idiomatic native models, network routes, client SDKs, and boilerplate directly from OpenAPI (`.json` / `.yaml`) specifications.
    - **`Kotlin` → OpenAPI**: Statically parse existing `Kotlin` source code and emit compliant OpenAPI specifications.
- **AST-Driven & Safe**: Employs static analysis instead of unsafe dynamic execution or reflection, allowing it to safely parse and emit code even for incomplete or un-compilable project states.
- **Seamless Sync**: Keep your docs, tests, database, clients, and routing in perfect harmony. Update your code, and generate the docs; or update the docs, and generate the code.

## 📦 Installation & Build

### Native Tooling

```bash
./gradlew build
```

### Makefile / make.bat

You can also use the included cross-platform Makefiles to fetch dependencies, build, and test:

```bash
# Install dependencies
make deps

# Build the project
make build

# Run tests
make test
```

## 🛠 Usage

### Command Line Interface

```bash
# Generate Kotlin models from an OpenAPI spec
cdd-kotlin from_openapi to_sdk -i spec.json -o src/models

# Generate an OpenAPI spec from your Kotlin code
cdd-kotlin to_openapi -f src/models -o openapi.json
```

### Programmatic SDK / Library

```kt
import com.cdd.CddGenerator
import com.cdd.Config

fun main() {
    val config = Config("spec.json", "src/models")
    CddGenerator.generateSdk(config)
    println("SDK generation complete.")
}
```

## 🏗 Supported Conversions for Kotlin

*(The boxes below reflect the features supported by this specific `cdd-kotlin` implementation)*

| Features | Parse (From) | Emit (To) |
| --- | --- | --- |
| OpenAPI 3.2.0 | ✅ | ✅ |
| API Client SDK | ✅ | ✅ |
| API Client CLI | ✅ | ✅ |
| Server Routes / Endpoints | ✅ | ✅ |
| ORM / DB Schema | [ ] | [ ] |
| Mocks + Tests | [ ] | [ ] |
| Model Context Protocol (MCP) | [ ] | [ ] |

### Uncommon Features

`cdd-kotlin` supports extensive auto-generation features beyond the standard suite:
- **KMP Auto-Admin Scaffold:** Generates fully functional, component-based administration dashboards across Kotlin Multiplatform targets (including WebAssembly via `wasmWasi`) directly from the OpenAPI schema.

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
