package com.ejden.app.ui.components

object ProductIcons {
    val Back = IconSpec(listOf("M15 18l-6-6 6-6"))
    val Search = IconSpec(listOf("M16 16l5 5"), listOf(IconCircle(11f, 11f, 7f)))
    val Scan = IconSpec(
        listOf(
            "M4 7V4h3", "M17 4h3v3", "M20 17v3h-3", "M7 20H4v-3",
            "M8 9v6", "M11 9v6", "M14 9v6", "M17 9v6"
        )
    )
    val Grid = IconSpec(
        listOf(
            "M5 4H9A1 1 0 0 1 10 5V9A1 1 0 0 1 9 10H5A1 1 0 0 1 4 9V5A1 1 0 0 1 5 4Z",
            "M15 4H19A1 1 0 0 1 20 5V9A1 1 0 0 1 19 10H15A1 1 0 0 1 14 9V5A1 1 0 0 1 15 4Z",
            "M5 14H9A1 1 0 0 1 10 15V19A1 1 0 0 1 9 20H5A1 1 0 0 1 4 19V15A1 1 0 0 1 5 14Z",
            "M15 14H19A1 1 0 0 1 20 15V19A1 1 0 0 1 19 20H15A1 1 0 0 1 14 19V15A1 1 0 0 1 15 14Z"
        )
    )
    val ListView = IconSpec(listOf("M5 6h14", "M5 12h14", "M5 18h14"))
    val ImagePlaceholder = IconSpec(
        listOf("M4 5h16v14H4z", "M8 14l2.5-3 2.5 2 2-2 3 3"),
        listOf(IconCircle(9f, 9f, 1.5f))
    )
    val EmptyBox = IconSpec(listOf("M4 7h16v13H4z", "M8 7V4h8v3", "M8 12h8"))
    val NoResult = IconSpec(listOf("M16 16l4 4"), listOf(IconCircle(11f, 11f, 6f)))
}
