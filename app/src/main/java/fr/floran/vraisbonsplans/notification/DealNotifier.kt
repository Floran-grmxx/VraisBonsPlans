package fr.floran.vraisbonsplans.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.floran.vraisbonsplans.model.Deal
import fr.floran.vraisbonsplans.model.openableUrl
import fr.floran.vraisbonsplans.scan.ScanStore
import fr.floran.vraisbonsplans.ui.formatEuros

object DealNotifier {

    private const val CHANNEL_ID = "deals"
    private const val MAXIMUM_SEPARATE_NOTIFICATIONS = 5
    const val EXTRA_DEAL_KEY = "deal_key"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    /** Returns the deals actually shown, so that hidden ones are not marked as notified. */
    fun notify(context: Context, deals: List<Deal>): List<Deal> {
        if (deals.isEmpty()) return emptyList()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Vrais bons plans", NotificationManager.IMPORTANCE_HIGH),
        )
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return emptyList()

        deals.take(MAXIMUM_SEPARATE_NOTIFICATIONS).forEach { deal ->
            val notificationId = deal.key.hashCode()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.star_big_on)
                .setContentTitle(title(deal))
                .setContentText(body(deal))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body(deal) + (deal.crossCheck?.let { "\n$it" } ?: "")))
                .setSubText(deal.source.label)
                .setContentIntent(openDealIntent(context, deal, notificationId))
                .addAction(0, "Ignorer ce produit", ignoreIntent(context, deal, notificationId))
                .setAutoCancel(true)
                .build()
            notifications.notify(notificationId, notification)
        }
        val remaining = deals.size - MAXIMUM_SEPARATE_NOTIFICATIONS
        if (remaining > 0) {
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.star_big_on)
                .setContentTitle("+$remaining autres vrais bons plans")
                .setContentText("Ouvre l'app pour les voir")
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, 0,
                        context.packageManager.getLaunchIntentForPackage(context.packageName),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .setAutoCancel(true)
                .build()
            notifications.notify(SUMMARY_NOTIFICATION_ID, summary)
        }
        return deals
    }

    private const val SUMMARY_NOTIFICATION_ID = 1

    private fun title(deal: Deal): String {
        val discount = deal.realDiscountPercent?.let { "-$it % réel" } ?: "Erreur de prix"
        return "$discount : ${deal.name}"
    }

    private fun body(deal: Deal): String {
        val price = if (deal.currentPriceCents > 0) formatEuros(deal.currentPriceCents) else "Prix inconnu"
        val reference = deal.referencePriceCents?.let { " au lieu de ${formatEuros(it)} habituellement" }.orEmpty()
        val detail = deal.detail.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
        return "$price$reference$detail"
    }

    /** idealo product links are app links: they open the official idealo app when installed. */
    private fun openDealIntent(context: Context, deal: Deal, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(Intent.ACTION_VIEW, Uri.parse(deal.openableUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ignoreIntent(context: Context, deal: Deal, notificationId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, notificationId,
            Intent(context, IgnoreDealReceiver::class.java)
                .putExtra(EXTRA_DEAL_KEY, deal.key)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** "Ignorer ce produit" action: never notify this product again and remove the notification. */
class IgnoreDealReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(DealNotifier.EXTRA_DEAL_KEY) ?: return
        ScanStore.ignore(context, key)
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(DealNotifier.EXTRA_NOTIFICATION_ID, 0))
    }
}
