package com.chatfolio.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = HoldingEntity::class,
            parentColumns = ["id"],
            childColumns = ["holdingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("holdingId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val holdingId: Int,
    // "BUY", "SELL"
    val type: String,
    val date: Long,
    val shares: Double,
    val pricePerShare: Double,
    val totalValue: Double,
    // "CHAT", "CSV", "API"
    val source: String,
)
