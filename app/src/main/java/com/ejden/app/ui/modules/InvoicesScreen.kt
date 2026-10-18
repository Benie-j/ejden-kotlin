package com.ejden.app.ui.modules

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.Invoice
import com.ejden.app.data.InvoiceDisplayStatus
import com.ejden.app.data.PrintableDocument
import com.ejden.app.data.documentToHtml
import com.ejden.app.data.documentToText
import com.ejden.app.data.formatMoney
import com.ejden.app.data.invoiceToDocument
import com.ejden.app.data.jsTrim
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InvoicesData(
    val invoices: List<Invoice>,
    val shopName: String,
    val address: String
)

private fun invoiceCount(n: Int): String = n.toString() + " facture" + (if (n > 1) "s" else "")

@Composable
fun InvoicesScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val listState = rememberLazyListState()

    var data by remember { mutableStateOf(InvoicesData(emptyList(), "", "")) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(resumeKey) {
        data = withContext(Dispatchers.Default) {
            InvoicesData(
                app.invoices.getInvoices(),
                app.settings.getShop().name,
                app.documents.getSettings().address
            )
        }
        loaded = true
    }

    var screen by rememberSaveable { mutableStateOf("list") }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var filterKey by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = screen == "preview") {
        screen = "list"
        previewId = null
    }
    LaunchedEffect(screen) { listState.scrollToItem(0) }

    // ----- Résumé, filtre, recherche -----
    val statusOf = remember(data.invoices) {
        data.invoices.associate { it.id to app.invoices.displayStatus(it) }
    }
    var pending = 0.0
    var pendingCount = 0
    var late = 0.0
    var lateCount = 0
    for (invoice in data.invoices) {
        val status = statusOf[invoice.id]
        if (status == InvoiceDisplayStatus.Unpaid || status == InvoiceDisplayStatus.Partial ||
            status == InvoiceDisplayStatus.Overdue
        ) {
            pending += invoice.remaining()
            pendingCount += 1
        }
        if (status == InvoiceDisplayStatus.Overdue) {
            late += invoice.remaining()
            lateCount += 1
        }
    }

    val search = query.jsTrim().lowercase()
    val visible = data.invoices.filter { invoice ->
        val status = statusOf[invoice.id] ?: InvoiceDisplayStatus.Draft
        val matchesFilter = when (filterKey) {
            "draft" -> status == InvoiceDisplayStatus.Draft
            "open" -> status == InvoiceDisplayStatus.Unpaid || status == InvoiceDisplayStatus.Partial
            "overdue" -> status == InvoiceDisplayStatus.Overdue
            "paid" -> status == InvoiceDisplayStatus.Paid
            "cancelled" -> status == InvoiceDisplayStatus.Cancelled
            else -> true
        }
        matchesFilter && (
            search.isEmpty() ||
                invoice.number.lowercase().contains(search) ||
                invoice.clientName.lowercase().contains(search) ||
                invoice.subject.lowercase().contains(search)
            )
    }

    ModuleScaffold(
        "DOCUMENTS", "Factures",
        {
            if (screen == "preview") {
                screen = "list"
                previewId = null
            } else {
                nav.goBack()
            }
        },
        toast, listState
    ) {
        if (screen == "preview") {
            invoicePreview(
                invoice = data.invoices.firstOrNull { it.id == previewId },
                loaded = loaded,
                toast = toast
            )
        } else {
            if (loaded && (data.shopName.isEmpty() || data.address.isEmpty())) {
                item { InvoicesSettingsNotice { nav.open(Routes.SETTINGS) } }
            }

            item {
                Row(
                    Modifier
                        .padding(bottom = if (lateCount > 0) 8.dp else 14.dp)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        "À encaisser", formatMoney(pending),
                        Modifier.weight(1f).fillMaxHeight(), unit = invoiceCount(pendingCount)
                    )
                    Spacer(Modifier.weight(1f))
                }
            }

            if (lateCount > 0) {
                item {
                    SummaryCard(
                        "En retard", formatMoney(late),
                        Modifier.padding(bottom = 14.dp).fillMaxWidth(),
                        unit = invoiceCount(lateCount), wide = true
                    )
                }
            }

            item { InvoiceFilterChips(filterKey) { filterKey = it } }
            item { ModuleSearch(query, { query = it }, "Rechercher (client, numéro…)") }

            if (loaded && visible.isEmpty()) {
                item {
                    ModuleEmpty(
                        if (data.invoices.isEmpty()) "Aucune facture" else "Aucun résultat",
                        if (data.invoices.isEmpty()) "Créez votre première facture professionnelle."
                        else "Aucune facture ne correspond à cette recherche."
                    )
                }
            }

            items(visible, key = { it.id }) { invoice ->
                InvoiceRow(invoice, statusOf[invoice.id] ?: InvoiceDisplayStatus.Draft) {
                    previewId = invoice.id
                    screen = "preview"
                }
            }
        }
    }
}

/** Aperçu d'une facture : impression, partage et document. */
private fun LazyListScope.invoicePreview(
    invoice: Invoice?,
    loaded: Boolean,
    toast: ModuleToastState
) {
    if (invoice == null) {
        if (loaded) item { ModuleEmpty("Facture introuvable", "Cette facture n'existe plus.") }
        return
    }

    item { InvoicePreviewContent(invoice, toast) }
}

@Composable
private fun InvoicePreviewContent(invoice: Invoice, toast: ModuleToastState) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp

    val status = remember(invoice) { app.invoices.displayStatus(invoice) }
    val shop = remember(invoice) { app.settings.getShop() }
    val settings = remember(invoice) { app.documents.getSettings() }
    val doc: PrintableDocument = remember(invoice) { invoiceToDocument(invoice, settings, status) }
    val jobName = doc.title + " " + doc.number

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Imprimer / PDF", Modifier.fillMaxWidth()) {
                if (!printHtml(context, documentToHtml(doc, shop, settings), jobName)) {
                    toast.show("Impression indisponible sur cet appareil.")
                }
            }
            ModuleActionButton("Partager", ModuleButtonStyle.Normal, Modifier.fillMaxWidth()) {
                if (!shareDocumentText(context, jobName, documentToText(doc, shop))) {
                    toast.show("Impossible de partager sur cet appareil.")
                }
            }
        }

        DocumentSheet(doc, shop, settings)
    }
}

/** Encart « Complétez votre entreprise » (.settings-notice). */
@Composable
private fun InvoicesSettingsNotice(onOpen: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .background(c.primarySoft, shape)
            .border(1.dp, c.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Complétez votre entreprise", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "Adresse, e-mail, NIF, logo… apparaîtront sur vos factures.",
                modifier = Modifier.padding(top = 2.dp),
                color = c.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        Text(
            "Ouvrir les Paramètres",
            modifier = Modifier
                .background(c.primary, RoundedCornerShape(10.dp))
                .tap(onOpen)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InvoiceFilterChips(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InvoiceChip("Tous", selected == "all") { onSelect("all") }
        InvoiceChip("Brouillons", selected == "draft") { onSelect("draft") }
        InvoiceChip("À payer", selected == "open") { onSelect("open") }
        InvoiceChip("En retard", selected == "overdue") { onSelect("overdue") }
        InvoiceChip("Payées", selected == "paid") { onSelect("paid") }
        InvoiceChip("Annulées", selected == "cancelled") { onSelect("cancelled") }
    }
}

@Composable
private fun InvoiceChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = Ejden.colors
    Box(
        Modifier
            .height(36.dp)
            .background(if (active) c.primary else c.surface, CircleShape)
            .border(1.dp, if (active) c.primary else c.border, CircleShape)
            .tap(onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) Color.White else c.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Brouillon (gris), à payer (orange), partielle (bleu), en retard / annulée (rouge), payée (vert). */
@Composable
private fun InvoiceStatusBadge(status: InvoiceDisplayStatus) {
    val c = Ejden.colors
    when (status) {
        InvoiceDisplayStatus.Paid -> Badge(status.label, BadgeTone.Success)
        InvoiceDisplayStatus.Unpaid -> Badge(status.label, BadgeTone.Debt)
        InvoiceDisplayStatus.Overdue, InvoiceDisplayStatus.Cancelled -> Badge(status.label, BadgeTone.Overdue)
        InvoiceDisplayStatus.Partial -> Text(
            status.label,
            modifier = Modifier.background(c.primarySoft, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
            color = c.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        InvoiceDisplayStatus.Draft -> Text(
            status.label,
            modifier = Modifier.background(Color(0xFFEEF1F2), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
            color = c.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InvoiceRow(invoice: Invoice, status: InvoiceDisplayStatus, onClick: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .tap(onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                invoice.clientName,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                invoice.number.ifEmpty { "Brouillon" } + " · " + docDayText(invoice.issueDate),
                modifier = Modifier.padding(top = 2.dp),
                color = c.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatMoney(invoice.totals().total),
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Box(Modifier.height(4.dp))
            InvoiceStatusBadge(status)
        }
    }
}
