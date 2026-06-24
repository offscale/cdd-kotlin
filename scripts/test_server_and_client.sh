#!/bin/bash
set -e

SPEC_FILE=$1

OUT_DIR_SERVER="out_server_integration"
OUT_DIR_SDK="out_sdk_integration"
rm -rf "$OUT_DIR_SERVER" "$OUT_DIR_SDK"

# 1. Generate the Server Artifact
echo "Generating Mock Server from $SPEC_FILE"
./gradlew run --args="from_openapi to_server -i ${SPEC_FILE} --output ${OUT_DIR_SERVER} --tests"

# 2. Generate the SDK Artifact
echo "Generating SDK from $SPEC_FILE"
./gradlew run --args="from_openapi to_sdk -i ${SPEC_FILE} --output ${OUT_DIR_SDK} --tests"

cd "$OUT_DIR_SERVER"

# 3. Start the Server in Seeded Mode
echo "Building the Mock Server"
../gradlew :server:jvmMainClasses

echo "Starting Server in Ephemeral Seeded Mode"
../gradlew :server:jvmRun --args="--ephemeral --seed" &
SERVER_PID=$!

PORT=8080
MAX_RETRIES=30
RETRY_COUNT=0

echo "Waiting for server to start on port $PORT..."
while ! curl -s http://localhost:$PORT/ > /dev/null; do
    if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
        echo "Server failed to start!"
        kill $SERVER_PID
        exit 1
    fi
    sleep 2
    RETRY_COUNT=$((RETRY_COUNT+1))
done

echo "Server is up!"

# 4. Test the Client SDK
cd "../$OUT_DIR_SDK"
if [ -d "$HOME/Library/Android/sdk" ]; then
    echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
elif [ -n "$ANDROID_HOME" ]; then
    echo "sdk.dir=$ANDROID_HOME" > local.properties
fi

echo "Running SDK Tests against the mock server"
../gradlew test || {
    echo "SDK Tests failed!"
    kill $SERVER_PID
    exit 1
}

echo "Killing server..."
kill $SERVER_PID

echo "Integration Test Passed!"