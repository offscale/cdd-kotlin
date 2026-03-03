@echo off
setlocal
if "%1"=="" goto help
if "%1"=="help" goto help
if "%1"=="all" goto help
if "%1"=="install_base" goto install_base
if "%1"=="install_deps" goto install_deps
if "%1"=="build_docs" goto build_docs
if "%1"=="build" goto build
if "%1"=="build_wasm" goto build_wasm
if "%1"=="test" goto test
if "%1"=="run" goto run
if "%1"=="build_docker" goto build_docker
if "%1"=="run_docker" goto run_docker
goto end

:help
echo Available tasks:
echo   install_base   Install language runtime and dependencies
echo   install_deps   Install local dependencies
echo   build_docs     Build the API docs
echo   build          Build the CLI binary
echo   build_wasm     Build the WASM binary
echo   build_docker   Build docker images
echo   run_docker     Run docker container
echo   test           Run tests locally
echo   run            Run the CLI
goto end

:install_base
echo Ensure Java 17+ and Kotlin are installed.
goto end

:install_deps
call gradlew.bat dependencies
goto end

:build_docs
set DOCS_DIR=docs
if not "%2"=="" set DOCS_DIR=%2
call gradlew.bat dokkaHtml
mkdir %DOCS_DIR% 2^>nul
xcopy /E /Y build\dokka\html\* %DOCS_DIR%\
goto end

:build
set BIN_DIR=build\install\cdd-kotlin
if not "%2"=="" set BIN_DIR=%2
call gradlew.bat installDist
mkdir %BIN_DIR% 2^>nul
xcopy /E /Y build\install\cdd-kotlin\* %BIN_DIR%\
goto end

:build_wasm
echo WASM build is not supported for cdd-kotlin (see WASM.md for details)
goto end

:test
call gradlew.bat check checkDocCoverage koverVerify
goto end

:run
call make.bat build
build\install\cdd-kotlin\bin\cdd-kotlin.bat %2 %3 %4 %5 %6 %7 %8 %9
goto end

:build_docker
docker build -t cdd-kotlin-alpine -f alpine.Dockerfile .
docker build -t cdd-kotlin-debian -f debian.Dockerfile .
goto end

:run_docker
docker run -p 8082:8082 cdd-kotlin-alpine serve_json_rpc --port 8082 --listen 0.0.0.0
goto end

:end
endlocal
