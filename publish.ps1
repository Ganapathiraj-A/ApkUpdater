# publish.ps1 for ApkUpdater

# 1. Set JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# 2. Build the APK
Write-Host "Building ApkUpdater APK..."
& "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" -cp gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain clean assembleDebug

$buildApkPath = "app/build/outputs/apk/debug/app-debug.apk"
$finalApkPath = "ApkUpdater.apk"

if (-Not (Test-Path $buildApkPath)) {
    Write-Error "APK not found at $buildApkPath"
    exit 1
}

# Copy to root and rename
Copy-Item $buildApkPath $finalApkPath

# 3. Get current version from build.gradle.kts
$gradleFile = "app/build.gradle.kts"
$versionName = (Get-Content $gradleFile | Select-String 'versionName = "(.*)"').Matches.Groups[1].Value
$versionCode = (Get-Content $gradleFile | Select-String 'versionCode = (.*)').Matches.Groups[1].Value

Write-Host "Current Version: $versionName ($versionCode)"

# 4. Commit and Push any changes
$branch = git branch --show-current
git add .
git commit -m "Build v$versionName.$versionCode"
git push origin $branch

# 5. Handle GitHub Release
$tagName = "latest"
$versionTag = "v$versionName.$versionCode"

# Delete existing 'latest' release and tag to ensure the URL always points to the newest
Write-Host "Cleaning up existing 'latest' release..."
gh release delete $tagName --yes
git tag -d $tagName
git push origin :refs/tags/$tagName

# Create new 'latest' release
Write-Host "Creating GitHub Release $tagName..."
gh release create $tagName $finalApkPath --title "ApkUpdater Latest" --notes "Automated release for ApkUpdater v$versionName" --latest

# Also create a versioned release for history
Write-Host "Creating versioned release $versionTag..."
gh release create $versionTag $finalApkPath --title "ApkUpdater $versionName" --notes "Versioned release for history"

Write-Host "Publish complete!"
