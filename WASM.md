# WASM Support for `cdd-kotlin`

Currently, `cdd-kotlin` **does not** support compilation to WebAssembly (WASM).

## Why?

The tool relies heavily on the `kotlin-compiler-embeddable` library to generate an Abstract Syntax Tree (AST) of Kotlin code. This library parses and understands Kotlin code utilizing the Program Structure Interface (PSI) built into the IntelliJ IDEA platform.

These APIs are intrinsically bound to the Java Virtual Machine (JVM). They make heavy use of Java standard library features (I/O, Reflection, Threading) that are not currently available or easily polyfilled in WebAssembly environments, whether through Kotlin/Wasm, Kotlin/Native, or tools like TeaVM.

## Future Possibilities

For WASM support to become a reality, one of the following would need to occur:
1.  **Lightweight Parser:** We would need to replace the `kotlin-compiler-embeddable` parser with a lightweight, multiplatform-compatible Kotlin parser (like a custom ANTLR grammar compiled to Kotlin Multiplatform) that does not depend on the JVM or IntelliJ PSI.
2.  **J2CL/J2WASM Advancements:** Java-to-WASM tools (like J2WASM via GraalVM) become robust enough to seamlessly compile complex, reflection-heavy JVM applications like the Kotlin compiler itself into WASM.

Because of this limitation, `cdd-kotlin` cannot currently run inside a web browser or natively as a standalone WASM binary in a unified CLI without a JVM presence.
