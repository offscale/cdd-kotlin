#!/usr/bin/env python3
import sys
import subprocess
import os
import shutil

def main():
    if len(sys.argv) < 2:
        sys.exit("Usage: xplat.py <task> [args]")

    task = sys.argv[1]
    is_win = sys.platform == "win32"
    gradlew = "gradlew.bat" if is_win else "./gradlew"

    if task == "gradle":
        sys.exit(subprocess.run([gradlew] + sys.argv[2:]).returncode)
    elif task == "build_wasm":
        env = os.environ.copy()
        r = subprocess.run([gradlew, "--gradle-user-home", ".gradle_home", "compileProductionExecutableKotlinWasmWasiOptimize"], env=env)
        if r.returncode != 0:
            sys.exit(r.returncode)
        
        os.makedirs("bin", exist_ok=True)
        src = os.path.join("build", "compileSync", "wasmWasi", "main", "productionExecutable", "optimized", "cdd-kotlin.wasm")
        dest = os.path.join("bin", "cdd-kotlin.wasm")
        shutil.copyfile(src, dest)
    else:
        sys.exit(f"Unknown task: {task}")

if __name__ == "__main__":
    main()
