# Developing

## Prerequisite

This project requires a JDK environment capable of running the Kotlin Compiler PSI.

- **JDK:** 17+
- **Kotlin:** 2.0+

## Testing & Verification

The project contains a comprehensive test suite in `src/test/kotlin` split into three categories:

1. **PSI Tests (`psi/`):** Validates that generators produce valid Kotlin syntax and parsers correctly extract
   definitions from source code.
2. **Scaffold Tests (`scaffold/`):** Ensures all Gradle configurations, version catalogs, and directory structures are
   created correctly.
3. **Round-Trip Verification (`verification/RoundTripTest.kt`):**
    - **Process:** `Spec A` → `Generate Code` → `Parse Code` → `Spec B`.
    - **assertion:** `Spec A == Spec B`.
    - This ensures that no data is lost during the generation/parsing lifecycle.

Run tests via Gradle:

```bash
./gradlew test
```
