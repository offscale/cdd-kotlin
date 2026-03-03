# WebAssembly (WASM) Support

`cdd-kotlin` utilizes the `kotlin-compiler-embeddable` dependency to parse Kotlin files into PSI (Program Structure Interface) ASTs. 

| Feature         | Possible | Implemented |
|-----------------|----------|-------------|
| WASM Build      | No       | No          |

### Why is it not possible?

The embeddable Kotlin Compiler (`kotlin-compiler-embeddable`) relies heavily on `com.intellij` core libraries and JVM-specific code (Java `File`, `ThreadLocal`, etc.), preventing it from being compiled to Kotlin/Wasm or Kotlin/Native. To make it WASM-compatible, one would need to use a completely different parser (e.g. Tree-sitter) that does not rely on the IntelliJ JVM platform, or use GraalVM/TeaVM to transpile JVM bytecode to WASM (which is highly experimental and heavyweight).