package com.ejden.app.data

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

/** Ligne du rapport Produits (ProductRow dans report-engine.ts). */
data class ReportProductRow(
    val id: String,
    val name: String,
    val category: String,
    val qty: Double,
    val amount: Double,
    /** Part du CA des produits vendus, en %. */
    val share: Double,
    /** Jours avec au moins une vente / jours de la période. */
    val frequency: Double,
    /** null si la comparaison est indisponible. */
    val prevQty: Double?,
    /** « A », « B » ou « C » ; null sous 10 produits vendus (classement non significatif). */
    val abc: String?
)

data class ReportNoSaleProduct(val id: String, val name: String, val stock: Int)

data class ReportProducts(
    val rows: List<ReportProductRow>,
    val noSale: List<ReportNoSaleProduct>,
    /** Quantités vendues par produit (ventes non annulées), réutilisées par le rapport Stock. */
    val soldQty: Map<String, Double>
)

private class ProductAcc(val name: String) {
    var qty = 0.0
    var amount = 0.0
    val days = HashSet<LocalDate>()
}

/**
 * previousViews : ventes de la période comparée, ou null si la comparaison est indisponible
 * (alors prevQty vaut null, jamais 0 inventé).
 */
fun computeReportProducts(
    views: List<ReportSaleView>,
    previousViews: List<ReportSaleView>?,
    products: List<Product>,
    filters: ReportFilters,
    range: ReportRange,
    zone: ZoneId = ZoneId.systemDefault()
): ReportProducts {
    val categoryOf = products.associate { it.id to (it.category ?: "") }
    val productOf = products.associateBy { it.id }

    val acc = LinkedHashMap<String, ProductAcc>()
    for (view in views) {
        if (view.cancelled) continue
        val day = statDay(view.at, zone)
        for (line in view.lines) {
            val e = acc.getOrPut(line.productId) { ProductAcc(line.name) }
            e.qty += line.qty
            e.amount += line.amount
            e.days.add(day)
        }
    }

    val previousQty = HashMap<String, Double>()
    if (previousViews != null) {
        for (view in previousViews) {
            if (view.cancelled) continue
            for (line in view.lines) {
                previousQty[line.productId] = (previousQty[line.productId] ?: 0.0) + line.qty
            }
        }
    }

    val total = acc.values.sumOf { it.amount }
    val sorted = acc.entries.sortedByDescending { it.value.amount }
    var cumulative = 0.0
    val rows = sorted.map { (id, e) ->
        val share = if (total > 0) e.amount / total * 100 else 0.0
        val before = cumulative
        cumulative += share
        ReportProductRow(
            id = id,
            name = productOf[id]?.name ?: e.name,
            category = categoryOf[id].takeUnless { it.isNullOrEmpty() } ?: UNCATEGORIZED_LABEL,
            qty = e.qty,
            amount = e.amount,
            share = share,
            frequency = e.days.size.toDouble() / range.dayCount,
            prevQty = if (previousViews != null) (previousQty[id] ?: 0.0) else null,
            abc = if (sorted.size >= 10) {
                when {
                    before < 80 -> "A"
                    before < 95 -> "B"
                    else -> "C"
                }
            } else null
        )
    }

    val noSale = products
        .filter {
            !acc.containsKey(it.id) &&
                (filters.productId == "" || filters.productId == it.id) &&
                (filters.category == "" || (it.category ?: "") == filters.category)
        }
        .map { ReportNoSaleProduct(it.id, it.name, it.stock) }

    return ReportProducts(rows, noSale, acc.mapValues { it.value.qty })
}

enum class ReportStockStatus(val id: String) {
    Out("rupture"),
    Low("faible"),
    Ok("ok")
}

data class ReportStockRow(
    val id: String,
    val name: String,
    val stock: Int,
    val threshold: Int,
    val value: Double,
    val sold: Double,
    val dailyRate: Double?,
    val daysLeft: Double?,
    val status: ReportStockStatus,
    val rotation: Double?
)

data class ReportMovement(
    val at: Long,
    val product: String,
    val type: String,
    val quantity: Int,
    val before: Int,
    val after: Int,
    val reason: String
)

data class ReportStock(
    val rows: List<ReportStockRow>,
    val value: Double,
    val entries: Int,
    val exits: Int,
    val movements: List<ReportMovement>,
    val rotation: Double?
)

/**
 * État actuel du stock + rythme de vente observé sur la période.
 * Le rythme n'est calculé qu'à partir de 7 jours d'historique (sinon null, jamais inventé).
 */
fun computeReportStock(
    products: List<Product>,
    movements: List<StockMovement>,
    soldQty: Map<String, Double>,
    filters: ReportFilters,
    range: ReportRange,
    historyDays: Int,
    zone: ZoneId = ZoneId.systemDefault()
): ReportStock {
    val categoryOf = products.associate { it.id to (it.category ?: "") }
    val daysObserved = min(range.dayCount, if (historyDays > 0) historyDays else range.dayCount)

    val rows = products
        .filter {
            (filters.productId == "" || filters.productId == it.id) &&
                (filters.category == "" || (it.category ?: "") == filters.category)
        }
        .map { p ->
            val sold = soldQty[p.id] ?: 0.0
            val rate = if (historyDays >= 7 && sold > 0) sold / daysObserved else null
            ReportStockRow(
                id = p.id,
                name = p.name,
                stock = p.stock,
                threshold = p.stockThreshold,
                value = p.stock * p.purchasePrice,
                sold = sold,
                dailyRate = rate,
                daysLeft = if (rate != null && rate > 0) p.stock / rate else null,
                status = when {
                    p.stock <= 0 -> ReportStockStatus.Out
                    p.stock <= p.stockThreshold -> ReportStockStatus.Low
                    else -> ReportStockStatus.Ok
                },
                rotation = if (p.stock > 0 && sold > 0) sold / p.stock else null
            )
        }

    val from = range.startMillis(zone)
    val to = range.endMillis(zone)
    val moves = movements.mapNotNull { m ->
        val at = parseDateMillis(m.createdAt) ?: return@mapNotNull null
        if (at < from || at > to) return@mapNotNull null
        if (filters.productId != "" && m.productId != filters.productId) return@mapNotNull null
        if (filters.category != "" && (categoryOf[m.productId] ?: "") != filters.category) {
            return@mapNotNull null
        }
        ReportMovement(
            at = at,
            product = m.productName,
            type = when (m.type) {
                StockMovementType.In -> "Entrée"
                StockMovementType.Out -> "Sortie"
                StockMovementType.Adjust -> "Ajustement"
            },
            quantity = m.quantity,
            before = m.before,
            after = m.after,
            reason = m.reason
        )
    }.sortedByDescending { it.at }

    return ReportStock(
        rows = rows,
        value = rows.sumOf { it.value },
        entries = moves.filter { it.quantity > 0 }.sumOf { it.quantity },
        exits = moves.filter { it.quantity < 0 }.sumOf { -it.quantity },
        movements = moves,
        rotation = ReportStats.mean(rows.mapNotNull { it.rotation })
    )
}
