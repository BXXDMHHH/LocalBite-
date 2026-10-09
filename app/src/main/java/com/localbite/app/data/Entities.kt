package com.localbite.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val description: String,
    val priceCents: Int,
    val imagePath: String = "",
    val available: Boolean = true
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val totalCents: Int,
    val itemSummary: String,
    val status: String = "已下单"
)

@Entity(tableName = "settings")
data class ShopSettings(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "拾味小馆",
    val tagline: String = "认真做好每一口",
    val accentColor: Long = 0xFFC56A45
)
