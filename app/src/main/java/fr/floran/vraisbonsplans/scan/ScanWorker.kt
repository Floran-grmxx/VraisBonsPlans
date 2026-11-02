package fr.floran.vraisbonsplans.scan

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.floran.vraisbonsplans.notification.DealNotifier
import java.util.concurrent.TimeUnit

/** One scan of every source, followed by notifications for the deals not seen before. */
class ScanWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val settings = ScanSettingsStore.read(applicationContext)
        val result = DealScanner.scan(applicationContext, settings)
        ScanStore.save(applicationContext, result)

        if (settings.alertsEnabled) {
            val alreadyNotified = ScanStore.notifiedKeys(applicationContext)
            val ignored = ScanStore.ignoredKeys(applicationContext)
            val newDeals = result.deals.filter { it.key !in alreadyNotified && it.key !in ignored }
            val shown = DealNotifier.notify(applicationContext, newDeals)
            ScanStore.addNotifiedKeys(applicationContext, shown.map { it.key })
        }
        return Result.success()
    }
}

object ScanScheduler {

    private const val PERIODIC_WORK_NAME = "deal-scan"
    private const val MANUAL_WORK_NAME = "deal-scan-now"

    private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** (Re)schedules the periodic scan to match the settings; cancels it when alerts are off. */
    fun apply(context: Context, settings: ScanSettings) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.alertsEnabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ScanWorker>(settings.effectiveIntervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scanNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ScanWorker>().setConstraints(networkConstraint).build()
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun isManualScanRunning(context: Context) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(MANUAL_WORK_NAME)
}
