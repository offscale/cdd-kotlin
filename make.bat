@echo off
setlocal

set TARGET=%1
if "%TARGET%"=="" set TARGET=help

if "%TARGET%"=="help" goto help
if "%TARGET%"=="all" goto help
if "%TARGET%"=="install_base" goto install_base
if "%TARGET%"=="install_deps" goto install_deps
if "%TARGET%"=="build_docs" goto build_docs
if "%TARGET%"=="build" goto build
if "%TARGET%"=="build_wasm" goto build_wasm
if "%TARGET%"=="test" goto test
if "%TARGET%"=="run" goto run

echo Unknown target: %TARGET%
goto help

:help
echo Available targets:
echo   install_base   Install language runtime hints
echo   install_deps   Fetch dependencies via Gradle
echo   build_docs     Build the API docs (Dokka) to docs/ directory or DOCS_DIR
echo   build          Build the CLI binary, optionally to BIN_DIR
echo   build_wasm     Build a WASM binary (Currently unsupported in Kotlin CLI)
echo   test           Run tests locally
echo   run            Run the built CLI. Usage: make.bat run --help
echo   all            Show help text
goto end

:install_base
echo Please ensure Java 19+ is installed.
goto end

:install_deps
call gradlew.bat dependencies
goto end

:build_docs
call gradlew.bat dokkaHtml
set D_DIR=docs
if not "%DOCS_DIR%"=="" set D_DIR=%DOCS_DIR%
if not exist "%D_DIR%" mkdir "%D_DIR%"
xcopy /s /y /i build\dokka\html "%D_DIR%"
goto end

:build
call gradlew.bat installDist
if not "%BIN_DIR%"=="" (
    if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"
    xcopy /s /y /i build\install\cdd-kotlin\* "%BIN_DIR%"
)
goto end

:build_wasm
echo Not supported natively due to JVM-bound dependencies (kotlin-compiler-embeddable, PSI).
exit /b 1
goto end

:test
call gradlew.bat test
goto end

:run
call gradlew.bat installDist
shift
set ARGS=
:loop
if "%1"=="" goto run_end
set ARGS=%ARGS% %1
shift
goto loop
:run_end
call build\install\cdd-kotlin\bin\cdd-kotlin.bat %ARGS%
goto end

:end
endlocal
