build_wasm:
	./gradlew compileProductionExecutableKotlinWasmWasiOptimize
	mkdir -p bin
	cp build/compileSync/wasmWasi/main/productionExecutable/optimized/cdd-kotlin.wasm bin/cdd-kotlin.wasm
