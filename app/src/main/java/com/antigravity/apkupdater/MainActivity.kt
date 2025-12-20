package com.antigravity.apkupdater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.app.NotificationManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.work.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val PREFS_NAME = "ApkUpdaterPrefs"
    private val KEY_URL = "update_url"
    private val KEY_LAST_LAUNCH = "last_launch_ms"
    private val KEY_MONITOR_PERIOD = "monitor_period" // Off, 1d, 3d, 1w
    private val KEY_MONITOR_INTERVAL = "monitor_interval" // 1m, 15m, 1h
    private val KEY_LAST_TAG = "last_installed_tag"
    private val KEY_LAST_ID = "last_installed_id"
    private val KEY_UPDATER_URL = "updater_update_url"
    private val GITHUB_API_URL = "https://api.github.com/repos/Ganapathiraj-A/SriBagavath/releases/latest"
    private val DEFAULT_URL = "https://github.com/Ganapathiraj-A/SriBagavath/releases/download/latest/SriBagavath.apk"
    private val DEFAULT_UPDATER_URL = "https://github.com/Ganapathiraj-A/ApkUpdater/releases/download/latest/ApkUpdater.apk"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isDoubleLaunch = checkDoubleLaunch()

        setContent {
            var showSettings by remember { mutableStateOf(isDoubleLaunch) }
            var currentUrl by remember { mutableStateOf(getSavedUrl()) }
            var updaterUrl by remember { mutableStateOf(getSavedUpdaterUrl()) }
            var monitorPeriod by remember { mutableStateOf(getMonitorPeriod()) }
            var statusMessage by remember { mutableStateOf(if (isDoubleLaunch) "Settings Mode" else "Ready to Update") }
            var isDownloading by remember { mutableStateOf(false) }
            var lastDownloadedFileName by remember { mutableStateOf<String?>(null) }
            
            // State for reinstalling latest
            var latestAvailableUrl by remember { mutableStateOf<String?>(null) }
            var latestAvailableTag by remember { mutableStateOf<String?>(null) }
            var latestAvailableId by remember { mutableStateOf<String?>(null) }

            // Check if last downloaded file exists
            val checkLastFile = {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastId = prefs.getString(KEY_LAST_ID, null)
                if (lastId != null) {
                    val fileName = "update_$lastId.apk"
                    val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                    if (file.exists()) {
                        lastDownloadedFileName = fileName
                    } else {
                        lastDownloadedFileName = null
                    }
                } else {
                    lastDownloadedFileName = null
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                if (showSettings) {
                    SettingsScreen(
                        url = currentUrl,
                        updaterUrl = updaterUrl,
                        period = monitorPeriod,
                        interval = getMonitorInterval(),
                        onSave = { newUrl, newUpdaterUrl, newPeriod, newInterval ->
                            saveUrl(newUrl)
                            saveUpdaterUrl(newUpdaterUrl)
                            saveMonitorPeriod(newPeriod)
                            saveMonitorInterval(newInterval)
                            setupBackgroundWork(newPeriod, newInterval)
                            currentUrl = newUrl
                            updaterUrl = newUpdaterUrl
                            monitorPeriod = newPeriod
                            showSettings = false
                            statusMessage = "Settings Saved"
                            Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
                        },
                        onCancel = { 
                            showSettings = false 
                            if (isDoubleLaunch) {
                                statusMessage = "Exited Settings"
                            }
                        }
                    )
                } else {
                    MainScreen(
                        status = statusMessage,
                        isDownloading = isDownloading,
                        lastDownloadedFileName = lastDownloadedFileName,
                        latestVersionTag = latestAvailableTag,
                        onUpdateClick = {
                            isDownloading = true
                            statusMessage = "Checking for new version..."
                            latestAvailableUrl = null // Reset
                            checkForNewVersion { hasNew, tag, id, downloadUrl, message ->
                                if (hasNew) {
                                    statusMessage = "New version $tag found. Downloading..."
                                    downloadAndInstallApk(downloadUrl ?: currentUrl, tag, id) { _, msg ->
                                        isDownloading = false
                                        statusMessage = msg
                                        checkLastFile()
                                    }
                                } else {
                                    isDownloading = false
                                    statusMessage = message ?: "Already up to date."
                                    // Enable reinstall
                                    latestAvailableTag = tag
                                    latestAvailableId = id
                                    latestAvailableUrl = downloadUrl
                                }
                            }
                        },
                        onUpdaterUpdateClick = {
                            isDownloading = true
                            statusMessage = "Downloading Updater Update..."
                            downloadAndInstallApk(updaterUrl, "Latest", "updater") { _, msg ->
                                isDownloading = false
                                statusMessage = msg
                            }
                        },
                        onReinstallCachedClick = {
                            lastDownloadedFileName?.let { installApk(it) }
                        },
                        onReinstallLatestClick = {
                            if (latestAvailableUrl != null) {
                                isDownloading = true
                                statusMessage = "Re-downloading $latestAvailableTag..."
                                downloadAndInstallApk(latestAvailableUrl!!, latestAvailableTag, latestAvailableId) { _, msg ->
                                    isDownloading = false
                                    statusMessage = msg
                                    checkLastFile()
                                }
                            }
                        },
                        onSettingsClick = { showSettings = true }
                    )
                }
            }

            // Auto-trigger on launch if NOT double launch
            LaunchedEffect(Unit) {
                checkLastFile()
            }
        }
    }

    private fun checkDoubleLaunch(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastLaunch = prefs.getLong(KEY_LAST_LAUNCH, 0L)
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_LAUNCH, now).apply()

        // If launched within 1.5 seconds of previous launch
        return (now - lastLaunch) < 1500
    }

    private fun getSavedUrl() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
    private fun saveUrl(url: String) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_URL, url).apply()

    private fun getSavedUpdaterUrl() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_UPDATER_URL, DEFAULT_UPDATER_URL) ?: DEFAULT_UPDATER_URL
    private fun saveUpdaterUrl(url: String) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_UPDATER_URL, url).apply()

    private fun getMonitorPeriod() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_MONITOR_PERIOD, "Off") ?: "Off"
    private fun saveMonitorPeriod(period: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiry = when (period) {
            "1 Day" -> System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
            "3 Days" -> System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3)
            "1 Week" -> System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
            else -> 0L
        }
        prefs.edit().putString(KEY_MONITOR_PERIOD, period).putLong("monitoring_expiry_ms", expiry).apply()
    }
    private fun getMonitorInterval() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_MONITOR_INTERVAL, "15 Mins") ?: "15 Mins"
    private fun saveMonitorInterval(interval: String) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_MONITOR_INTERVAL, interval).apply()

    private fun setupBackgroundWork(period: String, interval: String) {
        val workManager = WorkManager.getInstance(this)
        if (period == "Off") {
            workManager.cancelAllWorkByTag("update_check")
            workManager.cancelAllWorkByTag("update_check_1m") // Also cancel 1-min specific work
        } else {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val intervalMinutes = when (interval) {
                "1 Min" -> 15L // WorkManager minimum is 15 mins for periodic work
                "15 Mins" -> 15L
                "1 Hour" -> 60L
                else -> 60L
            }

            val request = PeriodicWorkRequestBuilder<UpdateWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag("update_check")
                .build()

            workManager.enqueueUniquePeriodicWork(
                "update_check_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            
            // If user wants 1 min, we use a different approach: chain one-time workers
            if (interval == "1 Min") {
                scheduleOneMinuteUpdate()
            } else {
                // Ensure 1-min specific work is cancelled if not selected
                workManager.cancelAllWorkByTag("update_check_1m")
            }
        }
    }

    private fun scheduleOneMinuteUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .addTag("update_check_1m")
            .build()
            
        WorkManager.getInstance(this).enqueueUniqueWork(
            "update_check_1m_work",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun checkForNewVersion(onResult: (Boolean, String?, String?, String?, String?) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder().url(GITHUB_API_URL).build()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_LAST_ID, "")

        // Move networking to a thread
        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = Json.parseToJsonElement(body).jsonObject
                    val latestId = json["id"]?.jsonPrimitive?.content ?: ""
                    val latestTag = json["tag_name"]?.jsonPrimitive?.content ?: "latest"
                    
                    // Try to find the APK asset for dynamic download URL
                    val assets = json["assets"]?.let { if (it is kotlinx.serialization.json.JsonArray) it else null }
                    val downloadUrl = assets?.find { 
                        it.jsonObject["name"]?.jsonPrimitive?.content == "SriBagavath.apk" 
                    }?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content

                    if (latestId.isNotEmpty() && latestId != savedId) {
                        runOnUiThread { onResult(true, latestTag, latestId, downloadUrl, null) }
                    } else {
                        // Pass details anyway for reinstall
                        runOnUiThread { onResult(false, latestTag, latestId, downloadUrl, "Up to date ($latestTag)") }
                    }
                } else {
                    runOnUiThread { onResult(false, null, null, null, "Update check failed: ${response.code}") }
                }
            } catch (e: Exception) {
                runOnUiThread { onResult(false, null, null, null, "Error checking update: ${e.message}") }
            }
        }.start()
    }

    private fun downloadAndInstallApk(url: String, tag: String?, id: String?, onResult: (Boolean, String) -> Unit) {
        try {
            // Clean up old update files first
            getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()?.forEach { 
                if (it.name.startsWith("update") && it.name.endsWith(".apk")) {
                    it.delete()
                }
            }

            val fileName = if (id != null) "update_$id.apk" else "update.apk"
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri)
                .setTitle("Downloading Update")
                .setDescription("Downloading version ${tag ?: "latest"}...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadId = downloadManager.enqueue(request)

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val completedDownloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (completedDownloadId == downloadId) {
                        unregisterReceiver(this)
                        if (id != null) {
                            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                                .putString(KEY_LAST_ID, id)
                                .putString(KEY_LAST_TAG, tag ?: "latest")
                                .apply()
                        }
                        installApk(fileName)
                        onResult(true, "Download complete. Installing...")
                    }
                }
            }

            registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        } catch (e: Exception) {
            onResult(false, "Error: ${e.message}")
        }
    }

    private fun installApk(fileName: String) {
        try {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                
                // Debugging Toast
                Toast.makeText(this, "Opening: $uri", Toast.LENGTH_LONG).show()
                
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Apk file not found: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Installation Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(status: String, isDownloading: Boolean, lastDownloadedFileName: String?, latestVersionTag: String?, onUpdateClick: () -> Unit, onUpdaterUpdateClick: () -> Unit, onReinstallCachedClick: () -> Unit, onReinstallLatestClick: () -> Unit, onSettingsClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "APK Updater", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = status, style = MaterialTheme.typography.bodyLarge)
            
            Spacer(modifier = Modifier.height(32.dp))
            if (isDownloading) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onUpdateClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Check for Sri Bagavath Update")
                }
                
                // Reinstall Latest from Server
                if (latestVersionTag != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onReinstallLatestClick, 
                        modifier = Modifier.fillMaxWidth(),
                         colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("Reinstall $latestVersionTag (Download)")
                    }
                }
                
                // Reinstall Cached
                if (lastDownloadedFileName != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onReinstallCachedClick, 
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Reinstall Cached File")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onUpdaterUpdateClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Update Apk Updater")
                }
            }
        }
        
        TextButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Text("Launch Settings", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(url: String, updaterUrl: String, period: String, interval: String, onSave: (String, String, String, String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(url) }
    var updaterText by remember { mutableStateOf(updaterUrl) }
    var selectedPeriod by remember { mutableStateOf(period) }
    var selectedInterval by remember { mutableStateOf(interval) }
    
    val periods = listOf("Off", "1 Day", "3 Days", "1 Week")
    val intervals = listOf("1 Min", "15 Mins", "1 Hour")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Configuration", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Sri Bagavath Update URL") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = updaterText,
            onValueChange = { updaterText = it },
            label = { Text("Apk Updater Update URL") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Monitoring Duration", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            periods.forEach { p ->
                FilterChip(
                    selected = selectedPeriod == p,
                    onClick = { selectedPeriod = p },
                    label = { Text(p, fontSize = 10.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Check Interval", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            intervals.forEach { i ->
                FilterChip(
                    selected = selectedInterval == i,
                    onClick = { selectedInterval = i },
                    label = { Text(i, fontSize = 10.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Button(onClick = { onSave(text, updaterText, selectedPeriod, selectedInterval) }) {
                Text("Save")
            }
        }
    }
}
