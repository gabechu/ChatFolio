package com.chatfolio.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chatfolio.data.local.dao.HoldingDao
import com.chatfolio.data.local.dao.PortfolioDao
import com.chatfolio.data.local.dao.PriceCacheDao
import com.chatfolio.data.local.dao.TransactionDao
import com.chatfolio.data.local.entity.DividendEntity
import com.chatfolio.data.local.entity.HoldingEntity
import com.chatfolio.data.local.entity.PortfolioEntity
import com.chatfolio.data.local.entity.PriceCacheEntity
import com.chatfolio.data.local.entity.TransactionEntity

@Database(
    entities = [
        PortfolioEntity::class,
        HoldingEntity::class,
        TransactionEntity::class,
        DividendEntity::class,
        PriceCacheEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao
    abstract fun holdingDao(): HoldingDao
    abstract fun transactionDao(): TransactionDao
    abstract fun priceCacheDao(): PriceCacheDao

    companion object {
        // Drops and recreates price_cache: date:Long → tradingDate:String
        // Safe to do since cached prices are always re-fetchable from Yahoo.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `price_cache`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `price_cache` (" +
                        "`ticker` TEXT NOT NULL, " +
                        "`tradingDate` TEXT NOT NULL, " +
                        "`closePrice` REAL NOT NULL, " +
                        "`currency` TEXT NOT NULL, " +
                        "PRIMARY KEY(`ticker`, `tradingDate`))"
                )
            }
        }
    }
}
