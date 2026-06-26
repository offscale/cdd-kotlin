#!/usr/bin/env python3
import sys
import subprocess
import os
import shutil

def is_tool(name):
    """Check whether `name` is on PATH and marked as executable."""
    from shutil import which
    return which(name) is not None

def main():
    if len(sys.argv) < 2:
        sys.exit("Usage: run_with_fallback.py <tool> [args]")

    tool = sys.argv[1]
    args = sys.argv[2:]
    is_win = sys.platform == "win32"

    if tool == "gradle":
        if is_tool("java"):
            gradlew = "gradlew.bat" if is_win else "gradlew"
            gradlew_path = None
            for p in ["./", "../", "../../"]:
                if os.path.exists(os.path.join(p, gradlew)):
                    gradlew_path = os.path.join(p, gradlew)
                    break
            
            if not gradlew_path:
                gradlew_path = f"./{gradlew}" if not is_win else gradlew
                
            cmd = [gradlew_path] + args
            sys.exit(subprocess.run(cmd).returncode)
        elif is_tool("docker"):
            cwd = os.getcwd()
            cmd = ["docker", "run", "--rm", "-v", f"{cwd}:/app", "-w", "/app", "-e", "GRADLE_USER_HOME=/app/.gradle_home", "gradle:9.0.0-jdk21", "gradle"] + args
            sys.exit(subprocess.run(cmd).returncode)
        else:
            sys.exit("Error: Neither java nor docker is available.")

    elif tool == "python":
        if is_tool("python3") or is_tool("python"):
            py = "python3" if is_tool("python3") else "python"
            sys.exit(subprocess.run([py] + args).returncode)
        elif is_tool("docker"):
            cwd = os.getcwd()
            cmd = ["docker", "run", "--rm", "-v", f"{cwd}:/app", "-w", "/app", "python:3-slim", "python3"] + args
            sys.exit(subprocess.run(cmd).returncode)
        else:
            sys.exit("Error: Neither python nor docker is available.")

    elif tool == "build_wasm":
        gradle_cmd = [sys.executable, sys.argv[0], "gradle", "--gradle-user-home", ".gradle_home", "compileProductionExecutableKotlinWasmWasiOptimize"]
        res = subprocess.run(gradle_cmd)
        if res.returncode != 0:
            sys.exit(res.returncode)
        os.makedirs("bin", exist_ok=True)
        src = os.path.join("build", "compileSync", "wasmWasi", "main", "productionExecutable", "optimized", "cdd-kotlin.wasm")
        dest = os.path.join("bin", "cdd-kotlin.wasm")
        shutil.copyfile(src, dest)
    else:
        sys.exit(f"Unknown tool: {tool}")

if __name__ == "__main__":
    main()
