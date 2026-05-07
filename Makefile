build_wasm:
	./gradlew --gradle-user-home .gradle_home compileProductionExecutableKotlinWasmWasiOptimize
	mkdir -p bin
	cp build/compileSync/wasmWasi/main/productionExecutable/optimized/cdd-kotlin.wasm bin/cdd-kotlin.wasm
