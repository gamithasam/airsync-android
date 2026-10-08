package com.sameerasw.airsync.utils

/** Tracks app transitions, rather than screen transitions within the same app. */
class HandoffForegroundTracker(private val sessionID: String) {
    var packageName: String? = null
        private set
    var eventID: String? = null
        private set
    fun resume(packageName: String, timestamp: Long) {
        if (packageName == "com.android.systemui" || packageName == this.packageName) return
        this.packageName = packageName
        eventID = "$sessionID:$timestamp"
    }
}
