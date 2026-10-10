package com.ejden.app.ui.components

object FormIcons {
    val PhotoAdd = IconSpec(
        listOf("M4 5h16v14H4z", "M4 16l5-5 4 4 2-2 5 5"),
        listOf(IconCircle(9f, 10f, 1.5f))
    )
    val Barcode = IconSpec(
        listOf("M4 5v14", "M7 5v14", "M10 5v14", "M14 5v14", "M17 5v14", "M20 5v14")
    )
    val ScanSmall = IconSpec(
        listOf(
            "M4 7V5a1 1 0 0 1 1-1h2", "M17 4h2a1 1 0 0 1 1 1v2",
            "M20 17v2a1 1 0 0 1-1 1h-2", "M7 20H5a1 1 0 0 1-1-1v-2",
            "M7 9v6", "M10 9v6", "M13 9v6", "M16 9v6"
        )
    )
}
