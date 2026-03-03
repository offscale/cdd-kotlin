# Architecture

`cdd-kotlin` uses Kotlin's embeddable compiler (PSI) for static analysis.
It parses Kotlin source files representing controllers, CLI commands, and POJOs (data classes),
and generates a full OpenApi Document tree object that can be emitted as JSON or YAML.

Bidirectionality is maintained through the `Parse.kt` and `Emit.kt` files within modules:
- `cdd.classes`
- `cdd.docstrings`
- `cdd.functions`
- `cdd.mocks`
- `cdd.openapi`
- `cdd.routes`

A JSON-RPC server is also available for integration into larger ecosystems.