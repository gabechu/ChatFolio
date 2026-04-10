package com.chatfolio.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
        PriceCacheEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao

    abstract fun holdingDao(): HoldingDao

    abstract fun transactionDao(): TransactionDao

    abstract fun priceCacheDao(): PriceCacheDao
}
