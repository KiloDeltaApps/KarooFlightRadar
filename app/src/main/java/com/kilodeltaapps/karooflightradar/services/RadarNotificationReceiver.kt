package com.kilodeltaapps.karooflightradar.services

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresPermission
import com.kilodeltaapps.karooflightradar.utils.AppSettings
/**
 * Broadcast receiver for handling notification button clicks
 * With proper permission handling for Android 13+
 */
class RadarNotificationReceiver : BroadcastReceiver() {

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("RadarNotification", "Received broadcast: ${intent?.action}")

        try {
            when (intent?.action) {
                RadarNotificationService.ACTION_INCREASE_RANGE -> {
                    Log.d("RadarNotification", "Increase range button pressed")
                    RadarNotificationService.handleIncreaseRange(context)
                }
                RadarNotificationService.ACTION_DECREASE_RANGE -> {
                    Log.d("RadarNotification", "Decrease range button pressed")
                    RadarNotificationService.handleDecreaseRange(context)
                }
                RadarNotificationService.ACTION_TOGGLE_NOTIFICATION -> {
                    Log.d("RadarNotification", "Hide notification button pressed")
                    RadarNotificationService.hideRadarControlNotification(context)
                }
                RadarNotificationService.ACTION_OPEN_SETTINGS -> {
                    Log.d("RadarNotification", "Open settings button pressed")
                    RadarNotificationService.handleOpenSettings(context)
                }
            }
        } catch (securityException: SecurityException) {
            // Handle case where notification permission was revoked
            Log.e("RadarNotification", "Notification permission denied: ${securityException.message}")
            // We can still handle the range change even if notification update fails
            handleRangeChangeWithoutNotification(context, intent?.action)
        } catch (e: Exception) {
            Log.e("RadarNotification", "Error handling notification action: ${e.message}")
        }
    }

    private fun handleRangeChangeWithoutNotification(context: Context, action: String?) {
        // We can still process the range change even if we can't show the notification
        when (action) {
            RadarNotificationService.ACTION_INCREASE_RANGE -> {
                // Handle increase range without updating notification
                handleIncreaseRangeSilently(context)
            }
            RadarNotificationService.ACTION_DECREASE_RANGE -> {
                // Handle decrease range without updating notification
                handleDecreaseRangeSilently(context)
            }
            // For toggle notification, we can't do anything without permission
        }
    }

    private fun handleIncreaseRangeSilently(context: Context) {
        try {
            val currentRange = (AppSettings.getRadarViewRange(context) / 1000).toInt()
            val rangeSteps = listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50)
            val currentIndex = rangeSteps.indexOfFirst { it >= currentRange }.coerceAtLeast(0)

            if (currentIndex < rangeSteps.size - 1) {
                val newRange = rangeSteps[currentIndex + 1]
                AppSettings.setRadarViewRange(context, newRange * 1000.0)
                Log.d("RadarNotification", "Range increased to ${newRange}km (silent)")
            }
        } catch (e: Exception) {
            Log.e("RadarNotification", "Error increasing range silently: ${e.message}")
        }
    }

    private fun handleDecreaseRangeSilently(context: Context) {
        try {
            val currentRange = (AppSettings.getRadarViewRange(context) / 1000).toInt()
            val rangeSteps = listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50)
            val currentIndex = rangeSteps.indexOfFirst { it >= currentRange }.coerceAtLeast(0)

            if (currentIndex > 0) {
                val newRange = rangeSteps[currentIndex - 1]
                AppSettings.setRadarViewRange(context, newRange * 1000.0)
                Log.d("RadarNotification", "Range decreased to ${newRange}km (silent)")
            }
        } catch (e: Exception) {
            Log.e("RadarNotification", "Error decreasing range silently: ${e.message}")
        }
    }
}