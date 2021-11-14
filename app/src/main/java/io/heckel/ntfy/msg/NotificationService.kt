package io.heckel.ntfy.msg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import io.heckel.ntfy.R
import io.heckel.ntfy.data.Subscription
import io.heckel.ntfy.data.topicShortUrl
import io.heckel.ntfy.ui.DetailActivity
import io.heckel.ntfy.ui.MainActivity
import kotlin.random.Random

class NotificationService(val context: Context) {
    fun send(subscription: Subscription, message: String) {
        val title = topicShortUrl(subscription.baseUrl, subscription.topic)
        Log.d(TAG, "Displaying notification $title: $message")

        // Create an Intent for the activity you want to start
        val intent = Intent(context, DetailActivity::class.java)
        intent.putExtra(MainActivity.EXTRA_SUBSCRIPTION_ID, subscription.id)
        intent.putExtra(MainActivity.EXTRA_SUBSCRIPTION_BASE_URL, subscription.baseUrl)
        intent.putExtra(MainActivity.EXTRA_SUBSCRIPTION_TOPIC, subscription.topic)
        val pendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent) // Add the intent, which inflates the back stack
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT) // Get the PendingIntent containing the entire back stack
        }

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent) // Click target for notification
            .setAutoCancel(true) // Cancel when notification is clicked

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = context.getString(R.string.channel_notifications_name) // Show's up in UI
            val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(Random.nextInt(), notificationBuilder.build())
    }

    companion object {
        private const val TAG = "NtfyNotificationService"
        private const val CHANNEL_ID = "ntfy"
    }
}
