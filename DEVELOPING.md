# Developing `cdd-kotlin`

## Prerequisites

- **JDK 21** (or JDK 17+)
- **Python 3** (used by helper scripts in `scripts/`)
- **Gradle 8.11+** (automatically managed via `./gradlew` or `scripts/run_with_fallback.py`)

To install missing system dependencies automatically:

```sh
make deps
# or on Windows:
.\make.bat deps
```

---

## Building, Formatting, and Testing

```sh
# Format all Kotlin and Gradle Kotlin DSL files using Spotless (ktfmt)
./gradlew spotlessApply

# Verify formatting and 100% KDoc coverage across commonMain, main, and wasmMain
./gradlew spotlessCheck checkDocCoverage

# Run the JVM and WASI test suites and enforce 100% Kover coverage (LINE, INSTRUCTION, BRANCH)
./gradlew check

# Build JVM and WASI binaries
./gradlew assemble
make build_wasm
```

---

## Quality Gates

`cdd-kotlin` enforces strict quality gates in `build.gradle.kts`:
1. **Code Formatting (`spotlessCheck`)**: Uses `ktfmt()` across `src/**/*.kt` and `*.gradle.kts`.
2. **Documentation Coverage (`checkDocCoverage`)**: Custom Gradle task that parses all Kotlin source files in `src/commonMain/kotlin`, `src/main/kotlin`, and `src/wasmMain/kotlin` using `KotlinCoreEnvironment` PSI and verifies that 100% of classes, objects, interfaces, functions, and properties have KDoc comments.
3. **Test Coverage (`koverVerify`)**: Enforces a minimum bound of `100%` across `CoverageUnit.LINE`, `CoverageUnit.INSTRUCTION`, and `CoverageUnit.BRANCH`.
