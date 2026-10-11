package com.ejden.app.ui.components

object SettingsIcons {
    val Home = IconSpec(listOf("M3 10l9-7 9 7v10a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"))
    val Team = IconSpec(
        listOf(
            "M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6",
            "M16 5.2a3.2 3.2 0 0 1 0 5.6",
            "M18 14.4c1.8 0.8 3 2.6 3 4.6"
        ),
        listOf(IconCircle(9f, 8f, 3.2f))
    )
    val Sales = IconSpec(
        listOf("M6 7h14l-1.5 8H8z", "M6 7L5 3H2"),
        listOf(IconCircle(9f, 19f, 1.4f), IconCircle(17f, 19f, 1.4f))
    )
    val Stock = IconSpec(listOf("M21 8l-9-5-9 5 9 5z", "M3 8v8l9 5 9-5V8", "M12 13v8"))
    val Client = IconSpec(listOf("M5 20c0-3.9 3.1-7 7-7s7 3.1 7 7"), listOf(IconCircle(12f, 8f, 3.5f)))
    val Appearance = IconSpec(
        listOf(
            "M12 2v2", "M12 20v2", "M4.9 4.9l1.4 1.4", "M17.7 17.7l1.4 1.4",
            "M2 12h2", "M20 12h2", "M4.9 19.1l1.4-1.4", "M17.7 6.3l1.4-1.4"
        ),
        listOf(IconCircle(12f, 12f, 4f))
    )
    val Data = IconSpec(
        listOf(
            "M4 6a8 3 0 1 0 16 0a8 3 0 1 0 -16 0",
            "M4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6",
            "M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6"
        )
    )
    val Security = IconSpec(listOf("M12 3l8 3v6c0 4.5-3.2 8.2-8 9-4.8-.8-8-4.5-8-9V6z"))
    val About = IconSpec(listOf("M12 11v5", "M12 8h0.01"), listOf(IconCircle(12f, 12f, 9f)))
}
