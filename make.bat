@echo off
setlocal enabledelayedexpansion

if "%~1"=="build_wasm" goto build_wasm
if "%~1"=="run" goto run
if "%~1"=="install_deps" goto install_deps
if "%~1"=="" goto help

echo Unknown target %1
exit /b 1

:help
echo Available targets: build_wasm, run, install_deps
exit /b 0

:build_wasm
python scripts\run_with_fallback.py gradle --gradle-user-home .gradle_home assemble
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
if not exist bin mkdir bin
copy /Y build\compileSync\wasmWasi\main\productionExecutable\optimized\cdd-kotlin.wasm bin\cdd-kotlin.wasm
exit /b %ERRORLEVEL%

:run
set ARGS=
shift
:run_loop
if "%~1"=="" goto run_execute
set ARGS=%ARGS% "%~1"
shift
goto run_loop

:run_execute
python scripts\run_with_fallback.py gradle run "--args=%ARGS%"
exit /b %ERRORLEVEL%

:install_deps
set MISSING_PYTHON=0
set MISSING_JAVA=0

python --version >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo Python is already installed.
) else (
    set MISSING_PYTHON=1
)

java -version 2>&1 | findstr /C:"version \"21" >nul
if %ERRORLEVEL% equ 0 (
    echo Java 21 is already installed.
) else (
    set MISSING_JAVA=1
)

if %MISSING_PYTHON% equ 0 if %MISSING_JAVA% equ 0 (
    echo All dependencies are satisfied.
    exit /b 0
)

where winget >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo Using winget...
    if %MISSING_PYTHON% equ 1 winget install -e --id Python.Python.3.12
    if %MISSING_JAVA% equ 1 winget install -e --id Microsoft.OpenJDK.21
    exit /b 0
)

where scoop >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo Using scoop...
    if %MISSING_PYTHON% equ 1 scoop install python
    if %MISSING_JAVA% equ 1 scoop install corretto21
    exit /b 0
)

echo Neither winget nor scoop found. Please install missing dependencies manually.
exit /b 1
