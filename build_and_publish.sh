#!/bin/bash
export JAVA_HOME=/usr/lib/jvm/java-1.21.0-openjdk-amd64
echo "Building ApkUpdater..."
./gradlew assembleDebug

echo "---------------------------------------------------"
echo "Build Complete: app-debug.apk"
echo "---------------------------------------------------"

# Note: We use debug build for simplicity as per previous sessions
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
TAG="latest"

echo "Verifying APK..."
if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH"
    exit 1
fi

echo "Publishing to tag: $TAG"
cp "$APK_PATH" "ApkUpdater.apk"
gh release delete "$TAG" --yes --repo Ganapathiraj-A/ApkUpdater
git tag -d "$TAG"
git push origin :refs/tags/"$TAG"
gh release create "$TAG" "ApkUpdater.apk" --title "Apk Updater Latest" --notes "Reliability improvements and size optimization" --repo Ganapathiraj-A/ApkUpdater
rm "ApkUpdater.apk"

echo "---------------------------------------------------"
echo "Apk Updater Published!"
echo "---------------------------------------------------"
