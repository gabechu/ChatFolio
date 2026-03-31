package com.chatfolio.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dividends",
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
data class DividendEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val holdingId: Int,
    val exDate: Long,
    val payDate: Long,
    val amountPerShare: Double,
    val totalAmount: Double,
    // "YAHOO", "CHAT"
    val source: String,
)
