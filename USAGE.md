# `cdd-kotlin` Usage Guide

This guide covers common CLI workflows, programmatic SDK usage, generated server operational modes, and bidirectional synchronization in `cdd-kotlin`.

---

## 1. Generating Client SDKs, CLIs, and Servers from OpenAPI

```sh
# Generate a Kotlin Multiplatform Client SDK (Compose + Ktor + kotlinx.serialization)
./cdd.sh from_openapi to_sdk -i mega_spec.json -o ./generated-sdk --tests

# Generate a Client SDK with a Clikt command-line interface
./cdd.sh from_openapi to_sdk_cli -i mega_spec.json -o ./generated-cli --tests

# Generate a Ktor Server with Exposed ORM DAOs, Faker Seeder, IdP, and Webhooks
./cdd.sh from_openapi to_server -i mega_spec.json -o ./generated-server --tests
```

### Generated Server Runtime Modes

The generated Ktor server (`server/src/main/kotlin/.../Main.kt`) includes a `MockServerCli` supporting four database modes:

```sh
# 1. Stub Mode (No database required; uses Stub<Entity>Dao)
./gradlew :server:run --args="start --port 8080"

# 2. Production Mode (Uses DATABASE_URL with PostgreSQL or SQLite + Concrete<Entity>Dao)
DATABASE_URL="jdbc:postgresql://localhost:5432/mydb" ./gradlew :server:run --args="start --port 8080"

# 3. Ephemeral Sandbox Mode (Uses throwaway SQLite database jdbc:sqlite:ephemeral.db)
./gradlew :server:run --args="start --ephemeral --port 8080"

# 4. Seeded Mock Mode (Populates referentially-consistent fake data via kotlin-faker)
./gradlew :server:run --args="start --ephemeral --seed --port 8080"
```

Additional server flags:
- `--strict-validation`: Enforces strict OpenAPI request validation.
- `--enforce-auth`: Requires `Authorization: Bearer mock-token-123` on protected routes.
- `--start-auth-server`: Enables `/auth/login`, `/auth/register`, `/auth/refresh`, and `/auth/logout` routes.

---

## 2. Reverse-Compiling Kotlin Source Code to OpenAPI 3.2.0

```sh
./cdd.sh to_openapi -i ./generated-sdk -o extracted_openapi.json
```

---

## 3. Bidirectional Arazzo 1.1.0 Workflows

```sh
# Generate Kotlin workflow orchestrators from an Arazzo 1.1.0 specification
./cdd.sh from_arazzo to_sdk -i workflow.arazzo.json -o ./workflow-sdk --tests

# Generate a workflow CLI runner
./cdd.sh from_arazzo to_sdk_cli -i workflow.arazzo.json -o ./workflow-cli --tests

# Generate Ktor workflow server routes and E2E tests
./cdd.sh from_arazzo to_server -i workflow.arazzo.json -o ./workflow-server --tests

# Extract an Arazzo 1.1.0 specification from Kotlin workflow source code
./cdd.sh to_arazzo -i ./workflow-sdk -o roundtrip.arazzo.json
```

---

## 4. Multi-Truth Synchronization (`sync`)

Keep Kotlin `data class` DTOs, JetBrains Exposed ORM `Table`/`Dao` definitions, Arazzo workflows, and OpenAPI specifications aligned:

```sh
# Use Kotlin data classes as the source of truth (updates DAOs and openapi_sync.json)
./cdd.sh sync -i ./my-project --truth class

# Use Exposed ORM Tables as the source of truth (updates DTOs and openapi_sync.json)
./cdd.sh sync -i ./my-project --truth dao

# Synchronize Arazzo workflows (updates arazzo_sync.json)
./cdd.sh sync -i ./my-project --truth arazzo

# Synchronize OpenAPI specification from project sources
./cdd.sh sync -i ./my-project --truth openapi
```

---

## 5. Documentation JSON & Model Context Protocol (MCP)

```sh
# Emit Ktor client snippets for documentation portals
./cdd.sh to_docs_json --no-imports --no-wrapping -i mega_spec.json -o docs.json

# Start the MCP stdio server
./cdd.sh mcp

# Start the JSON-RPC 2.0 stdio server
./cdd.sh serve_json_rpc
```
