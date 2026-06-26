#!/usr/bin/env python3
import sys
import os
import shutil
import subprocess
import time
import signal
import urllib.request
import urllib.error

def cleanup_port():
    try:
        if shutil.which("lsof"):
            output = subprocess.check_output(["lsof", "-i", ":8080", "-sTCP:LISTEN", "-t"]).decode().strip()
            if output:
                for pid in output.split('\n'):
                    try:
                        os.kill(int(pid), signal.SIGKILL)
                    except ProcessLookupError:
                        pass
        elif shutil.which("fuser"):
            subprocess.run(["fuser", "-k", "-9", "8080/tcp"], stderr=subprocess.DEVNULL)
    except subprocess.CalledProcessError:
        pass

def main():
    if len(sys.argv) < 2:
        sys.exit("Usage: test_server_and_client.py <spec_file>")

    cleanup_port()

    spec_file = sys.argv[1]
    if ("stripe.json" in spec_file or "mega_spec.json" in spec_file) and os.environ.get("RUN_SLOW_TESTS") != "1":
        print(f"Skipping slow test for {spec_file} because RUN_SLOW_TESTS is not 1")
        sys.exit(0)

    spec_basename = os.path.splitext(os.path.basename(spec_file))[0]

    out_dir_server = f"out_server_integration_{spec_basename}"
    out_dir_sdk = f"out_sdk_integration_{spec_basename}"

    for d in [out_dir_server, out_dir_sdk]:
        if os.path.exists(d):
            shutil.rmtree(d)

    script_dir = os.path.dirname(os.path.abspath(__file__))
    run_with_fallback = os.path.join(script_dir, "run_with_fallback.py")

    print(f"Generating Mock Server from {spec_file}")
    cmd_gen_server = [sys.executable, run_with_fallback, "gradle", "run", f"--args=from_openapi to_server -i {spec_file} --output {out_dir_server} --tests"]
    if subprocess.run(cmd_gen_server).returncode != 0:
        sys.exit("Failed to generate server.")

    print(f"Generating SDK from {spec_file}")
    cmd_gen_sdk = [sys.executable, run_with_fallback, "gradle", "run", f"--args=from_openapi to_sdk -i {spec_file} --output {out_dir_sdk} --tests"]
    if subprocess.run(cmd_gen_sdk).returncode != 0:
        sys.exit("Failed to generate SDK.")

    os.chdir(out_dir_server)

    print("Building the Mock Server")
    cmd_build = [sys.executable, run_with_fallback, "gradle", ":server:jvmMainClasses"]
    if subprocess.run(cmd_build).returncode != 0:
        sys.exit("Failed to build server.")

    print("Starting Server in Ephemeral Seeded Mode")
    cmd_run = [sys.executable, run_with_fallback, "gradle", ":server:jvmRun", "--args=--ephemeral --seed"]
    server_process = subprocess.Popen(cmd_run)

    port = 8080
    max_retries = 300
    retry_count = 0

    print(f"Waiting for server to start on port {port}...")
    server_up = False
    while retry_count < max_retries:
        try:
            req = urllib.request.urlopen(f"http://localhost:{port}/", timeout=1)
            server_up = True
            break
        except urllib.error.URLError:
            time.sleep(2)
            retry_count += 1
            
    if not server_up:
        print("Server failed to start!")
        server_process.kill()
        server_process.wait()
        cleanup_port()
        sys.exit(1)

    print("Server is up!")

    os.chdir(f"../{out_dir_sdk}")
    
    android_home = os.environ.get("ANDROID_HOME")
    home = os.path.expanduser("~")
    mac_sdk = os.path.join(home, "Library", "Android", "sdk")
    
    if os.path.isdir(mac_sdk):
        with open("local.properties", "w") as f:
            f.write(f"sdk.dir={mac_sdk}\n")
    elif android_home:
        with open("local.properties", "w") as f:
            f.write(f"sdk.dir={android_home}\n")

    print("Running SDK Tests against the mock server")
    cmd_test = [sys.executable, run_with_fallback, "gradle", "allTests"]
    res = subprocess.run(cmd_test)

    print("Killing server...")
    server_process.kill()
    server_process.wait()
    cleanup_port()

    if res.returncode != 0:
        sys.exit("SDK Tests failed!")

    print("Integration Test Passed!")

if __name__ == "__main__":
    main()
