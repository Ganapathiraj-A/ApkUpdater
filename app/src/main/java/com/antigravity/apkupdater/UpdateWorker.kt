package com.antigravity.apkupdater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val PREFS_NAME = "ApkUpdaterPrefs"
    private val KEY_LAST_TAG = "last_seen_tag"
    private val KEY_LAST_ID = "last_seen_id"
    private val KEY_EXPIRY = "monitoring_expiry_ms"
    private val CHANNEL_ID = "update_notifications"

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong(KEY_EXPIRY, 0L)

        // Check if monitoring has expired
        if (expiry > 0 && System.currentTimeMillis() > expiry) {
            // Expired, let's not fail but we shouldn't continue periodic work 
            // Better to cancel from MainActivity, but here we just returns success and stop logic
            return Result.success()
        }

        val url = "https://api.github.com/repos/Ganapathiraj-A/SriBagavath/releases/latest"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return Result.failure()
                val json = Json.parseToJsonElement(body).jsonObject
                val latestId = json["id"]?.jsonPrimitive?.content ?: ""
                val latestTag = json["tag_name"]?.jsonPrimitive?.content ?: "latest"
                val savedId = prefs.getString(KEY_LAST_ID, "")

                if (latestId.isNotEmpty() && latestId != savedId) {
                    showNotification(latestTag)
                    prefs.edit()
                        .putString(KEY_LAST_ID, latestId)
                        .putString(KEY_LAST_TAG, latestTag)
                        .apply()
                }
                
                // Reschedule if 1-minute interval is active
                val interval = prefs.getString("monitor_interval", "15 Mins")
                if (interval == "1 Min" && expiry > System.currentTimeMillis()) {
                   rescheduleOneMinute()
                }
                
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun rescheduleOneMinute() {
        val request = androidx.work.OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInitialDelay(1, java.util.concurrent.TimeUnit.MINUTES)
            .addTag("update_check_1m")
            .build()
            
        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "update_check_1m_work",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun showNotification(tag: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Update Notifications", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("New Build Available")
            .setContentText("Version $tag is ready for install.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
