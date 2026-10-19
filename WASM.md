# Kotlin WebAssembly (WASM / WASI) Support

The `cdd-kotlin` toolchain compiles natively to **WebAssembly System Interface (WASI Preview 1)** via the Kotlin Multiplatform `wasmWasi` compiler backend.

## Building the WASI Binary

Build the optimized production WASI binary (`bin/cdd-kotlin.wasm`) using Make or Gradle:

```sh
make build_wasm
# or directly via Gradle:
./gradlew compileProductionExecutableKotlinWasmWasiOptimize
cp build/compileSync/wasmWasi/main/productionExecutable/optimized/cdd-kotlin.wasm bin/cdd-kotlin.wasm
```

## Architecture & Low-Level WASI Bindings

- **`src/wasmMain/kotlin/PlatformWasm.kt`**: Implements the platform `expect`/`actual` contract (`getEnvVar`, `readFile`, `writeToFile`, `readDirectoryFiles`) directly against `@WasmImport("wasi_snapshot_preview1", ...)` system calls (`environ_sizes_get`, `environ_get`, `path_open`, `fd_read`, `fd_write`, `fd_close`, `path_create_directory`) using `kotlin.wasm.unsafe.withScopedMemoryAllocator` without external native dependencies.
- **`src/wasmMain/kotlin/org/cdd/wasm/Main.kt`**: Exposes C-ABI `@WasmExport` entrypoints for host runtimes and browsers:
  - `from_openapi()`
  - `to_openapi()`
  - `from_arazzo()`
  - `to_arazzo()`
  - `to_docs_json()`
  - `to_sdk()`

## Running in WASI Runtimes (Node.js / Wasmtime)

```sh
# Execute with Wasmtime granting directory access and environment variables
wasmtime --dir=. --env CDD_INPUT=mega_spec.json --env CDD_OUTPUT=docs.json 
  bin/cdd-kotlin.wasm --invoke to_docs_json

# Execute the WASI test suite via Node.js (configured in build.gradle.kts)
./gradlew wasmWasiNodeTest
```
