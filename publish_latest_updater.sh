#!/bin/bash

# Configuration
APP_NAME="ApkUpdater"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_APK="${APP_NAME}.apk"
TAG_NAME="latest"
RELEASE_TITLE="Latest ApkUpdater"
RELEASE_NOTES="Direct download link for ApkUpdater to avoid Google Drive corruption issues."

echo "Starting Build & Publish Process for $APP_NAME..."

# 1. Build APK
echo "Building APK..."
./gradlew assembleDebug
if [ $? -ne 0 ]; then
    echo "Gradle build failed!"
    exit 1
fi

# 2. Verify APK exists
if [ ! -f "$APK_PATH" ]; then
    echo "APK not found at $APK_PATH"
    exit 1
fi

# 3. Rename and Move to Root
cp "$APK_PATH" "$OUTPUT_APK"
echo "APK copied to $OUTPUT_APK"

# 4. Github Release Management
echo "Publishing to tag: $TAG_NAME"

# Delete existing release and tag
gh release delete "$TAG_NAME" --yes || echo "No existing release to delete"
gh api repos/:owner/:repo/git/refs/tags/"$TAG_NAME" -X DELETE || echo "No existing tag to delete"

sleep 2

# Create new release
gh release create "$TAG_NAME" "$OUTPUT_APK" \
    --title "$RELEASE_TITLE" \
    --notes "$RELEASE_NOTES" \
    --prerelease=false \
    --latest

echo "---------------------------------------------------"
echo "Persistent URL:"
echo "https://github.com/Ganapathiraj-A/ApkUpdater/releases/download/$TAG_NAME/$OUTPUT_APK"
echo "---------------------------------------------------"
