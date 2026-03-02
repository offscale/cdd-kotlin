@echo off
if "%1"=="" goto help
if "%1"=="help" goto help
if "%1"=="all" goto help
if "%1"=="install_base" goto install_base
if "%1"=="install_deps" goto install_deps
if "%1"=="build_docs" goto build_docs
if "%1"=="build" goto build
if "%1"=="build_wasm" goto build_wasm
if "%1"=="build_docker" goto build_docker
if "%1"=="run_docker" goto run_docker
if "%1"=="test" goto test
if "%1"=="run" goto run
goto help

:help
echo Available tasks:
echo   install_base   - Install language runtime (Java/Gradle)
echo   install_deps   - Install local dependencies
echo   build_docs     - Build API docs (optional: docs_dir=path)
echo   build          - Build the CLI binary
echo   build_wasm     - Build the WASM output
echo   build_docker   - Build alpine and debian Docker images
echo   run_docker     - Run the docker container
echo   test           - Run tests locally
echo   run            - Run the CLI (builds if necessary)
echo   help / all     - Show this help text
goto end

:install_base
echo Please install Java (JDK 17+) to run Gradle.
goto end

:install_deps
call gradlew.bat dependencies
goto end

:build_docs
set DOCS_DIR=%2
if "%DOCS_DIR%"=="" set DOCS_DIR=docs
if not exist %DOCS_DIR% mkdir %DOCS_DIR%
call gradlew.bat dokkaHtml -PdocsDir=%DOCS_DIR%
goto end

:build
call gradlew.bat installDist
goto end

:build_wasm
call gradlew.bat jsBrowserProductionWebpack
goto end

:build_docker
docker build -f alpine.Dockerfile -t cdd-kotlin-alpine .
docker build -f debian.Dockerfile -t cdd-kotlin-debian .
goto end

:run_docker
docker run -p 8082:8082 cdd-kotlin-alpine
goto end

:test
call gradlew.bat test
goto end

:run
call gradlew.bat installDist
set args=%*
call build\install\cdd-kotlin\bin\cdd-kotlin.bat %args:*run =%
goto end

:end
