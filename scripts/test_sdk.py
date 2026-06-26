#!/usr/bin/env python3
import sys
import os
import shutil
import subprocess

def main():
    if len(sys.argv) < 2:
        sys.exit("Usage: test_sdk.py <spec_file> [base_path]")

    spec_file = sys.argv[1]
    if ("stripe.json" in spec_file or "mega_spec.json" in spec_file) and os.environ.get("RUN_SLOW_TESTS") != "1":
        print(f"Skipping slow test for {spec_file} because RUN_SLOW_TESTS is not 1")
        sys.exit(0)

    spec_basename = os.path.splitext(os.path.basename(spec_file))[0]
    out_dir = f"out_sdk_{spec_basename}"

    if os.path.exists(out_dir):
        shutil.rmtree(out_dir)

    script_dir = os.path.dirname(os.path.abspath(__file__))
    run_with_fallback = os.path.join(script_dir, "run_with_fallback.py")

    cmd_generate = [sys.executable, run_with_fallback, "gradle", "run", f"--args=from_openapi to_sdk -i {spec_file} --output {out_dir} --tests"]
    if subprocess.run(cmd_generate).returncode != 0:
        sys.exit("Failed to generate SDK.")

    os.chdir(out_dir)

    android_home = os.environ.get("ANDROID_HOME")
    home = os.path.expanduser("~")
    mac_sdk = os.path.join(home, "Library", "Android", "sdk")
    
    if os.path.isdir(mac_sdk):
        with open("local.properties", "w") as f:
            f.write(f"sdk.dir={mac_sdk}\n")
    elif android_home:
        with open("local.properties", "w") as f:
            f.write(f"sdk.dir={android_home}\n")

    cmd_test = [sys.executable, run_with_fallback, "gradle", "allTests"]
    if subprocess.run(cmd_test).returncode != 0:
        sys.exit("Tests failed!")

if __name__ == "__main__":
    main()
