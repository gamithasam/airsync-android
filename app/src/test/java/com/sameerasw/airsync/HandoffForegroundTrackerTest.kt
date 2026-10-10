package com.sameerasw.airsync

import com.sameerasw.airsync.utils.HandoffForegroundTracker
import org.junit.Assert.*
import org.junit.Test

class HandoffForegroundTrackerTest {
    @Test fun sameAppScreensKeepTheConsumedSession() {
        val tracker = HandoffForegroundTracker("session")
        tracker.resume("app.a", 10)
        tracker.resume("app.a", 20)
        assertEquals("session:10", tracker.eventID)
        tracker.pause("app.a")
        assertFalse(tracker.isForeground)
        tracker.resume("app.a", 40)
        assertTrue(tracker.isForeground)
        assertEquals("session:10", tracker.eventID)
    }
    @Test fun returningFromHomeCreatesANewHandoff() {
        val tracker = HandoffForegroundTracker("session")
        tracker.resume("app.a", 10)
        tracker.resume("launcher", 20)
        tracker.resume("app.a", 30)
        assertEquals("session:30", tracker.eventID)
        assertEquals("app.a", tracker.packageName)
    }
    @Test fun sessionsRemainDistinctAcrossReconnects() {
        val first = HandoffForegroundTracker("first")
        val second = HandoffForegroundTracker("second")
        first.resume("app.a", 10)
        second.resume("app.a", 10)
        assertNotEquals(first.eventID, second.eventID)
    }
}
