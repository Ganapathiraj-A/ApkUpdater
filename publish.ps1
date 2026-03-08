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
$content = Get-Content $gradleFile
$versionName = ($content | Select-String 'versionName = "(.*)"').Matches.Groups[1].Value
$versionCode = ($content | Select-String 'versionCode = (.*)').Matches.Groups[1].Value

if (-Not $versionName) { $versionName = "1.1.2" }
if (-Not $versionCode) { $versionCode = "12" }

Write-Host "Detected Version: $versionName ($versionCode)"

# 4. Commit and Push any changes
$branch = git branch --show-current
git add .
git commit -m "Build v$versionName.$versionCode"
git push origin $branch

# 5. Handle GitHub Releases
$latestTag = "latest"
$versionTag = "v$versionName.$versionCode"

# Clean up 'latest'
Write-Host "Cleaning up existing 'latest' release..."
gh release delete $latestTag --yes 2>$null
git tag -d $latestTag 2>$null
git push origin :refs/tags/$latestTag 2>$null

# Clean up versioned tag
Write-Host "Cleaning up existing versioned release $versionTag..."
gh release delete $versionTag --yes 2>$null
git tag -d $versionTag 2>$null
git push origin :refs/tags/$versionTag 2>$null

# Create 'latest' release
Write-Host "Creating GitHub Release $latestTag..."
gh release create $latestTag $finalApkPath --title "ApkUpdater Latest" --notes "Automated release for ApkUpdater v$versionName" --latest

# Create versioned release
Write-Host "Creating versioned release $versionTag..."
gh release create $versionTag $finalApkPath --title "ApkUpdater $versionName" --notes "Versioned release for history"

Write-Host "Publish complete!"
