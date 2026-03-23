build_wasm:
	./gradlew compileKotlinWasmWasi
	mkdir -p bin
	cp build/compileSync/wasmWasi/main/productionExecutable/kotlin/cdd-kotlin-wasm-wasi.wasm bin/cdd-kotlin.wasm || cp build/compileSync/wasmWasi/main/productionExecutable/kotlin/cdd-kotlin-wasmWasiMain.wasm bin/cdd-kotlin.wasm || cp build/classes/kotlin/wasmWasi/main/cdd-kotlin.wasm bin/cdd-kotlin.wasm || find build -name "*.wasm" -exec cp {} bin/cdd-kotlin.wasm \;
