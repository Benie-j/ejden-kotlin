package com.ejden.app.data

import org.json.JSONObject
import org.json.JSONTokener

data class ShopSettings(val name: String, val phone: String)
data class UserProfile(val firstName: String, val lastName: String, val phone: String)

object ShopLimits {
    const val NAME_MAX = 60
    const val PHONE_MAX = 20
}

object UserLimits {
    const val NAME_MAX = 40
    const val PHONE_MAX = 20
}

private val PHONE_PATTERN =
    Regex("[0-9+().\\-\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]*")
private val SETTINGS_CONTROL = Regex("[\\u0000-\\u001F\\u007F]")

fun isValidPhone(value: String): Boolean =
    value.length <= ShopLimits.PHONE_MAX && PHONE_PATTERN.matches(value)

private fun cleanShopText(value: Any?, max: Int): String =
    if (value is String) value.replace(SETTINGS_CONTROL, " ").jsTrim().take(max) else ""

/** Salutation selon l'heure locale : 5h-11h59 / 12h-17h59 / sinon soir. */
fun greetingFor(hour: Int): String = when {
    hour in 5..11 -> "Bonjour"
    hour in 12..17 -> "Bon après-midi"
    else -> "Bonsoir"
}

class SettingsRepository(private val store: KeyValueStore) {
    companion object {
        const val SHOP_KEY = "ejden_shop"
        const val USER_KEY = "ejden_user"
    }

    private fun readObject(key: String): JSONObject? {
        val stored = store.get(key)
        if (stored == null || stored.isEmpty()) return null
        return try {
            JSONTokener(stored).nextValue() as? JSONObject
        } catch (e: Exception) {
            null
        }
    }

    fun getShop(): ShopSettings {
        val data = readObject(SHOP_KEY) ?: return ShopSettings("", "")
        val phone = cleanShopText(data.opt("phone"), ShopLimits.PHONE_MAX)
        return ShopSettings(
            name = cleanShopText(data.opt("name"), ShopLimits.NAME_MAX),
            phone = if (isValidPhone(phone)) phone else ""
        )
    }

    /** Un numéro trop long ou invalide est refusé (pas coupé en silence). */
    fun saveShop(settings: ShopSettings): Boolean {
        val name = cleanShopText(settings.name, ShopLimits.NAME_MAX)
        val phone = cleanShopText(settings.phone, ShopLimits.PHONE_MAX)
        if (settings.phone.jsTrim().length > ShopLimits.PHONE_MAX || !isValidPhone(phone)) return false
        val json = JSONObject().put("name", name).put("phone", phone)
        return store.set(SHOP_KEY, json.toString())
    }

    fun getUser(): UserProfile {
        val data = readObject(USER_KEY) ?: return UserProfile("", "", "")
        val phone = cleanShopText(data.opt("phone"), UserLimits.PHONE_MAX)
        return UserProfile(
            firstName = cleanShopText(data.opt("firstName"), UserLimits.NAME_MAX),
            lastName = cleanShopText(data.opt("lastName"), UserLimits.NAME_MAX),
            phone = if (isValidPhone(phone)) phone else ""
        )
    }

    fun saveUser(profile: UserProfile): Boolean {
        if (profile.firstName.jsTrim().length > UserLimits.NAME_MAX ||
            profile.lastName.jsTrim().length > UserLimits.NAME_MAX ||
            profile.phone.jsTrim().length > UserLimits.PHONE_MAX
        ) {
            return false
        }
        val clean = UserProfile(
            cleanShopText(profile.firstName, UserLimits.NAME_MAX),
            cleanShopText(profile.lastName, UserLimits.NAME_MAX),
            cleanShopText(profile.phone, UserLimits.PHONE_MAX)
        )
        if (!isValidPhone(clean.phone)) return false
        val json = JSONObject()
            .put("firstName", clean.firstName)
            .put("lastName", clean.lastName)
            .put("phone", clean.phone)
        return store.set(USER_KEY, json.toString())
    }
}

enum class ThemeChoice(val id: String) {
    Dark("dark"), Light("light"), Auto("auto")
}

enum class TextSize(val id: String?, val zoom: Float) {
    Normal(null, 1f), Large("large", 1.1f), XLarge("xlarge", 1.2f)
}

data class Appearance(
    val theme: ThemeChoice = ThemeChoice.Dark,
    val textSize: TextSize = TextSize.Normal,
    val reduceMotion: Boolean = false
)

/** Réglages d'affichage, stockés en texte brut comme dans l'ancienne app. */
class AppearanceRepository(private val store: KeyValueStore) {
    fun get(): Appearance {
        val theme = ThemeChoice.entries.firstOrNull { it.id == store.get("ejden_theme") }
            ?: ThemeChoice.Dark
        val size = TextSize.entries.firstOrNull { it.id == store.get("ejden_text_size") }
            ?: TextSize.Normal
        return Appearance(theme, size, store.get("ejden_reduce_motion") == "1")
    }

    fun saveTheme(choice: ThemeChoice): Boolean = store.set("ejden_theme", choice.id)

    fun saveTextSize(size: TextSize): Boolean = store.set("ejden_text_size", size.id ?: "normal")

    fun saveReduceMotion(enabled: Boolean): Boolean =
        store.set("ejden_reduce_motion", if (enabled) "1" else "0")

    fun saveTheme(choice: ThemeChoice): Boolean = store.set("ejden_theme", choice.id)

    fun saveTextSize(size: TextSize): Boolean = store.set("ejden_text_size", size.id ?: "normal")

    fun saveReduceMotion(enabled: Boolean): Boolean =
        store.set("ejden_reduce_motion", if (enabled) "1" else "0")
}
