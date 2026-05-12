cdd-kotlin
==========
[![License](https://img.shields.io/badge/license-Apache--2.0%20OR%20MIT-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI](https://github.com/offscale/cdd-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/offscale/cdd-kotlin/actions)
[![Test Coverage](https://img.shields.io/badge/test_coverage-100%25-brightgreen.svg)](#)
[![Doc Coverage](https://img.shields.io/badge/doc_coverage-100%25-brightgreen.svg)](#)

----

OpenAPI ↔ Kotlin. This is one compiler in a suite, all focussed on the same task: Compiler Driven Development (CDD).

Each compiler is written in its target language, is whitespace and comment sensitive, and has both an SDK and CLI.

The core philosophy of Compiler Driven Development (CDD) is synchronization without compromise. Where traditional generators silo your API boundaries into read-only files, this compiler natively merges changes into your codebase via a robust, [whitespace and comment aware] Abstract Syntax Tree (AST) driven parser & emitter. It bridges the gap between design and implementation, allowing you to seamlessly generate SDKs from a spec or extract a spec from existing code. By keeping your APIs, SDKs, and tests in continuous, automated alignment, it drastically improves both delivery speed and software reliability.

The CLI—at a minimum—has:

- `cdd-kotlin --help`
- `cdd-kotlin --version`
- `cdd-kotlin from_openapi to_sdk_cli -i spec.json`
- `cdd-kotlin from_openapi to_sdk -i spec.json --no-github-actions --no-installable-package --create-composable-tests-mocks`
- `cdd-kotlin from_openapi to_server -i spec.json`
- `cdd-kotlin to_openapi -f path/to/code`
- `cdd-kotlin to_docs_json --no-imports --no-wrapping -i spec.json`
- `cdd-kotlin serve_json_rpc --port 8080 --listen 0.0.0.0`

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
