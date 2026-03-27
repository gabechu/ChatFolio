package com.chatfolio.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "holdings",
    foreignKeys = [
        ForeignKey(
            entity = PortfolioEntity::class,
            parentColumns = ["id"],
            childColumns = ["portfolioId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("portfolioId")]
)
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val portfolioId: Int,
    val ticker: String,
    val market: String,
    val totalShares: Double,
    val costBase: Double
)
