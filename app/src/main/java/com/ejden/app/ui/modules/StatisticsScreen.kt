package com.ejden.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.ForecastData
import com.ejden.app.data.StatRow
import com.ejden.app.data.activeStatRows
import com.ejden.app.data.computeForecast
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class StatsTab(val label: String) {
    Forecast("Prévisions"),
    Products("Produits"),
    Stock("Stock"),
    Clients("Clients")
}

private val STATS_HORIZONS = listOf(30 to "30 jours", 90 to "90 jours", 365 to "12 mois")

@Composable
fun StatisticsScreen(nav: NavController) {
    val app = LocalContext.current.applicationContext as EjdenApp
    val resumeKey = rememberResumeKey()
    val toast = rememberModuleToast()
    val listState = rememberLazyListState()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var horizon by rememberSaveable { mutableIntStateOf(90) }
    var rows by remember { mutableStateOf<List<StatRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var forecast by remember { mutableStateOf<ForecastData?>(null) }

    LaunchedEffect(resumeKey) {
        val result = withContext(Dispatchers.Default) {
            val statRows = activeStatRows(app.sales.getSales())
            statRows to computeForecast(statRows, app.expenses.getExpenses(), System.currentTimeMillis())
        }
        rows = result.first
        forecast = result.second
        loaded = true
    }
    LaunchedEffect(tabIndex) { listState.scrollToItem(0) }

    val tab = StatsTab.entries[tabIndex]
    val c = Ejden.colors

    ModuleScaffold(
        eyebrow = "ANALYSE",
        title = "Statistiques",
        onBack = { nav.goBack() },
        toast = toast,
        listState = listState
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatsTab.entries.forEachIndexed { index, t ->
                    FilterPill(t.label, index == tabIndex) { tabIndex = index }
                }
            }
        }

        // Comme le HTML : la barre de période est cachée sur Prévisions et Stock.
        if (tab == StatsTab.Products || tab == StatsTab.Clients) {
            item { StatsHorizonBar(horizon) { horizon = it } }
        }

        if (tab == StatsTab.Forecast) {
            forecastTab(forecast, loaded)
        } else item {
            ModuleSection(tab.label) {
                Text(
                    if (loaded) "${rows.size} ventes analysées (période : $horizon jours)" else "Chargement…",
                    color = c.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        item {
            Text(
                "Les prévisions sont des estimations calculées à partir de votre historique : " +
                    "plus vous avez de ventes enregistrées, plus elles sont fiables.",
                color = c.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun StatsHorizonBar(selected: Int, onSelect: (Int) -> Unit) {
    val c = Ejden.colors
    val outer = RoundedCornerShape(10.dp)
    val inner = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .background(c.surface, outer)
            .border(1.dp, c.border, outer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for ((days, label) in STATS_HORIZONS) {
            val active = days == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .background(if (active) c.primary else Color.Transparent, inner)
                    .tap { onSelect(days) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) Color.White else c.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
