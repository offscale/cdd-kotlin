build_wasm:
	python3 scripts/run_with_fallback.py gradle --gradle-user-home .gradle_home assemble
	mkdir -p bin
	cp build/compileSync/wasmWasi/main/productionExecutable/optimized/cdd-kotlin.wasm bin/cdd-kotlin.wasm

.PHONY: run
run:
	python3 scripts/run_with_fallback.py gradle run "--args=\$(filter-out \$@,\$(MAKECMDGOALS))"

%:
	@:
