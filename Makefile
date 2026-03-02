.PHONY: install_base install_deps build_docs build test run help all build_wasm build_docker run_docker

help:
	@echo "Available tasks:"
	@echo "  install_base   - Install language runtime (Java/Gradle)"
	@echo "  install_deps   - Install local dependencies"
	@echo "  build_docs     - Build API docs (optional: docs_dir=path)"
	@echo "  build          - Build the CLI binary (optional: bin_dir=path)"
	@echo "  build_wasm     - Build the WASM output"
	@echo "  build_docker   - Build alpine and debian Docker images"
	@echo "  run_docker     - Run the docker container"
	@echo "  test           - Run tests locally"
	@echo "  run            - Run the CLI (builds if necessary)"
	@echo "  help / all     - Show this help text"

all: help

: 
	@echo "Available tasks:"
	@echo "  install_base   - Install language runtime (Java/Gradle)"
	@echo "  install_deps   - Install local dependencies"
	@echo "  build_docs     - Build API docs (optional: docs_dir=path)"
	@echo "  build          - Build the CLI binary (optional: bin_dir=path)"
	@echo "  build_wasm     - Build the WASM output"
	@echo "  build_docker   - Build alpine and debian Docker images"
	@echo "  run_docker     - Run the docker container"
	@echo "  test           - Run tests locally"
	@echo "  run            - Run the CLI (builds if necessary)"
	@echo "  help / all     - Show this help text"

install_base:
	@echo "Please install Java (JDK 17+) to run Gradle."

install_deps:
	./gradlew dependencies

build_docs:
	docs_dir=$${docs_dir:-docs} && mkdir -p $$docs_dir && ./gradlew dokkaHtml -PdocsDir=$$docs_dir

build:
	bin_dir=$${bin_dir:-build/install/cdd-kotlin/bin} && ./gradlew installDist -PbinDir=$$bin_dir

build_wasm:
	./gradlew jsBrowserProductionWebpack

build_docker:
	docker build -f alpine.Dockerfile -t cdd-kotlin-alpine .
	docker build -f debian.Dockerfile -t cdd-kotlin-debian .

run_docker:
	docker run -p 8082:8082 cdd-kotlin-alpine

test:
	./gradlew test

run: build
	./build/install/cdd-kotlin/bin/cdd-kotlin $(filter-out $@,$(MAKECMDGOALS))

%:
	@:
