package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val KONE = Supplier("f1", "Koné Distribution", "07 00", "2026-01-01T10:00:00.000Z")

class SupplierTest {

    @Test
    fun formulaireFournisseur() {
        val others = listOf(KONE)

        val empty = validateSupplierForm("   ", "", others, null) as SupplierFormResult.Invalid
        assertEquals("Veuillez saisir le nom du fournisseur.", empty.message)
        assertEquals(SupplierField.Name, empty.field)

        val long = validateSupplierForm("x".repeat(81), "", others, null) as SupplierFormResult.Invalid
        assertEquals("Nom trop long (80 caractères max).", long.message)

        val phone = validateSupplierForm("Bob", "abc", others, null) as SupplierFormResult.Invalid
        assertEquals("Téléphone invalide : chiffres, +, espaces et tirets seulement.", phone.message)
        assertEquals(SupplierField.Phone, phone.field)

        val dup = validateSupplierForm("  koné   DISTRIBUTION ", "", others, null) as SupplierFormResult.Invalid
        assertEquals("Un fournisseur porte déjà ce nom.", dup.message)

        val same = validateSupplierForm("Koné Distribution", "07 11", others, "f1") as SupplierFormResult.Valid
        assertEquals("07 11", same.phone)

        val ok = validateSupplierForm("  Grossiste   Abidjan ", " +225 07 ", others, null) as SupplierFormResult.Valid
        assertEquals("Grossiste Abidjan", ok.name)
        assertEquals("+225 07", ok.phone)
    }

    @Test
    fun ajoutModificationEtDoublons() {
        val repo = SupplierRepository(InMemoryStore())
        assertTrue(repo.getSuppliers().isEmpty())

        assertTrue(repo.addSupplier(KONE))
        assertEquals("f1", repo.getSupplierById("f1")?.id)
        assertTrue(repo.supplierNameExists("koné distribution"))
        assertFalse(repo.supplierNameExists("Koné Distribution", exceptId = "f1"))

        assertTrue(repo.updateSupplier(KONE.copy(phone = "07 99")))
        assertEquals("07 99", repo.getSupplierById("f1")?.phone)

        assertFalse(repo.addSupplier(KONE.copy(name = "")))
        assertFalse(repo.updateSupplier(KONE.copy(id = "inconnu")))
        assertNull(repo.getSupplierById("inconnu"))
    }
}
