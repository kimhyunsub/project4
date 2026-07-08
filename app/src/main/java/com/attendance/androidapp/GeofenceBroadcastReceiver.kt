package com.attendance.androidapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ACTION_CONFIRM_GEOFENCE_CHECK_IN = "com.attendance.androidapp.CONFIRM_GEOFENCE_CHECK_IN"

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            return
        }

        if (geofencingEvent.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { AutoCheckInWorker.tryAutoCheckIn(context) }
                .getOrElse { AutoCheckInResult.Failed("자동 출근 처리에 실패했습니다.") }

            showAutoCheckInResult(context, result)
            pendingResult.finish()
        }
    }

    private fun showAutoCheckInResult(context: Context, result: AutoCheckInResult) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        ensureNotificationChannel(context)

        val (title, message) = when (result) {
            is AutoCheckInResult.CheckedIn -> "자동 출근 완료" to result.message
            is AutoCheckInResult.Skipped -> "자동 출근 미처리" to result.reason
            is AutoCheckInResult.Failed -> "자동 출근 실패" to result.message
        }

        val notification = NotificationCompat.Builder(context, CHECK_IN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(CHECK_IN_NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHECK_IN_CHANNEL_ID,
            "출근 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "사업장 진입 시 출근 확인 알림을 표시합니다."
        }

        context
            .getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val CHECK_IN_CHANNEL_ID = "attendance_check_in"
        private const val CHECK_IN_NOTIFICATION_ID = 7303
    }
}
