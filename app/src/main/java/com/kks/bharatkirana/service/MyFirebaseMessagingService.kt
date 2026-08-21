package com.kks.bharatkirana.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kks.bharatkirana.MainActivity
import com.kks.bharatkirana.R

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "bharat_kirana_orders"
        const val CHANNEL_NAME = "Order Updates"
        const val EXTRA_ORDER_ID = "notification_order_id"
        const val EXTRA_FROM_PUSH = "from_push_notification"

        // Idempotent — safe to call at every app start.
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Order status updates from BreakQ"
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val orderId = data["order_id"] // Edge Function includes this in data payload.

        val title = remoteMessage.notification?.title ?: data["title"] ?: "BreakQ"
        val body = remoteMessage.notification?.body ?: data["body"] ?: ""

        showNotification(title, body, orderId)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Persist locally; ViewModel picks it up on next app open / login and
        // PATCHes profiles.fcm_token via syncFcmTokenToServer().
        saveTokenLocally(token)
    }

    private fun showNotification(title: String, message: String, orderId: String?) {
        ensureChannel(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_FROM_PUSH, true)
            if (!orderId.isNullOrBlank()) putExtra(EXTRA_ORDER_ID, orderId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (orderId ?: title).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun saveTokenLocally(token: String) {
        val prefs = getSharedPreferences("bharat_kirana_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
    }
}

