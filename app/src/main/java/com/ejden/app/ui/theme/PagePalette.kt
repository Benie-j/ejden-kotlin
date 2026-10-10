package com.ejden.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Palette des écrans-pages (produits, formulaires, ventes, paramètres...). */
val PageLightColors = EjdenColors(
    primary = Color(0xFF176B87), primaryDark = Color(0xFF12566C), primarySoft = Color(0xFFEAF4F7),
    background = Color(0xFFF6F9FA), surface = Color(0xFFFFFFFF),
    text = Color(0xFF172126), textSecondary = Color(0xFF68777D), textLight = Color(0xFF8D9A9F),
    border = Color(0xFFE3EAED),
    success = Color(0xFF168A5B), successSoft = Color(0xFFE9F6F0),
    warning = Color(0xFFD98A16), warningSoft = Color(0xFFFFF5E5),
    error = Color(0xFFD64545), errorSoft = Color(0xFFFCECEC),
)

val PageDarkColors = EjdenColors(
    primary = Color(0xFF176B87), primaryDark = Color(0xFF12566C), primarySoft = Color(0xFF183640),
    background = Color(0xFF101719), surface = Color(0xFF172126),
    text = Color(0xFFF1F5F6), textSecondary = Color(0xFF9AA9AE), textLight = Color(0xFF748288),
    border = Color(0xFF29383D),
    success = Color(0xFF168A5B), successSoft = Color(0xFF15372B),
    warning = Color(0xFFD98A16), warningSoft = Color(0xFF3B2C17),
    error = Color(0xFFD64545), errorSoft = Color(0xFF3B2020),
)
