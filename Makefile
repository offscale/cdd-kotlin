.PHONY: help install_base install_deps build_docs build test run all build_wasm

help:
	@echo "Available targets:"
	@echo "  install_base   Install language runtime hints"
	@echo "  install_deps   Fetch dependencies via Gradle"
	@echo "  build_docs     Build the API docs (Dokka) to docs/ directory, or a specific directory via DOCS_DIR="
	@echo "  build          Build the CLI binary, optionally specific directory via BIN_DIR="
	@echo "  build_wasm     Build a WASM binary (Currently unsupported in Kotlin CLI)"
	@echo "  test           Run tests locally"
	@echo "  run            Run the built CLI. Usage: make run ARGS=\"--help\""
	@echo "  all            Show help text"

all: help

install_base:
	@echo "Please ensure Java 19+ is installed."
	@echo "If using sdkman, run: sdk install java 19.0.2-tem"

install_deps:
	./gradlew dependencies

build_docs:
	./gradlew dokkaHtml
	@if [ -n "$(DOCS_DIR)" ]; then \
		mkdir -p $(DOCS_DIR) && cp -r build/dokka/html/* $(DOCS_DIR)/; \
	else \
		mkdir -p docs && cp -r build/dokka/html/* docs/; \
	fi

build:
	./gradlew installDist
	@if [ -n "$(BIN_DIR)" ]; then \
		mkdir -p $(BIN_DIR) && cp -r build/install/cdd-kotlin/* $(BIN_DIR)/; \
	fi

build_wasm:
	@echo "Not supported natively due to JVM-bound dependencies (kotlin-compiler-embeddable, PSI)."
	@exit 1

test:
	./gradlew test

run: build
	./build/install/cdd-kotlin/bin/cdd-kotlin $(ARGS)

:
	@make help
