package com.ejden.app.data

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val STOCK_DAY_MS = 86_400_000L

enum class RotationLevel(val label: String) {
    VeryGood("Très bonne"),
    Good("Correcte"),
    Weak("Faible"),
    VeryWeak("Très faible")
}

fun rotationLevel(turnover: Double): RotationLevel = when {
    turnover >= 6 -> RotationLevel.VeryGood
    turnover >= 3 -> RotationLevel.Good
    turnover >= 1.5 -> RotationLevel.Weak
    else -> RotationLevel.VeryWeak
}

data class StockRisk(
    val name: String,
    val stock: Int,
    val velocity: Double,
    val cover: Double,
    val toBuy: Double,
    val salePrice: Double
)

data class StockDormant(val name: String, val value: Double, val sinceDays: Int?, val stock: Int)
data class StockOverstock(val name: String, val days: Long)

data class StockData(
    val turnover: Double,
    val hasSales90: Boolean,
    val daysOfStock: Double,
    val sellThrough: Double,
    val stockCost: Double,
    val risks: List<StockRisk>,
    val lostWeekly: Double?,
    val dormant: List<StockDormant>,
    val dormantCount: Int,
    val dormantValue: Double,
    val overstock: List<StockOverstock>
)

/** Retourne null s'il n'y a aucun produit (message vide du HTML). */
fun computeStock(rows: List<StatRow>, products: List<Product>, nowMillis: Long): StockData? {
    if (products.isEmpty()) return null

    val byProduct = products.associateBy { it.id }
    val sold30 = HashMap<String, Double>()
    val lastSale = HashMap<String, Long>()
    var cogs90 = 0.0
    var units30 = 0.0
    val since30 = nowMillis - 30 * STOCK_DAY_MS
    val since90 = nowMillis - 90 * STOCK_DAY_MS

    for (row in rows) {
        for (item in row.sale.items) {
            lastSale[item.productId] = max(lastSale[item.productId] ?: 0L, row.at)
            if (row.at >= since30) {
                sold30[item.productId] = (sold30[item.productId] ?: 0.0) + item.quantity
                units30 += item.quantity
            }
            if (row.at >= since90) {
                cogs90 += (item.unitCost ?: byProduct[item.productId]?.purchasePrice ?: 0.0) * item.quantity
            }
        }
    }

    val stockCost = products.sumOf { max(0, it.stock) * it.purchasePrice }
    val stockUnits = products.sumOf { max(0, it.stock).toDouble() }

    /* Rotation */
    val firstSale = if (rows.isEmpty()) nowMillis else rows.minOf { it.at }
    val span = min(
        90.0,
        max(1.0, Math.round((nowMillis - firstSale).toDouble() / STOCK_DAY_MS).toDouble())
    )
    val turnover = if (stockCost > 0) (cogs90 / span) * 365 / stockCost else 0.0
    val daysOfStock = if (cogs90 > 0) stockCost / (cogs90 / span) else 0.0
    val sellThrough = if (units30 + stockUnits > 0) units30 / (units30 + stockUnits) * 100 else 0.0

    /* Couverture : produits qui vont manquer */
    val risksAll = products.mapNotNull { p ->
        val velocity = (sold30[p.id] ?: 0.0) / 30
        if (velocity > 0 && p.stock >= 0) {
            val cover = p.stock / velocity
            if (cover <= 14) {
                StockRisk(p.name, p.stock, velocity, cover, max(1.0, ceil(velocity * 30 - p.stock)), p.salePrice)
            } else {
                null
            }
        } else {
            null
        }
    }.sortedBy { it.cover }
    val outOfStock = risksAll.filter { it.stock == 0 }
    val lostWeekly = if (outOfStock.isEmpty()) null else outOfStock.sumOf { it.velocity * 7 * it.salePrice }

    /* Stock dormant et sur-stock */
    val dormantAll = products
        .filter { it.stock > 0 && nowMillis - (lastSale[it.id] ?: 0L) > 60 * STOCK_DAY_MS }
        .map { p ->
            val last = lastSale[p.id]
            StockDormant(
                p.name,
                p.stock * p.purchasePrice,
                if (last != null) floor((nowMillis - last).toDouble() / STOCK_DAY_MS).toInt() else null,
                p.stock
            )
        }
        .sortedByDescending { it.value }

    val overstock = products
        .mapNotNull { p ->
            val velocity = (sold30[p.id] ?: 0.0) / 30
            if (velocity > 0 && p.stock / velocity > 120) p to (p.stock / velocity) else null
        }
        .sortedByDescending { it.second }
        .take(5)
        .map { StockOverstock(it.first.name, Math.round(it.second)) }

    return StockData(
        turnover = turnover,
        hasSales90 = cogs90 > 0,
        daysOfStock = daysOfStock,
        sellThrough = sellThrough,
        stockCost = stockCost,
        risks = risksAll.take(10),
        lostWeekly = lostWeekly,
        dormant = dormantAll.take(6),
        dormantCount = dormantAll.size,
        dormantValue = dormantAll.sumOf { it.value },
        overstock = overstock
    )
}
