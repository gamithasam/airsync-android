package com.sameerasw.airsync.utils

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.Process
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID

/** Reports app identity only; no screen contents or usage history leave the phone. */
object HandoffReporter {
    const val PREFS = "handoff"
    const val ENABLED = "enabled"
    @Volatile private var peerVersion = 0
    @Volatile private var refresh = false
    private var job: Job? = null

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }
    fun setPeerVersion(version: Int) { peerVersion = version; refresh = true }
    fun requestCurrent() { refresh = true }

    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val usage = app.getSystemService(UsageStatsManager::class.java)
            val keyguard = app.getSystemService(KeyguardManager::class.java)
            val power = app.getSystemService(PowerManager::class.java)
            val homes = app.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .map { it.activityInfo.packageName }.toSet()
            var cursor = 0L
            var lastSent: String? = null
            var active = false
            var unavailableSent = false
            var tracker = HandoffForegroundTracker(UUID.randomUUID().toString())
            try {
                while (isActive) {
                    val connected = WebSocketUtil.isConnected() && peerVersion == 1
                    val allowed = connected && preferences.getBoolean(ENABLED, false) && hasUsageAccess(app)
                    if (!allowed) {
                        if (active && connected) sendUnavailable()
                        active = false
                        cursor = 0L
                        lastSent = null
                        unavailableSent = false
                        delay(1000)
                        continue
                    }
                    if (!active) { active = true; tracker = HandoffForegroundTracker(UUID.randomUUID().toString()) }
                    if (refresh) { refresh = false; lastSent = null; unavailableSent = false }
                    // A locked phone has no app available for handoff.
                    if (!power.isInteractive || keyguard.isKeyguardLocked) {
                        if (!unavailableSent) unavailableSent = sendUnavailable()
                        lastSent = null
                        delay(1000)
                        continue
                    }
                    try {
                        val now = System.currentTimeMillis()
                        val events = usage.queryEvents(if (cursor == 0L) now - 86_400_000 else cursor, now)
                        val event = UsageEvents.Event()
                        while (events != null && events.hasNextEvent()) {
                            events.getNextEvent(event)
                            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                                tracker.resume(event.packageName, event.timeStamp)
                            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                                tracker.pause(event.packageName)
                            }
                        }
                        cursor = now
                        val pkg = tracker.packageName
                        val id = tracker.eventID
                        if (tracker.isForeground && pkg != null && id != null && pkg != app.packageName &&
                            pkg != "com.android.systemui" && pkg !in homes &&
                            app.packageManager.getLaunchIntentForPackage(pkg) != null) {
                            if (id == lastSent) { delay(1000); continue }
                            val info = app.packageManager.getApplicationInfo(pkg, 0)
                            val data = JSONObject().put("package", pkg)
                                .put("displayName", app.packageManager.getApplicationLabel(info).toString())
                                .put("eventID", id)
                            if (WebSocketUtil.sendMessage(JSONObject().put("type", "handoffApp").put("data", data).toString())) {
                                lastSent = id
                                unavailableSent = false
                            }
                        } else {
                            if (!unavailableSent) unavailableSent = sendUnavailable()
                            lastSent = null
                        }
                    } catch (e: Exception) {
                        Log.w("Handoff", "Unable to read foreground app", e)
                        if (!unavailableSent) unavailableSent = sendUnavailable()
                        lastSent = null
                        if (!hasUsageAccess(app)) active = false
                    }
                    delay(1000)
                }
            } finally {
                if (active && WebSocketUtil.isConnected() && peerVersion == 1) sendUnavailable()
            }
        }
    }
    private fun sendUnavailable(): Boolean {
        return WebSocketUtil.sendMessage(JSONObject().put("type", "handoffUnavailable").put("data", JSONObject()).toString())
    }
    fun stop() { job?.cancel(); job = null }
}
