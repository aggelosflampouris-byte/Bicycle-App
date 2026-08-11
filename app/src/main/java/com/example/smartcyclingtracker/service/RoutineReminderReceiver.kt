package com.example.smartcyclingtracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.smartcyclingtracker.MainActivity
import com.example.smartcyclingtracker.R
import com.example.smartcyclingtracker.data.local.RoutineRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RoutineReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var routineRepository: RoutineRepository

    override fun onReceive(context: Context, intent: Intent) {
        // Run in coroutine to fetch data
        CoroutineScope(Dispatchers.IO).launch {
            val progress = routineRepository.getRoutineProgressFlow().firstOrNull() ?: return@launch
            
            // Only notify if NOT completed
            if (!progress.isCompleted) {
                showNotification(context, progress.routine.metric)
            }
        }
    }

    private fun showNotification(context: Context, metric: String) {
        val channelId = "routine_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Routine Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val metricName = if (metric == "CALORIES") "calorie" else "distance"
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Use default launcher icon
            .setContentTitle("Workout Reminder!")
            .setContentText("You are falling behind on your $metricName routine. Time to get moving!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
