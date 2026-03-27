package com.chatfolio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portfolios")
data class PortfolioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val baseCurrency: String = "AUD",
    val createdAt: Long = System.currentTimeMillis()
)
