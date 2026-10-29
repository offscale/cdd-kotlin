# Publishing & Releasing `cdd-kotlin`

## Versioning

The project version is configured in `build.gradle.kts` (`version = "0.0.4"`) and reported via `cdd-kotlin --version`.

## Building Release Artifacts

1. **JVM Distribution & Fat/Standard JARs**:
   ```sh
   ./gradlew assemble
   ```
2. **Optimized WASI Binary (`bin/cdd-kotlin.wasm`)**:
   ```sh
   make build_wasm
   ```
3. **Badge Verification**:
   ```sh
   python3 scripts/update_badges.py
   ```

## Continuous Integration & GitHub Releases

GitHub Actions (`.github/workflows/ci.yml`) automatically:
1. Runs `./gradlew check` (Spotless formatting, 100% KDoc coverage, JUnit 5 JVM tests, WASI Node.js tests, and 100% Kover line/instruction/branch verification).
2. Builds `bin/cdd-kotlin.wasm` via `make build_wasm`.
3. Publishes WASM artifacts to GitHub Releases on new version tags (`v*`).

