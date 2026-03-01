# USAGE

## cdd-kotlin CLI

```bash
# Display help
cdd-kotlin --help

# Generate a KMP project from an OpenAPI spec
cdd-kotlin from_openapi -i spec.json -o generated-client --clientName MyClient

# Parse a Kotlin codebase and generate an OpenAPI spec
cdd-kotlin to_openapi -f path/to/kotlin/src --format yaml > openapi.yaml

# Merge OpenAPI changes back into existing Kotlin codebase
cdd-kotlin merge_openapi -s new_spec.yaml -d path/to/kotlin/src

# Generate documentation snippets
cdd-kotlin to_docs_json -i spec.json --no-imports --no-wrapping
```
