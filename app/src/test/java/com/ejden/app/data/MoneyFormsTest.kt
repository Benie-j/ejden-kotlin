package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MoneyFormsTest {

    @Test
    fun formulaireDepense() {
        val cat = ExpenseCategory.Transport

        val noLabel = validateExpenseForm("  ", "5000", cat) as ExpenseFormResult.Valid
        assertEquals("Transport", noLabel.label)

        val tooLong = validateExpenseForm("x".repeat(81), "5000", cat) as ExpenseFormResult.Invalid
        assertEquals("Description trop longue (80 caractères max).", tooLong.message)

        for (bad in listOf("", "0", "abc")) {
            val r = validateExpenseForm("Transport", bad, cat) as ExpenseFormResult.Invalid
            assertEquals("Montant invalide.", r.message)
            assertEquals(MoneyField.Amount, r.field)
        }

        val ok = validateExpenseForm("  Transport   de   marchandises ", "5000", cat) as ExpenseFormResult.Valid
        assertEquals("Transport de marchandises", ok.label)
        assertEquals(5000.0, ok.amount, 0.0)
        assertEquals(cat, ok.category)
    }

    @Test
    fun formulaireCaisse() {
        val noAmount = validateCashForm(CashType.In, "", "Fonds", 0.0) as CashFormResult.Invalid
        assertEquals("Montant invalide.", noAmount.message)
        assertEquals(MoneyField.Amount, noAmount.field)

        val noReason = validateCashForm(CashType.In, "1000", "   ", 0.0) as CashFormResult.Valid
        assertEquals("Entrée", noReason.reason)
        val noReasonOut = validateCashForm(CashType.Out, "1000", "", 0.0) as CashFormResult.Valid
        assertEquals("Sortie", noReasonOut.reason)

        val tooLong = validateCashForm(CashType.In, "1000", "x".repeat(81), 0.0) as CashFormResult.Invalid
        assertEquals("Motif trop long (80 caractères max).", tooLong.message)

        val ok = validateCashForm(CashType.Out, "500", " Achat  sacs ", 1000.0) as CashFormResult.Valid
        assertEquals("Achat sacs", ok.reason)
        assertEquals(500.0, ok.amount, 0.0)
        assertFalse(ok.goesNegative)

        val negative = validateCashForm(CashType.Out, "1500", "Achat", 1000.0) as CashFormResult.Valid
        assertTrue(negative.goesNegative)
        assertFalse((validateCashForm(CashType.In, "1500", "Apport", -5000.0) as CashFormResult.Valid).goesNegative)
    }

    @Test
    fun messagesDeLaCaisse() {
        assertEquals("Entrée enregistrée.", cashSavedMessage(CashType.In, false))
        assertEquals("Sortie enregistrée.", cashSavedMessage(CashType.Out, false))
        assertEquals(
            "Enregistré. Attention : le solde de la caisse est négatif.",
            cashSavedMessage(CashType.Out, true)
        )
    }

    @Test
    fun libellesDesMouvements() {
        assertEquals("Acomptes sur crédit", cashMethodLabel(PaymentMethod.Credit))
        assertEquals("Espèces", cashMethodLabel(PaymentMethod.Cash))

        val sale = CashMovement("m1", CashSource.Sale, 1000.0, "Vente N° ABC", "2026-10-09T09:05:00.000Z", PaymentMethod.Cash)
        assertEquals("Vente · Espèces · 09:05", cashMovementSubtitle(sale, ZoneOffset.UTC))

        val expense = CashMovement("m2", CashSource.Expense, -100.0, "Transport", "2026-10-09T10:30:00.000Z")
        assertEquals("Dépense · 10:30", cashMovementSubtitle(expense, ZoneOffset.UTC))

        val entry = CashMovement("m3", CashSource.Entry, 500.0, "Apport", "2026-10-09T08:00:00.000Z", PaymentMethod.Cash)
        assertEquals("Entrée de caisse · 08:00", cashMovementSubtitle(entry, ZoneOffset.UTC))
    }

    @Test
    fun regroupementParJour() {
        val today = LocalDate.of(2026, 10, 9)
        val dates = listOf(
            "2026-10-09T10:00:00.000Z",
            "2026-10-09T08:00:00.000Z",
            "2026-10-08T20:00:00.000Z",
            "2026-10-01T12:00:00.000Z"
        )
        val groups = groupByDay(dates, today, ZoneOffset.UTC) { it }
        assertEquals(listOf("Aujourd'hui", "Hier", "Jeudi 1 Octobre 2026"), groups.map { it.label })
        assertEquals(2, groups[0].items.size)

        assertEquals("Date inconnue", groupByDay(listOf("n'importe quoi"), today, ZoneOffset.UTC) { it }[0].label)
    }
}
