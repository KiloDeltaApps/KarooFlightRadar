package com.kilodeltaapps.karooflightradar.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kilodeltaapps.karooflightradar.FlightRadarApplication
import com.kilodeltaapps.karooflightradar.R
import com.kilodeltaapps.karooflightradar.utils.AppSettings
import androidx.compose.material.icons.Icons

/**
 * Service for managing radar control notifications with proper permission handling
 */
object RadarNotificationService {

    private const val CHANNEL_ID = "flight_radar_control_channel"
    private const val NOTIFICATION_ID = 1001
    const val ACTION_INCREASE_RANGE = "increase_range"
    const val ACTION_DECREASE_RANGE = "decrease_range"
    const val ACTION_TOGGLE_NOTIFICATION = "toggle_notification"
    const val ACTION_OPEN_SETTINGS = "open_settings"

    // Radar range steps in kilometers
    private val rangeSteps = listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50)

    private var isNotificationVisible = false

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Flight Radar Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for flight radar display"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Check if we have notification permission
     */
    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Show radar control notification with permission check
     * @return true if notification was shown, false if permission denied
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showRadarControlNotification(context: Context): Boolean {
        if (!hasNotificationPermission(context)) {
            Log.w("RadarNotification", "Cannot show notification - permission denied")
            return false
        }

        return try {
            val currentRange = (AppSettings.getRadarViewRange(context) / 1000).toInt()
            val currentRangeIndex = rangeSteps.indexOfFirst { it >= currentRange }.coerceAtLeast(0)
            val currentRangeKm = rangeSteps.getOrElse(currentRangeIndex) { 25 }

            // Create increase range action
            val increaseRangeIntent = Intent(context, RadarNotificationReceiver::class.java).apply {
                action = ACTION_INCREASE_RANGE
            }
            val increaseRangePendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                increaseRangeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create decrease range action
            val decreaseRangeIntent = Intent(context, RadarNotificationReceiver::class.java).apply {
                action = ACTION_DECREASE_RANGE
            }
            val decreaseRangePendingIntent = PendingIntent.getBroadcast(
                context,
                2,
                decreaseRangeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create open settings action
            val openSettingsIntent = Intent(context, RadarNotificationReceiver::class.java).apply {
                action = ACTION_OPEN_SETTINGS
            }
            val openSettingsPendingIntent = PendingIntent.getBroadcast(
                context,
                4,
                openSettingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create toggle notification action
            val toggleNotificationIntent = Intent(context, RadarNotificationReceiver::class.java).apply {
                action = ACTION_TOGGLE_NOTIFICATION
            }
            val toggleNotificationPendingIntent = PendingIntent.getBroadcast(
                context,
                3,
                toggleNotificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Build notification
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Flight Radar")
                .setContentText("Range: ${currentRangeKm}km")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

                // Add action buttons
                .addAction(
                    R.drawable.magnify_plus, // decrease range, zoom in
                    "+",
                    decreaseRangePendingIntent
                )
                .addAction(
                    R.drawable.magnify_minus, // increase range, zoom out
                    "-",
                    increaseRangePendingIntent
                )
                .addAction(
                    R.drawable.ic_radar, // You'll need to add this drawable
                    "Settings",
                    openSettingsPendingIntent
                )
                .addAction(
                    R.drawable.close_circle_outline, // You'll need to add this drawable
                    "Hide",
                    toggleNotificationPendingIntent
                )

                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .build()

            // Show notification
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            isNotificationVisible = true
            Log.d("RadarNotification", "Notification shown with range: ${currentRangeKm}km")
            true
        } catch (securityException: SecurityException) {
            Log.e("RadarNotification", "Security exception when showing notification: ${securityException.message}")
            false
        } catch (e: Exception) {
            Log.e("RadarNotification", "Error showing notification: ${e.message}")
            false
        }
    }

    /**
     * Hide radar control notification with permission check
     * @return true if notification was hidden, false if permission denied
     */
    fun hideRadarControlNotification(context: Context): Boolean {
        if (!hasNotificationPermission(context)) {
            Log.w("RadarNotification", "Cannot hide notification - permission denied")
            return false
        }

        return try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            isNotificationVisible = false
            Log.d("RadarNotification", "Notification hidden")
            true
        } catch (securityException: SecurityException) {
            Log.e("RadarNotification", "Security exception when hiding notification: ${securityException.message}")
            false
        } catch (e: Exception) {
            Log.e("RadarNotification", "Error hiding notification: ${e.message}")
            false
        }
    }

    fun isNotificationVisible(): Boolean {
        return isNotificationVisible
    }

    /**
     * Handle increase range - will work even without notification permission
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun handleIncreaseRange(context: Context) {
        val currentRange = (AppSettings.getRadarViewRange(context) / 1000).toInt()
        val currentIndex = rangeSteps.indexOfFirst { it >= currentRange }.coerceAtLeast(0)

        if (currentIndex < rangeSteps.size - 1) {
            val newRange = rangeSteps[currentIndex + 1]
            AppSettings.setRadarViewRange(context, newRange * 1000.0)
            showRadarControlNotification(context)
            Log.d("RadarNotification", "Range increased to ${newRange}km")
        } else {
            Log.d("RadarNotification", "Already at max range: ${rangeSteps.last()}km")
        }
    }

    /**
     * Handle decrease range - will work even without notification permission
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun handleDecreaseRange(context: Context) {
        val currentRange = (AppSettings.getRadarViewRange(context) / 1000).toInt()
        val currentIndex = rangeSteps.indexOfFirst { it >= currentRange }.coerceAtLeast(0)

        if (currentIndex > 0) {
            val newRange = rangeSteps[currentIndex - 1]
            AppSettings.setRadarViewRange(context, newRange * 1000.0)
            showRadarControlNotification(context)
            Log.d("RadarNotification", "Range decreased to ${newRange}km")
        } else {
            Log.d("RadarNotification", "Already at min range: ${rangeSteps.first()}km")
        }
    }

    /**
     * Handle opening settings screen
     */
    fun handleOpenSettings(context: Context) {
        try {
            // Create intent for your settings activity
            val intent = Intent(context, com.kilodeltaapps.karooflightradar.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_settings_fragment", true) // Add extra to identify settings request
            }
            context.startActivity(intent)
            Log.d("RadarNotification", "Opening settings screen")
        } catch (e: Exception) {
            Log.e("RadarNotification", "Failed to open settings: ${e.message}")
            // Fallback: Open app main activity
            val fallbackIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            fallbackIntent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(fallbackIntent)
        }
    }

    /**
     * Toggle notification with permission check
     * @return true if operation succeeded, false if permission denied
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun toggleNotification(context: Context): Boolean {
        return if (isNotificationVisible) {
            hideRadarControlNotification(context)
        } else {
            showRadarControlNotification(context)
        }
    }

    /**
     * Check if notification permission is available
     */
    fun canShowNotifications(context: Context): Boolean {
        return hasNotificationPermission(context)
    }
}