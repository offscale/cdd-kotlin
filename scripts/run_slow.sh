#!/usr/bin/env bash

if [ "${RUN_SLOW_TESTS:-0}" = "1" ] || [ "${RUN_SLOW_TESTS:-0}" = "true" ]; then
    exec "$@"
else
    echo "Skipping slow tests. Set RUN_SLOW_TESTS=1 to enable."
    exit 0
fi
