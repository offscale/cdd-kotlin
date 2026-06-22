build_wasm:
	./gradlew --gradle-user-home .gradle_home compileProductionExecutableKotlinWasmWasi
	mkdir -p bin
	cp build/compileSync/wasmWasi/main/productionExecutable/kotlin/cdd-kotlin.wasm bin/cdd-kotlin.wasm

.PHONY: run
run:
	./gradlew run "--args=$(filter-out $@,$(MAKECMDGOALS))"

%:
	@:
