#!/usr/bin/env python3
import sys
import os
import shutil
import subprocess
import time
import urllib.request
import urllib.error
import socket

def is_pingable(port):
    try:
        urllib.request.urlopen(f"http://localhost:{port}/", timeout=1)
        return True
    except:
        return False

def main():
    if len(sys.argv) < 2:
        sys.exit("Usage: test_server_and_client.py <spec_file>")

    spec_file = sys.argv[1]
    
    # Use different ports to avoid conflicts when run in parallel or sequence
    port = 8080 if "stripe.json" in spec_file else 8081
    
    if ("stripe.json" in spec_file or "mega_spec.json" in spec_file) and os.environ.get("RUN_SLOW_TESTS") != "1":
        print(f"Skipping slow test for {spec_file} because RUN_SLOW_TESTS is not 1")
        sys.exit(0)

    spec_basename = os.path.splitext(os.path.basename(spec_file))[0]
    out_dir_sdk = f"out_sdk_integration_{spec_basename}"

    if os.path.exists(out_dir_sdk):
        shutil.rmtree(out_dir_sdk)

    script_dir = os.path.dirname(os.path.abspath(__file__))
    run_with_fallback = os.path.join(script_dir, "run_with_fallback.py")

    print(f"Generating SDK from {spec_file}")
    cmd_gen_sdk = [sys.executable, run_with_fallback, "gradle", "run", f"--args=from_openapi to_sdk -i {spec_file} --output {out_dir_sdk} --tests"]
    if subprocess.run(cmd_gen_sdk).returncode != 0:
        sys.exit("Failed to generate SDK.")

    docker_container_id = None
    if is_pingable(port):
        print(f"Reusing active mock server on port {port}")
    else:
        print(f"Starting docker mock server on port {port}")
        abs_spec = os.path.abspath(spec_file)
        cmd_run = [
            "docker", "run", "--rm", "-d", "-p", f"{port}:4010",
            "-v", f"{abs_spec}:/spec.json", "stoplight/prism:3",
            "mock", "-h", "0.0.0.0", "/spec.json"
        ]
        proc = subprocess.run(cmd_run, capture_output=True, text=True)
        if proc.returncode != 0:
            sys.exit(f"Failed to start docker mock server: {proc.stderr}")
        docker_container_id = proc.stdout.strip()

        max_retries = 30
        retry_count = 0
        server_up = False
        print(f"Waiting for mock server to start on port {port}...")
        while retry_count < max_retries:
            if is_pingable(port):
                server_up = True
                break
            time.sleep(1)
            retry_count += 1
            
        if not server_up:
            if docker_container_id:
                subprocess.run(["docker", "stop", docker_container_id])
            sys.exit("Mock server failed to start!")
        print("Mock server is up!")

    os.chdir(out_dir_sdk)
    
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

    if docker_container_id:
        print("Killing docker mock server...")
        subprocess.run(["docker", "stop", docker_container_id])

    if res.returncode != 0:
        sys.exit("SDK Tests failed!")

    print("Integration Test Passed!")

if __name__ == "__main__":
    main()
