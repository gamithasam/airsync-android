package com.sameerasw.airsync.utils

/** Tracks app transitions, rather than screen transitions within the same app. */
class HandoffForegroundTracker(private val sessionID: String) {
    var packageName: String? = null
        private set
    var eventID: String? = null
        private set
    var isForeground: Boolean = false
        private set
    fun pause(packageName: String) {
        if (packageName == this.packageName) isForeground = false
    }
    fun resume(packageName: String, timestamp: Long) {
        isForeground = true
        if (packageName == this.packageName) return
        this.packageName = packageName
        eventID = "$sessionID:$timestamp"
    }
}
