package com.chatfolio.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add realizedProfit column to support Ledger Replay and tracking historical gains
                db.execSQL("ALTER TABLE holdings ADD COLUMN realizedProfit REAL NOT NULL DEFAULT 0.0")
            }
        }

    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add currency column to track transaction and holding denomination natively
                db.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'AUD'")
                db.execSQL("ALTER TABLE holdings ADD COLUMN currency TEXT NOT NULL DEFAULT 'AUD'")
            }
        }

    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop old price_cache to shed the legacy composite PK and Primitive date structure
                db.execSQL("DROP TABLE IF EXISTS `price_cache`")
                // Recreate price_cache based on Expected schema from current PriceCacheEntity
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `price_cache` (
                        `ticker` TEXT NOT NULL, 
                        `tradingDate` TEXT NOT NULL, 
                        `closePrice` REAL NOT NULL, 
                        `currency` TEXT NOT NULL, 
                        PRIMARY KEY(`ticker`)
                    )
                    """.trimIndent(),
                )
            }
        }
}
