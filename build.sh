#!/bin/bash
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
if [ -f "./gradlew" ]; then
    ./gradlew assembleDebug
else
    echo "Gradle wrapper not found."
    exit 1
fi
