package com.sameerasw.airsync.presentation.ui.components.cards

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.sameerasw.airsync.R
import com.sameerasw.airsync.utils.HandoffReporter
import kotlinx.coroutines.delay

@Composable
fun HandoffSettingsCard(context: Context) {
    val preferences = remember(context) { context.getSharedPreferences(HandoffReporter.PREFS, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(preferences.getBoolean(HandoffReporter.ENABLED, false)) }
    var permitted by remember { mutableStateOf(HandoffReporter.hasUsageAccess(context)) }
    LaunchedEffect(context) {
        while (true) {
            permitted = HandoffReporter.hasUsageAccess(context)
            enabled = preferences.getBoolean(HandoffReporter.ENABLED, false)
            delay(1000)
        }
    }
    fun requestPermission() {
        try { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { Toast.makeText(context, R.string.handoff_permission_unavailable, Toast.LENGTH_LONG).show() }
    }
    IconToggleItem(
        title = stringResource(R.string.handoff_title),
        description = stringResource(R.string.handoff_description),
        iconRes = R.drawable.rounded_devices_24,
        isChecked = enabled,
        onCheckedChange = {
            enabled = it
            preferences.edit().putBoolean(HandoffReporter.ENABLED, it).apply()
            HandoffReporter.requestCurrent()
            if (it && !permitted) requestPermission()
        }
    )
    if (enabled) {
        IconToggleItem(
            title = stringResource(if (permitted) R.string.handoff_access_granted else R.string.handoff_grant_access),
            description = stringResource(R.string.handoff_permission_description),
            iconRes = R.drawable.rounded_devices_24,
            showToggle = false,
            onClick = { requestPermission() }
        )
    }
}
