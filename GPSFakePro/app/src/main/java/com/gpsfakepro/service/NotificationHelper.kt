package com.gpsfakepro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gpsfakepro.MainActivity
import com.gpsfakepro.R
import com.gpsfakepro.engine.SimStatus
import com.gpsfakepro.engine.SimulationState
import com.gpsfakepro.util.Formatters

/**
 * Persistent notification for the running simulation: status line, current
 * coordinates, route progress and Pause/Resume + Stop actions. The notification
 * stays visible while the app is backgrounded (foreground service).
 */
object NotificationHelper {

    const val CHANNEL_ID = "simulation"
    const val NOTIFICATION_ID = 1001

    private const val REQ_OPEN = 100
    private const val REQ_PAUSE = 101
    private const val REQ_RESUME = 102
    private const val REQ_STOP = 103

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManagerCompat.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun build(context: Context, state: SimulationState): Notification {
        val titleRes = when (state.status) {
            SimStatus.RUNNING -> R.string.notif_title_running
            SimStatus.PAUSED -> R.string.notif_title_paused
            SimStatus.COMPLETED -> R.string.notif_title_completed
            SimStatus.IDLE -> R.string.notif_title_running
        }
        val pos = state.position
        val text = if (pos != null) {
            val coords = Formatters.coords(pos.latitude, pos.longitude)
            if (state.mode == com.gpsfakepro.engine.SimMode.ROUTE) {
                val pct = ((state.routeProgress?.fraction ?: 0f) * 100).toInt()
                context.getString(R.string.notif_text_route, coords, pct)
            } else {
                context.getString(R.string.notif_text_fixed, coords)
            }
        } else {
            context.getString(R.string.app_name)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sim)
            .setContentTitle(context.getString(titleRes))
            .setContentText(text)
            .setOngoing(state.status != SimStatus.IDLE)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (state.error != null) {
            builder.setSubText(state.error)
        } else if (state.speedMps > 0.0) {
            builder.setSubText(
                context.getString(R.string.notif_speed, Formatters.speed(state.speedMps))
            )
        }

        val progress = state.routeProgress
        if (progress != null && progress.totalDistanceM > 0.0) {
            builder.setProgress(100, (progress.fraction * 100).toInt(), false)
        }

        when (state.status) {
            SimStatus.RUNNING -> builder.addAction(
                action(context, R.string.notif_action_pause, R.drawable.ic_stat_pause, REQ_PAUSE,
                    LocationSimulationService.ACTION_PAUSE)
            )
            SimStatus.PAUSED -> builder.addAction(
                action(context, R.string.notif_action_resume, R.drawable.ic_stat_play, REQ_RESUME,
                    LocationSimulationService.ACTION_RESUME)
            )
            SimStatus.COMPLETED -> builder.addAction(
                action(context, R.string.sim_restart, R.drawable.ic_stat_play, REQ_RESUME,
                    LocationSimulationService.ACTION_RESTART)
            )
            SimStatus.IDLE -> Unit
        }
        if (state.status != SimStatus.IDLE) {
            builder.addAction(
                action(context, R.string.notif_action_stop, R.drawable.ic_stat_stop, REQ_STOP,
                    LocationSimulationService.ACTION_STOP)
            )
        }
        return builder.build()
    }

    fun update(context: Context, state: SimulationState) {
        if (!notificationsAllowed(context)) return
        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID, build(context, state))
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, REQ_OPEN, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun action(
        context: Context,
        labelRes: Int,
        iconRes: Int,
        requestCode: Int,
        serviceAction: String
    ): NotificationCompat.Action {
        val intent = Intent(context, LocationSimulationService::class.java)
            .setAction(serviceAction)
        val pi = PendingIntent.getForegroundService(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(iconRes, context.getString(labelRes), pi).build()
    }
}
