package de.sulo.manitr

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Hintergrund-Auftrag: speichert samstags und zum Monatsende den SK-Gesamtwert.
 * Android führt ihn nur ungefähr zeitgenau aus; verpasste Stichtage (bis 7 Tage) trägt auch die App beim Öffnen nach.
 */
class SkSnapshotWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val store = runCatching { Vault(applicationContext).load()?.let { Json.storeFromJson(it) } }.getOrNull() ?: return Result.success()
        if (store.portfolio.sk.isEmpty()) return Result.success()
        val history = SkHistoryStore(applicationContext)
        val today = LocalDate.now()
        if (SkHistory.due(today, history.load()).isEmpty()) return Result.success()
        val market = runCatching { MarketData.refreshRates(store.market) }.getOrDefault(store.market)
        val rates = Rates.from(market)
        val usd = store.portfolio.sk.sumOf { Calc.skUsd(it, rates) ?: 0.0 }
        history.recordDue(today, usd, rates.toEur(usd))
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SkSnapshotWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sk-snapshot", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
