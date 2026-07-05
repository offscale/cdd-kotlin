build_wasm:
	python3 scripts/run_with_fallback.py gradle --gradle-user-home .gradle_home assemble
	mkdir -p bin
	cp build/compileSync/wasmWasi/main/productionExecutable/optimized/cdd-kotlin.wasm bin/cdd-kotlin.wasm

.PHONY: run install_deps
run:
	python3 scripts/run_with_fallback.py gradle run "--args=$(filter-out $@,$(MAKECMDGOALS))"

install_deps:
	@MISSING_PYTHON=0; MISSING_JAVA=0; \
	if ! command -v python3 >/dev/null 2>&1; then MISSING_PYTHON=1; else echo "Python 3 is already installed."; fi; \
	if ! java -version 2>&1 | grep -q 'version "21'; then MISSING_JAVA=1; else echo "Java 21 is already installed."; fi; \
	if [ $$MISSING_PYTHON -eq 0 ] && [ $$MISSING_JAVA -eq 0 ]; then echo "All dependencies are satisfied."; exit 0; fi; \
	if command -v apt-get >/dev/null 2>&1; then \
		echo "Using apt..."; \
		sudo apt-get update; \
		if [ $$MISSING_PYTHON -eq 1 ]; then sudo apt-get install -y python3; fi; \
		if [ $$MISSING_JAVA -eq 1 ]; then sudo apt-get install -y openjdk-21-jdk; fi; \
	elif command -v dnf >/dev/null 2>&1; then \
		echo "Using dnf..."; \
		if [ $$MISSING_PYTHON -eq 1 ]; then sudo dnf install -y python3; fi; \
		if [ $$MISSING_JAVA -eq 1 ]; then sudo dnf install -y java-21-openjdk; fi; \
	elif command -v apk >/dev/null 2>&1; then \
		echo "Using apk..."; \
		if [ $$MISSING_PYTHON -eq 1 ]; then sudo apk add python3 || apk add python3; fi; \
		if [ $$MISSING_JAVA -eq 1 ]; then sudo apk add openjdk21 || apk add openjdk21; fi; \
	elif command -v winget >/dev/null 2>&1 || command -v winget.exe >/dev/null 2>&1; then \
		echo "Using winget..."; \
		if [ $$MISSING_PYTHON -eq 1 ]; then winget install -e --id Python.Python.3.12; fi; \
		if [ $$MISSING_JAVA -eq 1 ]; then winget install -e --id Microsoft.OpenJDK.21; fi; \
	elif command -v scoop >/dev/null 2>&1 || command -v scoop.cmd >/dev/null 2>&1; then \
		echo "Using scoop..."; \
		if [ $$MISSING_PYTHON -eq 1 ]; then scoop install python; fi; \
		if [ $$MISSING_JAVA -eq 1 ]; then scoop install corretto21; fi; \
	else \
		echo "Unsupported package manager. Please install missing dependencies manually."; \
		exit 1; \
	fi

%:
	@:
