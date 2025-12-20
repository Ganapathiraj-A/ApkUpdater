#!/bin/bash
set -e

echo "Building ApkUpdater APK and Syncing to Drive..."
# Sync to GDrive using the gradle task
./gradlew assembleDebug -Pg

echo "Build and Sync Complete."
echo "Generating Public Link..."
# Try to get the link using rclone (requires the file to be shared or public folder)
rclone link "gdrive:Code/Build/ApkUpdater/app-debug.apk" || echo "Note: Link generation failed. Please ensure the file is shared manually."
