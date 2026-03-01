# Developing `cdd-kotlin`

## Requirements
- Java 19 or later
- Gradle (provided via wrapper)

## Building
To build the CLI:
```bash
make build
```

## Testing
To run tests and check doc coverage:
```bash
make test
```

## Structure
- `src/main/kotlin/cdd/`: Main application logic divided functionally (`classes`, `openapi`, `routes`, `functions`, `mocks`, `tests`, `scaffold`, `shared`).
- `src/test/kotlin/cdd/`: Unit tests matching the main directory structure.

## Code Conventions
Please document all public functions and classes with KDoc. Tests must maintain 100% test and doc coverage.
