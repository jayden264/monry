package com.monry.app.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Schema version 1. When the schema changes: bump [version], add a Migration in
 * [com.monry.app.AppContainer] and keep the exported JSON under app/schemas. Never use
 * fallbackToDestructiveMigration - user data must survive upgrades.
 */
@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        RecurringRuleEntity::class,
        RuleExecutionEntity::class,
        InvestmentEntity::class,
        TrancheEntity::class,
        ContractEntity::class
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)] // v2 only adds the contracts table
)
abstract class MonryDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun transactions(): TransactionDao
    abstract fun rules(): RuleDao
    abstract fun investments(): InvestmentDao
    abstract fun backup(): BackupDao
    abstract fun contracts(): ContractDao
}
