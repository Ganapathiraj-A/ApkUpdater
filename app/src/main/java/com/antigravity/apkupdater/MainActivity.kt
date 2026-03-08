package com.antigravity.apkupdater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.work.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val KEY_CALL_COMPANION_URL = "call_companion_update_url"
    private val DEFAULT_UPDATER_URL = "https://github.com/Ganapathiraj-A/ApkUpdater/releases/download/latest/ApkUpdater.apk"
    private val DEFAULT_AGENT_URL = "https://github.com/Ganapathiraj-A/AgentCompanion/releases/download/latest/AgentCompanion.apk"
    private val DEFAULT_TAMIL_CALENDAR_URL = "https://github.com/Ganapathiraj-A/TamilCalendar/releases/download/latest/TamilCalendar.apk"
    private val DEFAULT_SBB_PAYMENT_URL = "https://github.com/Ganapathiraj-A/SBBPayment/releases/download/latest/SBBPayment.apk"
    private val DEFAULT_GPAY_TEST_URL = "https://github.com/Ganapathiraj-A/GpayTest/releases/download/latest/GpayTest.apk"
    private val DEFAULT_SIG_SCANNER_URL = "https://github.com/Ganapathiraj-A/SriBagavath/releases/download/scanner/SignatureScanner.apk"
    private val DEFAULT_CALL_COMPANION_URL = "https://github.com/Ganapathiraj-A/CallCompanion/releases/download/latest/CallCompanion.apk"

    private val SRI_BAGAVATH_API_URL = "https://api.github.com/repos/Ganapathiraj-A/SriBagavath/releases/latest"
    private val APK_UPDATER_API_URL = "https://api.github.com/repos/Ganapathiraj-A/ApkUpdater/releases/latest"
    private val AGENT_COMPANION_API_URL = "https://api.github.com/repos/Ganapathiraj-A/AgentCompanion/releases/latest"
    private val TAMIL_CALENDAR_API_URL = "https://api.github.com/repos/Ganapathiraj-A/TamilCalendar/releases/latest"
    private val SBB_PAYMENT_API_URL = "https://api.github.com/repos/Ganapathiraj-A/SBBPayment/releases/latest"
    private val GPAY_TEST_API_URL = "https://api.github.com/repos/Ganapathiraj-A/GpayTest/releases/latest"
    private val SIG_SCANNER_API_URL = "https://api.github.com/repos/Ganapathiraj-A/SriBagavath/releases/tags/scanner"
    private val CALL_COMPANION_API_URL = "https://api.github.com/repos/Ganapathiraj-A/CallCompanion/releases/latest"
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
            val callCompanionUrlState = remember { mutableStateOf(getSavedCallCompanionUrl()) }
            val monitorPeriod = remember { mutableStateOf(getMonitorPeriod()) }
            var statusMessage by remember { mutableStateOf(if (isDoubleLaunch) "Settings Mode" else "Ready to Update") }
            var isDownloading by remember { mutableStateOf(false) }
            var downloadProgress by remember { mutableStateOf(0f) }
            var progressText by remember { mutableStateOf("") }
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
                        callCompanionUrl = callCompanionUrlState.value,
                        period = monitorPeriod.value,
                        interval = getMonitorInterval(),
                        onSave = { newUrl, newUpdaterUrl, newAgentUrl, newTamilUrl, newSbbUrl, newGpayUrl, newSigScannerUrl, newCallCompanionUrl, newPeriod, newInterval ->
                            saveUrl(newUrl)
                            saveUpdaterUrl(newUpdaterUrl)
                            saveAgentUrl(newAgentUrl)
                            saveTamilCalendarUrl(newTamilUrl)
                            saveSBBPaymentUrl(newSbbUrl)
                            saveGpayTestUrl(newGpayUrl)
                            saveSigScannerUrl(newSigScannerUrl)
                            saveCallCompanionUrl(newCallCompanionUrl)
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
                            callCompanionUrlState.value = newCallCompanionUrl
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
                        url = currentUrl,
                        updaterUrl = updaterUrl.value,
                        agentUrl = agentUrlState.value,
                        tamilUrl = tamilCalendarUrlState.value,
                        sbbUrl = sbbPaymentUrlState.value,
                        gpayUrl = gpayTestUrlState.value,
                        sigScannerUrl = sigScannerUrlState.value,
                        callCompanionUrl = callCompanionUrlState.value,
                        status = statusMessage,
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        progressText = progressText,
                        lastDownloadedFileName = lastDownloadedFileName,
                        latestVersionTag = latestAvailableTag,
                        onUpdateClick = {
                            isDownloading = true
                            statusMessage = "Checking for Sri Bagavath..."
                            latestAvailableUrl = null
                            checkForNewVersion(SRI_BAGAVATH_API_URL, "SriBagavath.apk") { hasNew, tag, id, downloadUrl, message ->
                                if (hasNew) {
                                    statusMessage = "New version $tag found. Downloading..."
                                    downloadAndInstallApk(downloadUrl ?: currentUrl, tag, id, { progress, text ->
                                        downloadProgress = progress
                                        progressText = text
                                    }) { _, msg ->
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
                                    downloadAndInstallApk(downloadUrl ?: updaterUrl.value, tag, "updater", { progress, text ->
                                        downloadProgress = progress
                                        progressText = text
                                    }) { _, msg ->
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
                                    downloadAndInstallApk(downloadUrl ?: agentUrlState.value, tag, "agent", { progress, text ->
                                        downloadProgress = progress
                                        progressText = text
                                    }) { _, msg ->
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
                                    downloadAndInstallApk(downloadUrl ?: tamilCalendarUrlState.value, tag, "tamil_calendar", { progress, text ->
                                        downloadProgress = progress
                                        progressText = text
                                    }) { _, msg ->
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
                                    downloadAndInstallApk(downloadUrl ?: sbbPaymentUrlState.value, tag, "sbb_payment", { progress, text ->
                                        downloadProgress = progress
                                        progressText = text
                                    }) { _, msg ->
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
                                    downloadAndInstallApk(downloadUrl ?: gpayTestUrlState.value, tag, "gpay_test", { progress, text ->
                                        downloadProgress = progress
                                        progressText = text
                                    }) { _, msg ->
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
                                    downloadAndInstallApk(downloadUrl ?: sigScannerUrlState.value, tag, "sig_scanner", { progress, text ->
                                        downloadProgress = progress
                                        progressText = text
                                    }) { _, msg ->
                                        isDownloading = false
                                        statusMessage = msg
                                    }
                                } else {
                                    isDownloading = false
                                    statusMessage = message ?: "Scanner up to date."
                                }
                            }
                        },
                        onCallCompanionUpdateClick = {
                            isDownloading = true
                            statusMessage = "Checking for Call Companion..."
                            checkForNewVersion(CALL_COMPANION_API_URL, "CallCompanion.apk") { hasNew, tag, id, downloadUrl, message ->
                                if (hasNew || downloadUrl != null) {
                                    statusMessage = "Downloading Call Companion $tag..."
                                    downloadAndInstallApk(downloadUrl ?: callCompanionUrlState.value, tag, "call_companion", { progress, text ->
                                        downloadProgress = progress
                                        progressText = text
                                    }) { _, msg ->
                                        isDownloading = false
                                        statusMessage = msg
                                    }
                                } else {
                                    isDownloading = false
                                    statusMessage = message ?: "Call Companion up to date."
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
                                downloadAndInstallApk(latestAvailableUrl!!, latestAvailableTag, latestAvailableId, { progress, text ->
                                    downloadProgress = progress
                                    progressText = text
                                }) { _, msg ->
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

    private fun getSavedCallCompanionUrl() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CALL_COMPANION_URL, DEFAULT_CALL_COMPANION_URL) ?: DEFAULT_CALL_COMPANION_URL
    private fun saveCallCompanionUrl(url: String) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CALL_COMPANION_URL, url).apply()

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

    private fun downloadAndInstallApk(url: String, tag: String?, id: String?, onProgress: (Float, String) -> Unit, onResult: (Boolean, String) -> Unit) {
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

            // Monitor progress in a thread/coroutine
            Thread {
                var downloading = true
                while (downloading) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        
                        if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                            downloading = false
                            runOnUiThread { onProgress(1f, "100%") }
                        } else if (bytesTotal > 0) {
                            val progress = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                            val percentage = (progress * 100).toInt()
                            runOnUiThread { onProgress(progress, "$percentage%") }
                        }
                    }
                    cursor.close()
                    if (downloading) Thread.sleep(500)
                }
            }.start()

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
    url: String,
    updaterUrl: String,
    agentUrl: String,
    tamilUrl: String,
    sbbUrl: String,
    gpayUrl: String,
    sigScannerUrl: String,
    callCompanionUrl: String,
    status: String, 
    isDownloading: Boolean, 
    downloadProgress: Float,
    progressText: String,
    lastDownloadedFileName: String?, 
    latestVersionTag: String?, 
    onUpdateClick: () -> Unit,
    onUpdaterUpdateClick: () -> Unit, 
    onAgentUpdateClick: () -> Unit,
    onTamilCalendarUpdateClick: () -> Unit,
    onSBBPaymentUpdateClick: () -> Unit,
    onGpayTestUpdateClick: () -> Unit,
    onSigScannerUpdateClick: () -> Unit,
    onCallCompanionUpdateClick: () -> Unit,
    onReinstallCachedClick: () -> Unit, 
    onReinstallLatestClick: () -> Unit, 
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "APK Updater", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = status, style = MaterialTheme.typography.bodyLarge)
            
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Direct Download URL:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            if (isDownloading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = downloadProgress,
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = progressText, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                UpdateActionRow(
                    label = "Check for Sri Bagavath Update",
                    url = url,
                    onClick = onUpdateClick,
                    context = context
                )
                
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
                UpdateActionRow(
                    label = "Update Apk Updater",
                    url = updaterUrl,
                    onClick = onUpdaterUpdateClick,
                    context = context,
                    buttonColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                UpdateActionRow(
                    label = "Update Agent Companion",
                    url = agentUrl,
                    onClick = onAgentUpdateClick,
                    context = context,
                    buttonColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))
                UpdateActionRow(
                    label = "Update Tamil Calendar",
                    url = tamilUrl,
                    onClick = onTamilCalendarUpdateClick,
                    context = context,
                    buttonColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))
                UpdateActionRow(
                    label = "Update SBB Payment",
                    url = sbbUrl,
                    onClick = onSBBPaymentUpdateClick,
                    context = context,
                    buttonColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))
                UpdateActionRow(
                    label = "Update Gpay Test",
                    url = gpayUrl,
                    onClick = onGpayTestUpdateClick,
                    context = context,
                    buttonColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))
                UpdateActionRow(
                    label = "Get Signature Scanner",
                    url = sigScannerUrl,
                    onClick = onSigScannerUpdateClick,
                    context = context,
                    buttonColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))
                UpdateActionRow(
                    label = "Update Call Companion",
                    url = callCompanionUrl,
                    onClick = onCallCompanionUpdateClick,
                    context = context,
                    buttonColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                )
            }
        }
        
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }

        Text(
            text = "Version 1.0.7",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(url: String, updaterUrl: String, agentUrl: String, tamilUrl: String, sbbUrl: String, gpayUrl: String, sigScannerUrl: String, callCompanionUrl: String, period: String, interval: String, onSave: (String, String, String, String, String, String, String, String, String, String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(url) }
    var updaterText by remember { mutableStateOf(updaterUrl) }
    var agentText by remember { mutableStateOf(agentUrl) }
    var tamilText by remember { mutableStateOf(tamilUrl) }
    var sbbText by remember { mutableStateOf(sbbUrl) }
    var gpayText by remember { mutableStateOf(gpayUrl) }
    var sigScannerText by remember { mutableStateOf(sigScannerUrl) }
    var callCompanionText by remember { mutableStateOf(callCompanionUrl) }
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

        OutlinedTextField(
            value = callCompanionText,
            onValueChange = { callCompanionText = it },
            label = { Text("Call Companion Update URL") },
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
            Button(onClick = { onSave(text, updaterText, agentText, tamilText, sbbText, gpayText, sigScannerText, callCompanionText, selectedPeriod, selectedInterval) }) {
                Text("Save")
            }
        }
    }
}

@Composable
fun UpdateActionRow(
    label: String,
    url: String,
    onClick: () -> Unit,
    context: Context,
    buttonColor: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
        ) {
            Text(label, fontSize = 13.sp)
        }
        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("APK Link", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Link Copied", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy, 
                contentDescription = "Copy Link", 
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
