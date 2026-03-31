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
}
