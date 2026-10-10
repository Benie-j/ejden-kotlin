package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    @Test
    fun salutationSelonLHeure() {
        assertEquals("Bonsoir", greetingFor(0))
        assertEquals("Bonsoir", greetingFor(4))
        assertEquals("Bonjour", greetingFor(5))
        assertEquals("Bonjour", greetingFor(11))
        assertEquals("Bon après-midi", greetingFor(12))
        assertEquals("Bon après-midi", greetingFor(17))
        assertEquals("Bonsoir", greetingFor(18))
        assertEquals("Bonsoir", greetingFor(23))
    }

    @Test
    fun boutiqueVideParDefaut() {
        val repo = SettingsRepository(InMemoryStore())
        assertEquals(ShopSettings("", ""), repo.getShop())
        assertEquals(UserProfile("", "", ""), repo.getUser())
    }

    @Test
    fun boutiqueLueEtNettoyee() {
        val store = InMemoryStore()
        store.set("ejden_shop", """{"name":"  Chez Awa\u0000 ","phone":"+225 07 00"}""")
        val shop = SettingsRepository(store).getShop()
        assertEquals("Chez Awa", shop.name)
        assertEquals("+225 07 00", shop.phone)
    }

    @Test
    fun telephoneInvalideIgnoreALaLecture() {
        val store = InMemoryStore()
        store.set("ejden_shop", """{"name":"Boutique","phone":"abc"}""")
        val shop = SettingsRepository(store).getShop()
        assertEquals("Boutique", shop.name)
        assertEquals("", shop.phone)
    }

    @Test
    fun enregistrementRefuseLesValeursTropLonguesOuInvalides() {
        val store = InMemoryStore()
        val repo = SettingsRepository(store)

        assertFalse(repo.saveShop(ShopSettings("Boutique", "1".repeat(21))))
        assertFalse(repo.saveShop(ShopSettings("Boutique", "12ab")))
        assertNull(store.get("ejden_shop"))

        assertTrue(repo.saveShop(ShopSettings("  Boutique  ", "07 00 00")))
        assertEquals(ShopSettings("Boutique", "07 00 00"), repo.getShop())

        assertFalse(repo.saveUser(UserProfile("A".repeat(41), "", "")))
        assertTrue(repo.saveUser(UserProfile(" Awa ", "Koné", "")))
        assertEquals(UserProfile("Awa", "Koné", ""), repo.getUser())
    }

    @Test
    fun apparenceParDefautEtValeursConnues() {
        val store = InMemoryStore()
        assertEquals(Appearance(), AppearanceRepository(store).get())

        store.set("ejden_theme", "auto")
        store.set("ejden_text_size", "large")
        store.set("ejden_reduce_motion", "1")
        val a = AppearanceRepository(store).get()
        assertEquals(ThemeChoice.Auto, a.theme)
        assertEquals(1.1f, a.textSize.zoom, 0f)
        assertTrue(a.reduceMotion)

        store.set("ejden_theme", "n'importe quoi")
        store.set("ejden_text_size", "xlarge")
        val b = AppearanceRepository(store).get()
        assertEquals(ThemeChoice.Dark, b.theme)
        assertEquals(1.2f, b.textSize.zoom, 0f)
    }
}
