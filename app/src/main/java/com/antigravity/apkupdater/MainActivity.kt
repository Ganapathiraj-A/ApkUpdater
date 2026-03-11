package com.antigravity.apkupdater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.net.NetworkInterface
import java.net.Inet4Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonObject

data class AppStatus(
    val lastInstalledTime: Long = 0L,
    val lastBuildTime: Long = 0L,
    val installedLabel: String = "not installed",
    val buildLabel: String = "unknown",
    val source: String = "GitHub",
    val isLocalAvailable: Boolean = false
)

class MainActivity : ComponentActivity() {

    private val DEV_API_URL = "https://api.github.com/repos/Ganapathiraj-A/SriBagavath/releases/tags/dev-clean"
    private val PROD_API_URL = "https://api.github.com/repos/Ganapathiraj-A/SriBagavath/releases/latest"
    private val APK_UPDATER_API_URL = "https://api.github.com/repos/Ganapathiraj-A/ApkUpdater/releases/latest"
    private val AGENT_COMPANION_API_URL = "https://api.github.com/repos/Ganapathiraj-A/AgentCompanion/releases/latest"
    private val TAMIL_CALENDAR_API_URL = "https://api.github.com/repos/Ganapathiraj-A/TamilCalendar/releases/latest"
    private val SBB_ADMIN_API_URL = "https://api.github.com/repos/Ganapathiraj-A/SriBagavath/releases/tags/sbb-admin-latest"

    private val client = OkHttpClient()
    private val appStatuses = mutableStateMapOf<String, AppStatus>()
    
    private var statusMessage by mutableStateOf("Manual Update Dashboard")
    private var isDownloading by mutableStateOf(false)
    private var downloadProgress by mutableStateOf(0f)
    private var downloadSpeed by mutableStateOf("")
    private var lastDownloadedFile by mutableStateOf<String?>(null)
    private var downloadElapsedSeconds by mutableStateOf(0)
    private var isLaptopSourceSelected by mutableStateOf(false)
    private var showDownloadScreen by mutableStateOf(true)
    private var currentDownloadId by mutableStateOf<Long?>(null)
    private var lastDownloadDuration by mutableStateOf("")
    private var lastDownloadSpeed by mutableStateOf("")
    private var lastDownloadedFileBuildTime by mutableStateOf<String?>(null)

    private var localServerIp by mutableStateOf<String?>(null)
    private var isScanningByWifi by mutableStateOf(false)
    private var localManifest by mutableStateOf<JsonObject?>(null)
    private var showManualIpInput by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("ApkUpdater", "onCreate called")
        
        try {
            cleanupOldDownloads()
        } catch (e: Exception) {
            android.util.Log.e("ApkUpdater", "Cleanup error", e)
        }

        setContent {
            val scope = rememberCoroutineScope()
            
            LaunchedEffect(Unit) {
                android.util.Log.d("ApkUpdater", "LaunchedEffect(Unit) started")
                try {
                    scanForLocalServer(scope)
                    while(true) {
                        refreshAll()
                        kotlinx.coroutines.delay(TimeUnit.MINUTES.toMillis(15))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ApkUpdater", "Loop error", e)
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        status = statusMessage,
                        isDownloading = isDownloading,
                        onDevUpdateClick = { handleUpdateClick(DEV_API_URL, "SriBagavathDevClean.apk", "com.bhavathpathai.app.dev", "Sri Bagavath (DEV)") },
                        onProdUpdateClick = { handleUpdateClick(PROD_API_URL, "SriBagavath.apk", "com.bhavathpathai.app", "Sri Bagavath (PROD)") },
                        onUpdaterUpdateClick = { handleUpdateClick(APK_UPDATER_API_URL, "ApkUpdater.apk", "com.antigravity.apkupdater", "Apk Updater") },
                        onAgentUpdateClick = { handleUpdateClick(AGENT_COMPANION_API_URL, "AgentCompanion.apk", "com.antigravity.companion", "Agent Companion") },
                        onTamilCalendarUpdateClick = { handleUpdateClick(TAMIL_CALENDAR_API_URL, "TamilCalendar.apk", "com.tamil.calendar", "Tamil Calendar") },
                        onSbbAdminUpdateClick = { handleUpdateClick(SBB_ADMIN_API_URL, "SBBAdmin.apk", "com.bhavathpathai.admin", "SBB Admin") },
                        onCopyUrlClick = { label, url -> copyToClipboard(label, url) },
                        onAddTesterClick = { launchUrl("https://play.google.com/console/u/0/developers/6210751302207441757/app/4976096709784341895/tracks/internal-testing?tab=testers") },
                        onInstallClick = { lastDownloadedFile?.let { installApk(it) } },
                        onReinstallClick = { showReinstallDialog(statusMessage) { statusMessage = it } },
                        onRefreshClick = { scope.launch { scanForLocalServer(scope); refreshAll() } },
                        onResetClick = { resetApp(); refreshAll(); statusMessage = "App Reset: Cache \u0026 Downloads cleared." },
                        onScanClick = { scope.launch { scanForLocalServer(scope) } },
                        onManualIpClick = { showManualIpInput = true },
                        onManualIpSubmit = { ip ->
                            scope.launch {
                                statusMessage = "Checking $ip..."
                                if (checkTargetIp(ip)) {
                                    showManualIpInput = false
                                } else {
                                    statusMessage = "Could not connect to $ip"
                                }
                            }
                        },
                        localServerIp = localServerIp,
                        localManifest = localManifest,
                        isScanningByWifi = isScanningByWifi,
                        showManualIpDialog = showManualIpInput,
                        onCloseManualIpDialog = { showManualIpInput = false },
                        lastDownloadedFile = lastDownloadedFile,
                        lastDownloadedFileBuildTime = lastDownloadedFileBuildTime,
                        appStatuses = appStatuses,
                        downloadProgress = downloadProgress,
                        downloadSpeed = downloadSpeed,
                        downloadElapsedSeconds = downloadElapsedSeconds,
                        isLaptopSourceSelected = isLaptopSourceSelected,
                        onSourceToggle = { isLaptopSourceSelected = it },
                        onCancelClick = { currentDownloadId?.let { cancelDownload(it) } },
                        showDownloadScreen = showDownloadScreen,
                        onShowDownloadScreenToggle = { showDownloadScreen = it },
                        lastDownloadDuration = lastDownloadDuration,
                        lastDownloadSpeed = lastDownloadSpeed
                    )
                }
            }
        }
    }

    private fun copyToClipboard(label: String, url: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("apk_url", url)
            clipboard.setPrimaryClip(clip)
            statusMessage = "$label URL Copied"
            launchUrl(url)
        } catch (e: Exception) {
            statusMessage = "Failed to copy/launch URL"
        }
    }

    private fun launchUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("ApkUpdater", "Launch error", e)
        }
    }

    private fun refreshAll() {
        listOf("com.bhavathpathai.app", "com.bhavathpathai.app.dev", "com.bhavathpathai.admin", "com.antigravity.apkupdater", "com.antigravity.companion", "com.tamil.calendar").forEach { pkg ->
            appStatuses[pkg] = getInitialAppStatus(pkg)
        }
        fetchBuildTime(DEV_API_URL, "com.bhavathpathai.app.dev", localManifest, "SriBagavathDevClean.apk") { appStatuses["com.bhavathpathai.app.dev"] = it }
        fetchBuildTime(PROD_API_URL, "com.bhavathpathai.app", localManifest, "SriBagavath.apk") { appStatuses["com.bhavathpathai.app"] = it }
        fetchBuildTime(APK_UPDATER_API_URL, "com.antigravity.apkupdater", localManifest, "ApkUpdater.apk") { appStatuses["com.antigravity.apkupdater"] = it }
        fetchBuildTime(AGENT_COMPANION_API_URL, "com.antigravity.companion", localManifest, "AgentCompanion.apk") { appStatuses["com.antigravity.companion"] = it }
        fetchBuildTime(TAMIL_CALENDAR_API_URL, "com.tamil.calendar", localManifest, "TamilCalendar.apk") { appStatuses["com.tamil.calendar"] = it }
        fetchBuildTime(SBB_ADMIN_API_URL, "com.bhavathpathai.admin", localManifest, "SBBAdmin.apk") { appStatuses["com.bhavathpathai.admin"] = it }
    }

    private fun getWifiIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && intf.name.contains("wlan")) return addr.hostAddress
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private suspend fun checkTargetIp(targetIp: String, silent: Boolean = false): Boolean {
        val scanClient = OkHttpClient.Builder().connectTimeout(1, TimeUnit.SECONDS).readTimeout(1, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("http://$targetIp:8080/manifest.json").build()
        return try {
            val response = scanClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = Json.parseToJsonElement(body).jsonObject
                if (json["server"]?.jsonPrimitive?.content == "SriBagavath-ApkServer") {
                    localServerIp = targetIp
                    localManifest = json
                    if (!silent) statusMessage = "Connected to Laptop at $targetIp"
                    getSharedPreferences("apk_updater", Context.MODE_PRIVATE).edit().putString("last_laptop_ip", targetIp).apply()
                    true
                } else false
            } else false
        } catch (e: Exception) { false }
    }

    private suspend fun scanForLocalServer(scope: CoroutineScope) {
        if (isScanningByWifi) return
        isScanningByWifi = true
        val lastIp = getSharedPreferences("apk_updater", Context.MODE_PRIVATE).getString("last_laptop_ip", null)
        if (lastIp != null && checkTargetIp(lastIp, silent = true)) {
            isScanningByWifi = false
            statusMessage = "Connected to Laptop at $lastIp"
            return
        }
        val myIp = getWifiIp() ?: run { isScanningByWifi = false; return }
        val prefix = myIp.substring(0, myIp.lastIndexOf('.') + 1)
        withContext(Dispatchers.IO) {
            (1..254).toList().chunked(25).forEach { chunk ->
                if (localServerIp != null) return@forEach
                chunk.map { i -> scope.launch(Dispatchers.IO) { if (checkTargetIp(prefix + i, silent = true)) refreshAll() } }
                kotlinx.coroutines.delay(200)
            }
            kotlinx.coroutines.delay(2000)
        }
        isScanningByWifi = false
    }

    private fun handleUpdateClick(apiUrl: String, assetName: String, packageName: String, displayName: String) {
        isDownloading = true
        downloadProgress = 0f
        downloadSpeed = ""
        downloadElapsedSeconds = 0
        currentDownloadId = null
        
        if (isLaptopSourceSelected) {
            val laptopApk = localManifest?.get("apks")?.jsonArray?.find { 
                val name = it.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                name.equals(assetName, ignoreCase = true) || name.contains(assetName, ignoreCase = true)
            }
            val laptopUrl = laptopApk?.jsonObject?.get("url")?.jsonPrimitive?.content
            val laptopModified = laptopApk?.jsonObject?.get("modified")?.jsonPrimitive?.content
            val laptopTime = if (laptopModified != null) getRelativeTime(laptopModified) else null

            if (laptopUrl != null) {
                statusMessage = "Downloading $displayName from Laptop..."
                downloadAndInstallApk(laptopUrl, "Laptop", laptopModified, packageName, { msg, file ->
                    isDownloading = false; statusMessage = msg; lastDownloadedFile = file; lastDownloadedFileBuildTime = laptopTime; refreshAll()
                }, { p, s, e -> downloadProgress = p; downloadSpeed = s; downloadElapsedSeconds = e }, { id -> currentDownloadId = id })
            } else { isDownloading = false; statusMessage = "Error: $displayName not found on Laptop." }
        } else {
            checkForGithubVersion(apiUrl, assetName) { githubUrl, tag, publishedAt ->
                val githubTime = if (publishedAt != null) getRelativeTime(publishedAt) else null
                if (githubUrl != null) {
                    statusMessage = "Downloading $displayName from GitHub..."
                    downloadAndInstallApk(githubUrl, tag, publishedAt, packageName, { msg, file ->
                        isDownloading = false; statusMessage = msg; lastDownloadedFile = file; lastDownloadedFileBuildTime = githubTime; refreshAll()
                    }, { p, s, e -> downloadProgress = p; downloadSpeed = s; downloadElapsedSeconds = e }, { id -> currentDownloadId = id })
                } else { isDownloading = false; statusMessage = "Error: $displayName not found on GitHub." }
            }
        }
    }

    private fun cancelDownload(downloadId: Long) {
        try {
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).remove(downloadId)
            statusMessage = "Download cancelled."
            isDownloading = false
            downloadProgress = 0f
        } catch (e: Exception) {}
    }

    private fun showReinstallDialog(currentStatus: String, setStatus: (String) -> Unit) {
        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val apkFiles = downloadsDir?.listFiles()?.filter { it.name.endsWith(".apk") }?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (apkFiles.isEmpty()) { setStatus("No APKs found."); return }
        val items = apkFiles.map { it.name }.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("Select APK").setItems(items) { _, which ->
            installApk(apkFiles[which].name)
            setStatus("Reinstalling ${apkFiles[which].name}...")
        }.setNegativeButton("Cancel", null).show()
    }

    private fun getInitialAppStatus(packageName: String): AppStatus {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            AppStatus(lastInstalledTime = info.lastUpdateTime, installedLabel = formatRelativeTime(info.lastUpdateTime))
        } catch (e: Exception) { AppStatus() }
    }

    private fun fetchBuildTime(apiUrl: String, packageName: String, localManifest: JsonObject?, assetName: String, onResult: (AppStatus) -> Unit) {
        localManifest?.get("apks")?.jsonArray?.forEach { apk ->
            val name = apk.jsonObject["name"]?.jsonPrimitive?.content ?: ""
            if (name.contains(assetName, ignoreCase = true)) {
                val modified = apk.jsonObject["modified"]?.jsonPrimitive?.content ?: return@forEach
                val buildTime = parseIsoDate(modified) ?: return@forEach
                runOnUiThread { onResult(getInitialAppStatus(packageName).copy(lastBuildTime = buildTime, buildLabel = formatRelativeTime(buildTime) + " (WiFi)", isLocalAvailable = true)) }
                return
            }
        }
        val request = Request.Builder().url(apiUrl).header("Cache-Control", "no-cache").build()
        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val publishedAt = Json.parseToJsonElement(response.body?.string() ?: "").jsonObject["published_at"]?.jsonPrimitive?.content
                    if (publishedAt != null) {
                        val buildTime = parseIsoDate(publishedAt) ?: return@Thread
                        runOnUiThread { onResult(getInitialAppStatus(packageName).copy(lastBuildTime = buildTime, buildLabel = formatRelativeTime(buildTime) + " (GitHub)")) }
                    }
                }
            } catch (e: Exception) {}
        }.start()
    }

    private fun formatRelativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            diff < 0 -> "just now"
            days > 0 -> "$days d ago"
            hours > 0 -> "$hours h $minutes m ago"
            else -> "$minutes m ago"
        }
    }

    private fun checkForGithubVersion(apiUrl: String, assetName: String, onResult: (String?, String?, String?) -> Unit) {
        val request = Request.Builder().url(apiUrl).header("Cache-Control", "no-cache").build()
        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = Json.parseToJsonElement(response.body?.string() ?: "").jsonObject
                    val tag = json["tag_name"]?.jsonPrimitive?.content ?: "latest"
                    val publishedAt = json["published_at"]?.jsonPrimitive?.content
                    val assets = json["assets"]?.jsonArray
                    val downloadUrl = assets?.find { it.jsonObject["name"]?.jsonPrimitive?.content == assetName || it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content
                    runOnUiThread { onResult(downloadUrl, tag, publishedAt) }
                } else runOnUiThread { onResult(null, null, null) }
            } catch (e: Exception) { runOnUiThread { onResult(null, null, null) } }
        }.start()
    }

    private fun getRelativeTime(publishedAt: String?): String = parseIsoDate(publishedAt)?.let { formatRelativeTime(it) } ?: "recently"

    private fun parseIsoDate(isoString: String?): Long? {
        if (isoString == null) return null
        val clean = isoString.substringBefore('.').replace("Z", "")
        val formats = listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                if (isoString.contains("Z") || fmt.contains("'T'")) sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(clean)?.time
            } catch (e: Exception) {}
        }
        return null
    }

    private fun downloadAndInstallApk(url: String, tag: String?, publishedAt: String?, packageName: String, onResult: (String, String?) -> Unit, onUpdate: (Float, String, Int) -> Unit, onDownloadStarted: (Long) -> Unit) {
        if (publishedAt != null) {
            try {
                val buildTime = parseIsoDate(publishedAt)
                if (buildTime != null) {
                    val info = packageManager.getPackageInfo(packageName, 0)
                    if (info.lastUpdateTime >= buildTime) {
                        runOnUiThread {
                            android.app.AlertDialog.Builder(this).setTitle("Confirm").setMessage("Already up to date. Proceed?").setPositiveButton("Yes") { _, _ -> 
                                val id = startDownload(url, tag, onResult, onUpdate)
                                if (id != -1L) onDownloadStarted(id)
                            }.setNegativeButton("No") { _, _ -> onResult("Cancelled.", null) }.show()
                        }
                        return
                    }
                }
            } catch (e: Exception) {}
        }
        val id = startDownload(url, tag, onResult, onUpdate)
        if (id != -1L) onDownloadStarted(id)
    }

    private fun startDownload(url: String, tag: String?, onResult: (String, String?) -> Unit, onUpdate: (Float, String, Int) -> Unit): Long {
        try {
            getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }
            val fileName = "update_${System.currentTimeMillis()}.apk"
            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(DownloadManager.Request(Uri.parse(url)).setTitle("Update").setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName))
            
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val startTime = System.currentTimeMillis()
            val progressRunnable = object : Runnable {
                override fun run() {
                    val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
                    if (cursor != null && cursor.moveToFirst()) {
                        val downloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                        cursor.close()
                        if (total > 0) runOnUiThread { onUpdate(downloaded.toFloat() / total, "", ((System.currentTimeMillis() - startTime) / 1000).toInt()) }
                        if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) handler.postDelayed(this, 1000)
                    }
                }
            }
            handler.post(progressRunnable)

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    if (i?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) == downloadId) {
                        try { unregisterReceiver(this) } catch (e: Exception) {}
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ installApk(fileName) }, 500)
                        onResult("Download complete.", fileName)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            else registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            return downloadId
        } catch (e: Exception) { return -1L }
    }

    private fun installApk(fileName: String) {
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun resetApp() {
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = downloadManager.query(DownloadManager.Query())
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                if (idIndex != -1) {
                    downloadManager.remove(cursor.getLong(idIndex))
                }
            }
            cursor.close()
        }
        getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }
    }

    private fun cleanupOldDownloads() {
        getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()?.forEach { if (it.name.endsWith(".apk") && System.currentTimeMillis() - it.lastModified() > 3600000) it.delete() }
    }
}

@Composable
fun MainScreen(
    status: String, isDownloading: Boolean, onDevUpdateClick: () -> Unit, onProdUpdateClick: () -> Unit,
    onUpdaterUpdateClick: () -> Unit, onAgentUpdateClick: () -> Unit, onTamilCalendarUpdateClick: () -> Unit,
    onSbbAdminUpdateClick: () -> Unit, onCopyUrlClick: (String, String) -> Unit, onAddTesterClick: () -> Unit,
    onInstallClick: () -> Unit, onReinstallClick: () -> Unit, onRefreshClick: () -> Unit, onResetClick: () -> Unit,
    onScanClick: () -> Unit, onManualIpClick: () -> Unit, onManualIpSubmit: (String) -> Unit,
    localServerIp: String?, localManifest: JsonObject?, isScanningByWifi: Boolean,
    showManualIpDialog: Boolean, onCloseManualIpDialog: () -> Unit, lastDownloadedFile: String?,
    lastDownloadedFileBuildTime: String?, appStatuses: Map<String, AppStatus>, downloadProgress: Float,
    downloadSpeed: String, downloadElapsedSeconds: Int, isLaptopSourceSelected: Boolean,
    onSourceToggle: (Boolean) -> Unit, onCancelClick: () -> Unit, showDownloadScreen: Boolean,
    onShowDownloadScreenToggle: (Boolean) -> Unit, lastDownloadDuration: String, lastDownloadSpeed: String
) {
    var showUrlDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        AlertDialog(onDismissRequest = { showSettingsDialog = false }, title = { Text("Settings") }, text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Source: ${if (isLaptopSourceSelected) "Laptop" else "GitHub"}")
                    Switch(checked = isLaptopSourceSelected, onCheckedChange = onSourceToggle)
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Show Download UI")
                    Switch(checked = showDownloadScreen, onCheckedChange = onShowDownloadScreenToggle)
                }
            }
        }, confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Close") } })
    }

    if (showUrlDialog) {
        AlertDialog(onDismissRequest = { showUrlDialog = false }, title = { Text("Copy URL") }, text = {
            Column {
                listOf("PROD" to "https://github.com/Ganapathiraj-A/SriBagavath/releases/download/latest/SriBagavath.apk",
                       "DEV" to "https://github.com/Ganapathiraj-A/SriBagavath/releases/download/dev-clean/SriBagavathDevClean.apk",
                       "SBB Admin" to "https://github.com/Ganapathiraj-A/SriBagavath/releases/download/sbb-admin-latest/SBBAdmin.apk",
                       "Updater" to "https://github.com/Ganapathiraj-A/ApkUpdater/releases/download/latest/ApkUpdater.apk"
                ).forEach { (label, url) -> TextButton(onClick = { onCopyUrlClick(label, url); showUrlDialog = false }, modifier = Modifier.fillMaxWidth()) { Text(label) } }
            }
        }, confirmButton = { TextButton(onClick = { showUrlDialog = false }) { Text("Close") } })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "APK Updater v1.6.1", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = status, style = MaterialTheme.typography.bodyLarge)
            
            if (isDownloading && showDownloadScreen) {
                LinearProgressIndicator(progress = downloadProgress, modifier = Modifier.fillMaxWidth().height(8.dp))
                Button(onClick = onCancelClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Cancel") }
            } else {
                Button(onClick = onDevUpdateClick, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Update DEV") }
                AppStatusDisplay(appStatuses["com.bhavathpathai.app.dev"] ?: AppStatus())
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onProdUpdateClick, modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("Update PROD") }
                AppStatusDisplay(appStatuses["com.bhavathpathai.app"] ?: AppStatus())
                
                if (lastDownloadedFile != null) Button(onClick = onInstallClick, modifier = Modifier.fillMaxWidth()) { Text("Install Last Downloaded") }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                
                listOf("Apk Updater" to onUpdaterUpdateClick, "Agent" to onAgentUpdateClick, "Tamil Calendar" to onTamilCalendarUpdateClick, "SBB Admin" to onSbbAdminUpdateClick).forEach { (l, click) ->
                    OutlinedButton(onClick = click, modifier = Modifier.fillMaxWidth()) { Text("Update $l") }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        
        Surface(tonalElevation = 4.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { showSettingsDialog = true }) { Text("Settings") }
                TextButton(onClick = onScanClick) { Text("Scan WiFi") }
                TextButton(onClick = { showUrlDialog = true }) { Text("URLs") }
            }
        }
    }

    if (showManualIpDialog) {
        var ip by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = onCloseManualIpDialog, title = { Text("Enter IP") }, text = { TextField(value = ip, onValueChange = { ip = it }) },
            confirmButton = { Button(onClick = { onManualIpSubmit(ip) }) { Text("Connect") } })
    }
}

@Composable
fun AppStatusDisplay(status: AppStatus) {
    val isNew = status.lastBuildTime > status.lastInstalledTime && status.lastInstalledTime != 0L
    Text(text = "${status.installedLabel} / ${status.buildLabel}", style = MaterialTheme.typography.labelSmall, color = if (isNew) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Gray)
}
