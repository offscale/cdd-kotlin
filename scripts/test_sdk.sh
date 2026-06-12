#!/bin/bash
set -e

SPEC_FILE=$1
BASE_PATH=$2

OUT_DIR="out_sdk"
rm -rf "$OUT_DIR"
./gradlew run --args="from_openapi to_sdk -i ${SPEC_FILE} --output ${OUT_DIR} --tests"

# Create settings.gradle.kts to make it a standalone project
cat << 'SET_EOF' > "$OUT_DIR"/settings.gradle.kts
rootProject.name = "generated-sdk"
SET_EOF

cd "$OUT_DIR"
../gradlew test || {
    echo "Tests failed!"
    exit 1
}

