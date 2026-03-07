# publish.ps1 for ApkUpdater

# 1. Set JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# 2. Build the APK
Write-Host "Building ApkUpdater APK..."
& "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" -cp gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleDebug

$apkPath = "app/build/outputs/apk/debug/app-debug.apk"
if (-Not (Test-Path $apkPath)) {
    Write-Error "APK not found at $apkPath"
    exit 1
}

# 2. Get current version from build.gradle.kts
$gradleFile = "app/build.gradle.kts"
$versionName = (Get-Content $gradleFile | Select-String 'versionName = "(.*)"').Matches.Groups[1].Value
$versionCode = (Get-Content $gradleFile | Select-String 'versionCode = (.*)').Matches.Groups[1].Value

Write-Host "Current Version: $versionName ($versionCode)"

# 3. Create a unique tag based on time or version
$tag = "v$versionName.$versionCode"
$releaseTitle = "ApkUpdater $versionName"

# 4. Commit and Push any changes
git add .
git commit -m "Build $tag"
git push origin master

# 5. Create GitHub Release
Write-Host "Creating GitHub Release $tag..."
gh release create $tag $apkPath --title $releaseTitle --notes "Automated release for ApkUpdater with CallCompanion integration" --latest

Write-Host "Publish complete!"
