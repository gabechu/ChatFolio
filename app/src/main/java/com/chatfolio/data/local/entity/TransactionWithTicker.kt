package com.chatfolio.data.local.entity

import androidx.room.Embedded

data class TransactionWithTicker(
    @Embedded val transaction: TransactionEntity,
    val ticker: String,
)
