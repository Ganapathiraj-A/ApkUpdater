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
    private val KEY_AGENT_URL = "agent_update_url"
    private val KEY_TAMIL_CALENDAR_URL = "tamil_calendar_update_url"
    private val KEY_SBB_PAYMENT_URL = "sbb_payment_update_url"
    private val KEY_GPAY_TEST_URL = "gpay_test_update_url"
    private val KEY_SIG_SCANNER_URL = "sig_scanner_update_url"
    private val DEFAULT_UPDATER_URL = "https://github.com/Ganapathiraj-A/ApkUpdater/releases/download/latest/ApkUpdater.apk"
    private val DEFAULT_AGENT_URL = "https://github.com/Ganapathiraj-A/AgentCompanion/releases/download/latest/AgentCompanion.apk"
    private val DEFAULT_TAMIL_CALENDAR_URL = "https://github.com/Ganapathiraj-A/TamilCalendar/releases/download/latest/TamilCalendar.apk"
    private val DEFAULT_SBB_PAYMENT_URL = "https://github.com/Ganapathiraj-A/SBBPayment/releases/download/latest/SBBPayment.apk"
    private val DEFAULT_GPAY_TEST_URL = "https://github.com/Ganapathiraj-A/GpayTest/releases/download/latest/GpayTest.apk"
    private val DEFAULT_SIG_SCANNER_URL = "https://github.com/Ganapathiraj-A/SriBagavath/releases/download/scanner/SignatureScanner.apk"

    private val SRI_BAGAVATH_API_URL = "https://api.github.com/repos/Ganapathiraj-A/SriBagavath/releases/latest"
    private val APK_UPDATER_API_URL = "https://api.github.com/repos/Ganapathiraj-A/ApkUpdater/releases/latest"
    private val AGENT_COMPANION_API_URL = "https://api.github.com/repos/Ganapathiraj-A/AgentCompanion/releases/latest"
    private val TAMIL_CALENDAR_API_URL = "https://api.github.com/repos/Ganapathiraj-A/TamilCalendar/releases/latest"
    private val SBB_PAYMENT_API_URL = "https://api.github.com/repos/Ganapathiraj-A/SBBPayment/releases/latest"
    private val GPAY_TEST_API_URL = "https://api.github.com/repos/Ganapathiraj-A/GpayTest/releases/latest"
    private val SIG_SCANNER_API_URL = "https://api.github.com/repos/Ganapathiraj-A/SriBagavath/releases/tags/scanner"
    private val GITHUB_API_URL = SRI_BAGAVATH_API_URL
    private val DEFAULT_URL = "https://github.com/Ganapathiraj-A/SriBagavath/releases/download/latest/SriBagavath.apk"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isDoubleLaunch = checkDoubleLaunch()

        setContent {
            var showSettings by remember { mutableStateOf(isDoubleLaunch) }
            var currentUrl by remember { mutableStateOf(getSavedUrl()) }
            val updaterUrl = remember { mutableStateOf(getSavedUpdaterUrl()) }
            val agentUrlState = remember { mutableStateOf(getSavedAgentUrl()) }
            val tamilCalendarUrlState = remember { mutableStateOf(getSavedTamilCalendarUrl()) }
            val sbbPaymentUrlState = remember { mutableStateOf(getSavedSBBPaymentUrl()) }
            val gpayTestUrlState = remember { mutableStateOf(getSavedGpayTestUrl()) }
            val sigScannerUrlState = remember { mutableStateOf(getSavedSigScannerUrl()) }
            val monitorPeriod = remember { mutableStateOf(getMonitorPeriod()) }
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
                        updaterUrl = updaterUrl.value,
                        agentUrl = agentUrlState.value,
                        tamilUrl = tamilCalendarUrlState.value,
                        sbbUrl = sbbPaymentUrlState.value,
                        gpayUrl = gpayTestUrlState.value,
                        sigScannerUrl = sigScannerUrlState.value,
                        period = monitorPeriod.value,
                        interval = getMonitorInterval(),
                        onSave = { newUrl, newUpdaterUrl, newAgentUrl, newTamilUrl, newSbbUrl, newGpayUrl, newSigScannerUrl, newPeriod, newInterval ->
                            saveUrl(newUrl)
                            saveUpdaterUrl(newUpdaterUrl)
                            saveAgentUrl(newAgentUrl)
                            saveTamilCalendarUrl(newTamilUrl)
                            saveSBBPaymentUrl(newSbbUrl)
                            saveGpayTestUrl(newGpayUrl)
                            saveSigScannerUrl(newSigScannerUrl)
                            saveMonitorPeriod(newPeriod)
                            saveMonitorInterval(newInterval)
                            setupBackgroundWork(newPeriod, newInterval)
                            currentUrl = newUrl
                            updaterUrl.value = newUpdaterUrl
                            agentUrlState.value = newAgentUrl
                            tamilCalendarUrlState.value = newTamilUrl
                            sbbPaymentUrlState.value = newSbbUrl
                            gpayTestUrlState.value = newGpayUrl
                            sigScannerUrlState.value = newSigScannerUrl
                            monitorPeriod.value = newPeriod
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
                            statusMessage = "Checking for Sri Bagavath..."
                            latestAvailableUrl = null
                            checkForNewVersion(SRI_BAGAVATH_API_URL, "SriBagavath.apk") { hasNew, tag, id, downloadUrl, message ->
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
                                    latestAvailableTag = tag
                                    latestAvailableId = id
                                    latestAvailableUrl = downloadUrl
                                }
                            }
                        },
                        onUpdaterUpdateClick = {
                            isDownloading = true
                            statusMessage = "Checking for Updater Update..."
                            checkForNewVersion(APK_UPDATER_API_URL, "ApkUpdater.apk") { hasNew, tag, id, downloadUrl, message ->
                                if (hasNew || downloadUrl != null) {
                                    statusMessage = "Downloading Updater $tag..."
                                    downloadAndInstallApk(downloadUrl ?: updaterUrl.value, tag, "updater") { _, msg ->
                                        isDownloading = false
                                        statusMessage = msg
                                    }
                                } else {
                                    isDownloading = false
                                    statusMessage = message ?: "Updater up to date."
                                }
                            }
                        },
                        onAgentUpdateClick = {
                            isDownloading = true
                            statusMessage = "Checking for Agent Update..."
                            checkForNewVersion(AGENT_COMPANION_API_URL, "AgentCompanion.apk") { hasNew, tag, id, downloadUrl, message ->
                                if (hasNew || downloadUrl != null) {
                                    statusMessage = "Downloading Agent $tag..."
                                    downloadAndInstallApk(downloadUrl ?: agentUrlState.value, tag, "agent") { _, msg ->
                                        isDownloading = false
                                        statusMessage = msg
                                    }
                                } else {
                                    isDownloading = false
                                    statusMessage = message ?: "Agent up to date."
                                }
                            }
                        },
                        onTamilCalendarUpdateClick = {
                            isDownloading = true
                            statusMessage = "Checking for Tamil Calendar..."
                            checkForNewVersion(TAMIL_CALENDAR_API_URL, "TamilCalendar.apk") { hasNew, tag, id, downloadUrl, message ->
                                if (hasNew || downloadUrl != null) {
                                    statusMessage = "Downloading Tamil Calendar $tag..."
                                    downloadAndInstallApk(downloadUrl ?: tamilCalendarUrlState.value, tag, "tamil_calendar") { _, msg ->
                                        isDownloading = false
                                        statusMessage = msg
                                    }
                                } else {
                                    isDownloading = false
                                    statusMessage = message ?: "Tamil Calendar up to date."
                                }
                            }
                        },
                        onSBBPaymentUpdateClick = {
                            isDownloading = true
                            statusMessage = "Checking for SBB Payment..."
                            checkForNewVersion(SBB_PAYMENT_API_URL, "SBBPayment.apk") { hasNew, tag, id, downloadUrl, message ->
                                if (hasNew || downloadUrl != null) {
                                    statusMessage = "Downloading SBB Payment $tag..."
                                    downloadAndInstallApk(downloadUrl ?: sbbPaymentUrlState.value, tag, "sbb_payment") { _, msg ->
                                        isDownloading = false
                                        statusMessage = msg
                                    }
                                } else {
                                    isDownloading = false
                                    statusMessage = message ?: "SBB Payment up to date."
                                }
                            }
                        },
                        onGpayTestUpdateClick = {
                            isDownloading = true
                            statusMessage = "Checking for Gpay Test..."
                            checkForNewVersion(GPAY_TEST_API_URL, "GpayTest.apk") { hasNew, tag, id, downloadUrl, message ->
                                if (hasNew || downloadUrl != null) {
                                    statusMessage = "Downloading Gpay Test $tag..."
                                    downloadAndInstallApk(downloadUrl ?: gpayTestUrlState.value, tag, "gpay_test") { _, msg ->
                                        isDownloading = false
                                        statusMessage = msg
                                    }
                                } else {
                                    isDownloading = false
                                    statusMessage = message ?: "Gpay Test up to date."
                                }
                            }
                        },
                        onSigScannerUpdateClick = {
                            isDownloading = true
                            statusMessage = "Checking for Signature Scanner..."
                            checkForNewVersion(SIG_SCANNER_API_URL, "SignatureScanner.apk") { hasNew, tag, id, downloadUrl, message ->
                                if (hasNew || downloadUrl != null) {
                                    statusMessage = "Downloading Scanner $tag..."
                                    downloadAndInstallApk(downloadUrl ?: sigScannerUrlState.value, tag, "sig_scanner") { _, msg ->
                                        isDownloading = false
                                        statusMessage = msg
                                    }
                                } else {
                                    isDownloading = false
                                    statusMessage = message ?: "Scanner up to date."
                                }
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

    private fun getSavedAgentUrl() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_AGENT_URL, DEFAULT_AGENT_URL) ?: DEFAULT_AGENT_URL
    private fun saveAgentUrl(url: String) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_AGENT_URL, url).apply()

    private fun getSavedTamilCalendarUrl() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_TAMIL_CALENDAR_URL, DEFAULT_TAMIL_CALENDAR_URL) ?: DEFAULT_TAMIL_CALENDAR_URL
    private fun saveTamilCalendarUrl(url: String) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_TAMIL_CALENDAR_URL, url).apply()

    private fun getSavedSBBPaymentUrl() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SBB_PAYMENT_URL, DEFAULT_SBB_PAYMENT_URL) ?: DEFAULT_SBB_PAYMENT_URL
    private fun saveSBBPaymentUrl(url: String) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_SBB_PAYMENT_URL, url).apply()

    private fun getSavedGpayTestUrl() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_GPAY_TEST_URL, DEFAULT_GPAY_TEST_URL) ?: DEFAULT_GPAY_TEST_URL
    private fun saveGpayTestUrl(url: String) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_GPAY_TEST_URL, url).apply()

    private fun getSavedSigScannerUrl() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SIG_SCANNER_URL, DEFAULT_SIG_SCANNER_URL) ?: DEFAULT_SIG_SCANNER_URL
    private fun saveSigScannerUrl(url: String) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_SIG_SCANNER_URL, url).apply()

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

    private fun checkForNewVersion(apiUrl: String, assetName: String, onResult: (Boolean, String?, String?, String?, String?) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder().url(apiUrl).build()
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
                    var downloadUrl = assets?.find { 
                        it.jsonObject["name"]?.jsonPrimitive?.content == assetName 
                    }?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content

                    // Fallback for SriBagavath renaming
                    if (downloadUrl == null && assetName == "SriBagavath.apk") {
                        downloadUrl = assets?.find { 
                            it.jsonObject["name"]?.jsonPrimitive?.content == "BagavathPathai.apk"
                        }?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content
                    }

                    if (latestId.isNotEmpty() && latestId != savedId) {
                        runOnUiThread { onResult(true, latestTag, latestId, downloadUrl, null) }
                    } else {
                        // Pass details anyway for reinstall
                        runOnUiThread { onResult(false, latestTag, latestId, downloadUrl, if (downloadUrl == null) "Asset '$assetName' not found. Available assets: ${assets?.joinToString { it.jsonObject["name"]?.jsonPrimitive?.content ?: "" }}" else "Up to date ($latestTag)") }
                    }
                } else {
                    val errorMsg = if (response.code == 404) "Release or Repository not found (404)" else "Update check failed: ${response.code}"
                    runOnUiThread { onResult(false, null, null, null, errorMsg) }
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
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = downloadManager.query(query)
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = cursor.getInt(columnIndex)
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = cursor.getInt(reasonIndex)

                            cursor.close()
                            unregisterReceiver(this)

                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                if (id != null) {
                                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                                        .putString(KEY_LAST_ID, id)
                                        .putString(KEY_LAST_TAG, tag ?: "latest")
                                        .apply()
                                }
                                installApk(fileName)
                                onResult(true, "Download complete. Installing...")
                            } else {
                                onResult(false, "Download failed (Status: $status, Reason: $reason). Delete old update and try again.")
                            }
                        } else {
                            cursor.close()
                            unregisterReceiver(this)
                            onResult(false, "Download record not found.")
                        }
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
                val size = file.length()
                if (size == 0L) {
                    Toast.makeText(this, "Apk file is empty (0 bytes). Download may have failed.", Toast.LENGTH_LONG).show()
                    return
                }
                
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                Toast.makeText(this, "File missing at: ${file.absolutePath}. External storage might be full or inaccessible.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Installation Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    status: String, 
    isDownloading: Boolean, 
    lastDownloadedFileName: String?, 
    latestVersionTag: String?, 
    onUpdateClick: () -> Unit,
    onUpdaterUpdateClick: () -> Unit, 
    onAgentUpdateClick: () -> Unit,
    onTamilCalendarUpdateClick: () -> Unit,
    onSBBPaymentUpdateClick: () -> Unit,
    onGpayTestUpdateClick: () -> Unit,
    onSigScannerUpdateClick: () -> Unit,
    onReinstallCachedClick: () -> Unit, 
    onReinstallLatestClick: () -> Unit, 
    onSettingsClick: () -> Unit
) {
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
                
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onAgentUpdateClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Update Agent Companion")
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onTamilCalendarUpdateClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Update Tamil Calendar")
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onSBBPaymentUpdateClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Update SBB Payment")
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onGpayTestUpdateClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Update Gpay Test")
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onSigScannerUpdateClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Get Signature Scanner")
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
fun SettingsScreen(url: String, updaterUrl: String, agentUrl: String, tamilUrl: String, sbbUrl: String, gpayUrl: String, sigScannerUrl: String, period: String, interval: String, onSave: (String, String, String, String, String, String, String, String, String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(url) }
    var updaterText by remember { mutableStateOf(updaterUrl) }
    var agentText by remember { mutableStateOf(agentUrl) }
    var tamilText by remember { mutableStateOf(tamilUrl) }
    var sbbText by remember { mutableStateOf(sbbUrl) }
    var gpayText by remember { mutableStateOf(gpayUrl) }
    var sigScannerText by remember { mutableStateOf(sigScannerUrl) }
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
        
        OutlinedTextField(
            value = agentText,
            onValueChange = { agentText = it },
            label = { Text("Agent Companion Update URL") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = tamilText,
            onValueChange = { tamilText = it },
            label = { Text("Tamil Calendar Update URL") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = sbbText,
            onValueChange = { sbbText = it },
            label = { Text("SBB Payment Update URL") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = gpayText,
            onValueChange = { gpayText = it },
            label = { Text("Gpay Test Update URL") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = sigScannerText,
            onValueChange = { sigScannerText = it },
            label = { Text("Signature Scanner URL") },
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
            Button(onClick = { onSave(text, updaterText, agentText, tamilText, sbbText, gpayText, sigScannerText, selectedPeriod, selectedInterval) }) {
                Text("Save")
            }
        }
    }
}
