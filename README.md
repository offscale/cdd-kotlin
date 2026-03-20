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

## CLI Help

```
$ ./build/install/cdd-kotlin/bin/cdd-kotlin --help
Usage: cdd-kotlin [<options>] <command> [<args>]...

  OpenAPI ↔ Kotlin

Options:
  --version   Show the version and exit
  -h, --help  Show this message and exit

Commands:
  from_openapi    Generate from an OpenAPI specification
  to_openapi      Generate an OpenAPI specification from Kotlin code
  merge_openapi   Merge an OpenAPI specification into an existing Kotlin
                  codebase
  to_docs_json    Generate API documentation code examples as JSON
  serve_json_rpc  Start JSON-RPC server
```

### `from_openapi`

```
$ ./build/install/cdd-kotlin/bin/cdd-kotlin from_openapi --help
Usage: cdd-kotlin from_openapi [<options>] <command> [<args>]...

  Generate from an OpenAPI specification

Options:
  -h, --help  Show this message and exit

Commands:
  to_sdk      Generate Client SDK services
  to_sdk_cli  Generate CLI for Client SDK
  to_server   Generate Server services
```

### `to_openapi`

```
$ ./build/install/cdd-kotlin/bin/cdd-kotlin to_openapi --help
Usage: cdd-kotlin to_openapi [<options>]

  Generate an OpenAPI specification from Kotlin code

Options:
  -i, --input=<text>    Path to a snapshot file or a generated output directory
  --format=(json|yaml)  Output format for the OpenAPI spec
  -o, --output=<text>   Output specification file
  -h, --help            Show this message and exit
```

### `to_docs_json`

```
$ ./build/install/cdd-kotlin/bin/cdd-kotlin to_docs_json --help
Usage: cdd-kotlin to_docs_json [<options>]

  Generate API documentation code examples as JSON

Options:
  -i, --input=<text>   Path or URL to the OpenAPI specification
  --no-imports         Omit the imports field
  --no-wrapping        Omit the wrapper_start and wrapper_end fields
  -o, --output=<text>  Output JSON file
  -h, --help           Show this message and exit
```
