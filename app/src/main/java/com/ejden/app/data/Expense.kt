package com.ejden.app.data

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

enum class ExpenseCategory(val id: String, val label: String) {
    Merchandise("marchandises", "Marchandises"),
    Transport("transport", "Transport"),
    Rent("loyer", "Loyer"),
    Energy("energie", "Électricité / Eau"),
    Salaries("salaires", "Salaires"),
    Other("autre", "Autre");

    companion object {
        fun fromId(value: Any?): ExpenseCategory? = entries.firstOrNull { it.id == value }
    }
}

data class Expense(
    val id: String,
    val label: String,
    val amount: Double,
    val category: ExpenseCategory,
    val createdAt: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("amount", amount)
        put("category", category.id)
        put("createdAt", createdAt)
    }
}

fun normalizeExpense(value: Any?): Expense? {
    val e = value as? JSONObject ?: return null
    val label = cleanLabel(e.opt("label"), ModuleLimits.TEXT_MAX)
    val amount = cleanAmount(e.opt("amount"))
    val createdAt = validDate(e.opt("createdAt"))
    val category = ExpenseCategory.fromId(e.opt("category"))
    val id = e.opt("id") as? String

    if (id == null || id.isEmpty() || id.length > 100 ||
        label.isEmpty() || amount == null || createdAt == null || category == null
    ) {
        return null
    }
    return Expense(id, label, amount, category, createdAt)
}

data class ExpenseTotals(val today: Double, val month: Double)

class ExpenseRepository(private val store: KeyValueStore) {
    companion object {
        const val KEY = "ejden_expenses"
    }

    fun getExpenses(): List<Expense> = readList(store, KEY, ::normalizeExpense)

    private fun save(expenses: List<Expense>): Boolean =
        writeList(store, KEY, expenses.map { it.toJson() })

    fun saveAll(expenses: List<Expense>): Boolean = save(expenses)

    fun addExpense(expense: Expense): Boolean {
        val clean = normalizeExpense(expense.toJson()) ?: return false
        return save(getExpenses() + clean)
    }

    fun deleteExpense(expenseId: String): Boolean {
        val expenses = getExpenses()
        val remaining = expenses.filter { it.id != expenseId }
        if (remaining.size == expenses.size) return false
        return save(remaining)
    }

    fun getExpensesTotals(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ExpenseTotals {
        val nowDate = now.atZone(zone).toLocalDate()
        var todayTotal = 0.0
        var monthTotal = 0.0

        for (expense in getExpenses()) {
            val millis = parseDateMillis(expense.createdAt) ?: continue
            val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            if (date.year == nowDate.year && date.monthValue == nowDate.monthValue) {
                monthTotal += expense.amount
                if (date.dayOfMonth == nowDate.dayOfMonth) todayTotal += expense.amount
            }
        }
        return ExpenseTotals(todayTotal, monthTotal)
    }
}
