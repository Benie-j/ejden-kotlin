package com.ejden.app.ui.components

object SalesIcons {
    val History = IconSpec(listOf("M3 12a9 9 0 1 0 3-6.7", "M3 4v6h6", "M12 7v5l3 2"))
    val Search = IconSpec(listOf("M16 16l5 5"), listOf(IconCircle(11f, 11f, 6.5f)))
    val Scan = IconSpec(
        listOf(
            "M4 7V5a1 1 0 0 1 1-1h2", "M17 4h2a1 1 0 0 1 1 1v2",
            "M20 17v2a1 1 0 0 1-1 1h-2", "M7 20H5a1 1 0 0 1-1-1v-2",
            "M7 8v8", "M10 8v8", "M13 8v8", "M17 8v8"
        )
    )
    val Box = IconSpec(listOf("M4 5h16v14H4z", "M8 9h8", "M8 13h5"))
    val Cart = IconSpec(
        listOf("M4 5h2l2 11h10l2-8H7"),
        listOf(IconCircle(10f, 20f, 1.2f), IconCircle(18f, 20f, 1.2f))
    )
    val Minus = IconSpec(listOf("M5 12h14"))
    val Trash = IconSpec(
        listOf("M4 7h16", "M9 7V4h6v3", "M7 7l1 13h8l1-13", "M10 11v5", "M14 11v5")
    )
    val Check = IconSpec(listOf("M5 12l4 4L19 6"))
    val ChevronDown = IconSpec(listOf("M6 9l6 6 6-6"))
}
