#!/bin/bash
# Check if gradle is installed
if command -v gradle >/dev/null 2>&1; then
    gradle assembleDebug
else
    echo "Gradle not found. Please install gradle or use the wrapper if available."
    exit 1
fi
