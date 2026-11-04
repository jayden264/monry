package com.monry.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.monry.app.data.BackupRepository
import com.monry.app.data.InvestmentRepository
import com.monry.app.data.LedgerRepository
import com.monry.app.data.MonryDatabase
import com.monry.app.data.RecurringRepository
import com.monry.app.data.RoomRecurringStore
import com.monry.app.domain.RecurringProcessor
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Hand-rolled dependency container; one instance per process. */
class AppContainer(context: Context) {
    val db: MonryDatabase = Room.databaseBuilder(context, MonryDatabase::class.java, "monry.db")
        // Add Migrations here when the schema version changes. No destructive fallback, ever.
        .build()

    val ledger = LedgerRepository(db)
    val recurring = RecurringRepository(db)
    val investments = InvestmentRepository(db)
    val backup = BackupRepository(db)
    val processor = RecurringProcessor(RoomRecurringStore(db))
    val prefs = context.getSharedPreferences("monry", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()
}

class MonryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Background catch-up. WorkManager survives reboots; the foreground loop in MainActivity
        // and the run on every app start cover everything else, and the DB's unique key keeps
        // overlapping runs from booking an occurrence twice.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "recurring-rules",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecurringWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}

class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MonryApp
        return try {
            app.container.processor.run(LocalDateTime.now())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
