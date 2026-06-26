cdd-kotlin
==========
[![License](https://img.shields.io/badge/license-Apache--2.0%20OR%20MIT-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![interactive WASM web demo](https://img.shields.io/badge/interactive-WASM_web_demo-blue.svg)](https://offscale.io/wasm_web_demo)
[![CI](https://github.com/offscale/cdd-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/offscale/cdd-kotlin/actions)
[![Test Coverage](https://img.shields.io/badge/test_coverage-99%25-brightgreen.svg)](#)
[![Doc Coverage](https://img.shields.io/badge/doc_coverage-100%25-brightgreen.svg)](#)

----

OpenAPI ↔ Kotlin. This is one compiler in a suite, all focussed on the same task: Compiler Driven Development (CDD).

Each compiler is written in its target language, is whitespace and comment sensitive, and has both an SDK and CLI.

The core philosophy of Compiler Driven Development (CDD) is synchronization without compromise. Where traditional generators silo your API boundaries into read-only files, this compiler natively merges changes into your codebase via a robust, [whitespace and comment aware] Abstract Syntax Tree (AST) driven parser & emitter. It bridges the gap between design and implementation, allowing you to seamlessly generate SDKs from a spec or extract a spec from existing code. By keeping your APIs, SDKs, and tests in continuous, automated alignment, it drastically improves both delivery speed and software reliability.

The CLI—at a minimum—has:

- `cdd-kotlin --help`
- `cdd-kotlin --version`
- `cdd-kotlin from_openapi to_sdk_cli -i spec.json`
- `cdd-kotlin from_openapi to_sdk -i spec.json --no-github-actions --no-installable-package --tests`
- `cdd-kotlin from_openapi to_server -i spec.json`
- `cdd-kotlin to_openapi -f path/to/code`
- `cdd-kotlin to_docs_json --no-imports --no-wrapping -i spec.json`
- `cdd-kotlin mcp` (Run the Model Context Protocol server via stdio)

## SDK Example

```kt
import org.cdd.CddGenerator
import org.cdd.Config

fun main() {
    val config = Config(
        inputPath = "spec.json", 
        outputDir = "src/models",
        noGithubActions = false,
        noInstallablePackage = false,
        createComposableTestsAndMocks = false
    )
    CddGenerator.generateSdk(config)
    println("SDK generation complete.")
}
```

## Installation

```bash
./gradlew build
```

## Development

You can use standard tooling commands or the included cross-platform Makefiles to fetch dependencies, build, and test:

```bash
./gradlew build
# or
make deps
make build
make test
# or on Windows
.\make.bat deps
.\make.bat build
.\make.bat test
```

See [PUBLISH.md](PUBLISH.md) for packaging and releasing.

## Features

The `cdd-kotlin` compiler leverages a unified architecture to support various facets of API and code lifecycle management.

- **Compilation**:
    - **OpenAPI → `Kotlin`**: Generate idiomatic native models, network routes, client SDKs, and boilerplate directly from OpenAPI (`.json` / `.yaml`) specifications.
    - **`Kotlin` → OpenAPI**: Statically parse existing `Kotlin` source code and emit compliant OpenAPI specifications.
- **AST-Driven & Safe**: Employs static analysis instead of unsafe dynamic execution or reflection, allowing it to safely parse and emit code even for incomplete or un-compilable project states.
- **Model Context Protocol (MCP)**: Native support for MCP integration, exposing tools and resources directly to LLMs for seamless API design to code generation workflows.
- **Seamless Sync**: Keep your docs, tests, database, clients, and routing in perfect harmony. Update your code, and generate the docs; or update the docs, and generate the code.

**Uncommon Features:**

`cdd-kotlin` supports extensive auto-generation features beyond the standard suite:
- **KMP Auto-Admin Scaffold:** Generates fully functional, component-based administration dashboards across Kotlin Multiplatform targets (including WebAssembly via `wasmWasi`) directly from the OpenAPI schema.

---

## CLI Options

```text
Usage: cdd-kotlin [OPTIONS] <COMMAND>
```

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

## Unified CLI Toolset
CDD Server generation exposes the following CLI subcommands and options:
* `cdd-kotlin from_openapi to_sdk -i spec.json` - Generates a Kotlin Multiplatform Client SDK.
* `cdd-kotlin from_openapi to_sdk_cli -i spec.json` - Generates a Kotlin Multiplatform Client SDK and a CLI.
* `cdd-kotlin from_openapi to_server -i spec.json` - Generates an exhaustive Mock Server implementation.
* `cdd-kotlin to_docs_json -i spec.json` - Generates JSON documentation.
* `cdd-kotlin mcp` - Starts the Model Context Protocol stdio server.

### Decoupled CDD Server Modes
When running the generated server (`cdd-kotlin from_openapi to_server -i spec.json`), the generated artifact includes a standalone CLI that supports the following decoupled modes for realistic or sandbox mock environments:

* `start` (No DB configured): **Stub Mode**. Server runs using traditional scaffolds, endpoints return `NotImplementedError` or empty bodies.
* `start` (With `DATABASE_URL`): **Production Mode**. Uses actual ORM interactions against a real database.
* `start --ephemeral`: **Sandbox Mode**. Uses actual ORM interactions against a fresh, throwaway database (SQLite memory database).
* `start --ephemeral --seed`: **Full Mock Mode**. Ephemeral database, automatically populated with a localized fake data graph honoring all referential dependencies using `kotlin-faker`.

### Webhooks & Administrative Triggers
If the OpenAPI specification defines callbacks or webhooks, the generated server exposes an administrative trigger endpoint that acts as a dispatch mechanism:

* `POST /_mock/trigger-webhook/{webhookName}?targetUrl=<URL>`
This dummy endpoint utilizes an internal HTTP client to immediately dispatch a mock JSON payload representing the given `{webhookName}` to the specified `targetUrl` parameter, returning an HTTP `202 Accepted` response.

### Contract Synchronization
To force data classes, ORM layers, and API representations into sync based on a specified source of truth, utilize:
* `cdd-kotlin sync -i <source_dir> --truth class`
