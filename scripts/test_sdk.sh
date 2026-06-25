#!/bin/bash
set -e

SPEC_FILE=$1
BASE_PATH=$2
SPEC_BASENAME=$(basename "$SPEC_FILE" .json)

OUT_DIR="out_sdk_${SPEC_BASENAME}"
rm -rf "$OUT_DIR"
./gradlew run --args="from_openapi to_sdk -i ${SPEC_FILE} --output ${OUT_DIR} --tests"

cd "$OUT_DIR"

if [ -d "$HOME/Library/Android/sdk" ]; then
    echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
elif [ -n "$ANDROID_HOME" ]; then
    echo "sdk.dir=$ANDROID_HOME" > local.properties
fi

../gradlew test || {
    echo "Tests failed!"
    exit 1
}

