cdd-kotlin
==========

[![Kotlin Integration](https://github.com/offscale/cdd-kotlin/actions/workflows/integration.yml/badge.svg)](https://github.com/offscale/cdd-kotlin/actions/workflows/integration.yml)
![Doc Coverage](https://img.shields.io/badge/doc_coverage-100%25-brightgreen)
![Test Coverage](https://img.shields.io/badge/test_coverage-100%25-brightgreen)

`cdd-kotlin` is a bidirectional compiler that converts between OpenAPI specifications and native Kotlin code (models, routes, SDKs, and CLIs) using AST and static analysis. It supports OpenAPI 3.2.0 spec compliance.

## Features

- Generate an installable Kotlin SDK from an OpenAPI spec.
- Generate a fully-typed SDK CLI with nested commands and help documentation from an OpenAPI spec.
- Scaffold a server.
- Extract an OpenAPI spec from an existing Kotlin codebase or a generated SDK CLI (full editability!).
- JSON-RPC server integration.
- Supports WASM compilation.

## Supported Commands

```bash
# General CLI arguments
cdd-kotlin --help
cdd-kotlin --version

# OpenAPI Generation (from Kotlin code)
cdd-kotlin to_openapi -f path/to/code -o spec.json

# Serve JSON-RPC
cdd-kotlin serve_json_rpc --port 8082 --listen 0.0.0.0

# Generate API Docs JSON
cdd-kotlin to_docs_json --no-imports --no-wrapping -i spec.json -o docs.json

# SDK CLI Generation (from OpenAPI spec)
cdd-kotlin from_openapi to_sdk_cli -i spec.json -o target_directory
cdd-kotlin from_openapi to_sdk_cli --input-dir ./specs/ -o target_directory

# SDK Generation (from OpenAPI spec)
cdd-kotlin from_openapi to_sdk -i spec.json -o target_directory
cdd-kotlin from_openapi to_sdk --input-dir ./specs/ -o target_directory

# Server Scaffolding (from OpenAPI spec)
cdd-kotlin from_openapi to_server -i spec.json -o target_directory
cdd-kotlin from_openapi to_server --input-dir ./specs/ -o target_directory
```

WASM support is **not possible** due to JVM dependencies (see [WASM.md](WASM.md) for details).
