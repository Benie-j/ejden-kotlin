package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierViewsTest {

    @Test
    fun versementsGroupesParLot() {
        val payments = listOf(
            SupplierPayment("a1", "b1", "p1", "f1", 900.0, PaymentMethod.Cash, "2026-10-10T09:00:00.000Z"),
            SupplierPayment("a2", "b1", "p2", "f1", 300.0, PaymentMethod.Cash, "2026-10-10T09:00:00.000Z"),
            SupplierPayment(
                "a3", "b2", "p2", "f1", 50.0, PaymentMethod.Card,
                "2026-10-12T09:00:00.000Z", "2026-10-13T09:00:00.000Z"
            ),
            SupplierPayment("a4", "b3", "p9", "f2", 10.0, PaymentMethod.Cash, "2026-10-14T09:00:00.000Z")
        )

        val batches = groupSupplierPaymentBatches(payments, "f1")
        assertEquals(listOf("b2", "b1"), batches.map { it.batchId })
        assertEquals(listOf(50.0, 1200.0), batches.map { it.amount })
        assertEquals("2026-10-13T09:00:00.000Z", batches[0].cancelledAt)
        assertNull(batches[1].cancelledAt)
        assertTrue(groupSupplierPaymentBatches(payments, "zzz").isEmpty())
    }
}
